package dev.goshadows.createsablenrails.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.TrainRelocator;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a denied right click from being swallowed on the client.
 *
 * <h2>The problem this solves</h2>
 * <p>{@code ContraptionHandlerClient#rightClickingOnContraptionsGetsHandledLocally} cancels
 * {@code InputEvent.InteractionKeyMappingTriggered} as soon as the player's crosshair hits <em>any</em>
 * contraption. Without this mixin, looking at a non-interactive contraption would silently eat the
 * click, so the player could not use the held item on the world (or in the air) — even though the
 * server would have rejected the contraption interaction anyway.</p>
 *
 * <h2>Why a redirect on one call site</h2>
 * <p>{@code rayTraceContraption} is shared by four call sites in Create 6.0.10:</p>
 * <ol>
 *     <li>{@code rightClickingOnContraptionsGetsHandledLocally} — interaction. <b>Must</b> be filtered.</li>
 *     <li>{@code ContraptionCollider#savePlayerFromClipping} — anti-clipping safety. Must <b>not</b> be
 *     filtered: hiding a contraption from that raytrace would let players clip inside it.</li>
 *     <li>{@code TrainRelocator} — relocation clearance check. Must <b>not</b> be filtered: hiding blocks
 *     would make the clearance test more permissive.</li>
 *     <li>{@code ElevatorControlsHandler} — elevator button interaction.</li>
 * </ol>
 * <p>A {@code RETURN} inject in {@code rayTraceContraption} would therefore break 2 and 3. This mixin
 * instead redirects the single call inside the interaction handler, which leaves every other caller
 * untouched.</p>
 *
 * <p>Sable 2.0.6 also mixins {@code ContraptionHandlerClient}, but only with {@code @Redirect}s on
 * {@code Vec3#distanceTo}, {@code getBoundingBox} and {@code toLocalVector} <em>inside</em> the same
 * method. Redirecting the call to {@code rayTraceContraption} is a different instruction, so the two
 * mixins do not compete.</p>
 *
 * <p>Cosmetic only: the authoritative decision is made in {@link AbstractContraptionEntityMixin} and, for
 * train relocation, in {@code TrainRelocationPacketMixin}.</p>
 *
 * <h2>Why there is also a cancellation on {@code handleSpecialInteractions}</h2>
 * <p>The raytrace redirect above hides a denied contraption from the interaction loop, so in normal play a
 * denied contraption never reaches {@code handleSpecialInteractions} through this path at all. The
 * cancellation below exists as a second, independent guard on the client: it does not rely on the
 * raytrace filter also being applied (another mod could re-target the loop, and the class is also reached
 * on other event paths in custom builds). It is still cosmetic — see the note on why the server check is
 * the one that matters.</p>
 */
@Mixin(ContraptionHandlerClient.class)
public abstract class ContraptionHandlerClientMixin {

    /**
     * Replaces the interaction-path raytrace with one that returns {@code null} for contraptions the
     * player is not allowed to touch, so Create's loop simply skips them and never reaches the
     * event-cancelling branch.
     */
    @Redirect(method = "rightClickingOnContraptionsGetsHandledLocally",
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/ContraptionHandlerClient;"
                            + "rayTraceContraption(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                            + "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;)"
                            + "Lnet/minecraft/world/phys/BlockHitResult;"),
            remap = false)
    private static BlockHitResult sableNRails$skipNonInteractiveContraptions(final Vec3 origin, final Vec3 target,
                                                                            final AbstractContraptionEntity entity) {
        if (!ContraptionInteractionPolicy.isInteractive(entity)) {
            return null;
        }
        return com.simibubi.create.content.contraptions.ContraptionHandlerClient
                .rayTraceContraption(origin, target, entity);
    }

    /**
     * Stops {@code handleSpecialInteractions} from acting on a contraption the policy denies.
     *
     * <p>In Create 6.0.10 the only non-trivial branch of that method is the wrench-on-carriage case
     * (line 134-138): {@code TrainRelocator#carriageWrenched}, which sets {@code relocatingTrain},
     * {@code relocatingOrigin} and {@code relocatingEntityId} and thereby puts the client into relocation
     * mode. From there the next click on a track calls {@code TrainRelocator#relocateClient}, which sends
     * {@code TrainRelocationPacket}. Returning {@code false} here keeps the denied contraption out of that
     * mode entirely, so no such packet is ever produced from this path.</p>
     *
     * <p>The injector is scoped to the parameter descriptors of
     * {@code handleSpecialInteractions(AbstractContraptionEntity, Player, BlockPos, Direction, InteractionHand)}
     * — the only private method with that shape in the class.</p>
     *
     * <p><b>Two checks, in this order.</b> First the clicked contraption itself
     * ({@link ContraptionInteractionPolicy#isInteractive(AbstractContraptionEntity)}); then, for a carriage,
     * the whole train ({@link ContraptionInteractionPolicy#isTrainInteractive}). The second one is the point:
     * {@code TrainRelocator#relocate} moves the train, so pointing at an allowed carriage of a train that
     * contains a denied carriage must not start the mode. Both sides therefore use the same rule as the
     * authoritative server check in {@code TrainRelocationPacketMixin} — one shared method, no drift.</p>
     *
     * <p>The train is taken from {@code CarriageContraptionEntity#getCarriage().train}. On the client this
     * is populated by Create itself: the carriage entity is bound through
     * {@code CarriageContraptionEntity#bindCarriage} via {@code Carriage#getDimensional(Level)}, and
     * {@code Carriage#train} is set by {@code Train#setTrain} from the train constructor, using the client's
     * mirrored railway data. If it is somehow absent, the click is refused rather than allowed.</p>
     *
     * <p>Allowed contraptions fall through to Create's own logic, so permitted trains keep working exactly
     * as before.</p>
     */
    @Inject(method = "handleSpecialInteractions(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/world/InteractionHand;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void sableNRails$denySpecialInteractions(final AbstractContraptionEntity contraptionEntity,
                                                            final Player player, final BlockPos localPos,
                                                            final Direction side, final InteractionHand hand,
                                                            final CallbackInfoReturnable<Boolean> cir) {
        if (!ContraptionInteractionPolicy.isInteractive(contraptionEntity)) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        // A carriage click must not start relocation mode for a train that another carriage denies:
        // TrainRelocator#relocate moves the whole train, so the rule is train-wide, not per carriage.
        if (contraptionEntity instanceof CarriageContraptionEntity carriageEntity) {
            final Carriage carriage = carriageEntity.getCarriage();
            if (carriage == null || carriage.train == null
                    || !ContraptionInteractionPolicy.isTrainInteractive(carriage.train, carriageEntity.level())) {
                cir.setReturnValue(Boolean.FALSE);
            }
        }
    }
}

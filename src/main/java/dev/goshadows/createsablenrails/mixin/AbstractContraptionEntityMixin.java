package dev.goshadows.createsablenrails.mixin;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the contraption interactivity policy on the logical server and keeps its cache coherent.
 *
 * <h2>Why a mixin is required</h2>
 * <p>Create 6.0.10 for Minecraft 1.21.1 exposes no server-side hook at this granularity:</p>
 * <ul>
 *     <li>{@code MovingInteractionBehaviour} is consulted <em>after</em> Create has decided an
 *     interaction is allowed, and only for blocks registered with such a behaviour. It cannot veto
 *     storage access (inventories, tanks), which goes through
 *     {@code MountedStorageManager#handlePlayerStorageInteraction}.</li>
 *     <li>{@code ContraptionInteractionPacket#handle} calls
 *     {@code AbstractContraptionEntity#handlePlayerInteraction} directly, with no event in between.</li>
 * </ul>
 *
 * <p>The injection is a single cancelling {@code HEAD} inject on
 * {@code handlePlayerInteraction(Player, BlockPos, Direction, InteractionHand)} and runs on both
 * logical sides. On the server it is the authoritative check: a rejected interaction returns
 * {@code false}, which is Create's own "not handled" contract, so nothing inside the method body runs —
 * no {@code contraption.interactors} dispatch, no
 * {@code MountedStorageManager#handlePlayerStorageInteraction}, no seat boarding, no passenger
 * ejection — and {@code ContraptionInteractionPacket#handle} does not call {@code sender.swing(...)}.</p>
 *
 * <p><b>What {@code false} does not cancel.</b> On the client,
 * {@code ContraptionHandlerClient#rightClickingOnContraptionsGetsHandledLocally} uses the return value
 * to choose a branch, not to stop (line 124-127 in 6.0.10): {@code false} leads to
 * {@code handleSpecialInteractions(...)}, whose only non-trivial case is
 * {@code TrainRelocator#carriageWrenched} for a wrench used on a carriage. That method
 * (line 357) only assigns client-side static fields ({@code relocatingOrigin}, {@code relocatingTrain},
 * {@code relocatingEntityId}); it sends no packet. Wrench handling and seat boarding are intentionally
 * <b>blocked by this policy</b> (a player may not board or wrench a contraption the server marked
 * non-interactive). The wrench path is additionally closed at its own two points:
 * {@code ContraptionHandlerClientMixin} cancels {@code handleSpecialInteractions} on the client, and
 * {@code TrainRelocationPacketMixin} rejects the completing packet on the server — so a denied carriage can
 * neither start nor finish a relocation. See those two classes for the full trace.</p>
 *
 * <p>Target signature verified against Create {@code mc1.21.1-6.0.10},
 * {@code src/main/java/com/simibubi/create/content/contraptions/AbstractContraptionEntity.java}
 * lines 287-296 ({@code handlePlayerInteraction}) and 427-430 ({@code setBlock}); the file is
 * byte-identical in {@code mc1.21.1/dev}. Sable 2.0.6 does not inject into either method, so the two
 * mixins do not share an injection point. {@code remap = false} is used because the target is a Create
 * class rather than a Minecraft class, matching Sable's own convention.</p>
 */
@Mixin(AbstractContraptionEntity.class)
public abstract class AbstractContraptionEntityMixin {

    /**
     * Rejects interaction with the blocks of a contraption whose interactivity policy denies it.
     *
     * <p>Seats and wrenches are deliberately not exempted, and this is a documented product decision
     * rather than an accident of where the hook sits. Create routes both through the same method
     * ({@code handlePlayerInteraction} lines 289-296: a seat position with no wrench <em>boards</em>,
     * a wrench skips the seat branch), so exempting them would mean a player could board or wrench a
     * contraption that the server marked non-interactive — which would make the policy meaningless for
     * trains, the one category where boarding matters most.</p>
     *
     * <p>Consequence to keep in mind: with {@code trainHandling = NEVER} nobody can board a carriage, and
     * with any denying configuration nobody can start <em>or</em> complete a wrench relocation of a
     * carriage — the start is cancelled client-side and the completing {@code TrainRelocationPacket} is
     * rejected server-side. That is intended.</p>
     */
    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void sableNRails$enforceInteractivityPolicy(final Player player, final BlockPos localPos,
                                                       final Direction side, final InteractionHand hand,
                                                       final CallbackInfoReturnable<Boolean> cir) {
        if (!ContraptionInteractionPolicy.isInteractive((AbstractContraptionEntity) (Object) this)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }

    /**
     * Drops the cached decision when a block inside the contraption changes through the base path.
     *
     * <p>This is <b>one of three</b> invalidation points, and on its own it is not sufficient. Verified in
     * Create {@code mc1.21.1-6.0.10}:</p>
     * <ul>
     *     <li>{@code AbstractContraptionEntity#setBlock} (line 427) writes the map and sends
     *     {@code ContraptionBlockChangedPacket}; this inject covers it.</li>
     *     <li>{@code CarriageContraptionEntity#setBlock} (line 200) <b>overrides</b> it without calling
     *     {@code super}, writing {@code cce.contraption.getBlocks()} for every present carriage entity. A
     *     separate inject on that override is required — see below.</li>
     *     <li>The client applies {@code ContraptionBlockChangedPacket} through
     *     {@code AbstractContraptionEntity#handleBlockChange} (line 762), which replaces the map entry
     *     directly. A third inject covers it.</li>
     * </ul>
     *
     * <p>All three invalidate unconditionally rather than only when the touched position holds a marker:
     * the cheap, always-correct answer is the point, and a stale entry is a policy bypass. The cache itself
     * additionally compares an exact marker signature, not a hash or a size, so even a path that bypasses
     * all three cannot keep an outdated decision (see {@code ContraptionInteractionPolicy} and
     * {@code MarkerSignature}).</p>
     */
    @Inject(method = "setBlock", at = @At("HEAD"), remap = false)
    private void sableNRails$invalidateOnBlockChange(final BlockPos localPos, final StructureBlockInfo newInfo,
                                                     final CallbackInfo ci) {
        ContraptionInteractionPolicy.invalidate(((AbstractContraptionEntity) (Object) this).getUUID());
    }
}

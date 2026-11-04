package dev.goshadows.createsablenrails.mixin;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TrainRelocationPacket;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * Server-side authorisation for wrench-driven train relocation.
 *
 * <h2>Why a second enforcement point is needed at all</h2>
 * <p>{@code AbstractContraptionEntityMixin} cancels {@code handlePlayerInteraction}, which covers every
 * interaction that reaches a contraption's blocks. Train relocation is the one action that does not need
 * a contraption interaction to <em>complete</em>: the wrench click only starts a client-side mode, and the
 * actual move is requested later by a separate packet that carries a train id rather than a contraption
 * hit. Traced in Create {@code mc1.21.1-6.0.10}:</p>
 *
 * <ol>
 *     <li>{@code ContraptionHandlerClient#rightClickingOnContraptionsGetsHandledLocally} (124-127) calls the
 *     local {@code handlePlayerInteraction}; a {@code false} result goes to
 *     {@code handleSpecialInteractions}, whose wrench-on-carriage branch (134-138) calls
 *     {@code TrainRelocator#carriageWrenched}. That method (357-365) only assigns the static
 *     {@code relocatingOrigin}, {@code relocatingTrain} and {@code relocatingEntityId} — no packet.</li>
 *     <li>The next click is handled by {@code TrainRelocator#onClicked} (75-108), which calls
 *     {@code relocateClient}; that method sends {@code TrainRelocationPacket(trainId, clickedTrackPos,
 *     lookAngle, entityId, direction, hoveredBezier)} (TrainRelocator line 157).</li>
 *     <li>{@code TrainRelocationPacket#handle} (44-82) checks that the named entity is a
 *     {@link CarriageContraptionEntity} whose {@code trainId} equals {@code trainId}, then calls
 *     {@code TrainRelocator#relocate(train, ..., simulate = false)} (line 70), which moves the whole train.</li>
 * </ol>
 *
 * <p>So the interaction gate alone leaves a hole: a client that entered relocation mode could still
 * complete the move, because the completing packet mentions no contraption the interaction gate ever sees.
 * {@code TrainRelocationPacket#handle} is Create's authoritative point for this action — it is the only
 * caller of {@code TrainRelocator#relocate} in the whole mod (verified by grep over Create 6.0.10) — so the
 * check belongs here.</p>
 *
 * <h2>Scope: the whole train, not the carriage named in the packet</h2>
 * <p>This is the part that is easy to get wrong, and it was wrong in the first version of this mixin.
 * {@code relocate} moves the <em>train</em>:
 * {@code TrainRelocator.java:263-264} loops over {@code train.carriages} calling
 * {@code carriage.updateContraptionAnchors()}, and the earlier state mutations
 * ({@code leaveStation()}, {@code graph}, every travelling point) are train-wide. A train is a list of
 * independent carriages: {@code Train.carriages} is a {@code List<Carriage>}, every {@code Carriage} has its
 * own {@code CarriageContraption} and therefore its own block map, its own markers and its own
 * {@code CarriageContraptionEntity} per dimension
 * ({@code Carriage.DimensionalCarriageEntity#entity}, reachable through
 * {@code Carriage#getDimensionalIfPresent(ResourceKey<Level>)}).</p>
 *
 * <p>Consequence: checking only the carriage named by {@code entityId} is <b>not</b> sufficient. A packet
 * could name an allowed carriage of a train whose other carriage carries {@code interact_me_not} (or simply
 * no {@code interact_me}) and still move the entire train, including the denied carriage. The decision is
 * therefore evaluated over <b>every carriage of the train</b>, and the relocation is cancelled if any single
 * carriage is non-interactive. That matches the documented rule: a denied contraption must not be relocated,
 * and moving the train relocates it.</p>
 *
 * <p><b>Rule: one denied carriage denies the whole train.</b> The policy is per contraption, so the
 * combination is a conjunction. This is stricter than "the carriage you pointed at", and deliberately so:
 * otherwise the marker on the denied carriage would be decorative as soon as the train had a second
 * carriage.</p>
 *
 * <p><b>What is evaluated, and where the rule lives.</b> The rule itself is
 * {@link ContraptionInteractionPolicy#isTrainInteractive} — one shared method, also called by the client
 * pre-check, so the two sides cannot drift apart. It resolves every carriage through the
 * {@code CarriageContraptionEntity} registered for the sender's level and answers {@code false} if any
 * carriage is non-interactive <em>or cannot be resolved</em> (fail-closed: a carriage with no entity in this
 * level cannot be proven safe). Allowed trains take Create's original code path unchanged.</p>
 *
 * <h2>Malformed and forged packets</h2>
 * <p>The packet is a client-to-server payload, so both ids in it are attacker-controlled. Three cases are
 * rejected here <em>before</em> Create's body runs, and every one of them is a case Create itself does not
 * handle safely:</p>
 * <ul>
 *     <li><b>Unknown {@code trainId}.</b> Create looks the train up
 *     ({@code TrainRelocationPacket.java:45}) and then, in the {@code train == null} branch, builds its log
 *     message from {@code train.id.toString()} (line 52) — a {@link NullPointerException} on the server
 *     thread instead of a rejection. This mixin therefore cancels any packet whose {@code trainId} does not
 *     resolve, which is the outcome Create intended ("train not present on server") without the crash.
 *     The map itself is read exactly the way Create reads it ({@code Create.RAILWAYS.trains}); if it is not
 *     initialised the packet is cancelled as well, rather than dereferenced.</li>
 *     <li><b>{@code trainId} and {@code entityId} naming different trains.</b> Create checks
 *     {@code train.id.equals(cce.trainId)} (line 57) and silently returns, so nothing moves — but the check
 *     only happens after the null branch above. This mixin performs the same comparison as part of the
 *     rejection set, so a forged pair is refused explicitly and identically on every path.</li>
 *     <li><b>{@code entityId} not resolving to a carriage.</b> Rejected here; Create would also reject it,
 *     via the same null branch that can throw.</li>
 * </ul>
 * <p>Cancelling at {@code HEAD} means none of Create's body runs, so the {@code train.id} dereference is
 * never reached, no message is sent to the player, and the train is untouched. Verified fields and methods
 * used: {@code Create.RAILWAYS} ({@code public static final GlobalRailwayManager}, {@code Create.java:101}),
 * {@code GlobalRailwayManager#trains} ({@code public Map<UUID, Train>}), {@code Train#id}
 * ({@code public UUID}), {@code CarriageContraptionEntity#trainId} ({@code public UUID}, assigned in
 * {@code setCarriage} from {@code carriage.train.id} and restored from the {@code TrainId} tag on load), and
 * the record accessors {@code trainId()} / {@code entityId()}.</p>
 *
 * <p><b>Limitation.</b> The decision is per contraption, not per player and not per track: the position the
 * train is being relocated towards is not inspected by the policy. The check and Create's own work happen in
 * the same tick on the server thread.</p>
 */
@Mixin(TrainRelocationPacket.class)
public abstract class TrainRelocationPacketMixin {

    /**
     * Rejects a relocation whose ids do not resolve, or whose train contains a non-interactive carriage.
     *
     * <p>{@code CallbackInfo} (not {@code CallbackInfoReturnable}) is correct because
     * {@code TrainRelocationPacket#handle(ServerPlayer)} returns {@code void}; the cancellation simply
     * suppresses the method body, so {@code TrainRelocator#relocate} never runs. Server-side only — the
     * packet class is never handled on the client.</p>
     *
     * <p>The guard is written so that "everything resolved and interactive" is the only way through: an
     * unresolvable {@code trainId}, an {@code entityId} that is not a carriage of that train, a carriage
     * that cannot be resolved, and a non-interactive carriage all cancel.</p>
     */
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void sableNRails$denyRelocationOfNonInteractiveTrain(final ServerPlayer sender,
                                                                 final CallbackInfo ci) {
        final TrainRelocationPacket self = (TrainRelocationPacket) (Object) this;

        // 1) The train id must resolve. Create's own handler dereferences `train.id` inside its
        //    `train == null` branch (TrainRelocationPacket.java:51-53), so letting an unknown id through
        //    would be an NPE in Create, not a rejection.
        final Map<UUID, Train> trains = Create.RAILWAYS.trains;
        final Train train = trains == null ? null : trains.get(self.trainId());
        if (train == null) {
            ci.cancel();
            return;
        }

        // 2) The entity id must resolve to a carriage, and that carriage must belong to the same train.
        //    Create checks the same thing (line 57) but only after the null-branch dereference above.
        final Entity entity = sender.level().getEntity(self.entityId());
        if (!(entity instanceof CarriageContraptionEntity carriage)
                || carriage.trainId == null
                || !train.id.equals(carriage.trainId)) {
            ci.cancel();
            return;
        }

        // 3) Same shared rule as the client pre-check: one denied carriage denies the whole train.
        if (!ContraptionInteractionPolicy.isTrainInteractive(train, sender.level())) {
            ci.cancel();
        }
    }
}

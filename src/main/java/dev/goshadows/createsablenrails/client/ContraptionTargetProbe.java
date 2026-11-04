package dev.goshadows.createsablenrails.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the contraption the player's crosshair is actually on.
 *
 * <h2>Why not {@code Minecraft#hitResult}</h2>
 * <p>{@code mc.hitResult} is whatever vanilla picked in the client level. A contraption is not part of that
 * level, so vanilla reports the block <em>behind</em> the contraption, or nothing at all. Using it as the
 * contraption hit would make the indicator appear and disappear depending on unrelated geometry.</p>
 *
 * <h2>Why the geometry is Create's, not ours</h2>
 * <p>Create already implements exactly this raycast, for the same purpose (deciding what the player is
 * pointing at inside a contraption), and it is reachable from an addon in the pinned version:</p>
 * <ul>
 *     <li>{@code ContraptionHandlerClient#getRayInputs(LocalPlayer)} (line 142) builds the eye-origin ray,
 *     caps it at {@code player.blockInteractionRange()} and shortens it to {@code mc.hitResult} when vanilla
 *     reports something closer — so a block in front of the contraption limits the ray instead of being
 *     ignored;</li>
 *     <li>{@code ContraptionHandlerClient#rayTraceContraption(Vec3, Vec3, AbstractContraptionEntity)}
 *     (line 154) maps the ray into the contraption's local space with
 *     {@code toLocalVector(..., 1)} and tests real block shapes with {@code VoxelShape#clip}, skipping blocks
 *     that are hidden in a portal.</li>
 * </ul>
 * <p>Re-implementing that here would be duplicated geometry that could drift from the version the interaction
 * gate uses. This class therefore calls Create's methods and contributes only the selection rule.</p>
 *
 * <h2>What this class adds</h2>
 * <p>Two things Create's client path does not expose as a helper:</p>
 * <ol>
 *     <li>a broad-phase pre-filter ({@link AABB#intersects}) so most candidates are rejected before a block
 *     raytrace is attempted — this is only a filter, never a hit;</li>
 *     <li>nearest-hit selection, comparing distances in <b>global</b> space via
 *     {@link AbstractContraptionEntity#toGlobalVector(Vec3, float)}, which is the transform that accounts for
 *     a rotating, moving contraption.</li>
 * </ol>
 * <p>A contraption whose box the ray only grazes, but whose blocks the ray misses, produces no hit: the
 * decision is made by the block-shape clip, not by the box.</p>
 */
public final class ContraptionTargetProbe {

    /** Slack added to the ray's bounding box, matching the 16-block inflation Create uses for the same test. */
    static final double RAY_BOX_INFLATION = 16.0D;

    private ContraptionTargetProbe() {
    }

    /**
     * Resolves the ray Create's interaction path uses: eye position to the capped, occlusion-limited target.
     *
     * @param player the local player, never {@code null} in the render path
     * @return origin/target pair, or {@code null} when the player is not available
     */
    public static Couple<Vec3> rayInputs(final net.minecraft.client.player.LocalPlayer player) {
        return player == null ? null : ContraptionHandlerClient.getRayInputs(player);
    }

    /**
     * Picks the nearest contraption whose <b>blocks</b> the ray actually hits.
     *
     * @param origin     ray origin in world space, from {@link #rayInputs}
     * @param target     ray end in world space, from {@link #rayInputs}
     * @param candidates contraptions to consider; {@code null} or empty yields {@code null}
     * @return the nearest contraption with a real block hit, or {@code null} when the ray hits none
     */
    public static AbstractContraptionEntity nearest(final Vec3 origin, final Vec3 target,
                                                    final Iterable<AbstractContraptionEntity> candidates) {
        if (origin == null || target == null || candidates == null) {
            return null;
        }
        final AABB rayBox = new AABB(origin, target).inflate(RAY_BOX_INFLATION);
        AbstractContraptionEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final AbstractContraptionEntity candidate : candidates) {
            if (candidate == null || !hasBlocks(candidate.getContraption())) {
                continue;
            }
            // Broad phase only: a box intersection is not a hit and never becomes one.
            final AABB box = candidate.getBoundingBox();
            if (box == null || !box.intersects(rayBox)) {
                continue;
            }
            // Narrow phase: Create's own block-shape raytrace, in the contraption's local space.
            final BlockHitResult localHit = ContraptionHandlerClient.rayTraceContraption(origin, target, candidate);
            if (localHit == null) {
                continue;
            }
            // Compare in global space: toGlobalVector applies the contraption's current rotation and position,
            // and the partial-tick factor 1 is what Create's interaction path passes for the same transform.
            final Vec3 globalHit = candidate.toGlobalVector(localHit.getLocation(), 1);
            final double distance = globalHit.distanceTo(origin);
            if (distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = candidate;
        }
        return best;
    }

    /**
     * Whether a contraption has any blocks at all.
     *
     * <p>A contraption entity whose block map is empty is mid-assembly or mid-disassembly; an indicator about
     * its interactivity would be meaningless, so it is treated as no target.</p>
     *
     * @param contraption the contraption to inspect, may be {@code null}
     * @return {@code true} when it has at least one block
     */
    public static boolean hasBlocks(final Contraption contraption) {
        return contraption != null && !contraption.getBlocks()
                .isEmpty();
    }

    /**
     * Whether a candidate is worth a block raytrace at all — the filter the render callback uses so it does not
     * scan every contraption and every block every frame.
     *
     * @param origin     ray origin in world space
     * @param target     ray end in world space
     * @param candidate  the contraption to test, may be {@code null}
     * @return {@code true} when the box test passes and the contraption has blocks
     */
    public static boolean isCandidate(final Vec3 origin, final Vec3 target, final AbstractContraptionEntity candidate) {
        if (origin == null || target == null || candidate == null || !hasBlocks(candidate.getContraption())) {
            return false;
        }
        final AABB box = candidate.getBoundingBox();
        return box != null && box.intersects(new AABB(origin, target).inflate(RAY_BOX_INFLATION));
    }
}

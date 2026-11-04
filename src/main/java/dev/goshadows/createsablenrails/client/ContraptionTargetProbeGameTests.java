package dev.goshadows.createsablenrails.client;

import com.simibubi.create.content.contraptions.Contraption;
import dev.goshadows.createsablenrails.CreateSableNRails;
import dev.goshadows.createsablenrails.index.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for the production targeting helper used by the status overlay.
 *
 * <p>They live in the {@code client} package so they can reach the package-private constant, and they are
 * GameTests because they need a bootstrapped block registry. <b>They have never been executed</b> in this
 * environment; they require {@code ./gradlew gameTestServer}.</p>
 *
 * <p><b>Coverage limits, stated explicitly.</b> The narrow phase (a ray that crosses a bounding box but
 * passes through empty space inside it), occlusion by a block in front, and nearest-of-two selection all need
 * real, placed {@code AbstractContraptionEntity} instances with non-trivial block maps and transforms. A
 * GameTest cannot fabricate those without assembling a contraption through Create, and the block-shape
 * raytrace itself is Create's {@code rayTraceContraption}, not this addon's code. What is asserted here is the
 * contract this addon owns: the broad-phase filter never reports a hit for a contraption with no blocks, and
 * {@code nearest} returns {@code null} for empty, {@code null} and blockless candidates. The three geometric
 * scenarios remain <b>unverified</b> and are listed as such in {@code IMPLEMENTATION_STATUS.md}.</p>
 */
@GameTestHolder(CreateSableNRails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContraptionTargetProbeGameTests {

    private static final String TEMPLATE = "empty_3x3";

    /** No candidates means no target, never an exception. */
    @GameTest(template = TEMPLATE)
    public static void noCandidatesYieldNoTarget(final GameTestHelper helper) {
        final Vec3 origin = new Vec3(0, 1, 0);
        final Vec3 target = new Vec3(0, 1, 5);
        if (ContraptionTargetProbe.nearest(origin, target, List.of()) != null) {
            helper.fail("an empty candidate list produced a target");
            return;
        }
        if (ContraptionTargetProbe.nearest(origin, target, null) != null) {
            helper.fail("a null candidate list produced a target");
            return;
        }
        if (ContraptionTargetProbe.nearest(null, target, List.of()) != null) {
            helper.fail("a null origin produced a target");
            return;
        }
        helper.succeed();
    }

    /** A contraption with no blocks can never be a candidate or a target: no box test, no raytrace. */
    @GameTest(template = TEMPLATE)
    public static void blocklessContraptionIsNotATarget(final GameTestHelper helper) {
        final Contraption empty = new TestContraption();
        final Vec3 origin = new Vec3(0, 1, 0);
        final Vec3 target = new Vec3(0, 1, 5);
        if (ContraptionTargetProbe.hasBlocks(empty)) {
            helper.fail("a contraption with no blocks reported as having blocks");
            return;
        }
        if (ContraptionTargetProbe.hasBlocks(null)) {
            helper.fail("null reported as having blocks");
            return;
        }
        final BlockState marker = ModBlocks.INTERACT_ME.get()
                .defaultBlockState();
        empty.getBlocks()
                .put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, marker, null));
        if (!ContraptionTargetProbe.hasBlocks(empty)) {
            helper.fail("a contraption with a marker block reported as empty");
            return;
        }
        // The filter is a filter, not a hit: these calls must not throw even with no entity in a level.
        if (ContraptionTargetProbe.isCandidate(origin, target, null)) {
            helper.fail("null was reported as a candidate");
            return;
        }
        if (ContraptionTargetProbe.nearest(origin, target, List.of()) != null) {
            helper.fail("an empty candidate list produced a target");
            return;
        }
        helper.succeed();
    }

    /** The ray box inflation constant is positive, so a contraption touching the ray is not missed. */
    @GameTest(template = TEMPLATE)
    public static void rayBoxIsInflated(final GameTestHelper helper) {
        if (ContraptionTargetProbe.RAY_BOX_INFLATION <= 0) {
            helper.fail("ray box inflation must be positive, was " + ContraptionTargetProbe.RAY_BOX_INFLATION);
            return;
        }
        helper.succeed();
    }

    /** Concrete contraption; matches no category branch, so the default policy applies to it. */
    private static final class TestContraption extends Contraption {
        @Override
        public boolean assemble(final net.minecraft.world.level.Level world, final BlockPos pos) {
            return false;
        }

        @Override
        public boolean canBeStabilized(final Direction facing, final BlockPos localPos) {
            return false;
        }

        @Override
        public com.simibubi.create.api.contraption.ContraptionType getType() {
            return com.simibubi.create.AllContraptionTypes.PISTON.value();
        }
    }
}

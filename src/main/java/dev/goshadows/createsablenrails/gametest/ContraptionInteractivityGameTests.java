package dev.goshadows.createsablenrails.gametest;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.ClockworkContraption;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import dev.goshadows.createsablenrails.CreateSableNRails;
import dev.goshadows.createsablenrails.index.ModBlocks;
import dev.goshadows.createsablenrails.interaction.ContraptionCategory;
import dev.goshadows.createsablenrails.interaction.ContraptionInteractionPolicy;
import dev.goshadows.createsablenrails.interaction.InteractiveDecision;
import dev.goshadows.createsablenrails.interaction.InteractiveHandling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side GameTests for the interactivity feature, runnable headlessly with
 * {@code ./gradlew gameTestServer}.
 *
 * <p>These tests cover everything that can be asserted without a client: registration of both
 * markers, block-state rotation/mirroring, the NBT round trip that Create relies on for contraption
 * persistence, classification of every Create 6.0.10 contraption type, and the decision returned by
 * {@link ContraptionInteractionPolicy} for a contraption whose block map really contains markers.</p>
 *
 * <p><b>Not covered here:</b> {@code AbstractContraptionEntity#handlePlayerInteraction} needs a live
 * contraption entity and a player, so the mixin enforcement path is verified manually; see the
 * checklist in {@code IMPLEMENTATION_STATUS.md}. Passing these tests must not be read as proof that
 * the mixin applied at runtime.</p>
 */
@GameTestHolder(CreateSableNRails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContraptionInteractivityGameTests {

    private static final String TEMPLATE = "empty_3x3";

    /** Both markers are registered, distinct, and readable back after being placed. */
    @GameTest(template = TEMPLATE)
    public static void markersAreRegistered(final GameTestHelper helper) {
        final BlockState allow = ModBlocks.INTERACT_ME.get().defaultBlockState();
        final BlockState deny = ModBlocks.INTERACT_ME_NOT.get().defaultBlockState();

        if (allow.getBlock() == deny.getBlock()) {
            helper.fail("interact_me and interact_me_not must be different blocks");
            return;
        }
        if (allow.is(ModBlocks.INTERACT_ME_NOT.get())) {
            helper.fail("interact_me state incorrectly matches interact_me_not");
            return;
        }

        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.INTERACT_ME.get());
        if (!helper.getBlockState(pos).is(ModBlocks.INTERACT_ME.get())) {
            helper.fail("placed interact_me was not readable back from the level");
            return;
        }
        helper.setBlock(pos, ModBlocks.INTERACT_ME_NOT.get());
        if (!helper.getBlockState(pos).is(ModBlocks.INTERACT_ME_NOT.get())) {
            helper.fail("placed interact_me_not was not readable back from the level");
            return;
        }
        if (!helper.getBlockState(pos).is(ModBlocks.INTERACT_ME_NOT.get())
                || helper.getBlockState(pos).is(ModBlocks.INTERACT_ME.get())) {
            helper.fail("interact_me_not state matched the wrong marker block");
            return;
        }
        helper.succeed();
    }

    /** Rotation and mirroring keep the block identity and only change the facing state. */
    @GameTest(template = TEMPLATE)
    public static void markerRotationAndMirroring(final GameTestHelper helper) {
        final BlockState base = ModBlocks.INTERACT_ME.get().defaultBlockState();
        for (final Rotation rotation : Rotation.values()) {
            if (!base.rotate(rotation).is(ModBlocks.INTERACT_ME.get())) {
                helper.fail("rotation " + rotation + " changed the marker block");
                return;
            }
        }
        for (final Mirror mirror : Mirror.values()) {
            if (!base.mirror(mirror).is(ModBlocks.INTERACT_ME.get())) {
                helper.fail("mirror " + mirror + " changed the marker block");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A filled marker state survives an NBT round trip.
     *
     * <p>This is the exact mechanism Create uses to persist contraption blocks, so it is a direct
     * test of the persistence claim rather than a proxy for it.</p>
     */
    @GameTest(template = TEMPLATE)
    public static void markerStateSurvivesNbtRoundTrip(final GameTestHelper helper) {
        final BlockState state = ModBlocks.INTERACT_ME_NOT.get()
                .defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,
                        Boolean.TRUE);

        final CompoundTag tag = NbtUtils.writeBlockState(state);
        final BlockState readBack = NbtUtils.readBlockState(
                helper.getLevel().holderLookup(Registries.BLOCK), tag);

        if (!readBack.equals(state)) {
            helper.fail("marker block state changed across an NBT round trip: " + state + " -> " + readBack);
            return;
        }
        if (!readBack.is(ModBlocks.INTERACT_ME_NOT.get())) {
            helper.fail("marker block identity was lost across an NBT round trip");
            return;
        }
        helper.succeed();
    }

    /** Every Create 6.0.10 contraption class maps to the documented category. */
    @GameTest(template = TEMPLATE)
    public static void contraptionClassification(final GameTestHelper helper) {
        assertCategory(helper, new PistonContraption(Direction.UP, false), ContraptionCategory.TRANSLATING);
        assertCategory(helper, new BearingContraption(false, Direction.NORTH), ContraptionCategory.BEARING);
        assertCategory(helper, new StabilizedContraption(Direction.NORTH), ContraptionCategory.BEARING);
        assertCategory(helper, new ClockworkContraptionHack(), ContraptionCategory.CLOCKWORK);
        assertCategory(helper, new MountedContraption(CartMovementMode.ROTATE), ContraptionCategory.MOUNTED);
        assertCategory(helper, new CarriageContraption(Direction.NORTH), ContraptionCategory.TRAIN);
        helper.succeed();
    }

    /** The category of an unrecognised contraption falls back to OTHER. */
    @GameTest(template = TEMPLATE)
    public static void unknownContraptionFallsBackToOther(final GameTestHelper helper) {
        assertCategory(helper, new UnknownContraption(), ContraptionCategory.OTHER);
        helper.succeed();
    }

    /**
     * The policy reads markers out of the contraption's own block map.
     *
     * <p>A bare contraption (never assembled, so no world mutation and no physics body) gets entries
     * injected into its block map. This is the same map Create fills during assembly and serialises
     * into NBT, so the assertions below exercise the real data path used in game.</p>
     */
    @GameTest(template = TEMPLATE)
    public static void policyReadsMarkersFromContraptionBlocks(final GameTestHelper helper) {
        final Contraption plain = contraptionWithBlocks(
                new BlockState[]{Blocks.STONE.defaultBlockState()});
        final Contraption withAllow = contraptionWithBlocks(
                new BlockState[]{Blocks.STONE.defaultBlockState(),
                        ModBlocks.INTERACT_ME.get().defaultBlockState()});
        final Contraption withDeny = contraptionWithBlocks(
                new BlockState[]{Blocks.STONE.defaultBlockState(),
                        ModBlocks.INTERACT_ME_NOT.get().defaultBlockState()});
        final Contraption withBoth = contraptionWithBlocks(
                new BlockState[]{ModBlocks.INTERACT_ME.get().defaultBlockState(),
                        ModBlocks.INTERACT_ME_NOT.get().defaultBlockState()});

        // Exhaustive cross-check against the pure decision helper for every policy.
        for (final InteractiveHandling handling : InteractiveHandling.values()) {
            assertPolicy(helper, handling, plain, false, false);
            assertPolicy(helper, handling, withAllow, true, false);
            assertPolicy(helper, handling, withDeny, false, true);
            assertPolicy(helper, handling, withBoth, true, true);
        }

        // Spot-check the two useful modes with the real marker states.
        if (!ContraptionInteractionPolicy.isInteractive(withAllow, InteractiveHandling.WITH_STICKER)) {
            helper.fail("WITH_STICKER must allow a contraption carrying interact_me");
            return;
        }
        if (ContraptionInteractionPolicy.isInteractive(plain, InteractiveHandling.WITH_STICKER)) {
            helper.fail("WITH_STICKER must reject a contraption without a marker");
            return;
        }
        if (ContraptionInteractionPolicy.isInteractive(withDeny, InteractiveHandling.WITHOUT_STICKER)) {
            helper.fail("WITHOUT_STICKER must reject a contraption carrying interact_me_not");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static void assertPolicy(final GameTestHelper helper, final InteractiveHandling handling,
                                     final Contraption contraption, final boolean hasInteractMe,
                                     final boolean hasInteractMeNot) {
        final boolean expected = InteractiveDecision.allowsInteraction(handling, hasInteractMe, hasInteractMeNot);
        final boolean actual = ContraptionInteractionPolicy.isInteractive(contraption, handling);
        if (expected != actual) {
            helper.fail("policy " + handling + " returned " + actual + " but expected " + expected
                    + " (interactMe=" + hasInteractMe + ", interactMeNot=" + hasInteractMeNot + ")");
        }
    }

    private static void assertCategory(final GameTestHelper helper, final Contraption contraption,
                                       final ContraptionCategory expected) {
        final ContraptionCategory actual = ContraptionCategory.of(contraption);
        if (actual != expected) {
            helper.fail("expected " + expected + " for " + contraption.getClass().getSimpleName()
                    + " but got " + actual);
        }
    }

    /** Builds a never-assembled contraption whose block map contains exactly the given states. */
    private static Contraption contraptionWithBlocks(final BlockState[] states) {
        final Contraption contraption = new TestContraption();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(0, 0, 0);
        for (int i = 0; i < states.length; i++) {
            final BlockPos pos = cursor.set(i, 0, 0).immutable();
            // StructureBlockInfo carries a nullable NBT tag; a marker never has block entity data.
            @SuppressWarnings("DataFlowIssue")
            final StructureBlockInfo info = new StructureBlockInfo(pos, states[i], null);
            contraption.getBlocks().put(pos, info);
        }
        return contraption;
    }

    /**
     * Clockwork contraptions have no public constructor and are created by
     * {@code ClockworkContraption#assembleClockworkAt}. A bare instance is enough for the
     * {@code instanceof} classification test, so this subclass only needs to be constructible from
     * inside the test class: {@code Contraption}'s abstract surface is exactly
     * {@code assemble(Level, BlockPos)}, {@code canBeStabilized(Direction, BlockPos)} and
     * {@code getType()}, and {@code ClockworkContraption} already implements all three. There is no
     * {@code codec()} method on {@code Contraption} in Create 6.0.10 (verified: no {@code MapCodec}
     * anywhere in {@code Contraption}, {@code ClockworkContraption}, {@code PistonContraption},
     * {@code GantryContraption} or {@code PulleyContraption}), so this class must not override one.
     */
    private static final class ClockworkContraptionHack extends ClockworkContraption {

        ClockworkContraptionHack() {
        }
    }

    /** Concrete contraption with no extra behaviour, used so the tests can build a bare block map. */
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

    /**
     * A contraption type an unknown addon could contribute: it extends {@link Contraption} directly, so
     * {@link ContraptionCategory#of(Contraption)} has no {@code instanceof} branch that matches it and may
     * only fall through to {@link ContraptionCategory#OTHER}.
     *
     * <p>It deliberately does <b>not</b> extend {@code TranslatingContraption}: that would make the test
     * assert {@code OTHER} for an object the classifier is documented (and implemented) to report as
     * {@link ContraptionCategory#TRANSLATING}, i.e. the assertion and the classifier would contradict each
     * other. Extending {@code Contraption} is also the more faithful stand-in, because an addon's own
     * contraption type is not required to reuse any Create subclass.</p>
     */
    private static final class UnknownContraption extends Contraption {
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

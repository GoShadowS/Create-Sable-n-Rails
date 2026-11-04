package dev.goshadows.createsablenrails.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import dev.goshadows.createsablenrails.CreateSableNRails;
import dev.goshadows.createsablenrails.index.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Regression tests for the <b>production cache path</b> of the interactivity decision.
 *
 * <p>They call {@link ContraptionInteractionPolicy#decide(UUID, Contraption)} — the method that
 * {@code isInteractive(AbstractContraptionEntity)} uses for every real decision — with an entity UUID and a
 * contraption built inside the test. No caching logic is reimplemented here; the test only supplies the two
 * inputs that a GameTest cannot take from a live contraption entity. That is why that helper was extracted
 * instead of the test mimicking the lookup.</p>
 *
 * <p>Plain JUnit cannot run these: obtaining the marker block states needs a bootstrapped registry.
 * <b>These tests have never been executed</b> in this environment — they require {@code ./gradlew gameTestServer}.</p>
 */
@GameTestHolder(CreateSableNRails.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContraptionInteractionPolicyGameTests {

    private static final String TEMPLATE = "empty_3x3";

    /**
     * The regression this change exists for.
     *
     * <p>Sequence: (1) calculate and cache a decision for a fresh UUID, (2) replace the marker at the same
     * position with the other marker — the block map size is asserted to be unchanged, so a size-keyed cache
     * could not tell the difference, (3) call the same production path for the same UUID, (4) require a cache
     * miss, the recalculated answer, and that the new answer is the one cached afterwards.</p>
     *
     * <p><b>Deterministic policy.</b> The test calls the four-argument production overload
     * {@link ContraptionInteractionPolicy#decide(UUID, Contraption, ContraptionCategory, InteractiveHandling)}
     * with {@code WITH_STICKER} explicitly, instead of letting the live configuration choose. Under
     * {@code WITH_STICKER} a lone {@code interact_me} allows and a lone {@code interact_me_not} denies, so the
     * swap <em>must</em> invert the answer; the assertion cannot silently weaken because of whatever the
     * shipped or reloaded config happens to contain. Production still resolves the policy from the
     * configuration in the two-argument overload, so the code under test is the same code.</p>
     *
     * <p>The contraption is a {@link TestContraption}, which matches no category branch in
     * {@link ContraptionCategory} and therefore classifies as {@code OTHER}; the category is passed explicitly
     * as {@code OTHER} and the test asserts that this is what the result reports.</p>
     */
    @GameTest(template = TEMPLATE)
    public static void sameSizeMarkerSwapInvalidatesCachedDecision(final GameTestHelper helper) {
        final UUID entityId = UUID.randomUUID();
        final BlockPos markerPos = new BlockPos(1, 1, 1);
        final Contraption contraption = new TestContraption();
        // A non-marker block keeps the map realistic: markers are a small part of a real contraption.
        contraption.getBlocks()
                .put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null));
        contraption.getBlocks()
                .put(markerPos, new StructureBlockInfo(markerPos, interactMe(), null));
        final int sizeBefore = contraption.getBlocks()
                .size();

        final ContraptionCategory category = ContraptionCategory.OTHER;
        final InteractiveHandling handling = InteractiveHandling.WITH_STICKER;

        try {
            final ContraptionInteractionPolicy.Result first =
                    ContraptionInteractionPolicy.decide(entityId, contraption, category, handling);
            if (first.cacheHit()) {
                helper.fail("a fresh UUID must not produce a cache hit");
                return;
            }
            if (first.category() != category || first.handling() != handling) {
                helper.fail("the result did not report the category and policy supplied to it");
                return;
            }
            if (!first.interactive()) {
                helper.fail("WITH_STICKER must allow a contraption whose only marker is interact_me");
                return;
            }

            // Same position, same map size, opposite marker.
            contraption.getBlocks()
                    .put(markerPos, new StructureBlockInfo(markerPos, interactMeNot(), null));
            if (contraption.getBlocks()
                    .size() != sizeBefore) {
                helper.fail("the swap changed the block map size, so the test would prove nothing");
                return;
            }

            final ContraptionInteractionPolicy.Result second =
                    ContraptionInteractionPolicy.decide(entityId, contraption, category, handling);
            if (second.cacheHit()) {
                helper.fail("a same-size marker swap still hit the entry from before the swap");
                return;
            }
            if (second.interactive()) {
                helper.fail("WITH_STICKER must deny a contraption whose only marker is interact_me_not, "
                        + "so the answer must have inverted");
                return;
            }

            // The recalculated answer must now be the cached one for the new layout.
            final ContraptionInteractionPolicy.Result third =
                    ContraptionInteractionPolicy.decide(entityId, contraption, category, handling);
            if (!third.cacheHit() || third.interactive() != second.interactive()) {
                helper.fail("the recalculated decision was not cached for the new marker layout");
                return;
            }
            helper.succeed();
        } finally {
            ContraptionInteractionPolicy.invalidate(entityId);
        }
    }

    /** Identical inputs must hit the cache and return the same answer; a fresh UUID must not. */
    @GameTest(template = TEMPLATE)
    public static void unchangedInputsHitTheCache(final GameTestHelper helper) {
        final UUID entityId = UUID.randomUUID();
        final Contraption contraption = new TestContraption();
        contraption.getBlocks()
                .put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, interactMe(), null));
        try {
            final ContraptionInteractionPolicy.Result first = ContraptionInteractionPolicy.decide(entityId, contraption);
            final ContraptionInteractionPolicy.Result second = ContraptionInteractionPolicy.decide(entityId, contraption);
            if (first.cacheHit()) {
                helper.fail("a fresh UUID must not produce a cache hit");
                return;
            }
            if (!second.cacheHit()) {
                helper.fail("identical inputs did not hit the cache");
                return;
            }
            if (first.interactive() != second.interactive()) {
                helper.fail("the cached answer differs from the calculated answer");
                return;
            }
            helper.succeed();
        } finally {
            ContraptionInteractionPolicy.invalidate(entityId);
        }
    }

    /**
     * The category is part of the cache key, so a different contraption class under the same UUID is a miss.
     *
     * <p>What this proves is precise: the category is <b>recomputed from the current {@code Contraption} on
     * every lookup and compared</b>, so a replaced contraption cannot reuse a decision. It does not claim
     * that a live carriage can swap its contraption class in place.</p>
     */
    @GameTest(template = TEMPLATE)
    public static void changedCategoryIsNotReused(final GameTestHelper helper) {
        final UUID entityId = UUID.randomUUID();
        final Contraption translating = new PistonContraption(Direction.UP, false);
        translating.getBlocks()
                .put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, interactMe(), null));
        final Contraption other = new TestContraption();
        other.getBlocks()
                .put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, interactMe(), null));
        try {
            final ContraptionInteractionPolicy.Result first = ContraptionInteractionPolicy.decide(entityId, translating);
            if (first.category() != ContraptionCategory.TRANSLATING) {
                helper.fail("a piston contraption must classify as TRANSLATING but was " + first.category());
                return;
            }
            final ContraptionInteractionPolicy.Result second = ContraptionInteractionPolicy.decide(entityId, other);
            if (second.cacheHit()) {
                helper.fail("a contraption of a different category reused the previous entry");
                return;
            }
            helper.succeed();
        } finally {
            ContraptionInteractionPolicy.invalidate(entityId);
        }
    }

    /** The signature stores marker pairs and compares them structurally; hashes are not the criterion. */
    @GameTest(template = TEMPLATE)
    public static void signatureDistinguishesMarkerKinds(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 1, 2);
        final MarkerSignature allow = MarkerSignature.of(
                Map.of(pos, new StructureBlockInfo(pos, interactMe(), null)));
        final MarkerSignature deny = MarkerSignature.of(
                Map.of(pos, new StructureBlockInfo(pos, interactMeNot(), null)));
        if (allow.equals(deny)) {
            helper.fail("signatures of the two marker kinds compared equal at the same position");
            return;
        }
        if (allow.size() != 1 || deny.size() != 1
                || !allow.contains(MarkerSignature.Kind.ALLOW) || !deny.contains(MarkerSignature.Kind.DENY)) {
            helper.fail("the signature did not record kind and position as expected");
            return;
        }
        final Map<BlockPos, StructureBlockInfo> withStone = new LinkedHashMap<>();
        withStone.put(pos, new StructureBlockInfo(pos, interactMe(), null));
        withStone.put(BlockPos.ZERO, new StructureBlockInfo(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null));
        if (!MarkerSignature.of(withStone)
                .equals(allow)) {
            helper.fail("a non-marker block changed the marker signature");
            return;
        }
        helper.succeed();
    }

    /**
     * A signature must not alias the caller's positions. {@code MutableBlockPos} is a mutable subclass of
     * {@code BlockPos}, so a signature built from one and stored in the cache key would change under it —
     * and could even break the {@code TreeMap} ordering it lives in.
     */
    @GameTest(template = TEMPLATE)
    public static void signatureSnapshotsMutablePositions(final GameTestHelper helper) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(1, 1, 1);
        final Map<BlockPos, StructureBlockInfo> blocks = new LinkedHashMap<>();
        blocks.put(cursor, new StructureBlockInfo(cursor.immutable(), interactMe(), null));
        final MarkerSignature signature = MarkerSignature.of(blocks);

        // Move the caller's position after the signature was built.
        cursor.set(5, 5, 5);

        if (signature.size() != 1) {
            helper.fail("signature lost its single marker");
            return;
        }
        final BlockPos stored = signature.markers()
                .keySet()
                .iterator()
                .next();
        if (stored instanceof BlockPos.MutableBlockPos) {
            helper.fail("the signature stored a mutable position instead of an immutable snapshot");
            return;
        }
        if (!stored.equals(new BlockPos(1, 1, 1))) {
            helper.fail("the stored position followed the caller's mutation: " + stored + " (expected 1,1,1)");
            return;
        }
        // A signature must compare equal to a freshly built one after the caller mutated its own cursor.
        final MarkerSignature rebuilt = MarkerSignature.of(
                Map.of(new BlockPos(1, 1, 1), new StructureBlockInfo(new BlockPos(1, 1, 1), interactMe(), null)));
        if (!signature.equals(rebuilt)) {
            helper.fail("the signature changed after the caller mutated its position");
            return;
        }
        helper.succeed();
    }

    private static BlockState interactMe() {
        return ModBlocks.INTERACT_ME.get()
                .defaultBlockState();
    }

    private static BlockState interactMeNot() {
        return ModBlocks.INTERACT_ME_NOT.get()
                .defaultBlockState();
    }

    /** Matches no category branch in {@link ContraptionCategory}, so it classifies as {@code OTHER}. */
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

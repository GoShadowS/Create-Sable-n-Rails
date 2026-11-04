package dev.goshadows.createsablenrails.interaction;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import dev.goshadows.createsablenrails.config.SableNRailsConfig;
import dev.goshadows.createsablenrails.index.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side evaluation and caching of the contraption interactivity decision.
 *
 * <h2>Where the state lives</h2>
 * <p>The decision is <b>derived</b> from the contraption's own block map, never stored separately.
 * The marker blocks are ordinary contraption blocks, so Create serialises them into the contraption
 * entity's NBT through {@code Contraption#writeNBT} / {@code AbstractContraptionEntity#writeAdditional},
 * sends them to clients through {@code ContraptionSyncLimiting}, and writes them back into the world on
 * disassembly via {@code Contraption#addBlocksToWorld}. An addon-owned NBT flag would therefore be
 * a duplicate source of truth that could desynchronise from the actual blocks, so this addon does not
 * add one. That is also why the decision survives world save/load and chunk reload without any extra
 * code: it is recomputed from the blocks that Create persisted.</p>
 *
 * <h2>Why a cache</h2>
 * <p>Create calls {@code AbstractContraptionEntity#handlePlayerInteraction} on every right click and
 * once per collided entity, and the client-side raytrace asks per rendered candidate. Re-scanning
 * every block of a large contraption on each of those calls is unnecessary, so the result is cached
 * per contraption entity UUID together with <b>every input the decision was derived from</b> — the exact
 * marker signature, the contraption category and the configuration policy that was actually applied. A
 * cached entry is used only when all three still match; see {@link CacheKey}.</p>
 *
 * <h2>Two layers of freshness</h2>
 * <p>The key comparison is the correctness mechanism: it makes a stale hit impossible even for a block-map
 * mutation that no mixin sees. Explicit invalidation ({@link #invalidate},
 * {@link #clear} on block-change paths, world start/stop and config load/reload) is the cheapness mechanism:
 * it drops entries so the next lookup does not have to recompute a key that is about to differ anyway.</p>
 */
public final class ContraptionInteractionPolicy {

    private static final Map<UUID, CachedDecision> CACHE = new ConcurrentHashMap<>();

    private ContraptionInteractionPolicy() {
    }

    /**
     * Everything the interactivity decision is derived from, in a form that supports exact comparison.
     *
     * <h2>Why not {@code blocks.size()}</h2>
     * <p>Reusing a decision because the map size is unchanged is wrong: replacing {@code interact_me} with
     * {@code interact_me_not} at the same position leaves the size identical and the decision inverted.</p>
     *
     * <h2>Why not a hash instead of the signature</h2>
     * <p>A hash answers "probably equal". A cache key must answer "equal": a false hit reuses a decision for
     * a contraption whose markers changed, which is exactly the bug this cache key exists to prevent.
     * {@link MarkerSignature} therefore stores the marker pairs and compares them structurally.</p>
     *
     * <h2>Why the category and the policy are part of the key</h2>
     * <p>{@code isInteractive(Contraption, InteractiveHandling)} consumes three things: the markers, the
     * category used to select the configuration entry, and the policy value that entry holds. The markers
     * are covered by the signature. The category is <em>not</em> assumed constant — it is recomputed from the
     * current {@code Contraption} object on every lookup and compared, so an entity whose contraption is
     * replaced by a different type (for example via {@code Contraption#fromNBT} on a reloaded or re-synced
     * entity) cannot reuse the old decision. The policy is read from the live configuration and compared, so
     * a configuration change is covered even if the reload event that calls {@link #clear()} were missed.</p>
     *
     * @param markers  exact marker layout of the contraption
     * @param category category the policy was selected for
     * @param handling the configuration policy that was actually applied
     * @param interactive the resulting decision
     */
    private record CachedDecision(MarkerSignature markers, ContraptionCategory category,
                                  InteractiveHandling handling, boolean interactive) {
    }

    /** Drops any cached decision for the given contraption entity. Safe on both logical sides. */
    public static void invalidate(@Nullable final UUID contraptionId) {
        if (contraptionId != null) {
            CACHE.remove(contraptionId);
        }
    }

    /** Drops the whole cache, e.g. when a world starts or stops, or the configuration is reloaded. */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Returns whether players may interact with the blocks of the contraption behind this entity.
     *
     * <p>Authoritative on the logical server; the client re-evaluates it only to avoid starting an
     * interaction that the server would reject.</p>
     */
    public static boolean isInteractive(final AbstractContraptionEntity entity) {
        final Contraption contraption = entity.getContraption();
        if (contraption == null) {
            return false;
        }
        return decide(entity.getUUID(), contraption).interactive();
    }

    /**
     * The production entry point: resolves the policy from the live configuration for the contraption's own
     * category, then delegates to {@link #decide(UUID, Contraption, ContraptionCategory, InteractiveHandling)}.
     *
     * <p>The category is recomputed from the current {@code Contraption} object here, on every call, so a
     * replaced contraption cannot be judged by a stale classification, and the policy is read from the
     * configuration as it is at that moment.</p>
     *
     * @param entityId    the cache key (the contraption entity's UUID in production)
     * @param contraption the contraption whose markers decide the result
     */
    static Result decide(final UUID entityId, final Contraption contraption) {
        final ContraptionCategory category = ContraptionCategory.of(contraption);
        return decide(entityId, contraption, category, SableNRailsConfig.SERVER.forCategory(category));
    }

    /**
     * The one calculation path used by the production cache <b>and</b> by the cache regression tests, with
     * the category and the policy supplied by the caller.
     *
     * <h2>Order of operations</h2>
     * <p>The marker signature and the key components are read first, then the cache is consulted, and
     * <b>only on a miss</b> is the decision calculated. A hit returns the stored value immediately, so the
     * common case costs one scan of the block map and nothing else. The decision itself is derived from the
     * signature that was already built, so a miss does not scan the block map a second time.</p>
     *
     * <h2>Why the inputs are read before the lookup</h2>
     * <p>The inputs <em>are</em> the key: a hit can only be recognised after reading them from the live
     * state. Because that happens on every call, a stale hit does not depend on any mixin succeeding — a
     * block-map mutation no mixin observes is caught by the signature comparison, a replaced contraption by
     * the category comparison, and a configuration change by the policy comparison. The injections and the
     * config listeners remain, to drop entries early instead of making the next lookup read a key that is
     * already known to be invalid.</p>
     *
     * <h2>Why the policy is a parameter</h2>
     * <p>Production passes the value the configuration currently holds (see the two-argument overload), so the
     * effective policy is part of the key. A test can pass a fixed value instead, which makes the assertion
     * independent of whatever the shipped configuration happens to contain without duplicating the caching
     * logic.</p>
     *
     * @param entityId    the cache key (the contraption entity's UUID in production)
     * @param contraption the contraption whose markers decide the result
     * @param category    the category the policy was selected for; part of the key
     * @param handling    the effective configuration policy; part of the key
     */
    static Result decide(final UUID entityId, final Contraption contraption, final ContraptionCategory category,
                         final InteractiveHandling handling) {
        final MarkerSignature markers = MarkerSignature.of(contraption);

        final CachedDecision cached = CACHE.get(entityId);
        if (cached != null
                && cached.markers().equals(markers)
                && cached.category() == category
                && cached.handling() == handling) {
            return new Result(cached.interactive(), markers, category, handling, true);
        }

        // Miss: derive the decision from the signature that was already built, so the block map is not
        // scanned again. This is the same rule as isInteractive(Contraption, InteractiveHandling), expressed
        // over the signature instead of over a second pass over the blocks.
        final boolean interactive = InteractiveDecision.allowsInteraction(handling,
                markers.contains(MarkerSignature.Kind.ALLOW), markers.contains(MarkerSignature.Kind.DENY));
        CACHE.put(entityId, new CachedDecision(markers, category, handling, interactive));
        return new Result(interactive, markers, category, handling, false);
    }

    /**
     * Outcome of {@link #decide}: the decision plus the inputs it was derived from, and whether it came from
     * the cache. The extra fields exist so a test can assert <em>which</em> inputs produced the answer
     * without reaching into the cache.
     *
     * @param interactive the decision
     * @param markers     the marker signature that was used
     * @param category    the category that was used
     * @param handling    the policy that was used
     * @param cacheHit    {@code true} if the stored entry matched all three inputs
     */
    record Result(boolean interactive, MarkerSignature markers, ContraptionCategory category,
                  InteractiveHandling handling, boolean cacheHit) {
    }

    /** Decision for a contraption under the configured policy. Does not touch the cache. */
    public static boolean isInteractive(final Contraption contraption) {
        return isInteractive(contraption,
                SableNRailsConfig.SERVER.forCategory(ContraptionCategory.of(contraption)));
    }

    /**
     * Decision for a contraption under an explicit policy. Does not touch the cache, so it is safe to
     * call from GameTests and from contexts where no entity exists.
     */
    public static boolean isInteractive(final Contraption contraption, final InteractiveHandling handling) {
        // Policies that ignore markers must not pay for a scan of the block map.
        if (handling == null || handling == InteractiveHandling.ALWAYS) {
            return true;
        }
        if (handling == InteractiveHandling.NEVER) {
            return false;
        }

        final Block allow = ModBlocks.INTERACT_ME.get();
        final Block deny = ModBlocks.INTERACT_ME_NOT.get();
        boolean hasInteractMe = false;
        boolean hasInteractMeNot = false;
        for (final StructureBlockInfo info : contraption.getBlocks().values()) {
            final BlockState state = info.state();
            if (state.is(allow)) {
                hasInteractMe = true;
            } else if (state.is(deny)) {
                hasInteractMeNot = true;
            }
            if (hasInteractMe && hasInteractMeNot) {
                break;
            }
        }
        return InteractiveDecision.allowsInteraction(handling, hasInteractMe, hasInteractMeNot);
    }

    /**
     * Whether a whole train may be relocated: every carriage must resolve to an interactive contraption.
     *
     * <h2>Why "every carriage" and not "the carriage that was clicked"</h2>
     * <p>{@code TrainRelocator#relocate} moves the train, not one carriage
     * ({@code TrainRelocator.java:263-264} loops {@code train.carriages} calling
     * {@code updateContraptionAnchors()}, and the state mutations before it are train-wide). A train is a
     * list of independent carriages, each with its own {@code CarriageContraption} — its own block map and
     * therefore its own markers. Judging only the clicked carriage would let a player point at an allowed
     * carriage and move a train that contains a denied one, which would make the deny marker decorative as
     * soon as a train had two carriages. The policy is per contraption, so the combination is a
     * conjunction: <b>one denied carriage denies the whole train</b>.</p>
     *
     * <h2>Who calls this</h2>
     * <p>Both the client-side pre-check in {@code ContraptionHandlerClientMixin} and the authoritative
     * server-side check in {@code TrainRelocationPacketMixin} call this one method, so the two cannot drift
     * apart. The server check remains the authoritative one; the client check exists so that a denied train
     * never enters relocation mode in the first place.</p>
     *
     * <h2>Per-carriage lookup and its fail-closed edge</h2>
     * <p>Each carriage is judged through the {@link CarriageContraptionEntity} registered for the given
     * level: on the server that entity is authoritative, on the client it is the carriage entity the client
     * is currently tracking ({@code CarriageContraptionEntity#bindCarriage} registers it through
     * {@code Carriage#getDimensional(Level)}) and it carries the client's mirrored copy of the contraption.
     * A carriage that has no entity in that level is reported as denied, because there is nothing to
     * evaluate and relocating it cannot be proven safe. See {@code IMPLEMENTATION_STATUS.md} for the
     * consequence of that rule in in-game terms.</p>
     *
     * <p>Unlike {@link #isInteractive(AbstractContraptionEntity)}, this method does not use the cache: it
     * answers a question about a set of contraptions at one instant, for example "may this train be moved
     * right now", and is called at most once per relocation attempt rather than per frame.</p>
     *
     * @param train the train that is about to be relocated
     * @param level the level the relocation is happening in
     * @return {@code true} only when every carriage of the train resolves to an interactive contraption
     */
    public static boolean isTrainInteractive(final Train train, final Level level) {
        if (train == null || train.carriages == null || level == null) {
            return false;
        }
        for (final Carriage carriage : train.carriages) {
            if (carriage == null) {
                return false;
            }
            final CarriageContraptionEntity entityInLevel = carriageEntityIn(carriage, level);
            if (entityInLevel == null || !isInteractive(entityInLevel)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The carriage entity registered for this level, or {@code null} if the carriage is not loaded here.
     *
     * <p>Uses {@code Carriage#getDimensionalIfPresent} rather than {@code getDimensional}, because the
     * latter creates a {@code DimensionalCarriageEntity} as a side effect ({@code computeIfAbsent}); a
     * read-only policy query must not mutate the carriage.</p>
     */
    @Nullable
    private static CarriageContraptionEntity carriageEntityIn(final Carriage carriage, final Level level) {
        final Carriage.DimensionalCarriageEntity dimensional = carriage.getDimensionalIfPresent(level.dimension());
        return dimensional == null ? null : dimensional.entity.get();
    }
}

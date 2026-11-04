package dev.goshadows.createsablenrails.interaction;

import com.simibubi.create.content.contraptions.Contraption;
import dev.goshadows.createsablenrails.index.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Exact, immutable description of the marker blocks of a contraption: which of the two marker kinds sits at
 * which position, and nothing else.
 *
 * <h2>Why a structural signature and not a hash</h2>
 * <p>The interactivity decision depends only on two facts: whether an {@code interact_me} marker exists and
 * whether an {@code interact_me_not} marker exists. A 64-bit hash of the marker positions is <em>evidence</em>
 * of equality, not proof — two different marker layouts can hash to the same value, and a false hit would
 * reuse a decision that no longer matches the contraption. This type therefore stores the pairs themselves
 * and compares them structurally via {@link TreeMap#equals}, so an equal signature means an identical marker
 * layout. {@link #hashCode()} is only a bucketing aid for hash-based collections.</p>
 *
 * <h2>What is deliberately excluded</h2>
 * <p>Block states ({@code face}, {@code facing}, {@code waterlogged}), block-entity NBT and every non-marker
 * block are excluded, because none of them can change the decision. Excluding them keeps a rotation or a
 * waterlogging change from needlessly evicting a cache entry.</p>
 *
 * <h2>Immutability</h2>
 * <p>A signature owns its data: {@link #of(Map)} copies only the marker entries into a fresh {@link TreeMap}
 * and exposes it through {@link Collections#unmodifiableSortedMap}, so the caller's map cannot be mutated
 * through the signature. Each <em>position</em> is snapshotted individually with {@link BlockPos#immutable()},
 * which matters because {@link net.minecraft.core.BlockPos.MutableBlockPos} is a <b>mutable subclass</b> of
 * {@link BlockPos}: storing the caller's reference would let a later {@code set}/{@code move} change a key
 * that is already in the map, corrupting the ordering and making the signature compare unequal to itself. A
 * signature that has been stored in a cache entry therefore cannot be changed from the outside, in place or
 * by aliasing.</p>
 */
final class MarkerSignature {

    /** The two marker kinds, as a closed set: {@code ALLOW} is {@code interact_me}, {@code DENY} is {@code interact_me_not}. */
    enum Kind {
        ALLOW,
        DENY
    }

    private static final MarkerSignature EMPTY = new MarkerSignature(Collections.emptySortedMap());

    private final SortedMap<BlockPos, Kind> markers;

    private MarkerSignature(final SortedMap<BlockPos, Kind> markers) {
        this.markers = markers;
    }

    /** Signature of a whole contraption block map. Non-marker entries are skipped. */
    static MarkerSignature of(final Contraption contraption) {
        return contraption == null ? EMPTY : of(contraption.getBlocks());
    }

    /**
     * Signature of a raw block map.
     *
     * <p>Iterates the map once and copies only the marker entries, so the signature is independent of the
     * map's iteration order and of any later mutation of the caller's map.</p>
     */
    static MarkerSignature of(final Map<BlockPos, StructureBlockInfo> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return EMPTY;
        }
        final Block allow = ModBlocks.INTERACT_ME.get();
        final Block deny = ModBlocks.INTERACT_ME_NOT.get();
        final SortedMap<BlockPos, Kind> found = new TreeMap<>();
        for (final Map.Entry<BlockPos, StructureBlockInfo> entry : blocks.entrySet()) {
            final StructureBlockInfo info = entry.getValue();
            if (info == null || entry.getKey() == null) {
                continue;
            }
            final BlockState state = info.state();
            final Kind kind;
            if (state.is(allow)) {
                kind = Kind.ALLOW;
            } else if (state.is(deny)) {
                kind = Kind.DENY;
            } else {
                continue;
            }
            // Snapshot the position. BlockPos itself is immutable, but MutableBlockPos is a mutable
            // subclass and is used by callers that move a cursor through a structure, so storing the
            // caller's reference would let a later set()/move() change a key that is already in the map —
            // silently corrupting the TreeMap ordering and producing a signature that compares unequal to
            // itself. immutable() returns a real BlockPos and is a no-op for a non-mutable instance.
            found.put(entry.getKey().immutable(), kind);
        }
        return found.isEmpty() ? EMPTY : new MarkerSignature(Collections.unmodifiableSortedMap(found));
    }

    /** {@code true} when the signature contains at least one marker of the given kind. */
    boolean contains(final Kind kind) {
        return this.markers.containsValue(kind);
    }

    boolean isEmpty() {
        return this.markers.isEmpty();
    }

    int size() {
        return this.markers.size();
    }

    /** Unmodifiable view, used by tests and diagnostics only. */
    SortedMap<BlockPos, Kind> markers() {
        return this.markers;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof MarkerSignature signature && this.markers.equals(signature.markers);
    }

    @Override
    public int hashCode() {
        // Derived from the sorted map only, so it is stable for equal signatures. Never used as a
        // stand-in for equality: equals() compares the pairs themselves.
        return this.markers.hashCode();
    }

    @Override
    public String toString() {
        return "MarkerSignature" + this.markers;
    }
}

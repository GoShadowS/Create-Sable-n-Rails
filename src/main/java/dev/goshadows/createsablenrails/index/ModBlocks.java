package dev.goshadows.createsablenrails.index;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.goshadows.createsablenrails.CreateSableNRails;
import dev.goshadows.createsablenrails.content.InteractMarkerBlock;
import dev.goshadows.createsablenrails.content.InteractMarkerItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block and item registration for the two contraption markers.
 *
 * <p>Both markers are instant-break, non-colliding decoration plates. {@code noOcclusion} keeps them
 * from making neighbouring faces disappear; the missing collision shape is what an attached plate
 * should have visually. {@link PushReaction#BLOCK} is kept purely for <em>piston</em> behaviour:
 * a piston cannot push a placed marker. It says nothing about contraption movement — that is a separate
 * question, answered below.</p>
 *
 * <h2>Contraption assembly: why {@link #registerMovementChecks()} exists</h2>
 * <p>The marker's own properties are actively hostile to Create's assembly defaults, in two
 * independent places of {@code Create 6.0.10}. Both were read from source, not assumed:</p>
 * <ol>
 *     <li><b>movement allowed.</b> {@code BlockMovementChecksImpl#isMovementAllowedFallback} ends with
 *     {@code return state.getPistonPushReaction() != PushReaction.BLOCK;} — so {@link PushReaction#BLOCK}
 *     makes every marker <em>unmovable</em>. {@code Contraption#moveBlock} then throws
 *     {@code AssemblyException.unmovableBlock(...)}, i.e. the whole contraption fails to assemble.
 *     This check runs <em>after</em> the "is it necessary" check, so it is the first hard failure.</li>
 *     <li><b>movement necessary.</b> {@code isMovementNecessaryFallback} returns {@code true} for brittle
 *     blocks <em>before</em> it looks at the collision shape, and
 *     {@code isBrittleFallback} classifies every {@code FaceAttachedHorizontalDirectionalBlock}
 *     (except {@code GrindstoneBlock} and {@code PackagerLinkBlock}) as brittle. So the empty collision
 *     shape is not what decides movement; brittleness does — and it happens to give the correct answer
 *     for a plate that is attached to its support.</li>
 * </ol>
 *
 * <p>Only the first point needs correcting, and it is corrected narrowly: a {@code MovementAllowedCheck}
 * that answers {@code SUCCESS} for exactly the two marker blocks and {@code PASS} for everything else, so
 * every other block in the game (including blocks from other addons) keeps Create's own behaviour.</p>
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateSableNRails.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateSableNRails.MOD_ID);

    /**
     * Shared properties. {@link PushReaction#BLOCK} is a <em>piston</em> rule ("a piston cannot push
     * this block") and is the reason the class-level javadoc needs a movement-check registration; it is
     * not a statement about contraptions.
     */
    private static BlockBehaviour.Properties markerProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_ORANGE)
                .strength(0.1F)
                .sound(SoundType.CANDLE)
                .noOcclusion()
                .noCollission()
                .instabreak()
                .pushReaction(PushReaction.BLOCK);
    }

    public static final DeferredBlock<InteractMarkerBlock> INTERACT_ME = BLOCKS.register("interact_me",
            () -> new InteractMarkerBlock(InteractMarkerBlock.Variant.INTERACT_ME, markerProperties()));

    public static final DeferredBlock<InteractMarkerBlock> INTERACT_ME_NOT = BLOCKS.register("interact_me_not",
            () -> new InteractMarkerBlock(InteractMarkerBlock.Variant.INTERACT_ME_NOT, markerProperties()));

    public static final DeferredItem<InteractMarkerItem> INTERACT_ME_ITEM = ITEMS.register("interact_me",
            () -> new InteractMarkerItem(INTERACT_ME.get(), true, InteractMarkerItem.properties()));

    public static final DeferredItem<InteractMarkerItem> INTERACT_ME_NOT_ITEM = ITEMS.register("interact_me_not",
            () -> new InteractMarkerItem(INTERACT_ME_NOT.get(), false, InteractMarkerItem.properties()));

    private ModBlocks() {
    }

    /**
     * Teaches Create that the two marker blocks may be carried by a contraption.
     *
     * <p>Registering through Create's public {@code BlockMovementChecks} API keeps Create's own
     * defaults for every other block: the check returns {@link BlockMovementChecks.CheckResult#PASS}
     * for anything that is not one of our two markers, and {@code PASS} means "no opinion, ask the next
     * check, then the fallback". Only the two marker blocks are answered with
     * {@link BlockMovementChecks.CheckResult#SUCCESS}.</p>
     *
     * <p>Identity is tested against the block references rather than the block-state, so all states
     * (floor/wall/ceiling × four facings × waterlogged) are covered by one comparison. The comparison is
     * by {@link Object#equals} on the block instance, which is what {@code DeferredBlock#get()} yields —
     * no tag lookup and no registry query is involved.</p>
     *
     * <p><b>Timing.</b> This method must be called while the mod is being constructed, i.e. from
     * {@link #register}, which runs before any world exists and therefore before any assembly can ask
     * the question. It deliberately does <em>not</em> touch {@link #INTERACT_ME} or
     * {@link #INTERACT_ME_NOT}: reading a {@code DeferredHolder} is deferred until the check actually
     * runs, which is only ever during assembly, long after registration. Calling {@code get()} inside
     * the check body (rather than at registration time) is what makes this safe on both sides and under
     * Create's own "registration is thread-safe and may happen in parallel mod init" contract.</p>
     *
     * <p>Create queries the most recently registered check first ({@code add(0, check)}, and Create 6.0.10
     * registers no checks of its own), so this check runs before the built-in fallback. No other addon's
     * check is bypassed: this one answers {@code SUCCESS} only for these two blocks and {@code PASS}
     * everywhere else.</p>
     */
    public static void registerMovementChecks() {
        BlockMovementChecks.registerMovementAllowedCheck((state, world, pos) ->
                isMarker(state) ? BlockMovementChecks.CheckResult.SUCCESS : BlockMovementChecks.CheckResult.PASS);
    }

    /** {@code true} only for the two marker blocks, in any block state. */
    private static boolean isMarker(final BlockState state) {
        return state.is(INTERACT_ME.get()) || state.is(INTERACT_ME_NOT.get());
    }

    public static void register(final IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        registerMovementChecks();
    }
}

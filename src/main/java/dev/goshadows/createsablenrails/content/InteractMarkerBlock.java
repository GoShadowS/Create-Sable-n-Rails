package dev.goshadows.createsablenrails.content;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Marker block declaring whether a Create contraption may be interacted with.
 *
 * <p>{@link Variant} selects the polarity instead of using two separate block classes, so the shared
 * placement, waterlogging, rotation and shape logic cannot drift between the two markers. The two
 * markers are nevertheless distinct registered blocks (see {@code ModBlocks}) so a block-state test
 * stays a plain identity comparison.</p>
 *
 * <h2>Contraption behaviour</h2>
 * <p>The marker returns no movement behaviour and no moving interaction behaviour. It is a plain
 * "rides along" block: Create copies its block state into the contraption's block map, the server
 * policy scan observes it there, and on disassembly Create writes it back to the world unchanged.
 * No addon-specific NBT is involved, which is what makes the marker survive disassembly, reassembly,
 * world save/load and chunk reload without extra persistence code.</p>
 */
public class InteractMarkerBlock extends FaceAttachedHorizontalDirectionalBlock implements ProperWaterloggedBlock {

    /** Which of the two markers this block instance represents. */
    public enum Variant {
        /** Permits interaction when the active policy is {@code WITH_STICKER}. */
        INTERACT_ME,
        /** Vetoes interaction when the active policy is {@code WITHOUT_STICKER}. */
        INTERACT_ME_NOT
    }

    /**
     * Plate shapes indexed by {@link Direction#get3DDataValue()} of
     * {@link FaceAttachedHorizontalDirectionalBlock#getConnectedDirection(BlockState)}: the marker
     * sits flush against the face it is attached to and is one pixel thick.
     */
    private static final VoxelShape[] SHAPES = {
            Block.box(2.0, 15.0, 2.0, 14.0, 16.0, 14.0),   // DOWN (plate on the ceiling side)
            Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0),     // UP
            Block.box(2.0, 2.0, 15.0, 14.0, 14.0, 16.0),   // NORTH
            Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 1.0),     // SOUTH
            Block.box(15.0, 2.0, 2.0, 16.0, 14.0, 14.0),   // WEST
            Block.box(0.0, 2.0, 2.0, 1.0, 14.0, 14.0)      // EAST
    };

    private final Variant variant;
    private final MapCodec<InteractMarkerBlock> codec;

    public InteractMarkerBlock(final Variant variant, final Properties properties) {
        super(properties);
        this.variant = variant;
        // All variation of this block lives in block state properties, which the codec-based block
        // serialisation does not encode, so a state-independent codec is correct here.
        this.codec = simpleCodec(ignored -> new InteractMarkerBlock(variant, properties));
        // FACE and FACING default to FLOOR/NORTH by the parent class; only waterlogging is added here.
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, Boolean.FALSE));
    }

    public Variant variant() {
        return this.variant;
    }

    public boolean isInteractMe() {
        return this.variant == Variant.INTERACT_ME;
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return this.codec;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        // The parent class already registers FACE and FACING; only WATERLOGGED is added.
        super.createBlockStateDefinition(builder.add(WATERLOGGED));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockState placement = super.getStateForPlacement(context);
        return placement == null ? null : withWater(placement, context);
    }

    @Override
    public boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        // Lenient attachment, matching Create's own face-attached decoration blocks: any non-empty
        // collision shape behind the marker counts as valid support.
        final BlockPos supportPos = pos.relative(getConnectedDirection(state).getOpposite());
        return !level.getBlockState(supportPos)
                .getCollisionShape(level, supportPos)
                .isEmpty();
    }

    @Override
    public BlockState updateShape(final BlockState state, final Direction direction, final BlockState neighbourState,
                                  final LevelAccessor level, final BlockPos pos, final BlockPos neighbourPos) {
        updateWater(level, state, pos);
        return super.updateShape(state, direction, neighbourState, level, pos, neighbourPos);
    }

    @Override
    public FluidState getFluidState(final BlockState state) {
        return fluidState(state);
    }

    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                               final CollisionContext context) {
        return SHAPES[getConnectedDirection(state).get3DDataValue()];
    }

    @Override
    public BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}

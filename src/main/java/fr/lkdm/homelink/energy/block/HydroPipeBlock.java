package fr.lkdm.homelink.energy.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.energy.hydro.HydroLayout;
import fr.lkdm.homelink.energy.hydro.HydroNetworks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Large Hydro conduit: about 10/16 of a block wide with 14/16 flanges, no block entity and no ticker.
 * It joins face to face only with another Hydro Pipe, a pump's top coupling or the turbine's inlet,
 * through {@link #connectsTo}: the very rule the hydraulic graph uses, so a drawn joint is a real one.
 * It never joins copper energy cables, Farm irrigation pipes, containers or tanks.
 */
public final class HydroPipeBlock extends Block {
    public static final MapCodec<HydroPipeBlock> CODEC = simpleCodec(HydroPipeBlock::new);
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(3, 3, 3, 13, 13, 13);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.DOWN, Block.box(3, 0, 3, 13, 3, 13), Direction.UP, Block.box(3, 13, 3, 13, 16, 13),
            Direction.NORTH, Block.box(3, 3, 0, 13, 13, 3), Direction.SOUTH, Block.box(3, 3, 13, 13, 13, 16),
            Direction.WEST, Block.box(0, 3, 3, 3, 13, 13), Direction.EAST, Block.box(13, 3, 3, 16, 13, 13));
    private final VoxelShape[] shapes = new VoxelShape[64];

    public HydroPipeBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) state = state.setValue(side, false);
        registerDefaultState(state);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction direction : Direction.values()) if ((mask & 1 << direction.ordinal()) != 0) shape = Shapes.or(shape, ARMS.get(direction));
            shapes[mask] = shape.optimize();
        }
    }

    @Override protected MapCodec<HydroPipeBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { SIDES.values().forEach(builder::add); }

    /**
     * @param neighbor state next to the pipe
     * @param direction from the pipe toward the neighbor
     * @return whether a pipe joins that neighbor
     */
    public static boolean connectsTo(BlockState neighbor, Direction direction) {
        if (neighbor.getBlock() instanceof HydroPipeBlock) return true;
        if (direction != Direction.DOWN) return false;
        if (neighbor.getBlock() instanceof HydroPumpBlock) return HydroMachineBlock.isController(neighbor);
        if (neighbor.getBlock() instanceof HydroTurbineBlock) return HydroTurbineBlock.isInlet(neighbor);
        return false;
    }

    /** @return the face of a machine cell where a pipe may attach, or null; mirrors {@link #connectsTo} */
    public static Direction portFace(BlockState machine) {
        if (machine.getBlock() instanceof HydroPumpBlock && HydroMachineBlock.isController(machine)) return HydroLayout.pumpOutletFace();
        if (machine.getBlock() instanceof HydroTurbineBlock && HydroTurbineBlock.isInlet(machine)) return HydroLayout.turbineInletFace();
        return null;
    }

    private BlockState connections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            // An unloaded neighbour keeps its drawn joint; the graph never treats it as connected.
            if (level instanceof LevelAccessor accessor && !accessor.hasChunkAt(next)) continue;
            state = state.setValue(SIDES.get(direction), connectsTo(level.getBlockState(next), direction));
        }
        return state;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(SIDES.get(direction), connectsTo(neighbor, direction));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) if (state.getValue(SIDES.get(direction))) mask |= 1 << direction.ordinal();
        return shapes[mask];
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server && !previous.is(this)) HydroNetworks.get(server).changed(pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (level instanceof ServerLevel server && !newState.is(this)) HydroNetworks.get(server).changed(pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}

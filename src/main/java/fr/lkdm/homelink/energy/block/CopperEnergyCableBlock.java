package fr.lkdm.homelink.energy.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.blockentity.CableBlockEntity;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

/** Surface-mounted copper traces. Several supported faces can share one junction. */
public final class CopperEnergyCableBlock extends BaseEntityBlock {
    public static final MapCodec<CopperEnergyCableBlock> CODEC = simpleCodec(CopperEnergyCableBlock::new);
    public static final Map<Direction, BooleanProperty> FACES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final Map<Direction, VoxelShape> PLATES = Map.of(
            Direction.DOWN, Block.box(0, 0, 0, 16, 1, 16), Direction.UP, Block.box(0, 15, 0, 16, 16, 16),
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 1), Direction.SOUTH, Block.box(0, 0, 15, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 1, 16, 16), Direction.EAST, Block.box(15, 0, 0, 16, 16, 16));
    private final VoxelShape[] shapes = new VoxelShape[64];

    public CopperEnergyCableBlock(Properties properties) {
        super(properties.noCollission().pushReaction(PushReaction.DESTROY));
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : FACES.values()) state = state.setValue(property, false);
        registerDefaultState(state.setValue(FACES.get(Direction.DOWN), true));
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = Shapes.empty();
            for (Direction direction : Direction.values()) if ((mask & 1 << direction.ordinal()) != 0) shape = Shapes.or(shape, PLATES.get(direction));
            shapes[mask] = shape;
        }
    }
    public static boolean hasFace(BlockState state, Direction face) {
        return state.getBlock() instanceof CopperEnergyCableBlock && state.getValue(FACES.get(face));
    }
    public static int faceCount(BlockState state) {
        int count = 0;
        for (Direction face : Direction.values()) if (hasFace(state, face)) count++;
        return count;
    }
    public static boolean supported(LevelReader level, BlockPos pos, Direction face) {
        BlockPos support = pos.relative(face);
        if (!level.hasChunkAt(support)) return false;
        return level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite())
                || level instanceof Level world && world.getCapability(HeCapabilities.PORT, support, face.getOpposite()) != null;
    }
    /** Tangential continuation, or a trace wrapping around the outside of the same supporting block. */
    @Nullable public static BlockPos continuation(Level level, BlockPos pos, Direction face, Direction tangent) {
        if (face.getAxis() == tangent.getAxis()) return null;
        BlockPos straight = pos.relative(tangent);
        if (!level.isLoaded(straight)) return null;
        if (hasFace(level.getBlockState(straight), face)) return straight;
        BlockPos corner = straight.relative(face);
        if (level.isLoaded(corner) && hasFace(level.getBlockState(corner), tangent.getOpposite())
                && !level.getBlockState(straight).isCollisionShapeFullBlock(level, straight)) return corner;
        return null;
    }
    public static Set<BlockPos> neighbors(Level level, BlockPos pos) {
        Set<BlockPos> result = new HashSet<>();
        BlockState state = level.getBlockState(pos);
        for (Direction face : Direction.values()) if (hasFace(state, face)) {
            for (Direction tangent : Direction.values()) {
                BlockPos next = continuation(level, pos, face, tangent);
                if (next != null) result.add(next);
            }
        }
        return result;
    }
    /** Ports can touch a support trace or the edge of a trace in the adjacent cell. */
    public static boolean connects(Level level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        if (!level.isLoaded(next)) return false;
        if (neighbors(level, pos).contains(next)) return true;
        if (level.getCapability(HeCapabilities.PORT, next, side.getOpposite()) == null) return false;
        BlockState state = level.getBlockState(pos);
        for (Direction face : Direction.values()) if (hasFace(state, face) && side != face.getOpposite()) return true;
        return false;
    }
    public static boolean arm(Level level, BlockPos pos, Direction face, Direction tangent) {
        if (face.getAxis() == tangent.getAxis()) return false;
        return hasFace(level.getBlockState(pos), tangent) || continuation(level, pos, face, tangent) != null
                || level.isLoaded(pos.relative(tangent)) && level.getCapability(HeCapabilities.PORT, pos.relative(tangent), tangent.getOpposite()) != null;
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { FACES.values().forEach(builder::add); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) if (hasFace(state, direction)) mask |= 1 << direction.ordinal();
        return shapes[mask];
    }
    @Override protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && faceCount(state) < 6;
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState existing = context.getLevel().getBlockState(pos);
        BlockState state = existing.is(this) ? existing : defaultBlockState().setValue(FACES.get(Direction.DOWN), false);
        // The clicked surface takes precedence; nearest looking directions allow adding an inner corner.
        Direction preferred = context.getClickedFace().getOpposite();
        if (!hasFace(state, preferred) && supported(context.getLevel(), pos, preferred)) return state.setValue(FACES.get(preferred), true);
        for (Direction face : context.getNearestLookingDirections())
            if (!hasFace(state, face) && supported(context.getLevel(), pos, face)) return state.setValue(FACES.get(face), true);
        return null;
    }
    @Override protected BlockState updateShape(BlockState state, Direction face, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // An unloaded support must not be interpreted as a removed support.
        if (hasFace(state, face) && level.hasChunkAt(neighborPos) && !supported(level, pos, face)) {
            state = state.setValue(FACES.get(face), false);
            if (faceCount(state) == 0) return Blocks.AIR.defaultBlockState();
            if (level instanceof ServerLevel server) Block.popResource(server, pos, new net.minecraft.world.item.ItemStack(asItem()));
        }
        return state;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server) EnergyNetworks.get(server).markDirty(pos);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level instanceof ServerLevel server) EnergyNetworks.get(server).markDirty(pos);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CableBlockEntity(pos, state); }
}

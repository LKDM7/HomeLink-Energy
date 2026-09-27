package fr.lkdm.homelink.energy.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.jetbrains.annotations.Nullable;

/** Same occupied footprint and single-controller lifecycle as solar arrays. */
public final class WindTurbineBlock extends BaseEntityBlock {
    public static final MapCodec<WindTurbineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(WindTier::valueOf, WindTier::name).fieldOf("tier").forGetter(WindTurbineBlock::tier),
            propertiesCodec()).apply(instance, WindTurbineBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    private final WindTier tier;
    private final java.util.Map<BlockState,VoxelShape> shapes = new java.util.HashMap<>();

    public WindTurbineBlock(WindTier tier, Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0));
        for (BlockState state : stateDefinition.getPossibleStates()) shapes.put(state, pedestalShape(state));
    }
    public WindTier tier() { return tier; }
    public static boolean isController(BlockState state) { return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0; }
    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return pos.relative(facing.getClockWise(), -state.getValue(COLUMN)).relative(facing.getOpposite(), -state.getValue(ROW));
    }
    public List<BlockPos> positions(BlockPos origin, BlockState state) {
        List<BlockPos> positions = new ArrayList<>(4);
        for (int row = 0; row < tier.depth(); row++) for (int column = 0; column < tier.width(); column++)
            positions.add(origin.relative(state.getValue(FACING).getClockWise(), column).relative(state.getValue(FACING).getOpposite(), row));
        return positions;
    }
    public boolean complete(Level level, BlockPos origin, BlockState state) {
        for (BlockPos pos : positions(origin, state)) {
            if (!level.isLoaded(pos)) return false;
            BlockState tile = level.getBlockState(pos);
            if (!tile.is(this) || tile.getValue(FACING) != state.getValue(FACING) || !controllerPos(pos, tile).equals(origin)) return false;
        }
        return true;
    }
    /** Upgrade old one-block saves only when every additional cell is empty; never overwrite builds. */
    public boolean expandLegacyBase(Level level, BlockPos origin, BlockState state) {
        for (BlockPos pos : positions(origin, state)) {
            if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
            BlockState tile = level.getBlockState(pos);
            if (!tile.isAir() && !(tile.is(this) && tile.getValue(FACING) == state.getValue(FACING)
                    && controllerPos(pos, tile).equals(origin))) return false;
        }
        setPlacedBy(level, origin, state, null, ItemStack.EMPTY);
        return true;
    }
    @Nullable public static WindTurbineBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        BlockPos origin = controllerPos(pos, state);
        if (!level.isLoaded(origin)) return null;
        BlockState master = level.getBlockState(origin);
        return master.is(state.getBlock()) && isController(master) && master.getValue(FACING) == state.getValue(FACING)
                && level.getBlockEntity(origin) instanceof WindTurbineBlockEntity turbine ? turbine : null;
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, COLUMN, ROW); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    private VoxelShape pedestalShape(BlockState state) {
        if(state.getValue(COLUMN)>=tier.width() || state.getValue(ROW)>=tier.depth()) return Shapes.empty();
        double cx=tier.width()*8,cz=tier.depth()*8;
        VoxelShape shape=Block.box(0,0,0,16,3,16);
        for(double[] box:new double[][]{{1.6,3,1.6,16*tier.width()-1.6,11,16*tier.depth()-1.6},{cx-4,11,cz-4,cx+4,14.5,cz+4}}) {
            double x0=Math.max(0,box[0]-16*state.getValue(COLUMN)),z0=Math.max(0,box[2]-16*state.getValue(ROW));
            double x1=Math.min(16,box[3]-16*state.getValue(COLUMN)),z1=Math.min(16,box[5]-16*state.getValue(ROW));
            if(x1<=x0 || z1<=z0) continue;
            var facing=state.getValue(FACING);
            double a=x0,b=z0,c=x1,d=z1;
            switch(facing) {
                case EAST -> { a=16-z1; b=x0; c=16-z0; d=x1; }
                case SOUTH -> { a=16-x1; b=16-z1; c=16-x0; d=16-z0; }
                case WEST -> { a=z0; b=16-x1; c=z1; d=16-x0; }
                default -> { }
            }
            shape=Shapes.or(shape,Block.box(a,box[1],b,c,box[4],d));
        }
        return shape.optimize();
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shapes.get(state); }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new WindTurbineBlockEntity(pos, state) : null;
    }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || !isController(state) ? null : createTickerHelper(type, EnergyRegistries.WIND_TURBINE.get(), WindTurbineBlockEntity::serverTick);
    }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        int index=0;
        for (BlockPos pos : positions(context.getClickedPos(), state)) {
            BlockState tile=state.setValue(COLUMN,index%tier.width()).setValue(ROW,index/tier.width());
            index++;
            if (!context.getLevel().isLoaded(pos) || !context.getLevel().getWorldBorder().isWithinBounds(pos)
                    || !context.getLevel().getBlockState(pos).canBeReplaced(context)
                    || !context.getLevel().isUnobstructed(tile, pos, context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer()))) return null;
        }
        return state;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            for (int row = 0; row < tier.depth(); row++) for (int column = 0; column < tier.width(); column++) {
                if (column == 0 && row == 0) continue;
                BlockPos tile = pos.relative(state.getValue(FACING).getClockWise(), column).relative(state.getValue(FACING).getOpposite(), row);
                level.setBlock(tile, state.setValue(COLUMN, column).setValue(ROW, row), Block.UPDATE_ALL);
            }
            if (placer instanceof Player player && level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) turbine.setOwner(player.getUUID());
        }
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!level.isClientSide && !isController(state)) level.scheduleTick(pos, this, 40);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isController(state)) return;
        if (level.isLoaded(controllerPos(pos, state)) && controller(level, pos, state) == null) level.removeBlock(pos, false);
        else level.scheduleTick(pos, this, 40);
    }
    private void dismantle(BlockState state, Level level, BlockPos broken, boolean dropController) {
        if (level.isClientSide || DISMANTLING.get()) return;
        DISMANTLING.set(true);
        try {
            BlockPos origin = controllerPos(broken, state);
            for (BlockPos pos : positions(origin, state)) {
                if (pos.equals(broken) || !level.isLoaded(pos)) continue;
                BlockState tile = level.getBlockState(pos);
                if (tile.is(this) && controllerPos(pos, tile).equals(origin)) {
                    if (pos.equals(origin) && dropController) level.destroyBlock(pos, true);
                    else level.removeBlock(pos, false);
                }
            }
        } finally { DISMANTLING.set(false); }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative() || !player.hasCorrectToolForDrops(state)) dismantle(state, level, pos, false);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) turbine.destroyed();
            dismantle(state, level, pos, true);
            level.invalidateCapabilities(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            WindTurbineBlockEntity turbine = controller(level, pos, state);
            if (turbine != null) {
                serverPlayer.openMenu(turbine, buffer -> buffer.writeBlockPos(turbine.getBlockPos()));
                fr.lkdm.homelink.energy.network.EnergyPayloads.sendNetworkChoices(serverPlayer, turbine);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide) {
            var turbine = controller(level, pos, state);
            if (turbine != null) turbine.invalidateClearance();
        }
    }
}

package fr.lkdm.homelink.energy.block;

import fr.lkdm.homelink.energy.blockentity.EnergyDeviceBlockEntity;
import fr.lkdm.homelink.energy.hydro.HydroLayout;
import fr.lkdm.homelink.energy.hydro.HydroNetworks;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Hydro multiblock with one master cell (0, 0, 0) and passive parts, in the {@link HydroLayout} frame.
 * Same lifecycle as the solar and wind arrays: every cell is checked before the item is used, breaking
 * any part dismantles only this machine and drops at most one item (from the master's loot table), and
 * an unloaded master is never mistaken for a destroyed one.
 */
public abstract class HydroMachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 0, 1);
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);

    protected HydroMachineBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0);
        if (state.hasProperty(LAYER)) state = state.setValue(LAYER, 0);
        registerDefaultState(state);
    }

    public abstract int width();
    public abstract int height();
    public abstract int depth();
    /** @return whether FACING points at the placing player (turbine) or away from them (pump intake) */
    protected abstract boolean facesPlayer();
    protected abstract boolean isOwnEntity(@Nullable net.minecraft.world.level.block.entity.BlockEntity entity);

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, COLUMN, ROW); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    public static int column(BlockState state) { return state.getValue(COLUMN); }
    public static int row(BlockState state) { return state.getValue(ROW); }
    public static int layer(BlockState state) { return state.hasProperty(LAYER) ? state.getValue(LAYER) : 0; }
    public static boolean isController(BlockState state) { return column(state) == 0 && row(state) == 0 && layer(state) == 0; }

    public static BlockPos controllerPos(BlockPos pos, BlockState state) {
        return HydroLayout.origin(pos, state.getValue(FACING), column(state), layer(state), row(state));
    }

    /** @return every cell of the machine, master first */
    public List<BlockPos> positions(BlockPos origin, BlockState state) {
        List<BlockPos> positions = new ArrayList<>(width() * height() * depth());
        for (int layer = 0; layer < height(); layer++) for (int row = 0; row < depth(); row++) for (int column = 0; column < width(); column++)
            positions.add(HydroLayout.cell(origin, state.getValue(FACING), column, layer, row));
        return positions;
    }

    protected BlockState part(BlockState state, int column, int layer, int row) {
        BlockState part = state.setValue(COLUMN, column).setValue(ROW, row);
        return part.hasProperty(LAYER) ? part.setValue(LAYER, layer) : part;
    }

    /** @return whether every loaded cell belongs to this machine; an unloaded cell counts as incomplete */
    public boolean complete(Level level, BlockPos origin, BlockState state) {
        for (BlockPos pos : positions(origin, state)) {
            if (!level.isLoaded(pos)) return false;
            BlockState tile = level.getBlockState(pos);
            if (!tile.is(this) || tile.getValue(FACING) != state.getValue(FACING) || !controllerPos(pos, tile).equals(origin)) return false;
        }
        return true;
    }

    /** @return the master block entity of any part, or null when it is not loaded or not this machine */
    @Nullable
    public static EnergyDeviceBlockEntity controllerEntity(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof HydroMachineBlock block)) return null;
        BlockPos origin = controllerPos(pos, state);
        if (!level.isLoaded(origin)) return null;
        BlockState master = level.getBlockState(origin);
        if (!master.is(block) || !isController(master) || master.getValue(FACING) != state.getValue(FACING)) return null;
        var entity = level.getBlockEntity(origin);
        return block.isOwnEntity(entity) ? (EnergyDeviceBlockEntity) entity : null;
    }

    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        BlockState state = defaultBlockState().setValue(FACING, facesPlayer() ? look.getOpposite() : look);
        Level level = context.getLevel();
        var collision = context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        for (int layer = 0; layer < height(); layer++) for (int row = 0; row < depth(); row++) for (int column = 0; column < width(); column++) {
            BlockPos pos = HydroLayout.cell(context.getClickedPos(), state.getValue(FACING), column, layer, row);
            if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context) || !level.isUnobstructed(part(state, column, layer, row), pos, collision)) return null;
        }
        return state;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        for (int layer = 0; layer < height(); layer++) for (int row = 0; row < depth(); row++) for (int column = 0; column < width(); column++) {
            if (column == 0 && row == 0 && layer == 0) continue;
            level.setBlock(HydroLayout.cell(pos, state.getValue(FACING), column, layer, row), part(state, column, layer, row), Block.UPDATE_ALL);
        }
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof EnergyDeviceBlockEntity entity && isOwnEntity(entity))
            entity.setOwner(player.getUUID());
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (level instanceof ServerLevel server) {
            HydroNetworks.get(server).changed(pos);
            if (!isController(state)) level.scheduleTick(pos, this, 40);
        }
    }

    /** Orphan parts are removed only once their master's chunk is loaded and the master is really gone. */
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isController(state)) return;
        if (level.isLoaded(controllerPos(pos, state)) && controllerEntity(level, pos, state) == null) level.removeBlock(pos, false);
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
            if (level.getBlockEntity(pos) instanceof EnergyDeviceBlockEntity entity && isOwnEntity(entity)) entity.destroyed();
            dismantle(state, level, pos, true);
            level.invalidateCapabilities(pos);
            if (level instanceof ServerLevel server) HydroNetworks.get(server).changed(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            var entity = controllerEntity(level, pos, state);
            if (entity != null) {
                serverPlayer.openMenu(entity, buffer -> buffer.writeBlockPos(entity.getBlockPos()));
                fr.lkdm.homelink.energy.network.EnergyPayloads.sendNetworkChoices(serverPlayer, entity);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
        if (level.isClientSide) return;
        var entity = controllerEntity(level, pos, state);
        if (entity instanceof fr.lkdm.homelink.energy.blockentity.HydroMachineEntity machine) machine.surroundingsChanged();
    }
}

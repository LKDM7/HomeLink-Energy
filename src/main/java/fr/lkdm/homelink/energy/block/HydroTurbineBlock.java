package fr.lkdm.homelink.energy.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.hydro.HydroLayout;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The single Hydro Turbine: eight real cells (2 wide, 2 high, 2 deep). FACING is the front with the
 * control panel and the discharge louvers; it faces the placing player. Ports are listed in {@link HydroLayout}.
 */
public final class HydroTurbineBlock extends HydroMachineBlock {
    public static final MapCodec<HydroTurbineBlock> CODEC = simpleCodec(HydroTurbineBlock::new);

    public HydroTurbineBlock(Properties properties) { super(properties); }

    @Override public int width() { return HydroLayout.TURBINE_SIZE; }
    @Override public int height() { return HydroLayout.TURBINE_SIZE; }
    @Override public int depth() { return HydroLayout.TURBINE_SIZE; }
    @Override protected boolean facesPlayer() { return true; }
    @Override protected boolean isOwnEntity(@Nullable BlockEntity entity) { return entity instanceof HydroTurbineBlockEntity; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LAYER);
    }

    @Nullable public static HydroTurbineBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return controllerEntity(level, pos, state) instanceof HydroTurbineBlockEntity turbine ? turbine : null;
    }

    /** @return whether this cell holds the Hydro inlet */
    public static boolean isInlet(BlockState state) {
        return column(state) == HydroLayout.TURBINE_INLET[0] && layer(state) == HydroLayout.TURBINE_INLET[1] && row(state) == HydroLayout.TURBINE_INLET[2];
    }

    /** @return whether {@code side} of this cell is the single HE output face */
    public static boolean isOutput(BlockState state, @Nullable Direction side) {
        return column(state) == HydroLayout.TURBINE_OUTPUT[0] && layer(state) == HydroLayout.TURBINE_OUTPUT[1] && row(state) == HydroLayout.TURBINE_OUTPUT[2]
                && side == HydroLayout.turbineOutputFace(state.getValue(FACING));
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new HydroTurbineBlockEntity(pos, state) : null;
    }

    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!isController(state)) return null;
        return level.isClientSide ? createTickerHelper(type, EnergyRegistries.HYDRO_TURBINE.get(), HydroTurbineBlockEntity::clientTick)
                : createTickerHelper(type, EnergyRegistries.HYDRO_TURBINE.get(), HydroTurbineBlockEntity::serverTick);
    }
}

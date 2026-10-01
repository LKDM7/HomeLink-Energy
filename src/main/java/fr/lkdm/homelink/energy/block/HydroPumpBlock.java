package fr.lkdm.homelink.energy.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.hydro.HydroPumpTier;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Hydro Pump I (1x1x1), II (2x1x1) or III (2x1x2). FACING is the intake grille, turned toward the water
 * the player looks at when placing it. The only hydraulic port is the coupling on top of the master.
 */
public final class HydroPumpBlock extends HydroMachineBlock {
    public static final MapCodec<HydroPumpBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(HydroPumpTier::valueOf, HydroPumpTier::name).fieldOf("tier").forGetter(HydroPumpBlock::tier),
            propertiesCodec()).apply(instance, HydroPumpBlock::new));
    private static final VoxelShape BODY = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape MASTER = Shapes.or(BODY, Block.box(3, 12, 3, 13, 16, 13));
    private final HydroPumpTier tier;

    public HydroPumpBlock(HydroPumpTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public HydroPumpTier tier() { return tier; }
    @Override public int width() { return tier.width(); }
    @Override public int height() { return 1; }
    @Override public int depth() { return tier.depth(); }
    @Override protected boolean facesPlayer() { return false; }
    @Override protected boolean isOwnEntity(@Nullable BlockEntity entity) { return entity instanceof HydroPumpBlockEntity; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable public static HydroPumpBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return controllerEntity(level, pos, state) instanceof HydroPumpBlockEntity pump ? pump : null;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isController(state) ? MASTER : BODY;
    }

    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new HydroPumpBlockEntity(pos, state) : null;
    }

    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!isController(state)) return null;
        return level.isClientSide ? createTickerHelper(type, EnergyRegistries.HYDRO_PUMP.get(), HydroPumpBlockEntity::clientTick)
                : createTickerHelper(type, EnergyRegistries.HYDRO_PUMP.get(), HydroPumpBlockEntity::serverTick);
    }
}

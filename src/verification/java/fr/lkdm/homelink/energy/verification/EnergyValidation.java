package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Development-only checks; this source set is excluded from the shipped mod. */
@Mod(EnergyValidation.MOD_ID)
public final class EnergyValidation {
    public static final String MOD_ID = "energy_validation";

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    static final DeferredBlock<ConsumerBlock> CONSUMER = BLOCKS.register("test_consumer", () -> new ConsumerBlock(BlockBehaviour.Properties.of()));
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestConsumer>> CONSUMER_ENTITY = ENTITIES.register("test_consumer",
            () -> BlockEntityType.Builder.of(TestConsumer::new, CONSUMER.get()).build(null));

    public EnergyValidation(IEventBus modBus) {
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(HeCapabilities.PORT, CONSUMER_ENTITY.get(), (consumer, side) -> consumer.port));
    }

    /** Test-only consumer block. */
    static final class ConsumerBlock extends BaseEntityBlock {
        ConsumerBlock(Properties properties) { super(properties); }
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(ConsumerBlock::new); }
        @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TestConsumer(pos, state); }
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    static <T extends BlockEntity> T place(GameTestHelper helper, Block block, BlockPos pos) {
        helper.setBlock(pos, block);
        if (block instanceof fr.lkdm.homelink.energy.block.BatteryBlock battery) {
            BlockPos absolute = helper.absolutePos(pos);
            battery.setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), null, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (block instanceof fr.lkdm.homelink.energy.block.SolarPanelBlock panel) {
            BlockPos absolute = helper.absolutePos(pos);
            panel.setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), null, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (block instanceof fr.lkdm.homelink.energy.block.WindTurbineBlock turbine) {
            BlockPos absolute = helper.absolutePos(pos);
            turbine.setPlacedBy(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), null, net.minecraft.world.item.ItemStack.EMPTY);
        }
        return helper.getBlockEntity(pos);
    }

    /** Sets a battery's energy through its saved data, bypassing the per-tick transfer limit. */
    static void charge(GameTestHelper helper, BatteryBlockEntity battery, long energy) {
        var registries = helper.getLevel().registryAccess();
        var tag = battery.saveWithFullMetadata(registries);
        tag.putLong("energy", energy);
        battery.loadWithComponents(tag, registries);
    }

    /** Frozen time and weather, set identically by every test of a batch. */
    static void world(GameTestHelper helper, long dayTime, boolean raining, boolean thundering) {
        var level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(dayTime);
        level.setWeatherParameters(raining ? 0 : 1_000_000, raining ? 1_000_000 : 0, raining, thundering);
        level.setRainLevel(raining ? 1 : 0);
        level.setThunderLevel(thundering ? 1 : 0);
    }
}

package fr.lkdm.homelink.energy.registry;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.block.WindTurbineBlock;
import fr.lkdm.homelink.energy.block.WindTier;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.menu.WindTurbineMenu;
import fr.lkdm.homelink.energy.block.BatteryBlock;
import fr.lkdm.homelink.energy.block.BatteryTier;
import fr.lkdm.homelink.energy.block.CopperEnergyCableBlock;
import fr.lkdm.homelink.energy.block.EnergyBlockItem;
import fr.lkdm.homelink.energy.block.SolarPanelBlock;
import fr.lkdm.homelink.energy.block.SolarTier;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.blockentity.CableBlockEntity;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.menu.BatteryMenu;
import fr.lkdm.homelink.energy.menu.SolarPanelMenu;
import fr.lkdm.homelink.energy.block.HydroPipeBlock;
import fr.lkdm.homelink.energy.block.HydroPumpBlock;
import fr.lkdm.homelink.energy.block.HydroTurbineBlock;
import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.hydro.HydroPumpTier;
import fr.lkdm.homelink.energy.menu.HydroPumpMenu;
import fr.lkdm.homelink.energy.menu.HydroTurbineMenu;
import net.minecraft.sounds.SoundEvent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registered object of HomeLink Energy. */
public final class EnergyRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeLinkEnergy.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeLinkEnergy.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeLinkEnergy.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HomeLinkEnergy.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HomeLinkEnergy.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeLinkEnergy.MOD_ID);

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_1 = solar("solar_panel_1", SolarTier.I);
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_2 = solar("solar_panel_2", SolarTier.II);
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL_3 = solar("solar_panel_3", SolarTier.III);
    public static final DeferredBlock<BatteryBlock> BATTERY_1 = battery("battery_1", BatteryTier.I);
    public static final DeferredBlock<WindTurbineBlock> WIND_TURBINE_1 = wind("wind_turbine_1", WindTier.I);
    public static final DeferredBlock<WindTurbineBlock> WIND_TURBINE_2 = wind("wind_turbine_2", WindTier.II);
    public static final DeferredBlock<WindTurbineBlock> WIND_TURBINE_3 = wind("wind_turbine_3", WindTier.III);
    public static final DeferredBlock<BatteryBlock> BATTERY_2 = battery("battery_2", BatteryTier.II);
    public static final DeferredBlock<BatteryBlock> BATTERY_3 = battery("battery_3", BatteryTier.III);
    public static final DeferredBlock<CopperEnergyCableBlock> COPPER_ENERGY_CABLE = BLOCKS.register("copper_energy_cable", () -> new CopperEnergyCableBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F, 3.0F).sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredBlock<HydroPumpBlock> HYDRO_PUMP_1 = pump("hydro_pump_1", HydroPumpTier.I);
    public static final DeferredBlock<HydroPumpBlock> HYDRO_PUMP_2 = pump("hydro_pump_2", HydroPumpTier.II);
    public static final DeferredBlock<HydroPumpBlock> HYDRO_PUMP_3 = pump("hydro_pump_3", HydroPumpTier.III);
    public static final DeferredBlock<HydroPipeBlock> HYDRO_PIPE = BLOCKS.register("hydro_pipe", () -> new HydroPipeBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredBlock<HydroTurbineBlock> HYDRO_TURBINE_BLOCK = BLOCKS.register("hydro_turbine", () -> new HydroTurbineBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredItem<EnergyBlockItem> SOLAR_PANEL_1_ITEM = ITEMS.register("solar_panel_1", () -> new EnergyBlockItem(SOLAR_PANEL_1.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> SOLAR_PANEL_2_ITEM = ITEMS.register("solar_panel_2", () -> new EnergyBlockItem(SOLAR_PANEL_2.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> SOLAR_PANEL_3_ITEM = ITEMS.register("solar_panel_3", () -> new EnergyBlockItem(SOLAR_PANEL_3.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> BATTERY_1_ITEM = ITEMS.register("battery_1", () -> new EnergyBlockItem(BATTERY_1.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> WIND_TURBINE_1_ITEM = ITEMS.register("wind_turbine_1", () -> new EnergyBlockItem(WIND_TURBINE_1.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> WIND_TURBINE_2_ITEM = ITEMS.register("wind_turbine_2", () -> new EnergyBlockItem(WIND_TURBINE_2.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> WIND_TURBINE_3_ITEM = ITEMS.register("wind_turbine_3", () -> new EnergyBlockItem(WIND_TURBINE_3.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> BATTERY_2_ITEM = ITEMS.register("battery_2", () -> new EnergyBlockItem(BATTERY_2.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> BATTERY_3_ITEM = ITEMS.register("battery_3", () -> new EnergyBlockItem(BATTERY_3.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> HYDRO_PUMP_1_ITEM = ITEMS.register("hydro_pump_1", () -> new EnergyBlockItem(HYDRO_PUMP_1.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> HYDRO_PUMP_2_ITEM = ITEMS.register("hydro_pump_2", () -> new EnergyBlockItem(HYDRO_PUMP_2.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> HYDRO_PUMP_3_ITEM = ITEMS.register("hydro_pump_3", () -> new EnergyBlockItem(HYDRO_PUMP_3.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> HYDRO_PIPE_ITEM = ITEMS.register("hydro_pipe", () -> new EnergyBlockItem(HYDRO_PIPE.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> HYDRO_TURBINE_ITEM = ITEMS.register("hydro_turbine", () -> new EnergyBlockItem(HYDRO_TURBINE_BLOCK.get(), new net.minecraft.world.item.Item.Properties()));
    public static final DeferredItem<EnergyBlockItem> COPPER_ENERGY_CABLE_ITEM = ITEMS.register("copper_energy_cable", () -> new EnergyBlockItem(COPPER_ENERGY_CABLE.get(), new net.minecraft.world.item.Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL = BLOCK_ENTITIES.register("solar_panel",
            () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL_1.get(), SOLAR_PANEL_2.get(), SOLAR_PANEL_3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY = BLOCK_ENTITIES.register("battery",
            () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, BATTERY_1.get(), BATTERY_2.get(), BATTERY_3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBlockEntity>> CABLE = BLOCK_ENTITIES.register("copper_energy_cable",
            () -> BlockEntityType.Builder.of(CableBlockEntity::new, COPPER_ENERGY_CABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE = BLOCK_ENTITIES.register("wind_turbine",
            () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, WIND_TURBINE_1.get(), WIND_TURBINE_2.get(), WIND_TURBINE_3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydroPumpBlockEntity>> HYDRO_PUMP = BLOCK_ENTITIES.register("hydro_pump",
            () -> BlockEntityType.Builder.of(HydroPumpBlockEntity::new, HYDRO_PUMP_1.get(), HYDRO_PUMP_2.get(), HYDRO_PUMP_3.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydroTurbineBlockEntity>> HYDRO_TURBINE = BLOCK_ENTITIES.register("hydro_turbine",
            () -> BlockEntityType.Builder.of(HydroTurbineBlockEntity::new, HYDRO_TURBINE_BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<HydroPumpMenu>> HYDRO_PUMP_MENU = MENUS.register("hydro_pump",
            () -> IMenuTypeExtension.create(HydroPumpMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<HydroTurbineMenu>> HYDRO_TURBINE_MENU = MENUS.register("hydro_turbine",
            () -> IMenuTypeExtension.create(HydroTurbineMenu::new));
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_PUMP_SOUND = sound("hydro_pump_running");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_TURBINE_SOUND = sound("hydro_turbine_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_FAN_SOUND = sound("hydro_turbine_fan");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_DISCHARGE_SOUND = sound("hydro_discharge");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_START_SOUND = sound("hydro_turbine_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> HYDRO_STOP_SOUND = sound("hydro_turbine_stop");
    public static final DeferredHolder<MenuType<?>, MenuType<WindTurbineMenu>> WIND_MENU = MENUS.register("wind_turbine",
            () -> IMenuTypeExtension.create(WindTurbineMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<SolarPanelMenu>> SOLAR_PANEL_MENU = MENUS.register("solar_panel",
            () -> IMenuTypeExtension.create(SolarPanelMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BatteryMenu>> BATTERY_MENU = MENUS.register("battery",
            () -> IMenuTypeExtension.create(BatteryMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("homelink_energy", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.homelink_energy"))
            .icon(() -> new ItemStack(SOLAR_PANEL_1_ITEM.get()))
            .displayItems((parameters, output) -> creativeItems().forEach(item -> output.accept(item.get())))
            .build());

    private EnergyRegistries() { }

    private static DeferredBlock<WindTurbineBlock> wind(String name, WindTier tier) {
        return BLOCKS.register(name, () -> new WindTurbineBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    }

    private static DeferredBlock<HydroPumpBlock> pump(String name, HydroPumpTier tier) {
        return BLOCKS.register(name, () -> new HydroPumpBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HomeLinkEnergy.id(name)));
    }

    private static DeferredBlock<SolarPanelBlock> solar(String name, SolarTier tier) {
        return BLOCKS.register(name, () -> new SolarPanelBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    }

    private static DeferredBlock<BatteryBlock> battery(String name, BatteryTier tier) {
        return BLOCKS.register(name, () -> new BatteryBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    }

    /** @return items shown in the creative tab, in progression order */
    public static List<Supplier<? extends net.minecraft.world.item.Item>> creativeItems() {
        return List.of(SOLAR_PANEL_1_ITEM, SOLAR_PANEL_2_ITEM, SOLAR_PANEL_3_ITEM, WIND_TURBINE_1_ITEM, WIND_TURBINE_2_ITEM, WIND_TURBINE_3_ITEM,
                HYDRO_PUMP_1_ITEM, HYDRO_PUMP_2_ITEM, HYDRO_PUMP_3_ITEM, HYDRO_PIPE_ITEM, HYDRO_TURBINE_ITEM, BATTERY_1_ITEM, BATTERY_2_ITEM, BATTERY_3_ITEM, COPPER_ENERGY_CABLE_ITEM);
    }

    /** @param bus mod event bus */
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
    }
}

package fr.lkdm.homelink.energy;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.config.EnergyConfig;
import fr.lkdm.homelink.energy.network.NetworkEvents;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

/** HomeLink Energy: renewable HE production, storage and transport for HomeLink machines. */
@Mod(HomeLinkEnergy.MOD_ID)
public final class HomeLinkEnergy {
    public static final String MOD_ID = "homelink_energy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HomeLinkEnergy(IEventBus modBus, ModContainer container) {
        EnergyRegistries.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, EnergyConfig.SPEC);
        modBus.addListener(HomeLinkEnergy::registerCapabilities);
        NeoForge.EVENT_BUS.register(NetworkEvents.class);
        NeoForge.EVENT_BUS.register(fr.lkdm.homelink.energy.wind.WindClearance.class);
        NeoForge.EVENT_BUS.register(fr.lkdm.homelink.energy.wind.WindCommands.class);
        modBus.addListener(fr.lkdm.homelink.energy.network.EnergyPayloads::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(fr.lkdm.homelink.energy.homelink.EnergyHomeCore::registerProviders));
    }

    /** @param path path in the mod namespace
     *  @return namespaced identifier */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(HeCapabilities.PORT, (level, pos, state, entity, side) -> {
            var panel = fr.lkdm.homelink.energy.block.SolarPanelBlock.controller(level, pos, state);
            return panel == null ? null : panel.port(side);
        }, EnergyRegistries.SOLAR_PANEL_1.get(), EnergyRegistries.SOLAR_PANEL_2.get(), EnergyRegistries.SOLAR_PANEL_3.get());
        event.registerBlock(HeCapabilities.PORT, (level, pos, state, entity, side) -> {
            var battery = fr.lkdm.homelink.energy.block.BatteryBlock.controller(level, pos, state);
            return battery == null ? null : battery.port(side);
        }, EnergyRegistries.BATTERY_1.get(), EnergyRegistries.BATTERY_2.get(), EnergyRegistries.BATTERY_3.get());
        event.registerBlock(HeCapabilities.PORT, (level, pos, state, entity, side) -> {
            var turbine = fr.lkdm.homelink.energy.block.WindTurbineBlock.controller(level, pos, state);
            return turbine == null ? null : turbine.port(side);
        }, EnergyRegistries.WIND_TURBINE_1.get(), EnergyRegistries.WIND_TURBINE_2.get(), EnergyRegistries.WIND_TURBINE_3.get());
    }
}

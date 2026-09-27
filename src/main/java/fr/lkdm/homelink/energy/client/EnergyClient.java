package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registrations. */
@EventBusSubscriber(modid = HomeLinkEnergy.MOD_ID, value = Dist.CLIENT)
public final class EnergyClient {
    private EnergyClient() { }

    @SubscribeEvent
    public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EnergyRegistries.CABLE.get(), CableRenderer::new);
        event.registerBlockEntityRenderer(EnergyRegistries.WIND_TURBINE.get(), WindTurbineRenderer::new);
    }

    @SubscribeEvent
    public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(CableRenderer.TRACE);
        event.register(CableRenderer.CORNER);
        event.register(WindTurbineRenderer.TOWER); event.register(WindTurbineRenderer.NACELLE);
        event.register(WindTurbineRenderer.BLADE); event.register(WindTurbineRenderer.HUB);
        event.register(WindTurbineRenderer.NACELLE_2); event.register(WindTurbineRenderer.NACELLE_3);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(EnergyRegistries.SOLAR_PANEL_MENU.get(), SolarPanelScreen::new);
        event.register(EnergyRegistries.BATTERY_MENU.get(), BatteryScreen::new);
        event.register(EnergyRegistries.WIND_MENU.get(), WindTurbineScreen::new);
    }
}

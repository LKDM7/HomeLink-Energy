package fr.lkdm.homelink.energy.network;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Drives the energy networks from the server level ticks (game event bus). */
public final class NetworkEvents {
    private NetworkEvents() { }

    @SubscribeEvent
    public static void wind(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level)
            fr.lkdm.homelink.energy.wind.WindSavedData.get(level).tick();
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) EnergyNetworks.existing(level).ifPresent(EnergyNetworks::tick);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) EnergyNetworks.unload(level);
    }
}

package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.metric.Unit;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Sided HE capability, shared through HomeCore so machines of other mods can plug in. No FE conversion. */
public final class HeCapabilities {
    public static final Unit HE = new Unit(HomeLinkEnergy.id("he"), "HE");
    public static final Unit HE_PER_TICK = new Unit(HomeLinkEnergy.id("he_per_tick"), "HE/t");
    public static final BlockCapability<EnergyPort, Direction> PORT = EnergyApi.BLOCK;
    private HeCapabilities() { }
}

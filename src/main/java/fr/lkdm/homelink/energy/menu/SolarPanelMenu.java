package fr.lkdm.homelink.energy.menu;

import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Solar panel information screen. */
public final class SolarPanelMenu extends EnergyMenu {
    public static final int STATUS = 0, TIER = 1, SKY = 2, EFFICIENCY = 3, PER_CYCLE = 4, RATE_MILLI = 5,
            BUFFER = 6, BUFFER_CAPACITY = 7, TODAY = 8, NETWORK = 9;
    public static final int VALUES = 10;

    /** Client constructor. */
    public SolarPanelMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(EnergyRegistries.SOLAR_PANEL_MENU.get(), id, buffer.readBlockPos(), new MenuData(VALUES), null);
    }

    /** Server constructor. */
    public SolarPanelMenu(int id, Inventory inventory, SolarPanelBlockEntity panel, ContainerData data) {
        super(EnergyRegistries.SOLAR_PANEL_MENU.get(), id, panel.getBlockPos(), data, panel);
    }
}

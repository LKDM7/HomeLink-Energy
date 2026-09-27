package fr.lkdm.homelink.energy.menu;

import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Battery information screen. */
public final class BatteryMenu extends EnergyMenu {
    public static final int TIER = 0, STORED = 1, CAPACITY = 2, INPUT_CENTI = 3, OUTPUT_CENTI = 4, RATE_LIMIT = 5, NETWORK = 6;
    public static final int COMPLETE = 7;
    public static final int VALUES = 8;

    /** Client constructor. */
    public BatteryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(EnergyRegistries.BATTERY_MENU.get(), id, buffer.readBlockPos(), new MenuData(VALUES), null);
    }

    /** Server constructor. */
    public BatteryMenu(int id, Inventory inventory, BatteryBlockEntity battery, ContainerData data) {
        super(EnergyRegistries.BATTERY_MENU.get(), id, battery.getBlockPos(), data, battery);
    }
}

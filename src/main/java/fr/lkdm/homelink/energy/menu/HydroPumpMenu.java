package fr.lkdm.homelink.energy.menu;

import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Read-only pump snapshot, including the server-computed water window used by the preview. */
public final class HydroPumpMenu extends EnergyMenu {
    public static final int STATUS = 0, TIER = 1, POWERED = 2, AVAILABILITY = 3, SOURCES = 4, REQUIRED = 5, AVAILABLE_FLOW = 6,
            ALLOCATED_FLOW = 7, MAX_FLOW = 8, HYDRAULIC = 9, HAS_TURBINE = 10, TURBINE_X = 11, TURBINE_Y = 12, TURBINE_Z = 13,
            MIN_X = 14, MIN_Y = 15, MIN_Z = 16, MAX_X = 17, MAX_Y = 18, MAX_Z = 19;
    public static final int VALUES = 20;

    public HydroPumpMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(EnergyRegistries.HYDRO_PUMP_MENU.get(), id, buffer.readBlockPos(), new MenuData(VALUES), null);
    }

    public HydroPumpMenu(int id, Inventory inventory, HydroPumpBlockEntity pump, ContainerData data) {
        super(EnergyRegistries.HYDRO_PUMP_MENU.get(), id, pump.getBlockPos(), data, pump);
    }
}

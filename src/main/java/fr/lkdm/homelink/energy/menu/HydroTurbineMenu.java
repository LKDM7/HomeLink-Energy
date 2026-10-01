package fr.lkdm.homelink.energy.menu;

import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Read-only turbine snapshot, including the server-validated first discharge obstruction. */
public final class HydroTurbineMenu extends EnergyMenu {
    public static final int STATUS = 0, POWERED = 1, AVAILABLE_FLOW = 2, USED_FLOW = 3, MAX_FLOW = 4, PUMPS = 5, HYDRAULIC = 6,
            OUTLET = 7, POTENTIAL = 8, DELIVERED = 9, PERIOD = 10, OBSERVED = 11, BUFFER = 12, CAPACITY = 13, LOST = 14,
            NETWORK = 15, LIMITED = 16, OBSTRUCTION = 17, OB_X = 18, OB_Y = 19, OB_Z = 20, DIRECT = 21;
    public static final int VALUES = 22;

    public HydroTurbineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(EnergyRegistries.HYDRO_TURBINE_MENU.get(), id, buffer.readBlockPos(), new MenuData(VALUES), null);
    }

    public HydroTurbineMenu(int id, Inventory inventory, HydroTurbineBlockEntity turbine, ContainerData data) {
        super(EnergyRegistries.HYDRO_TURBINE_MENU.get(), id, turbine.getBlockPos(), data, turbine);
    }
}

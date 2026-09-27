package fr.lkdm.homelink.energy.menu;

import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;

/** Read-only server snapshot, including the server-validated first obstacle. */
public final class WindTurbineMenu extends EnergyMenu {
    public static final int STATUS=0,TIER=1,WIND=2,TREND=3,WEATHER=4,WEATHER_MULT=5,ALTITUDE=6,HEIGHT_MULT=7,
            EFFICIENCY=8,ROTOR=9,SKY=10,RATE=11,PERIOD=12,BUFFER=13,CAPACITY=14,NETWORK=15,NOMINAL=16,
            OBSTRUCTION=17,OB_X=18,OB_Y=19,OB_Z=20;
    public static final int VALUES=21;
    public WindTurbineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(EnergyRegistries.WIND_MENU.get(),id,buf.readBlockPos(),new MenuData(VALUES),null);
    }
    public WindTurbineMenu(int id,Inventory inv,WindTurbineBlockEntity turbine,ContainerData data) {
        super(EnergyRegistries.WIND_MENU.get(),id,turbine.getBlockPos(),data,turbine);
    }
}

package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.block.Formats;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.menu.WindTurbineMenu;
import fr.lkdm.homelink.energy.wind.*;
import fr.lkdm.homelink.energy.energy.Weather;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.BlockPos;

/** Compact local telemetry with temporary world outlines. Values come exclusively from the menu. */
public final class WindTurbineScreen extends EnergyScreen<WindTurbineMenu> {
    private net.minecraft.client.gui.components.Button locate;
    public WindTurbineScreen(WindTurbineMenu menu,Inventory inventory,Component title) { super(menu,inventory,title,286); }
    @Override protected void init() {
        super.init();
        addRenderableWidget(EnergyButton.builder(tr("rotor_area"),b->show(false)).bounds(leftPos+10,topPos+235,138,18).build());
        locate=addRenderableWidget(EnergyButton.builder(tr("locate_obstruction"),b->show(true)).bounds(leftPos+154,topPos+235,140,18).build());
    }
    private void show(boolean obstruction) {
        if(minecraft==null || minecraft.level==null || !(minecraft.level.getBlockEntity(menu.pos()) instanceof WindTurbineBlockEntity turbine)) return;
        BlockPos obstacle=menu.value(WindTurbineMenu.OBSTRUCTION)==0?null:new BlockPos(menu.value(WindTurbineMenu.OB_X),menu.value(WindTurbineMenu.OB_Y),menu.value(WindTurbineMenu.OB_Z));
        WindOverlay.show(turbine,obstruction?obstacle:null);
        onClose();
    }
    @Override public void containerTick() { super.containerTick(); if(locate!=null) locate.active=menu.value(WindTurbineMenu.OBSTRUCTION)!=0; }
    private static Component tr(String key) { return Component.translatable("gui.homelink_energy."+key); }
    private static Component literal(String value) { return Component.literal(value); }
    private WindStatus status() { return WindStatus.values()[Math.clamp(menu.value(WindTurbineMenu.STATUS),0,WindStatus.values().length-1)]; }
    private int statusColor() { return status()==WindStatus.GENERATING?GOOD:status()==WindStatus.NO_WIND?LABEL:WARN; }
    @Override protected void renderLines(GuiGraphics g) {
        row(g,tr("status"),Component.translatable("status.homelink_energy.wind."+status().name().toLowerCase(Locale.ROOT)),statusColor());
        row(g,tr("wind"),literal(Formats.rate(menu.value(WindTurbineMenu.WIND)/10.)+" %"),TEXT);
        var trend=WindState.Trend.values()[Math.clamp(menu.value(WindTurbineMenu.TREND),0,2)];
        row(g,tr("trend"),tr("wind_trend."+trend.name().toLowerCase(Locale.ROOT)),TEXT);
        var weather=Weather.values()[Math.clamp(menu.value(WindTurbineMenu.WEATHER),0,2)];
        row(g,tr("wind_weather"),tr("wind_weather."+weather.name().toLowerCase(Locale.ROOT)),TEXT);
        row(g,tr("weather_multiplier"),literal("×"+Formats.rate(menu.value(WindTurbineMenu.WEATHER_MULT)/100.)),TEXT);
        row(g,tr("altitude"),literal("Y "+menu.value(WindTurbineMenu.ALTITUDE)+"  /  ×"+Formats.rate(menu.value(WindTurbineMenu.HEIGHT_MULT)/100.)),TEXT);
        row(g,tr("efficiency"),literal(menu.value(WindTurbineMenu.EFFICIENCY)+" %"),TEXT);
        row(g,tr("rotor"),tr(menu.value(WindTurbineMenu.ROTOR)==1?"rotor_clear":"rotor_obstructed"),menu.value(WindTurbineMenu.ROTOR)==1?GOOD:WARN);
        row(g,tr("exposure"),tr(menu.value(WindTurbineMenu.SKY)==1?"sky_visible":"sky_blocked"),TEXT);
        row(g,tr("wind_current"),literal(Formats.rate(menu.value(WindTurbineMenu.RATE)/1000.)+" HE/t"),TEXT);
        row(g,tr("wind_nominal"),literal(Formats.energy(menu.value(WindTurbineMenu.NOMINAL))+" HE / 24 000 t"),TEXT);
        row(g,tr("this_period"),literal(Formats.energy(menu.value(WindTurbineMenu.PERIOD))+" HE"),TEXT);
        row(g,tr("buffer"),literal(Formats.energy(menu.value(WindTurbineMenu.BUFFER))+" / "+Formats.energy(menu.value(WindTurbineMenu.CAPACITY))+" HE"),TEXT);
        network(g,menu.value(WindTurbineMenu.NETWORK));
    }
}

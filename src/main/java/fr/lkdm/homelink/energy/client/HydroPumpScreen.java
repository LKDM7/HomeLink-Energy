package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.block.Formats;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import fr.lkdm.homelink.energy.menu.HydroPumpMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;

/** Pump telemetry. Values come exclusively from the menu; the water zone preview is the server window. */
public final class HydroPumpScreen extends EnergyScreen<HydroPumpMenu> {
    public HydroPumpScreen(HydroPumpMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 262); }

    @Override protected void init() {
        super.init();
        addRenderableWidget(EnergyButton.builder(tr("hydro_show_water"), b -> showWindow()).bounds(leftPos + 10, topPos + 211, imageWidth - 20, 18).build());
    }

    private void showWindow() {
        var min = new BlockPos(menu.value(HydroPumpMenu.MIN_X), menu.value(HydroPumpMenu.MIN_Y), menu.value(HydroPumpMenu.MIN_Z));
        var max = new BlockPos(menu.value(HydroPumpMenu.MAX_X), menu.value(HydroPumpMenu.MAX_Y), menu.value(HydroPumpMenu.MAX_Z));
        HydroOverlay.show(menu.pos(), new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1), null);
        onClose();
    }

    static Component tr(String key, Object... args) { return Component.translatable("gui.homelink_energy." + key, args); }
    private static Component literal(String value) { return Component.literal(value); }
    static String flow(int milli) { return Formats.rate(milli / 1000.0); }

    private HydroStatus.Pump status() {
        return HydroStatus.Pump.values()[Math.clamp(menu.value(HydroPumpMenu.STATUS), 0, HydroStatus.Pump.values().length - 1)];
    }

    private int statusColor(HydroStatus.Pump status) {
        return switch (status) {
            case PUMPING -> GOOD;
            case STANDBY, DISABLED, NETWORK_PENDING, CONFIG_DISABLED -> LABEL;
            default -> WARN;
        };
    }

    @Override protected void renderLines(GuiGraphics g) {
        HydroStatus.Pump status = status();
        row(g, tr("status"), Component.translatable("status.homelink_energy.hydro_pump." + status.name().toLowerCase(Locale.ROOT)), statusColor(status));
        row(g, tr("hydro_level"), literal(String.valueOf(menu.value(HydroPumpMenu.TIER))), TEXT);
        boolean on = menu.value(HydroPumpMenu.POWERED) == 1;
        row(g, tr("hydro_capture"), tr(on ? "hydro_enabled" : "hydro_disabled"), on ? GOOD : LABEL);
        double availability = menu.value(HydroPumpMenu.AVAILABILITY) / 1000.0;
        row(g, tr("hydro_water"), literal(Formats.rate(availability * 100) + " %"), TEXT);
        bar(g, availability, availability >= 0.25 ? 0xFF6FA8B8 : WARN);
        row(g, tr("hydro_sources"), tr("hydro_sources_value", menu.value(HydroPumpMenu.SOURCES), menu.value(HydroPumpMenu.REQUIRED)), TEXT);
        row(g, tr("hydro_available_flow"), literal(flow(menu.value(HydroPumpMenu.AVAILABLE_FLOW)) + " / " + flow(menu.value(HydroPumpMenu.MAX_FLOW)) + " DH/t"), TEXT);
        row(g, tr("hydro_used_flow"), literal(flow(menu.value(HydroPumpMenu.ALLOCATED_FLOW)) + " DH/t"), TEXT);
        boolean hydraulic = menu.value(HydroPumpMenu.HYDRAULIC) == 1;
        row(g, tr("hydro_circuit"), tr(hydraulic ? "hydro_connected" : "hydro_not_connected"), hydraulic ? GOOD : LABEL);
        Component turbine = menu.value(HydroPumpMenu.HAS_TURBINE) == 1
                ? literal(menu.value(HydroPumpMenu.TURBINE_X) + ", " + menu.value(HydroPumpMenu.TURBINE_Y) + ", " + menu.value(HydroPumpMenu.TURBINE_Z))
                : tr("hydro_none");
        row(g, tr("hydro_turbine"), turbine, TEXT);
        row(g, tr("hydro_help_label"), tr("hydro_pump_help"), LABEL);
    }
}

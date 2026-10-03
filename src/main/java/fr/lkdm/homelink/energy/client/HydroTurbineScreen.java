package fr.lkdm.homelink.energy.client;

import static fr.lkdm.homelink.energy.client.HydroPumpScreen.flow;
import static fr.lkdm.homelink.energy.client.HydroPumpScreen.tr;

import fr.lkdm.homelink.energy.block.Formats;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import fr.lkdm.homelink.energy.menu.HydroTurbineMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Turbine telemetry. The three networks stay distinct: hydraulic circuit, HE cable network and the
 * HomeNetwork button. Potential generation and HE really delivered are shown separately.
 */
public final class HydroTurbineScreen extends EnergyScreen<HydroTurbineMenu> {
    private Button locate;

    public HydroTurbineScreen(HydroTurbineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title, 300); }
    @Override protected boolean hasActionRow() { return true; }

    @Override protected void init() {
        super.init();
        locate = null;
        if (!canShowActions()) return;
        locate = addRenderableWidget(HomeLinkButton.builder(tr("locate_obstruction"), b -> {
            if (menu.value(HydroTurbineMenu.OBSTRUCTION) == 0) return;
            var pos = new BlockPos(menu.value(HydroTurbineMenu.OB_X), menu.value(HydroTurbineMenu.OB_Y), menu.value(HydroTurbineMenu.OB_Z));
            HydroOverlay.show(menu.pos(), null, pos);
            onClose();
        }).bounds(leftPos + HomeLinkTheme.CONTENT_PADDING, actionY(), imageWidth - 2 * HomeLinkTheme.CONTENT_PADDING,
                HomeLinkTheme.CONTROL_HEIGHT).build());
    }

    @Override public void containerTick() {
        super.containerTick();
        if (locate != null) locate.active = menu.value(HydroTurbineMenu.OBSTRUCTION) != 0;
    }

    private HydroStatus.Turbine status() {
        return HydroStatus.Turbine.values()[Math.clamp(menu.value(HydroTurbineMenu.STATUS), 0, HydroStatus.Turbine.values().length - 1)];
    }

    private static int color(HydroStatus.Turbine status) {
        return switch (status) {
            case GENERATING -> GOOD;
            case DISABLED, NO_FLOW, NETWORK_PENDING, CONFIG_DISABLED -> LABEL;
            default -> WARN;
        };
    }

    @Override protected void renderLines(GuiGraphics g) {
        HydroStatus.Turbine status = status();
        row(g, tr("status"), Component.translatable("status.homelink_energy.hydro_turbine." + status.name().toLowerCase(Locale.ROOT)), color(status));
        boolean on = menu.value(HydroTurbineMenu.POWERED) == 1;
        row(g, tr("hydro_switch"), tr(on ? "hydro_enabled" : "hydro_disabled"), on ? GOOD : LABEL);
        int used = menu.value(HydroTurbineMenu.USED_FLOW), max = Math.max(1, menu.value(HydroTurbineMenu.MAX_FLOW));
        row(g, tr("hydro_flow"), Component.literal(flow(used) + " / " + flow(max) + " DH/t"), TEXT);
        bar(g, used / (double) max, 0xFF6FA8B8);
        boolean limited = menu.value(HydroTurbineMenu.LIMITED) == 1;
        row(g, tr("hydro_available_flow"), limited ? tr("hydro_flow_limited", flow(menu.value(HydroTurbineMenu.AVAILABLE_FLOW)))
                : Component.literal(flow(menu.value(HydroTurbineMenu.AVAILABLE_FLOW)) + " DH/t"), limited ? WARN : TEXT);
        row(g, tr("hydro_pumps"), Component.literal(String.valueOf(menu.value(HydroTurbineMenu.PUMPS))), TEXT);
        boolean circuitOk = !status.circuitFault() && status != HydroStatus.Turbine.NETWORK_PENDING;
        boolean hydraulic = menu.value(HydroTurbineMenu.HYDRAULIC) == 1;
        row(g, tr("hydro_circuit"), tr(!hydraulic ? "hydro_not_connected" : circuitOk ? "hydro_connected" : "hydro_circuit_invalid"),
                !hydraulic ? LABEL : circuitOk ? GOOD : WARN);
        boolean outlet = menu.value(HydroTurbineMenu.OUTLET) == 1;
        row(g, tr("hydro_outlet"), tr(outlet ? "hydro_outlet_clear" : "hydro_outlet_blocked"), outlet ? GOOD : WARN);
        row(g, tr("hydro_potential"), Component.literal(Formats.rate(menu.value(HydroTurbineMenu.POTENTIAL) / 10_000.0) + " HE/t"), TEXT);
        row(g, tr("hydro_delivered"), Component.literal(Formats.rate(menu.value(HydroTurbineMenu.DELIVERED) / 10_000.0) + " HE/t"), TEXT);
        int observed = menu.value(HydroTurbineMenu.OBSERVED);
        row(g, tr("this_period"), observed >= 24_000
                ? Component.literal(Formats.energy(menu.value(HydroTurbineMenu.PERIOD)) + " HE")
                : tr("hydro_period_partial", Formats.energy(menu.value(HydroTurbineMenu.PERIOD)), Formats.energy(observed)), TEXT);
        row(g, tr("buffer"), Component.literal(Formats.energy(menu.value(HydroTurbineMenu.BUFFER)) + " / "
                + Formats.energy(menu.value(HydroTurbineMenu.CAPACITY)) + " HE"), TEXT);
        int lost = menu.value(HydroTurbineMenu.LOST);
        row(g, tr("hydro_lost"), Component.literal(Formats.energy(lost) + " HE"), lost > 0 ? WARN : TEXT);
        var states = fr.lkdm.homelink.energy.network.EnergyNetworks.Connection.values();
        var electric = states[Math.clamp(menu.value(HydroTurbineMenu.NETWORK), 0, states.length - 1)];
        boolean direct = electric == fr.lkdm.homelink.energy.network.EnergyNetworks.Connection.NONE && menu.value(HydroTurbineMenu.DIRECT) == 1;
        row(g, tr("hydro_electric"), direct ? tr("hydro_direct") : tr("network." + electric.name().toLowerCase(Locale.ROOT)),
                direct ? GOOD : switch (electric) { case CONNECTED -> GOOD; case NONE -> LABEL; case NETWORK_TOO_LARGE -> BAD; });
        row(g, tr("hydro_help_label"), tr("hydro_turbine_help"), LABEL);
    }
}

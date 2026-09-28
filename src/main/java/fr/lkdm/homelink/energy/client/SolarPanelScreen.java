package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.block.Formats;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.menu.SolarPanelMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Solar panel screen: level, state, rated output, current flow, weather, exposure and buffer. */
public final class SolarPanelScreen extends EnergyScreen<SolarPanelMenu> {
    public SolarPanelScreen(SolarPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 224);
    }

    @Override
    protected void renderLines(GuiGraphics graphics) {
        SolarStatus[] states = SolarStatus.values();
        int ordinal = menu.value(SolarPanelMenu.STATUS);
        SolarStatus status = ordinal >= 0 && ordinal < states.length ? states[ordinal] : SolarStatus.NIGHT;
        int color = switch (status) {
            case GENERATING -> GOOD;
            case NIGHT, BUFFER_FULL -> WARN;
            default -> BAD;
        };
        row(graphics, Component.translatable("gui.homelink_energy.level"), Component.literal(roman(menu.value(SolarPanelMenu.TIER))), TEXT);
        int tier = menu.value(SolarPanelMenu.TIER);
        row(graphics, Component.translatable("gui.homelink_energy.footprint"), Component.literal(tier == 3 ? "2 × 2" : tier == 2 ? "2 × 1" : "1 × 1"), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.status"),
                Component.translatable("status.homelink_energy.solar." + status.name().toLowerCase(Locale.ROOT)), color);
        row(graphics, Component.translatable("gui.homelink_energy.nominal"),
                Component.translatable("gui.homelink_energy.he_per_cycle", Formats.energy(menu.value(SolarPanelMenu.PER_CYCLE))), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.current"),
                Component.translatable("gui.homelink_energy.he_per_tick", Formats.rate(menu.value(SolarPanelMenu.RATE_MILLI) / 1000.0)), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.weather"),
                Component.literal(menu.value(SolarPanelMenu.EFFICIENCY) + " %"), TEXT);
        boolean sky = menu.value(SolarPanelMenu.SKY) != 0;
        row(graphics, Component.translatable("gui.homelink_energy.exposure"),
                Component.translatable(sky ? "gui.homelink_energy.sky_visible" : "gui.homelink_energy.sky_blocked"), sky ? GOOD : BAD);
        int buffer = menu.value(SolarPanelMenu.BUFFER), capacity = menu.value(SolarPanelMenu.BUFFER_CAPACITY);
        row(graphics, Component.translatable("gui.homelink_energy.buffer"),
                Component.translatable("gui.homelink_energy.he_of", Formats.energy(buffer), Formats.energy(capacity)), TEXT);
        bar(graphics, capacity <= 0 ? 0 : buffer / (double) capacity, COPPER);
        row(graphics, Component.translatable("gui.homelink_energy.today"),
                Component.translatable("gui.homelink_energy.he", Formats.energy(menu.value(SolarPanelMenu.TODAY))), TEXT);
        network(graphics, menu.value(SolarPanelMenu.NETWORK));
    }

    static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(level);
        };
    }
}

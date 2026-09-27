package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.block.Formats;
import fr.lkdm.homelink.energy.menu.BatteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Battery screen: stored energy, capacity, charge gauge and flows. */
public final class BatteryScreen extends EnergyScreen<BatteryMenu> {
    public BatteryScreen(BatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 210);
    }

    @Override
    protected int headerColor() {
        if(menu.value(BatteryMenu.COMPLETE)==0) return WARN;
        long stored = menu.value(BatteryMenu.STORED) & 0xFFFFFFFFL;
        long capacity = menu.value(BatteryMenu.CAPACITY) & 0xFFFFFFFFL;
        return capacity <= 0 || stored <= capacity * 0.15 ? BAD : stored < capacity * 0.5 ? WARN : GOOD;
    }

    @Override
    protected void renderLines(GuiGraphics graphics) {
        row(graphics,Component.translatable("gui.homelink_energy.status"),Component.translatable(menu.value(BatteryMenu.COMPLETE)==1
                ? "status.homelink_energy.battery.ready" : "status.homelink_energy.battery.incomplete_base"),menu.value(BatteryMenu.COMPLETE)==1?GOOD:WARN);
        long stored = menu.value(BatteryMenu.STORED) & 0xFFFFFFFFL;
        long capacity = menu.value(BatteryMenu.CAPACITY) & 0xFFFFFFFFL;
        double fraction = capacity <= 0 ? 0 : stored / (double) capacity;
        int percent = (int) Math.floor(fraction * 100);
        int color = percent <= 15 ? BAD : percent < 50 ? WARN : GOOD;
        row(graphics, Component.translatable("gui.homelink_energy.stored"), Component.translatable("gui.homelink_energy.he", Formats.energy(stored)), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.capacity"), Component.translatable("gui.homelink_energy.he", Formats.energy(capacity)), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.charge"), Component.literal(percent + " %"), color);
        bar(graphics, fraction, color);
        row(graphics, Component.translatable("gui.homelink_energy.input"),
                Component.translatable("gui.homelink_energy.he_per_tick", "+" + Formats.rate(menu.value(BatteryMenu.INPUT_CENTI) / 100.0)), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.output"),
                Component.translatable("gui.homelink_energy.he_per_tick", "-" + Formats.rate(menu.value(BatteryMenu.OUTPUT_CENTI) / 100.0)), TEXT);
        row(graphics, Component.translatable("gui.homelink_energy.rate_limit"),
                Component.translatable("gui.homelink_energy.he_per_tick", Formats.energy(menu.value(BatteryMenu.RATE_LIMIT))), LABEL);
        network(graphics, menu.value(BatteryMenu.NETWORK));
    }
}

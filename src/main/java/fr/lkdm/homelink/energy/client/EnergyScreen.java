package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.menu.EnergyMenu;
import fr.lkdm.homelink.energy.network.EnergyPayloads;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.components.Button;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** HomeLink Farm visual language: metallic frame, recessed surfaces and warm beige accents. */
public abstract class EnergyScreen<M extends EnergyMenu> extends AbstractContainerScreen<M> {
    static final int COPPER = EnergyTheme.ACCENT;
    static final int TEXT = EnergyTheme.TEXT;
    static final int LABEL = EnergyTheme.MUTED;
    static final int GOOD = EnergyTheme.ONLINE;
    static final int WARN = EnergyTheme.WARNING;
    static final int BAD = EnergyTheme.OFFLINE;
    static final int LINE = 12;

    private int line;
    private Button networkButton;

    protected EnergyScreen(M menu, Inventory inventory, Component title, int height) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = height;
    }

    @Override
    protected void init() {
        super.init();
        networkButton = addRenderableWidget(EnergyButton.builder(networkLabel(), button -> cycleNetwork())
                .bounds(leftPos + 10, topPos + imageHeight - 26, imageWidth - 20, 18).build());
    }

    private Component networkLabel() {
        String current = EnergyClientData.choices(menu.pos()).current();
        return Component.translatable("gui.homelink_energy.home_network",
                current.isEmpty() ? Component.translatable("gui.homelink_energy.home_network.none") : Component.literal(current));
    }

    /** Asks the server to move the block to the next HomeNetwork the player manages (then none). */
    private void cycleNetwork() {
        var data = EnergyClientData.choices(menu.pos());
        var choices = data.choices();
        if (choices.isEmpty() && data.current().isEmpty()) {
            if (minecraft != null && minecraft.player != null)
                minecraft.player.displayClientMessage(Component.translatable("message.homelink_energy.network.no_choices"), true);
            return;
        }
        int index = -1;
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).name().equals(data.current())) index = i;
        Optional<UUID> next = index + 1 < choices.size() ? Optional.of(choices.get(index + 1).id()) : Optional.empty();
        PacketDistributor.sendToServer(new EnergyPayloads.Bind(menu.pos(), next));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (networkButton != null) networkButton.setMessage(networkLabel());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        EnergyTheme.window(graphics, leftPos, topPos, imageWidth, imageHeight, 24);
        EnergyTheme.panel(graphics, leftPos + 10, topPos + 45, imageWidth - 20, imageHeight - 80);
        EnergyTheme.divider(graphics, leftPos + 10, topPos + imageHeight - 31, imageWidth - 20);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header status: whether the block is linked to a HomeNetwork, as on the HomeLink Farm screens.
        boolean linked = !EnergyClientData.choices(menu.pos()).current().isEmpty();
        String status = font.plainSubstrByWidth(Component.translatable(linked
                ? "gui.homelink_energy.linked" : "gui.homelink_energy.not_linked").getString(), 90);
        int statusX = imageWidth - 16 - font.width(status);
        EnergyTheme.statusLight(graphics, statusX - 12, 8, linked ? GOOD : BAD);
        graphics.drawString(font, status, statusX, 8, LABEL, false);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), statusX - 30), 14, 8, TEXT, false);
        graphics.drawString(font, "HOMELINK / ENERGY", 14, 31, COPPER, false);
        line = 51;
        renderLines(graphics);
    }

    /** Draws the body lines, in order, with {@link #row} and {@link #bar}. */
    protected abstract void renderLines(GuiGraphics graphics);

    /** Draws a "label: value" row. */
    protected void row(GuiGraphics graphics, Component label, Component value, int valueColor) {
        int valueX = Math.max(16 + font.width(label) + 8, imageWidth - 16 - font.width(value));
        String visible = font.plainSubstrByWidth(value.getString(), Math.max(0, imageWidth - 16 - valueX));
        graphics.drawString(font, label, 16, line, LABEL, false);
        graphics.drawString(font, visible, valueX, line, valueColor, false);
        if (!visible.equals(value.getString()) && mouseOverRow(mouseXForTooltip, mouseYForTooltip, line))
            setTooltipForNextRenderPass(value);
        line += LINE;
    }

    /** Draws a horizontal gauge. */
    protected void bar(GuiGraphics graphics, double fraction, int color) {
        EnergyTheme.gauge(graphics, 16, line + 3, imageWidth - 32, (float) fraction, color);
        line += LINE;
    }

    /** Draws the cable network connection row. */
    protected void network(GuiGraphics graphics, int ordinal) {
        var states = fr.lkdm.homelink.energy.network.EnergyNetworks.Connection.values();
        var state = ordinal >= 0 && ordinal < states.length ? states[ordinal] : states[0];
        int color = switch (state) { case CONNECTED -> GOOD; case NONE -> LABEL; case NETWORK_TOO_LARGE -> BAD; };
        row(graphics, Component.translatable("gui.homelink_energy.network"),
                Component.translatable("gui.homelink_energy.network." + state.name().toLowerCase(java.util.Locale.ROOT)), color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        mouseXForTooltip = mouseX; mouseYForTooltip = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    private int mouseXForTooltip, mouseYForTooltip;
    private boolean mouseOverRow(int x, int y, int row) {
        return x >= leftPos + 10 && x < leftPos + imageWidth - 10 && y >= topPos + row && y < topPos + row + LINE;
    }
}

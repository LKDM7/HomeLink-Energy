package fr.lkdm.homelink.energy.client;

import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkScreenLayout;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
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
import org.lwjgl.glfw.GLFW;

/** Read-only telemetry using the shared HomeCore chrome and controls. */
public abstract class EnergyScreen<M extends EnergyMenu> extends AbstractContainerScreen<M> {
    static final int COPPER = HomeLinkTheme.ACCENT;
    static final int TEXT = HomeLinkTheme.TEXT;
    static final int LABEL = HomeLinkTheme.MUTED;
    static final int GOOD = HomeLinkTheme.ONLINE;
    static final int WARN = HomeLinkTheme.WARNING;
    static final int BAD = HomeLinkTheme.OFFLINE;
    static final int LINE = 12;
    private static final int BODY_TOP = HomeLinkTheme.HEADER_HEIGHT + HomeLinkTheme.CONTENT_PADDING + 10;

    private int line;
    private Button networkButton;
    private final int preferredHeight;
    private int uiScroll, maxScroll;

    protected EnergyScreen(M menu, Inventory inventory, Component title, int height) {
        super(menu, inventory, title);
        preferredHeight = height;
        imageWidth = 304;
        imageHeight = height;
    }

    @Override
    protected void init() {
        var layout = HomeLinkScreenLayout.fit(width, height, 304, preferredHeight);
        imageWidth = layout.width();
        imageHeight = layout.height();
        super.init();
        networkButton = null;
        if (imageWidth >= 32 && imageHeight >= HomeLinkTheme.HEADER_HEIGHT + HomeLinkTheme.FOOTER_HEIGHT)
            networkButton = addRenderableWidget(HomeLinkButton.builder(networkLabel(), button -> cycleNetwork())
                    .bounds(leftPos + HomeLinkTheme.CONTENT_PADDING, topPos + imageHeight - 26,
                            imageWidth - 2 * HomeLinkTheme.CONTENT_PADDING, HomeLinkTheme.CONTROL_HEIGHT).build());
    }

    /** Screens with overlay actions reserve one fixed row above the footer. */
    protected boolean hasActionRow() { return false; }

    /** Keep fixed actions out of extremely small viewports. */
    protected boolean canShowActions() { return imageWidth >= 48 && imageHeight >= BODY_TOP + 55; }

    protected int actionY() { return topPos + imageHeight - 51; }

    private int bodyBottom() { return Math.max(BODY_TOP, imageHeight - (hasActionRow() ? 55 : 35)); }

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
        HomeLinkUi.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        HomeLinkUi.panel(graphics, leftPos + HomeLinkTheme.CONTENT_PADDING, topPos + BODY_TOP,
                imageWidth - 2 * HomeLinkTheme.CONTENT_PADDING, Math.max(0, bodyBottom() - BODY_TOP));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (imageWidth < 40 || imageHeight < HomeLinkTheme.HEADER_HEIGHT) return;
        // The header light describes HomeNetwork binding; cable status stays in the body.
        boolean linked = !EnergyClientData.choices(menu.pos()).current().isEmpty();
        String status = HomeLinkUi.clip(font, Component.translatable(linked
                ? "gui.homelink_energy.linked" : "gui.homelink_energy.not_linked").getString(), Math.min(90, imageWidth / 3));
        int statusX = imageWidth - 16 - font.width(status);
        HomeLinkUi.statusDot(graphics, statusX - 12, 10, linked ? GOOD : BAD);
        graphics.drawString(font, status, statusX, 10, LABEL, false);
        graphics.drawString(font, HomeLinkUi.clip(font, title.getString(), Math.max(0, statusX - 30)), 14, 10, TEXT, false);
        if (imageHeight < BODY_TOP) return;
        graphics.drawString(font, HomeLinkUi.clip(font, "HOMELINK / ENERGY", imageWidth - 28), 14, 36, COPPER, false);
        int bottom = Math.min(imageHeight, bodyBottom());
        if (bottom <= BODY_TOP || imageWidth <= 32) return;
        uiScroll = Math.clamp(uiScroll, 0, maxScroll);
        line = BODY_TOP + 6 - uiScroll;
        // GuiGraphics scissor bounds are absolute, even though labels have a translated pose.
        graphics.enableScissor(leftPos + 16, topPos + BODY_TOP + 2, leftPos + imageWidth - 16, topPos + bottom - 2);
        try {
            renderLines(graphics);
        } finally {
            graphics.disableScissor();
        }
        maxScroll = Math.max(0, line + uiScroll + 2 - bottom);
        uiScroll = Math.min(uiScroll, maxScroll);
        if (maxScroll > 0 && bottom - BODY_TOP >= 12) {
            int trackHeight = bottom - BODY_TOP - 8;
            int thumbHeight = Math.min(trackHeight, Math.max(8, trackHeight * trackHeight / (trackHeight + maxScroll)));
            int thumbY = BODY_TOP + 4 + (trackHeight - thumbHeight) * uiScroll / maxScroll;
            graphics.fill(imageWidth - 15, BODY_TOP + 4, imageWidth - 14, bottom - 4, HomeLinkTheme.LINE);
            graphics.fill(imageWidth - 15, thumbY, imageWidth - 14, thumbY + thumbHeight, COPPER);
        }
    }

    /** Draws the body lines, in order, with {@link #row} and {@link #bar}. */
    protected abstract void renderLines(GuiGraphics graphics);

    /** Draws a "label: value" row. */
    protected void row(GuiGraphics graphics, Component label, Component value, int valueColor) {
        int available = Math.max(0, imageWidth - 32);
        String visibleLabel = HomeLinkUi.clip(font, label.getString(), Math.max(0, (available - 8) / 2));
        String visible = HomeLinkUi.clip(font, value.getString(), Math.max(0, available - font.width(visibleLabel) - 8));
        int valueX = imageWidth - 16 - font.width(visible);
        graphics.drawString(font, visibleLabel, 16, line, LABEL, false);
        graphics.drawString(font, visible, valueX, line, valueColor, false);
        if ((!visible.equals(value.getString()) || !visibleLabel.equals(label.getString()))
                && mouseOverRow(mouseXForTooltip, mouseYForTooltip, line))
            setTooltipForNextRenderPass(Component.empty().append(label).append(": ").append(value));
        line += LINE;
    }

    /** Draws a horizontal gauge. */
    protected void bar(GuiGraphics graphics, double fraction, int color) {
        HomeLinkUi.gauge(graphics, 16, line + 3, imageWidth - 32, (float) fraction, color);
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
        return x >= leftPos + 16 && x < leftPos + imageWidth - 16
                && y >= topPos + BODY_TOP + 2 && y < topPos + bodyBottom() - 2
                && y >= topPos + row && y < topPos + row + LINE;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= leftPos + 12 && mouseX < leftPos + imageWidth - 12
                && mouseY >= topPos + BODY_TOP && mouseY < topPos + bodyBottom()
                && scroll((int) Math.round(-vertical * LINE * 3))) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN && scroll(Math.max(LINE, bodyBottom() - BODY_TOP - LINE))) return true;
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP && scroll(-Math.max(LINE, bodyBottom() - BODY_TOP - LINE))) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean scroll(int delta) {
        int previous = uiScroll;
        uiScroll = Math.clamp(uiScroll + delta, 0, maxScroll);
        return uiScroll != previous;
    }
}

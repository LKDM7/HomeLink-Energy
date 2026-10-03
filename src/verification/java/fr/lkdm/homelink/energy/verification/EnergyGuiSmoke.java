package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homecore.api.client.ui.HomeLinkButton;
import fr.lkdm.homecore.api.client.ui.HomeLinkScreenLayout;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.client.BatteryScreen;
import fr.lkdm.homelink.energy.client.EnergyScreen;
import fr.lkdm.homelink.energy.client.HydroPumpScreen;
import fr.lkdm.homelink.energy.client.HydroTurbineScreen;
import fr.lkdm.homelink.energy.client.SolarPanelScreen;
import fr.lkdm.homelink.energy.client.WindTurbineScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

/** Reviews all five real menu snapshots after the existing in-world smoke finishes. */
@EventBusSubscriber(modid = EnergyValidation.MOD_ID, value = Dist.CLIENT)
public final class EnergyGuiSmoke {
    private static final List<String> KINDS = List.of("battery", "solar", "wind", "hydro-pump", "hydro-turbine");
    private static final int[] HEIGHTS = {210, 224, 286, 262, 300};
    private static final int[][] VIEWPORTS = {{1280, 720, 2}, {640, 480, 2}, {1280, 720, 3}};
    private static final Map<String, Function<Inventory, EnergyScreen<?>>> SCREENS = new HashMap<>();
    private static final List<Path> CAPTURES = new ArrayList<>();
    private static boolean active, complete;
    private static int viewport, kind, phase, frames, previousWidth, previousHeight, previousScale;
    private static long started;
    private static Throwable failure;

    private EnergyGuiSmoke() { }

    static void remember(Minecraft client) {
        if (client.screen instanceof BatteryScreen screen)
            SCREENS.put("battery", inventory -> new BatteryScreen(screen.getMenu(), inventory, screen.getTitle()));
        else if (client.screen instanceof SolarPanelScreen screen)
            SCREENS.put("solar", inventory -> new SolarPanelScreen(screen.getMenu(), inventory, screen.getTitle()));
        else if (client.screen instanceof WindTurbineScreen screen)
            SCREENS.put("wind", inventory -> new WindTurbineScreen(screen.getMenu(), inventory, screen.getTitle()));
        else if (client.screen instanceof HydroPumpScreen screen)
            SCREENS.put("hydro-pump", inventory -> new HydroPumpScreen(screen.getMenu(), inventory, screen.getTitle()));
        else if (client.screen instanceof HydroTurbineScreen screen)
            SCREENS.put("hydro-turbine", inventory -> new HydroTurbineScreen(screen.getMenu(), inventory, screen.getTitle()));
    }

    static void begin(Minecraft client) {
        if (!SCREENS.keySet().containsAll(KINDS)) throw new IllegalStateException("Not all real Energy menus were captured: " + SCREENS.keySet());
        previousWidth = client.getWindow().getWidth();
        previousHeight = client.getWindow().getHeight();
        previousScale = client.options.guiScale().get();
        started = System.nanoTime();
        active = true;
        show(client);
    }

    static boolean tick(Minecraft client) throws java.io.IOException {
        if (failure != null) throw new IllegalStateException("Responsive Energy GUI review failed", failure);
        if (System.nanoTime() - started > 120_000_000_000L) throw new IllegalStateException("Responsive GUI review timed out");
        if (!complete) return false;
        for (Path file : CAPTURES)
            if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IllegalStateException("Missing Energy GUI capture: " + file);
        HomeLinkEnergy.LOGGER.info("ENERGY_GUI_SMOKE_OK language={} screens=5 viewports=640x360,320x240,427x240 focus=true bounds=true scroll=true captures={}",
                client.options.languageCode, CAPTURES.size());
        return true;
    }

    private static void show(Minecraft client) {
        int[] size = VIEWPORTS[viewport];
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), size[0], size[1]);
        client.options.guiScale().set(size[2]);
        client.resizeDisplay();
        client.setScreen(SCREENS.get(KINDS.get(kind)).apply(client.player.getInventory()));
        phase = 0;
        frames = 0;
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!active || complete || failure != null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.getOverlay() != null || !(client.screen instanceof EnergyScreen<?> screen)) { frames = 0; return; }
        if (++frames < (phase == 0 ? 10 : 4)) return;
        try {
            if (phase == 0) {
                verify(screen);
                phase = 1;
                frames = 0;
            } else if (phase == 1) {
                capture(client, screen, "top");
                boolean scrolled = screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
                boolean expectsScroll = viewport > 0 && kind >= 2;
                if (expectsScroll && !scrolled) throw new IllegalStateException("Small telemetry body did not scroll: " + KINDS.get(kind));
                if (scrolled) {
                    if (!screen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0)
                            || !screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0))
                        throw new IllegalStateException("Telemetry paging keyboard controls failed");
                    phase = 2;
                    frames = 0;
                } else {
                    advance(client);
                }
            } else {
                capture(client, screen, "scrolled");
                if (!screen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0)) throw new IllegalStateException("Telemetry cannot return to its top");
                advance(client);
            }
        } catch (Throwable problem) {
            failure = problem;
        }
    }

    private static void verify(EnergyScreen<?> screen) {
        List<AbstractWidget> widgets = screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).toList();
        int expected = kind == 2 ? 3 : kind >= 3 ? 2 : 1;
        if (widgets.size() != expected) throw new IllegalStateException("Fixed Energy actions missing: " + KINDS.get(kind));
        for (AbstractWidget widget : widgets) {
            if (!(widget instanceof HomeLinkButton) || widget.getHeight() != HomeLinkTheme.CONTROL_HEIGHT)
                throw new IllegalStateException("Control does not use the official HomeLink button");
            if (widget.getX() < HomeLinkTheme.OUTER_MARGIN || widget.getY() < HomeLinkTheme.OUTER_MARGIN
                    || widget.getX() + widget.getWidth() > screen.width - HomeLinkTheme.OUTER_MARGIN
                    || widget.getY() + widget.getHeight() > screen.height - HomeLinkTheme.OUTER_MARGIN)
                throw new IllegalStateException("Fixed action outside Energy viewport: " + widget.getMessage().getString());
            if (widget.active) {
                screen.setFocused(widget);
                widget.setFocused(true);
                if (!widget.isFocused()) throw new IllegalStateException("Keyboard focus missing");
            }
        }
        screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        if (!(screen.getFocused() instanceof AbstractWidget focused) || !focused.isFocused() || !focused.active)
            throw new IllegalStateException("Native tab traversal failed");
        HomeLinkEnergy.LOGGER.info("ENERGY_GUI_VIEWPORT_OK screen={} width={} height={} controls={} focus=true",
                KINDS.get(kind), screen.width, screen.height, widgets.size());
    }

    private static void capture(Minecraft client, EnergyScreen<?> screen, String state) throws java.io.IOException {
        var layout = HomeLinkScreenLayout.fit(screen.width, screen.height, 304, HEIGHTS[kind]);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            double scale = client.getWindow().getGuiScale();
            int pixelX = (int) Math.floor((layout.x() + 8.5) * scale);
            int pixelY = (int) Math.floor((layout.y() + HomeLinkTheme.HEADER_HEIGHT + 2.5) * scale);
            int argb = HomeLinkTheme.BACKGROUND;
            int expected = (argb & 0xFF00FF00) | ((argb >>> 16) & 0xFF) | ((argb & 0xFF) << 16);
            if ((image.getPixelRGBA(pixelX, pixelY) & 0xFFFFFF) != (expected & 0xFFFFFF))
                throw new IllegalStateException("Official Energy frame not rendered");
            Path file = client.gameDirectory.toPath().resolve("screenshots/energy-ui-" + client.options.languageCode
                    + "-" + viewport + "-" + KINDS.get(kind) + "-" + state + ".png");
            Files.createDirectories(file.getParent());
            image.writeToFile(file);
            CAPTURES.add(file);
        }
    }

    private static void advance(Minecraft client) {
        if (++kind >= KINDS.size()) { kind = 0; viewport++; }
        if (viewport < VIEWPORTS.length) { show(client); return; }
        active = false;
        complete = true;
        client.setScreen(null);
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), previousWidth, previousHeight);
        client.options.guiScale().set(previousScale);
        client.resizeDisplay();
    }
}

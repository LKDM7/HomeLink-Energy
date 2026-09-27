import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Generates the 16x16 block textures of HomeLink Energy.
 * Run from the project root: java scripts/GenerateTextures.java
 * Palette: anthracite casing, light gray/white frames, copper details, very dark blue solar cells.
 */
public final class GenerateTextures {
    static final int ANTHRACITE = 0x2E3136, ANTHRACITE_DARK = 0x222428, ANTHRACITE_LIGHT = 0x3A3E44;
    static final int FRAME = 0xC9CDD2, FRAME_SHADE = 0x9EA3A9, WHITE = 0xE9ECEF;
    static final int COPPER = 0xC7794A, COPPER_DARK = 0x9A5433, COPPER_LIGHT = 0xE19A6A;
    static final int CELL = 0x0F1A33, CELL_ALT = 0x13234A, CELL_LINE = 0x2A3B5E, CELL_SHINE = 0x3B5387;

    static final String OUT = "src/main/resources/assets/homelink_energy/textures/block/";

    public static void main(String[] args) throws IOException {
        new File(OUT).mkdirs();
        for (int tier = 1; tier <= 3; tier++) {
            write("solar_panel_" + tier + "_top", solarTop(tier));
            write("battery_" + tier + "_side", batterySide(tier));
        }
        write("solar_panel_side", solarSide());
        write("solar_panel_bottom", solarBottom());
        write("battery_top", batteryTop());
        write("battery_bottom", batteryBottom());
        write("copper_energy_cable", cable());
        write("copper_energy_cable_core", cableCore());
    }

    static BufferedImage image() { return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }

    static void px(BufferedImage img, int x, int y, int rgb) { img.setRGB(x, y, 0xFF000000 | rgb); }

    static void rect(BufferedImage img, int x0, int y0, int x1, int y1, int rgb) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) px(img, x, y, rgb);
    }

    static void frame(BufferedImage img, int rgb, int shade) {
        for (int i = 0; i < 16; i++) {
            px(img, i, 0, rgb); px(img, 0, i, rgb);
            px(img, i, 15, shade); px(img, 15, i, shade);
        }
    }

    /** Solar surface: a finer cell grid for each tier and one copper mark per tier on the frame. */
    static BufferedImage solarTop(int tier) {
        BufferedImage img = image();
        int[] step = {0, 7, 5, 3};
        rect(img, 1, 1, 14, 14, CELL);
        for (int x = 1; x <= 14; x++) {
            for (int y = 1; y <= 14; y++) {
                int cx = (x - 1) / step[tier], cy = (y - 1) / step[tier];
                if (((cx + cy) & 1) == 1) px(img, x, y, CELL_ALT);
                if ((x - 1) % step[tier] == step[tier] - 1 || (y - 1) % step[tier] == step[tier] - 1) px(img, x, y, CELL_LINE);
            }
        }
        px(img, 2, 2, CELL_SHINE); px(img, 3, 2, CELL_SHINE); px(img, 2, 3, CELL_SHINE);
        frame(img, FRAME, FRAME_SHADE);
        for (int i = 0; i < tier; i++) {
            px(img, 13 - i * 2, 15, COPPER);
            px(img, 13 - i * 2, 14, COPPER_DARK);
        }
        return img;
    }

    /** Side of the 8-pixel-high panel: only the lower half of the texture is visible. */
    static BufferedImage solarSide() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE);
        rect(img, 0, 8, 15, 9, FRAME);
        rect(img, 0, 10, 15, 10, CELL_ALT);
        rect(img, 0, 13, 15, 13, COPPER);
        rect(img, 0, 15, 15, 15, ANTHRACITE_DARK);
        for (int x = 1; x < 16; x += 5) px(img, x, 11, ANTHRACITE_LIGHT);
        return img;
    }

    /** Underside with the copper output port. */
    static BufferedImage solarBottom() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE_DARK);
        frame(img, ANTHRACITE_LIGHT, ANTHRACITE_DARK);
        rect(img, 5, 5, 10, 10, FRAME_SHADE);
        rect(img, 6, 6, 9, 9, COPPER);
        rect(img, 7, 7, 8, 8, COPPER_LIGHT);
        return img;
    }

    /** Battery casing: one copper band per tier and a light gray gauge window. */
    static BufferedImage batterySide(int tier) {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE);
        frame(img, FRAME, FRAME_SHADE);
        rect(img, 1, 1, 14, 1, ANTHRACITE_LIGHT);
        rect(img, 6, 3, 9, 12, ANTHRACITE_DARK);
        rect(img, 7, 4, 8, 11, CELL_ALT);
        rect(img, 7, 8, 8, 11, COPPER_LIGHT);
        int[][] bands = {{}, {13}, {12, 14}, {3, 12, 14}};
        for (int y : bands[tier]) {
            rect(img, 1, y, 5, y, COPPER);
            rect(img, 10, y, 14, y, COPPER);
        }
        return img;
    }

    /** Top with its copper terminals. */
    static BufferedImage batteryTop() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE);
        frame(img, FRAME, FRAME_SHADE);
        rect(img, 3, 3, 6, 6, COPPER_DARK);
        rect(img, 4, 4, 5, 5, COPPER_LIGHT);
        rect(img, 9, 9, 12, 12, COPPER_DARK);
        rect(img, 10, 10, 11, 11, COPPER_LIGHT);
        rect(img, 9, 4, 11, 4, WHITE); rect(img, 10, 3, 10, 5, WHITE);
        rect(img, 3, 11, 5, 11, WHITE);
        return img;
    }

    static BufferedImage batteryBottom() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE_DARK);
        frame(img, ANTHRACITE_LIGHT, ANTHRACITE_DARK);
        rect(img, 4, 4, 11, 11, ANTHRACITE);
        return img;
    }

    /** Cable arm: gray insulation with a copper core stripe (the model uses the 6-pixel middle band). */
    static BufferedImage cable() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, FRAME_SHADE);
        rect(img, 0, 5, 15, 5, FRAME);
        rect(img, 0, 6, 15, 9, ANTHRACITE);
        rect(img, 0, 7, 15, 8, COPPER);
        for (int x = 1; x < 16; x += 4) { px(img, x, 7, COPPER_LIGHT); px(img, x, 8, COPPER_DARK); }
        rect(img, 0, 10, 15, 10, FRAME_SHADE);
        return img;
    }

    /** Cable junction: anthracite collar around a copper contact. */
    static BufferedImage cableCore() {
        BufferedImage img = image();
        rect(img, 0, 0, 15, 15, ANTHRACITE);
        rect(img, 5, 5, 10, 10, ANTHRACITE_DARK);
        rect(img, 6, 6, 9, 9, COPPER);
        rect(img, 7, 7, 8, 8, COPPER_LIGHT);
        rect(img, 5, 5, 10, 5, FRAME);
        return img;
    }

    static void write(String name, BufferedImage img) throws IOException {
        ImageIO.write(img, "png", new File(OUT + name + ".png"));
    }
}

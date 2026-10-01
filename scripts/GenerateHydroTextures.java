import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Hydro instruments use GenerateTextures' exact palette and 16x16 pixel scale.
 * Structural materials reference the batteries, cables and wind turbines directly.
 * Run from the project root with Java 21: java scripts/GenerateHydroTextures.java
 */
public class GenerateHydroTextures {
    static final String OUT = "src/main/resources/assets/homelink_energy/textures/block/";
    static final int DARK = 0x222428, BODY = 0x2e3136, LIGHT = 0x3a3e44;
    static final int FRAME = 0xc9cdd2, SHADE = 0x9ea3a9;
    static final int COPPER = 0xc7794a, COPPER_DARK = 0x9a5433, AMBER = 0xe19a6a;

    static BufferedImage material(int color) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        rect(image, 0, 0, 16, 16, color);
        return image;
    }
    static void rect(BufferedImage image, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) image.setRGB(x, y, 0xff000000 | color);
    }
    static void frame(BufferedImage image) {
        rect(image, 0, 0, 16, 1, FRAME);
        rect(image, 0, 0, 1, 16, FRAME);
        rect(image, 0, 15, 16, 16, SHADE);
        rect(image, 15, 0, 16, 16, SHADE);
    }
    static void write(String name, BufferedImage image) throws Exception {
        ImageIO.write(image, "png", new File(OUT + name + ".png"));
    }
    static void drop(BufferedImage image, int x, int y) {
        rect(image, x + 1, y, x + 2, y + 2, FRAME);
        rect(image, x, y + 2, x + 3, y + 4, SHADE);
        rect(image, x + 1, y + 4, x + 2, y + 5, SHADE);
    }
    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        var accent = material(COPPER);
        rect(accent, 0, 0, 16, 2, AMBER);
        rect(accent, 0, 14, 16, 16, COPPER_DARK);
        write("hydro_accent", accent);
        // Four amber segments echo the battery gauge; this is a static instrument face.
        var screen = material(DARK);
        rect(screen, 1, 1, 15, 2, LIGHT);
        for (int y = 4; y < 14; y += 3) {
            rect(screen, 2, y, 8, y + 1, AMBER);
            rect(screen, 9, y, 14, y + 1, y < 10 ? SHADE : LIGHT);
        }
        write("hydro_screen", screen);
        for (int tier = 1; tier <= 3; tier++) {
            var plate = material(BODY);
            frame(plate);
            drop(plate, 3, 3);
            rect(plate, 8, 3, 13, 4, FRAME);
            rect(plate, 8, 6, 12, 7, SHADE);
            for (int i = 0; i < tier; i++) {
                rect(plate, 3 + i * 4, 10, 5 + i * 4, 14, COPPER);
                rect(plate, 3 + i * 4, 10, 5 + i * 4, 11, AMBER);
            }
            write("hydro_label_" + tier, plate);
        }
        var turbine = material(BODY);
        drop(turbine, 2, 2);
        rect(turbine, 7, 3, 14, 4, FRAME);
        rect(turbine, 7, 6, 12, 7, SHADE);
        rect(turbine, 2, 10, 14, 11, COPPER);
        rect(turbine, 2, 13, 10, 14, SHADE);
        write("hydro_label_turbine", turbine);
        var intake = material(DARK);
        for (int y = 2; y < 15; y += 4) {
            rect(intake, 1, y, 15, y + 1, SHADE);
            rect(intake, 1, y + 1, 15, y + 2, LIGHT);
        }
        rect(intake, 0, 0, 1, 16, COPPER_DARK);
        rect(intake, 15, 0, 16, 16, COPPER);
        write("hydro_intake", intake);
    }
}

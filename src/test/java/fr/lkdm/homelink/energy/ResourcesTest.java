package fr.lkdm.homelink.energy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Static checks of the shipped resources: translations, models, textures, loot tables. */
class ResourcesTest {
    private static final Path ASSETS = Path.of(System.getProperty("user.dir")).resolve(locate("src/main/resources/assets/homelink_energy"));
    private static final Path DATA = ASSETS.getParent().getParent().resolve("data/homelink_energy");
    private static final List<String> BLOCKS = List.of("solar_panel_1", "solar_panel_2", "solar_panel_3",
            "wind_turbine_1", "wind_turbine_2", "wind_turbine_3", "battery_1", "battery_2", "battery_3", "copper_energy_cable");

    /** Unit tests may run from the project or from its run directory. */
    private static Path locate(String relative) {
        Path here = Path.of(System.getProperty("user.dir"));
        for (Path dir = here; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(relative))) return dir.resolve(relative);
        }
        throw new IllegalStateException("Resources not found from " + here);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test void translationsHaveTheSameKeys() throws IOException {
        var en = json(ASSETS.resolve("lang/en_us.json")).keySet();
        var fr = json(ASSETS.resolve("lang/fr_fr.json")).keySet();
        assertEquals(en, fr, "en_us and fr_fr differ");
        for (String block : BLOCKS) assertTrue(en.contains("block.homelink_energy." + block), "Missing name for " + block);
    }

    @Test void everyBlockHasItsAssetsAndLootTable() {
        for (String block : BLOCKS) {
            assertTrue(Files.isRegularFile(ASSETS.resolve("blockstates/" + block + ".json")), "Blockstate " + block);
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/item/" + block + ".json")), "Item model " + block);
            assertTrue(Files.isRegularFile(DATA.resolve("loot_table/blocks/" + block + ".json")), "Loot table " + block);
            assertTrue(Files.isRegularFile(DATA.resolve("recipe/" + block + ".json")), "Recipe " + block);
        }
    }

    @Test void everyReferencedModelAndTextureExists() throws IOException {
        Pattern reference = Pattern.compile("\"homelink_energy:((?:block|item)/[a-z0-9_/]+)\"");
        List<String> missing = new ArrayList<>();
        try (var files = Files.walk(ASSETS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json") && !p.toString().contains("lang")).toList()) {
                Matcher matcher = reference.matcher(Files.readString(file));
                while (matcher.find()) {
                    String path = matcher.group(1);
                    boolean model = Files.isRegularFile(ASSETS.resolve("models/" + path + ".json"));
                    boolean texture = Files.isRegularFile(ASSETS.resolve("textures/" + path + ".png"));
                    if (!model && !texture) missing.add(file.getFileName() + " -> " + path);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing references: " + missing);
    }

    private record Surface(int axis, double plane, double u0, double v0, double u1, double v1) { }

    @Test void allStaticMeshesHaveNoCoplanarDuplicates() throws IOException {
        try(var files=Files.walk(ASSETS.resolve("models"))) {
            for(var path:files.filter(p->p.toString().endsWith(".json")).toList()) {
                var model=json(path); if(!model.has("elements"))continue;
                List<Surface> surfaces=new ArrayList<>();
                for(var raw:model.getAsJsonArray("elements")) {
                    var box=raw.getAsJsonObject();
                    // Rotated item blades and renderer transforms are covered by the two Node geometry audits.
                    if(box.has("rotation"))continue;
                    var from=box.getAsJsonArray("from");var to=box.getAsJsonArray("to");
                    for(var face:box.getAsJsonObject("faces").keySet()) {
                        int axis=switch(face){case "west","east"->0;case "up","down"->1;default->2;};
                        boolean positive=List.of("east","up","south").contains(face);
                        int u=axis==0?1:0,v=axis==2?1:2;
                        var surface=new Surface(axis,(positive?to:from).get(axis).getAsDouble(),from.get(u).getAsDouble(),from.get(v).getAsDouble(),to.get(u).getAsDouble(),to.get(v).getAsDouble());
                        assertTrue(surface.u1>surface.u0 && surface.v1>surface.v0,"Degenerate face in "+path);
                        for(var other:surfaces) {
                            boolean same=other.axis==axis && Math.abs(other.plane-surface.plane)<1e-6;
                            boolean overlap=Math.min(other.u1,surface.u1)-Math.max(other.u0,surface.u0)>1e-6
                                    && Math.min(other.v1,surface.v1)-Math.max(other.v0,surface.v0)>1e-6;
                            assertFalse(same&&overlap,"Coplanar faces in "+path+": "+surface+" / "+other);
                        }
                        surfaces.add(surface);
                    }
                }
            }
        }
    }

    @Test void allTexturesAreOpaquePowerOfTwoAndStatic() throws IOException {
        try(var files=Files.walk(ASSETS.resolve("textures"))) {
            for(var path:files.filter(p->p.toString().endsWith(".png")).toList()) {
                var image=javax.imageio.ImageIO.read(path.toFile()); assertNotNull(image,"Unreadable PNG "+path);
                int width=image.getWidth(),height=image.getHeight();
                assertEquals(width,height,"Unexpected texture animation strip "+path);
                assertTrue(width>=16 && (width&(width-1))==0,"Invalid mipmap dimensions "+path);
                for(int y=0;y<height;y++)for(int x=0;x<width;x++) assertEquals(255,image.getRGB(x,y)>>>24,"Transparent texel in opaque material "+path);
                assertFalse(Files.exists(Path.of(path+".mcmeta")),"Unexpected animated texture "+path);
            }
        }
    }

    @Test void batteryMeshesHaveNoOverlappingCoplanarFaces() throws IOException {
        for (int tier = 1; tier <= 3; tier++) {
            List<Surface> surfaces = new ArrayList<>();
            var model = json(ASSETS.resolve("models/block/battery_" + tier + ".json"));
            for (var element : model.getAsJsonArray("elements")) {
                var box = element.getAsJsonObject();
                var from = box.getAsJsonArray("from");
                var to = box.getAsJsonArray("to");
                for (String face : box.getAsJsonObject("faces").keySet()) {
                    int axis = switch (face) { case "west", "east" -> 0; case "up", "down" -> 1; default -> 2; };
                    boolean positive = List.of("east", "up", "south").contains(face);
                    int u = axis == 0 ? 1 : 0, v = axis == 2 ? 1 : 2;
                    var surface = new Surface(axis, (positive ? to : from).get(axis).getAsDouble(),
                            from.get(u).getAsDouble(), from.get(v).getAsDouble(), to.get(u).getAsDouble(), to.get(v).getAsDouble());
                    assertTrue(surface.u1 - surface.u0 > 0 && surface.v1 - surface.v0 > 0, "Degenerate battery face");
                    for (Surface other : surfaces) {
                        boolean samePlane = other.axis == axis && Math.abs(other.plane - surface.plane) < 0.00001;
                        boolean overlap = Math.min(other.u1, surface.u1) - Math.max(other.u0, surface.u0) > 0.00001
                                && Math.min(other.v1, surface.v1) - Math.max(other.v0, surface.v0) > 0.00001;
                        assertFalse(samePlane && overlap, "Battery " + tier + " has overlapping faces: " + surface + " / " + other);
                    }
                    surfaces.add(surface);
                }
            }
            assertTrue(surfaces.size() > 20, "Missing battery mesh");
        }
    }
}

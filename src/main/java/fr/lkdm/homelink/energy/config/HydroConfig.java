package fr.lkdm.homelink.energy.config;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.hydro.HydroParameters;
import java.util.HashSet;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * [hydro] section of the existing server config. NeoForge resets out-of-range entries; the remaining
 * cross-checks happen in {@link HydroParameters#sanitize}, which logs each replaced value once.
 * DH/t is an abstract gameplay flow: not a stored volume, not mB/t, not HE/t.
 */
public final class HydroConfig {
    public static ModConfigSpec.BooleanValue ENABLED;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;
    public static ModConfigSpec.DoubleValue FLOW_1, FLOW_2, FLOW_3, MIN_AVAILABILITY, TURBINE_FLOW;
    public static ModConfigSpec.IntValue[] WINDOW = new ModConfigSpec.IntValue[9];
    public static ModConfigSpec.IntValue CHECK_INTERVAL, MAX_TURBINES, MAX_PUMPS, MAX_PIPES, VISITS;
    public static ModConfigSpec.LongValue HE_REFERENCE, BUFFER;
    private static HydroParameters cached;
    private static String reported = "";

    private HydroConfig() { }

    static void define(ModConfigSpec.Builder b) {
        b.comment("Hydro branch: pumps capture environmental water (no HE, water is not consumed), pipes route an abstract flow,",
                "one 2x2x2 turbine converts it into HE. No time, weather, altitude or head bonus.").push("hydro");
        ENABLED = b.comment("Whether Hydro pumps and turbines may work.").define("enabled", true);
        DIMENSIONS = b.comment("Dimensions where Hydro works. Only the Overworld is validated by default.")
                .defineListAllowEmpty("allowedDimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld",
                        value -> value instanceof String s && ResourceLocation.tryParse(s) != null);
        FLOW_1 = b.comment("Maximum flow of a Hydro Pump I, in DH/t.").defineInRange("pumpFlow1", 1.0, 0.001, 1_000);
        FLOW_2 = b.comment("Maximum flow of a Hydro Pump II, in DH/t.").defineInRange("pumpFlow2", 3.0, 0.001, 1_000);
        FLOW_3 = b.comment("Maximum flow of a Hydro Pump III, in DH/t.").defineInRange("pumpFlow3", 12.0, 0.001, 1_000);
        String[] axes = {"Width", "Distance", "Depth"};
        int[][] defaults = HydroParameters.DEFAULT.windows();
        for (int tier = 0; tier < 3; tier++) for (int axis = 0; axis < 3; axis++)
            WINDOW[tier * 3 + axis] = (axis == 0 && tier == 0
                            ? b.comment("Water window in front of the intake: width, distance in front, vertical depth from the intake level.") : b)
                    .defineInRange("waterWindow" + (tier + 1) + axes[axis], defaults[tier][axis], 1, axis == 2 ? 8 : 15);
        MIN_AVAILABILITY = b.comment("Share of the window that must hold reachable water sources; below it the pump gives nothing.")
                .defineInRange("minimumWaterAvailability", 0.25, 0.01, 1.0);
        CHECK_INTERVAL = b.comment("Ticks between two periodic water checks of a pump; nearby block changes also trigger one.")
                .defineInRange("waterCheckInterval", 100, 20, 1_200);
        TURBINE_FLOW = b.comment("Flow a turbine can use, in DH/t. Any surplus is not stored.").defineInRange("turbineMaxFlow", 12.0, 0.001, 1_000);
        HE_REFERENCE = b.comment("HE produced over 24000 executed ticks at full turbine flow (1 coal = 4000 HE; 4000 HE per DH/t of flow by default). 0 stops production.")
                .defineInRange("turbineMaxHEPerReferencePeriod", 48_000L, 0L, 10_000_000L);
        BUFFER = b.comment("Small internal turbine buffer, in HE.").defineInRange("turbineBufferCapacity", 1_000L, 1L, 1_000_000L);
        MAX_PUMPS = b.comment("Most distinct pumps on one circuit; more suspends it with TOO_MANY_PUMPS.").defineInRange("maxPumpsPerCircuit", 4, 1, 16);
        MAX_TURBINES = b.comment("Fixed V1 limit: a circuit with two turbines stops both (no flow sharing).").defineInRange("maxTurbinesPerCircuit", 1, 1, 1);
        MAX_PIPES = b.comment("Most Hydro Pipes on one circuit; more suspends it with NETWORK_TOO_LARGE.").defineInRange("maxPipesPerCircuit", 512, 1, 4_096);
        VISITS = b.comment("Hydraulic graph visits per tick and dimension during rebuilds.").defineInRange("graphVisitsPerTick", 128, 16, 4_096);
        b.pop();
    }

    /** Drops the cached parameters after a configuration load or reload. */
    public static void reload() { cached = null; }

    /** @return validated parameters; defaults while the server config is not loaded (menus, unit tests) */
    public static HydroParameters parameters() {
        if (!EnergyConfig.loaded()) return HydroParameters.DEFAULT;
        HydroParameters value = cached;
        if (value != null) return value;
        int[][] windows = new int[3][3];
        for (int i = 0; i < 9; i++) windows[i / 3][i % 3] = WINDOW[i].get();
        value = HydroParameters.sanitize(ENABLED.get(), new HashSet<>(DIMENSIONS.get()), new double[]{FLOW_1.get(), FLOW_2.get(), FLOW_3.get()},
                windows, MIN_AVAILABILITY.get(), CHECK_INTERVAL.get(), TURBINE_FLOW.get(), HE_REFERENCE.get(), BUFFER.get(),
                MAX_PUMPS.get(), MAX_PIPES.get(), VISITS.get());
        String fixes = String.join(", ", value.corrections());
        if (!fixes.isEmpty() && !fixes.equals(reported))
            HomeLinkEnergy.LOGGER.warn("Invalid Hydro configuration values replaced by safe defaults: {}", fixes);
        reported = fixes;
        cached = value;
        return value;
    }
}

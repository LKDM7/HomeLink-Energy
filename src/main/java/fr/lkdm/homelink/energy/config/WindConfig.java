package fr.lkdm.homelink.energy.config;

import fr.lkdm.homelink.energy.wind.WindParameters;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Uses the existing world/server config. Invalid cross-field combinations use safe defaults. */
public final class WindConfig {
    public static ModConfigSpec.LongValue NOMINAL_1, NOMINAL_2, NOMINAL_3, BUFFER;
    public static ModConfigSpec.DoubleValue MIN, MAX, START, STOP, RAIN, THUNDER, CAP, LOW, NORMAL, HIGH, VERY_HIGH, STEP;
    public static ModConfigSpec.IntValue LOW_Y, HIGH_Y, VERY_HIGH_Y, TARGET_INTERVAL, CHECK_INTERVAL;
    private static boolean warned;
    private WindConfig() { }
    public static void define(ModConfigSpec.Builder b) {
        b.push("wind");
        NOMINAL_1 = b.defineInRange("windTurbine1NominalPer24000Ticks", 3000L, 0, 10_000_000);
        NOMINAL_2 = b.defineInRange("windTurbine2NominalPer24000Ticks", 12000L, 0, 10_000_000);
        NOMINAL_3 = b.defineInRange("windTurbine3NominalPer24000Ticks", 28000L, 0, 10_000_000);
        BUFFER = b.defineInRange("windBufferCapacity", 1000L, 1, 1_000_000);
        MIN = b.defineInRange("windNaturalMin", .10, 0, 1); MAX = b.defineInRange("windNaturalMax", 1., 0, 1);
        START = b.defineInRange("windCutInStart", .16, .001, 1); STOP = b.defineInRange("windCutInStop", .13, 0, .999);
        RAIN = b.defineInRange("rainWindMultiplier", 1.25, 0, 10); THUNDER = b.defineInRange("thunderWindMultiplier", 1.60, 0, 10);
        CAP = b.defineInRange("maxWindEfficiency", 1.50, 0, 10);
        LOW_Y = b.defineInRange("altitudeLowThreshold", 80, -2048, 2046);
        HIGH_Y = b.defineInRange("altitudeHighThreshold", 120, -2047, 2047);
        VERY_HIGH_Y = b.defineInRange("altitudeVeryHighThreshold", 180, -2046, 2048);
        LOW = b.defineInRange("altitudeLowMultiplier", .80, 0, 10); NORMAL = b.defineInRange("altitudeNormalMultiplier", 1., 0, 10);
        HIGH = b.defineInRange("altitudeHighMultiplier", 1.15, 0, 10); VERY_HIGH = b.defineInRange("altitudeVeryHighMultiplier", 1.25, 0, 10);
        TARGET_INTERVAL = b.defineInRange("windTargetIntervalTicks", 2400, 20, 24000);
        STEP = b.defineInRange("windChangePerTick", .00015, .000001, .01);
        CHECK_INTERVAL = b.defineInRange("rotorCheckIntervalTicks", 100, 20, 1200);
        b.pop();
    }
    public static long buffer() { return EnergyConfig.loaded() ? BUFFER.get() : 1000; }
    public static WindParameters parameters() {
        if (!EnergyConfig.loaded()) return WindParameters.DEFAULT;
        try {
            var result = new WindParameters(MIN.get(),MAX.get(),START.get(),STOP.get(),RAIN.get(),THUNDER.get(),CAP.get(),
                    LOW_Y.get(),HIGH_Y.get(),VERY_HIGH_Y.get(),LOW.get(),NORMAL.get(),HIGH.get(),VERY_HIGH.get(),TARGET_INTERVAL.get(),STEP.get(),CHECK_INTERVAL.get());
            warned = false; return result;
        } catch (IllegalArgumentException invalid) {
            if (!warned) fr.lkdm.homelink.energy.HomeLinkEnergy.LOGGER.warn("Invalid wind threshold ordering; using default wind parameters until corrected");
            warned = true; return WindParameters.DEFAULT;
        }
    }
}

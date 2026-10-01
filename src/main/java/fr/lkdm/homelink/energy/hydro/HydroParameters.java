package fr.lkdm.homelink.energy.hydro;

import java.util.List;
import java.util.Set;

/**
 * Validated Hydro balance. Every factor is finite and bounded; a value that would allow a division
 * by zero, a negative amount or a NaN is replaced by its safe default and reported by {@link #corrections()}.
 *
 * @param enabled whether Hydro machines may work at all
 * @param dimensions dimension ids where Hydro works
 * @param pumpFlow maximum DH/t of pump I, II, III
 * @param windows water window WIDTH, DISTANCE IN FRONT, VERTICAL DEPTH per pump level
 * @param minimumAvailability fraction of the window under which a pump gives nothing
 * @param waterCheckInterval ticks between two periodic water scans of a pump
 * @param turbineMaxFlow DH/t a turbine can use
 * @param heReference HE produced over 24 000 executed ticks at turbineMaxFlow
 * @param turbineBuffer turbine buffer capacity in HE
 * @param maxPumps most distinct pumps on one circuit
 * @param maxPipes most Hydro Pipes on one circuit
 * @param visitsPerTick hydraulic graph visits per tick and dimension
 * @param corrections human-readable list of replaced values, empty when the input was valid
 */
public record HydroParameters(boolean enabled, Set<String> dimensions, double[] pumpFlow, int[][] windows,
        double minimumAvailability, int waterCheckInterval, double turbineMaxFlow, long heReference, long turbineBuffer,
        int maxPumps, int maxPipes, int visitsPerTick, List<String> corrections) {
    public static final int REFERENCE_PERIOD = 24_000;
    /** V1 limit: no flow sharing between turbines, so it is not configurable beyond one. */
    public static final int MAX_TURBINES = 1;
    public static final HydroParameters DEFAULT = new HydroParameters(true, Set.of("minecraft:overworld"),
            new double[]{1, 3, 12}, new int[][]{{3, 3, 2}, {5, 5, 2}, {7, 7, 3}}, 0.25, 100, 12, 48_000, 1_000, 4, 512, 128, List.of());

    /**
     * @return sanitized copy: unsafe values replaced by their default, each replacement listed
     */
    public static HydroParameters sanitize(boolean enabled, Set<String> dimensions, double[] flows, int[][] windows,
            double minimum, int interval, double turbineFlow, long reference, long buffer, int pumps, int pipes, int visits) {
        var d = DEFAULT;
        var fixes = new java.util.ArrayList<String>();
        double[] f = new double[3];
        int[][] w = new int[3][3];
        for (int i = 0; i < 3; i++) {
            f[i] = positive(flows != null && flows.length > i ? flows[i] : Double.NaN, d.pumpFlow[i], 1_000, "pumpFlow" + (i + 1), fixes);
            for (int k = 0; k < 3; k++) {
                int value = windows != null && windows.length > i && windows[i] != null && windows[i].length > k ? windows[i][k] : -1;
                int max = k == 2 ? 8 : 15;
                if (value < 1 || value > max) { fixes.add("waterWindow" + (i + 1) + "[" + k + "]"); value = d.windows[i][k]; }
                w[i][k] = value;
            }
        }
        double min = minimum;
        if (!Double.isFinite(min) || min <= 0 || min > 1) { fixes.add("minimumWaterAvailability"); min = d.minimumAvailability; }
        int check = interval < 20 || interval > 1_200 ? fix(fixes, "waterCheckInterval", d.waterCheckInterval) : interval;
        double turbine = positive(turbineFlow, d.turbineMaxFlow, 1_000, "turbineMaxFlow", fixes);
        long ref = reference < 0 || reference > 10_000_000 ? fix(fixes, "turbineMaxHEPerReferencePeriod", d.heReference) : reference;
        long buf = buffer < 1 || buffer > 1_000_000 ? fix(fixes, "turbineBufferCapacity", d.turbineBuffer) : buffer;
        int p = pumps < 1 || pumps > 16 ? fix(fixes, "maxPumpsPerCircuit", d.maxPumps) : pumps;
        int pi = pipes < 1 || pipes > 4_096 ? fix(fixes, "maxPipesPerCircuit", d.maxPipes) : pipes;
        int v = visits < 16 || visits > 4_096 ? fix(fixes, "graphVisitsPerTick", d.visitsPerTick) : visits;
        Set<String> dims = dimensions == null ? d.dimensions : Set.copyOf(dimensions);
        return new HydroParameters(enabled, dims, f, w, min, check, turbine, ref, buf, p, pi, v, List.copyOf(fixes));
    }

    private static double positive(double value, double fallback, double max, String name, List<String> fixes) {
        if (Double.isFinite(value) && value > 0 && value <= max) return value;
        fixes.add(name);
        return fallback;
    }

    private static <T> T fix(List<String> fixes, String name, T fallback) {
        fixes.add(name);
        return fallback;
    }

    public double pumpFlow(HydroPumpTier tier) { return pumpFlow[tier.index()]; }
    public int windowWidth(HydroPumpTier tier) { return windows[tier.index()][0]; }
    public int windowDistance(HydroPumpTier tier) { return windows[tier.index()][1]; }
    public int windowDepth(HydroPumpTier tier) { return windows[tier.index()][2]; }

    /** @return HE per executed tick at full turbine flow */
    public double maxHePerTick() { return heReference / (double) REFERENCE_PERIOD; }

    /** @return whether Hydro works in that dimension id */
    public boolean allows(String dimension) { return dimensions.contains(dimension); }
}

package fr.lkdm.homelink.energy.energy;

/**
 * Daylight curve of the solar panels. With {@code t = dayTime mod 24000}, the shape is
 * {@code f(t) = sin(PI * t / 12000)} while {@code t < 12000} and zero otherwise; it is normalized
 * by {@code S}, the sum of {@code f} over one cycle, so that a panel rated for {@code E} HE per
 * cycle produces {@code E * f(t) / S} HE on tick {@code t} and exactly {@code E} over a full clear
 * cycle. The night is already inside {@code E}. The table is computed once for all panels.
 */
public final class SolarCurve {
    /** Ticks in a Minecraft day. */
    public static final int CYCLE_TICKS = 24_000;
    /** Ticks with a nonzero production, from sunrise ({@code t = 0}) to sunset. */
    public static final int DAYLIGHT_TICKS = 12_000;

    private static final double SUM;
    private static final double[] WEIGHTS = new double[DAYLIGHT_TICKS];

    static {
        double sum = 0;
        for (int t = 0; t < DAYLIGHT_TICKS; t++) sum += shape(t);
        SUM = sum;
        for (int t = 0; t < DAYLIGHT_TICKS; t++) WEIGHTS[t] = shape(t) / sum;
    }

    private SolarCurve() { }

    private static double shape(int t) {
        return Math.sin(Math.PI * t / DAYLIGHT_TICKS);
    }

    /**
     * Normalization constant {@code S}.
     *
     * @return the sum of {@code f(t)} over one cycle
     */
    public static double sum() { return SUM; }

    /**
     * Share of a cycle's production made on the given tick: {@code f(t) / S}.
     *
     * @param dayTime world day time, any value; only its position in the cycle matters
     * @return weight from zero (night) to its noon maximum; the weights of one cycle add up to 1
     */
    public static double weight(long dayTime) {
        int t = (int) Math.floorMod(dayTime, (long) CYCLE_TICKS);
        return t < DAYLIGHT_TICKS ? WEIGHTS[t] : 0;
    }

    /**
     * Whether the sun is up for the solar panels.
     *
     * @param dayTime world day time
     * @return false for {@code t = 0} and the night half of the cycle
     */
    public static boolean isDay(long dayTime) {
        return weight(dayTime) > 0;
    }
}

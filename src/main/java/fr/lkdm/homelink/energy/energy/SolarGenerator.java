package fr.lkdm.homelink.energy.energy;

/**
 * Production engine shared by every solar panel tier. It turns the potential production of
 * each executed tick into whole HE, carrying the fraction to the next tick so that small panels
 * still produce. The carried fraction always stays in {@code [0, 1)}: nothing is hoarded when
 * the buffer is full.
 */
public final class SolarGenerator {
    private final FractionalEnergy accumulator = new FractionalEnergy();

    /**
     * Potential production of one tick, before storage.
     *
     * @param energyPerCycle HE produced over a full clear cycle
     * @param dayTime world day time
     * @param efficiency weather efficiency, from 0 to 1
     * @return HE that the tick can produce, possibly fractional
     */
    public static double potential(long energyPerCycle, long dayTime, double efficiency) {
        if (energyPerCycle <= 0 || !(efficiency > 0)) return 0;
        return energyPerCycle * SolarCurve.weight(dayTime) * Math.min(efficiency, 1.0);
    }

    /**
     * State of a panel before storage is considered.
     *
     * @param energyPerCycle configured production; zero disables the panel
     * @param supportedDimension whether the panel is in the Overworld
     * @param skyVisible whether nothing is above the panel
     * @param dayTime world day time
     * @return every state except {@link SolarStatus#BUFFER_FULL} and {@link SolarStatus#GENERATING} blocks production
     */
    public static SolarStatus status(long energyPerCycle, boolean supportedDimension, boolean skyVisible, long dayTime) {
        if (energyPerCycle <= 0) return SolarStatus.DISABLED;
        if (!supportedDimension) return SolarStatus.UNSUPPORTED_DIMENSION;
        if (!skyVisible) return SolarStatus.SKY_BLOCKED;
        if (!SolarCurve.isDay(dayTime)) return SolarStatus.NIGHT;
        return SolarStatus.GENERATING;
    }

    /**
     * Adds one tick of potential production and returns the whole HE it completes.
     *
     * @param potential nonnegative potential production of the tick
     * @return whole HE produced this tick; the remainder stays below one unit
     */
    public long produce(double potential) {
        return accumulator.produce(potential);
    }

    /** @return fractional HE carried to the next tick, in {@code [0, 1)} */
    public double fraction() { return accumulator.fraction(); }

    /**
     * Restores a saved fraction, clamped to {@code [0, 1)}.
     *
     * @param value saved fraction
     */
    public void setFraction(double value) {
        accumulator.setFraction(value);
    }
}

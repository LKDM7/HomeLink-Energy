package fr.lkdm.homelink.energy.energy;

import java.util.function.LongSupplier;

/**
 * Per-panel production state, independent of the world so it can be driven tick by tick in
 * tests: generator fraction, buffer and counters. It only advances on ticks that are actually
 * executed; skipped time is never produced retroactively.
 */
public final class SolarPanelCore {
    private final SolarGenerator generator = new SolarGenerator();
    private final EnergyStore buffer;
    private SolarStatus status = SolarStatus.NIGHT;
    private double potentialRate;
    private double efficiency = 1;
    private long generated;
    private long lost;
    private long generatedToday;
    private long day = Long.MIN_VALUE;

    /**
     * @param bufferCapacity HE held by the panel
     * @param clock game tick clock
     */
    public SolarPanelCore(long bufferCapacity, LongSupplier clock) {
        buffer = new EnergyStore(bufferCapacity, clock);
    }

    /**
     * Runs one executed tick of production.
     *
     * @param energyPerCycle HE produced over a full clear cycle
     * @param supportedDimension whether the panel is in the Overworld
     * @param skyVisible whether nothing is above the panel
     * @param dayTime world day time
     * @param weatherEfficiency efficiency of the current weather, from 0 to 1
     * @return whole HE accepted into the buffer this tick
     */
    public long tick(long energyPerCycle, boolean supportedDimension, boolean skyVisible, long dayTime, double weatherEfficiency) {
        long currentDay = Math.floorDiv(dayTime, SolarCurve.CYCLE_TICKS);
        if (currentDay != day) {
            day = currentDay;
            generatedToday = 0;
        }
        efficiency = weatherEfficiency;
        status = SolarGenerator.status(energyPerCycle, supportedDimension, skyVisible, dayTime);
        potentialRate = status.producing() ? SolarGenerator.potential(energyPerCycle, dayTime, weatherEfficiency) : 0;
        long whole = generator.produce(potentialRate);
        long accepted = buffer.receive(whole, false);
        generated = saturatedAdd(generated, accepted);
        generatedToday = saturatedAdd(generatedToday, accepted);
        lost = saturatedAdd(lost, whole - accepted);
        if (status.producing() && buffer.isFull()) status = SolarStatus.BUFFER_FULL;
        return accepted;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }

    /** @return panel buffer */
    public EnergyStore buffer() { return buffer; }

    /** @return production engine */
    public SolarGenerator generator() { return generator; }

    /** @return state after the last tick */
    public SolarStatus status() { return status; }

    /** @return potential production of the last tick in HE/t */
    public double potentialRate() { return potentialRate; }

    /** @return weather efficiency of the last tick */
    public double efficiency() { return efficiency; }

    /** @return HE really produced and stored since placement */
    public long generated() { return generated; }

    /** @return HE produced while the buffer was full and therefore discarded */
    public long lost() { return lost; }

    /** @return HE really produced during the current Minecraft day */
    public long generatedToday() { return generatedToday; }

    /**
     * Restores saved counters.
     *
     * @param generatedTotal saved lifetime production
     * @param lostTotal saved discarded production
     * @param today saved production of the current day
     * @param dayIndex day of {@code today}
     */
    public void restore(long generatedTotal, long lostTotal, long today, long dayIndex) {
        generated = Math.max(0, generatedTotal);
        lost = Math.max(0, lostTotal);
        generatedToday = Math.max(0, today);
        day = dayIndex;
    }

    /** @return day index of {@link #generatedToday()} */
    public long day() { return day; }
}

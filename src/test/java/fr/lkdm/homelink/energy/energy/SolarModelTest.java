package fr.lkdm.homelink.energy.energy;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Solar model over simulated cycles. Each simulated tick is one executed server tick; the
 * panel's buffer is emptied into an unbounded sink after every tick so that output never limits
 * production. Tolerance: 1 HE per cycle, from the fraction still carried at the end of the cycle.
 */
class SolarModelTest {
    private static final long BUFFER = 1_000;
    private long now;

    /** Runs {@code ticks} executed ticks from {@code startDayTime}, draining the buffer each tick. */
    private long run(SolarPanelCore panel, long perCycle, boolean overworld, boolean sky, long startDayTime, long ticks, double efficiency) {
        long drained = 0;
        for (long i = 0; i < ticks; i++) {
            now++;
            panel.tick(perCycle, overworld, sky, startDayTime + i, efficiency);
            drained += panel.buffer().extract(Long.MAX_VALUE, false);
        }
        return drained;
    }

    private SolarPanelCore panel() { return new SolarPanelCore(BUFFER, () -> now); }

    @Test void curveIsNormalizedAndZeroAtNight() {
        double total = 0;
        for (int t = 0; t < SolarCurve.CYCLE_TICKS; t++) total += SolarCurve.weight(t);
        assertEquals(1.0, total, 1e-9);
        assertEquals(24_000 / Math.PI * 2 / 2, SolarCurve.sum(), 1.0, "S is close to 12000 * 2 / PI");
        assertEquals(0, SolarCurve.weight(0));
        assertEquals(0, SolarCurve.weight(12_000));
        assertEquals(0, SolarCurve.weight(18_000));
        assertTrue(SolarCurve.weight(6_000) > SolarCurve.weight(1_000), "Noon is the maximum");
        assertEquals(SolarCurve.weight(1_000), SolarCurve.weight(11_000), 1e-15, "Symmetric sunrise and sunset");
        assertEquals(SolarCurve.weight(6_000), SolarCurve.weight(6_000 + 24_000 * 5L), "Periodic");
        assertEquals(SolarCurve.weight(6_000), SolarCurve.weight(6_000 - 24_000), "Negative day times wrap");
    }

    @ParameterizedTest(name = "{0} HE/cycle, efficiency {1}")
    @CsvSource({"2000,1.0", "8000,1.0", "20000,1.0", "2000,0.40", "8000,0.40", "20000,0.40", "2000,0.15", "8000,0.15", "20000,0.15"})
    void fullCycleMatchesTheRatedTotal(long perCycle, double efficiency) {
        long produced = run(panel(), perCycle, true, true, 0, SolarCurve.CYCLE_TICKS, efficiency);
        double expected = perCycle * efficiency;
        assertTrue(Math.abs(produced - expected) <= 1, "Produced " + produced + " HE, expected " + expected);
    }

    @Test void manyCyclesDoNotDrift() {
        var panel = panel();
        long produced = run(panel, 2_000, true, true, 0, SolarCurve.CYCLE_TICKS * 10L, 1.0);
        assertTrue(Math.abs(produced - 20_000) <= 1, "Ten cycles gave " + produced);
        assertEquals(produced, panel.generated());
        assertEquals(0, panel.lost());
    }

    @Test void cycleStartingAtAnyTimeGivesTheSameTotal() {
        long produced = run(panel(), 8_000, true, true, 17_345, SolarCurve.CYCLE_TICKS, 1.0);
        assertTrue(Math.abs(produced - 8_000) <= 1, "Produced " + produced);
    }

    @Test void nightBlockedSkyAndOtherDimensionsProduceNothing() {
        var night = panel();
        assertEquals(0, run(night, 20_000, true, true, 12_000, 12_000, 1.0));
        assertEquals(SolarStatus.NIGHT, night.status());
        var blocked = panel();
        assertEquals(0, run(blocked, 20_000, true, false, 0, SolarCurve.CYCLE_TICKS, 1.0));
        assertEquals(SolarStatus.SKY_BLOCKED, blocked.status());
        var nether = panel();
        assertEquals(0, run(nether, 20_000, false, true, 0, SolarCurve.CYCLE_TICKS, 1.0));
        assertEquals(SolarStatus.UNSUPPORTED_DIMENSION, nether.status());
        var disabled = panel();
        assertEquals(0, run(disabled, 0, true, true, 0, SolarCurve.CYCLE_TICKS, 1.0));
        assertEquals(SolarStatus.DISABLED, disabled.status());
        assertEquals(0, night.generator().fraction() + blocked.generator().fraction() + nether.generator().fraction());
    }

    @Test void switchedOffPanelProducesNothingUntilSwitchedOn() {
        var panel = panel();
        for (int tick = 0; tick < 6_000; tick++) panel.tick(20_000, true, true, tick, 1.0, false);
        assertEquals(0, panel.generated());
        assertEquals(SolarStatus.SWITCHED_OFF, panel.status());
        panel.tick(20_000, true, true, 6_000, 1.0, true);
        assertEquals(SolarStatus.GENERATING, panel.status());
    }

    @Test void smallPanelAccumulatesFractions() {
        var panel = panel();
        double noonRate = SolarGenerator.potential(2_000, 6_000, 1.0);
        assertTrue(noonRate < 1, "Solar Panel I makes less than 1 HE/t even at noon: " + noonRate);
        long produced = run(panel, 2_000, true, true, 5_000, 2_000, 1.0);
        assertTrue(produced > 0, "Fractions were rounded away");
        assertTrue(panel.generator().fraction() >= 0 && panel.generator().fraction() < 1);
        double expected = 0;
        for (int t = 5_000; t < 7_000; t++) expected += SolarGenerator.potential(2_000, t, 1.0);
        assertTrue(Math.abs(produced - expected) < 1, "Produced " + produced + ", potential " + expected);
    }

    @Test void fullBufferDiscardsProductionWithoutHiddenReserve() {
        var panel = panel();
        for (int t = 0; t < SolarCurve.CYCLE_TICKS; t++) {
            now++;
            panel.tick(20_000, true, true, t, 1.0);
        }
        assertEquals(BUFFER, panel.buffer().stored(), "Buffer never exceeds its capacity");
        assertEquals(SolarStatus.NIGHT, panel.status());
        assertTrue(panel.generator().fraction() < 1, "No reserve builds up");
        long total = panel.generated() + panel.lost();
        assertTrue(Math.abs(total - 20_000) <= 1, "Accepted + discarded = potential: " + total);
        assertEquals(BUFFER, panel.generated());
        // Emptying the buffer later only returns what it holds.
        assertEquals(BUFFER, panel.buffer().extract(Long.MAX_VALUE, false));
        now++;
        panel.tick(20_000, true, true, 18_000, 1.0);
        assertEquals(0, panel.buffer().stored(), "Night after a full day: nothing comes back");
    }

    @Test void bufferFullStatusDuringDaylight() {
        var panel = panel();
        panel.buffer().setStored(BUFFER);
        now++;
        panel.tick(20_000, true, true, 6_000, 1.0);
        assertEquals(SolarStatus.BUFFER_FULL, panel.status());
        assertEquals(BUFFER, panel.buffer().stored());
    }

    @Test void skippedTimeIsNeverProducedRetroactively() {
        var panel = panel();
        long before = run(panel, 20_000, true, true, 1_000, 10, 1.0);
        // The world jumps from 1010 to 23000 (sleeping, /time set): only executed ticks count.
        long after = run(panel, 20_000, true, true, 23_000, 10, 1.0);
        assertEquals(0, after, "Nothing produced for the skipped day");
        assertTrue(before >= 0 && panel.generated() == before);
    }

    @Test void thunderReplacesRain() {
        assertEquals(Weather.THUNDER, Weather.of(true, true));
        assertEquals(Weather.RAIN, Weather.of(true, false));
        assertEquals(Weather.CLEAR, Weather.of(false, false));
        assertEquals(0.15 * SolarGenerator.potential(1_000, 6_000, 1), SolarGenerator.potential(1_000, 6_000, 0.15), 1e-12);
    }

    @Test void generatedTodayResetsEachDay() {
        var panel = panel();
        run(panel, 20_000, true, true, 0, 12_000, 1.0);
        assertTrue(panel.generatedToday() > 0);
        run(panel, 20_000, true, true, 24_000, 1, 1.0);
        assertEquals(0, panel.generatedToday());
    }
}

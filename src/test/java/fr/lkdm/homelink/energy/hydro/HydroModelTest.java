package fr.lkdm.homelink.energy.hydro;

import static org.junit.jupiter.api.Assertions.*;

import fr.lkdm.homelink.energy.energy.EnergyStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Deterministic production, allocation, conservation and configuration bounds of the Hydro branch. */
class HydroModelTest {
    private static final HydroParameters P = HydroParameters.DEFAULT;
    private static final int PERIOD = HydroParameters.REFERENCE_PERIOD;

    /** One circuit simulated for {@code ticks}: pumps at a water availability, buffer emptied each tick. */
    private static long run(double[] pumpMaxFlows, double availability, long ticks, long start) {
        var circuit = new HydroCircuit(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false, false);
        var core = new HydroTurbineCore(P.turbineBuffer(), () -> 0);
        var sink = new EnergyStore(Long.MAX_VALUE, () -> 0);
        double[] flows = new double[pumpMaxFlows.length];
        for (int i = 0; i < flows.length; i++) flows[i] = availability >= P.minimumAvailability() ? pumpMaxFlows[i] * availability : 0;
        for (long t = start; t < start + ticks; t++) {
            var allocation = HydroAllocation.allocate(flows, true, P.turbineMaxFlow());
            core.tick(t, allocation.used(), P.turbineMaxFlow(), P.heReference());
            long out = core.buffer().extract(Long.MAX_VALUE, false);
            core.exported(out);
            sink.receive(out, false);
        }
        assertEquals(core.generated(), sink.stored() + core.buffer().stored(), "conservation");
        assertEquals(core.exported(), sink.stored());
        assertTrue(circuit.pumps().isEmpty());
        return core.generated();
    }

    @Test void referencePeriodPerPumpLevel() throws Exception {
        var report = new StringBuilder("Hydro production over 24 000 executed ticks, full basin, buffer emptied every tick.\n");
        Object[][] cases = {{"Pump I", new double[]{1}, 4_000L}, {"Pump II", new double[]{3}, 12_000L}, {"Pump III", new double[]{12}, 48_000L},
                {"3 x Pump I", new double[]{1, 1, 1}, 12_000L}, {"2 x Pump II", new double[]{3, 3}, 24_000L},
                {"4 x Pump II (capped)", new double[]{3, 3, 3, 3}, 48_000L},
                {"2 x Pump III (capped)", new double[]{12, 12}, 48_000L}, {"4 x Pump III (capped)", new double[]{12, 12, 12, 12}, 48_000L}};
        for (Object[] c : cases) {
            long produced = run((double[]) c[1], 1.0, PERIOD, 0);
            assertTrue(Math.abs(produced - (long) c[2]) <= 1, c[0] + " produced " + produced);
            report.append(String.format(Locale.ROOT, "%-24s target=%6d obtained=%6d%n", c[0], c[2], produced));
        }
        Files.createDirectories(Path.of(System.getProperty("energy.reportDir", "build/reports")));
        Files.writeString(Path.of(System.getProperty("energy.reportDir", "build/reports"), "hydro-periods.txt"), report);
    }

    @Test void waterAvailabilityThresholds() {
        double[] pump3 = {P.pumpFlow(HydroPumpTier.III)};
        assertEquals(0, run(pump3, 0, PERIOD, 0));
        assertEquals(0, run(pump3, 0.24, PERIOD, 0), "under 25 % gives nothing");
        assertTrue(Math.abs(run(pump3, 0.25, PERIOD, 0) - 12_000) <= 1);
        assertTrue(Math.abs(run(pump3, 0.5, PERIOD, 0) - 24_000) <= 1);
        assertTrue(Math.abs(run(pump3, 1, PERIOD, 0) - 48_000) <= 1);
    }

    @Test void noDriftOverManyPeriodsAndNoTimeOfDayDependency() {
        long many = run(new double[]{1}, 1, 10L * PERIOD, 0);
        assertTrue(Math.abs(many - 40_000) <= 1, "ten periods of Pump I: " + many);
        // The model has no day time input at all: any start tick gives the same total.
        assertEquals(run(new double[]{3}, 1, PERIOD, 0), run(new double[]{3}, 1, PERIOD, 6_000));
        assertEquals(run(new double[]{3}, 1, PERIOD, 0), run(new double[]{3}, 1, PERIOD, 13_000));
    }

    @Test void allocationNeverDoublesAndSharesSumToUsed() {
        var two = HydroAllocation.allocate(new double[]{6, 6}, true, 6);
        assertEquals(12, P.turbineMaxFlow(), 1e-12, "a full Pump III fills the turbine alone");
        assertEquals(12, two.available(), 1e-12);
        assertEquals(6, two.used(), 1e-12);
        assertTrue(two.limited());
        assertEquals(two.used(), two.shares()[0] + two.shares()[1], 1e-12);
        var mixed = HydroAllocation.allocate(new double[]{1, 3, 0}, true, 6);
        assertEquals(4, mixed.used(), 1e-12);
        assertFalse(mixed.limited());
        assertEquals(1, mixed.shares()[0], 1e-12);
        assertEquals(0, mixed.shares()[2], 1e-12);
        var off = HydroAllocation.allocate(new double[]{6}, false, 6);
        assertEquals(0, off.used());
        assertEquals(6, off.available(), 1e-12);
        assertFalse(off.limited(), "a stopped turbine is not flow limited");
        var nan = HydroAllocation.allocate(new double[]{Double.NaN, -3, Double.POSITIVE_INFINITY}, true, 6);
        assertEquals(0, nan.used());
    }

    @Test void circuitAllocatesOncePerTickWhateverTheTickerOrder() {
        var pump = new net.minecraft.core.BlockPos(0, 0, 0);
        var circuit = new HydroCircuit(Set.of(), Set.of(pump), Set.of(new net.minecraft.core.BlockPos(5, 0, 0)), Set.of(), Set.of(), false, false);
        int[] calls = {0};
        var first = circuit.allocate(10, p -> { calls[0]++; return 6; }, true, 6);
        var second = circuit.allocate(10, p -> { calls[0]++; return 600; }, true, 6);
        assertSame(first, second);
        assertEquals(1, calls[0]);
        assertEquals(6, circuit.share(pump, 10), 1e-12);
        assertEquals(0, circuit.share(pump, 12), "stale allocations are not reused");
    }

    @Test void fullBufferLosesEnergyWithoutHiddenStock() {
        var core = new HydroTurbineCore(10, () -> 0);
        double potential = 0;
        for (int t = 0; t < PERIOD; t++) {
            core.tick(t, 6, 6, 24_000);
            potential += 1;
        }
        assertEquals(10, core.buffer().stored());
        assertEquals(core.generated() + core.lost(), (long) Math.floor(potential + 1e-9));
        assertEquals(10, core.generated());
        assertTrue(core.fraction().fraction() >= 0 && core.fraction().fraction() < 1);
        assertEquals(core.generated() + core.lost(), core.potentialTotal(), 1);
    }

    @Test void stoppedTurbineDrainsButNeverGenerates() {
        var core = new HydroTurbineCore(1_000, () -> 0);
        for (int t = 0; t < 600; t++) core.tick(t, 6, 6, 24_000);
        long stored = core.buffer().stored(), generated = core.generated();
        assertTrue(stored > 0);
        for (int t = 600; t < 1_200; t++) {
            core.tick(t, 0, 6, 24_000);
            core.exported(core.buffer().extract(1, false));
        }
        assertEquals(generated, core.generated(), "no generation while off");
        assertEquals(stored - core.exported(), core.buffer().stored());
    }

    @Test void periodRollsOverOnExecutedTicksOnly() {
        var core = new HydroTurbineCore(Long.MAX_VALUE, () -> 0);
        for (int t = 23_990; t < 24_010; t++) core.tick(t, 6, 6, 24_000);
        assertEquals(10, core.observedTicks(), "new period started at tick 24 000");
        core.restore(5, 0, 0, 3, 50_000, 1);
        assertEquals(24_000, core.observedTicks(), "restored counters are bounded");
    }

    @Test void noCatchUpAfterUnloadOrRestart() {
        var core = new HydroTurbineCore(Long.MAX_VALUE, () -> 0);
        for (int t = 0; t < 100; t++) core.tick(t, 6, 6, 24_000);
        long before = core.generated();
        // The chunk was unloaded (or the server stopped) for 50 000 ticks: the next executed tick produces one tick of energy.
        core.tick(50_100, 6, 6, 24_000);
        assertTrue(core.generated() - before <= 1, "catch-up of " + (core.generated() - before));
        assertEquals(1, core.observedTicks(), "the new period starts with one observed tick, never presented as a full day");
    }

    @Test void configurationIsSanitized() {
        var bad = HydroParameters.sanitize(true, Set.of("minecraft:overworld"), new double[]{Double.NaN, -1, 0}, new int[][]{{0, 3, 2}, {5, 99, 2}, {7, 7, 0}},
                Double.POSITIVE_INFINITY, 0, 0, -5, 0, 0, 0, 0);
        assertEquals(P.pumpFlow(HydroPumpTier.I), bad.pumpFlow(HydroPumpTier.I));
        assertEquals(P.pumpFlow(HydroPumpTier.III), bad.pumpFlow(HydroPumpTier.III));
        assertEquals(P.windowWidth(HydroPumpTier.I), bad.windowWidth(HydroPumpTier.I));
        assertEquals(P.windowDistance(HydroPumpTier.II), bad.windowDistance(HydroPumpTier.II));
        assertEquals(P.minimumAvailability(), bad.minimumAvailability());
        assertEquals(P.turbineMaxFlow(), bad.turbineMaxFlow());
        assertTrue(bad.turbineMaxFlow() > 0 && bad.turbineBuffer() > 0 && bad.maxPipes() > 0 && bad.visitsPerTick() > 0);
        assertFalse(bad.corrections().isEmpty());
        var good = HydroParameters.sanitize(true, Set.of(), P.pumpFlow(), P.windows(), 0.25, 100, 12, 48_000, 1_000, 4, 512, 128);
        assertTrue(good.corrections().isEmpty());
        assertFalse(good.allows("minecraft:overworld"), "an empty list disables every dimension");
        assertEquals(2.0, P.maxHePerTick(), 1e-12);
        // Same ratio for every level: 4 000 HE per DH/t and per period, so only Pump III doubled.
        assertEquals(P.heReference() / P.turbineMaxFlow(), 4_000, 1e-9);
    }
}

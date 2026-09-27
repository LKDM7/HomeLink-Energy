package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homecore.api.energy.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ReliabilityTest {
    @Test void maximumLongStorageAndCountersDoNotOverflow() {
        var store = new EnergyStore(Long.MAX_VALUE, () -> 0);
        assertEquals(Long.MAX_VALUE, store.receive(Long.MAX_VALUE, false));
        assertEquals(0, store.receive(Long.MAX_VALUE, false));
        assertEquals(Long.MAX_VALUE, store.extract(Long.MAX_VALUE, false));
        var panel = new SolarPanelCore(1000, () -> 0);
        panel.restore(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, 0);
        panel.tick(20_000, true, true, 6000, 1);
        assertEquals(Long.MAX_VALUE, panel.generated());
        assertEquals(Long.MAX_VALUE, panel.generatedToday());
        panel.buffer().setStored(1000);
        panel.tick(20_000, true, true, 6000, 1);
        assertEquals(Long.MAX_VALUE, panel.lost());
    }

    @Test void simulatedUnloadAndReloadDoesNotCatchUpAndKeepsFraction() {
        var original = new SolarPanelCore(1000, () -> 0);
        for (int t = 0; t < 100; t++) original.tick(2000, true, true, 6000, 1);
        var resumed = new SolarPanelCore(1000, () -> 1_000_000);
        resumed.buffer().setStored(original.buffer().stored());
        resumed.generator().setFraction(original.generator().fraction());
        resumed.restore(original.generated(), original.lost(), original.generatedToday(), original.day());
        long before = resumed.generated();
        resumed.tick(2000, true, true, 24_000L * 100 + 6000, 1);
        assertTrue(resumed.generated() - before <= 1);
        original.tick(2000, true, true, 6000, 1);
        assertEquals(original.generated(), resumed.generated());
        assertEquals(original.generator().fraction(), resumed.generator().fraction());
    }

    @Test void frozenSunProducesOnlyForExecutedTicks() {
        var panel = new SolarPanelCore(1000, () -> 0);
        for (int tick = 0; tick < 200; tick++) panel.tick(2000, true, true, 6000, 1);
        assertEquals((long) Math.floor(200 * SolarGenerator.potential(2000, 6000, 1)), panel.generated());
    }

    @Test void corruptFractionIsDiscarded() {
        var generator = new SolarGenerator();
        for (double invalid : new double[]{-1, 1, 1000, Double.NaN, Double.POSITIVE_INFINITY}) {
            generator.setFraction(invalid);
            assertEquals(0, generator.fraction());
        }
    }

    @Test void nestedTransferCannotDoubleDebit() {
        var source = new EnergyStore(100, () -> 0);
        source.setStored(100);
        var sink = new EnergyStore(100, () -> 0);
        HePort from = new StorePort(source);
        HePort to = new StorePort(sink) {
            @Override public long insert(long amount, boolean simulate) {
                assertEquals(0, EnergyTransfer.move(from, this, 100));
                return super.insert(amount, simulate);
            }
        };
        assertEquals(100, EnergyTransfer.move(from, to, 100));
        assertEquals(0, source.stored());
        assertEquals(100, sink.stored());
    }

    private static class StorePort implements HePort {
        private final EnergyStore store;
        StorePort(EnergyStore store) { this.store = store; }
        public long stored() { return store.stored(); }
        public long capacity() { return store.capacity(); }
        public long insert(long n, boolean simulate) { return store.receive(n, simulate); }
        public long extract(long n, boolean simulate) { return store.extract(n, simulate); }
    }
}

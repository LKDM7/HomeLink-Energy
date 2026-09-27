package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homecore.api.energy.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EnergyStoreTest {
    private long now;

    private EnergyStore store(long capacity) { return new EnergyStore(capacity, () -> now); }

    private static HePort port(EnergyStore store, boolean receive, boolean send) {
        return new HePort() {
            @Override public long stored() { return store.stored(); }
            @Override public long capacity() { return store.capacity(); }
            @Override public long insert(long amount, boolean simulate) { return receive ? store.receive(amount, simulate) : 0; }
            @Override public long extract(long amount, boolean simulate) { return send ? store.extract(amount, simulate) : 0; }
        };
    }

    @Test void staysWithinBounds() {
        var store = store(40_000);
        assertEquals(40_000, store.receive(Long.MAX_VALUE, false));
        assertEquals(0, store.receive(1, false));
        assertEquals(40_000, store.stored());
        assertEquals(40_000, store.extract(Long.MAX_VALUE, false));
        assertEquals(0, store.extract(1, false));
        assertEquals(0, store.receive(-5, false));
        assertEquals(0, store.extract(-5, false));
        store.setStored(-10);
        assertEquals(0, store.stored());
        store.setStored(Long.MAX_VALUE);
        assertEquals(40_000, store.stored());
        store.setCapacity(1_000);
        assertEquals(1_000, store.stored(), "Shrinking the capacity removes the excess");
    }

    @Test void simulationChangesNothing() {
        var store = store(100);
        assertEquals(100, store.receive(500, true));
        assertEquals(0, store.stored());
        store.setStored(60);
        assertEquals(60, store.extract(500, true));
        assertEquals(60, store.stored());
    }

    @Test void perTickRatesLimitBothWaysAndResetNextTick() {
        var store = store(120_000);
        store.setRates(32, 32);
        now = 1;
        assertEquals(32, store.receive(1_000, false));
        assertEquals(0, store.receive(1, false), "Input limit reached this tick");
        assertEquals(32, store.extract(1_000, false));
        assertEquals(0, store.extract(1, false), "Output limit reached this tick");
        now = 2;
        assertEquals(32, store.receive(1_000, false));
        assertEquals(32, store.stored());
    }

    @Test void transferIsExactAndConservesEnergy() {
        var source = store(1_000);
        source.setStored(700);
        var target = store(40_000);
        target.setStored(39_900);
        var from = port(source, false, true);
        var to = port(target, true, true);
        long before = source.stored() + target.stored();
        assertEquals(100, EnergyTransfer.move(from, to, Long.MAX_VALUE));
        assertEquals(600, source.stored());
        assertEquals(40_000, target.stored());
        assertEquals(before, source.stored() + target.stored());
        assertEquals(0, EnergyTransfer.move(from, to, Long.MAX_VALUE), "Full target: nothing moves, source keeps its energy");
        assertEquals(600, source.stored());
    }

    @Test void transferRespectsPortDirections() {
        var a = store(100);
        a.setStored(100);
        var b = store(100);
        assertEquals(0, EnergyTransfer.move(port(a, true, false), port(b, true, true), 50));
        assertEquals(0, EnergyTransfer.move(port(a, true, true), port(b, false, true), 50));
        assertEquals(100, a.stored());
        assertEquals(0, b.stored());
    }

    @Test void panelToBatteryOverAFullDayConservesEnergy() {
        var panel = new SolarPanelCore(1_000, () -> now);
        var battery = store(40_000);
        var from = port(panel.buffer(), false, true);
        var to = port(battery, true, true);
        long moved = 0;
        for (int t = 0; t < SolarCurve.CYCLE_TICKS; t++) {
            now++;
            panel.tick(20_000, true, true, t, 1.0);
            moved += EnergyTransfer.move(from, to, Long.MAX_VALUE);
        }
        assertEquals(panel.generated(), battery.stored() + panel.buffer().stored(), "Every generated HE is in the battery or the buffer");
        assertEquals(moved, battery.stored());
        assertTrue(Math.abs(battery.stored() - 20_000) <= 1, "Battery received " + battery.stored());
    }
}

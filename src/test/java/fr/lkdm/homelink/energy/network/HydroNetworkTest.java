package fr.lkdm.homelink.energy.network;

import static org.junit.jupiter.api.Assertions.*;

import fr.lkdm.homecore.api.energy.EnergyPortType;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homelink.energy.energy.*;
import fr.lkdm.homelink.energy.hydro.HydroTurbineCore;
import fr.lkdm.homelink.energy.wind.WindParameters;
import fr.lkdm.homelink.energy.wind.WindTurbineCore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Solar, wind and hydro on one cable network: exact conservation and per-source telemetry without double counting. */
class HydroNetworkTest {
    private record Port(EnergyStore store, EnergyRole role, EnergySourceType source, HydroTurbineCore hydro) implements HePort {
        @Override public EnergySourceType sourceType() { return source; }
        @Override public EnergyPortType type() {
            return role == EnergyRole.PRODUCER ? EnergyPortType.OUTPUT : role == EnergyRole.CONSUMER ? EnergyPortType.INPUT : EnergyPortType.BOTH;
        }
        @Override public long stored() { return store.stored(); }
        @Override public long capacity() { return store.capacity(); }
        @Override public long insert(long amount, boolean simulate) { return role == EnergyRole.PRODUCER ? 0 : store.receive(amount, simulate); }
        @Override public long extract(long amount, boolean simulate) {
            long n = role == EnergyRole.CONSUMER ? 0 : store.extract(amount, simulate);
            if (!simulate && hydro != null) hydro.exported(n);
            return n;
        }
    }

    @Test void solarWindHydroBatteryConsumer() {
        var solar = new SolarPanelCore(1000, () -> 0);
        var wind = new WindTurbineCore(1000, () -> 0);
        var hydro = new HydroTurbineCore(1000, () -> 0);
        var battery = new EnergyStore(320_000, () -> 0);
        var consumer = new EnergyStore(3, () -> 0);
        List<EnergyNetwork.Endpoint> endpoints = new ArrayList<>();
        Port[] ports = {new Port(solar.buffer(), EnergyRole.PRODUCER, EnergySourceType.SOLAR, null),
                new Port(wind.buffer(), EnergyRole.PRODUCER, EnergySourceType.WIND, null),
                new Port(hydro.buffer(), EnergyRole.PRODUCER, EnergySourceType.HYDRO, hydro),
                new Port(battery, EnergyRole.STORAGE, EnergySourceType.OTHER, null),
                new Port(consumer, EnergyRole.CONSUMER, EnergySourceType.OTHER, null)};
        for (int i = 0; i < ports.length; i++) { Port port = ports[i]; endpoints.add(new EnergyNetwork.Endpoint(new BlockPos(i, 0, 0), () -> port)); }
        var network = new EnergyNetwork(1, Set.of(), endpoints, false);
        long consumed = 0;
        for (int t = 0; t < 48_000; t++) {
            solar.tick(20_000, true, true, t, 1);
            wind.tick(28_000, t, 0.6, 1, 100, true, true, true, WindParameters.DEFAULT);
            hydro.tick(t, t < 24_000 ? 6 : 3, 6, 24_000);
            network.tick(t);
            consumed += consumer.extract(3, false);
            long generated = solar.generated() + wind.generated() + hydro.generated();
            assertEquals(generated, consumed + battery.stored() + solar.buffer().stored() + wind.buffer().stored() + hydro.buffer().stored());
            assertEquals(hydro.generated() - hydro.exported(), hydro.buffer().stored(), "hydro buffer variation = generated - exported");
            if (t % 20 == 19) {
                double sum = 0;
                for (EnergySourceType type : EnergySourceType.values()) sum += network.production(type);
                assertEquals(network.production(), sum, 1e-9, "categories sum to the delivered flow");
            }
        }
        assertTrue(Math.abs(hydro.generated() - 36_000) <= 1, "24 000 + 12 000 HE: " + hydro.generated());
        assertTrue(network.production(EnergySourceType.HYDRO) > 0);
    }
}

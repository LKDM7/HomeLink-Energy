package fr.lkdm.homelink.energy.network;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homelink.energy.energy.HePort;
import fr.lkdm.homelink.energy.energy.EnergyStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DistributionTest {
    private static class Port implements HePort {
        final EnergyStore store = new EnergyStore(Long.MAX_VALUE, () -> 0);
        final EnergyRole role;
        Port(EnergyRole role, long energy) { this.role=role; store.setStored(energy); }
        public EnergyRole role() { return role; }
        public long stored() { return store.stored(); }
        public long capacity() { return store.capacity(); }
        public long insert(long n, boolean simulate) { return store.receive(n,simulate); }
        public long extract(long n, boolean simulate) { return store.extract(n,simulate); }
    }
    @Test void tinySupplyRotatesAcrossConsumers() {
        var a=new Port(EnergyRole.PRODUCER,1);
        var b=new Port(EnergyRole.CONSUMER,0);
        var c=new Port(EnergyRole.CONSUMER,0);
        assertEquals(1,EnergyNetwork.spread(List.of(a),List.of(b,c),0));
        a.store.setStored(1);
        assertEquals(1,EnergyNetwork.spread(List.of(a),List.of(b,c),1));
        assertEquals(1,b.stored()); assertEquals(1,c.stored());
    }
    @Test void summedSupplyCannotOverflowAndStopDistribution() {
        var a=new Port(EnergyRole.PRODUCER,Long.MAX_VALUE);
        var b=new Port(EnergyRole.PRODUCER,Long.MAX_VALUE);
        var c=new Port(EnergyRole.CONSUMER,0);
        assertEquals(Long.MAX_VALUE,EnergyNetwork.spread(List.of(a,b),List.of(c),0));
        assertEquals(Long.MAX_VALUE,c.stored());
        assertEquals(0,a.stored()); assertEquals(Long.MAX_VALUE,b.stored());
    }
    @Test void repeatedNetworkTickDoesNotTransferTwice() {
        var a=new Port(EnergyRole.PRODUCER,0);
        var b=new Port(EnergyRole.CONSUMER,0);
        var network=new EnergyNetwork(1,Set.of(),List.of(new EnergyNetwork.Endpoint(net.minecraft.core.BlockPos.ZERO,()->a),
            new EnergyNetwork.Endpoint(net.minecraft.core.BlockPos.ZERO.above(),()->b)),false);
        network.tick(1);
        a.store.setStored(10);
        network.tick(1);
        assertEquals(0,b.stored());
        network.tick(2);
        assertEquals(10,b.stored());
    }
}

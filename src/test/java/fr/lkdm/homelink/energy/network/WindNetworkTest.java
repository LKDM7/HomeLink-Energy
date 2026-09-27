package fr.lkdm.homelink.energy.network;

import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homelink.energy.energy.*;
import fr.lkdm.homelink.energy.wind.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindNetworkTest {
    private static class Port implements HePort {
        final EnergyStore store;
        final EnergyRole role;
        final EnergySourceType type;
        Port(EnergyStore store,EnergyRole role,EnergySourceType type) { this.store=store;this.role=role;this.type=type; }
        public EnergyRole role(){return role;} public EnergySourceType sourceType(){return type;}
        public EnergyPortType type(){return role==EnergyRole.PRODUCER?EnergyPortType.OUTPUT:role==EnergyRole.CONSUMER?EnergyPortType.INPUT:EnergyPortType.BOTH;}
        public long stored(){return store.stored();} public long capacity(){return store.capacity();}
        public long insert(long n,boolean sim){return store.receive(n,sim);} public long extract(long n,boolean sim){return store.extract(n,sim);}
    }
    @Test void twoSolarWindBatteryAndConsumerAcrossWeatherAndNight() {
        var solar1=new SolarPanelCore(1000,()->0); var solar2=new SolarPanelCore(1000,()->0);
        var wind=new WindTurbineCore(1000,()->0); var battery=new EnergyStore(320000,()->0); var consumer=new EnergyStore(2,()->0);
        var stores=List.of(solar1.buffer(),solar2.buffer(),wind.buffer(),battery,consumer);
        var endpoints=new ArrayList<EnergyNetwork.Endpoint>();
        for(int i=0;i<5;i++) {
            var port=new Port(stores.get(i),i<3?EnergyRole.PRODUCER:i==3?EnergyRole.STORAGE:EnergyRole.CONSUMER,i<2?EnergySourceType.SOLAR:i==2?EnergySourceType.WIND:EnergySourceType.OTHER);
            endpoints.add(new EnergyNetwork.Endpoint(new BlockPos(i,0,0),()->port));
        }
        var network=new EnergyNetwork(1,Set.of(),endpoints,false); long consumed=0;
        for(int t=0;t<24000*6;t++) {
            int weather=t/24000%3; double sun=weather==0?1:weather==1?.4:.15; double air=weather==0?1:weather==1?1.25:1.6;
            solar1.tick(20000,true,true,t,sun); solar2.tick(20000,true,true,t,sun);
            wind.tick(28000,t,.75,air,134,true,true,true,WindParameters.DEFAULT);
            if(t%24000>12000) { assertEquals(0,solar1.potentialRate()); assertTrue(wind.rate()>0); }
            network.tick(t); consumed+=consumer.extract(2,false);
            long generated=solar1.generated()+solar2.generated()+wind.generated();
            assertEquals(generated,consumed+battery.stored()+solar1.buffer().stored()+solar2.buffer().stored()+wind.buffer().stored());
            if(t%20==19) assertEquals(network.production(),network.production(EnergySourceType.SOLAR)+network.production(EnergySourceType.WIND)+network.production(EnergySourceType.OTHER),1e-12);
        }
        assertTrue(consumed>0); assertTrue(network.production(EnergySourceType.WIND)>0);
    }
    @Test void hundredAndFiveHundredLogicalTurbines() throws Exception {
        var report=new StringBuilder("Logical benchmark, JVM warmup included. 500 consumers; shared WindState. Not client FPS.\n");
        report.append(System.getProperty("java.version")+" / "+System.getProperty("os.name")+" / CPUs="+Runtime.getRuntime().availableProcessors()+"\n");
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        for(int count:new int[]{100,500}) {
            var cores=new ArrayList<WindTurbineCore>(); var endpoints=new ArrayList<EnergyNetwork.Endpoint>(); var sinks=new ArrayList<EnergyStore>();
            for(int i=0;i<count;i++) {
                var c=new WindTurbineCore(1000,()->0); cores.add(c);
                var source=new Port(c.buffer(),EnergyRole.PRODUCER,EnergySourceType.WIND);
                var sink=new EnergyStore(Long.MAX_VALUE,()->0); sinks.add(sink); var consumer=new Port(sink,EnergyRole.CONSUMER,EnergySourceType.OTHER);
                endpoints.add(new EnergyNetwork.Endpoint(new BlockPos(i,0,0),()->source));
                endpoints.add(new EnergyNetwork.Endpoint(new BlockPos(i,1,0),()->consumer));
            }
            var network=new EnergyNetwork(1,Set.of(),endpoints,false); var state=new WindState(42);
            long allocated=bean.isThreadAllocatedMemorySupported()?bean.getThreadAllocatedBytes(Thread.currentThread().threadId()):-1;
            long started=System.nanoTime();
            for(int t=0;t<10000;t++) {
                state.tick(WindParameters.DEFAULT);
                for(var c:cores) c.tick(28000,t,state.currentStrength(),1,100,true,true,true,WindParameters.DEFAULT);
                network.tick(t);
            }
            double ms=(System.nanoTime()-started)/1e6;
            long bytes=allocated<0?-1:bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
            assertEquals(cores.stream().mapToLong(WindTurbineCore::generated).sum(),sinks.stream().mapToLong(EnergyStore::stored).sum()+cores.stream().mapToLong(c->c.buffer().stored()).sum());
            report.append(String.format(Locale.ROOT,"turbines=%d ticks=10000 totalMs=%.3f msPerTick=%.6f allocatedBytes=%d%n",count,ms,ms/10000,bytes));
        }
        Files.writeString(Path.of(System.getProperty("energy.reportDir"),"wind-logical-load.txt"),report);
    }
}

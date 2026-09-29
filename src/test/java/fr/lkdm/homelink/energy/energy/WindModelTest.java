package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homelink.energy.wind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindModelTest {
    private static final WindParameters P=WindParameters.DEFAULT;
    private static long tick(WindTurbineCore core,double wind) { return core.tick(28000,0,wind,1,100,true,true,true,P); }
    @Test void allNominalsWithinOneHeFloatingPointTolerance() {
        for (long nominal : new long[]{3000,12000,28000}) {
            var core=new WindTurbineCore(Long.MAX_VALUE,()->0);
            for(int t=0;t<24000;t++) core.tick(nominal,t,1,1,100,true,true,true,P);
            assertTrue(Math.abs(core.generated()-nominal)<=1, "floor rounding is strictly less than 1 HE plus floating-point error");
            assertEquals(nominal,core.generated()+core.fraction().fraction(),1e-7);
        }
    }
    @Test void weatherAltitudeAndCap() {
        assertEquals(1,P.weather(Weather.CLEAR)); assertEquals(1.25,P.weather(Weather.RAIN));
        assertEquals(1.6,P.weather(Weather.of(true,true)));
        int[] y={79,80,119,120,179,180}; double[] expected={.8,1,1,1.15,1.15,1.25};
        for(int i=0;i<y.length;i++) assertEquals(expected[i],P.altitude(y[i]));
        assertEquals(1.5,P.efficiency(1,1.6,180)); assertEquals(1.15,P.efficiency(.8,1.25,134),1e-12);
    }
    @Test void hysteresisAndBlocking() {
        var core=new WindTurbineCore(1000,()->0);
        tick(core,.12); assertEquals(WindStatus.NO_WIND,core.status());
        tick(core,.14); assertFalse(core.running()); tick(core,.16); assertTrue(core.running());
        tick(core,.20); assertEquals(WindStatus.GENERATING,core.status());
        tick(core,.14); assertTrue(core.running()); tick(core,.13); assertFalse(core.running());
        core.tick(28000,1,1,1,100,true,false,true,P); assertEquals(WindStatus.ROTOR_OBSTRUCTED,core.status()); assertEquals(0,core.rate());
        core.tick(28000,2,1,1,100,true,true,false,P); assertEquals(WindStatus.SKY_BLOCKED,core.status());
        core.tick(28000,3,1,1,100,false,true,true,P); assertEquals(WindStatus.UNSUPPORTED_DIMENSION,core.status());
        tick(core,1); assertEquals(WindStatus.GENERATING,core.status());
    }
    @Test void fullBufferDiscardsWholeEnergyAndSkipNeverCatchesUp() {
        var core=new WindTurbineCore(1,()->0);
        for(int i=0;i<24000;i++) tick(core,1);
        assertEquals(1,core.buffer().stored()); assertTrue(core.lost()>27000); assertTrue(core.fraction().fraction()<1);
        core.buffer().extract(1,false); long before=core.generated();
        core.tick(28000,2400000,1,1,100,true,true,true,P);
        assertTrue(core.generated()-before<=2); assertTrue(core.generatedPeriod()<=2);
    }
    @Test void switchedOffTurbineProducesNothingUntilSwitchedOn() {
        var core=new WindTurbineCore(Long.MAX_VALUE,()->0);
        for(int t=0;t<2400;t++) core.tick(28000,t,1,1,100,true,true,true,true,false,P);
        assertEquals(0,core.generated()); assertEquals(WindStatus.SWITCHED_OFF,core.status());
        core.tick(28000,2400,1,1,100,true,true,true,true,true,P);
        assertEquals(WindStatus.GENERATING,core.status());
    }
}

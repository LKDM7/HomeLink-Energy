package fr.lkdm.homelink.energy.energy;

import fr.lkdm.homelink.energy.wind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reproducible 100-period balance study, executing the actual wind and generation engines. */
class WindSimulationTest {
    @Test void hundredPeriods() throws Exception {
        int[] heights={100,100,100,79,134,180,100};
        String[] names={"clear-normal","rain-normal","thunder-normal","clear-low","clear-high","clear-very-high","mixed-80clear-10rain-10thunder"};
        long[] nominal={3000,12000,28000};
        WindTurbineCore[][] cores=new WindTurbineCore[7][3];
        long[][][] periods=new long[7][3][100]; long[][] before=new long[7][3]; long[] idle=new long[7];
        for(int s=0;s<7;s++) for(int tier=0;tier<3;tier++) cores[s][tier]=new WindTurbineCore(Long.MAX_VALUE,()->0);
        var wind=new WindState(20260927); var p=WindParameters.DEFAULT;
        double windSum=0;
        for(int t=0;t<2400000;t++) {
            wind.tick(p); windSum+=wind.currentStrength();
            for(int s=0;s<7;s++) {
                double weather=s==1?1.25:s==2?1.6:s==6?(t/24000%10==8?1.25:t/24000%10==9?1.6:1):1;
                for(int tier=0;tier<3;tier++) cores[s][tier].tick(nominal[tier],t,wind.currentStrength(),weather,heights[s],true,true,true,p);
                if(cores[s][0].rate()==0) idle[s]++;
                if(t%24000==23999) for(int tier=0;tier<3;tier++) {
                    long total=cores[s][tier].generated(); periods[s][tier][t/24000]=total-before[s][tier]; before[s][tier]=total;
                }
            }
        }
        var out=new StringBuilder("seed=20260927 ticks=2400000 periods=100; unlimited storage; no obstruction\n");
        out.append(String.format(Locale.ROOT,"meanWind=%.6f%n",windSum/2400000));
        out.append("scenario tier meanHE minHE maxHE stddevHE idlePercent\n");
        for(int s=0;s<7;s++) for(int tier=0;tier<3;tier++) {
            var stats=Arrays.stream(periods[s][tier]).summaryStatistics(); double avg=stats.getAverage();
            double variance=Arrays.stream(periods[s][tier]).mapToDouble(v->(v-avg)*(v-avg)).average().orElseThrow();
            out.append(String.format(Locale.ROOT,"%s %d %.2f %d %d %.2f %.4f%n",names[s],tier+1,avg,stats.getMin(),stats.getMax(),Math.sqrt(variance),idle[s]*100./2400000));
            assertTrue(stats.getMax()<=nominal[tier]*1.5+1); assertTrue(avg>0); assertEquals(0,cores[s][tier].lost());
        }
        Path file=Path.of(System.getProperty("energy.reportDir"),"wind-simulation.txt"); Files.createDirectories(file.getParent()); Files.writeString(file,out);
    }
}

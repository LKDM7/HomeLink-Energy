package fr.lkdm.homelink.energy.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Writes the measured cycle totals to build/reports/solar-cycles.txt for ENERGY_BALANCE.md. */
class SolarReportTest {
    private long now;

    @Test void writeCycleReport() throws IOException {
        var out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "S = %.6f%n", SolarCurve.sum()));
        out.append("tier  perCycle  weather  expected  produced  peakHE/t\n");
        String[] names = {"clear", "rain", "thunder"};
        double[] efficiencies = {1.0, 0.40, 0.15};
        long[] tiers = {2_000, 8_000, 20_000};
        for (int tier = 0; tier < 3; tier++) {
            for (int w = 0; w < 3; w++) {
                var panel = new SolarPanelCore(1_000, () -> now);
                long produced = 0;
                for (int t = 0; t < SolarCurve.CYCLE_TICKS; t++) {
                    now++;
                    panel.tick(tiers[tier], true, true, t, efficiencies[w]);
                    produced += panel.buffer().extract(Long.MAX_VALUE, false);
                }
                double expected = tiers[tier] * efficiencies[w];
                assertTrue(Math.abs(produced - expected) <= 1);
                out.append(String.format(Locale.ROOT, "%-5s %8d  %-7s  %8.1f  %8d  %.4f%n", "I".repeat(tier + 1), tiers[tier], names[w],
                        expected, produced, SolarGenerator.potential(tiers[tier], 6_000, efficiencies[w])));
            }
        }
        Path report = Path.of(System.getProperty("energy.reportDir", "build/reports"), "solar-cycles.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out);
    }
}

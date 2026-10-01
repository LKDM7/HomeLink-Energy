package fr.lkdm.homelink.energy.hydro;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Hydraulic graph rules on a synthetic world: ports, limits, loops, unloaded cells and budgets. */
class HydroGraphTest {
    /** Pumps have their port on top of the master; the synthetic turbine's inlet is the cell above its master. */
    private static final class World implements HydroGraphScan.View {
        final Map<BlockPos, HydroGraphScan.Node> nodes = new HashMap<>();
        @Override public HydroGraphScan.Node at(BlockPos pos) { return nodes.getOrDefault(pos, HydroGraphScan.Node.EMPTY); }
        void pipe(int x, int y, int z) { nodes.put(new BlockPos(x, y, z), HydroGraphScan.Node.PIPE); }
        void line(int x0, int x1, int y, int z) { for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) pipe(x, y, z); }
        BlockPos pump(int x, int y, int z) {
            BlockPos master = new BlockPos(x, y, z);
            nodes.put(master, new HydroGraphScan.Node(HydroGraphScan.Kind.PUMP, master, Direction.UP));
            nodes.put(master.east(), new HydroGraphScan.Node(HydroGraphScan.Kind.PUMP, master, null));
            return master;
        }
        BlockPos turbine(int x, int y, int z) {
            BlockPos master = new BlockPos(x, y, z);
            nodes.put(master, new HydroGraphScan.Node(HydroGraphScan.Kind.TURBINE, master, null));
            nodes.put(master.above(), new HydroGraphScan.Node(HydroGraphScan.Kind.TURBINE, master, Direction.UP));
            return master;
        }
        HydroCircuit fromPump(BlockPos pump, int maxPipes) { return finish(new HydroGraphScan(this, pump, pump, Direction.UP, true, maxPipes)); }
        HydroCircuit fromTurbine(BlockPos turbine, int maxPipes) { return finish(new HydroGraphScan(this, turbine, turbine.above(), Direction.UP, false, maxPipes)); }
        static HydroCircuit finish(HydroGraphScan scan) {
            int guard = 0;
            while (!scan.done()) { scan.step(128); assertTrue(++guard < 10_000); }
            return scan.result();
        }
    }

    @Test void pumpPipesTurbine() {
        var w = new World();
        var pump = w.pump(0, 0, 0);
        w.pipe(0, 1, 0);
        w.line(1, 4, 1, 0);
        var turbine = w.turbine(4, -1, 0);
        var circuit = w.fromPump(pump, 512);
        assertEquals(5, circuit.pipes().size());
        assertEquals(1, circuit.pumps().size());
        assertEquals(1, circuit.turbines().size());
        assertTrue(circuit.turbines().contains(turbine));
        assertEquals(HydroStatus.Circuit.VALID, circuit.status(4));
        // The same circuit seen from the turbine.
        var back = w.fromTurbine(turbine, 512);
        assertEquals(circuit.pipes(), back.pipes());
        assertEquals(circuit.pumps(), back.pumps());
    }

    @Test void wrongFaceDoesNotConnect() {
        var w = new World();
        var pump = w.pump(0, 0, 0);
        w.pipe(-1, 0, 0);
        w.pipe(-1, 1, 0);
        // Pipe next to the pump body and next to its non-port part: never a connection.
        w.pipe(1, 1, 1);
        var circuit = w.fromPump(pump, 512);
        assertTrue(circuit.pipes().isEmpty());
        var turbine = w.turbine(10, 0, 0);
        w.pipe(11, 0, 0);
        assertTrue(w.fromTurbine(turbine, 512).pipes().isEmpty(), "pipe beside the turbine casing is not an inlet");
    }

    @Test void branchesAndLoopsNeverMultiplyMachines() {
        var w = new World();
        var a = w.pump(0, 0, 0);
        var b = w.pump(6, 0, 0);
        w.pipe(0, 1, 0);
        w.pipe(6, 1, 0);
        w.line(0, 6, 2, 0);
        w.line(0, 6, 2, 2);
        w.pipe(0, 2, 1);
        w.pipe(6, 2, 1);
        w.pipe(3, 2, 1);
        var turbine = w.turbine(3, 0, 1);
        w.pipe(3, 2, 1);
        var circuit = w.fromTurbine(turbine, 512);
        assertEquals(2, circuit.pumps().size());
        assertTrue(circuit.pumps().contains(a) && circuit.pumps().contains(b));
        assertEquals(1, circuit.turbines().size());
        var allocation = circuit.allocate(1, pos -> 6, true, 6);
        assertEquals(6, allocation.used(), 1e-12);
    }

    @Test void limits() {
        var w = new World();
        BlockPos[] pumps = new BlockPos[5];
        for (int i = 0; i < 5; i++) { pumps[i] = w.pump(i * 3, 0, 0); w.pipe(i * 3, 1, 0); }
        w.line(0, 12, 2, 0);
        w.turbine(12, 0, 2);
        w.pipe(12, 2, 1);
        w.pipe(12, 2, 2);
        var many = w.fromPump(pumps[0], 512);
        assertEquals(5, many.pumps().size());
        assertEquals(HydroStatus.Circuit.TOO_MANY_PUMPS, many.status(4));
        assertEquals(HydroStatus.Circuit.VALID, many.status(5), "configurable pump limit");

        var long_ = new World();
        var pump = long_.pump(0, 0, 0);
        long_.pipe(0, 1, 0);
        long_.line(0, 700, 2, 0);
        var big = long_.fromPump(pump, 512);
        assertTrue(big.tooLarge());
        assertEquals(HydroStatus.Circuit.TOO_LARGE, big.status(4));
        assertTrue(big.pipes().size() <= 512);

        var twin = new World();
        var p = twin.pump(0, 0, 0);
        twin.pipe(0, 1, 0);
        twin.line(-3, 3, 2, 0);
        twin.turbine(-3, 0, 0);
        twin.turbine(3, 0, 0);
        var two = twin.fromPump(p, 512);
        assertEquals(2, two.turbines().size());
        assertEquals(HydroStatus.Circuit.MULTIPLE_TURBINES, two.status(4));
    }

    @Test void noPumpUnloadedAndIndependentCircuits() {
        var w = new World();
        var turbine = w.turbine(0, 0, 0);
        w.pipe(0, 2, 0);
        w.line(0, 5, 3, 0);
        assertTrue(w.fromTurbine(turbine, 512).pumps().isEmpty());
        w.nodes.put(new BlockPos(3, 3, 0), HydroGraphScan.Node.UNLOADED);
        var cut = w.fromTurbine(turbine, 512);
        assertTrue(cut.incomplete());
        assertEquals(HydroStatus.Circuit.INCOMPLETE, cut.status(4));
        assertFalse(cut.pipes().contains(new BlockPos(5, 3, 0)), "no traversal assumed across an unloaded cell");

        var two = new World();
        var a = two.pump(0, 0, 0); two.pipe(0, 1, 0); two.line(0, 3, 2, 0); var ta = two.turbine(3, 0, 0);
        var b = two.pump(0, 0, 3); two.pipe(0, 1, 3); two.line(0, 3, 2, 3); var tb = two.turbine(3, 0, 3);
        var first = two.fromPump(a, 512);
        var second = two.fromPump(b, 512);
        assertTrue(first.turbines().contains(ta) && !first.turbines().contains(tb));
        assertTrue(second.turbines().contains(tb) && !second.pumps().contains(a));
    }

    @Test void budgetedScanResumes() {
        var w = new World();
        var pump = w.pump(0, 0, 0);
        w.pipe(0, 1, 0);
        w.line(0, 400, 2, 0);
        w.turbine(400, 0, 0);
        var scan = new HydroGraphScan(w, pump, pump, Direction.UP, true, 512);
        int ticks = 0;
        while (!scan.done()) { assertTrue(scan.step(128) <= 128); ticks++; }
        assertTrue(ticks >= 4, "a 402-pipe circuit needs several budgeted ticks");
        assertEquals(1, scan.result().turbines().size());
        assertTrue(scan.touches(new BlockPos(200, 2, 0)));
        assertTrue(scan.touches(new BlockPos(200, 3, 0)), "a block placed against a scanned pipe restarts the scan");
    }

    @Test void hundredCircuitsLogicalLoad() throws Exception {
        var w = new World();
        var starts = new BlockPos[100];
        for (int c = 0; c < 100; c++) {
            int z = c * 4;
            for (int i = 0; i < 4; i++) { var pump = w.pump(i * 3, 0, z); w.pipe(i * 3, 1, z); if (i == 0) starts[c] = pump; }
            w.line(0, 48, 2, z);
            w.turbine(48, 0, z);
        }
        long started = System.nanoTime();
        int visits = 0;
        var circuits = new HydroCircuit[100];
        for (int c = 0; c < 100; c++) {
            var scan = new HydroGraphScan(w, starts[c], starts[c], Direction.UP, true, 512);
            while (!scan.done()) visits += scan.step(128);
            circuits[c] = scan.result();
        }
        double buildMs = (System.nanoTime() - started) / 1e6;
        started = System.nanoTime();
        var cores = new HydroTurbineCore[100];
        for (int c = 0; c < 100; c++) cores[c] = new HydroTurbineCore(Long.MAX_VALUE, () -> 0);
        for (int t = 0; t < 24_000; t++)
            for (int c = 0; c < 100; c++) {
                var allocation = circuits[c].allocate(t, pos -> 1.5, true, 6);
                cores[c].tick(t, allocation.used(), 6, 24_000);
            }
        double tickMs = (System.nanoTime() - started) / 1e6;
        for (var core : cores) assertTrue(Math.abs(core.generated() - 24_000) <= 1);
        for (var circuit : circuits) assertEquals(4, circuit.pumps().size());
        String report = String.format(Locale.ROOT, "Logical Hydro load (JVM warm-up included, no world, not a TPS figure)%n"
                        + "java=%s os=%s cpus=%d%ncircuits=100 pumps=400 pipes=%d buildVisits=%d buildMs=%.3f%n"
                        + "allocation+production ticks=24000 totalMs=%.3f usPerCircuitTick=%.4f%n",
                System.getProperty("java.version"), System.getProperty("os.name"), Runtime.getRuntime().availableProcessors(),
                circuits[0].pipes().size() * 100, visits, buildMs, tickMs, tickMs * 1000 / (24_000.0 * 100));
        Path dir = Path.of(System.getProperty("energy.reportDir", "build/reports"));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("hydro-logical-load.txt"), report);
    }
}

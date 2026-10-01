package fr.lkdm.homelink.energy.hydro;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** Geometry and flood fill of the pump water windows, on a synthetic world. */
class WaterWindowTest {
    private static final BlockPos ORIGIN = new BlockPos(100, 64, 100);

    /** Synthetic world: listed cells, everything else OTHER (air, stone...). */
    private static final class World implements WaterWindow.Probe {
        final Map<BlockPos, WaterWindow.Cell> cells = new HashMap<>();
        @Override public WaterWindow.Cell at(BlockPos pos) { return cells.getOrDefault(pos, WaterWindow.Cell.OTHER); }
        void fill(WaterWindow window) {
            for (BlockPos pos : BlockPos.betweenClosed(window.min(), window.max())) cells.put(pos.immutable(), WaterWindow.Cell.SOURCE);
        }
    }

    private static WaterWindow window(HydroPumpTier tier, Direction facing) {
        var p = HydroParameters.DEFAULT;
        return WaterWindow.of(ORIGIN, facing, tier.width(), p.windowWidth(tier), p.windowDistance(tier), p.windowDepth(tier));
    }

    @Test void windowSizesAndCentering() {
        assertEquals(18, window(HydroPumpTier.I, Direction.NORTH).cells());
        assertEquals(50, window(HydroPumpTier.II, Direction.NORTH).cells());
        assertEquals(147, window(HydroPumpTier.III, Direction.NORTH).cells());
        // Pump I facing north: columns -1..1 around the intake, rows 1..3 to the north, Y 63..64.
        var one = window(HydroPumpTier.I, Direction.NORTH);
        assertEquals(new BlockPos(99, 63, 97), one.min());
        assertEquals(new BlockPos(101, 64, 99), one.max());
        // Even width: Pump II spans columns -1..3, half a block toward the clockwise side.
        var two = window(HydroPumpTier.II, Direction.NORTH);
        assertEquals(99, two.min().getX());
        assertEquals(103, two.max().getX());
        var three = window(HydroPumpTier.III, Direction.EAST);
        // Facing east: clockwise is south, so columns -2..4 run along Z, rows 1..7 along +X, depth 3.
        assertEquals(new BlockPos(101, 62, 98), three.min());
        assertEquals(new BlockPos(107, 64, 104), three.max());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var w = window(HydroPumpTier.III, facing);
            assertEquals(147, w.cells(), facing.toString());
            for (BlockPos seed : w.seeds()) assertTrue(w.contains(seed), "seed outside window " + facing);
            assertFalse(w.contains(ORIGIN), "the pump itself is never in its window");
        }
    }

    @Test void fullBasinIsFullAvailability() {
        for (HydroPumpTier tier : HydroPumpTier.values()) {
            var w = window(tier, Direction.SOUTH);
            var world = new World();
            world.fill(w);
            var result = w.scan(world);
            assertEquals(w.cells(), result.sources());
            assertEquals(1.0, result.availability(), 1e-12);
            assertFalse(result.unknown());
        }
    }

    @Test void isolatedSourceIsNotFullOutput() {
        var w = window(HydroPumpTier.III, Direction.NORTH);
        var world = new World();
        world.cells.put(ORIGIN.north(), WaterWindow.Cell.SOURCE);
        var result = w.scan(world);
        assertEquals(1, result.sources());
        assertTrue(result.availability() < 0.25);
    }

    @Test void flowingWaterWaterloggedLavaAndWallsDoNotCount() {
        var w = window(HydroPumpTier.I, Direction.NORTH);
        var world = new World();
        // Flowing water, waterlogged blocks and lava are reported as OTHER by the block entity probe.
        assertEquals(0, w.scan(world).sources(), "only non-source cells");
        // A basin behind a wall: sources two rows away, row 1 is a wall.
        for (BlockPos pos : BlockPos.betweenClosed(w.min(), w.max())) if (pos.getZ() <= ORIGIN.getZ() - 2) world.cells.put(pos.immutable(), WaterWindow.Cell.SOURCE);
        assertEquals(0, w.scan(world).sources(), "water behind a wall is not reachable from the intake");
        // Intake obstructed: open the side of the wall but keep the cell directly in front blocked.
        world.cells.put(ORIGIN.north().west(), WaterWindow.Cell.SOURCE);
        world.cells.put(ORIGIN.north().west().below(), WaterWindow.Cell.SOURCE);
        assertEquals(0, w.scan(world).sources(), "the intake cell itself must hold water");
        world.cells.put(ORIGIN.north(), WaterWindow.Cell.SOURCE);
        assertTrue(w.scan(world).sources() > 10, "connected once the intake touches the basin");
    }

    @Test void sourcesCountOnceAndStayInsideTheWindow() {
        var w = window(HydroPumpTier.II, Direction.WEST);
        var world = new World();
        world.fill(w);
        // A huge lake around the window must not raise the count above the window size.
        for (BlockPos pos : BlockPos.betweenClosed(w.min().offset(-10, -5, -10), w.max().offset(10, 0, 10)))
            world.cells.putIfAbsent(pos.immutable(), WaterWindow.Cell.SOURCE);
        assertEquals(w.cells(), w.scan(world).sources());
    }

    @Test void unloadedPartIsUnknownNotEmpty() {
        var w = window(HydroPumpTier.III, Direction.NORTH);
        var world = new World();
        world.fill(w);
        world.cells.put(w.max(), WaterWindow.Cell.UNLOADED);
        var result = w.scan(world);
        assertTrue(result.unknown());
        assertEquals(0, result.availability());
        world.cells.put(w.max(), WaterWindow.Cell.OTHER);
        assertFalse(w.scan(world).unknown());
    }

    @Test void removingAndRestoringWater() {
        var w = window(HydroPumpTier.I, Direction.NORTH);
        var world = new World();
        world.fill(w);
        assertEquals(18, w.scan(world).sources());
        world.cells.clear();
        assertEquals(0, w.scan(world).sources());
        world.fill(w);
        assertEquals(18, w.scan(world).sources());
        assertTrue(w.affectedBy(w.min().below()), "the edge just outside invalidates the cache");
        assertFalse(w.affectedBy(w.max().offset(5, 0, 5)));
    }
}

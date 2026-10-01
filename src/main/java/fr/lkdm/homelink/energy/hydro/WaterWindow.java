package fr.lkdm.homelink.energy.hydro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Local water window of a pump, in front of its intake and extending downward from the intake level.
 *
 * <p>Columns run along {@code facing.getClockWise()} from the master column 0. The window starts
 * {@code floor((windowWidth - machineWidth) / 2)} columns before column 0, so it is exactly centered
 * on an odd footprint and shifted half a block to the clockwise side on an even one (Pump II:
 * columns -1..3; Pump III: columns -2..4). Rows 1..distance lie in front of the intake; layers run
 * from the intake level down to {@code depth - 1} blocks below it.</p>
 *
 * <p>Only water sources reachable face to face from the cells directly in front of the intake count,
 * each once. A cell that is not a water source (air, flowing water, waterlogged block, lava, wall)
 * stops the flood fill. Any unloaded cell makes the result unknown; nothing is loaded to answer.</p>
 */
public final class WaterWindow {
    public enum Cell { SOURCE, OTHER, UNLOADED }

    /** Reads one cell; implementations must not load chunks. */
    @FunctionalInterface
    public interface Probe { Cell at(BlockPos pos); }

    /**
     * @param sources reachable water sources
     * @param cells cells in the window, i.e. sources needed for full output
     * @param unknown whether part of the window was unloaded
     */
    public record Result(int sources, int cells, boolean unknown) {
        public double availability() { return unknown || cells <= 0 ? 0 : Math.max(0, Math.min(1, sources / (double) cells)); }
    }

    private final BlockPos min, max;
    private final List<BlockPos> seeds;
    private final int cells;

    private WaterWindow(BlockPos min, BlockPos max, List<BlockPos> seeds) {
        this.min = min;
        this.max = max;
        this.seeds = seeds;
        this.cells = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    /**
     * @param origin pump master position (column 0, front row)
     * @param facing direction of the intake
     * @param machineWidth pump width in blocks
     * @param width window width
     * @param distance window distance in front of the intake
     * @param depth window vertical depth, intake level included
     */
    public static WaterWindow of(BlockPos origin, Direction facing, int machineWidth, int width, int distance, int depth) {
        Direction right = facing.getClockWise();
        int first = -Math.floorDiv(width - machineWidth, 2);
        BlockPos a = origin.relative(right, first).relative(facing, 1).below(depth - 1);
        BlockPos b = origin.relative(right, first + width - 1).relative(facing, distance);
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()));
        List<BlockPos> seeds = new ArrayList<>(machineWidth);
        for (int column = 0; column < machineWidth; column++) seeds.add(origin.relative(right, column).relative(facing).immutable());
        return new WaterWindow(min, max, List.copyOf(seeds));
    }

    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public List<BlockPos> seeds() { return seeds; }
    public int cells() { return cells; }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** @return whether a change at {@code pos} can alter the scan (window or its intake seeds' surroundings) */
    public boolean affectedBy(BlockPos pos) {
        return pos.getX() >= min.getX() - 1 && pos.getX() <= max.getX() + 1 && pos.getY() >= min.getY() - 1 && pos.getY() <= max.getY() + 1
                && pos.getZ() >= min.getZ() - 1 && pos.getZ() <= max.getZ() + 1;
    }

    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /** Flood fill from the intake seeds, bounded by the window: at most {@link #cells()} visits. */
    public Result scan(Probe probe) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++)
                if (probe.at(new BlockPos(Math.max(min.getX(), x << 4), min.getY(), Math.max(min.getZ(), z << 4))) == Cell.UNLOADED)
                    return new Result(0, cells, true);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos seed : seeds) {
            Cell cell = probe.at(seed);
            if (cell == Cell.UNLOADED) return new Result(0, cells, true);
            if (cell == Cell.SOURCE && visited.add(seed)) queue.add(seed);
        }
        int sources = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            sources++;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!contains(next) || visited.contains(next)) continue;
                Cell cell = probe.at(next);
                if (cell == Cell.UNLOADED) return new Result(0, cells, true);
                if (cell == Cell.SOURCE) { visited.add(next); queue.add(next); }
            }
        }
        return new Result(sources, cells, false);
    }
}

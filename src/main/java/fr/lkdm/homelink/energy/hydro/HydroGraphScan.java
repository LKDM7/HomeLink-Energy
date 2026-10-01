package fr.lkdm.homelink.energy.hydro;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Incremental breadth-first exploration of one hydraulic circuit. It can stop after any visit budget
 * and resume on a later tick. Pipes connect face to face to pipes; a pipe connects to a machine only on
 * that machine's declared port face. Several faces or parts of one machine resolve to one master.
 * An unloaded cell is never assumed to continue the circuit: the circuit is then INCOMPLETE.
 */
public final class HydroGraphScan {
    public enum Kind { EMPTY, UNLOADED, PIPE, PUMP, TURBINE }

    /**
     * @param kind what occupies the cell
     * @param master master of the machine owning the cell, or null
     * @param portFace face of this cell where a pipe may attach, or null
     */
    public record Node(Kind kind, @Nullable BlockPos master, @Nullable Direction portFace) {
        public static final Node EMPTY = new Node(Kind.EMPTY, null, null);
        public static final Node UNLOADED = new Node(Kind.UNLOADED, null, null);
        public static final Node PIPE = new Node(Kind.PIPE, null, null);
    }

    /** Read-only view of the world; never loads a chunk. */
    @FunctionalInterface
    public interface View { Node at(BlockPos pos); }

    private final View view;
    private final int maxPipes;
    private final BlockPos start;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> pipes = new LinkedHashSet<>();
    private final Set<BlockPos> pumps = new LinkedHashSet<>();
    private final Set<BlockPos> turbines = new LinkedHashSet<>();
    private final Set<BlockPos> ports = new HashSet<>();
    private final Set<Long> chunks = new HashSet<>();
    private boolean incomplete, tooLarge, done;
    private int visits;

    /**
     * @param view world view
     * @param start master of the machine that asked for its circuit
     * @param portCell cell holding that machine's hydraulic port
     * @param portFace face of the port
     * @param pump whether the machine is a pump (else a turbine)
     * @param maxPipes pipe limit
     */
    public HydroGraphScan(View view, BlockPos start, BlockPos portCell, Direction portFace, boolean pump, int maxPipes) {
        this.view = view;
        this.start = start.immutable();
        this.maxPipes = maxPipes;
        (pump ? pumps : turbines).add(this.start);
        ports.add(portCell.immutable());
        chunks.add(ChunkPos.asLong(start));
        chunks.add(ChunkPos.asLong(portCell));
        BlockPos first = portCell.relative(portFace);
        chunks.add(ChunkPos.asLong(first));
        Node node = view.at(first);
        if (node.kind() == Kind.UNLOADED) incomplete = true;
        else if (node.kind() == Kind.PIPE) { pipes.add(first.immutable()); queue.add(first.immutable()); }
    }

    /**
     * Explores up to {@code budget} pipes.
     *
     * @return visits consumed
     */
    public int step(int budget) {
        int used = 0;
        while (!done && used < budget) {
            BlockPos pipe = queue.poll();
            if (pipe == null) { done = true; break; }
            used++;
            visits++;
            for (Direction direction : Direction.values()) {
                BlockPos next = pipe.relative(direction);
                if (pipes.contains(next)) continue;
                Node node = view.at(next);
                switch (node.kind()) {
                    case UNLOADED -> { incomplete = true; chunks.add(ChunkPos.asLong(next)); }
                    case PIPE -> {
                        if (pipes.size() >= maxPipes) { tooLarge = true; done = true; return used; }
                        BlockPos immutable = next.immutable();
                        pipes.add(immutable);
                        queue.add(immutable);
                        chunks.add(ChunkPos.asLong(next));
                    }
                    case PUMP, TURBINE -> {
                        if (node.portFace() != direction.getOpposite() || node.master() == null) break;
                        ports.add(next.immutable());
                        chunks.add(ChunkPos.asLong(next));
                        chunks.add(ChunkPos.asLong(node.master()));
                        (node.kind() == Kind.PUMP ? pumps : turbines).add(node.master().immutable());
                    }
                    default -> { }
                }
            }
        }
        return used;
    }

    public boolean done() { return done; }
    public BlockPos start() { return start; }
    public int visits() { return visits; }

    /** @return whether a change at {@code pos} touches what this scan already explored */
    public boolean touches(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos near = pos.relative(direction);
            if (pipes.contains(near) || ports.contains(near)) return true;
        }
        return pipes.contains(pos) || ports.contains(pos) || pumps.contains(pos) || turbines.contains(pos);
    }

    public boolean touchesChunk(long chunk) { return chunks.contains(chunk); }

    /** @return the circuit; only meaningful once {@link #done()} */
    public HydroCircuit result() {
        return new HydroCircuit(Set.copyOf(pipes), Set.copyOf(pumps), Set.copyOf(turbines), Set.copyOf(ports), Set.copyOf(chunks), incomplete, tooLarge);
    }
}

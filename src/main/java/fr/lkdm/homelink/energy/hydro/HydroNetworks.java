package fr.lkdm.homelink.energy.hydro;

import fr.lkdm.homelink.energy.block.HydroMachineBlock;
import fr.lkdm.homelink.energy.block.HydroPipeBlock;
import fr.lkdm.homelink.energy.block.HydroPumpBlock;
import fr.lkdm.homelink.energy.block.HydroTurbineBlock;
import fr.lkdm.homelink.energy.config.HydroConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Hydraulic circuits of one server level, separate from HE cables and from HomeNetworks. Circuits are
 * built on request by loaded machines, indexed by node and by chunk, and discarded on any relevant
 * change (pipe or machine placed/removed, chunk loaded/unloaded, configuration reload). Rebuilds share
 * a visit budget per tick and dimension; a discarded circuit never keeps producing from its old cache.
 */
public final class HydroNetworks {
    private static final Map<ServerLevel, HydroNetworks> LEVELS = new WeakHashMap<>();
    private static volatile long configGeneration;

    private final ServerLevel level;
    private final Map<BlockPos, HydroCircuit> byNode = new HashMap<>();
    private final Map<Long, Set<HydroCircuit>> byChunk = new HashMap<>();
    private final LinkedHashSet<BlockPos> requests = new LinkedHashSet<>();
    @Nullable private HydroGraphScan current;
    private long generation = configGeneration;
    private long rebuilds, visits, discarded;

    private HydroNetworks(ServerLevel level) { this.level = level; }

    public static HydroNetworks get(ServerLevel level) { return LEVELS.computeIfAbsent(level, HydroNetworks::new); }
    public static Optional<HydroNetworks> existing(ServerLevel level) { return Optional.ofNullable(LEVELS.get(level)); }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }
    /** Limits changed: every circuit of every level is rebuilt lazily. */
    public static void configChanged() { configGeneration++; }

    /**
     * @param master master position of a loaded pump or turbine
     * @return its circuit, or empty while it is pending (the request is queued)
     */
    public Optional<HydroCircuit> circuit(BlockPos master) {
        HydroCircuit circuit = byNode.get(master);
        if (circuit == null) requests.add(master.immutable());
        return Optional.ofNullable(circuit);
    }

    /** A pipe or machine cell changed at {@code pos}. */
    public void changed(BlockPos pos) {
        drop(byNode.get(pos));
        for (Direction direction : Direction.values()) drop(byNode.get(pos.relative(direction)));
        if (current != null && current.touches(pos)) restart();
    }

    /** A chunk was loaded or unloaded. */
    public void chunkChanged(long chunk) {
        Set<HydroCircuit> touched = byChunk.get(chunk);
        if (touched != null) for (HydroCircuit circuit : new ArrayList<>(touched)) drop(circuit);
        if (current != null && current.touchesChunk(chunk)) restart();
    }

    private void restart() {
        requests.add(current.start());
        current = null;
    }

    private void drop(@Nullable HydroCircuit circuit) {
        if (circuit == null) return;
        discarded++;
        for (BlockPos node : nodes(circuit)) byNode.remove(node, circuit);
        for (Long chunk : circuit.chunks()) {
            Set<HydroCircuit> set = byChunk.get(chunk);
            if (set != null) { set.remove(circuit); if (set.isEmpty()) byChunk.remove(chunk); }
        }
    }

    private static Iterable<BlockPos> nodes(HydroCircuit circuit) {
        Set<BlockPos> all = new HashSet<>(circuit.pipes());
        all.addAll(circuit.pumps());
        all.addAll(circuit.turbines());
        all.addAll(circuit.ports());
        return all;
    }

    /** Runs the rebuild budget of this tick. Server thread, before block entities tick. */
    public void tick() {
        if (generation != configGeneration) {
            generation = configGeneration;
            for (HydroCircuit circuit : new HashSet<>(byNode.values())) drop(circuit);
            current = null;
        }
        HydroParameters parameters = HydroConfig.parameters();
        int budget = parameters.visitsPerTick();
        while (budget > 0) {
            if (current == null && !startNext(parameters)) return;
            budget -= Math.max(1, current.step(budget));
            if (current.done()) register(current.result());
        }
    }

    private boolean startNext(HydroParameters parameters) {
        Iterator<BlockPos> iterator = requests.iterator();
        while (iterator.hasNext()) {
            BlockPos start = iterator.next();
            iterator.remove();
            if (byNode.containsKey(start) || !level.isLoaded(start)) continue;
            BlockState state = level.getBlockState(start);
            if (!(state.getBlock() instanceof HydroMachineBlock) || !HydroMachineBlock.isController(state)) continue;
            Direction facing = state.getValue(HydroMachineBlock.FACING);
            boolean pump = state.getBlock() instanceof HydroPumpBlock;
            BlockPos portCell = pump ? HydroLayout.pumpOutlet(start) : HydroLayout.turbineInlet(start, facing);
            Direction face = pump ? HydroLayout.pumpOutletFace() : HydroLayout.turbineInletFace();
            current = new HydroGraphScan(this::view, start, portCell, face, pump, parameters.maxPipes());
            return true;
        }
        return false;
    }

    private void register(HydroCircuit circuit) {
        current = null;
        rebuilds++;
        visits += circuit.pipes().size();
        for (BlockPos node : nodes(circuit)) {
            drop(byNode.get(node));
            byNode.put(node, circuit);
        }
        for (Long chunk : circuit.chunks()) byChunk.computeIfAbsent(chunk, key -> new HashSet<>()).add(circuit);
    }

    /** World view for the scan: reads loaded cells only. */
    private HydroGraphScan.Node view(BlockPos pos) {
        if (!level.isLoaded(pos)) return HydroGraphScan.Node.UNLOADED;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof HydroPipeBlock) return HydroGraphScan.Node.PIPE;
        if (state.getBlock() instanceof HydroMachineBlock) {
            BlockPos master = HydroMachineBlock.controllerPos(pos, state);
            // A machine whose master is unloaded cannot be validated: treat it as an unknown continuation.
            if (!level.isLoaded(master)) return HydroGraphScan.Node.UNLOADED;
            var kind = state.getBlock() instanceof HydroTurbineBlock ? HydroGraphScan.Kind.TURBINE : HydroGraphScan.Kind.PUMP;
            return new HydroGraphScan.Node(kind, master, HydroPipeBlock.portFace(state));
        }
        return HydroGraphScan.Node.EMPTY;
    }

    public int circuitCount() { return new HashSet<>(byNode.values()).size(); }
    public long rebuilds() { return rebuilds; }
    public long visits() { return visits; }
    public long discarded() { return discarded; }
    public boolean idle() { return current == null && requests.isEmpty(); }
}

package fr.lkdm.homelink.energy.hydro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;

/**
 * One built hydraulic circuit: its pipes, distinct pump and turbine masters, and the last allocation.
 * The allocation runs at most once per server tick, whichever machine ticks first, so the ticker order
 * can never double the flow. Immutable topology; a change discards the whole circuit.
 */
public final class HydroCircuit {
    private final Set<BlockPos> pipes, pumps, turbines, ports;
    private final Set<Long> chunks;
    private final boolean incomplete, tooLarge;
    private final List<BlockPos> pumpOrder;
    private long allocatedTick = Long.MIN_VALUE;
    private HydroAllocation allocation = HydroAllocation.NONE;
    private final Map<BlockPos, Double> shares = new HashMap<>();

    public HydroCircuit(Set<BlockPos> pipes, Set<BlockPos> pumps, Set<BlockPos> turbines, Set<BlockPos> ports, Set<Long> chunks,
            boolean incomplete, boolean tooLarge) {
        this.pipes = pipes;
        this.pumps = pumps;
        this.turbines = turbines;
        this.ports = ports;
        this.chunks = chunks;
        this.incomplete = incomplete;
        this.tooLarge = tooLarge;
        List<BlockPos> order = new ArrayList<>(pumps);
        order.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        this.pumpOrder = List.copyOf(order);
    }

    public Set<BlockPos> pipes() { return pipes; }
    public Set<BlockPos> pumps() { return pumps; }
    public Set<BlockPos> turbines() { return turbines; }
    public Set<BlockPos> ports() { return ports; }
    public Set<Long> chunks() { return chunks; }
    public boolean incomplete() { return incomplete; }
    public boolean tooLarge() { return tooLarge; }

    /** @return validity with the current limits; the single-turbine rule is fixed in V1 */
    public HydroStatus.Circuit status(int maxPumps) {
        if (tooLarge) return HydroStatus.Circuit.TOO_LARGE;
        if (incomplete) return HydroStatus.Circuit.INCOMPLETE;
        if (turbines.size() > HydroParameters.MAX_TURBINES) return HydroStatus.Circuit.MULTIPLE_TURBINES;
        if (pumps.size() > maxPumps) return HydroStatus.Circuit.TOO_MANY_PUMPS;
        return HydroStatus.Circuit.VALID;
    }

    /**
     * Allocates the flow for this tick, once. Later calls during the same tick return the same result.
     *
     * @param tick server game time
     * @param pumpFlow available flow of a pump master (zero when unloaded or not working)
     * @param turbineReady whether the single turbine may convert flow
     * @param turbineMaxFlow turbine capacity
     */
    public HydroAllocation allocate(long tick, ToDoubleFunction<BlockPos> pumpFlow, boolean turbineReady, double turbineMaxFlow) {
        if (allocatedTick == tick) return allocation;
        allocatedTick = tick;
        double[] flows = new double[pumpOrder.size()];
        for (int i = 0; i < flows.length; i++) flows[i] = pumpFlow.applyAsDouble(pumpOrder.get(i));
        allocation = HydroAllocation.allocate(flows, turbineReady, turbineMaxFlow);
        shares.clear();
        for (int i = 0; i < flows.length; i++) shares.put(pumpOrder.get(i), allocation.shares()[i]);
        return allocation;
    }

    /** @return flow attributed to a pump by the allocation of {@code tick} or the tick before, else zero */
    public double share(BlockPos pump, long tick) {
        return tick - allocatedTick <= 1 ? shares.getOrDefault(pump, 0.0) : 0;
    }

    /** @return the allocation of {@code tick} or the tick before, else none */
    public HydroAllocation lastAllocation(long tick) {
        return tick - allocatedTick <= 1 ? allocation : HydroAllocation.NONE;
    }
}

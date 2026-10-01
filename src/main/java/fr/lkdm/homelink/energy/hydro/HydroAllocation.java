package fr.lkdm.homelink.energy.hydro;

/**
 * Flow allocation of one circuit for one tick, in abstract DH/t (not mB, not HE).
 *
 * @param available sum of the distinct pumps' available flows
 * @param used flow the single turbine converts, at most its capacity, zero when it is not ready
 * @param shares part of {@code used} attributed to each pump, proportional to its available flow; they sum to {@code used}
 * @param limited whether the pumps offered more than the turbine can use (information, not a fault)
 */
public record HydroAllocation(double available, double used, double[] shares, boolean limited) {
    public static final HydroAllocation NONE = new HydroAllocation(0, 0, new double[0], false);

    /**
     * @param pumpFlows available flow of each distinct pump
     * @param turbineReady whether the turbine is on, complete, allowed and its outlet is clear
     * @param turbineMaxFlow turbine capacity, strictly positive
     */
    public static HydroAllocation allocate(double[] pumpFlows, boolean turbineReady, double turbineMaxFlow) {
        double available = 0;
        for (double flow : pumpFlows) if (Double.isFinite(flow) && flow > 0) available += flow;
        double max = Double.isFinite(turbineMaxFlow) && turbineMaxFlow > 0 ? turbineMaxFlow : 0;
        double used = turbineReady ? Math.min(available, max) : 0;
        double[] shares = new double[pumpFlows.length];
        if (used > 0) {
            for (int i = 0; i < shares.length; i++) {
                double flow = pumpFlows[i];
                shares[i] = Double.isFinite(flow) && flow > 0 ? used * flow / available : 0;
            }
        }
        return new HydroAllocation(available, used, shares, turbineReady && available > max);
    }
}

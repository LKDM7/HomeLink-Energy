package fr.lkdm.homelink.energy.hydro;

import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homelink.energy.energy.EnergyStore;
import fr.lkdm.homelink.energy.energy.FractionalEnergy;
import java.util.function.LongSupplier;

/**
 * Executes exactly one production tick of a Hydro Turbine. No catch-up after an unload or a restart;
 * periods are counted in executed server ticks ({@code gameTime}), never in day time.
 *
 * <p>Accounting: {@code potential} is what the flow allowed this tick; whole units go to the buffer;
 * {@code generated} counts accepted units, {@code lost} the units refused by a full buffer, and
 * {@code exported} the units that left the buffer. Hence potential = generated + lost + fraction and
 * buffer = generated - exported (destruction excepted).</p>
 */
public final class HydroTurbineCore {
    private final EnergyStore buffer;
    private final FractionalEnergy fraction = new FractionalEnergy();
    private double potentialRate, storedRate, potentialTotal;
    private long generated, lost, exported, generatedPeriod, observedTicks, period = Long.MIN_VALUE;

    public HydroTurbineCore(long capacity, LongSupplier clock) { buffer = new EnergyStore(capacity, clock); }

    /**
     * @param gameTime executed server tick
     * @param usedFlow DH/t allocated to this turbine (zero when it must not produce)
     * @param maxFlow turbine capacity in DH/t
     * @param heReference HE over 24 000 executed ticks at full flow
     * @return HE accepted by the buffer this tick
     */
    public long tick(long gameTime, double usedFlow, double maxFlow, long heReference) {
        long index = Math.floorDiv(gameTime, HydroParameters.REFERENCE_PERIOD);
        if (period != index) { period = index; generatedPeriod = 0; observedTicks = 0; }
        observedTicks++;
        double ratio = maxFlow > 0 && Double.isFinite(usedFlow) ? Math.max(0, Math.min(1, usedFlow / maxFlow)) : 0;
        double potential = Math.max(0, heReference) / (double) HydroParameters.REFERENCE_PERIOD * ratio;
        potentialRate = potential;
        potentialTotal += potential;
        storedRate = Math.min(potential, buffer.space());
        long whole = fraction.produce(potential);
        long accepted = buffer.receive(whole, false);
        generated = EnergyApi.saturatedAdd(generated, accepted);
        generatedPeriod = EnergyApi.saturatedAdd(generatedPeriod, accepted);
        lost = EnergyApi.saturatedAdd(lost, whole - accepted);
        return accepted;
    }

    /** Records HE that really left the buffer. */
    public void exported(long amount) { if (amount > 0) exported = EnergyApi.saturatedAdd(exported, amount); }

    public EnergyStore buffer() { return buffer; }
    public FractionalEnergy fraction() { return fraction; }
    /** @return HE/t the current flow allows */
    public double potentialRate() { return potentialRate; }
    /** @return HE/t the buffer could still accept this tick */
    public double storedRate() { return storedRate; }
    /** @return potential summed since this object was created (diagnostics, not saved) */
    public double potentialTotal() { return potentialTotal; }
    public long generated() { return generated; }
    public long lost() { return lost; }
    public long exported() { return exported; }
    public long generatedPeriod() { return generatedPeriod; }
    /** @return executed ticks of the current period seen by this turbine, under 24 000 after a load */
    public long observedTicks() { return observedTicks; }
    public long period() { return period; }

    public void restore(long generated, long lost, long exported, long inPeriod, long observed, long period) {
        this.generated = Math.max(0, generated);
        this.lost = Math.max(0, lost);
        this.exported = Math.max(0, exported);
        this.generatedPeriod = Math.max(0, inPeriod);
        this.observedTicks = Math.max(0, Math.min(HydroParameters.REFERENCE_PERIOD, observed));
        this.period = period;
    }
}

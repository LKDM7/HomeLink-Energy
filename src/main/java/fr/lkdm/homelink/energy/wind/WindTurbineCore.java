package fr.lkdm.homelink.energy.wind;

import fr.lkdm.homelink.energy.energy.EnergyStore;
import fr.lkdm.homelink.energy.energy.FractionalEnergy;
import java.util.function.LongSupplier;

/** Executes exactly one production tick. No elapsed-time catch-up; authoritative server caller. */
public final class WindTurbineCore {
    public static final int PERIOD = 24000;
    private final EnergyStore buffer;
    private final FractionalEnergy fraction = new FractionalEnergy();
    private boolean running;
    private WindStatus status = WindStatus.NO_WIND;
    private double efficiency, rate;
    private long generated, lost, generatedPeriod, period = Long.MIN_VALUE;
    public WindTurbineCore(long capacity, LongSupplier clock) { buffer = new EnergyStore(capacity, clock); }
    public long tick(long nominal, long simulationTick, double wind, double weather, int hubY,
            boolean dimension, boolean rotor, boolean sky, WindParameters p) {
        return tick(nominal, simulationTick, wind, weather, hubY, dimension, rotor, sky, true, p);
    }
    public long tick(long nominal, long simulationTick, double wind, double weather, int hubY,
            boolean dimension, boolean rotor, boolean sky, boolean baseComplete, WindParameters p) {
        return tick(nominal, simulationTick, wind, weather, hubY, dimension, rotor, sky, baseComplete, true, p);
    }
    /** A turbine switched off by a player produces nothing; the wind hysteresis keeps following the weather. */
    public long tick(long nominal, long simulationTick, double wind, double weather, int hubY,
            boolean dimension, boolean rotor, boolean sky, boolean baseComplete, boolean powered, WindParameters p) {
        long index = Math.floorDiv(simulationTick, PERIOD);
        if (period != index) { period = index; generatedPeriod = 0; }
        if (running ? wind <= p.cutInStop() : wind >= p.cutInStart()) running = !running;
        efficiency = p.efficiency(wind, weather, hubY);
        status = !powered ? WindStatus.SWITCHED_OFF : nominal <= 0 ? WindStatus.DISABLED : !dimension ? WindStatus.UNSUPPORTED_DIMENSION
                : !baseComplete ? WindStatus.INCOMPLETE_BASE : !rotor ? WindStatus.ROTOR_OBSTRUCTED : !sky ? WindStatus.SKY_BLOCKED : !running ? WindStatus.NO_WIND : WindStatus.GENERATING;
        double potential = status == WindStatus.GENERATING ? nominal / (double) PERIOD * efficiency : 0;
        rate = Math.min(potential, buffer.space());
        long whole = fraction.produce(potential);
        long accepted = buffer.receive(whole, false);
        generated = add(generated, accepted); generatedPeriod = add(generatedPeriod, accepted); lost = add(lost, whole-accepted);
        if (status == WindStatus.GENERATING && buffer.isFull()) status = WindStatus.BUFFER_FULL;
        return accepted;
    }
    private static long add(long a, long b) { return a > Long.MAX_VALUE-b ? Long.MAX_VALUE : a+b; }
    public EnergyStore buffer() { return buffer; }
    public FractionalEnergy fraction() { return fraction; }
    public WindStatus status() { return status; }
    public double efficiency() { return efficiency; }
    /** HE/t currently storable; totals track accepted integer HE exactly. */
    public double rate() { return rate; }
    public long generated() { return generated; }
    public long lost() { return lost; }
    public long generatedPeriod() { return generatedPeriod; }
    public long period() { return period; }
    public boolean running() { return running; }
    public void restore(long generated, long lost, long inPeriod, long period, boolean running) {
        this.generated=Math.max(0,generated); this.lost=Math.max(0,lost); generatedPeriod=Math.max(0,inPeriod);
        this.period=period; this.running=running;
    }
}

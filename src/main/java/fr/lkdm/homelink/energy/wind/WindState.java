package fr.lkdm.homelink.energy.wind;

/** One smooth simulation per level, with its own persisted clock and random stream. No dayTime. */
public final class WindState {
    public enum Trend { RISING, STABLE, FALLING }
    private double current = .55, target = .55;
    private long ticks, nextTargetChange, randomState;
    public WindState(long seed) { randomState = seed; }

    public void tick(WindParameters parameters) {
        if (ticks >= nextTargetChange) {
            randomState += 0x9E3779B97F4A7C15L;
            long z = randomState;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            target = parameters.naturalMin() + (z >>> 11) * 0x1.0p-53 * (parameters.naturalMax() - parameters.naturalMin());
            nextTargetChange = ticks + parameters.targetInterval();
        }
        current += Math.max(-parameters.step(), Math.min(parameters.step(), target - current));
        ticks++;
    }

    public double currentStrength() { return current; }
    public double targetStrength() { return target; }
    public long ticks() { return ticks; }
    public long nextTargetChange() { return nextTargetChange; }
    public long randomState() { return randomState; }
    public Trend trend() { return Math.abs(target-current) < 1e-9 ? Trend.STABLE : target > current ? Trend.RISING : Trend.FALLING; }
    public void restore(double current, double target, long ticks, long next, long random) {
        this.current = unit(current); this.target = unit(target); this.ticks = Math.max(0, ticks);
        nextTargetChange = Math.max(this.ticks, next); randomState = random;
    }
    /** Trusted server/admin test override. Holds until the next natural target interval. */
    public void setStrength(double strength, int holdTicks) {
        current = target = unit(strength); nextTargetChange = ticks + Math.max(1, holdTicks);
    }
    private static double unit(double n) { return Double.isFinite(n) ? Math.max(0, Math.min(1, n)) : .55; }
}

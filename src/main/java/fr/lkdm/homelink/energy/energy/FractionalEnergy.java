package fr.lkdm.homelink.energy.energy;

/** Shared fractional accumulator: rejected whole units never become hidden storage. */
public final class FractionalEnergy {
    private double fraction;
    public long produce(double potential) {
        if (potential > 0 && Double.isFinite(potential)) fraction += potential;
        long whole = (long) Math.floor(fraction);
        fraction -= whole;
        if (!(fraction >= 0 && fraction < 1)) fraction = 0;
        return whole;
    }
    public double fraction() { return fraction; }
    public void setFraction(double value) { fraction = value >= 0 && value < 1 ? value : 0; }
}

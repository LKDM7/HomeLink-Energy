package fr.lkdm.homelink.energy.wind;

/** Validated immutable balance snapshot; shared by all turbines of a level. */
public record WindParameters(double naturalMin, double naturalMax, double cutInStart, double cutInStop,
        double rain, double thunder, double maxEfficiency, int lowY, int highY, int veryHighY,
        double low, double normal, double high, double veryHigh, int targetInterval, double step, int checkInterval) {
    public static final WindParameters DEFAULT = new WindParameters(.10, 1, .16, .13, 1.25, 1.60, 1.50,
            80, 120, 180, .80, 1, 1.15, 1.25, 2400, .00015, 100);

    public WindParameters {
        double[] values = {naturalMin,naturalMax,cutInStart,cutInStop,rain,thunder,maxEfficiency,low,normal,high,veryHigh,step};
        for (double value : values) if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid wind multiplier");
        if (naturalMin > naturalMax || naturalMax > 1 || cutInStop >= cutInStart || cutInStart > 1
                || lowY >= highY || highY >= veryHighY || targetInterval < 1 || step <= 0 || checkInterval < 1)
            throw new IllegalArgumentException("Invalid wind thresholds");
    }

    public double altitude(int y) { return y < lowY ? low : y < highY ? normal : y < veryHighY ? high : veryHigh; }
    public double weather(fr.lkdm.homelink.energy.energy.Weather weather) {
        return switch (weather) { case CLEAR -> 1; case RAIN -> rain; case THUNDER -> thunder; };
    }
    public double efficiency(double wind, double weather, int y) {
        return Math.min(maxEfficiency, Math.max(0, Math.min(1, wind)) * weather * altitude(y));
    }
}

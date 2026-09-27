package fr.lkdm.homelink.energy.block;

import fr.lkdm.homelink.energy.config.EnergyConfig;
import fr.lkdm.homelink.energy.config.WindConfig;

public enum WindTier {
    I(1,3000,4), II(2,12000,7), III(3,28000,11);
    private final int level, hubOffset;
    private final long nominal;
    WindTier(int level,long nominal,int hubOffset) { this.level=level; this.nominal=nominal; this.hubOffset=hubOffset; }
    public int level() { return level; }
    public int width() { return this == I ? 1 : 2; }
    public int depth() { return this == III ? 2 : 1; }
    public int radius() { return level; }
    public int hubOffset() { return hubOffset; }
    public long nominal() {
        if (!EnergyConfig.loaded()) return nominal;
        return switch(this) { case I -> WindConfig.NOMINAL_1.get(); case II -> WindConfig.NOMINAL_2.get(); case III -> WindConfig.NOMINAL_3.get(); };
    }
}

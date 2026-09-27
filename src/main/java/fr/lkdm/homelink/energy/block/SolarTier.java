package fr.lkdm.homelink.energy.block;

import fr.lkdm.homelink.energy.config.EnergyConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Solar panel tiers: same engine, different rated production. */
public enum SolarTier {
    I(1, 2_000L, EnergyConfig.SOLAR_1_ENERGY_PER_CYCLE),
    II(2, 8_000L, EnergyConfig.SOLAR_2_ENERGY_PER_CYCLE),
    III(3, 20_000L, EnergyConfig.SOLAR_3_ENERGY_PER_CYCLE);

    private final int level;
    private final long defaultEnergy;
    private final ModConfigSpec.LongValue energy;

    SolarTier(int level, long defaultEnergy, ModConfigSpec.LongValue energy) {
        this.level = level;
        this.defaultEnergy = defaultEnergy;
        this.energy = energy;
    }

    /** @return tier number, 1 to 3 */
    public int level() { return level; }

    public int width() { return level == 1 ? 1 : 2; }
    public int depth() { return level == 3 ? 2 : 1; }

    /** @return configured HE per full clear cycle (the default until the server config loads) */
    public long energyPerCycle() { return EnergyConfig.loaded() ? energy.get() : defaultEnergy; }
}

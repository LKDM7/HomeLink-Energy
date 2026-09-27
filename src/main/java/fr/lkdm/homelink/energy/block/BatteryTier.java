package fr.lkdm.homelink.energy.block;

import fr.lkdm.homelink.energy.config.EnergyConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Battery tiers: capacity and transfer limit per tick, both configurable. */
public enum BatteryTier {
    I(1, 40_000L, 8L, EnergyConfig.BATTERY_1_CAPACITY, EnergyConfig.BATTERY_1_TRANSFER_RATE),
    II(2, 120_000L, 32L, EnergyConfig.BATTERY_2_CAPACITY, EnergyConfig.BATTERY_2_TRANSFER_RATE),
    III(3, 320_000L, 128L, EnergyConfig.BATTERY_3_CAPACITY, EnergyConfig.BATTERY_3_TRANSFER_RATE);

    private final int level;
    private final long defaultCapacity;
    private final long defaultRate;
    private final ModConfigSpec.LongValue capacity;
    private final ModConfigSpec.LongValue rate;

    BatteryTier(int level, long defaultCapacity, long defaultRate, ModConfigSpec.LongValue capacity, ModConfigSpec.LongValue rate) {
        this.level = level;
        this.defaultCapacity = defaultCapacity;
        this.defaultRate = defaultRate;
        this.capacity = capacity;
        this.rate = rate;
    }

    /** @return tier number, 1 to 3 */
    public int level() { return level; }
    public int width() { return this == I ? 1 : 2; }
    public int depth() { return this == III ? 2 : 1; }

    /** @return configured capacity in HE */
    public long capacity() { return EnergyConfig.loaded() ? capacity.get() : defaultCapacity; }

    /** @return configured transfer limit in HE per tick, for charge and discharge */
    public long transferRate() { return EnergyConfig.loaded() ? rate.get() : defaultRate; }
}

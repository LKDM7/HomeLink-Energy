package fr.lkdm.homelink.energy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server balance settings, one per world ({@code serverconfig/homelink_energy-server.toml}).
 * Every value is range-checked; out-of-range entries are reset to their default by NeoForge.
 * Reference only: 1 coal = 4000 HE. HE are never converted from or to any other energy.
 */
public final class EnergyConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.LongValue SOLAR_1_ENERGY_PER_CYCLE;
    public static final ModConfigSpec.LongValue SOLAR_2_ENERGY_PER_CYCLE;
    public static final ModConfigSpec.LongValue SOLAR_3_ENERGY_PER_CYCLE;
    public static final ModConfigSpec.LongValue SOLAR_BUFFER_CAPACITY;
    public static final ModConfigSpec.DoubleValue CLEAR_WEATHER_EFFICIENCY;
    public static final ModConfigSpec.DoubleValue RAIN_EFFICIENCY;
    public static final ModConfigSpec.DoubleValue THUNDER_EFFICIENCY;
    public static final ModConfigSpec.IntValue SKY_CHECK_INTERVAL;

    public static final ModConfigSpec.LongValue BATTERY_1_CAPACITY;
    public static final ModConfigSpec.LongValue BATTERY_2_CAPACITY;
    public static final ModConfigSpec.LongValue BATTERY_3_CAPACITY;
    public static final ModConfigSpec.LongValue BATTERY_1_TRANSFER_RATE;
    public static final ModConfigSpec.LongValue BATTERY_2_TRANSFER_RATE;
    public static final ModConfigSpec.LongValue BATTERY_3_TRANSFER_RATE;
    public static final ModConfigSpec.IntValue BATTERY_LOW_THRESHOLD;
    public static final ModConfigSpec.IntValue BATTERY_LOW_REARM;

    public static final ModConfigSpec.IntValue MAX_ENERGY_NETWORK_NODES;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Solar panels. Totals are per full 24000-tick cycle in clear weather, night included (1 coal = 4000 HE).").push("solar");
        SOLAR_1_ENERGY_PER_CYCLE = builder.comment("HE produced by a Solar Panel I over a full clear cycle. 0 disables the tier.")
                .defineInRange("solar1EnergyPerCycle", 2_000L, 0L, 10_000_000L);
        SOLAR_2_ENERGY_PER_CYCLE = builder.comment("HE produced by a Solar Panel II over a full clear cycle. 0 disables the tier.")
                .defineInRange("solar2EnergyPerCycle", 8_000L, 0L, 10_000_000L);
        SOLAR_3_ENERGY_PER_CYCLE = builder.comment("HE produced by a Solar Panel III over a full clear cycle. 0 disables the tier.")
                .defineInRange("solar3EnergyPerCycle", 20_000L, 0L, 10_000_000L);
        SOLAR_BUFFER_CAPACITY = builder.comment("Small internal buffer of every panel, in HE. It does not replace a battery.")
                .defineInRange("solarBufferCapacity", 1_000L, 1L, 1_000_000L);
        CLEAR_WEATHER_EFFICIENCY = builder.comment("Production multiplier in clear weather.")
                .defineInRange("clearWeatherEfficiency", 1.0, 0.0, 1.0);
        RAIN_EFFICIENCY = builder.comment("Production multiplier while it rains.")
                .defineInRange("rainEfficiency", 0.40, 0.0, 1.0);
        THUNDER_EFFICIENCY = builder.comment("Production multiplier during a thunderstorm (replaces the rain multiplier).")
                .defineInRange("thunderEfficiency", 0.15, 0.0, 1.0);
        SKY_CHECK_INTERVAL = builder.comment("Ticks between two periodic sky checks of a panel; block updates next to it also trigger a check.")
                .defineInRange("skyCheckInterval", 40, 1, 1_200);
        builder.pop();

        builder.comment("Batteries: capacity in HE and transfer limit in HE per tick, for charge and discharge alike.").push("battery");
        BATTERY_1_CAPACITY = builder.defineInRange("battery1Capacity", 40_000L, 1L, 1_000_000_000L);
        BATTERY_2_CAPACITY = builder.defineInRange("battery2Capacity", 120_000L, 1L, 1_000_000_000L);
        BATTERY_3_CAPACITY = builder.defineInRange("battery3Capacity", 320_000L, 1L, 1_000_000_000L);
        BATTERY_1_TRANSFER_RATE = builder.defineInRange("battery1TransferRate", 8L, 1L, 1_000_000L);
        BATTERY_2_TRANSFER_RATE = builder.defineInRange("battery2TransferRate", 32L, 1L, 1_000_000L);
        BATTERY_3_TRANSFER_RATE = builder.defineInRange("battery3TransferRate", 128L, 1L, 1_000_000L);
        BATTERY_LOW_THRESHOLD = builder.comment("Charge percentage under which a battery reports battery_low.")
                .defineInRange("batteryLowThreshold", 15, 0, 99);
        BATTERY_LOW_REARM = builder.comment("Charge percentage a battery must reach again before battery_low can be reported anew.")
                .defineInRange("batteryLowRearm", 30, 1, 100);
        builder.pop();

        builder.push("network");
        MAX_ENERGY_NETWORK_NODES = builder.comment("Most nodes (cables, producers, batteries, consumers) in one energy network; larger networks stop with NETWORK_TOO_LARGE.")
                .defineInRange("maxEnergyNetworkNodes", 1_024, 2, 65_536);
        builder.pop();
        WindConfig.define(builder);
        SPEC = builder.build();
    }

    private EnergyConfig() { }

    /** @return whether the server configuration is loaded (false in menus and plain unit tests) */
    public static boolean loaded() { return SPEC.isLoaded(); }
}

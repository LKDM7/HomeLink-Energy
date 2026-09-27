package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** HomeCore identifiers of HomeLink Energy devices: types, metrics and events. */
public final class EnergyIds {
    public static final ResourceLocation SOLAR_PANEL = HomeLinkEnergy.id("solar_panel");
    public static final ResourceLocation BATTERY = HomeLinkEnergy.id("battery");

    // Solar panel metrics.
    public static final ResourceLocation SOLAR_STATUS = HomeLinkEnergy.id("status");
    public static final ResourceLocation TIER = HomeLinkEnergy.id("tier");
    public static final ResourceLocation GENERATION_RATE = HomeLinkEnergy.id("generation_rate");
    public static final ResourceLocation GENERATION_PERCENTAGE = HomeLinkEnergy.id("generation_percentage");
    public static final ResourceLocation GENERATED_TODAY = HomeLinkEnergy.id("generated_today");
    public static final ResourceLocation NOMINAL_DAILY_GENERATION = HomeLinkEnergy.id("nominal_daily_generation");
    public static final ResourceLocation SKY_VISIBLE = HomeLinkEnergy.id("sky_visible");
    public static final ResourceLocation WEATHER_EFFICIENCY = HomeLinkEnergy.id("weather_efficiency");
    public static final ResourceLocation BUFFER = HomeLinkEnergy.id("buffer");
    public static final ResourceLocation NETWORK_CONNECTED = HomeLinkEnergy.id("network_connected");

    // Battery metrics.
    public static final ResourceLocation STORED_ENERGY = HomeLinkEnergy.id("stored_energy");
    public static final ResourceLocation CAPACITY = HomeLinkEnergy.id("capacity");
    public static final ResourceLocation PERCENTAGE = HomeLinkEnergy.id("percentage");
    public static final ResourceLocation INPUT_RATE = HomeLinkEnergy.id("input_rate");
    public static final ResourceLocation OUTPUT_RATE = HomeLinkEnergy.id("output_rate");
    public static final ResourceLocation ENERGY = HomeLinkEnergy.id("energy");

    // Aggregates of the cable network the battery belongs to, for an energy widget.
    public static final ResourceLocation NETWORK_PRODUCTION = HomeLinkEnergy.id("network_production");
    public static final ResourceLocation NETWORK_CONSUMPTION = HomeLinkEnergy.id("network_consumption");
    public static final ResourceLocation NETWORK_NET_FLOW = HomeLinkEnergy.id("network_net_flow");
    public static final ResourceLocation NETWORK_STORED = HomeLinkEnergy.id("network_stored");
    public static final ResourceLocation NETWORK_CAPACITY = HomeLinkEnergy.id("network_capacity");
    public static final ResourceLocation NETWORK_PRODUCERS = HomeLinkEnergy.id("network_producers");
    public static final ResourceLocation NETWORK_CONSUMERS = HomeLinkEnergy.id("network_consumers");
    public static final ResourceLocation NETWORK_BATTERIES = HomeLinkEnergy.id("network_batteries");

    // Events.
    public static final ResourceLocation SOLAR_STARTED_GENERATING = HomeLinkEnergy.id("solar_started_generating");
    public static final ResourceLocation SOLAR_STOPPED_GENERATING = HomeLinkEnergy.id("solar_stopped_generating");
    public static final ResourceLocation SOLAR_SKY_BLOCKED = HomeLinkEnergy.id("solar_sky_blocked");
    public static final ResourceLocation BATTERY_LOW = HomeLinkEnergy.id("battery_low");
    public static final ResourceLocation BATTERY_FULL = HomeLinkEnergy.id("battery_full");
    public static final ResourceLocation BATTERY_EMPTY = HomeLinkEnergy.id("battery_empty");
    public static final ResourceLocation NETWORK_CONNECTED_EVENT = HomeLinkEnergy.id("energy_network_connected");
    public static final ResourceLocation NETWORK_OFFLINE = HomeLinkEnergy.id("energy_network_offline");

    public static final Set<ResourceLocation> SOLAR_EVENTS = Set.of(SOLAR_STARTED_GENERATING, SOLAR_STOPPED_GENERATING, SOLAR_SKY_BLOCKED,
            NETWORK_CONNECTED_EVENT, NETWORK_OFFLINE);
    public static final Set<ResourceLocation> BATTERY_EVENTS = Set.of(BATTERY_LOW, BATTERY_FULL, BATTERY_EMPTY,
            NETWORK_CONNECTED_EVENT, NETWORK_OFFLINE);

    private EnergyIds() { }
}

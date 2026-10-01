package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.config.EnergyConfig;
import fr.lkdm.homelink.energy.network.EnergyNetwork;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * HomeCore device of a battery: charge metrics, charge alarms, and the aggregate flows of the
 * cable network it belongs to, so that a dashboard can show production, consumption, net flow and
 * storage without depending on HomeLink Energy.
 */
public final class BatteryDevice extends EnergyDevice {
    private final BatteryBlockEntity battery;
    private final DeviceMetric<Long> stored = energy(EnergyIds.STORED_ENERGY, "Stored energy");
    private final DeviceMetric<Long> capacity = energy(EnergyIds.CAPACITY, "Capacity");
    private final DeviceMetric<Percentage> percentage = percentage(EnergyIds.PERCENTAGE, "Charge");
    private final DeviceMetric<Double> input = flow(EnergyIds.INPUT_RATE, "Input");
    private final DeviceMetric<Double> output = flow(EnergyIds.OUTPUT_RATE, "Output");
    private final DeviceMetric<Integer> tier = count(EnergyIds.TIER, "Level", 3);
    private final DeviceMetric<Boolean> connected = flag(EnergyIds.NETWORK_CONNECTED, "Network connected");
    private final DeviceMetric<Double> production = flow(EnergyIds.NETWORK_PRODUCTION, "Network production");
    private final DeviceMetric<Double> solarProduction = flow(fr.lkdm.homelink.energy.HomeLinkEnergy.id("network_solar_production"), "Solar delivered");
    private final DeviceMetric<Double> windProduction = flow(fr.lkdm.homelink.energy.HomeLinkEnergy.id("network_wind_production"), "Wind delivered");
    private final DeviceMetric<Double> hydroProduction = flow(fr.lkdm.homelink.energy.HomeLinkEnergy.id("network_hydro_production"), "Hydro delivered");
    private final DeviceMetric<Double> otherProduction = flow(fr.lkdm.homelink.energy.HomeLinkEnergy.id("network_other_production"), "Other delivered");
    private final DeviceMetric<Double> consumption = flow(EnergyIds.NETWORK_CONSUMPTION, "Network consumption");
    private final DeviceMetric<Double> netFlow = flow(EnergyIds.NETWORK_NET_FLOW, "Network net flow");
    private final DeviceMetric<Long> networkStored = energy(EnergyIds.NETWORK_STORED, "Network stored energy");
    private final DeviceMetric<Long> networkCapacity = energy(EnergyIds.NETWORK_CAPACITY, "Network storage capacity");
    private final DeviceMetric<Integer> producers = count(EnergyIds.NETWORK_PRODUCERS, "Network producers", 65_536);
    private final DeviceMetric<Integer> consumers = count(EnergyIds.NETWORK_CONSUMERS, "Network consumers", 65_536);
    private final DeviceMetric<Integer> batteries = count(EnergyIds.NETWORK_BATTERIES, "Network batteries", 65_536);
    private final List<DeviceMetric<?>> metrics = List.of(stored, capacity, percentage, input, output, tier, connected,
            production, solarProduction, windProduction, hydroProduction, otherProduction, consumption, netFlow, networkStored, networkCapacity, producers, consumers, batteries);
    private final DeviceSchema schema;
    private final BatteryAlarms alarms = new BatteryAlarms();
    private boolean networkObserved;
    private boolean lastConnected;

    public BatteryDevice(BatteryBlockEntity battery, Consumer<DeviceEvent> events) {
        super(battery, events);
        this.battery = battery;
        this.schema = DeviceSchema.from(this);
        refresh();
    }

    @Override
    protected void update() {
        long now = battery.stored(), max = battery.capacity();
        stored.setValue(now);
        capacity.setValue(max);
        percentage.setValue(percent(battery.percentage()));
        input.setValue(battery.inputRate());
        output.setValue(battery.outputRate());
        tier.setValue(battery.tier().level());
        EnergyNetwork network = battery.getLevel() instanceof ServerLevel server
                ? EnergyNetworks.existing(server).flatMap(n -> n.networksOfMachine(battery.getBlockPos()).stream().filter(it -> !it.tooLarge()).findFirst()).orElse(null)
                : null;
        boolean linked = network != null;
        connected.setValue(linked);
        production.setValue(linked ? network.production() : 0.0);
        solarProduction.setValue(linked ? network.production(fr.lkdm.homelink.energy.energy.EnergySourceType.SOLAR) : 0.0);
        windProduction.setValue(linked ? network.production(fr.lkdm.homelink.energy.energy.EnergySourceType.WIND) : 0.0);
        hydroProduction.setValue(linked ? network.production(fr.lkdm.homelink.energy.energy.EnergySourceType.HYDRO) : 0.0);
        otherProduction.setValue(linked ? network.production(fr.lkdm.homelink.energy.energy.EnergySourceType.OTHER) : 0.0);
        consumption.setValue(linked ? network.consumption() : 0.0);
        netFlow.setValue(linked ? network.netFlow() : 0.0);
        networkStored.setValue(linked ? network.stored() : now);
        networkCapacity.setValue(linked ? network.capacity() : max);
        producers.setValue(linked ? network.producerCount() : 0);
        consumers.setValue(linked ? network.consumerCount() : 0);
        batteries.setValue(linked ? network.storageCount() : 1);
        if (!isValid()) return;
        int low = EnergyConfig.BATTERY_LOW_THRESHOLD.get();
        for (BatteryAlarms.Alarm alarm : alarms.observe(now, max, low, EnergyConfig.BATTERY_LOW_REARM.get())) {
            Map<String, String> data = Map.of("stored_energy", Long.toString(now), "capacity", Long.toString(max),
                    "percentage", Integer.toString((int) Math.floor(battery.percentage())));
            switch (alarm) {
                case LOW -> publish(EnergyIds.BATTERY_LOW, DeviceEvent.Severity.WARNING, data);
                case EMPTY -> publish(EnergyIds.BATTERY_EMPTY, DeviceEvent.Severity.CRITICAL, data);
                case FULL -> publish(EnergyIds.BATTERY_FULL, DeviceEvent.Severity.INFO, data);
            }
        }
        if (networkObserved && linked != lastConnected) {
            publish(linked ? EnergyIds.NETWORK_CONNECTED_EVENT : EnergyIds.NETWORK_OFFLINE,
                    linked ? DeviceEvent.Severity.INFO : DeviceEvent.Severity.WARNING, Map.of());
        }
        networkObserved = true;
        lastConnected = linked;
    }

    @Override public ResourceLocation deviceType() { return EnergyIds.BATTERY; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public Set<ResourceLocation> eventTypes() { return EnergyIds.BATTERY_EVENTS; }
    @Override public DeviceSchema schema() { return schema; }

    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        if (!battery.baseComplete()) return DeviceStatus.WARNING.withMessage(Component.translatable("status.homelink_energy.battery.incomplete_base"));
        int percent = (int) Math.floor(battery.percentage());
        Component message = Component.translatableWithFallback("status.homelink_energy.battery", "%s %%", percent);
        return (percent < EnergyConfig.BATTERY_LOW_THRESHOLD.get() ? DeviceStatus.WARNING : DeviceStatus.ONLINE).withMessage(message);
    }
}

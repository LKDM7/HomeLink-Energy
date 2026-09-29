package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** HomeCore device of a solar panel: production metrics and obstruction events. */
public final class SolarDevice extends EnergyDevice implements Switchable {
    private final SolarPanelBlockEntity panel;
    private final DeviceMetric<SolarStatus> status = DeviceMetric.builder(EnergyIds.SOLAR_STATUS, name(EnergyIds.SOLAR_STATUS, "Status"),
            MetricTypes.enumeration(EnergyIds.SOLAR_STATUS, SolarStatus.class), SolarStatus.NIGHT).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Integer> tier = count(EnergyIds.TIER, "Level", 3);
    private final DeviceMetric<Double> rate = flow(EnergyIds.GENERATION_RATE, "Generation rate");
    private final DeviceMetric<Percentage> percentage = percentage(EnergyIds.GENERATION_PERCENTAGE, "Generation");
    private final DeviceMetric<Long> today = energy(EnergyIds.GENERATED_TODAY, "Generated today");
    private final DeviceMetric<Long> nominal = energy(EnergyIds.NOMINAL_DAILY_GENERATION, "Maximum per clear cycle");
    private final DeviceMetric<Boolean> sky = flag(EnergyIds.SKY_VISIBLE, "Sky visible");
    private final DeviceMetric<Percentage> weather = percentage(EnergyIds.WEATHER_EFFICIENCY, "Weather efficiency");
    private final DeviceMetric<Long> buffer = energy(EnergyIds.BUFFER, "Buffer");
    private final DeviceMetric<Long> bufferCapacity = energy(fr.lkdm.homelink.energy.HomeLinkEnergy.id("buffer_capacity"), "Buffer capacity");
    private final DeviceMetric<Boolean> connected = flag(EnergyIds.NETWORK_CONNECTED, "Network connected");
    private final List<DeviceMetric<?>> metrics = List.of(status, tier, rate, percentage, today, nominal, sky, weather, buffer, bufferCapacity, connected);
    private final DeviceSchema schema;
    private final SolarAlarms alarms = new SolarAlarms();
    private boolean networkObserved;
    private boolean lastConnected;

    public SolarDevice(SolarPanelBlockEntity panel, Consumer<DeviceEvent> events) {
        super(panel, events);
        this.panel = panel;
        this.schema = DeviceSchema.from(this);
        refresh();
    }

    @Override
    protected void update() {
        SolarStatus now = panel.status();
        status.setValue(now);
        tier.setValue(panel.tier().level());
        rate.setValue(panel.core().potentialRate());
        percentage.setValue(percent(panel.generationPercentage()));
        today.setValue(panel.core().generatedToday());
        nominal.setValue(panel.nominalPerCycle());
        sky.setValue(panel.skyVisible());
        weather.setValue(percent(panel.core().efficiency() * 100));
        long capacity = panel.core().buffer().capacity();
        buffer.setValue(Math.min(panel.core().buffer().stored(), capacity));
        bufferCapacity.setValue(capacity);
        boolean linked = panel.networkConnection() == EnergyNetworks.Connection.CONNECTED;
        connected.setValue(linked);
        if (!isValid() || panel.getLevel() == null) return;
        for (SolarAlarms.Alarm alarm : alarms.observe(now, panel.getLevel().getGameTime())) {
            switch (alarm) {
                case STARTED -> publish(EnergyIds.SOLAR_STARTED_GENERATING, DeviceEvent.Severity.INFO, Map.of());
                case STOPPED -> publish(EnergyIds.SOLAR_STOPPED_GENERATING, DeviceEvent.Severity.WARNING,
                        Map.of("reason", now.name().toLowerCase(Locale.ROOT)));
                case SKY_BLOCKED -> publish(EnergyIds.SOLAR_SKY_BLOCKED, DeviceEvent.Severity.WARNING, Map.of());
            }
        }
        if (networkObserved && linked != lastConnected) {
            publish(linked ? EnergyIds.NETWORK_CONNECTED_EVENT : EnergyIds.NETWORK_OFFLINE,
                    linked ? DeviceEvent.Severity.INFO : DeviceEvent.Severity.WARNING, Map.of());
        }
        networkObserved = true;
        lastConnected = linked;
    }

    @Override public ResourceLocation deviceType() { return EnergyIds.SOLAR_PANEL; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public Set<ResourceLocation> eventTypes() { return EnergyIds.SOLAR_EVENTS; }
    @Override public DeviceSchema schema() { return schema; }

    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        SolarStatus state = panel.status();
        Component message = Component.translatableWithFallback("status.homelink_energy.solar." + state.name().toLowerCase(Locale.ROOT), state.name());
        return (state == SolarStatus.SKY_BLOCKED || state == SolarStatus.UNSUPPORTED_DIMENSION ? DeviceStatus.WARNING
                : state == SolarStatus.DISABLED || state == SolarStatus.SWITCHED_OFF ? DeviceStatus.DISABLED : DeviceStatus.ONLINE).withMessage(message);
    }
}

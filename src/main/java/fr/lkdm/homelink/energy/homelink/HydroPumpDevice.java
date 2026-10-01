package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One HomeCore type for the three pump levels; the level is a metric. Its switch means "allow capture",
 * not "has electricity": a pump never uses HE. Water events fire on transitions only, never on first load.
 */
public final class HydroPumpDevice extends EnergyDevice implements Switchable {
    public static final ResourceLocation TYPE = HomeLinkEnergy.id("hydro_pump");
    public static final Unit DH_PER_TICK = new Unit(HomeLinkEnergy.id("dh_per_tick"), "DH/t");
    public static final ResourceLocation WATER_LOST = HomeLinkEnergy.id("hydro_water_lost"), WATER_RESTORED = HomeLinkEnergy.id("hydro_water_restored");
    public static final Set<ResourceLocation> EVENTS = Set.of(WATER_LOST, WATER_RESTORED);
    private final HydroPumpBlockEntity pump;
    private final DeviceMetric<Integer> level = count(id("hydro_pump_level"), "Level", 3);
    private final DeviceMetric<HydroStatus.Pump> state = DeviceMetric.builder(id("hydro_pump_status"), name(id("hydro_pump_status"), "Status"),
            MetricTypes.enumeration(id("hydro_pump_status"), HydroStatus.Pump.class), HydroStatus.Pump.NETWORK_PENDING).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Boolean> enabled = flag(id("enabled"), "Enabled");
    private final DeviceMetric<Percentage> availability = percentage(id("water_availability"), "Water availability");
    private final DeviceMetric<Integer> sources = count(id("sources_count"), "Water sources", 10_000);
    private final DeviceMetric<Integer> required = count(id("sources_required"), "Sources for full output", 10_000);
    private final DeviceMetric<Double> availableFlow = flow("available_flow", "Available flow");
    private final DeviceMetric<Double> allocatedFlow = flow("allocated_flow", "Used flow");
    private final DeviceMetric<Boolean> hydraulic = flag(id("hydraulic_connected"), "Hydraulic connected");
    private final List<DeviceMetric<?>> metrics = List.of(level, state, enabled, availability, sources, required, availableFlow, allocatedFlow, hydraulic);
    private final DeviceSchema schema;
    private boolean observed, lastWater;

    public HydroPumpDevice(HydroPumpBlockEntity pump, Consumer<DeviceEvent> events) {
        super(pump, events);
        this.pump = pump;
        schema = DeviceSchema.from(this);
        refresh();
    }

    private static ResourceLocation id(String key) { return HomeLinkEnergy.id(key); }

    static DeviceMetric<Double> flow(String key, String label) {
        return DeviceMetric.builder(id(key), name(id(key), label), MetricTypes.DOUBLE, 0.0).unit(DH_PER_TICK).updatePolicy(UpdatePolicy.NORMAL).build();
    }

    @Override protected void update() {
        var water = pump.water();
        HydroStatus.Pump status = pump.status();
        level.setValue(pump.tier().level());
        state.setValue(status);
        enabled.setValue(pump.powered());
        availability.setValue(percent(water.availability() * 100));
        sources.setValue(water.sources());
        required.setValue(water.cells());
        availableFlow.setValue(pump.availableFlow());
        allocatedFlow.setValue(pump.allocatedFlow());
        hydraulic.setValue(pump.hydraulicConnected());
        // Unknown water (unloaded chunk) is neither a loss nor a recovery.
        // Water is only evaluated once the pump is on, complete and allowed; unknown water is not a loss.
        boolean evaluated = switch (status) {
            case DISABLED, CONFIG_DISABLED, INCOMPLETE_STRUCTURE, UNSUPPORTED_DIMENSION, WATER_UNKNOWN -> false;
            default -> true;
        };
        if (evaluated) {
            boolean hasWater = status.waterOk();
            if (observed && hasWater != lastWater) publish(hasWater ? WATER_RESTORED : WATER_LOST,
                    hasWater ? DeviceEvent.Severity.INFO : DeviceEvent.Severity.WARNING, Map.of("reason", status.name(), "source", "hydro_pump"));
            observed = true;
            lastWater = hasWater;
        }
    }

    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public Set<ResourceLocation> eventTypes() { return EVENTS; }
    @Override public DeviceSchema schema() { return schema; }

    @Override public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        HydroStatus.Pump status = pump.status();
        DeviceStatus result = switch (status) {
            case PUMPING, STANDBY -> DeviceStatus.ONLINE;
            case DISABLED, CONFIG_DISABLED -> DeviceStatus.DISABLED;
            case NETWORK_PENDING, WATER_UNKNOWN -> DeviceStatus.UNKNOWN;
            default -> DeviceStatus.WARNING;
        };
        return result.withMessage(Component.translatable("status.homelink_energy.hydro_pump." + status.name().toLowerCase(Locale.ROOT)));
    }
}

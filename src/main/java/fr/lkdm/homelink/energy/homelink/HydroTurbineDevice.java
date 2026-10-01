package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.config.HydroConfig;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * HomeCore view of the Hydro Turbine. {@code current_generation} is the potential HE/t of the flow it
 * converts; HE really delivered to cables is counted by the cable network, never a second time here.
 * Events fire on transitions only; the first observation after loading only records the state, and a
 * voluntary stop is informative, never a warning.
 */
public final class HydroTurbineDevice extends EnergyDevice implements Switchable {
    public static final ResourceLocation TYPE = HomeLinkEnergy.id("hydro_turbine");
    public static final ResourceLocation CIRCUIT_INVALID = id("hydro_circuit_invalid"), CIRCUIT_RESTORED = id("hydro_circuit_restored"),
            OUTLET_BLOCKED = id("hydro_outlet_blocked"), OUTLET_CLEARED = id("hydro_outlet_cleared"),
            STARTED = id("hydro_generation_started"), STOPPED = id("hydro_generation_stopped");
    public static final Set<ResourceLocation> EVENTS = Set.of(CIRCUIT_INVALID, CIRCUIT_RESTORED, OUTLET_BLOCKED, OUTLET_CLEARED, STARTED, STOPPED);
    private final HydroTurbineBlockEntity turbine;
    private final DeviceMetric<HydroStatus.Turbine> state = DeviceMetric.builder(id("hydro_turbine_status"), name(id("hydro_turbine_status"), "Status"),
            MetricTypes.enumeration(id("hydro_turbine_status"), HydroStatus.Turbine.class), HydroStatus.Turbine.NETWORK_PENDING).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Boolean> enabled = flag(id("enabled"), "Enabled");
    private final DeviceMetric<Double> availableFlow = HydroPumpDevice.flow("available_flow", "Available flow");
    private final DeviceMetric<Double> usedFlow = HydroPumpDevice.flow("used_flow", "Used flow");
    private final DeviceMetric<Double> maxFlow = HydroPumpDevice.flow("max_flow", "Flow capacity");
    private final DeviceMetric<Percentage> flowPercentage = percentage(id("flow_percentage"), "Flow");
    private final DeviceMetric<Integer> pumpCount = count(id("pump_count"), "Pumps", 16);
    private final DeviceMetric<Boolean> outletClear = flag(id("outlet_clear"), "Outlet clear");
    private final DeviceMetric<Double> generation = DeviceMetric.builder(id("current_generation"), name(id("current_generation"), "Generation"),
            MetricTypes.DOUBLE, 0.0).unit(EnergyApi.HE_PER_TICK).updatePolicy(UpdatePolicy.NORMAL).build();
    private final DeviceMetric<Long> period = he("generated_this_period", "Generated this period");
    private final DeviceMetric<Long> buffer = he("buffer_energy", "Buffer");
    private final DeviceMetric<Long> capacity = he("buffer_capacity", "Buffer capacity");
    private final DeviceMetric<Long> lost = he("lost_energy", "Lost energy");
    private final DeviceMetric<Boolean> connected = flag(id("network_connected"), "Network connected");
    private final List<DeviceMetric<?>> metrics = List.of(state, enabled, availableFlow, usedFlow, maxFlow, flowPercentage, pumpCount,
            outletClear, generation, period, buffer, capacity, lost, connected);
    private final DeviceSchema schema;
    private boolean observed, lastCircuit, lastOutlet, lastProducing;

    public HydroTurbineDevice(HydroTurbineBlockEntity turbine, Consumer<DeviceEvent> events) {
        super(turbine, events);
        this.turbine = turbine;
        schema = DeviceSchema.from(this);
        refresh();
    }

    private static ResourceLocation id(String key) { return HomeLinkEnergy.id(key); }

    private static DeviceMetric<Long> he(String key, String label) {
        return DeviceMetric.builder(id(key), name(id(key), label), MetricTypes.LONG, 0L).unit(EnergyApi.HE).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    @Override protected void update() {
        var core = turbine.core();
        HydroStatus.Turbine status = turbine.status();
        double max = HydroConfig.parameters().turbineMaxFlow();
        state.setValue(status);
        enabled.setValue(turbine.powered());
        availableFlow.setValue(turbine.allocation().available());
        usedFlow.setValue(turbine.usedFlow());
        maxFlow.setValue(max);
        flowPercentage.setValue(percent(max <= 0 ? 0 : turbine.usedFlow() / max * 100));
        pumpCount.setValue(turbine.pumpCount());
        outletClear.setValue(turbine.outletClear());
        generation.setValue(status.producing() ? core.potentialRate() : 0.0);
        period.setValue(core.generatedPeriod());
        buffer.setValue(core.buffer().stored());
        capacity.setValue(core.buffer().capacity());
        lost.setValue(core.lost());
        connected.setValue(turbine.networkConnection() == EnergyNetworks.Connection.CONNECTED || turbine.directOutput());
        // Pending circuits are rebuilding, not broken: they never raise an alert.
        if (status == HydroStatus.Turbine.NETWORK_PENDING) return;
        boolean circuit = !status.circuitFault(), outlet = turbine.outletClear(), producing = status.producing();
        if (observed) {
            Map<String, String> data = Map.of("reason", status.name(), "source", "hydro_turbine");
            if (circuit != lastCircuit) publish(circuit ? CIRCUIT_RESTORED : CIRCUIT_INVALID, circuit ? DeviceEvent.Severity.INFO : DeviceEvent.Severity.WARNING, data);
            if (outlet != lastOutlet) publish(outlet ? OUTLET_CLEARED : OUTLET_BLOCKED, outlet ? DeviceEvent.Severity.INFO : DeviceEvent.Severity.WARNING, data);
            if (producing != lastProducing) publish(producing ? STARTED : STOPPED, DeviceEvent.Severity.INFO, data);
        }
        observed = true;
        lastCircuit = circuit;
        lastOutlet = outlet;
        lastProducing = producing;
    }

    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public Set<ResourceLocation> eventTypes() { return EVENTS; }
    @Override public DeviceSchema schema() { return schema; }

    @Override public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        HydroStatus.Turbine status = turbine.status();
        DeviceStatus result = switch (status) {
            case GENERATING, NO_FLOW -> DeviceStatus.ONLINE;
            case DISABLED, CONFIG_DISABLED -> DeviceStatus.DISABLED;
            case NETWORK_PENDING -> DeviceStatus.UNKNOWN;
            default -> DeviceStatus.WARNING;
        };
        return result.withMessage(Component.translatable("status.homelink_energy.hydro_turbine." + status.name().toLowerCase(Locale.ROOT)));
    }
}

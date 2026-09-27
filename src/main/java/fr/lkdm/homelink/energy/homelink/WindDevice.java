package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.device.*;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.*;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.wind.*;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

/** One public HomeCore schema for all three tiers; no Dashboard/Tasks implementation dependency. */
public final class WindDevice extends EnergyDevice {
    private final WindTurbineBlockEntity turbine;
    private final List<DeviceMetric<?>> metrics=new ArrayList<>();
    private final DeviceMetric<Integer> tier=count(id("wind_turbine_level"),"Level",3);
    private final DeviceMetric<Double> wind=number("wind_strength",Unit.PERCENT,UpdatePolicy.NORMAL);
    private final DeviceMetric<WindState.Trend> trend=DeviceMetric.builder(id("wind_trend"),name(id("wind_trend"),"Trend"),
            MetricTypes.enumeration(id("wind_trend"),WindState.Trend.class),WindState.Trend.STABLE).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<WindStatus> state=DeviceMetric.builder(id("wind_status"),name(id("wind_status"),"Status"),
            MetricTypes.enumeration(id("wind_status"),WindStatus.class),WindStatus.NO_WIND).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Double> rate=number("current_generation",HeCapabilities.HE_PER_TICK,UpdatePolicy.NORMAL);
    // HomeCore Percentage is constrained to 100. Efficiency can exceed 100%, so use DOUBLE + Unit.PERCENT.
    private final DeviceMetric<Double> percentage=number("generation_percentage",Unit.PERCENT,UpdatePolicy.NORMAL);
    private final DeviceMetric<Long> period=energy(id("generated_this_period"),"Generated this period");
    private final DeviceMetric<Long> nominal=DeviceMetric.builder(id("nominal_generation"),name(id("nominal_generation"),"Nominal output"),MetricTypes.LONG,0L)
            .unit(new Unit(id("he_per_reference_period"),"HE / 24 000 ticks")).updatePolicy(UpdatePolicy.STATIC).build();
    private final DeviceMetric<Double> weather=number("weather_multiplier",Unit.NONE,UpdatePolicy.ON_CHANGE);
    private final DeviceMetric<fr.lkdm.homelink.energy.energy.Weather> weatherState=DeviceMetric.builder(id("wind_weather"),name(id("wind_weather"),"Weather"),
            MetricTypes.enumeration(id("wind_weather"),fr.lkdm.homelink.energy.energy.Weather.class),fr.lkdm.homelink.energy.energy.Weather.CLEAR).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Integer> altitude=DeviceMetric.builder(id("altitude"),name(id("altitude"),"Hub altitude"),MetricTypes.INTEGER,0).updatePolicy(UpdatePolicy.STATIC).build();
    private final DeviceMetric<Double> height=number("altitude_multiplier",Unit.NONE,UpdatePolicy.ON_CHANGE);
    private final DeviceMetric<Double> efficiency=number("effective_efficiency",Unit.PERCENT,UpdatePolicy.NORMAL);
    private final DeviceMetric<Boolean> rotor=flag(id("rotor_clear"),"Rotor clear"),sky=flag(id("sky_visible"),"Sky visible"),connected=flag(id("network_connected"),"Network connected");
    private final DeviceMetric<Long> buffer=energy(id("buffer_energy"),"Buffer");
    private final DeviceSchema schema;
    private boolean observed,lastRotor,lastSky,lastConnected,lastGenerating;
    public static final Set<ResourceLocation> EVENTS=Set.of(id("wind_generation_started"),id("wind_generation_stopped"),
            id("wind_rotor_obstructed"),id("wind_rotor_cleared"),id("wind_sky_blocked"),id("wind_network_connected"),id("wind_network_disconnected"));
    public WindDevice(WindTurbineBlockEntity turbine,Consumer<DeviceEvent> events) {
        super(turbine,events); this.turbine=turbine;
        metrics.addAll(List.of(tier,wind,trend,state,rate,percentage,period,nominal,weather,weatherState,altitude,height,efficiency,rotor,sky,buffer,connected));
        schema=DeviceSchema.from(this); refresh();
    }
    private static ResourceLocation id(String key) { return HomeLinkEnergy.id(key); }
    private static DeviceMetric<Double> number(String key,Unit unit,UpdatePolicy policy) {
        return DeviceMetric.builder(id(key),name(id(key),key),MetricTypes.DOUBLE,0.).unit(unit).updatePolicy(policy).build();
    }
    @Override protected void update() {
        var c=turbine.core();
        tier.setValue(turbine.tier().level()); wind.setValue(turbine.wind()*100); trend.setValue(turbine.trend()); state.setValue(c.status());
        rate.setValue(c.rate()); percentage.setValue(turbine.tier().nominal()==0?0:c.rate()*24000/turbine.tier().nominal()*100);
        period.setValue(c.generatedPeriod()); nominal.setValue(turbine.tier().nominal()); weather.setValue(turbine.weatherMultiplier());
        weatherState.setValue(turbine.weather());
        altitude.setValue(turbine.hubY()); height.setValue(turbine.altitudeMultiplier()); efficiency.setValue(c.efficiency()*100);
        boolean clear=turbine.rotorClear(),visible=turbine.skyVisible(),linked=turbine.networkConnection()==EnergyNetworks.Connection.CONNECTED;
        boolean generating=c.status()==WindStatus.GENERATING;
        rotor.setValue(clear); sky.setValue(visible); connected.setValue(linked); buffer.setValue(c.buffer().stored());
        if(observed) {
            if(clear!=lastRotor) event(clear?"wind_rotor_cleared":"wind_rotor_obstructed",!clear);
            if(!visible && lastSky) event("wind_sky_blocked",true);
            if(linked!=lastConnected) event(linked?"wind_network_connected":"wind_network_disconnected",!linked);
            // Calm wind is normal: stopped is informative, never a low-wind warning.
            if(generating!=lastGenerating) event(generating?"wind_generation_started":"wind_generation_stopped",false);
        }
        observed=true; lastRotor=clear; lastSky=visible; lastConnected=linked; lastGenerating=generating;
    }
    private void event(String key,boolean warning) {
        publish(id(key),warning?DeviceEvent.Severity.WARNING:DeviceEvent.Severity.INFO,Map.of("reason",turbine.core().status().name()));
    }
    @Override public ResourceLocation deviceType() { return id("wind_turbine"); }
    @Override public List<DeviceMetric<?>> metrics() { return Collections.unmodifiableList(metrics); }
    @Override public Set<ResourceLocation> eventTypes() { return EVENTS; }
    @Override public DeviceSchema schema() { return schema; }
    @Override public DeviceStatus status() {
        if(!isValid()) return DeviceStatus.OFFLINE;
        var status=turbine.core().status();
        var result=switch(status) {
            case GENERATING,NO_WIND -> DeviceStatus.ONLINE;
            case DISABLED -> DeviceStatus.DISABLED;
            default -> DeviceStatus.WARNING;
        };
        return result.withMessage(Component.translatable("status.homelink_energy.wind."+status.name().toLowerCase(Locale.ROOT)));
    }
}

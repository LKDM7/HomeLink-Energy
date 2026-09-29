package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homelink.energy.blockentity.EnergyDeviceBlockEntity;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;

/**
 * Common part of the HomeCore devices of HomeLink Energy: identity, validity, metric builders and
 * event publishing. Metrics are refreshed about once per second by the block entity; events are
 * only published on transitions, never once per tick.
 */
public abstract class EnergyDevice implements DashboardDevice, NetworkMember, Renamable {
    protected final EnergyDeviceBlockEntity entity;
    protected final UUID identity;
    private final Consumer<DeviceEvent> events;
    private boolean failureLogged;

    protected EnergyDevice(EnergyDeviceBlockEntity entity, Consumer<DeviceEvent> events) {
        this.entity = entity;
        this.identity = entity.deviceId();
        this.events = events;
    }

    /** Copies the block state into the metrics and publishes transitions; failures are logged once. */
    public final void refresh() {
        try {
            update();
        } catch (RuntimeException failure) {
            if (!failureLogged) {
                failureLogged = true;
                LogUtils.getLogger().error("HomeCore rejected an update of energy device {}", identity, failure);
            }
        }
    }

    protected abstract void update();

    protected void publish(ResourceLocation type, DeviceEvent.Severity severity, Map<String, String> data) {
        if (isValid()) events.accept(new DeviceEvent(type, identity, Instant.now(), severity, data));
    }

    protected static Component name(ResourceLocation id, String fallback) {
        return Component.translatableWithFallback("metric.homelink_energy." + id.getPath(), fallback);
    }

    protected static DeviceMetric<Long> energy(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.LONG, 0L).unit(HeCapabilities.HE).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    protected static DeviceMetric<Double> flow(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.DOUBLE, 0.0).unit(HeCapabilities.HE_PER_TICK).updatePolicy(UpdatePolicy.FAST).build();
    }

    protected static DeviceMetric<Percentage> percentage(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.PERCENTAGE, new Percentage(0)).unit(Unit.PERCENT)
                .range(0, 100, 0).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    protected static DeviceMetric<Boolean> flag(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.BOOLEAN, false).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    protected static DeviceMetric<Integer> count(ResourceLocation id, String label, int max) {
        return DeviceMetric.builder(id, name(id, label), MetricTypes.INTEGER, 0).range(0, max, 1).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    protected static Percentage percent(double value) {
        return new Percentage(Double.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0);
    }

    @Override public UUID id() { return identity; }
    @Override public Component displayName() { return entity.name(); }
    @Override public ActionResult rename(String name) { entity.rename(name); return ActionResult.success(); }

    // Switchable, for the generators that declare it: batteries have nothing to switch off.
    public boolean powered() { return entity.powered(); }
    public ActionResult setPowered(boolean powered) { entity.setPowered(powered); return ActionResult.success(); }

    // NetworkMember: lets a dashboard move this block between networks while its recorded binding stays in step.
    @Override public Optional<UUID> homeNetwork() { return entity.homeNetwork(); }
    @Override public Optional<UUID> owner() { return entity.owner(); }
    @Override public boolean canConfigure(ServerPlayer player) { return EnergyHomeCore.canConfigure(player, entity); }
    @Override public void homeNetworkChanged(Optional<HomeNetwork> network) {
        network.ifPresentOrElse(value -> entity.setHomeNetwork(value.id(), value.name()), entity::clearHomeNetwork);
    }
    @Override public Optional<BlockPos> position() { return Optional.of(entity.getBlockPos().immutable()); }
    @Override public Optional<ResourceKey<Level>> dimension() {
        return entity.getLevel() == null ? Optional.empty() : Optional.of(entity.getLevel().dimension());
    }

    @Override
    public boolean isValid() {
        return !entity.isRemoved() && entity.getLevel() instanceof ServerLevel server && server.isLoaded(entity.getBlockPos())
                && server.getBlockEntity(entity.getBlockPos()) == entity && identity.equals(entity.deviceId());
    }
}

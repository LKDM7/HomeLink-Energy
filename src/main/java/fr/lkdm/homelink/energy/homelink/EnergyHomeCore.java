package fr.lkdm.homelink.energy.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.EnergyDeviceBlockEntity;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bridge to the public HomeCore API only. Providers are registered once; live devices are
 * registered and unregistered from the block entity lifecycle.
 */
public final class EnergyHomeCore {
    public static final int OPERATOR_LEVEL = 2;

    public enum BindResult { BOUND, UNBOUND, UNCHANGED, DENIED, UNKNOWN_NETWORK }

    private EnergyHomeCore() { }

    /** Call once during common setup. */
    public static void registerProviders() {
        DashboardAPI.registerDeviceProvider(EnergyRegistries.WIND_TURBINE.get(), turbine -> turbine.createDevice(event -> publish(turbine, event)));
        DashboardAPI.registerDeviceProvider(EnergyRegistries.SOLAR_PANEL.get(), panel -> panel.createDevice(event -> publish(panel, event)));
        DashboardAPI.registerDeviceProvider(EnergyRegistries.BATTERY.get(), battery -> battery.createDevice(event -> publish(battery, event)));
        HomeLinkEnergy.LOGGER.info("HomeLink Energy registered its HomeCore device providers (HomeCore API {})", DashboardAPI.API_VERSION);
    }

    private static void publish(EnergyDeviceBlockEntity entity, fr.lkdm.homecore.api.event.DeviceEvent event) {
        if (entity.getLevel() instanceof ServerLevel level) DashboardAPI.events(level.getServer()).publish(event);
    }

    /** Registers a loaded block as a live device, giving a copied block its own identity. */
    public static Optional<EnergyDevice> register(ServerLevel level, EnergyDeviceBlockEntity entity) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        if (registry.get(entity.deviceId()).isPresent()) {
            HomeLinkEnergy.LOGGER.warn("Energy block at {} duplicated device {}; assigning a new identity", entity.getBlockPos(), entity.deviceId());
            entity.resetIdentityAfterCollision();
        }
        try {
            Optional<DashboardDevice> discovered = DashboardAPI.providers().discover(entity);
            if (discovered.isEmpty() || !(discovered.get() instanceof EnergyDevice device)) return Optional.empty();
            registry.register(device);
            return Optional.of(device);
        } catch (RuntimeException rejected) {
            HomeLinkEnergy.LOGGER.error("HomeCore rejected energy device {}", entity.deviceId(), rejected);
            return Optional.empty();
        }
    }

    /** Unregisters only if the registry still holds this exact instance. */
    public static void unregister(ServerLevel level, DashboardDevice device) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        registry.get(device.id()).filter(current -> current == device).ifPresent(current -> registry.unregister(current.id()));
    }

    /** Owner, operators and CONFIGURE members of the device's network may change its network. */
    public static boolean canConfigure(ServerPlayer player, EnergyDeviceBlockEntity entity) {
        if (player.hasPermissions(OPERATOR_LEVEL)) return true;
        if (entity.owner().map(owner -> owner.equals(player.getUUID())).orElse(true)) return true;
        return entity.homeNetwork().map(network -> DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)).orElse(false);
    }

    /** Networks in which this player may add or remove devices. */
    public static List<HomeNetwork> manageableNetworks(ServerPlayer player) {
        return DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.MANAGE_NETWORK)).toList();
    }

    /**
     * Attaches the block to a network (empty target = detach). The player needs rights on the block
     * and MANAGE_NETWORK on both the new and the previous network.
     */
    public static BindResult bind(ServerPlayer player, EnergyDeviceBlockEntity entity, Optional<UUID> target) {
        if (!canConfigure(player, entity)) return BindResult.DENIED;
        Optional<UUID> current = entity.homeNetwork();
        if (current.equals(target)) return BindResult.UNCHANGED;
        var networks = DashboardAPI.networks(player.server);
        Optional<HomeNetwork> destination = Optional.empty();
        if (target.isPresent()) {
            destination = networks.getNetwork(target.get());
            if (destination.isEmpty()) return BindResult.UNKNOWN_NETWORK;
            if (!DashboardAPI.hasPermission(player, target.get(), Permission.MANAGE_NETWORK)) return BindResult.DENIED;
        }
        if (current.isPresent() && networks.getNetwork(current.get()).isPresent()) {
            if (!DashboardAPI.hasPermission(player, current.get(), Permission.MANAGE_NETWORK)) return BindResult.DENIED;
            networks.removeDevice(current.get(), entity.deviceId());
        }
        if (destination.isPresent()) {
            networks.addDevice(destination.get().id(), entity.deviceId());
            entity.setHomeNetwork(destination.get().id(), destination.get().name());
            return BindResult.BOUND;
        }
        entity.clearHomeNetwork();
        return BindResult.UNBOUND;
    }

    /** The block was destroyed: remove its identity from its network. */
    public static void forgetOnRemoval(ServerLevel level, EnergyDeviceBlockEntity entity) {
        entity.homeNetwork().ifPresent(network -> {
            var networks = DashboardAPI.networks(level.getServer());
            if (networks.getNetwork(network).isPresent()) networks.removeDevice(network, entity.deviceId());
        });
    }
}

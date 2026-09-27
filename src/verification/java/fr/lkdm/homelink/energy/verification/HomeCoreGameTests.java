package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.charge;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.check;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.place;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.world;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.energy.SolarStatus;
import fr.lkdm.homelink.energy.homelink.EnergyHomeCore;
import fr.lkdm.homelink.energy.homelink.EnergyIds;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Part 2: HomeCore devices, metrics, network membership and events, through the public API only. */
@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeCoreGameTests {
    private static DashboardDevice device(GameTestHelper helper, UUID id) {
        return DashboardAPI.devices(helper.getLevel().getServer()).get(id).orElse(null);
    }

    private static Object metric(DashboardDevice device, ResourceLocation id) {
        return device.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().map(DeviceMetric::value).orElseThrow();
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    @GameTest(template = "empty", batch = "homecore_solar", timeoutTicks = 100)
    public static void solarPanelIsADeviceWithMetrics(GameTestHelper helper) {
        world(helper, 6_000, false, false);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_2.get(), new BlockPos(1, 2, 1));
        helper.runAfterDelay(45, () -> {
            DashboardDevice device = device(helper, panel.deviceId());
            check(helper, device != null, "Solar panel not registered with HomeCore");
            check(helper, device.deviceType().equals(EnergyIds.SOLAR_PANEL), "Device type");
            for (ResourceLocation id : List.of(EnergyIds.GENERATION_RATE, EnergyIds.GENERATION_PERCENTAGE, EnergyIds.GENERATED_TODAY,
                    EnergyIds.NOMINAL_DAILY_GENERATION, EnergyIds.SKY_VISIBLE, EnergyIds.WEATHER_EFFICIENCY, EnergyIds.BUFFER, EnergyIds.NETWORK_CONNECTED)) {
                check(helper, device.schema().metrics().stream().anyMatch(metric -> metric.id().equals(id)), "Missing metric " + id);
            }
            check(helper, device.schema().events().contains(EnergyIds.SOLAR_SKY_BLOCKED), "Events");
            check(helper, (long) metric(device, EnergyIds.NOMINAL_DAILY_GENERATION) == 8_000, "Nominal metric");
            check(helper, (boolean) metric(device, EnergyIds.SKY_VISIBLE), "Sky metric");
            check(helper, metric(device, EnergyIds.SOLAR_STATUS) == SolarStatus.GENERATING, "Status metric");
            check(helper, (double) metric(device, EnergyIds.GENERATION_RATE) > 1.0, "Rate metric (Solar II ~1.05 HE/t at noon)");
            check(helper, ((Percentage) metric(device, EnergyIds.WEATHER_EFFICIENCY)).value() == 100, "Weather metric");
            check(helper, (long) metric(device, fr.lkdm.homelink.energy.HomeLinkEnergy.id("buffer_capacity")) == 1_000, "Buffer capacity in HE");
            check(helper, metric(device, EnergyIds.BUFFER) instanceof Long, "HE must not use the FE Energy metric");
            var units = device.metrics().stream().filter(m -> m.id().equals(EnergyIds.GENERATION_RATE)).findFirst().orElseThrow().unit();
            check(helper, units.equals(HeCapabilities.HE_PER_TICK), "A flow must be in HE/t, not HE");
            var registries = helper.getLevel().registryAccess();
            SolarPanelBlockEntity copy = new SolarPanelBlockEntity(panel.getBlockPos(), panel.getBlockState());
            copy.loadWithComponents(panel.saveWithoutMetadata(registries), registries);
            check(helper, copy.deviceId().equals(panel.deviceId()), "UUID changed on reload");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "homecore_battery", timeoutTicks = 200)
    public static void batteryMetricsAndHysteresisEvents(GameTestHelper helper) {
        world(helper, 18_000, false, false);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_2.get(), new BlockPos(0, 1, 0));
        charge(helper, battery, 60_000);
        List<DeviceEvent> received = new ArrayList<>();
        var subscription = DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(battery.deviceId())) received.add(event);
        });
        helper.runAfterDelay(25, () -> {
            DashboardDevice device = device(helper, battery.deviceId());
            check(helper, device != null && device.deviceType().equals(EnergyIds.BATTERY), "Battery not registered");
            check(helper, (long) metric(device, EnergyIds.STORED_ENERGY) == 60_000 && (long) metric(device, EnergyIds.CAPACITY) == 120_000, "Stored/capacity");
            check(helper, ((Percentage) metric(device, EnergyIds.PERCENTAGE)).value() == 50, "Percentage");
            charge(helper, battery, 16_800); // 14 %
            helper.runAfterDelay(25, () -> {
                charge(helper, battery, 15_600); // 13 %
                helper.runAfterDelay(25, () -> {
                    charge(helper, battery, 36_000); // 30 %: re-armed
                    helper.runAfterDelay(25, () -> {
                        charge(helper, battery, 16_800); // 14 % again
                        helper.runAfterDelay(25, () -> {
                            subscription.close();
                            long lows = received.stream().filter(e -> e.type().equals(EnergyIds.BATTERY_LOW)).count();
                            check(helper, lows == 2, "Expected BATTERY_LOW at 14 %, none at 13 %, again after 30 %: got " + lows);
                            check(helper, received.stream().allMatch(e -> e.data().containsKey("percentage")), "Event data");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "homecore_network", timeoutTicks = 100)
    public static void joiningAHomeNetworkNeedsPermissions(GameTestHelper helper) {
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        FakePlayer owner = player(helper, "energy_owner");
        FakePlayer stranger = player(helper, "energy_stranger");
        battery.setOwner(owner.getUUID());
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        UUID home = networks.createNetwork("Energy test", owner.getUUID()).id();
        UUID foreign = networks.createNetwork("Foreign", stranger.getUUID()).id();
        try {
            check(helper, EnergyHomeCore.bind(stranger, battery, Optional.of(foreign)) == EnergyHomeCore.BindResult.DENIED, "A stranger bound someone else's battery");
            check(helper, EnergyHomeCore.bind(owner, battery, Optional.of(foreign)) == EnergyHomeCore.BindResult.DENIED, "Owner joined a network it cannot manage");
            check(helper, EnergyHomeCore.bind(owner, battery, Optional.of(home)) == EnergyHomeCore.BindResult.BOUND, "Owner could not bind");
            check(helper, networks.getDevices(home).contains(battery.deviceId()) && battery.homeNetworkName().equals("Energy test"), "Membership");
            helper.destroyBlock(new BlockPos(0, 1, 0));
            check(helper, !networks.getDevices(home).contains(battery.deviceId()), "A destroyed battery stays in its network");
            helper.succeed();
        } finally {
            networks.deleteNetwork(home);
            networks.deleteNetwork(foreign);
        }
    }

    /** The Dashboard moves machines through HomeCore's NetworkMember contract; the block must record the same binding. */
    @GameTest(template = "empty", batch = "homecore_member", timeoutTicks = 100)
    public static void dashboardBindingKeepsTheBlockInStepAndShowsItsName(GameTestHelper helper) {
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        FakePlayer owner = player(helper, "energy_member_owner");
        FakePlayer stranger = player(helper, "energy_member_stranger");
        battery.setOwner(owner.getUUID());
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        UUID first = networks.createNetwork("Base", owner.getUUID()).id();
        UUID second = networks.createNetwork("Atelier", owner.getUUID()).id();
        helper.runAfterDelay(45, () -> {
            try {
                DashboardDevice device = device(helper, battery.deviceId());
                check(helper, device instanceof fr.lkdm.homecore.api.network.NetworkMember, "Energy devices must implement NetworkMember");
                check(helper, DashboardAPI.bindDevice(stranger, device, Optional.of(first)) == fr.lkdm.homecore.api.network.NetworkMember.BindResult.DENIED,
                        "A stranger moved someone else's battery");
                check(helper, DashboardAPI.bindDevice(owner, device, Optional.of(first)) == fr.lkdm.homecore.api.network.NetworkMember.BindResult.BOUND,
                        "The owner could not bind from the dashboard");
                check(helper, battery.homeNetwork().equals(Optional.of(first)) && battery.homeNetworkName().equals("Base"), "Block binding not recorded");
                check(helper, DashboardAPI.bindDevice(owner, device, Optional.of(second)) == fr.lkdm.homecore.api.network.NetworkMember.BindResult.BOUND
                        && !networks.getDevices(first).contains(battery.deviceId()) && networks.getDevices(second).contains(battery.deviceId())
                        && battery.homeNetwork().equals(Optional.of(second)), "Moving must leave the previous network");
                check(helper, device.displayName().getString().equals(battery.getBlockState().getBlock().getName().getString()), "Default name");
                var registries = helper.getLevel().registryAccess();
                var tag = battery.saveWithoutMetadata(registries);
                tag.putString("CustomName", "\"Batterie nord\"");
                battery.loadWithComponents(tag, registries);
                check(helper, device.displayName().getString().equals("Batterie nord") && battery.getDisplayName().getString().equals("Batterie nord"),
                        "An anvil name must reach the menu and HomeCore");
                var copy = new BatteryBlockEntity(battery.getBlockPos(), battery.getBlockState());
                copy.loadWithComponents(battery.saveWithoutMetadata(registries), registries);
                check(helper, copy.name().getString().equals("Batterie nord"), "The custom name must survive a reload");
                helper.succeed();
            } finally {
                networks.deleteNetwork(first);
                networks.deleteNetwork(second);
            }
        });
    }

    @GameTest(template = "empty", batch = "homecore_sky", timeoutTicks = 200)
    public static void skyBlockedEventOnTransitionOnly(GameTestHelper helper) {
        world(helper, 6_000, false, false);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_1.get(), new BlockPos(1, 2, 1));
        List<DeviceEvent> received = new ArrayList<>();
        var subscription = DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(panel.deviceId())) received.add(event);
        });
        helper.runAfterDelay(45, () -> {
            helper.setBlock(new BlockPos(1, 5, 1), Blocks.GLASS);
            helper.runAfterDelay(90, () -> {
                subscription.close();
                long blocked = received.stream().filter(e -> e.type().equals(EnergyIds.SOLAR_SKY_BLOCKED)).count();
                long stopped = received.stream().filter(e -> e.type().equals(EnergyIds.SOLAR_STOPPED_GENERATING)).count();
                check(helper, blocked == 1 && stopped == 1, "Expected one sky_blocked and one stopped event, got " + received);
                helper.succeed();
            });
        });
    }
}

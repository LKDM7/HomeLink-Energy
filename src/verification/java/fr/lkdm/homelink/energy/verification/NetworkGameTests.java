package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.charge;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.check;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.place;
import static fr.lkdm.homelink.energy.verification.EnergyValidation.world;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.block.CopperEnergyCableBlock;
import fr.lkdm.homelink.energy.blockentity.BatteryBlockEntity;
import fr.lkdm.homelink.energy.blockentity.CableBlockEntity;
import fr.lkdm.homelink.energy.blockentity.SolarPanelBlockEntity;
import fr.lkdm.homelink.energy.config.EnergyConfig;
import fr.lkdm.homelink.energy.network.EnergyNetworks;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Part 2: cable networks. Most tests run at night with pre-filled buffers so that no new
 * production interferes: every HE is accounted for exactly.
 */
@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static void night(GameTestHelper helper) { world(helper, 18_000, false, false); }

    private static void cables(GameTestHelper helper, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            BlockPos pos = new BlockPos(x, y, z);
            helper.setBlock(pos.below(), Blocks.STONE);
            var state = EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState();
            if (helper.getLevel().getCapability(fr.lkdm.homelink.energy.energy.HeCapabilities.PORT, helper.absolutePos(pos.above()), Direction.DOWN) != null)
                state = state.setValue(CopperEnergyCableBlock.FACES.get(Direction.UP), true);
            helper.setBlock(pos, state);
        }
    }

    private static EnergyNetworks networks(GameTestHelper helper) { return EnergyNetworks.get(helper.getLevel()); }

    @GameTest(template = "empty", batch = "net_1", timeoutTicks = 100)
    public static void cablesConnectVisuallyToPortsOnly(GameTestHelper helper) {
        night(helper);
        place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        cables(helper, 1, 2, 1, 0);
        place(helper, EnergyRegistries.SOLAR_PANEL_1.get(), new BlockPos(2, 2, 0));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        var first = helper.getBlockState(new BlockPos(1, 1, 0));
        check(helper, CopperEnergyCableBlock.connects(helper.getLevel(), helper.absolutePos(new BlockPos(1,1,0)), Direction.WEST), "Cable not joined to the battery");
        check(helper, CopperEnergyCableBlock.connects(helper.getLevel(), helper.absolutePos(new BlockPos(1,1,0)), Direction.EAST), "Cable not joined to the next cable");
        check(helper, !CopperEnergyCableBlock.connects(helper.getLevel(), helper.absolutePos(new BlockPos(1,1,0)), Direction.SOUTH), "Cable joined to plain stone");
        var underPanel = helper.getBlockState(new BlockPos(2,1,0)).setValue(CopperEnergyCableBlock.FACES.get(Direction.UP), true);
        helper.setBlock(new BlockPos(2,1,0), underPanel);
        check(helper, CopperEnergyCableBlock.connects(helper.getLevel(), helper.absolutePos(new BlockPos(2,1,0)), Direction.UP), "Cable not joined to the panel's bottom output");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "net_2", timeoutTicks = 100)
    public static void producerFeedsConsumerExactly(GameTestHelper helper) {
        night(helper);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), new BlockPos(0, 2, 0));
        panel.core().buffer().setStored(1_000);
        cables(helper, 0, 3, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(4, 1, 0));
        consumer.demand = 10;
        helper.runAfterDelay(30, () -> {
            check(helper, consumer.received > 0, "Consumer received nothing");
            check(helper, panel.core().buffer().stored() + consumer.received == 1_000,
                    "Not conserved: buffer " + panel.core().buffer().stored() + " + consumer " + consumer.received);
            check(helper, consumer.received % 10 == 0, "The consumer got more than it asked for in a tick: " + consumer.received);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "net_3", timeoutTicks = 100)
    public static void surplusChargesBatteriesAfterConsumers(GameTestHelper helper) {
        night(helper);
        SolarPanelBlockEntity panel = place(helper, EnergyRegistries.SOLAR_PANEL_3.get(), new BlockPos(0, 2, 0));
        panel.core().buffer().setStored(1_000);
        cables(helper, 0, 3, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(4, 1, 0));
        consumer.demand = 3;
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(2, 1, 1));
        helper.runAfterDelay(25, () -> {
            long total = panel.core().buffer().stored() + consumer.received + battery.stored();
            check(helper, total == 1_000, "Not conserved: " + total);
            check(helper, consumer.received >= 3 * 20, "Consumer starved: " + consumer.received);
            check(helper, battery.stored() > 0 && battery.stored() <= 8L * 25, "Battery I charge must respect 8 HE/t: " + battery.stored());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "net_4", timeoutTicks = 100)
    public static void batteriesCoverTheDeficitWithinTheirRate(GameTestHelper helper) {
        night(helper);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        charge(helper, battery, 1_000);
        cables(helper, 1, 3, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(4, 1, 0));
        consumer.demand = 100;
        helper.runAfterDelay(21, () -> {
            check(helper, battery.stored() + consumer.received == 1_000, "Not conserved");
            check(helper, consumer.received > 0 && consumer.received % 8 == 0 && consumer.received <= 8 * 21,
                    "Battery I must discharge at most 8 HE/t: " + consumer.received);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "net_5", timeoutTicks = 100)
    public static void batteriesNeverFeedEachOther(GameTestHelper helper) {
        night(helper);
        BatteryBlockEntity full = place(helper, EnergyRegistries.BATTERY_3.get(), new BlockPos(0, 1, 0));
        charge(helper, full, 50_000);
        cables(helper, 2, 3, 1, 0);
        BatteryBlockEntity empty = place(helper, EnergyRegistries.BATTERY_3.get(), new BlockPos(4, 1, 0));
        helper.runAfterDelay(40, () -> {
            check(helper, full.stored() == 50_000 && empty.stored() == 0, "Battery to battery transfer: " + full.stored() + " / " + empty.stored());
            check(helper, networks(helper).networksOfMachine(helper.absolutePos(new BlockPos(0, 1, 0))).size() == 1, "Battery not in the network");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "net_6", timeoutTicks = 200)
    public static void brokenCableSplitsAndReplacedCableMerges(GameTestHelper helper) {
        night(helper);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_2.get(), new BlockPos(0, 1, 0));
        charge(helper, battery, 5_000);
        cables(helper, 2, 5, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(6, 1, 0));
        consumer.demand = 4;
        BlockPos middle = new BlockPos(3, 1, 0);
        helper.runAfterDelay(10, () -> {
            check(helper, consumer.received > 0, "Not delivering before the break");
            helper.destroyBlock(middle);
            helper.runAfterDelay(2, () -> {
                long afterBreak = consumer.received;
                var level = helper.getLevel();
                var left = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(2, 1, 0)));
                var right = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0)));
                check(helper, left.isPresent() && right.isPresent() && left.get() != right.get(), "Network did not split");
                helper.runAfterDelay(10, () -> {
                    check(helper, consumer.received == afterBreak, "Energy crossed a broken cable");
                    check(helper, battery.stored() + consumer.received == 5_000, "Split lost or created energy");
                    helper.setBlock(middle, EnergyRegistries.COPPER_ENERGY_CABLE.get());
                    helper.runAfterDelay(10, () -> {
                        check(helper, consumer.received > afterBreak, "Merged network does not deliver");
                        check(helper, networks(helper).networkOfCable(helper.absolutePos(new BlockPos(2, 1, 0)))
                                .equals(networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0)))), "Network did not merge");
                        check(helper, battery.stored() + consumer.received == 5_000, "Merge lost or created energy");
                        check(helper, level.getBlockEntity(helper.absolutePos(middle)) instanceof CableBlockEntity, "Cable entity");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "net_7", timeoutTicks = 200)
    public static void unloadedCableStopsTheFlowWithoutFreeEnergy(GameTestHelper helper) {
        night(helper);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_2.get(), new BlockPos(0, 1, 0));
        charge(helper, battery, 2_000);
        cables(helper, 2, 5, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(6, 1, 0));
        consumer.demand = 4;
        CableBlockEntity middle = helper.getBlockEntity(new BlockPos(3, 1, 0));
        helper.runAfterDelay(5, () -> {
            // The same hook the chunk unload runs: the cable leaves the graph, nothing is loaded.
            middle.onChunkUnloaded();
            helper.runAfterDelay(2, () -> {
                long frozen = consumer.received;
                long stored = battery.stored();
                helper.runAfterDelay(20, () -> {
                    check(helper, consumer.received == frozen && battery.stored() == stored, "Energy crossed an unloaded cable");
                    middle.onLoad();
                    helper.runAfterDelay(5, () -> {
                        check(helper, consumer.received > frozen, "Reloaded cable does not carry energy");
                        check(helper, battery.stored() + consumer.received == 2_000, "Not conserved over unload/reload");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "net_limit", timeoutTicks = 100)
    public static void oversizedNetworkStopsCleanly(GameTestHelper helper) {
        night(helper);
        int previous = EnergyConfig.MAX_ENERGY_NETWORK_NODES.get();
        EnergyConfig.MAX_ENERGY_NETWORK_NODES.set(8);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        charge(helper, battery, 1_000);
        cables(helper, 1, 8, 1, 0);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(9, 1, 0));
        helper.runAfterDelay(10, () -> {
            try {
                var network = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0))).orElseThrow();
                check(helper, network.tooLarge(), "10 nodes over a limit of 8 must be NETWORK_TOO_LARGE");
                check(helper, consumer.received == 0 && battery.stored() == 1_000, "An oversized network moved energy");
                check(helper, battery.networkConnection() == EnergyNetworks.Connection.NETWORK_TOO_LARGE, "Battery state " + battery.networkConnection());
                helper.succeed();
            } finally {
                EnergyConfig.MAX_ENERGY_NETWORK_NODES.set(previous);
            }
        });
    }

    @GameTest(template = "empty", batch = "net_8", timeoutTicks = 60)
    public static void batteryCapacitiesAndOverflowClamp(GameTestHelper helper) {
        night(helper);
        BatteryBlockEntity b1 = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(0, 1, 0));
        BatteryBlockEntity b2 = place(helper, EnergyRegistries.BATTERY_2.get(), new BlockPos(2, 1, 0));
        BatteryBlockEntity b3 = place(helper, EnergyRegistries.BATTERY_3.get(), new BlockPos(4, 1, 0));
        check(helper, b1.capacity() == 40_000 && b2.capacity() == 120_000 && b3.capacity() == 320_000, "Capacities");
        check(helper, b1.stored() == 0 && b2.stored() == 0 && b3.stored() == 0, "Batteries start empty");
        charge(helper, b3, Long.MAX_VALUE);
        check(helper, b3.stored() == 320_000, "Overflowing save not clamped: " + b3.stored());
        charge(helper, b2, -50);
        check(helper, b2.stored() == 0, "Negative save not clamped");
        check(helper, b3.port(null).insert(1, false) == 0, "Full battery accepted energy");
        check(helper, b1.port(null).insert(1_000, false) == 8 && b2.port(null).insert(1_000, false) == 32, "Charge limits 8 / 32");
        check(helper, b3.port(null).extract(1_000, false) == 128, "Discharge limit 128");
        helper.runAfterDelay(30, () -> {
            check(helper, b1.stored() == 8, "Battery I changed by itself: " + b1.stored());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "net_id", timeoutTicks = 40)
    public static void namespace(GameTestHelper helper) {
        check(helper, HomeLinkEnergy.id("copper_energy_cable").equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(EnergyRegistries.COPPER_ENERGY_CABLE.get())), "Cable id");
        helper.succeed();
    }

    /** A trace under a stone block powers the machine standing on that block: one plain block is crossed. */
    @GameTest(template = "empty", batch = "net_through", timeoutTicks = 100)
    public static void cableFeedsMachineThroughSupportBlock(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cable = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(cable.above(), Blocks.STONE.defaultBlockState());
        var ceiling = EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState()
                .setValue(CopperEnergyCableBlock.FACES.get(Direction.DOWN), false).setValue(CopperEnergyCableBlock.FACES.get(Direction.UP), true);
        level.setBlockAndUpdate(cable, ceiling);
        BatteryBlockEntity battery = place(helper, EnergyRegistries.BATTERY_1.get(), new BlockPos(3, 3, 2));
        charge(helper, battery, 10_000);
        TestConsumer consumer = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(2, 5, 2));
        // Beside the support block rather than on its opposite face: never fed.
        TestConsumer beside = place(helper, EnergyValidation.CONSUMER.get(), new BlockPos(2, 4, 1));
        helper.runAfterDelay(40, () -> {
            check(helper, level.getBlockState(cable).is(EnergyRegistries.COPPER_ENERGY_CABLE.get()), "Ceiling cable dropped");
            check(helper, consumer.received > 0, "No HE crossed the stone block");
            check(helper, beside.received == 0, "A machine beside the support was fed");
            check(helper, EnergyNetworks.connection(level, consumer.getBlockPos()) == EnergyNetworks.Connection.CONNECTED, "Machine on the block not connected");
            long before = consumer.received;
            level.setBlockAndUpdate(cable.above(2), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(10, () -> {
                check(helper, EnergyNetworks.connection(level, cable.above(2)) == EnergyNetworks.Connection.NONE, "Removed machine still connected");
                check(helper, consumer.received == before, "Removed machine still receives");
                helper.succeed();
            });
        });
    }

    /** A change in one network leaves the others untouched: same object, statistics kept. */
    @GameTest(template = "empty", batch = "net_incremental", timeoutTicks = 100)
    public static void changeRebuildsOnlyTheNetworkItTouches(GameTestHelper helper) {
        night(helper);
        cables(helper, 0, 2, 1, 0);
        cables(helper, 0, 2, 1, 4);
        helper.runAfterDelay(5, () -> {
            var far = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 4))).orElseThrow();
            var near = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 0))).orElseThrow();
            check(helper, far != near, "Separate lines share a network");
            cables(helper, 3, 3, 1, 0);
            helper.runAfterDelay(5, () -> {
                check(helper, networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 4))).orElseThrow() == far,
                        "An untouched network was rebuilt");
                var grown = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(3, 1, 0))).orElseThrow();
                check(helper, grown != near && grown.cables().size() == 4
                        && grown == networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 0))).orElseThrow(),
                        "The extended line was not rebuilt as one network");
                check(helper, !networks(helper).networks().contains(near), "The replaced network is still ticking");
                helper.succeed();
            });
        });
    }

    /** Bridging two lines merges them; breaking the bridge splits them again with no stale cable. */
    @GameTest(template = "empty", batch = "net_merge", timeoutTicks = 100)
    public static void bridgeMergesThenSplitsNetworks(GameTestHelper helper) {
        night(helper);
        cables(helper, 0, 1, 1, 0);
        cables(helper, 3, 4, 1, 0);
        BlockPos bridge = new BlockPos(2, 1, 0);
        helper.runAfterDelay(5, () -> {
            var left = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 0))).orElseThrow();
            var right = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0))).orElseThrow();
            check(helper, left != right, "Separated lines share a network");
            int before = networks(helper).networks().size();
            cables(helper, 2, 2, 1, 0);
            helper.runAfterDelay(5, () -> {
                var merged = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 0))).orElseThrow();
                check(helper, merged.cables().size() == 5
                        && merged == networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0))).orElseThrow(), "Bridge did not merge");
                check(helper, networks(helper).networks().size() == before - 1, "Merged networks left a duplicate");
                helper.setBlock(bridge, Blocks.AIR);
                helper.runAfterDelay(5, () -> {
                    var l = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(0, 1, 0))).orElseThrow();
                    var r = networks(helper).networkOfCable(helper.absolutePos(new BlockPos(4, 1, 0))).orElseThrow();
                    check(helper, l != r && l.cables().size() == 2 && r.cables().size() == 2, "Broken bridge did not split");
                    check(helper, networks(helper).networkOfCable(helper.absolutePos(bridge)).isEmpty(), "Removed cable still mapped");
                    check(helper, !networks(helper).networks().contains(merged), "The merged network is still ticking");
                    helper.succeed();
                });
            });
        });
    }
}

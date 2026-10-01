package fr.lkdm.homelink.energy.verification;

import static fr.lkdm.homelink.energy.verification.EnergyValidation.check;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homelink.energy.block.HydroMachineBlock;
import fr.lkdm.homelink.energy.block.HydroPipeBlock;
import fr.lkdm.homelink.energy.block.HydroPumpBlock;
import fr.lkdm.homelink.energy.block.HydroTurbineBlock;
import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.homelink.HydroPumpDevice;
import fr.lkdm.homelink.energy.homelink.HydroTurbineDevice;
import fr.lkdm.homelink.energy.hydro.HydroLayout;
import fr.lkdm.homelink.energy.hydro.HydroParameters;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import fr.lkdm.homelink.energy.hydro.WaterWindow;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hydro in a real server level: multiblocks, real water, pipes, HE output, HomeCore devices and failure paths. */
@GameTestHolder(EnergyValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HydroGameTests {
    private static BlockPos place(GameTestHelper h, HydroMachineBlock block, BlockPos absolute, Direction facing) {
        var level = h.getLevel();
        BlockState state = block.defaultBlockState().setValue(HydroMachineBlock.FACING, facing);
        level.setBlock(absolute, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, absolute, state, null, ItemStack.EMPTY);
        return absolute;
    }

    private static void pipe(GameTestHelper h, BlockPos absolute) {
        var level = h.getLevel();
        BlockState state = EnergyRegistries.HYDRO_PIPE.get().defaultBlockState();
        level.setBlock(absolute, Block.updateFromNeighbourShapes(state, level, absolute), Block.UPDATE_ALL);
    }

    /** Water sources filling the pump window, enclosed by stone so nothing floods; returns the sources placed. */
    private static Set<BlockPos> basin(GameTestHelper h, BlockPos pump, HydroPumpBlock block, Direction facing) {
        var p = HydroParameters.DEFAULT;
        var tier = block.tier();
        var window = WaterWindow.of(pump, facing, tier.width(), p.windowWidth(tier), p.windowDistance(tier), p.windowDepth(tier));
        var level = h.getLevel();
        Set<BlockPos> footprint = new HashSet<>(block.positions(pump, block.defaultBlockState().setValue(HydroMachineBlock.FACING, facing)));
        for (BlockPos pos : BlockPos.betweenClosed(window.min().offset(-1, -1, -1), window.max().offset(1, 0, 1))) {
            if (!window.contains(pos) && !footprint.contains(pos) && level.getBlockState(pos).isAir()) level.setBlock(pos.immutable(), Blocks.STONE.defaultBlockState(), 2);
        }
        Set<BlockPos> sources = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(window.min(), window.max())) {
            level.setBlock(pos.immutable(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            sources.add(pos.immutable());
        }
        return sources;
    }

    private static long sources(GameTestHelper h, Set<BlockPos> cells) {
        return cells.stream().filter(pos -> h.getLevel().getBlockState(pos).is(Blocks.WATER) && h.getLevel().getFluidState(pos).isSource()).count();
    }

    /** Pump III facing north at (5,4,10), 16 pipes, turbine facing north at (14,4,14), consumer on the HE output face. */
    private record Rig(BlockPos pump, BlockPos turbine, BlockPos consumer, Set<BlockPos> water) {
        HydroPumpBlockEntity pumpEntity(GameTestHelper h) { return (HydroPumpBlockEntity) h.getLevel().getBlockEntity(pump); }
        HydroTurbineBlockEntity turbineEntity(GameTestHelper h) { return (HydroTurbineBlockEntity) h.getLevel().getBlockEntity(turbine); }
        TestConsumer consumerEntity(GameTestHelper h) { return (TestConsumer) h.getLevel().getBlockEntity(consumer); }
    }

    private static Rig rig(GameTestHelper h, HydroPumpBlock pumpBlock) {
        BlockPos pump = place(h, pumpBlock, h.absolutePos(new BlockPos(5, 4, 10)), Direction.NORTH);
        Set<BlockPos> water = basin(h, pump, pumpBlock, Direction.NORTH);
        BlockPos turbine = place(h, EnergyRegistries.HYDRO_TURBINE_BLOCK.get(), h.absolutePos(new BlockPos(14, 4, 14)), Direction.NORTH);
        pipe(h, pump.above());
        for (int x = 5; x <= 14; x++) pipe(h, h.absolutePos(new BlockPos(x, 6, 10)));
        for (int z = 11; z <= 15; z++) pipe(h, h.absolutePos(new BlockPos(14, 6, z)));
        BlockPos output = HydroLayout.turbineOutput(turbine, Direction.NORTH).relative(HydroLayout.turbineOutputFace(Direction.NORTH));
        h.getLevel().setBlockAndUpdate(output, EnergyValidation.CONSUMER.get().defaultBlockState());
        return new Rig(pump, turbine, output, water);
    }

    @GameTest(template = "empty", batch = "hydro_footprint", timeoutTicks = 100)
    public static void footprintsOrientationsChunksAndParts(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(4, 3, 4));
        // Straddle a chunk corner so that every multiblock spans several chunks.
        base = base.offset(Math.floorMod(15 - base.getX(), 16), 0, Math.floorMod(15 - base.getZ(), 16));
        List<HydroMachineBlock> blocks = List.of(EnergyRegistries.HYDRO_PUMP_1.get(), EnergyRegistries.HYDRO_PUMP_2.get(),
                EnergyRegistries.HYDRO_PUMP_3.get(), EnergyRegistries.HYDRO_TURBINE_BLOCK.get());
        int[] sizes = {1, 2, 4, 8};
        for (int b = 0; b < blocks.size(); b++) {
            var block = blocks.get(b);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                place(h, block, base, facing);
                BlockState state = level.getBlockState(base);
                var cells = block.positions(base, state);
                check(h, cells.size() == sizes[b] && block.complete(level, base, state), "Footprint " + block + " / " + facing);
                var master = level.getBlockEntity(base);
                int entities = 0;
                for (BlockPos cell : cells) {
                    if (level.getBlockEntity(cell) != null) entities++;
                    check(h, HydroMachineBlock.controllerEntity(level, cell, level.getBlockState(cell)) == master, "Part resolves elsewhere " + facing);
                }
                check(h, entities == 1, "One master only");
                if (block instanceof HydroTurbineBlock) {
                    var port = ((HydroTurbineBlockEntity) master).port(level.getBlockState(HydroLayout.turbineOutput(base, facing)), HydroLayout.turbineOutputFace(facing));
                    int ports = 0;
                    for (BlockPos cell : cells) for (Direction side : Direction.values()) {
                        var cap = level.getCapability(HeCapabilities.PORT, cell, side);
                        if (cap != null) { ports++; check(h, cap == port, "Second HE port"); }
                    }
                    check(h, ports == 1 && port != null, "Exactly one HE output face, found " + ports);
                    check(h, HydroTurbineBlock.isInlet(level.getBlockState(HydroLayout.turbineInlet(base, facing))), "Inlet cell");
                }
                level.removeBlock(cells.get(cells.size() - 1), false);
                for (BlockPos cell : cells) check(h, level.getBlockState(cell).isAir(), "Orphan part after breaking " + block);
            }
        }
        // Breaking each of the eight turbine parts removes the whole machine and drops exactly one item.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(8)).forEach(ItemEntity::discard);
        var turbine = EnergyRegistries.HYDRO_TURBINE_BLOCK.get();
        for (int k = 0; k < 8; k++) {
            place(h, turbine, base, Direction.EAST);
            BlockPos part = turbine.positions(base, level.getBlockState(base)).get(k);
            level.destroyBlock(part, true);
            var box = new AABB(base).inflate(4);
            var drops = level.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(EnergyRegistries.HYDRO_TURBINE_ITEM.get()));
            check(h, drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "Part " + k + " dropped " + drops.size() + " items");
            drops.forEach(ItemEntity::discard);
            for (BlockPos cell : turbine.positions(base, turbine.defaultBlockState().setValue(HydroMachineBlock.FACING, Direction.EAST)))
                check(h, !level.getBlockState(cell).is(turbine), "Half turbine left after part " + k);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "hydro_place", timeoutTicks = 40)
    public static void placementChecksEveryCell(GameTestHelper h) {
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "hydro_placement"));
        player.setYRot(0);
        var block = EnergyRegistries.HYDRO_TURBINE_BLOCK.get();
        BlockPos origin = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        var stack = new ItemStack(block);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(origin.below()).add(0, 0.5, 0), Direction.UP, origin.below(), false));
        BlockState state = block.getStateForPlacement(context);
        check(h, state != null && state.getValue(HydroMachineBlock.FACING) == Direction.NORTH, "Clear 2x2x2 placement rejected or not facing the player");
        BlockPos upper = HydroLayout.cell(origin, Direction.NORTH, 1, 1, 1);
        level.setBlockAndUpdate(upper, Blocks.DIAMOND_BLOCK.defaultBlockState());
        check(h, block.getStateForPlacement(context) == null, "Occupied upper cell accepted");
        check(h, !((net.minecraft.world.item.BlockItem) stack.getItem()).place(context).consumesAction() && stack.getCount() == 1, "Item consumed on failure");
        check(h, level.getBlockState(upper).is(Blocks.DIAMOND_BLOCK) && level.getBlockState(origin).isAir(), "Neighbour overwritten or half structure");
        // Clicking replaceable air places the master in that very cell: the top layer would exceed the build height.
        BlockPos top = new BlockPos(origin.getX(), level.getMaxBuildHeight() - 1, origin.getZ());
        var high = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false));
        check(h, block.getStateForPlacement(high) == null, "Turbine accepted above the build height");
        var pump = EnergyRegistries.HYDRO_PUMP_1.get();
        check(h, pump.getStateForPlacement(context).getValue(HydroMachineBlock.FACING) == Direction.SOUTH, "Pump intake must face where the player looks");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "hydro_flow", timeoutTicks = 400)
    public static void realWaterPipesTurbineAndHomeCore(GameTestHelper h) {
        Rig rig = rig(h, EnergyRegistries.HYDRO_PUMP_3.get());
        h.runAfterDelay(150, () -> {
            var pump = rig.pumpEntity(h);
            var turbine = rig.turbineEntity(h);
            check(h, pump.water().sources() == 147 && pump.water().availability() == 1.0, "Water window " + pump.water());
            check(h, turbine.status() == HydroStatus.Turbine.GENERATING, "Turbine status " + turbine.status());
            check(h, pump.status() == HydroStatus.Pump.PUMPING, "Pump status " + pump.status());
            check(h, Math.abs(turbine.usedFlow() - 12) < 1e-9 && Math.abs(pump.allocatedFlow() - 12) < 1e-9, "Flow " + turbine.usedFlow());
            check(h, Math.abs(turbine.core().potentialRate() - 2.0) < 1e-9, "2 HE/t for a full Pump III");
            check(h, rig.consumerEntity(h).received > 0, "No HE delivered through the output face");
            check(h, turbine.core().generated() - turbine.core().exported() == turbine.core().buffer().stored(), "Buffer accounting");
            check(h, sources(h, rig.water()) == rig.water().size(), "Water was consumed");
            for (BlockPos cell : HydroLayout.turbineDischarge(rig.turbine(), Direction.NORTH))
                check(h, h.getLevel().getBlockState(cell).isAir(), "Discharge placed a block at " + cell);
            var devices = DashboardAPI.devices(h.getLevel().getServer());
            var pumpDevice = devices.get(pump.deviceId()).orElse(null);
            var turbineDevice = devices.get(turbine.deviceId()).orElse(null);
            check(h, pumpDevice != null && pumpDevice.deviceType().equals(HydroPumpDevice.TYPE), "Pump device");
            check(h, turbineDevice != null && turbineDevice.deviceType().equals(HydroTurbineDevice.TYPE), "Turbine device");
            check(h, turbineDevice instanceof Switchable && pumpDevice instanceof Switchable, "Switchable");
            ((Switchable) pumpDevice).setPowered(false);
            long generated = turbine.core().generated();
            h.runAfterDelay(60, () -> {
                check(h, pump.status() == HydroStatus.Pump.DISABLED, "Switched-off pump " + pump.status());
                check(h, turbine.status() == HydroStatus.Turbine.NO_FLOW, "Turbine without flow " + turbine.status());
                check(h, turbine.core().generated() - generated <= 2, "Generation continued while the only pump was off");
                check(h, pumpDevice.status().state() == fr.lkdm.homecore.api.device.DeviceStatus.State.DISABLED, "Disabled, not offline");
                h.succeed();
            });
        });
    }

    /** Regression: the cable network must reach the single output face, which is not on the master cell. */
    @GameTest(template = "empty", batch = "hydro_cable", timeoutTicks = 300)
    public static void turbineFeedsCopperCables(GameTestHelper h) {
        Rig rig = rig(h, EnergyRegistries.HYDRO_PUMP_3.get());
        var level = h.getLevel();
        Direction east = HydroLayout.turbineOutputFace(Direction.NORTH);
        level.removeBlock(rig.consumer(), false);
        BlockPos cable = rig.consumer();
        for (int i = 0; i < 4; i++, cable = cable.relative(east)) {
            level.setBlockAndUpdate(cable.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cable, EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState());
        }
        level.setBlockAndUpdate(cable, EnergyValidation.CONSUMER.get().defaultBlockState());
        BlockPos consumerPos = cable;
        h.runAfterDelay(150, () -> {
            var turbine = rig.turbineEntity(h);
            var consumer = (TestConsumer) level.getBlockEntity(consumerPos);
            check(h, turbine.status() == HydroStatus.Turbine.GENERATING, "Turbine " + turbine.status());
            check(h, !turbine.directOutput(), "The test must not rely on a block touching the output");
            check(h, turbine.networkConnection() == fr.lkdm.homelink.energy.network.EnergyNetworks.Connection.CONNECTED, "Cable network not connected");
            check(h, consumer.received > 50, "HE did not travel through the cables: " + consumer.received);
            check(h, turbine.core().exported() == consumer.received, "Exported " + turbine.core().exported() + " != received " + consumer.received);
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hydro_outlet", timeoutTicks = 400)
    public static void blockedOutletStopsAndResumes(GameTestHelper h) {
        Rig rig = rig(h, EnergyRegistries.HYDRO_PUMP_1.get());
        BlockPos blocker = HydroLayout.turbineDischarge(rig.turbine(), Direction.NORTH).get(1);
        h.runAfterDelay(120, () -> {
            var turbine = rig.turbineEntity(h);
            check(h, turbine.status() == HydroStatus.Turbine.GENERATING, "Not generating before the block " + turbine.status());
            check(h, Math.abs(turbine.usedFlow() - 1.0) < 1e-9, "Pump I gives 1 DH/t, got " + turbine.usedFlow());
            h.getLevel().setBlockAndUpdate(blocker, Blocks.STONE.defaultBlockState());
            h.runAfterDelay(5, () -> {
                long generated = turbine.core().generated();
                check(h, turbine.status() == HydroStatus.Turbine.OUTLET_BLOCKED && blocker.equals(turbine.obstruction()), "Outlet " + turbine.status());
                h.runAfterDelay(40, () -> {
                    check(h, turbine.core().generated() == generated, "Generation while blocked");
                    h.getLevel().removeBlock(blocker, false);
                    h.runAfterDelay(10, () -> {
                        check(h, turbine.status() == HydroStatus.Turbine.GENERATING, "No automatic restart " + turbine.status());
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "hydro_twin", timeoutTicks = 300)
    public static void twoTurbinesOnOneCircuitBothStop(GameTestHelper h) {
        Rig rig = rig(h, EnergyRegistries.HYDRO_PUMP_2.get());
        BlockPos second = place(h, EnergyRegistries.HYDRO_TURBINE_BLOCK.get(), h.absolutePos(new BlockPos(9, 4, 14)), Direction.NORTH);
        for (int z = 11; z <= 15; z++) pipe(h, h.absolutePos(new BlockPos(9, 6, z)));
        h.runAfterDelay(100, () -> {
            var first = rig.turbineEntity(h);
            var other = (HydroTurbineBlockEntity) h.getLevel().getBlockEntity(second);
            check(h, first.status() == HydroStatus.Turbine.MULTIPLE_TURBINES && other.status() == HydroStatus.Turbine.MULTIPLE_TURBINES,
                    "Both turbines must stop: " + first.status() + " / " + other.status());
            check(h, first.core().generated() + other.core().generated() == 0, "A shared flow was converted twice");
            check(h, rig.pumpEntity(h).status() == HydroStatus.Pump.NETWORK_INVALID, "Pump " + rig.pumpEntity(h).status());
            // Cutting the branch to the second turbine restores the first circuit.
            h.getLevel().removeBlock(h.absolutePos(new BlockPos(9, 6, 11)), false);
            h.runAfterDelay(40, () -> {
                check(h, first.status() == HydroStatus.Turbine.GENERATING, "Circuit not restored " + first.status());
                check(h, other.status() == HydroStatus.Turbine.NO_PUMP, "Isolated turbine " + other.status());
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "hydro_pipe", timeoutTicks = 40)
    public static void pipesJoinOnlyHydroPorts(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pump = place(h, EnergyRegistries.HYDRO_PUMP_2.get(), h.absolutePos(new BlockPos(2, 2, 2)), Direction.NORTH);
        BlockPos part = HydroLayout.cell(pump, Direction.NORTH, 1, 0, 0);
        check(h, HydroPipeBlock.connectsTo(level.getBlockState(pump), Direction.DOWN), "Pump outlet must accept a pipe above");
        check(h, !HydroPipeBlock.connectsTo(level.getBlockState(part), Direction.DOWN), "Second pump cell is not a port");
        check(h, !HydroPipeBlock.connectsTo(level.getBlockState(pump), Direction.EAST), "Pump side is not a port");
        BlockPos cablePos = h.absolutePos(new BlockPos(2, 2, 6));
        level.setBlockAndUpdate(cablePos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(cablePos, EnergyRegistries.COPPER_ENERGY_CABLE.get().defaultBlockState());
        pipe(h, cablePos.east());
        check(h, !level.getBlockState(cablePos.east()).getValue(HydroPipeBlock.SIDES.get(Direction.WEST)), "Pipe joined a copper cable");
        check(h, !HydroPipeBlock.connectsTo(Blocks.CHEST.defaultBlockState(), Direction.DOWN) && !HydroPipeBlock.connectsTo(Blocks.WATER.defaultBlockState(), Direction.NORTH), "Foreign blocks");
        pipe(h, cablePos.east().east());
        check(h, level.getBlockState(cablePos.east()).getValue(HydroPipeBlock.SIDES.get(Direction.EAST)), "Two pipes must join");
        check(h, level.getCapability(HeCapabilities.PORT, cablePos.east(), Direction.WEST) == null, "A Hydro pipe is not an HE port");
        BlockPos turbine = place(h, EnergyRegistries.HYDRO_TURBINE_BLOCK.get(), h.absolutePos(new BlockPos(8, 2, 2)), Direction.NORTH);
        check(h, HydroPipeBlock.connectsTo(level.getBlockState(HydroLayout.turbineInlet(turbine, Direction.NORTH)), Direction.DOWN), "Turbine inlet");
        check(h, !HydroPipeBlock.connectsTo(level.getBlockState(HydroLayout.cell(turbine, Direction.NORTH, 1, 1, 1)), Direction.DOWN), "Other top cell");
        check(h, !HydroPipeBlock.connectsTo(level.getBlockState(HydroLayout.cell(turbine, Direction.NORTH, 0, 1, 1)), Direction.NORTH), "Rear grille is not an inlet");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "hydro_save", timeoutTicks = 300)
    public static void persistenceKeepsAccountingWithoutCatchUp(GameTestHelper h) {
        Rig rig = rig(h, EnergyRegistries.HYDRO_PUMP_3.get());
        h.runAfterDelay(120, () -> {
            var turbine = rig.turbineEntity(h);
            var registries = h.getLevel().registryAccess();
            var copy = new HydroTurbineBlockEntity(turbine.getBlockPos(), turbine.getBlockState());
            copy.loadWithComponents(turbine.saveWithoutMetadata(registries), registries);
            check(h, copy.deviceId().equals(turbine.deviceId()), "UUID");
            check(h, copy.core().generated() == turbine.core().generated() && copy.core().exported() == turbine.core().exported()
                    && copy.core().lost() == turbine.core().lost() && copy.core().buffer().stored() == turbine.core().buffer().stored(), "Counters");
            check(h, copy.core().fraction().fraction() == turbine.core().fraction().fraction(), "Fraction");
            check(h, copy.core().generatedPeriod() == turbine.core().generatedPeriod() && copy.core().period() == turbine.core().period(), "Period");
            check(h, copy.core().generated() <= 242, "More than the executed ticks: " + copy.core().generated());
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "hydro_explosion", timeoutTicks = 60)
    public static void explosionNeverLeavesHalfATurbineOrDuplicates(GameTestHelper h) {
        var level = h.getLevel();
        var turbine = EnergyRegistries.HYDRO_TURBINE_BLOCK.get();
        BlockPos origin = place(h, turbine, h.absolutePos(new BlockPos(4, 2, 4)), Direction.SOUTH);
        var cells = turbine.positions(origin, level.getBlockState(origin));
        Vec3 center = Vec3.atCenterOf(cells.get(cells.size() - 1));
        level.explode(null, center.x, center.y, center.z, 5f, Level.ExplosionInteraction.BLOCK);
        h.runAfterDelay(5, () -> {
            long present = cells.stream().filter(cell -> level.getBlockState(cell).is(turbine)).count();
            check(h, present == 0 || present == 8, "Half a turbine after an explosion: " + present);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8), item -> item.getItem().is(EnergyRegistries.HYDRO_TURBINE_ITEM.get()));
            check(h, drops.stream().mapToInt(item -> item.getItem().getCount()).sum() <= 1, "Turbine duplicated by an explosion");
            h.succeed();
        });
    }
}

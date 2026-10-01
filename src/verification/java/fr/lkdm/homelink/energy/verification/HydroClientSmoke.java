package fr.lkdm.homelink.energy.verification;

import fr.lkdm.homelink.energy.block.HydroMachineBlock;
import fr.lkdm.homelink.energy.block.HydroPumpBlock;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.client.HydroPumpScreen;
import fr.lkdm.homelink.energy.client.HydroTurbineScreen;
import fr.lkdm.homelink.energy.hydro.HydroLayout;
import fr.lkdm.homelink.energy.hydro.HydroParameters;
import fr.lkdm.homelink.energy.hydro.HydroStatus;
import fr.lkdm.homelink.energy.hydro.WaterWindow;
import fr.lkdm.homelink.energy.menu.HydroPumpMenu;
import fr.lkdm.homelink.energy.menu.HydroTurbineMenu;
import fr.lkdm.homelink.energy.registry.EnergyRegistries;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Real client check of the Hydro branch, run after the wind scenario: a Pump III in a real basin,
 * thirteen pipes, a turbine facing south and a Battery I on its copper connector. Captures the front
 * (louvers and water sheet), the rear (grille and rotor), both screens, the water zone and the obstruction marker.
 */
final class HydroClientSmoke {
    private static int stage, age;
    private static BlockPos origin, turbine;
    static volatile Throwable failure;

    private HydroClientSmoke() { }

    static void begin(Minecraft mc, BlockPos base) {
        origin = base;
        turbine = base.offset(6, 0, 6);
        stage = 1;
        age = 0;
        server(mc, (level, player) -> {
            build(level, player);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, turbine.getX() + 0.0, turbine.getY() + 1.6, turbine.getZ() + 6.5, 180, 12);
        });
    }

    /** @return true once every Hydro check passed */
    static boolean tick(Minecraft mc) {
        if (failure != null) throw new IllegalStateException("Hydro fixture failed", failure);
        if (mc.player == null || mc.level == null) return false;
        mc.options.hideGui = stage < 5;
        switch (stage) {
            case 1 -> {
                if (++age < 160) return false;
                if (!(mc.level.getBlockEntity(turbine) instanceof HydroTurbineBlockEntity entity) || entity.clientFlow() < 0.99f
                        || entity.clientStatus() != HydroStatus.Turbine.GENERATING) throw new IllegalStateException("Turbine not generating on the client");
                ClientSmoke.capture(mc, "hydro-front");
                // Rear three-quarter view, beside the pipe run, to see the grille bars and the rotor behind them.
                next(mc, 2, (level, player) -> player.teleportTo(level, turbine.getX() + 3.5, turbine.getY() + 2.6, turbine.getZ() - 5.5, 38, 15));
            }
            case 2 -> {
                if (++age < 40) return false;
                ClientSmoke.capture(mc, "hydro-rear");
                next(mc, 3, (level, player) -> player.teleportTo(level, origin.getX() + 4.5, origin.getY() + 6, origin.getZ() + 12, 160, 32));
            }
            case 3 -> {
                if (++age < 40) return false;
                ClientSmoke.capture(mc, "hydro-overview");
                next(mc, 4, (level, player) -> player.teleportTo(level, origin.getX() + 14, origin.getY() + 2.5, origin.getZ() + 5, 180, 25));
            }
            case 4 -> {
                if (++age < 40) return false;
                ClientSmoke.capture(mc, "hydro-pumps");
                next(mc, 5, (level, player) -> open(level, player, HydroLayout.cell(turbine, Direction.SOUTH, 1, 1, 1)));
            }
            case 5 -> {
                if (!(mc.screen instanceof HydroTurbineScreen) || !(mc.player.containerMenu instanceof HydroTurbineMenu menu) || ++age < 30) return false;
                if (menu.value(HydroTurbineMenu.USED_FLOW) != 12_000 || menu.value(HydroTurbineMenu.POTENTIAL) != 20_000 || menu.value(HydroTurbineMenu.PUMPS) != 1
                        || !menu.pos().equals(turbine)) throw new IllegalStateException("Turbine menu mismatch");
                ClientSmoke.capture(mc, "hydro-turbine-gui");
                // The server closes the turbine menu when it opens the pump menu; a client close packet could arrive after it.
                next(mc, 6, (level, player) -> open(level, player, origin));
            }
            case 6 -> {
                if (!(mc.screen instanceof HydroPumpScreen) || !(mc.player.containerMenu instanceof HydroPumpMenu menu) || ++age < 30) return false;
                if (menu.value(HydroPumpMenu.SOURCES) != 147 || menu.value(HydroPumpMenu.ALLOCATED_FLOW) != 12_000) throw new IllegalStateException("Pump menu mismatch");
                ClientSmoke.capture(mc, "hydro-pump-gui");
                ClientSmoke.click(mc, "gui.homelink_energy.hydro_show_water");
                stage = 7; age = 0;
            }
            case 7 -> {
                if (mc.screen != null) return false;
                mc.player.setYRot(180); mc.player.setXRot(45);
                if (++age < 20) return false;
                ClientSmoke.capture(mc, "hydro-water-zone");
                next(mc, 8, (level, player) -> {
                    level.setBlockAndUpdate(turbine.south(), Blocks.STONE.defaultBlockState());
                    player.teleportTo(level, turbine.getX() + 0.0, turbine.getY() + 1.6, turbine.getZ() + 5.5, 180, 25);
                    open(level, player, turbine);
                });
            }
            case 8 -> {
                if (!(mc.screen instanceof HydroTurbineScreen) || !(mc.player.containerMenu instanceof HydroTurbineMenu menu) || ++age < 40) return false;
                if (menu.value(HydroTurbineMenu.OUTLET) != 0 || menu.value(HydroTurbineMenu.OBSTRUCTION) != 1 || menu.value(HydroTurbineMenu.USED_FLOW) != 0)
                    throw new IllegalStateException("Blocked outlet not reported");
                ClientSmoke.capture(mc, "hydro-blocked-gui");
                ClientSmoke.click(mc, "gui.homelink_energy.locate_obstruction");
                stage = 9; age = 0;
            }
            case 9 -> {
                if (mc.screen != null) return false;
                mc.player.setYRot(180); mc.player.setXRot(25);
                if (++age < 30) return false;
                ClientSmoke.capture(mc, "hydro-obstruction");
                return true;
            }
            default -> { }
        }
        return false;
    }

    private static void next(Minecraft mc, int nextStage, ServerAction action) {
        stage = nextStage;
        age = 0;
        server(mc, action);
    }

    private interface ServerAction { void run(ServerLevel level, ServerPlayer player); }

    private static void server(Minecraft mc, ServerAction action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(id);
                action.run(player.serverLevel(), player);
            } catch (Throwable t) { failure = t; }
        });
    }

    /** Menus close beyond 8 blocks from the master: stand three blocks south of the clicked cell first. */
    private static void open(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        player.teleportTo(level, clicked.getX() + 0.5, clicked.getY() + 1, clicked.getZ() + 3.5, 180, 25);
        level.getBlockState(clicked).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
    }

    private static void place(ServerLevel level, HydroMachineBlock block, BlockPos pos, Direction facing, ServerPlayer player) {
        var state = block.defaultBlockState().setValue(HydroMachineBlock.FACING, facing);
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, ItemStack.EMPTY);
    }

    private static void pipe(ServerLevel level, BlockPos pos) {
        var state = EnergyRegistries.HYDRO_PIPE.get().defaultBlockState();
        level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_ALL);
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var stone = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = -4; x <= 18; x++) for (int z = -9; z <= 9; z++)
            for (int y = 0; y <= 6; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        HydroPumpBlock pump = EnergyRegistries.HYDRO_PUMP_3.get();
        place(level, pump, origin, Direction.NORTH, player);
        var p = HydroParameters.DEFAULT;
        var window = WaterWindow.of(origin, Direction.NORTH, 2, p.windowWidth(pump.tier()), p.windowDistance(pump.tier()), p.windowDepth(pump.tier()));
        var footprint = new HashSet<>(pump.positions(origin, level.getBlockState(origin)));
        for (BlockPos pos : BlockPos.betweenClosed(window.min().offset(-1, -1, -1), window.max().offset(1, 0, 1)))
            if (!window.contains(pos) && !footprint.contains(pos)) level.setBlockAndUpdate(pos.immutable(), stone);
        for (BlockPos pos : BlockPos.betweenClosed(window.min(), window.max())) level.setBlockAndUpdate(pos.immutable(), Blocks.WATER.defaultBlockState());
        place(level, EnergyRegistries.HYDRO_TURBINE_BLOCK.get(), turbine, Direction.SOUTH, player);
        BlockPos output = HydroLayout.turbineOutput(turbine, Direction.SOUTH).relative(HydroLayout.turbineOutputFace(Direction.SOUTH));
        level.setBlockAndUpdate(output, EnergyRegistries.BATTERY_1.get().defaultBlockState());
        pipe(level, origin.above());
        pipe(level, origin.above(2));
        for (int x = 1; x <= 6; x++) pipe(level, origin.offset(x, 2, 0));
        for (int z = 1; z <= 5; z++) pipe(level, origin.offset(6, 2, z));
        place(level, EnergyRegistries.HYDRO_PUMP_1.get(), origin.offset(11, 0, 0), Direction.NORTH, player);
        place(level, EnergyRegistries.HYDRO_PUMP_2.get(), origin.offset(13, 0, 0), Direction.NORTH, player);
        place(level, EnergyRegistries.HYDRO_PUMP_3.get(), origin.offset(16, 0, 0), Direction.NORTH, player);
        // Couplings on each master, an elbow, and a pipe beside a non-port cell that stays unconnected.
        for (int x : new int[]{11, 13, 16}) pipe(level, origin.offset(x, 1, 0));
        pipe(level, origin.offset(11, 2, 0));
        pipe(level, origin.offset(16, 2, 0));
        pipe(level, origin.offset(16, 2, -1));
        pipe(level, origin.offset(17, 1, 0));
    }

}

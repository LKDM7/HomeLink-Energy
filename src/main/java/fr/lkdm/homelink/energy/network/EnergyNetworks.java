package fr.lkdm.homelink.energy.network;

import fr.lkdm.homelink.energy.energy.HeCapabilities;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.config.EnergyConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/**
 * Energy networks of one server level. Loaded cables register themselves; the graph is cached
 * and only rebuilt after a change (cable placed, broken, loaded or unloaded, neighbour changed,
 * port invalidated). Cables in unloaded chunks are not registered, so energy never crosses
 * an unloaded chunk, and nothing here loads a chunk.
 */
public final class EnergyNetworks {
    private static final Map<ServerLevel, EnergyNetworks> LEVELS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Set<BlockPos> cables = new HashSet<>();
    private final Map<BlockPos, EnergyNetwork> byCable = new HashMap<>();
    private final Map<BlockPos, List<EnergyNetwork>> byMachine = new HashMap<>();
    private List<EnergyNetwork> networks = List.of();
    private boolean dirty;
    private BlockPos lastChange;
    private int nextId = 1;
    private int rebuilds;

    private EnergyNetworks(ServerLevel level) {
        this.level = level;
    }

    /** @param level server level
     *  @return its networks */
    public static EnergyNetworks get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, EnergyNetworks::new);
    }

    /** @param level server level
     *  @return its networks, if any cable was ever loaded there */
    public static Optional<EnergyNetworks> existing(ServerLevel level) {
        return Optional.ofNullable(LEVELS.get(level));
    }

    /** Forgets a level that is being unloaded. */
    public static void unload(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** A cable was placed or its chunk loaded. */
    public void addCable(BlockPos pos) {
        if (cables.add(pos.immutable())) markDirty(pos);
    }

    /** A cable was broken or its chunk unloaded. */
    public void removeCable(BlockPos pos) {
        if (cables.remove(pos)) markDirty(pos);
    }

    /** Something next to the networks changed. */
    public void markDirty(BlockPos pos) {
        dirty = true;
        lastChange = pos.immutable();
    }

    /** Runs one server tick: rebuild if needed, then distribute. */
    public void tick() {
        if (dirty) rebuild();
        long gameTime = level.getGameTime();
        for (EnergyNetwork network : networks) network.tick(gameTime);
    }

    private void rebuild() {
        dirty = false;
        rebuilds++;
        int limit = EnergyConfig.MAX_ENERGY_NETWORK_NODES.get();
        List<EnergyNetwork> built = new ArrayList<>();
        byCable.clear();
        byMachine.clear();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> ordered = new ArrayList<>(cables);
        ordered.sort(Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos start : ordered) {
            if (!visited.add(start)) continue;
            Set<BlockPos> component = new HashSet<>();
            Map<BlockPos, Direction> machines = new LinkedHashMap<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos cable = queue.poll();
                component.add(cable);
                for (BlockPos adjacent : fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.neighbors(level, cable)) {
                    if (cables.contains(adjacent) && visited.add(adjacent)) queue.add(adjacent);
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = cable.relative(direction);
                    if (!cables.contains(next) && !machines.containsKey(next) && level.isLoaded(next)
                            && fr.lkdm.homelink.energy.block.CopperEnergyCableBlock.connects(level, cable, direction)
                            && level.getCapability(HeCapabilities.PORT, next, direction.getOpposite()) != null) {
                        var state = level.getBlockState(next);
                        BlockPos machine = state.getBlock() instanceof fr.lkdm.homelink.energy.block.SolarPanelBlock
                                ? fr.lkdm.homelink.energy.block.SolarPanelBlock.controllerPos(next, state)
                                : state.getBlock() instanceof fr.lkdm.homelink.energy.block.WindTurbineBlock
                                ? fr.lkdm.homelink.energy.block.WindTurbineBlock.controllerPos(next, state)
                                : state.getBlock() instanceof fr.lkdm.homelink.energy.block.BatteryBlock
                                ? fr.lkdm.homelink.energy.block.BatteryBlock.controllerPos(next, state) : next;
                        machines.putIfAbsent(machine.immutable(), direction.getOpposite());
                    }
                }
            }
            boolean tooLarge = component.size() + machines.size() > limit;
            List<EnergyNetwork.Endpoint> endpoints = new ArrayList<>();
            if (!tooLarge) {
                List<BlockPos> machinePositions = new ArrayList<>(machines.keySet());
                machinePositions.sort(Comparator.comparingLong(BlockPos::asLong));
                for (BlockPos machine : machinePositions) {
                    var cache = BlockCapabilityCache.create(HeCapabilities.PORT, level, machine, machines.get(machine),
                            () -> true, () -> markDirty(machine));
                    endpoints.add(new EnergyNetwork.Endpoint(machine, cache::getCapability));
                }
            }
            EnergyNetwork network = new EnergyNetwork(nextId++, component, endpoints, tooLarge);
            built.add(network);
            for (BlockPos cable : component) byCable.put(cable, network);
            for (EnergyNetwork.Endpoint endpoint : endpoints) byMachine.computeIfAbsent(endpoint.pos(), key -> new ArrayList<>(1)).add(network);
            if (tooLarge) warnTooLarge(network, limit);
        }
        networks = Collections.unmodifiableList(built);
    }

    private void warnTooLarge(EnergyNetwork network, int limit) {
        HomeLinkEnergy.LOGGER.warn("Energy network {} in {} has {}+ nodes, over the limit of {}: NETWORK_TOO_LARGE, it will not move energy",
                network.id(), level.dimension().location(), network.nodeCount(), limit);
        if (lastChange == null || network.cables().stream().noneMatch(cable -> cable.distManhattan(lastChange) <= 1)) return;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(lastChange) <= 32 * 32) {
                player.displayClientMessage(Component.translatable("message.homelink_energy.network_too_large", limit), true);
            }
        }
    }

    /** @return current networks */
    public List<EnergyNetwork> networks() { return networks; }

    /** @param pos cable position
     *  @return its network */
    public Optional<EnergyNetwork> networkOfCable(BlockPos pos) { return Optional.ofNullable(byCable.get(pos)); }

    /** @param pos machine position
     *  @return networks the machine is connected to */
    public List<EnergyNetwork> networksOfMachine(BlockPos pos) { return byMachine.getOrDefault(pos, List.of()); }

    /** @return number of graph rebuilds since the level loaded, for diagnostics and tests */
    public int rebuilds() { return rebuilds; }

    /** @return whether a rebuild is pending */
    public boolean isDirty() { return dirty; }

    /** @return registered loaded cables */
    public int cableCount() { return cables.size(); }

    /** Connection state of a machine, as shown in screens and HomeCore metrics. */
    public enum Connection { NONE, CONNECTED, NETWORK_TOO_LARGE }

    /** @param level server level
     *  @param pos machine position
     *  @return whether the machine takes part in an active network */
    public static Connection connection(ServerLevel level, BlockPos pos) {
        var networks = existing(level).map(n -> n.networksOfMachine(pos)).orElse(List.of());
        if (networks.isEmpty()) {
            var manager = existing(level);
            if (manager.isPresent()) {
                for (Direction direction : Direction.values()) {
                    var network = manager.get().byCable.get(pos.relative(direction));
                    if (network != null && network.tooLarge()) return Connection.NETWORK_TOO_LARGE;
                }
            }
            return Connection.NONE;
        }
        return networks.stream().anyMatch(n -> !n.tooLarge()) ? Connection.CONNECTED : Connection.NETWORK_TOO_LARGE;
    }
}

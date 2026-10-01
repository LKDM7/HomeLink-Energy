package fr.lkdm.homelink.energy.hydro;

import fr.lkdm.homelink.energy.blockentity.HydroPumpBlockEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Event glue of the Hydro branch (game bus). Chunk buckets hold only loaded pumps watching that chunk,
 * so a block change never scans the world; chunk loads and unloads also discard the hydraulic circuits
 * that crossed them. The rebuild budget runs before block entities tick.
 */
public final class HydroWatch {
    private static final Map<ServerLevel, Map<Long, Set<HydroPumpBlockEntity>>> LEVELS = new IdentityHashMap<>();

    private HydroWatch() { }

    public static void add(ServerLevel level, HydroPumpBlockEntity pump, WaterWindow window) {
        var chunks = LEVELS.computeIfAbsent(level, key -> new HashMap<>());
        visit(window, key -> chunks.computeIfAbsent(key, k -> new HashSet<>()).add(pump));
    }

    public static void remove(ServerLevel level, HydroPumpBlockEntity pump, WaterWindow window) {
        var chunks = LEVELS.get(level);
        if (chunks == null) return;
        visit(window, key -> {
            var set = chunks.get(key);
            if (set != null) { set.remove(pump); if (set.isEmpty()) chunks.remove(key); }
        });
        if (chunks.isEmpty()) LEVELS.remove(level);
    }

    private static void visit(WaterWindow window, java.util.function.LongConsumer action) {
        BlockPos min = window.min().offset(-1, 0, -1), max = window.max().offset(1, 0, 1);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) action.accept(ChunkPos.asLong(x, z));
    }

    /** @return pumps watching the chunk of {@code pos}, for diagnostics and tests */
    public static int watchers(ServerLevel level, BlockPos pos) {
        var chunks = LEVELS.get(level);
        var set = chunks == null ? null : chunks.get(ChunkPos.asLong(pos));
        return set == null ? 0 : set.size();
    }

    static void changed(ServerLevel level, BlockPos pos) {
        var chunks = LEVELS.get(level);
        if (chunks == null) return;
        var set = chunks.get(ChunkPos.asLong(pos));
        if (set != null) for (var pump : set) if (pump.window() != null && pump.window().affectedBy(pos)) pump.surroundingsChanged();
    }

    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent e) { if (e.getLevel() instanceof ServerLevel l) changed(l, e.getPos()); }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e) { if (e.getLevel() instanceof ServerLevel l) changed(l, e.getPos()); }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e) { if (e.getLevel() instanceof ServerLevel l) changed(l, e.getPos()); }
    @SubscribeEvent public static void fluid(BlockEvent.FluidPlaceBlockEvent e) { if (e.getLevel() instanceof ServerLevel l) changed(l, e.getPos()); }

    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load e) { chunk(e); }
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload e) { chunk(e); }

    private static void chunk(ChunkEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        long key = e.getChunk().getPos().toLong();
        HydroNetworks.existing(level).ifPresent(networks -> networks.chunkChanged(key));
        var chunks = LEVELS.get(level);
        var set = chunks == null ? null : chunks.get(key);
        if (set != null) for (var pump : set) pump.surroundingsChanged();
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Pre e) {
        if (e.getLevel() instanceof ServerLevel level) HydroNetworks.existing(level).ifPresent(HydroNetworks::tick);
    }

    @SubscribeEvent public static void levelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel level) { LEVELS.remove(level); HydroNetworks.unload(level); }
    }
}

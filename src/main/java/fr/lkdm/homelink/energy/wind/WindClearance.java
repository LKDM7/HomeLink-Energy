package fr.lkdm.homelink.energy.wind;

import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.*;

/** Chunk buckets contain only loaded turbines watching that chunk; changes never scan the world. */
public final class WindClearance {
    private static final Map<ServerLevel, Map<Long,Set<WindTurbineBlockEntity>>> LEVELS=new IdentityHashMap<>();
    private WindClearance() { }
    public static void add(ServerLevel level, WindTurbineBlockEntity turbine) {
        var chunks=LEVELS.computeIfAbsent(level,k->new HashMap<>());
        visit(turbine.area(),key->chunks.computeIfAbsent(key,k->new HashSet<>()).add(turbine));
    }
    public static void remove(ServerLevel level, WindTurbineBlockEntity turbine, RotorArea area) {
        var chunks=LEVELS.get(level); if(chunks==null) return;
        visit(area,key->{ var set=chunks.get(key); if(set!=null) { set.remove(turbine); if(set.isEmpty()) chunks.remove(key); }});
        if(chunks.isEmpty()) LEVELS.remove(level);
    }
    private static void visit(RotorArea area, java.util.function.LongConsumer action) {
        for(int x=area.min().getX()>>4;x<=area.max().getX()>>4;x++)
            for(int z=area.min().getZ()>>4;z<=area.max().getZ()>>4;z++) action.accept(ChunkPos.asLong(x,z));
    }
    public static void changed(ServerLevel level, BlockPos pos) {
        var chunks=LEVELS.get(level); if(chunks==null) return;
        var set=chunks.get(ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4)); if(set==null) return;
        for(var turbine:set) if(turbine.area().affectedBy(pos)) turbine.invalidateClearance();
    }
    @SubscribeEvent public static void neighbors(BlockEvent.NeighborNotifyEvent e) { if(e.getLevel() instanceof ServerLevel l) changed(l,e.getPos()); }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e) { if(e.getLevel() instanceof ServerLevel l) changed(l,e.getPos()); }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e) { if(e.getLevel() instanceof ServerLevel l) changed(l,e.getPos()); }
    @SubscribeEvent public static void loaded(ChunkEvent.Load e) { chunkChanged(e); }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload e) { chunkChanged(e); }
    private static void chunkChanged(ChunkEvent e) {
        if(!(e.getLevel() instanceof ServerLevel l)) return;
        var chunks=LEVELS.get(l); if(chunks==null) return;
        var set=chunks.get(e.getChunk().getPos().toLong()); if(set!=null) for(var turbine:set) turbine.invalidateClearance();
    }
    @SubscribeEvent public static void levelUnload(LevelEvent.Unload e) { if(e.getLevel() instanceof ServerLevel l) LEVELS.remove(l); }
}

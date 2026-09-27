package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** At most two boxes, no per-block effects. Selection expires and never survives a level change. */
@EventBusSubscriber(modid=HomeLinkEnergy.MOD_ID,value=Dist.CLIENT)
public final class WindOverlay {
    private static net.minecraft.client.multiplayer.ClientLevel selectedLevel;
    private static BlockPos base;
    private static AABB area,obstacle;
    private static long until;
    public static void show(WindTurbineBlockEntity turbine,BlockPos pos) {
        var mc=Minecraft.getInstance(); if(mc.level==null) return;
        selectedLevel=mc.level; base=turbine.getBlockPos(); area=turbine.area().bounds();
        obstacle=pos!=null && area.contains(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)?new AABB(pos).inflate(.004):null;
        until=mc.level.getGameTime()+200;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || area==null) return;
        var mc=Minecraft.getInstance();
        if(mc.level!=selectedLevel || mc.level==null || mc.level.getGameTime()>until || !(mc.level.getBlockEntity(base) instanceof WindTurbineBlockEntity)) {
            selectedLevel=null; area=null; obstacle=null; base=null; return;
        }
        var camera=e.getCamera().getPosition();
        if(camera.distanceToSqr(area.getCenter())>96*96) return;
        var pose=e.getPoseStack(); pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource(); var lines=buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose,lines,area,1f,.55f,.22f,.8f);
        if(obstacle!=null) LevelRenderer.renderLineBox(pose,lines,obstacle,1f,.25f,.1f,1f);
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        selectedLevel=null; area=null; obstacle=null; base=null;
    }
}

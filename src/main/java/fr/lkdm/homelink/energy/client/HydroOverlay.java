package fr.lkdm.homelink.energy.client;

import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.config.HydroClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Personal, temporary previews: one outline for a pump's water window, one marker for a turbine's
 * first discharge obstruction. Two boxes at most, no particles; expires after ten seconds or a level change.
 */
@EventBusSubscriber(modid = HomeLinkEnergy.MOD_ID, value = Dist.CLIENT)
public final class HydroOverlay {
    private static ClientLevel selectedLevel;
    private static BlockPos machine;
    private static AABB area, obstacle;
    private static long until;

    private HydroOverlay() { }

    public static void show(BlockPos master, AABB window, BlockPos obstruction) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        selectedLevel = mc.level;
        machine = master.immutable();
        area = window;
        obstacle = obstruction == null ? null : new AABB(obstruction).inflate(0.004);
        until = mc.level.getGameTime() + 200;
    }

    private static void clear() { selectedLevel = null; machine = null; area = null; obstacle = null; }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (area == null && obstacle == null)) return;
        var mc = Minecraft.getInstance();
        if (mc.level != selectedLevel || mc.level == null || mc.level.getGameTime() > until || mc.level.getBlockEntity(machine) == null) { clear(); return; }
        var camera = e.getCamera().getPosition();
        int range = HydroClientConfig.previewRange();
        if (camera.distanceToSqr(machine.getCenter()) > (double) range * range) return;
        var pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        if (area != null) LevelRenderer.renderLineBox(pose, lines, area, 0.35f, 0.75f, 0.9f, 0.9f);
        if (obstacle != null) LevelRenderer.renderLineBox(pose, lines, obstacle, 1f, 0.25f, 0.1f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}

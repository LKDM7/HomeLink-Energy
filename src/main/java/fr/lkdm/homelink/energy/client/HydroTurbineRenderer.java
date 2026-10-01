package fr.lkdm.homelink.energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.blockentity.HydroTurbineBlockEntity;
import fr.lkdm.homelink.energy.config.HydroClientConfig;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Client art and motion of the turbine: rear rotor behind the grille, front louvers and a short
 * translucent water sheet. The server sends a bounded, normalized flow and the state; everything here
 * is interpolated locally and never fed back into production. No block of water is ever placed.
 */
public final class HydroTurbineRenderer implements BlockEntityRenderer<HydroTurbineBlockEntity> {
    public static final ModelResourceLocation HUB = model("hydro_rotor_hub"), BLADE = model("hydro_rotor_blade"), LOUVER = model("hydro_louver");
    private static final int BLADES = 6;
    private static final double MAX_DEGREES_PER_TICK = 24;
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("block/water_still");
    private final WeakHashMap<HydroTurbineBlockEntity, Motion> motions = new WeakHashMap<>();

    private static ModelResourceLocation model(String key) { return ModelResourceLocation.standalone(HomeLinkEnergy.id("block/" + key)); }
    private static final class Motion { double time, speed, angle, louvers; }

    public HydroTurbineRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public int getViewDistance() { return 64; }

    /** Covers the eight cells plus the discharge in front, so culling never hides a visible turbine. */
    @Override public AABB getRenderBoundingBox(HydroTurbineBlockEntity turbine) {
        var origin = turbine.getBlockPos();
        var facing = turbine.facing();
        var far = origin.relative(facing.getClockWise()).relative(facing.getOpposite()).above();
        return new AABB(origin).minmax(new AABB(far)).minmax(new AABB(origin.relative(facing))).inflate(0.25);
    }

    @Override public void render(HydroTurbineBlockEntity turbine, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (turbine.getLevel() == null) return;
        double now = turbine.getLevel().getGameTime() + partial;
        double target = turbine.clientFlow();
        Motion motion = motions.computeIfAbsent(turbine, key -> {
            Motion m = new Motion();
            m.time = now; m.speed = target; m.louvers = target > 0 ? 1 : 0;
            return m;
        });
        boolean animate = HydroClientConfig.animations();
        double dt = Math.max(0, Math.min(20, now - motion.time));
        motion.time = now;
        if (animate) {
            // Spin-up and coast-down over about three seconds; the angle is never sent by the server.
            double decay = Math.exp(-dt / 60.0);
            motion.speed = target + (motion.speed - target) * decay;
            motion.angle = (motion.angle + motion.speed * MAX_DEGREES_PER_TICK * dt) % 360;
            double open = target > 0 ? 1 : 0;
            motion.louvers += Math.signum(open - motion.louvers) * Math.min(Math.abs(open - motion.louvers), dt / 30.0);
        } else {
            motion.speed = target;
            motion.louvers = target > 0 ? 1 : 0;
        }

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - turbine.facing().toYRot()));
        pose.translate(-0.5, 0, -0.5);

        // Rotor: hub and six blades inside the rear chamber, axis along the depth.
        pose.pushPose();
        pose.translate(1, 16.5 / 16, 25 / 16.0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) motion.angle));
        for (int i = 0; i < BLADES; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / BLADES));
            pose.translate(-0.5, -0.5, -0.5);
            draw(BLADE, turbine, pose, buffers, light, overlay);
            pose.popPose();
        }
        pose.translate(-0.5, -0.5, -0.5);
        draw(HUB, turbine, pose, buffers, light, overlay);
        pose.popPose();

        // Louvers: three slats in the lower front opening, vertical when closed, almost flat when open.
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(3.2 / 16.0, (3 + 2 * i) / 16.0, 2 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees((float) (-72 * motion.louvers)));
            pose.scale(25.6f / 16f, 1, 1);
            pose.translate(0, -0.95 / 16, -0.5);
            draw(LOUVER, turbine, pose, buffers, light, overlay);
            pose.popPose();
        }

        if (motion.louvers > 0.6 && target > 0) sheet(pose, buffers.getBuffer(RenderType.translucent()), light, (float) Math.min(1, target), now, animate);
        pose.popPose();
    }

    /** Two-sided water sheet leaving the opening lip and falling in front, within the cleared cells. */
    private static void sheet(PoseStack pose, VertexConsumer consumer, int light, float flow, double now, boolean animate) {
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float shift = animate ? (float) ((now * 0.02) % 1) : 0;
        float vm = v0 + (v1 - v0) * (0.5f + shift * 0.5f);
        int alpha = (int) (110 + 90 * flow);
        float x0 = 3.5f / 16, x1 = 28.5f / 16;
        float[][] profile = {{2.6f / 16, 0.4f / 16}, {2.2f / 16, -6f / 16}, {0.2f / 16, -11f / 16}};
        var last = pose.last();
        for (int k = 0; k < profile.length - 1; k++) {
            float ya = profile[k][0], za = profile[k][1], yb = profile[k + 1][0], zb = profile[k + 1][1];
            float va = k == 0 ? v0 : vm, vb = k == 0 ? vm : v1;
            for (int side = 0; side < 2; side++) {
                float ny = side == 0 ? 1 : -1;
                vertex(consumer, last, side == 0 ? x0 : x1, ya, za, side == 0 ? u0 : u1, va, alpha, light, ny);
                vertex(consumer, last, side == 0 ? x1 : x0, ya, za, side == 0 ? u1 : u0, va, alpha, light, ny);
                vertex(consumer, last, side == 0 ? x1 : x0, yb, zb, side == 0 ? u1 : u0, vb, alpha, light, ny);
                vertex(consumer, last, side == 0 ? x0 : x1, yb, zb, side == 0 ? u0 : u1, vb, alpha, light, ny);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int alpha, int light, float ny) {
        consumer.addVertex(pose, x, y, z).setColor(63, 118, 228, alpha).setUv(u, v).setLight(light).setNormal(pose, 0, ny, 0);
    }

    private static void draw(ModelResourceLocation model, HydroTurbineBlockEntity turbine, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()), turbine.getBlockState(),
                mc.getModelManager().getModel(model), 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
    }
}

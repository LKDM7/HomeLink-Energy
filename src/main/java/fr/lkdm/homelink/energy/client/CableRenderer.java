package fr.lkdm.homelink.energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.block.CopperEnergyCableBlock;
import fr.lkdm.homelink.energy.blockentity.CableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

/** Thin surface traces, using the same topology queries as the server's electrical graph. */
public final class CableRenderer implements BlockEntityRenderer<CableBlockEntity> {
    public static final ModelResourceLocation TRACE = ModelResourceLocation.standalone(HomeLinkEnergy.id("block/cable_trace"));
    public static final ModelResourceLocation CORNER = ModelResourceLocation.standalone(HomeLinkEnergy.id("block/cable_trace_corner"));
    public CableRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(CableBlockEntity cable, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = cable.getLevel();
        if (level == null) return;
        var state = cable.getBlockState();
        var mc = Minecraft.getInstance();
        for (Direction face : Direction.values()) {
            if (!CopperEnergyCableBlock.hasFace(state, face)) continue;
            var rotation = face.getOpposite().getRotation();
            for (Direction local : Direction.Plane.HORIZONTAL) {
                Vector3f vector = new Vector3f(local.getStepX(), 0, local.getStepZ()).rotate(rotation);
                Direction tangent = Direction.getNearest(vector.x, vector.y, vector.z);
                if (!CopperEnergyCableBlock.arm(level, cable.getBlockPos(), face, tangent)) continue;
                boolean trimmed = CopperEnergyCableBlock.hasFace(state, tangent) && face.ordinal() > tangent.ordinal();
                var model = mc.getModelManager().getModel(trimmed ? CORNER : TRACE);
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                pose.mulPose(rotation);
                pose.mulPose(Axis.YP.rotationDegrees(180 - local.toYRot()));
                pose.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                        state, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
                pose.popPose();
            }
        }
    }
}

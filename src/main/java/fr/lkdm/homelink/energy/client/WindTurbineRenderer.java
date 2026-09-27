package fr.lkdm.homelink.energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lkdm.homelink.energy.HomeLinkEnergy;
import fr.lkdm.homelink.energy.block.WindTurbineBlock;
import fr.lkdm.homelink.energy.blockentity.WindTurbineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.WeakHashMap;

/** Client-only art and motion. The server speed is never fed back into energy production. */
public final class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity> {
    public static final ModelResourceLocation TOWER=model("wind_tower"), NACELLE=model("wind_nacelle"), BLADE=model("wind_blade"), HUB=model("wind_hub");
    public static final ModelResourceLocation NACELLE_2=model("wind_nacelle_2"), NACELLE_3=model("wind_nacelle_3");
    private final WeakHashMap<WindTurbineBlockEntity,Motion> motions=new WeakHashMap<>();
    private static ModelResourceLocation model(String key) { return ModelResourceLocation.standalone(HomeLinkEnergy.id("block/"+key)); }
    private static final class Motion { double time,speed,angle; }
    public WindTurbineRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public int getViewDistance() { return 96; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(WindTurbineBlockEntity turbine) {
        var block=(WindTurbineBlock)turbine.getBlockState().getBlock();
        var bounds=turbine.area().bounds();
        for(var tile:block.positions(turbine.getBlockPos(),turbine.getBlockState())) bounds=bounds.minmax(new net.minecraft.world.phys.AABB(tile));
        return bounds.inflate(1);
    }
    @Override public void render(WindTurbineBlockEntity turbine,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(turbine.getLevel()==null) return;
        double now=turbine.getLevel().getGameTime()+partial;
        Motion motion=motions.get(turbine);
        if(motion==null) {
            motion=new Motion(); motion.time=now; motion.speed=turbine.targetRotorSpeed(); motion.angle=now*motion.speed*9%360; motions.put(turbine,motion);
        }
        double dt=Math.max(0,Math.min(20,now-motion.time)),target=turbine.targetRotorSpeed(), decay=Math.exp(-dt/12.);
        motion.angle=(motion.angle+9*(target*dt+(motion.speed-target)*12*(1-decay)))%360;
        motion.speed=target+(motion.speed-target)*decay; motion.time=now;
        pose.pushPose(); pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-turbine.getBlockState().getValue(WindTurbineBlock.FACING).toYRot()));
        pose.translate((turbine.tier().width()-1)/2.,0,(turbine.tier().depth()-1)/2.);
        // Repeat one-block sections so steel UVs retain their scale instead of stretching over 12 blocks.
        int height=turbine.tier().hubOffset();
        for(int segment=0;segment<height;segment++) {
            float width=.70f+.20f*(turbine.tier().level()-1);
            pose.pushPose(); pose.translate(-width/2,.875+segment,-width/2);
            pose.scale(width,segment==height-1?.625f:1,width);
            draw(TOWER,turbine,pose,buffers,light,overlay); pose.popPose();
        }
        pose.translate(0,turbine.tier().hubOffset()+.5,0);
        var nacelle=turbine.tier().level()==3?NACELLE_3:turbine.tier().level()==2?NACELLE_2:NACELLE;
        pose.pushPose(); pose.translate(-.5,-.5,-.5); draw(nacelle,turbine,pose,buffers,light,overlay); pose.popPose();
        // Bearing clearance keeps the rotating hub's rear faces away from the fixed nacelle faces.
        pose.translate(0,0,-1.1); pose.mulPose(Axis.ZP.rotationDegrees((float)motion.angle));
        float scale=(2*turbine.tier().radius()+1)/3f;
        for(int i=0;i<3;i++) {
            pose.pushPose(); pose.mulPose(Axis.ZP.rotationDegrees(i*120)); pose.scale(scale,scale,1); pose.translate(-.5,-.5,-.5);
            draw(BLADE,turbine,pose,buffers,light,overlay); pose.popPose();
        }
        pose.scale(scale,scale,1); pose.translate(-.5,-.5,-.5);
        draw(HUB,turbine,pose,buffers,light,overlay); pose.popPose();
    }
    private static void draw(ModelResourceLocation model,WindTurbineBlockEntity turbine,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.solid()),turbine.getBlockState(),
                mc.getModelManager().getModel(model),1,1,1,light,overlay,ModelData.EMPTY,RenderType.solid());
    }
}

package fr.lkdm.homelink.energy.wind;

import fr.lkdm.homelink.energy.block.WindTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Conservative block envelope around the rotor, centered on the entire pedestal footprint. */
public record RotorArea(BlockPos hub, BlockPos min, BlockPos max) {
    public static RotorArea of(BlockPos base, Direction facing, WindTier tier) {
        var right=facing.getClockWise(); var back=facing.getOpposite();
        double x=base.getX()+.5+right.getStepX()*(tier.width()-1)/2.+back.getStepX()*(tier.depth()-1)/2.+facing.getStepX()*1.1;
        double z=base.getZ()+.5+right.getStepZ()*(tier.width()-1)/2.+back.getStepZ()*(tier.depth()-1)/2.+facing.getStepZ()*1.1;
        double y=base.getY()+tier.hubOffset()+.5,r=tier.radius()+.5;
        double rx=facing.getAxis()==Direction.Axis.Z?r:.4,rz=facing.getAxis()==Direction.Axis.X?r:.4;
        return new RotorArea(BlockPos.containing(x,y,z),BlockPos.containing(x-rx+1e-7,y-r+1e-7,z-rz+1e-7),
                BlockPos.containing(Math.ceil(x+rx-1e-7)-1,Math.ceil(y+r-1e-7)-1,Math.ceil(z+rz-1e-7)-1));
    }
    public AABB bounds() { return new AABB(min.getX(),min.getY(),min.getZ(),max.getX()+1,max.getY()+1,max.getZ()+1); }
    public boolean affectedBy(BlockPos pos) {
        return (pos.getX()>=min.getX() && pos.getX()<=max.getX() && pos.getY()>=min.getY() && pos.getY()<=max.getY()
                && pos.getZ()>=min.getZ() && pos.getZ()<=max.getZ())
                || (pos.getX()==hub.getX() && pos.getZ()==hub.getZ() && pos.getY()>=hub.getY());
    }
}

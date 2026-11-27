package com.example.examplemod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/** Bounded static block geometry snapshot in a ragdoll's local rig space. */
public final class RagdollBlockCollision
{
    private static final int MAX_BOXES=384;
    public static float[][] capture(World world,double x,double y,double z,float yaw)
    {
        if(world==null||!Double.isFinite(x)||!Double.isFinite(y)||
                !Double.isFinite(z)||!Float.isFinite(yaw))return new float[0][];
        double low=Math.max(0D,y-96D),high=Math.min(256D,y+4D);
        if(low>=high)return new float[0][];
        AxisAlignedBB area=new AxisAlignedBB(x-4D,low,z-4D,x+4D,high,z+4D);
        List<AxisAlignedBB> boxes=new ArrayList<AxisAlignedBB>(world.getCollisionBoxes(null,area));
        boxes.sort(Comparator.comparingDouble(b->distance(b,x,y,z)));
        double radians=Math.toRadians(yaw);
        double c=Math.cos(radians),s=Math.sin(radians);
        float qy=(float)Math.sin(radians*.5D),qw=(float)Math.cos(radians*.5D);
        List<float[]> result=new ArrayList<float[]>();
        for(AxisAlignedBB b:boxes)
        {
            if(result.size()>=MAX_BOXES)break;
            double hx=(b.maxX-b.minX)*.5D,hy=(b.maxY-b.minY)*.5D,hz=(b.maxZ-b.minZ)*.5D;
            if(hx<=0D||hy<=0D||hz<=0D)continue;
            double dx=(b.minX+b.maxX)*.5D-x,dy=(b.minY+b.maxY)*.5D-y,
                    dz=(b.minZ+b.maxZ)*.5D-z;
            result.add(new float[]{(float)(c*dx+s*dz),(float)dy,(float)(-s*dx+c*dz),
                    (float)hx,(float)hy,(float)hz,0F,qy,0F,qw});
        }
        return result.toArray(new float[result.size()][]);
    }
    private static double distance(AxisAlignedBB b,double x,double y,double z)
    {
        double dx=Math.max(0D,Math.max(b.minX-x,x-b.maxX));
        double dy=Math.max(0D,Math.max(b.minY-y,y-b.maxY));
        double dz=Math.max(0D,Math.max(b.minZ-z,z-b.maxZ));
        return dx*dx+dy*dy+dz*dz;
    }
    private RagdollBlockCollision(){}
}

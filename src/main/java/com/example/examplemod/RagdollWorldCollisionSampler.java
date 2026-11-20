package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Samples Minecraft's *actual* collision AABBs around the live ragdoll,
 * including partial shapes provided by Chisels & Bits / LittleTiles when
 * those mods publish their boxes through the 1.12.2 collision API.
 *
 * No fallback full-block solid test: holes must remain holes. Sampling is
 * repeated before each fixed Jolt tick so a falling body can reach terrain
 * several blocks below its original anchor.
 */
public final class RagdollWorldCollisionSampler
{
    public static final int MAX_COLLIDERS=192;
    private static final int MAX_CANDIDATES=8192;
    private static boolean reportedLimit;

    private RagdollWorldCollisionSampler() {}

    public static float[][] sample(World world,BlockbusterRecordFrame record,
            float[][] centers,float[] lengths)
    {
        if(world==null||record==null||centers==null||lengths==null)
            return new float[0][];
        final double actorX=record.getX(),actorY=record.getY(),
                actorZ=record.getZ();
        final float yaw=record.hasBodyYaw()?
                record.getBodyYaw():record.getYaw();
        final List<double[]> worldCenters=new ArrayList<double[]>();
        Set<AxisAlignedBB> unique=new HashSet<AxisAlignedBB>();
        List<AxisAlignedBB> candidates=new ArrayList<AxisAlignedBB>();
        for(int i=0;i<centers.length&&i<lengths.length;i++)
        {
            float[] center=centers[i];
            if(center==null||center.length<3||
                    !Float.isFinite(center[0])||
                    !Float.isFinite(center[1])||
                    !Float.isFinite(center[2]))continue;
            double[] pos=RagdollWorldCollisionMath.worldPoint(
                    center,actorX,actorY,actorZ,yaw);
            worldCenters.add(pos);
            // A capsule may be rotated; its bounding sphere always covers
            // both ends. Extra margin permits the next Jolt substeps.
            double radius=Math.max(0.9D,0.5D*lengths[i]+0.65D);
            AxisAlignedBB area=new AxisAlignedBB(
                    pos[0]-radius,pos[1]-radius,pos[2]-radius,
                    pos[0]+radius,pos[1]+radius,pos[2]+radius);
            List<AxisAlignedBB> nearby=world.getCollisionBoxes(null,area);
            for(AxisAlignedBB box:nearby)
            {
                if(unique.add(box))
                    candidates.add(box);
                if(candidates.size()>=MAX_CANDIDATES)break;
            }
            if(candidates.size()>=MAX_CANDIDATES)break;
        }
        if(candidates.size()>MAX_COLLIDERS)
        {
            Collections.sort(candidates,new Comparator<AxisAlignedBB>()
            {
                @Override public int compare(AxisAlignedBB a,AxisAlignedBB b)
                {
                    int distance=Double.compare(
                            proximity(a,worldCenters),proximity(b,worldCenters));
                    if(distance!=0)return distance;
                    int x=Double.compare(a.minX,b.minX);
                    if(x!=0)return x;
                    int y=Double.compare(a.minY,b.minY);
                    if(y!=0)return y;
                    int z=Double.compare(a.minZ,b.minZ);
                    if(z!=0)return z;
                    x=Double.compare(a.maxX,b.maxX);
                    if(x!=0)return x;
                    y=Double.compare(a.maxY,b.maxY);
                    if(y!=0)return y;
                    return Double.compare(a.maxZ,b.maxZ);
                }
            });
            if(!reportedLimit)
            {
                reportedLimit=true;
                System.err.println("[BBS Editor][Ragdoll][World] "+
                        "Nearby voxel collision count exceeds "+
                        MAX_COLLIDERS+"; closest real boxes retained");
            }
        }
        int count=Math.min(MAX_COLLIDERS,candidates.size());
        float[][] local=new float[count][];
        for(int i=0;i<count;i++)
        {
            AxisAlignedBB b=candidates.get(i);
            local[i]=RagdollWorldCollisionMath.localBox(
                    b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,
                    actorX,actorY,actorZ,yaw);
        }
        return local;
    }

    private static double proximity(AxisAlignedBB box,List<double[]> centers)
    {
        double best=Double.POSITIVE_INFINITY;
        for(double[] p:centers)
        {
            double dx=Math.max(Math.max(box.minX-p[0],0D),p[0]-box.maxX);
            double dy=Math.max(Math.max(box.minY-p[1],0D),p[1]-box.maxY);
            double dz=Math.max(Math.max(box.minZ-p[2],0D),p[2]-box.maxZ);
            best=Math.min(best,dx*dx+dy*dy+dz*dz);
        }
        return best;
    }
}

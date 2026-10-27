package com.example.examplemod;

/**
 * The ONE coordinate contract shared by CAMERA, animated light rig, the
 * BetterLights caster and the editor's cone guide.
 *
 * Everything is in absolute Minecraft world BLOCKS. A Target is a world
 * point, never a bone transform, entity local direction or camera-relative
 * position. No Blockbuster model units, body yaw or GL matrix offsets.
 *
 * Pure Java 8 so projection/coordinate tests can run without Minecraft.
 */
public final class BetterLightsWorldState
{
    public final double x,y,z,targetX,targetY,targetZ;
    public final float directionX,directionY,directionZ;

    private BetterLightsWorldState(double x,double y,double z,
            double tx,double ty,double tz)
    {
        this.x=x;this.y=y;this.z=z;
        double dx=tx-x,dy=ty-y,dz=tz-z;
        double len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        // Constructor only receives validated nondegenerate pairs.
        // Never silently rotate a broken Target toward arbitrary world +Z.
        this.targetX=x+dx;this.targetY=y+dy;this.targetZ=z+dz;
        this.directionX=(float)(dx/len);
        this.directionY=(float)(dy/len);
        this.directionZ=(float)(dz/len);
    }

    public static BetterLightsWorldState points(
            double x,double y,double z,double tx,double ty,double tz)
    {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
                !Double.isFinite(tx)||!Double.isFinite(ty)||
                !Double.isFinite(tz))return null;
        double dx=tx-x,dy=ty-y,dz=tz-z;
        double len=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(!Double.isFinite(len)||len<1E-7D)return null;
        return new BetterLightsWorldState(x,y,z,tx,ty,tz);
    }

    /**
     * Origin and Target keys are INDEPENDENT offsets in WORLD blocks from
     * the sampled Record XYZ. Moving Origin does not drag the aiming point.
     * Target offset includes its rest point (+Z 4 blocks).
     */
    public static BetterLightsWorldState actor(double recordX,
            double recordY,double recordZ,
            float[] origin,float[] target)
    {
        if(origin==null||target==null||origin.length<3||target.length<3)
            return null;
        double x=recordX+origin[0],y=recordY+origin[1],
                z=recordZ+origin[2];
        return points(x,y,z,recordX+target[0],
                recordY+target[1],recordZ+target[2]);
    }
}

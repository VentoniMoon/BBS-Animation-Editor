package com.example.examplemod;

/**
 * Converts between the rendered actor frame and Minecraft world coordinates.
 * Deliberately has no Minecraft or Jolt dependency: this must be testable
 * without Forge, native DLLs, or an editor world.
 *
 * The Emoticons renderer rotates by -renderYawOffset about +Y and then
 * translates by the actor's Record position. World AABBs become OBBs in
 * ragdoll model space; using only translated AABBs breaks at nonzero yaw.
 */
public final class RagdollWorldCollisionMath
{
    private RagdollWorldCollisionMath() {}

    /** World XYZ of a live Jolt model-space body center. */
    public static double[] worldPoint(float[] local,double x,double y,
            double z,float renderYawDegrees)
    {
        if(local==null||local.length<3)
            throw new IllegalArgumentException("Missing ragdoll center");
        double rad=Math.toRadians(-renderYawDegrees);
        double c=Math.cos(rad),s=Math.sin(rad);
        return new double[]{
                x+c*local[0]+s*local[2],
                y+local[1],
                z-s*local[0]+c*local[2]
        };
    }

    /**
     * Convert a genuine world-space collision AABB to model-space oriented
     * box: center XYZ, half extents XYZ, quaternion XYZW (10 floats).
     * No bounding-box inflation or merging across empty microblock space.
     */
    public static float[] localBox(double minX,double minY,double minZ,
            double maxX,double maxY,double maxZ,
            double actorX,double actorY,double actorZ,
            float renderYawDegrees)
    {
        if(!(maxX>minX&&maxY>minY&&maxZ>minZ)||
                !Float.isFinite(renderYawDegrees))
            throw new IllegalArgumentException("Invalid collision box");
        double rad=Math.toRadians(-renderYawDegrees);
        double c=Math.cos(rad),s=Math.sin(rad);
        double dx=(minX+maxX)*0.5D-actorX;
        double dz=(minZ+maxZ)*0.5D-actorZ;
        // Inverse of worldFromActor(-yaw), including the Z sign.
        double localX=c*dx-s*dz;
        double localZ=s*dx+c*dz;
        double inverseAngle=-rad;
        return new float[]{
                (float)localX,
                (float)((minY+maxY)*0.5D-actorY),
                (float)localZ,
                (float)((maxX-minX)*0.5D),
                (float)((maxY-minY)*0.5D),
                (float)((maxZ-minZ)*0.5D),
                0F,(float)Math.sin(inverseAngle*0.5D),
                0F,(float)Math.cos(inverseAngle*0.5D)
        };
    }
}

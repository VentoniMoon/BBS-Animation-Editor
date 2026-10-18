package com.example.examplemod;

/**
 * Physics-safe classification of actor/attachment teleports.
 *
 * Never modifies transforms or decides how much hair should swing. A caller
 * may elect to re-seed native bodies instead of feeding a multi-block
 * teleport into moveKinematic() as a 50ms velocity impulse.
 *
 * Angular comparisons use normalized matrix bases (not Euler channels).
 * Degrees and distances are diagnostics; source transform handedness is
 * unchanged. Pure Java8 and testable without Minecraft/JNI.
 */
public final class JoltPhysicsDiscontinuity
{
    public enum Kind { CONTINUOUS, GAP, TELEPORT_TRANSLATION, TELEPORT_ROTATION }

    private JoltPhysicsDiscontinuity() {}

    public static Kind classify(int previousFrame,int currentFrame,
            float[] previousRoot,float[] currentRoot,
            float[] previousParent,float[] currentParent,
            float maxBlocksPerTick,float maxRadiansPerTick)
    {
        if(currentFrame!=previousFrame+1)return Kind.GAP;
        if(!(maxBlocksPerTick>0F) || !(maxRadiansPerTick>0F) ||
                !Float.isFinite(maxBlocksPerTick) ||
                !Float.isFinite(maxRadiansPerTick))
            throw new IllegalArgumentException("Invalid discontinuity thresholds");
        if(distance(previousRoot,currentRoot)>maxBlocksPerTick)
            return Kind.TELEPORT_TRANSLATION;
        if(angularDistance(previousParent,currentParent)>maxRadiansPerTick)
            return Kind.TELEPORT_ROTATION;
        return Kind.CONTINUOUS;
    }

    public static float distance(float[] a,float[] b)
    {
        if(a==null || b==null || a.length!=3 || b.length!=3)
            throw new IllegalArgumentException("Expected two xyz vectors");
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Invalid world position");
        return (float)Math.sqrt(x*x+y*y+z*z);
    }

    public static float angularDistance(float[] a,float[] b)
    {
        if(a==null || b==null || a.length!=16 || b.length!=16)
            throw new IllegalArgumentException("Expected two 4x4 frames");
        float trace=0F;
        for(int col=0;col<3;col++)
        {
            int at=col*4;
            float ax=a[at],ay=a[at+1],az=a[at+2];
            float bx=b[at],by=b[at+1],bz=b[at+2];
            float al=(float)Math.sqrt(ax*ax+ay*ay+az*az);
            float bl=(float)Math.sqrt(bx*bx+by*by+bz*bz);
            if(!(al>1E-6F) || !(bl>1E-6F) ||
                    !Float.isFinite(al) || !Float.isFinite(bl))
                throw new IllegalArgumentException("Invalid parent basis");
            trace+=(ax*bx+ay*by+az*bz)/(al*bl);
        }
        float cosine=Math.max(-1F,Math.min(1F,(trace-1F)*0.5F));
        return (float)Math.acos(cosine);
    }
}

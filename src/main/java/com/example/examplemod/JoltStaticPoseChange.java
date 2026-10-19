package com.example.examplemod;

/**
 * Detects deliberate pose edits on a PAUSED timeline frame without treating
 * tiny render interpolation differences as native simulation ticks.
 * Geometry is already in Jolt world space. No Forge/JNI dependency.
 */
public final class JoltStaticPoseChange
{
    private JoltStaticPoseChange() {}

    /** Thresholds intentionally larger than interpolation noise. */
    public static boolean significant(float[] previousParent,float[] currentParent,
            float[][] previousBoxes,float[][] currentBoxes)
    {
        final float translation=0.20F;
        final float radians=(float)Math.toRadians(15D);
        if(previousParent!=null && currentParent!=null)
        {
            try
            {
                if(JoltPhysicsDiscontinuity.angularDistance(
                        previousParent,currentParent)>radians)
                    return true;
            }
            catch(IllegalArgumentException malformed)
            {
                return true;
            }
        }
        if(previousBoxes==null || currentBoxes==null ||
                previousBoxes.length!=currentBoxes.length)
            return false; // topology changes have their own reset path
        final float minimumDot=(float)Math.cos(radians*0.5F);
        for(int i=0;i<previousBoxes.length;i++)
        {
            float[] old=previousBoxes[i],now=currentBoxes[i];
            if(old==null || now==null || old.length!=10 || now.length!=10)
                return true;
            float dx=old[0]-now[0],dy=old[1]-now[1],dz=old[2]-now[2];
            if(!Float.isFinite(dx) || !Float.isFinite(dy) ||
                    !Float.isFinite(dz))return true;
            if(dx*dx+dy*dy+dz*dz>translation*translation)return true;
            float dot=0F,oldNorm=0F,newNorm=0F;
            for(int k=3;k<=6;k++)
            {
                dot+=old[k]*now[k];
                oldNorm+=old[k]*old[k];
                newNorm+=now[k]*now[k];
            }
            float norm=(float)Math.sqrt(oldNorm*newNorm);
            if(!(norm>1E-6F) || !Float.isFinite(norm))return true;
            if(Math.abs(dot)/norm<minimumDot)return true;
        }
        return false;
    }
}

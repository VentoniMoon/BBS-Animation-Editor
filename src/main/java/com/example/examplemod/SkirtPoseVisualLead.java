package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded visual lead for an exactly one-tick-delayed Chameleon skirt.
 *
 * The parent Emoticons actor renders frame N before native post-render Jolt
 * has solved frame N. The frozen skirt sample used in that draw is N-1.
 * Estimate N from the TWO previously completed native samples (N-2,N-1)
 * without advancing Jolt, re-rendering actors, editing bones or touching
 * native collision state. Rewinds, seeks, and first samples never predict.
 *
 * Quaternion shortest-arc extrapolation avoids Euler flips; each bone gets
 * at most MAX_STEP radians of additional visual motion in this one tick.
 * This improves temporal alignment only, not imperfect Jolt contacts.
 */
public final class SkirtPoseVisualLead
{
    public static final float MAX_STEP=.12F; // about 6.9 deg / tick
    public static final float MAX_WIDTH_CHANGE=.035F;

    private SkirtPoseVisualLead(){}

    public static Map<String,float[]> swings(Map<String,float[]> older,
            Map<String,float[]> newest)
    {
        if(older==null||newest==null || older.size()!=40 ||
                !older.keySet().equals(newest.keySet()))
            return null;
        Map<String,float[]> output=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,float[]> e:newest.entrySet())
        {
            float[] before=older.get(e.getKey()),after=e.getValue();
            if(!valid(before)||!valid(after))return null;
            float[] result=predict(before,after);
            if(result==null)return null;
            output.put(e.getKey(),result);
        }
        return output;
    }

    public static Map<String,SkirtAdaptiveWidth.Width> widths(
            Map<String,SkirtAdaptiveWidth.Width> older,
            Map<String,SkirtAdaptiveWidth.Width> newest)
    {
        if(older==null||newest==null||older.size()!=40||
                !older.keySet().equals(newest.keySet()))return null;
        Map<String,SkirtAdaptiveWidth.Width> output=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        for(Map.Entry<String,SkirtAdaptiveWidth.Width> e:newest.entrySet())
        {
            SkirtAdaptiveWidth.Width before=older.get(e.getKey());
            SkirtAdaptiveWidth.Width after=e.getValue();
            if(before==null||after==null||before.axis!=after.axis||
                    !Float.isFinite(before.multiplier)||
                    !Float.isFinite(after.multiplier))return null;
            float delta=Math.max(-MAX_WIDTH_CHANGE,Math.min(
                    MAX_WIDTH_CHANGE,after.multiplier-before.multiplier));
            float cap=after.multiplier+delta;
            cap=Math.max(1F,Math.min(after.axis==0 || after.axis==2?
                    SkirtAdaptiveWidth.MAX_LOWER:1.28F,cap));
            output.put(e.getKey(),new SkirtAdaptiveWidth.Width(
                    after.axis,cap));
        }
        return output;
    }

    private static float[] predict(float[] before,float[] after)
    {
        try
        {
            float[] q0=JoltPhysicsPoseInterpolator.axisAngleToQuaternion(before);
            float[] q1=JoltPhysicsPoseInterpolator.axisAngleToQuaternion(after);
            // Relative rotation of the most recent one-tick motion.
            float[] delta=multiply(q1,conjugate(q0));
            if(delta[3]<0F)for(int i=0;i<4;i++)delta[i]=-delta[i];
            float len=(float)Math.sqrt(delta[0]*delta[0]+
                    delta[1]*delta[1]+delta[2]*delta[2]);
            if(len<1E-7F)return after.clone();
            float angle=2F*(float)Math.atan2(len,delta[3]);
            float lead=Math.min(MAX_STEP,angle);
            float half=lead*.5F;
            float scale=(float)Math.sin(half)/len;
            float[] step={delta[0]*scale,delta[1]*scale,
                    delta[2]*scale,(float)Math.cos(half)};
            return JoltPhysicsPoseInterpolator.quaternionToAxisAngle(
                    multiply(step,q1));
        }
        catch(IllegalArgumentException invalid)
        {
            return null;
        }
    }

    private static float[] conjugate(float[] q)
    {return new float[]{-q[0],-q[1],-q[2],q[3]};}

    private static float[] multiply(float[] a,float[] b)
    {
        float[] q={
                a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
                a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
                a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
                a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]
        };
        float n=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
        if(n<1E-7F)throw new IllegalArgumentException("Invalid quaternion");
        for(int i=0;i<4;i++)q[i]/=n;
        return q;
    }

    private static boolean valid(float[] v)
    {
        if(v==null||v.length!=4)return false;
        for(float f:v)if(!Float.isFinite(f))return false;
        return true;
    }
}

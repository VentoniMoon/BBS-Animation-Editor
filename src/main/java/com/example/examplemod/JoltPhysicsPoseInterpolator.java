package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Side-effect-free interpolation of recorded Jolt quaternion poses.
 *
 * Jolt solves exactly 20 fixed ticks per second; this class interpolates
 * display values without adding energy, changing constraints or mutating
 * native bodies. Quaternions use xyzw, axis angles use {axis xyz, radians}
 * (the adapter's current Chameleon pose-writer contract).
 *
 * Never interpolate Euler angles: their discontinuities and wrap can
 * introduce a visual 90/180-degree snap unrelated to Jolt dynamics.
 */
public final class JoltPhysicsPoseInterpolator
{
    private JoltPhysicsPoseInterpolator() {}

    private static float clampAlpha(float alpha)
    {
        if(!Float.isFinite(alpha))
            throw new IllegalArgumentException("Invalid render fraction");
        return Math.max(0F,Math.min(1F,alpha));
    }

    public static float[] position(float[] from,float[] to,float alpha)
    {
        float t=clampAlpha(alpha);
        if(from==null || to==null || from.length!=3 || to.length!=3)
            throw new IllegalArgumentException("Expected pair of xyz positions");
        float[] out=new float[3];
        for(int i=0;i<3;i++)
        {
            if(!Float.isFinite(from[i]) || !Float.isFinite(to[i]))
                throw new IllegalArgumentException("Invalid pose position");
            out[i]=from[i]+(to[i]-from[i])*t;
        }
        return out;
    }

    public static float[] quaternion(float[] from,float[] to,float alpha)
    {
        float t=clampAlpha(alpha);
        float[] a=unit(from),b=unit(to);
        float dot=dot(a,b);
        // q and -q express the identical orientation. Negate ONE endpoint
        // so the visual pose follows the shortest rotational path.
        if(dot<0F)
        {
            dot=-dot;
            for(int i=0;i<4;i++)b[i]=-b[i];
        }
        dot=Math.max(0F,Math.min(1F,dot));
        float x,y;
        if(dot>0.9995F)
        {
            x=1F-t;y=t;
        }
        else
        {
            double theta=Math.acos(dot);
            double sin=Math.sin(theta);
            x=(float)(Math.sin((1F-t)*theta)/sin);
            y=(float)(Math.sin(t*theta)/sin);
        }
        return unit(new float[]{
                x*a[0]+y*b[0],x*a[1]+y*b[1],
                x*a[2]+y*b[2],x*a[3]+y*b[3]});
    }

    public static float[] axisAngleToQuaternion(float[] axisAngle)
    {
        if(axisAngle==null || axisAngle.length!=4)
            throw new IllegalArgumentException("Expected {axis xyz, radians}");
        for(float value:axisAngle)
            if(!Float.isFinite(value))
                throw new IllegalArgumentException("Invalid axis angle");
        float angle=axisAngle[3];
        float length=(float)Math.sqrt(axisAngle[0]*axisAngle[0]+
                axisAngle[1]*axisAngle[1]+axisAngle[2]*axisAngle[2]);
        if(Math.abs(angle)<1E-7F)
            return new float[]{0F,0F,0F,1F};
        if(!(length>1E-7F))
            throw new IllegalArgumentException("Missing nonzero rotation axis");
        float sine=(float)Math.sin(angle*0.5F)/length;
        return unit(new float[]{
                axisAngle[0]*sine,axisAngle[1]*sine,
                axisAngle[2]*sine,(float)Math.cos(angle*0.5F)});
    }

    public static float[] quaternionToAxisAngle(float[] quaternion)
    {
        float[] q=unit(quaternion);
        if(q[3]<0F)
            for(int i=0;i<4;i++)q[i]=-q[i];
        float len=(float)Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]);
        if(len<1E-7F)
            return new float[]{1F,0F,0F,0F};
        float radians=2F*(float)Math.atan2(len,q[3]);
        return new float[]{q[0]/len,q[1]/len,q[2]/len,radians};
    }

    /** Axis-angle swings returned in the SAME order as Jolt adapter. */
    public static float[] axisAngle(float[] before,float[] after,float alpha)
    {
        return quaternionToAxisAngle(quaternion(
                axisAngleToQuaternion(before),
                axisAngleToQuaternion(after),alpha));
    }

    public static Map<String,float[]> swings(Map<String,float[]> previous,
            Map<String,float[]> next,float alpha)
    {
        clampAlpha(alpha);
        if(previous==null || next==null ||
                !previous.keySet().equals(next.keySet()))
            throw new IllegalArgumentException(
                    "The two frame samples must contain the same bones");
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        for(Map.Entry<String,float[]> entry:previous.entrySet())
            result.put(entry.getKey(),
                    axisAngle(entry.getValue(),next.get(entry.getKey()),alpha));
        return result;
    }

    private static float dot(float[] a,float[] b)
    {
        return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3];
    }

    private static float[] unit(float[] source)
    {
        if(source==null || source.length!=4)
            throw new IllegalArgumentException("Expected quaternion xyzw");
        double lengthSquared=0D;
        for(float v:source)
        {
            if(!Float.isFinite(v))
                throw new IllegalArgumentException("Nonfinite quaternion");
            lengthSquared+=v*v;
        }
        if(!(lengthSquared>1E-14D))
            throw new IllegalArgumentException("Zero quaternion");
        float factor=(float)(1D/Math.sqrt(lengthSquared));
        return new float[]{source[0]*factor,source[1]*factor,
                source[2]*factor,source[3]*factor};
    }
}

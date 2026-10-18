package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JoltPoseInterpolationSmoke
{
    private JoltPoseInterpolationSmoke() {}

    private static void near(float a,float b)
    {
        if(Math.abs(a-b)>0.0004F)
            throw new AssertionError("Expected "+b+", found "+a);
    }

    private static void equivalent(float[] a,float[] b)
    {
        float dot=Math.abs(a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]);
        near(dot,1F);
    }

    public static void main(String[] args)
    {
        float s=(float)Math.sin(Math.PI/4D);
        float c=(float)Math.cos(Math.PI/4D);
        float[] ident={0,0,0,1},yaw90={0,s,0,c};
        float[] half=JoltPhysicsPoseInterpolator.quaternion(ident,yaw90,0.5F);
        equivalent(half,new float[]{0,
                (float)Math.sin(Math.PI/8D),0,
                (float)Math.cos(Math.PI/8D)});
        float[] antipode={0,-s,0,-c};
        equivalent(JoltPhysicsPoseInterpolator.quaternion(
                yaw90,antipode,0.5F),yaw90);

        float[] near180a=JoltPhysicsPoseInterpolator.axisAngleToQuaternion(
                new float[]{0,1,0,(float)Math.toRadians(179D)});
        float[] near180b=JoltPhysicsPoseInterpolator.axisAngleToQuaternion(
                new float[]{0,1,0,(float)Math.toRadians(-179D)});
        float[] across=JoltPhysicsPoseInterpolator.quaternion(
                near180a,near180b,0.5F);
        equivalent(across,new float[]{0,1,0,0});
        float[] zero=JoltPhysicsPoseInterpolator.axisAngle(
                new float[]{1,0,0,0},new float[]{0,0,1,0},0.5F);
        near(zero[3],0F);

        Map<String,float[]> from=new LinkedHashMap<String,float[]>();
        Map<String,float[]> to=new LinkedHashMap<String,float[]>();
        from.put("hair-1",new float[]{0,1,0,0});
        to.put("hair-1",new float[]{0,1,0,(float)Math.PI/2F});
        float[] middle=JoltPhysicsPoseInterpolator.swings(from,to,.5F).get("hair-1");
        near(middle[3],(float)Math.PI/4F);
        equivalent(JoltPhysicsPoseInterpolator.axisAngleToQuaternion(middle),
                half);
        if(!Float.isFinite(middle[3]))
            throw new AssertionError("Invalid interpolation");
        try
        {
            JoltPhysicsPoseInterpolator.quaternion(ident,yaw90,Float.NaN);
            throw new AssertionError("NaN interpolation accepted");
        }
        catch(IllegalArgumentException expected) {}
        try
        {
            JoltPhysicsPoseInterpolator.swings(from,new LinkedHashMap<String,float[]>(),.5F);
            throw new AssertionError("Mismatched bone topology accepted");
        }
        catch(IllegalArgumentException expected) {}
        float[] lerp=JoltPhysicsPoseInterpolator.position(
                new float[]{-1,1,3},new float[]{1,3,-1},.25F);
        near(lerp[0],-.5F);
        near(lerp[1],1.5F);
        near(lerp[2],2F);
        System.out.println("20Hz Jolt pose slerp, antipodal 180°, axis-angle and positions: PASS");
    }
}

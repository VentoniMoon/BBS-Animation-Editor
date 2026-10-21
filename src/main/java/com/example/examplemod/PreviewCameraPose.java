package com.example.examplemod;

/**
 * Immutable camera sample, independent of Minecraft/GL.
 * Shared by interactive Orbit/Free navigation, later camera-track playback,
 * scene rendering and editor overlays. Angles are degrees, FOV is vertical.
 */
public final class PreviewCameraPose
{
    public final double x,y,z;
    public final float yaw,pitch,roll,fov;
    public final float nearPlane,farPlane;

    public PreviewCameraPose(double x,double y,double z,
            float yaw,float pitch,float roll,float fov,
            float nearPlane,float farPlane)
    {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
                !Float.isFinite(yaw)||!Float.isFinite(pitch)||
                !Float.isFinite(roll)||!Float.isFinite(fov)||
                !Float.isFinite(nearPlane)||!Float.isFinite(farPlane)||
                fov<1F||fov>170F||nearPlane<=0F||farPlane<=nearPlane)
            throw new IllegalArgumentException("Invalid Preview 3.0 camera pose");
        this.x=x;this.y=y;this.z=z;
        this.yaw=yaw;this.pitch=pitch;this.roll=roll;this.fov=fov;
        this.nearPlane=nearPlane;this.farPlane=farPlane;
    }

    public static PreviewCameraPose interpolate(PreviewCameraPose a,
            PreviewCameraPose b,double fraction)
    {
        if(a==null||b==null)throw new IllegalArgumentException("Missing camera pose");
        double t=Math.max(0D,Math.min(1D,fraction));
        return new PreviewCameraPose(
                lerp(a.x,b.x,t),lerp(a.y,b.y,t),lerp(a.z,b.z,t),
                lerpAngle(a.yaw,b.yaw,t),lerpAngle(a.pitch,b.pitch,t),
                lerpAngle(a.roll,b.roll,t),(float)lerp(a.fov,b.fov,t),
                (float)lerp(a.nearPlane,b.nearPlane,t),
                (float)lerp(a.farPlane,b.farPlane,t));
    }

    private static double lerp(double a,double b,double t)
    {
        return a+(b-a)*t;
    }

    private static float lerpAngle(float a,float b,double t)
    {
        double change=(b-a)%360D;
        if(change>180D)change-=360D;
        if(change< -180D)change+=360D;
        return (float)(a+change*t);
    }
}

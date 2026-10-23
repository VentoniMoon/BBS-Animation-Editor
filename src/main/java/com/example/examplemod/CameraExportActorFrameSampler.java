package com.example.examplemod;

/**
 * Temporary render-only Record pose: the authored 20 TPS .dat frames are
 * immutable. Continuous position/angles blend between ticks; flags, morph
 * actions, equipment and other discrete Record data remain on the floor tick.
 * Never interpolates over absent Record frames or beyond the last scene tick.
 */
public final class CameraExportActorFrameSampler
{
    public static BlockbusterRecordFrame sample(BlockbusterRecord record,
            CameraExportPlan.Sample time)
    {
        if(record==null||time==null)return null;
        BlockbusterRecordFrame a=record.getFrame(time.sceneTick);
        if(a==null||time.tickFraction<=0D)return a;
        BlockbusterRecordFrame b=record.getFrame(time.sceneTick+1);
        if(b==null)return a;
        double t=time.tickFraction;
        BlockbusterRecordFrame result=BlockbusterRecordFrame.fromNBT(a.toNBT());
        result.setX(lerp(a.getX(),b.getX(),t));
        result.setY(lerp(a.getY(),b.getY(),t));
        result.setZ(lerp(a.getZ(),b.getZ(),t));
        result.setYaw(angle(a.getYaw(),b.getYaw(),t));
        result.setPitch(angle(a.getPitch(),b.getPitch(),t));
        result.setYawHead(angle(a.getYawHead(),b.getYawHead(),t));
        if(a.hasBodyYaw()&&b.hasBodyYaw())
            result.setBodyYaw(angle(a.getBodyYaw(),b.getBodyYaw(),t));
        result.setMountYaw(angle(a.getMountYaw(),b.getMountYaw(),t));
        result.setMountPitch(angle(a.getMountPitch(),b.getMountPitch(),t));
        result.setMotionX((float)lerp(a.getMotionX(),b.getMotionX(),t));
        result.setMotionY((float)lerp(a.getMotionY(),b.getMotionY(),t));
        result.setMotionZ((float)lerp(a.getMotionZ(),b.getMotionZ(),t));
        return result;
    }

    private static double lerp(double a,double b,double t)
    {
        return a+(b-a)*t;
    }
    private static float angle(float a,float b,double t)
    {
        double delta=(b-a)%360D;
        if(delta>180D)delta-=360D;
        if(delta< -180D)delta+=360D;
        return (float)(a+delta*t);
    }
    private CameraExportActorFrameSampler(){}
}

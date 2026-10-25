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
        return time==null?null:sample(record,time.sceneTick,time.tickFraction);
    }

    /** A render-only Record sample for both GUI Preview and offline output. */
    public static BlockbusterRecordFrame sample(BlockbusterRecord record,
            int tick,double fraction)
    {
        if(record==null)return null;
        BlockbusterRecordFrame a=record.getFrame(tick);
        if(a==null||fraction<=0D)return a;
        BlockbusterRecordFrame b=record.getFrame(tick+1);
        if(b==null)return a;
        double t=Math.max(0D,Math.min(1D,fraction));
        BlockbusterRecordFrame result=BlockbusterRecordFrame.fromNBT(a.toNBT());
        result.setX(lerp(a.getX(),b.getX(),t));
        result.setY(lerp(a.getY(),b.getY(),t));
        result.setZ(lerp(a.getZ(),b.getZ(),t));
        result.setYaw(angle(a.getYaw(),b.getYaw(),t));
        result.setPitch(angle(a.getPitch(),b.getPitch(),t));
        result.setYawHead(angle(a.getYawHead(),b.getYawHead(),t));
        result.setRoll(angle(a.getRoll(),b.getRoll(),t));
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

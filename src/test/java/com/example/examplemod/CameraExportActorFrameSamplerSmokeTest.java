package com.example.examplemod;

/** 30/60 FPS actor poses remain continuous, Record data stays immutable. */
public final class CameraExportActorFrameSamplerSmokeTest
{
    static void near(double a,double b,String msg)
    {if(Math.abs(a-b)>0.0001D)throw new AssertionError(msg+" "+a+" != "+b);}
    static void check(boolean yes,String msg)
    {if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args)
    {
        BlockbusterRecord record=new BlockbusterRecord();
        BlockbusterRecordFrame a=new BlockbusterRecordFrame();
        BlockbusterRecordFrame b=new BlockbusterRecordFrame();
        a.setX(0);b.setX(12);a.setYaw(170);b.setYaw(-170);
        a.setMotionZ(0);b.setMotionZ(6);
        a.discreteAction=3;b.discreteAction=7;
        record.frames[0]=a;record.frames[1]=b;
        CameraExportPlan fps60=new CameraExportPlan(128,128,60,2);
        CameraExportPlan.Sample midway=fps60.sample(1);
        near(midway.sceneTime(),1D/3D,"60 FPS fractional clock");
        CameraExportRenderClock.set(midway);
        near(CameraExportRenderClock.animationFrame(0),1D/3D,
                "render adapters use fractional frame");
        near(CameraExportRenderClock.partialTick(0,0.5F),1D/3D,
                "Jolt visuals use matching fractional sample");
        BlockbusterRecordFrame result=
                CameraExportActorFrameSampler.sample(record,midway);
        near(result.getX(),4D,"actor moves a third of record interval");
        near(result.getYaw(),176.666666D,"yaw crosses 180 shortest path");
        near(result.getMotionZ(),2D,"record velocity subframe");
        check(result.discreteAction==3,"actions must remain on the floor tick");
        check(record.frames[0].getX()==0D,"authored record is immutable");
        CameraExportRenderClock.clear();
        near(CameraExportRenderClock.animationFrame(0),0D,
                "normal preview clock restored");
        near(CameraExportRenderClock.partialTick(0,0.75F),0.75F,
                "normal render partial ticks restored");
        CameraExportPlan fps30=new CameraExportPlan(128,128,30,2);
        near(fps30.sample(1).sceneTime(),2D/3D,"30 FPS fractional clock");
        near(fps60.sample(5).sceneTime(),5D/3D,"60 FPS last subframe");
        record.frames[1]=null;
        check(CameraExportActorFrameSampler.sample(record,midway)==a,
                "missing record successor holds last valid frame");
        System.out.println("CAMERA 30/60 FPS actor, Jolt clock and cut PASS");
    }
}

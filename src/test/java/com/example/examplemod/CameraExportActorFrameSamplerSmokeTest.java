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
        // Normal preview uses render FPS while timeline retains 20 TPS.
        CameraVisualFrameClock.beginPreview(0,0.5F,true,false);
        near(CameraVisualFrameClock.sceneTime(0),0.5D,
                "60 FPS GUI preview samples 20.5 at partial=0.5");
        near(CameraVisualFrameClock.animationFrame(0),0.5D,
                "selected bones receive the same fractional frame");
        near(CameraVisualFrameClock.partialTick(0,0F),0.5D,
                "native model renderer receives preview sub-tick");
        near(CameraExportActorFrameSampler.sample(record,0,
                CameraVisualFrameClock.fraction(0)).getX(),6D,
                "actor Record position interpolates in the GUI preview");
        CameraVisualFrameClock.beginPreview(0,0.9F,true,true);
        check(!CameraVisualFrameClock.hasSample(0),
                "Jolt bake disables render fractional timing");
        CameraVisualFrameClock.beginPreview(0,0.9F,false,false);
        check(!CameraVisualFrameClock.hasSample(0),
                "paused scrub still resolves whole Record tick");
        CameraVisualFrameClock.beginPreview(0,0.7F,true,false);
        CameraExportRenderClock.set(fps60.sample(1));
        near(CameraVisualFrameClock.sceneTime(0),1D/3D,
                "export exact sample overrides realtime partial");
        CameraExportRenderClock.clear();
        CameraVisualFrameClock.clearPreview();
        check(!CameraVisualFrameClock.hasSample(0),
                "preview render clock restored at RenderTick END");

        record.frames[1]=null;
        check(CameraExportActorFrameSampler.sample(record,midway)==a,
                "missing record successor holds last valid frame");
        System.out.println("CAMERA 30/60 FPS actor, Jolt clock and cut PASS");
    }
}

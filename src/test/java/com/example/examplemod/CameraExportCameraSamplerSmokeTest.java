package com.example.examplemod;

/** Validate smooth offline camera frames without blending across hard cuts. */
public final class CameraExportCameraSamplerSmokeTest
{
    private static void check(boolean okay,String note)
    {
        if(!okay)throw new AssertionError(note);
    }

    private static void near(double actual,double expected,String note)
    {
        if(Math.abs(actual-expected)>1E-5D)
            throw new AssertionError(note+": "+actual+" != "+expected);
    }

    private static PreviewCameraPose pose(double x,float yaw)
    {
        return new PreviewCameraPose(x,3D,0D,yaw,0F,0F,
                70F,0.05F,512F);
    }

    public static void main(String[] args)
    {
        CameraExportPlan clock=new CameraExportPlan(128,128,40,20);
        CameraClipDocument timeline=new CameraClipDocument();
        CameraClipDocument.Clip movement=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.KEYFRAME,0,20,0);
        movement.keys.add(new CameraClipDocument.Key(0,pose(0D,170F)));
        movement.keys.add(new CameraClipDocument.Key(10,pose(10D,-170F)));
        check(timeline.add(movement),"create continuous KEYFRAME clip");
        PreviewCameraPose fallback=pose(-99D,0F);
        CameraExportPlan.Sample between=clock.sample(1);
        near(between.sceneTime(),0.5D,"half-tick at 40fps");
        PreviewCameraPose p=CameraExportCameraSampler.sample(timeline,
                clock,between,fallback,null);
        near(p.x,0.5D,"fractional X interpolates to sub-tick");
        near(p.yaw,171D,"yaw uses shortest 20-degree arc");
        p=CameraExportCameraSampler.sample(timeline,
                clock,clock.sample(2),fallback,null);
        near(p.x,1D,"exact integer frame not modified");
        PreviewCameraPose realTime=CameraExportCameraSampler.sampleAt(
                timeline,clock.sceneTicks,0,0.5D,fallback,null);
        near(realTime.x,p.x*0.5D,"preview's half-tick camera is smooth");
        near(realTime.x,0.5D,"preview and 40fps film have identical pose");

        // A second on/off clip is an intentional hard camera cut: do not
        // invent an intermediate camera position between different shots.
        CameraClipDocument cutDoc=new CameraClipDocument();
        CameraClipDocument.Clip before=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IDLE,0,10,0);
        before.keys.add(new CameraClipDocument.Key(0,pose(12D,0F)));
        CameraClipDocument.Clip after=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IDLE,10,10,0);
        after.keys.add(new CameraClipDocument.Key(0,pose(300D,0F)));
        check(cutDoc.add(before)&&cutDoc.add(after),"add adjacent shots");
        PreviewCameraPose a=CameraExportCameraSampler.sample(cutDoc,
                clock,clock.sample(19),fallback,null);
        near(a.x,12D,"frame 9.5 holds prior shot instead of blending cut");
        PreviewCameraPose b=CameraExportCameraSampler.sample(cutDoc,
                clock,clock.sample(20),fallback,null);
        near(b.x,300D,"frame 10 starts new camera shot sharply");

        // Media and sound have no effect on camera interpolation boundaries.
        CameraClipDocument mediaDoc=new CameraClipDocument();
        check(mediaDoc.add(movement.copy()),"copy animated camera track");
        CameraClipDocument.Clip subtitle=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.SUBTITLE,1,5,1);
        check(mediaDoc.add(subtitle),"add timed subtitle");
        PreviewCameraPose mediaPose=CameraExportCameraSampler.sample(
                mediaDoc,clock,clock.sample(1),fallback,null);
        near(mediaPose.x,0.5D,
                "subtitle start does not freeze smooth camera movement");

        // Clamp the final fractional frames to the last scene tick; there is
        // no phantom next scene frame when sampling 60fps video.
        CameraExportPlan last=new CameraExportPlan(128,128,60,1);
        PreviewCameraPose held=CameraExportCameraSampler.sample(
                timeline,last,last.sample(2),fallback,null);
        near(held.x,0D,"last output subframe holds final valid scene tick");

        // Procedural modifiers must be evaluated at 60 Hz rather than
        // linearly interpolating two 20 Hz results (aliasing).
        CameraClipDocument curve=new CameraClipDocument();
        CameraClipDocument.Clip shake=CameraClipParameters.create(
                CameraClipDocument.Kind.SHAKE,0,40,0);
        shake.settings.put("shake","0.5");
        shake.settings.put("shakeAmount","8");
        check(curve.add(shake),"procedural Shake clip");
        CameraExportPlan fps60=new CameraExportPlan(128,128,60,40);
        CameraExportPlan.Sample sub=fps60.sample(1);
        near(sub.sceneTime(),1D/3D,"first 60fps subframe");
        PreviewCameraPose actual=CameraExportCameraSampler.sample(
                curve,fps60,sub,fallback,null);
        PreviewCameraPose exact=curve.sample(1D/3D,fallback,null);
        near(actual.yaw,exact.yaw,
                "60fps SHAKE samples true fractional angle");
        near(actual.pitch,exact.pitch,
                "60fps SHAKE samples true fractional pitch");
        PreviewCameraPose blended=PreviewCameraPose.interpolate(
                curve.sample(0,fallback),curve.sample(1,fallback),1D/3D);
        check(Math.abs(actual.yaw-blended.yaw)>0.1D ||
                Math.abs(actual.pitch-blended.pitch)>0.1D,
                "do not repeat the old interpolated 20TPS procedural result");

        System.out.println("CAMERA export fractional camera interpolation/cuts PASS");
    }
}

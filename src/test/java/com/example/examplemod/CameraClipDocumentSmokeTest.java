package com.example.examplemod;

/**
 * Runs without Forge/OpenGL. Gson is replaced by tiny test-only stubs;
 * production uses Minecraft 1.12.2's bundled Gson implementation.
 */
public final class CameraClipDocumentSmokeTest
{
    private static final PreviewCameraPose BASE=new PreviewCameraPose(
            0,0,0,0,0,0,70,0.05F,512F);
    private static PreviewCameraPose pose(double x,double y,double z)
    {
        return new PreviewCameraPose(x,y,z,0,0,0,70,0.05F,512F);
    }
    private static void check(boolean result,String message)
    {
        if(!result)throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message)
    {
        if(Math.abs(actual-expected)>1E-6)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    public static void main(String[] args)
    {
        check(CameraClipDocument.Kind.values().length==20,
                "BBS FS catalog must include all 20 clip types");
        CameraClipDocument doc=new CameraClipDocument();
        CameraClipDocument.Clip move=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.KEYFRAME,10,50,0);
        move.keys.add(new CameraClipDocument.Key(0,pose(0,3,5)));
        move.keys.add(new CameraClipDocument.Key(40,pose(20,3,5)));
        check(doc.add(move),"add base camera clip");
        CameraClipDocument.Clip bad=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IDLE,20,15,0);
        check(!doc.add(bad),"one clip per layer at overlapping time");
        CameraClipDocument.Clip translate=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.TRANSLATE,10,50,1);
        translate.settings.put("translate","3,0,-2");
        translate.settings.put("active","7");
        check(doc.add(translate),"add upper modifier");
        PreviewCameraPose sample=doc.sample(30,BASE);
        near(sample.x,13,"layer0 base then layer1 translation");
        near(sample.y,3,"Y camera");
        near(sample.z,3,"Z camera");
        near(doc.sample(10,BASE).x,3,"first camera key");
        near(doc.sample(49,BASE).x,22.5,"interpolation before last key");
        check(!doc.move(translate,25,0),"cannot move into occupied layer");
        check(translate.tick==10&&translate.layer==1,
                "failed move must restore original clip");
        check(doc.move(translate,60,1),"valid clip move");
        near(doc.sample(30,BASE).x,10,"out-of-range modifier stops");
        check(doc.move(translate,10,1),"return modifier to scene");

        CameraClipDocument.Clip copied=move.copy();
        check(copied!=move&&copied.keys.get(0)!=move.keys.get(0),
                "deep keyframe duplication");
        copied.keys.get(0).x=999;
        near(move.keys.get(0).x,0,"copy must not change original");
        com.google.gson.JsonObject json=doc.toJson();
        CameraClipDocument loaded=new CameraClipDocument();
        loaded.readJson(json);
        check(loaded.clips().size()==2,"document persistence count");
        near(loaded.sample(30,BASE).x,13,"document persistence pose");
        check("3,0,-2".equals(loaded.clips().get(1).settings.get("translate")),
                "modifier parameters survive serialization");

        CameraClipDocument.Clip look=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.LOOK,0,100,1);
        look.settings.put("atBlock","true");
        look.settings.put("block","0,0,10");
        CameraClipDocument angles=new CameraClipDocument();
        CameraClipDocument.Clip idle=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IDLE,0,100,0);
        idle.keys.add(new CameraClipDocument.Key(0,pose(0,0,0)));
        check(angles.add(idle)&&angles.add(look),"look stack");
        near(angles.sample(20,BASE).yaw,0,"look along +Z");
        look.settings.put("block","10,0,0");
        near(angles.sample(20,BASE).yaw,-90,"look along +X");

        CameraClipDocument.Clip shake=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.SHAKE,0,100,2);
        shake.settings.put("shake","2");
        shake.settings.put("shakeAmount","4");
        shake.settings.put("noise","true");
        check(angles.add(shake),"shake overlay");
        PreviewCameraPose a=angles.sample(25,BASE);
        PreviewCameraPose b=angles.sample(25,BASE);
        near(a.yaw,b.yaw,"repeatable noise yaw");
        near(a.pitch,b.pitch,"repeatable noise pitch");

        CameraClipDocument.Clip tracker=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.TRACKER,0,100,3);
        tracker.settings.put("selector","actor123");
        tracker.settings.put("offset","0,1,0");
        check(angles.add(tracker),"actor tracker");
        PreviewCameraPose tracked=angles.sample(25,BASE,
                new CameraClipDocument.TargetResolver()
                {
                    @Override public CameraClipDocument.Target resolve(
                            String id,int frame)
                    {
                        if(!"actor123".equals(id))return null;
                        return new CameraClipDocument.Target(frame,6,4,25,10);
                    }
                });
        near(tracked.x,25,"record actor tracking at selected frame");
        near(tracked.y,7,"record actor offset");
        near(tracked.z,4,"record actor Z");
        CameraClipDocument channels=new CameraClipDocument();
        CameraClipDocument.Clip channelClip=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.KEYFRAME,0,60,0);
        CameraClipDocument.Key cx0=new CameraClipDocument.Key(0,pose(0,999,0));
        CameraClipDocument.Key cx1=new CameraClipDocument.Key(40,pose(20,999,0));
        cx0.mask=1;cx1.mask=1;
        channelClip.keys.add(cx0);channelClip.keys.add(cx1);
        check(channels.add(channelClip),"single-channel keyframe");
        PreviewCameraPose channelPose=channels.sample(20,pose(0,3,7));
        near(channelPose.x,10,"independent channel interpolation");
        near(channelPose.y,3,"missing channel must preserve lower layer");
        near(channelPose.z,7,"missing position Z remains unchanged");
        near(channelPose.fov,70,"missing FOV remains unchanged");

        CameraClipDocument channelRestored=new CameraClipDocument();
        channelRestored.readJson(channels.toJson());
        check(channelRestored.clips().get(0).keys.get(0).mask==1,
                "per-key channel mask persists");
        CameraClipDocument.Clip path=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.PATH,0,60,0);
        path.settings.put("interpPoint","hermite");
        path.keys.add(new CameraClipDocument.Key(0,pose(0,0,0)));
        path.keys.add(new CameraClipDocument.Key(20,pose(10,0,0)));
        path.keys.add(new CameraClipDocument.Key(40,pose(50,0,0)));
        CameraClipDocument pathDoc=new CameraClipDocument();
        check(pathDoc.add(path),"hermite path");
        double curved=pathDoc.sample(10,BASE).x;
        check(curved>0&&curved<10&&Math.abs(curved-5)>0.1D,
                "Hermite Path must differ from linear path");

        CameraClipDocument fading=new CameraClipDocument();
        CameraClipDocument.Clip fade=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IDLE,0,40,0);
        fade.keys.add(new CameraClipDocument.Key(0,pose(20,0,0)));
        fade.settings.put("envelope.enabled","true");
        fade.settings.put("envelope.mode","0");
        fade.settings.put("envelope.fadeIn","10");
        fade.settings.put("envelope.fadeOut","10");
        check(fading.add(fade),"add envelope clip");
        near(fading.sample(0,BASE).x,0,"fade-in starts transparent");
        near(fading.sample(5,BASE).x,10,"half fade-in");
        near(fading.sample(20,BASE).x,20,"sustain");
        near(fading.sample(35,BASE).x,10,"half fade-out");
        near(CameraClipEnvelope.factor(fade,5),0.5,"shared media envelope");

        // All BBS interpolation dropdown choices must be finite and share
        // endpoints with the curve shown in the native inspector.
        check(CameraInterpolation.OPTIONS.length==40,"BBS FS interpolation registry");
        for(String interpolation:CameraInterpolation.OPTIONS)
        {
            near(CameraInterpolation.sample(0D,interpolation),0D,
                    "interpolation start "+interpolation);
            near(CameraInterpolation.sample(1D,interpolation),1D,
                    "interpolation end "+interpolation);
            for(int t=1;t<10;t++)
                check(Double.isFinite(CameraInterpolation.sample(t/10D,
                        interpolation)),"finite interpolation "+interpolation);
        }
        near(CameraInterpolation.sample(0.25D,"quad_in"),0.0625D,
                "quadratic easing preview equals actual interpolation");
        CameraClipDocument remappedDoc=new CameraClipDocument();
        CameraClipDocument.Clip timed=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.KEYFRAME,0,90,0);
        timed.keys.add(new CameraClipDocument.Key(0,pose(0,0,0)));
        timed.keys.add(new CameraClipDocument.Key(40,pose(40,0,0)));
        check(remappedDoc.add(timed),"remapper lower camera path");
        CameraClipDocument.Clip remap=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.REMAPPER,0,40,1);
        CameraClipDocument.Key remap0=new CameraClipDocument.Key(0,pose(0,0,0));
        CameraClipDocument.Key remap1=new CameraClipDocument.Key(20,pose(40,0,0));
        remap.keys.add(remap0);remap.keys.add(remap1);
        check(remappedDoc.add(remap),"remapper upper layer");
        near(remappedDoc.sample(10,BASE).x,20D,
                "remapper channel evaluates underlying camera at mapped frame");
        CameraClipDocument trimDoc=new CameraClipDocument();
        CameraClipDocument.Clip trimClip=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.KEYFRAME,10,20,0);
        trimClip.keys.add(new CameraClipDocument.Key(0,pose(1,0,0)));
        trimClip.keys.add(new CameraClipDocument.Key(12,pose(2,0,0)));
        check(trimDoc.add(trimClip),"add trimmable camera clip");
        CameraClipDocument.Clip neighbor=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.IMAGE,40,15,0);
        check(trimDoc.add(neighbor),"add neighboring clip");
        check(trimDoc.trim(trimClip,false,35),"extend right edge");
        check(trimClip.tick==10&&trimClip.duration==25,
                "right trim keeps start fixed");
        check(!trimDoc.trim(trimClip,false,45),"reject overlap");
        check(trimClip.tick==10&&trimClip.duration==25,
                "failed trim keeps original duration");
        check(!trimDoc.trim(trimClip,false,17),"protect keys on trim");
        check(trimDoc.trim(trimClip,true,16),"trim left boundary");
        check(trimClip.tick==16&&trimClip.duration==19,
                "left trim keeps opposite end fixed");
        check(!trimDoc.trim(trimClip,true,25),"left key trim blocked");
        check(trimDoc.trim(trimClip,true,4),"extend clip leftwards");
        check(trimClip.tick==4&&trimClip.duration==31,
                "extended clip retains right boundary");
        CameraClipDocument restoredTrim=new CameraClipDocument();
        restoredTrim.readJson(trimDoc.toJson());
        check(restoredTrim.clips().get(0).tick==4,
                "trim persistence roundtrip");
        check(restoredTrim.clips().get(0).keys.size()==2,
                "keys unchanged by edge trim");
        System.out.println("CAMERA clip layering, channels, trim and persistence PASS");
    }
}

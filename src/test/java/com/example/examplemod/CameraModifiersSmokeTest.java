package com.example.examplemod;

/**
 * Complete CAMERA modifier smoke: integer/fractional render time, isolation,
 * stacked order and reproducible seek/forward samples, without Minecraft/GL.
 */
public final class CameraModifiersSmokeTest
{
    private static final PreviewCameraPose ZERO=new PreviewCameraPose(
            0,0,0,0,0,0,70,0.05F,512F);
    private static void ok(boolean condition,String label)
    {if(!condition)throw new AssertionError(label);}
    private static void near(double actual,double expected,String label)
    {
        if(Math.abs(actual-expected)>0.001D)
            throw new AssertionError(label+": "+actual+" != "+expected);
    }
    private static CameraClipDocument.Clip modifier(
            CameraClipDocument.Kind kind)
    {return CameraClipParameters.create(kind,0,41,1);}
    private static CameraClipDocument with(CameraClipDocument.Clip effect)
    {
        CameraClipDocument doc=new CameraClipDocument();
        ok(doc.add(effect),"add "+effect.kind);
        return doc;
    }
    private static CameraClipDocument moving()
    {
        CameraClipDocument doc=new CameraClipDocument();
        CameraClipDocument.Clip path=CameraClipParameters.create(
                CameraClipDocument.Kind.KEYFRAME,0,101,0);
        path.keys.add(new CameraClipDocument.Key(0,ZERO));
        path.keys.add(new CameraClipDocument.Key(100,
                new PreviewCameraPose(100,0,0,0,0,0,70,0.05F,512F)));
        ok(doc.add(path),"base Path");
        return doc;
    }
    public static void main(String[] args)
    {
        CameraClipDocument.Clip translate=modifier(
                CameraClipDocument.Kind.TRANSLATE);
        near(with(translate).sample(4,ZERO).x,1D,
                "Translate acts immediately with default settings");
        translate.settings.put("local","true");
        PreviewCameraPose turned=new PreviewCameraPose(
                0,0,0,90,0,0,70,0.05F,512F);
        near(with(translate).sample(0,turned).z,1D,
                "Local camera X follows camera rotation");
        translate.settings.put("active","2");
        near(with(translate).sample(0,turned).z,0D,
                "Translate respects disabled X channel");

        CameraClipDocument.Clip angle=modifier(CameraClipDocument.Kind.ANGLE);
        near(with(angle).sample(0,ZERO).yaw,5D,
                "Angle has visible default yaw");
        angle.settings.put("fov","10");
        near(with(angle).sample(0,ZERO).fov,80D,
                "Angle can change FOV with active channel");

        CameraClipDocument.Clip shake=modifier(CameraClipDocument.Kind.SHAKE);
        CameraClipDocument shakes=with(shake);
        PreviewCameraPose a=shakes.sample(0,ZERO);
        PreviewCameraPose b=shakes.sample(0.5D,ZERO,null);
        near(a.yaw,shakes.sample(0,ZERO).yaw,
                "Shake is deterministic per output timestamp");
        ok(a.pitch!=b.pitch||a.yaw!=b.yaw,
                "Shake really changes at fractional output times");

        CameraClipDocument.Clip math=modifier(CameraClipDocument.Kind.MATH);
        CameraClipDocument expression=with(math);
        near(expression.sample(5,ZERO).yaw,5D,
                "Math defaults to oscillating camera yaw at quarter-second");
        near(expression.sample(15,ZERO).yaw,-5D,
                "Math follows negative sine in the second half-second");
        PreviewCameraPose base=new PreviewCameraPose(
                8,0,0,17,0,0,70,0.05F,512F);
        near(expression.sample(5,base).yaw,22D,
                "Math reads underlying layer yaw without replacing it");

        CameraClipDocument.Clip orbit=modifier(CameraClipDocument.Kind.ORBIT);
        CameraClipDocument orbital=with(orbit);
        near(orbital.sample(0,ZERO).x,0D,
                "Orbit no-target start stays on camera pivot");
        ok(orbital.sample(10,ZERO).x>1.0D,
                "Orbit no-target moves with yawSpeed");
        near(orbital.sample(10,ZERO).yaw,20D,
                "Orbit looks back at pivot through its rotation");
        near(orbital.sample(10.5D,ZERO,null).yaw,21D,
                "Orbit animates at precise sub-tick camera timestamp");
        orbit.settings.put("atBlock","true");
        orbit.settings.put("block","10,0,0");
        ok(orbital.sample(0,ZERO).x>0D,
                "Orbit can use a manually specified world pivot");

        CameraClipDocument.Clip zoom=modifier(
                CameraClipDocument.Kind.DOLLY_ZOOM);
        CameraClipDocument dolly=with(zoom);
        near(dolly.sample(0,ZERO).fov,70D,
                "Dolly zoom initial FOV");
        near(dolly.sample(20,ZERO).fov,57.5D,
                "Dolly zoom FOV interpolates over its duration");
        near(dolly.sample(40,ZERO).fov,45D,
                "Dolly zoom final FOV");
        ok(dolly.sample(20,ZERO).z<0D,
                "Narrower Dolly Zoom FOV moves camera backwards");

        CameraClipDocument.Clip look=modifier(CameraClipDocument.Kind.LOOK);
        CameraClipDocument aiming=with(look);
        PreviewCameraPose viewpoint=new PreviewCameraPose(
                10,3,0,0,0,0,70,0.05F,512F);
        ok(aiming.sample(0,viewpoint).yaw>45D,
                "Default Look aims at an actual editable block coordinate");
        look.settings.put("atBlock","false");
        look.settings.put("selector","person");
        CameraClipDocument.TargetResolver actor=new CameraClipDocument.TargetResolver(){
            public CameraClipDocument.Target resolve(String id,int tick)
            {
                return "person".equals(id)
                    ?new CameraClipDocument.Target(0,3,10,0,0):null;
            }
        };
        near(aiming.sample(0,ZERO,actor).yaw,0D,
                "Look follows actor at selected time");

        CameraClipDocument.Clip drag=modifier(CameraClipDocument.Kind.DRAG);
        CameraClipDocument smooth=moving();
        ok(smooth.add(drag),"Drag on higher layer");
        PreviewCameraPose unsmoothed=smooth.clips().get(0).keys.isEmpty()
                ?ZERO:CameraClipChannels.sample(smooth.clips().get(0),20,ZERO);
        PreviewCameraPose filtered=smooth.sample(20,ZERO);
        ok(filtered.x>0D&&filtered.x<unsmoothed.x,
                "Drag smooths the lower camera without hiding it");
        near(filtered.x,smooth.sample(20,ZERO).x,
                "Drag is deterministic after arbitrary seeks");
        ok(Double.isFinite(smooth.sample(40.5D,ZERO,null).x),
                "Long Drag integrates fractional time with finite result");

        CameraClipDocument remapped=moving();
        CameraClipDocument.Clip remapper=modifier(
                CameraClipDocument.Kind.REMAPPER);
        ok(remapped.add(remapper),"Remapper above a moving path");
        near(remapped.sample(10,ZERO).x,800D/41D,
                "Remapper changes underlying camera time");
        near(remapped.sample(10.5D,ZERO,null).x,840D/41D,
                "Remapper preserves sub-tick time without rounding");

        CameraClipDocument stacked=moving();
        CameraClipDocument.Clip move=modifier(
                CameraClipDocument.Kind.TRANSLATE);
        CameraClipDocument.Clip tilt=modifier(CameraClipDocument.Kind.ANGLE);
        move.layer=1;tilt.layer=2;
        ok(stacked.add(move)&&stacked.add(tilt),
                "Path + Translate + Angle stack");
        near(stacked.sample(5,ZERO).x,6D,
                "Stacked translation follows authored lower path");
        near(stacked.sample(5,ZERO).yaw,5D,
                "Stacked Angle doesn't discard Path or Translate");
        near(stacked.sample(5.5D,ZERO,null).x,6.5D,
                "Stacked modifiers evaluated at actual sub-tick time");

        // Settings are persistently saved in scene JSON.
        CameraClipDocument reloaded=new CameraClipDocument();
        reloaded.readJson(stacked.toJson());
        near(reloaded.sample(5,ZERO).x,6D,
                "Modified camera stack survives scene roundtrip");
        System.out.println("CAMERA all 9 modifier behaviors / 60 FPS PASS");
    }
}

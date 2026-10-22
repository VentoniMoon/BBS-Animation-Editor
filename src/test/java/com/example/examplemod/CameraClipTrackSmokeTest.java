package com.example.examplemod;
import net.minecraft.nbt.NBTTagCompound;
public class CameraClipTrackSmokeTest
{
    private static void check(boolean b,String message)
    {if(!b)throw new AssertionError(message);}
    private static void near(double v,double expected,String label)
    {if(Math.abs(v-expected)>0.00001)throw new AssertionError(label+" "+v+" != "+expected);}
    private static PreviewCameraPose pose(double x)
    {return new PreviewCameraPose(x,70,0,0,0,0,60,0.05F,512F);}
    public static void main(String[] args)
    {
        check(CameraClipTrack.Type.values().length==20,"expected all 20 BBS FS clip types");
        CameraClipTrack track=new CameraClipTrack();
        CameraClipTrack.Clip base=new CameraClipTrack.Clip(CameraClipTrack.Type.IDLE,pose(0));
        base.duration=90;
        track.add(base);
        CameraClipTrack.Clip move=new CameraClipTrack.Clip(CameraClipTrack.Type.TRANSLATE,pose(20));
        move.layer=1;move.tick=0;move.duration=90;
        near(move.value("x",-1),0,"translate defaults zero, not camera world pos");
        move.values.put("x",5D);track.add(move);
        near(track.sample(20,pose(0)).x,5,"upper layer translation");
        track.remove(move);

        CameraClipTrack.Clip keys=new CameraClipTrack.Clip(CameraClipTrack.Type.KEYFRAME,pose(0));
        keys.tick=5;keys.duration=40;keys.keys.clear();
        keys.keys.put(0,pose(0));keys.keys.put(20,pose(10));
        track.add(keys);
        near(track.sample(15,pose(0)).x,5,"interpolated internal key");
        near(track.sample(15.5,pose(0)).x,5.25,"fractional internal key");
        check(track.conflicts(null,0,10,5),"overlapping same-layer clip forbidden");
        check(!track.conflicts(null,2,10,5),"different layers may overlap");

        NBTTagCompound saved=track.toNBT();
        CameraClipTrack restored=new CameraClipTrack();
        restored.fromNBT(saved);
        check(restored.all().size()==2,"round-trip clip count");
        check(restored.all().get(1).keys.size()==2,"round-trip internal keys");
        near(restored.sample(15,pose(0)).x,5,"round-trip camera pose");

        CameraClipTrack actors=new CameraClipTrack();
        CameraClipTrack.Clip tracker=new CameraClipTrack.Clip(CameraClipTrack.Type.TRACKER,pose(0));
        tracker.duration=20;tracker.strings.put("target","Hero");actors.add(tracker);
        near(actors.sample(4,pose(0),new EditorSceneState()).x,10,
            "stable actor ID target capture");
        CameraClipTrack.Clip orbit=new CameraClipTrack.Clip(CameraClipTrack.Type.ORBIT,pose(0));
        orbit.layer=1;orbit.duration=40;orbit.values.put("radius",2D);
        orbit.strings.put("target","Hero");actors.add(orbit);
        PreviewCameraPose o=actors.sample(10,pose(0),new EditorSceneState());
        check(Double.isFinite(o.x)&&Double.isFinite(o.z),"actor orbit finite");

        CameraClipTrack.Clip remap=new CameraClipTrack.Clip(CameraClipTrack.Type.REMAPPER,pose(0));
        remap.layer=2;remap.duration=40;
        remap.values.put("speed",0.5D);
        actors.add(remap);
        PreviewCameraPose a=actors.sample(6,pose(0),new EditorSceneState());
        check(Double.isFinite(a.x),"remapper must terminate below its layer");

        System.out.println("CAMERA 20-type layer/NBT/keys/target/Remapper PASS");
    }
}

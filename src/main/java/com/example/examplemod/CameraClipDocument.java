package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scene-wide CAMERA timeline, independent of the selected Blockbuster actor.
 * All BBS FS 1.12.2 clip types have a stable serializable identity. Evaluators
 * are opt-in: a registered type does not imply that an unsupported operation
 * silently produces a valid rendered result.
 */
public final class CameraClipDocument
{
    public enum Family { OVERWRITE, MODIFIER, MISC }
    public enum Kind
    {
        IDLE("View",Family.OVERWRITE,0xFF249670,false),
        DOLLY("Dolly",Family.OVERWRITE,0xFFFFA500,false),
        PATH("Path",Family.OVERWRITE,0xFF7338AD,true),
        KEYFRAME("Keyframe",Family.OVERWRITE,0xFFBE3B86,true),
        TRACKER("Tracker",Family.OVERWRITE,0xFF47BFC9,false),
        SPLINE("Spline",Family.OVERWRITE,0xFF4B88CB,false),
        TRANSLATE("Translate",Family.MODIFIER,0xFF4BA03E,false),
        ANGLE("Angle",Family.MODIFIER,0xFFD77A0A,false),
        DRAG("Drag",Family.MODIFIER,0xFF4BAFF7,false),
        SHAKE("Shake",Family.MODIFIER,0xFF159E64,false),
        MATH("Math",Family.MODIFIER,0xFF7532A5,false),
        REMAPPER("Remapper",Family.MODIFIER,0xFF555555,true),
        LOOK("Look",Family.MODIFIER,0xFF197FFF,false),
        ORBIT("Orbit",Family.MODIFIER,0xFFD82253,false),
        DOLLY_ZOOM("Dolly Zoom",Family.MODIFIER,0xFF7D56C9,false),
        AUDIO("Audio",Family.MISC,0xFFFFC825,false),
        CURVE("Curve",Family.MISC,0xFFFF1493,true),
        SUBTITLE("Subtitle",Family.MISC,0xFF888899,false),
        IMAGE("Image",Family.MISC,0xFF5278CD,false),
        VIDEO("Video",Family.MISC,0xFFD21F3C,false);

        public final String label;
        public final Family family;
        public final int color;
        public final boolean keyed;
        Kind(String label, Family family, int color, boolean keyed)
        {
            this.label=label; this.family=family; this.color=color; this.keyed=keyed;
        }
    }

    public static final class Key
    {
        public int tick;
        public double x,y,z;
        public float yaw,pitch,roll,fov;
        public Key(int tick, PreviewCameraPose p)
        {
            this.tick=tick; this.x=p.x; this.y=p.y; this.z=p.z;
            this.yaw=p.yaw; this.pitch=p.pitch; this.roll=p.roll; this.fov=p.fov;
        }
        public Key copy()
        {
            Key c=new Key(tick,new PreviewCameraPose(x,y,z,yaw,pitch,roll,fov,0.05F,512F));
            return c;
        }
    }

    public static final class Clip
    {
        public Kind kind;
        public String title;
        public int tick, duration, layer;
        public boolean enabled=true;
        public final List<Key> keys=new ArrayList<Key>();
        /** Type-dependent settings, serialized separately from camera keys. */
        public final Map<String,String> settings=new LinkedHashMap<String,String>();
        public Clip(Kind kind,int tick,int duration,int layer)
        {
            this.kind=kind; this.title=kind.label; this.tick=Math.max(0,tick);
            this.duration=Math.max(1,duration); this.layer=Math.max(0,layer);
        }
        public boolean contains(int frame)
        {
            return frame>=tick && frame<tick+duration;
        }
        public Clip copy()
        {
            Clip c=new Clip(kind,tick,duration,layer);
            c.title=title;c.enabled=enabled;c.settings.putAll(settings);
            for(Key k:keys)c.keys.add(k.copy());
            return c;
        }
    }

    private final List<Clip> clips=new ArrayList<Clip>();
    private Clip selected;
    public List<Clip> clips(){return Collections.unmodifiableList(clips);}
    public Clip selected(){return selected;}
    public void select(Clip clip){selected=clip;}
    public boolean add(Clip clip)
    {
        if(clip==null||overlaps(clip,null))return false;
        clips.add(clip);selected=clip;return true;
    }
    public boolean overlaps(Clip clip,Clip except)
    {
        for(Clip other:clips)
            if(other!=except&&other.layer==clip.layer&&clip.tick<other.tick+other.duration
                 &&other.tick<clip.tick+clip.duration)return true;
        return false;
    }
    public boolean move(Clip clip,int tick,int layer)
    {
        if(!clips.contains(clip))return false;
        int oldTick=clip.tick,oldLayer=clip.layer;
        clip.tick=Math.max(0,tick);clip.layer=Math.max(0,layer);
        if(overlaps(clip,clip)){clip.tick=oldTick;clip.layer=oldLayer;return false;}
        return true;
    }
    public boolean remove(Clip clip)
    {
        if(!clips.remove(clip))return false;
        if(selected==clip)selected=null;
        return true;
    }
    public Clip hit(int frame,int layer)
    {
        for(Clip clip:clips)if(clip.layer==layer&&clip.contains(frame))return clip;
        return null;
    }
    public void keyAt(Clip clip,int absoluteFrame,PreviewCameraPose pose)
    {
        if(clip==null||!clip.kind.keyed||!clip.contains(absoluteFrame)||pose==null)return;
        int relative=absoluteFrame-clip.tick;
        for(int i=0;i<clip.keys.size();i++)if(clip.keys.get(i).tick==relative){clip.keys.set(i,new Key(relative,pose));return;}
        clip.keys.add(new Key(relative,pose));
        Collections.sort(clip.keys,new Comparator<Key>(){
            @Override public int compare(Key a,Key b){return Integer.compare(a.tick,b.tick);}
        });
    }
    /** These are the currently supported pose-producing clip types. */
    public PreviewCameraPose sample(int frame,PreviewCameraPose fallback)
    {
        PreviewCameraPose result=fallback;
        List<Clip> active=new ArrayList<Clip>();
        for(Clip clip:clips)if(clip.enabled&&clip.contains(frame))active.add(clip);
        Collections.sort(active,new Comparator<Clip>(){
            @Override public int compare(Clip a,Clip b){return Integer.compare(a.layer,b.layer);}
        });
        for(Clip clip:active)
        {
            if((clip.kind==Kind.KEYFRAME||clip.kind==Kind.PATH)&&!clip.keys.isEmpty())
            {
                int local=frame-clip.tick;
                Key from=clip.keys.get(0),to=from;
                for(Key k:clip.keys)
                {
                    if(k.tick<=local)from=k;
                    if(k.tick>=local){to=k;break;}
                }
                double t=from==to?0D:(double)(local-from.tick)/(to.tick-from.tick);
                result=new PreviewCameraPose(
                    from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t,from.z+(to.z-from.z)*t,
                    (float)(from.yaw+(to.yaw-from.yaw)*t),
                    (float)(from.pitch+(to.pitch-from.pitch)*t),
                    (float)(from.roll+(to.roll-from.roll)*t),
                    (float)(from.fov+(to.fov-from.fov)*t),0.05F,512F);
            }
            // Other clip types are catalogued but require explicit, faithful
            // implementation of their BBS FS semantics before affecting render.
        }
        return result;
    }
}

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
        SPLINE("Spline",Family.OVERWRITE,0xFF4B88CB,true),
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
        IMAGE("Image",Family.MISC,0xFF5278CD,true),
        VIDEO("Video",Family.MISC,0xFFD21F3C,true);

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
        /** Channel mask: x=1,y=2,z=4,yaw=8,pitch=16,roll=32,fov=64. */
        public int mask=127;
        /** Interpolation following this key; empty delegates to clip setting. */
        public String interpolation="";
        /** IMAGE/VIDEO visual transform channels, independent from camera pose. */
        public final Map<String,Double> media=new LinkedHashMap<String,Double>();
        public Key(int tick, PreviewCameraPose p)
        {
            this.tick=tick; this.x=p.x; this.y=p.y; this.z=p.z;
            this.yaw=p.yaw; this.pitch=p.pitch; this.roll=p.roll; this.fov=p.fov;
        }
        public Key copy()
        {
            Key c=new Key(tick,new PreviewCameraPose(x,y,z,yaw,pitch,roll,fov,0.05F,512F));
            c.mask=this.mask;
            c.interpolation=this.interpolation;
            c.media.putAll(this.media);
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

    /** JSON is editor-side metadata; never modify Blockbuster scene records. */
    public com.google.gson.JsonObject toJson()
    {
        com.google.gson.JsonObject root=new com.google.gson.JsonObject();
        root.addProperty("version",1);
        com.google.gson.JsonArray items=new com.google.gson.JsonArray();
        for(Clip clip:clips)items.add(clipToJson(clip));
        root.add("clips",items);
        return root;
    }
    public static com.google.gson.JsonObject clipToJson(Clip clip)
    {
        com.google.gson.JsonObject j=new com.google.gson.JsonObject();
        j.addProperty("kind",clip.kind.name());
        j.addProperty("title",clip.title);
        j.addProperty("tick",clip.tick);
        j.addProperty("duration",clip.duration);
        j.addProperty("layer",clip.layer);
        j.addProperty("enabled",clip.enabled);
        com.google.gson.JsonObject settings=new com.google.gson.JsonObject();
        for(Map.Entry<String,String> e:clip.settings.entrySet())
            settings.addProperty(e.getKey(),e.getValue());
        j.add("settings",settings);
        com.google.gson.JsonArray keys=new com.google.gson.JsonArray();
        for(Key key:clip.keys)
        {
            com.google.gson.JsonObject k=new com.google.gson.JsonObject();
            k.addProperty("tick",key.tick);
            k.addProperty("x",key.x);k.addProperty("y",key.y);k.addProperty("z",key.z);
            k.addProperty("yaw",key.yaw);k.addProperty("pitch",key.pitch);
            k.addProperty("roll",key.roll);k.addProperty("fov",key.fov);
            k.addProperty("mask",key.mask);
            k.addProperty("interpolation",key.interpolation);
            if(!key.media.isEmpty())
            {
                com.google.gson.JsonObject visual=new com.google.gson.JsonObject();
                for(Map.Entry<String,Double> item:key.media.entrySet())
                    if(Double.isFinite(item.getValue()))
                        visual.addProperty(item.getKey(),item.getValue());
                k.add("media",visual);
            }
            keys.add(k);
        }
        j.add("keys",keys);
        return j;
    }
    public static Clip clipFromJson(com.google.gson.JsonObject j)
    {
        Kind kind=Kind.valueOf(j.get("kind").getAsString());
        Clip clip=new Clip(kind,j.get("tick").getAsInt(),
                j.get("duration").getAsInt(),j.get("layer").getAsInt());
        if(j.has("title"))clip.title=j.get("title").getAsString();
        if(j.has("enabled"))clip.enabled=j.get("enabled").getAsBoolean();
        if(j.has("settings"))for(Map.Entry<String,com.google.gson.JsonElement> e:
                j.getAsJsonObject("settings").entrySet())
            clip.settings.put(e.getKey(),e.getValue().getAsString());
        if(j.has("keys"))for(com.google.gson.JsonElement entry:j.getAsJsonArray("keys"))
        {
            com.google.gson.JsonObject k=entry.getAsJsonObject();
            PreviewCameraPose pose=new PreviewCameraPose(
                    k.get("x").getAsDouble(),k.get("y").getAsDouble(),
                    k.get("z").getAsDouble(),k.get("yaw").getAsFloat(),
                    k.get("pitch").getAsFloat(),k.get("roll").getAsFloat(),
                    k.get("fov").getAsFloat(),0.05F,512F);
            Key key=new Key(k.get("tick").getAsInt(),pose);
            if(k.has("mask"))key.mask=k.get("mask").getAsInt()&127;
            if(k.has("interpolation"))
                key.interpolation=k.get("interpolation").getAsString();
            if(k.has("media")&&
                    k.get("media") instanceof com.google.gson.JsonObject)
                for(Map.Entry<String,com.google.gson.JsonElement> item:
                        k.getAsJsonObject("media").entrySet())
                {
                    try
                    {
                        double value=item.getValue().getAsDouble();
                        if(Double.isFinite(value)&&
                                java.util.Arrays.asList(
                                        CameraMediaAnimation.FIELDS)
                                        .contains(item.getKey()))
                            key.media.put(item.getKey(),value);
                    }
                    catch(RuntimeException ignored){}
                }
            if(key.tick>=0&&key.tick<clip.duration)clip.keys.add(key);
        }
        Collections.sort(clip.keys,new Comparator<Key>(){
            @Override public int compare(Key a,Key b){return Integer.compare(a.tick,b.tick);}
        });
        return clip;
    }
    public void readJson(com.google.gson.JsonObject root)
    {
        List<Clip> loaded=new ArrayList<Clip>();
        if(root!=null&&root.has("clips"))for(com.google.gson.JsonElement entry:
                root.getAsJsonArray("clips"))
        {
            try
            {
                Clip clip=clipFromJson(entry.getAsJsonObject());
                boolean overlaps=false;
                for(Clip prior:loaded)
                    if(prior.layer==clip.layer&&clip.tick<prior.tick+prior.duration
                            &&prior.tick<clip.tick+clip.duration)
                    {overlaps=true;break;}
                if(!overlaps)loaded.add(clip);
            }
            catch(RuntimeException error)
            {
                System.err.println("[BBS CAMERA] Skipping invalid clip: "+error);
            }
        }
        clips.clear();clips.addAll(loaded);selected=null;
    }
    public void clear(){clips.clear();selected=null;}

    private static double numeric(Clip clip,String id,double fallback)
    {
        try {return Double.parseDouble(clip.settings.get(id));}
        catch(Exception e){return fallback;}
    }
    private static double[] point(Clip clip,String id)
    {
        double[] out={0D,0D,0D};
        String text=clip.settings.get(id);
        if(text==null)return out;
        String[] pieces=text.split(",");
        for(int i=0;i<Math.min(3,pieces.length);i++)
            try {out[i]=Double.parseDouble(pieces[i].trim());}
            catch(NumberFormatException ignored){}
        return out;
    }

    public static final class Target
    {
        public final double x,y,z;
        public final float yaw,pitch;
        public Target(double x,double y,double z,float yaw,float pitch)
        {
            this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;
        }
    }
    /** Stable scene actor IDs, never transient UI-selected actor indexes. */
    public interface TargetResolver
    {
        Target resolve(String actorId,int frame);
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
    /**
     * Resize either timeline boundary without moving the opposite edge.
     * Keys stay relative to the clip start; reject trims that would silently
     * discard keys. No changes persist when a same-layer clip overlaps.
     *
     * @param left true for the start handle; false for the end handle
     * @param boundary absolute scene frame of the grabbed edge
     */
    public boolean trim(Clip clip,boolean left,int boundary)
    {
        if(clip==null||!clips.contains(clip))return false;
        int oldTick=clip.tick,oldDuration=clip.duration;
        long oldEnd=(long)oldTick+oldDuration;
        long start=left?Math.max(0L,Math.min(
                (long)boundary,oldEnd-1L)):oldTick;
        long end=left?oldEnd:Math.max(start+1L,(long)boundary);
        if(end>Integer.MAX_VALUE)return false;
        int newDuration=(int)(end-start);
        for(Key key:clip.keys)
            if(key.tick<0||key.tick>=newDuration)return false;
        if(start==oldTick&&newDuration==oldDuration)return false;
        clip.tick=(int)start;
        clip.duration=newDuration;
        if(overlaps(clip,clip))
        {
            clip.tick=oldTick;
            clip.duration=oldDuration;
            return false;
        }
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
        Key created=new Key(relative,pose);
        if(CameraMediaAnimation.supports(clip))
            CameraMediaAnimation.capture(clip,created,absoluteFrame);
        else if(clip.kind==Kind.CURVE)
        {
            created.x=settingNumber(clip,"value",0.25D);
            for(Key previous:clip.keys)
                if(previous.tick==relative)
                {
                    created.x=previous.x;
                    created.interpolation=previous.interpolation;
                    break;
                }
        }
        else if(clip.kind==Kind.REMAPPER)
        {
            // Remapper's sole channel stores SOURCE TIME in key.x,
            // not camera X. Capture the current mapping under the cursor
            // when adding a time key, so it doesn't jump unexpectedly.
            double from=settingNumber(clip,"from",clip.tick);
            double to=settingNumber(clip,"to",clip.tick+clip.duration);
            double phase=CameraInterpolation.sample(
                    relative/(double)Math.max(1,clip.duration),
                    clip.settings.get("interp"));
            created.x=from+(to-from)*phase;
            for(Key previous:clip.keys)
                if(previous.tick==relative)
                {
                    created.x=previous.x;
                    created.interpolation=previous.interpolation;
                    break;
                }
        }
        for(int i=0;i<clip.keys.size();i++)
            if(clip.keys.get(i).tick==relative)
            {clip.keys.set(i,created);return;}
        clip.keys.add(created);
        Collections.sort(clip.keys,new Comparator<Key>(){
            @Override public int compare(Key a,Key b){return Integer.compare(a.tick,b.tick);}
        });
    }
    private static double settingNumber(Clip clip,String field,double fallback)
    {
        try
        {
            double v=Double.parseDouble(clip.settings.get(field));
            return Double.isFinite(v)?v:fallback;
        }
        catch(RuntimeException ignored){return fallback;}
    }

    /** These are the currently supported pose-producing clip types. */
    public PreviewCameraPose sample(int frame,PreviewCameraPose fallback)
    {
        return sample(frame,fallback,null);
    }
    public PreviewCameraPose sample(int frame,PreviewCameraPose fallback,
            TargetResolver resolver)
    {
        return sample((double)frame,fallback,resolver);
    }

    /** Continuous camera time used by both SHOT preview and offline MP4. */
    public PreviewCameraPose sample(double frame,PreviewCameraPose fallback,
            TargetResolver resolver)
    {
        return CameraClipEvaluator.evaluate(clips,frame,fallback,resolver);
    }
}

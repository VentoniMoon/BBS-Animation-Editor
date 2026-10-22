package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Scene-level CAMERA data. It never depends on the selected Blockbuster actor.
 *
 * BBS FS inspired model: clip layers are evaluated bottom (0) to top;
 * clips on a single layer cannot overlap; all clips have an independent local
 * time, envelope, enabled flag and serializable per-type settings.
 *
 * NOTE: this is native EDITOR data, not a BBS FS film file.  This keeps
 * existing Blockbuster .dat and actor animation timelines unaffected.
 */
public final class CameraClipTrack
{
    public enum Category { OVERWRITE, MODIFIERS, MISC }

    public enum Type
    {
        IDLE("View",Category.OVERWRITE,0xFF208866,false),
        DOLLY("Dolly",Category.OVERWRITE,0xFFD69530,false),
        PATH("Path",Category.OVERWRITE,0xFF713AB2,true),
        KEYFRAME("Keyframe",Category.OVERWRITE,0xFFB43A87,true),
        TRACKER("Tracker",Category.OVERWRITE,0xFF3DAEBD,false),
        SPLINE("Spline",Category.OVERWRITE,0xFF4086D6,true),
        TRANSLATE("Translate",Category.MODIFIERS,0xFF42943A,true),
        ANGLE("Angle",Category.MODIFIERS,0xFFCB8230,true),
        DRAG("Drag",Category.MODIFIERS,0xFF3C9ED4,false),
        SHAKE("Shake",Category.MODIFIERS,0xFF208766,true),
        MATH("Math",Category.MODIFIERS,0xFF6D4EB1,true),
        REMAPPER("Remapper",Category.MODIFIERS,0xFF555A64,true),
        LOOK("Look",Category.MODIFIERS,0xFF327BC7,false),
        ORBIT("Orbit",Category.MODIFIERS,0xFFC33A61,true),
        DOLLY_ZOOM("Dolly Zoom",Category.MODIFIERS,0xFF875BB4,false),
        AUDIO("Audio",Category.MISC,0xFFC39C31,false),
        CURVE("Curve",Category.MISC,0xFFC244A8,true),
        SUBTITLE("Subtitle",Category.MISC,0xFF81889A,false),
        IMAGE("Image",Category.MISC,0xFF4472B7,false),
        VIDEO("Video",Category.MISC,0xFFB93551,false);

        public final String label;
        public final Category category;
        public final int color;
        public final boolean keyed;

        Type(String label,Category category,int color,boolean keyed)
        {
            this.label=label;this.category=category;this.color=color;this.keyed=keyed;
        }
    }

    public static final class Clip
    {
        public Type type;
        public String title="";
        public int tick, duration=40, layer;
        public boolean enabled=true;
        public double fadeIn, fadeOut;
        /** settings used for native clip evaluation; keys are stable serialization ids */
        public final LinkedHashMap<String,Double> values=new LinkedHashMap<String,Double>();
        /** resource paths, target identifiers and expression strings */
        public final LinkedHashMap<String,String> strings=new LinkedHashMap<String,String>();
        /** clip-local keys, never global scene times */
        public final TreeMap<Integer,PreviewCameraPose> keys=new TreeMap<Integer,PreviewCameraPose>();
        public PreviewCameraPose rest;

        public Clip(Type type,PreviewCameraPose pose)
        {
            this.type=type;
            this.title=type.label;
            this.rest=pose;
            values.put("x",pose.x);values.put("y",pose.y);values.put("z",pose.z);
            values.put("yaw",(double)pose.yaw);values.put("pitch",(double)pose.pitch);
            values.put("roll",(double)pose.roll);values.put("fov",(double)pose.fov);
            switch(type)
            {
            case DOLLY: values.put("distance",4D);break;
            case DRAG: values.put("strength",0.6D);break;
            case SHAKE:
                values.put("amplitude",0.15D);values.put("frequency",2D);
                values.put("rotation",1.2D);break;
            case ORBIT:
                values.put("radius",4D);values.put("degrees",360D);break;
            case DOLLY_ZOOM:values.put("focus",4D);break;
            case AUDIO:values.put("volume",1D);values.put("offset",0D);break;
            case IMAGE:case VIDEO:
                values.put("screenX",0.5D);values.put("screenY",0.5D);
                values.put("scale",1D);values.put("alpha",1D);break;
            case SUBTITLE:
                values.put("screenX",0.5D);values.put("screenY",0.9D);break;
            default: break;
            }
            if(type.keyed)keys.put(0,pose);
        }

        public double value(String key,double otherwise)
        {
            Double v=values.get(key);
            return v==null?otherwise:v;
        }

        public String text(String key){String s=strings.get(key);return s==null?"":s;}

        public boolean inside(double frame){return enabled&&frame>=tick&&frame<tick+(double)duration;}

        public double alpha(double local)
        {
            double a=fadeIn>0?Math.min(1,local/fadeIn):1;
            double b=fadeOut>0?Math.min(1,(duration-local)/fadeOut):1;
            return Math.max(0,Math.min(a,b));
        }

        public Clip copy()
        {
            Clip c=new Clip(type,rest);
            c.title=title;c.tick=tick;c.duration=duration;c.layer=layer;c.enabled=enabled;
            c.fadeIn=fadeIn;c.fadeOut=fadeOut;
            c.values.clear();c.values.putAll(values);
            c.strings.putAll(strings);c.keys.clear();c.keys.putAll(keys);
            return c;
        }

        public NBTTagCompound toNBT()
        {
            NBTTagCompound tag=new NBTTagCompound();
            tag.setString("Type",type.name());
            tag.setString("Title",title);
            tag.setInteger("Start",tick);tag.setInteger("Duration",duration);
            tag.setInteger("Layer",layer);tag.setBoolean("Enabled",enabled);
            tag.setDouble("FadeIn",fadeIn);tag.setDouble("FadeOut",fadeOut);
            tag.setTag("Rest",poseTag(rest));
            NBTTagCompound n=new NBTTagCompound();
            for(Map.Entry<String,Double> v:values.entrySet())n.setDouble(v.getKey(),v.getValue());
            tag.setTag("Values",n);
            NBTTagCompound str=new NBTTagCompound();
            for(Map.Entry<String,String> v:strings.entrySet())str.setString(v.getKey(),v.getValue());
            tag.setTag("Strings",str);
            NBTTagList list=new NBTTagList();
            for(Map.Entry<Integer,PreviewCameraPose> e:keys.entrySet())
            {
                NBTTagCompound k=poseTag(e.getValue());
                k.setInteger("At",e.getKey());list.appendTag(k);
            }
            tag.setTag("Keys",list);
            return tag;
        }

        public static Clip fromNBT(NBTTagCompound tag)
        {
            Type type;
            try{type=Type.valueOf(tag.getString("Type"));}catch(Exception ignored){return null;}
            PreviewCameraPose rest=readPose(tag.getCompoundTag("Rest"),null);
            if(rest==null)rest=DEFAULT_POSE;
            Clip c=new Clip(type,rest);
            c.title=tag.getString("Title");
            if(c.title.isEmpty())c.title=type.label;
            c.tick=Math.max(0,tag.getInteger("Start"));
            c.duration=Math.max(1,tag.getInteger("Duration"));
            c.layer=Math.max(0,tag.getInteger("Layer"));
            c.enabled=!tag.hasKey("Enabled")||tag.getBoolean("Enabled");
            c.fadeIn=Math.max(0,tag.getDouble("FadeIn"));
            c.fadeOut=Math.max(0,tag.getDouble("FadeOut"));
            NBTTagCompound n=tag.getCompoundTag("Values");
            for(String k:n.getKeySet())if(n.hasKey(k,99)&&Double.isFinite(n.getDouble(k)))
                c.values.put(k,n.getDouble(k));
            NBTTagCompound str=tag.getCompoundTag("Strings");
            for(String k:str.getKeySet())c.strings.put(k,str.getString(k));
            c.keys.clear();
            NBTTagList list=tag.getTagList("Keys",10);
            for(int i=0;i<list.tagCount();i++)
            {
                NBTTagCompound key=list.getCompoundTagAt(i);
                PreviewCameraPose pose=readPose(key,rest);
                if(pose!=null)c.keys.put(Math.max(0,key.getInteger("At")),pose);
            }
            return c;
        }
    }

    public static final PreviewCameraPose DEFAULT_POSE=
        new PreviewCameraPose(0,70,0,0,0,0,60,0.05F,512F);

    private final List<Clip> clips=new ArrayList<Clip>();
    public List<Clip> all(){return Collections.unmodifiableList(clips);}
    public void clear(){clips.clear();}
    public void add(Clip c){if(c!=null)clips.add(c);}
    public void remove(Clip c){clips.remove(c);}
    public int maxLayer()
    {
        int n=0;for(Clip c:clips)n=Math.max(n,c.layer);return n;
    }
    public Clip at(int layer,double frame)
    {
        for(Clip c:clips)if(c.layer==layer&&c.inside(frame))return c;
        return null;
    }
    public boolean conflicts(Clip moving,int layer,int start,int duration)
    {
        for(Clip c:clips)
            if(c!=moving&&c.layer==layer&&start<c.tick+c.duration&&c.tick<start+duration)
                return true;
        return false;
    }
    public NBTTagCompound toNBT()
    {
        NBTTagCompound t=new NBTTagCompound();NBTTagList l=new NBTTagList();
        for(Clip c:clips)l.appendTag(c.toNBT());t.setTag("Clips",l);return t;
    }
    public void fromNBT(NBTTagCompound tag)
    {
        clear();NBTTagList l=tag.getTagList("Clips",10);
        for(int i=0;i<l.tagCount();i++)
        {
            Clip c=Clip.fromNBT(l.getCompoundTagAt(i));
            if(c!=null)add(c);
        }
    }

    private static NBTTagCompound poseTag(PreviewCameraPose p)
    {
        NBTTagCompound t=new NBTTagCompound();
        t.setDouble("x",p.x);t.setDouble("y",p.y);t.setDouble("z",p.z);
        t.setFloat("yaw",p.yaw);t.setFloat("pitch",p.pitch);t.setFloat("roll",p.roll);
        t.setFloat("fov",p.fov);return t;
    }
    private static PreviewCameraPose readPose(NBTTagCompound t,PreviewCameraPose fallback)
    {
        try
        {
            if(!t.hasKey("fov"))return fallback;
            return new PreviewCameraPose(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"),
                t.getFloat("yaw"),t.getFloat("pitch"),t.getFloat("roll"),t.getFloat("fov"),
                0.05F,512F);
        }
        catch(IllegalArgumentException e){return fallback;}
    }

    /**
     * BBS FS layer semantics: low layers establish the camera and high
     * layers transform its result. Evaluation is pure in (frame,clip data)
     * so nonsequential scrubbing remains reproducible.
     *
     * Advanced links (record bone matrices, shader channels, external video,
     * sound, expressions) are data-preserved but require their own runtimes.
     */
    public PreviewCameraPose sample(double frame,PreviewCameraPose fallback)
    {
        PreviewCameraPose current=fallback==null?DEFAULT_POSE:fallback;
        List<Clip> active=new ArrayList<Clip>();
        for(Clip c:clips)if(c.inside(frame))active.add(c);
        Collections.sort(active,Comparator.comparingInt(c->c.layer));
        for(Clip c:active)
        {
            double local=frame-c.tick;
            double a=c.alpha(local);
            if(a<=0)continue;
            current=apply(c,local,a,current);
        }
        return current;
    }

    private static PreviewCameraPose apply(Clip c,double local,double blend,PreviewCameraPose prev)
    {
        double progress=Math.max(0,Math.min(1,local/Math.max(1,c.duration)));
        PreviewCameraPose keyed=sampleKeys(c,local);
        double x=prev.x,y=prev.y,z=prev.z;
        float yaw=prev.yaw,pitch=prev.pitch,roll=prev.roll,fov=prev.fov;
        switch(c.type)
        {
        case IDLE: case KEYFRAME: case PATH: case SPLINE:
            if(c.type==Type.SPLINE)keyed=sampleKeys(c,smooth(local,c.duration));
            return PreviewCameraPose.interpolate(prev,keyed,blend);
        case DOLLY:
        {
            PreviewCameraPose base=c.rest;
            double yawRad=Math.toRadians(base.yaw),pitchRad=Math.toRadians(base.pitch);
            double dist=c.value("distance",4)*progress;
            x=base.x-Math.sin(yawRad)*Math.cos(pitchRad)*dist;
            y=base.y-Math.sin(pitchRad)*dist;
            z=base.z+Math.cos(yawRad)*Math.cos(pitchRad)*dist;
            return PreviewCameraPose.interpolate(prev,pose(x,y,z,base.yaw,base.pitch,base.roll,base.fov,prev),blend);
        }
        case TRANSLATE:
            x+=c.value("x",0)*blend;y+=c.value("y",0)*blend;z+=c.value("z",0)*blend;break;
        case ANGLE:
            yaw+=c.value("yaw",0)*blend;pitch+=c.value("pitch",0)*blend;
            roll+=c.value("roll",0)*blend;fov+=c.value("fov",0)*blend;break;
        case SHAKE:
        {
            double amp=c.value("amplitude",0.15)*blend;
            double f=c.value("frequency",2);
            double rad=c.value("rotation",1.2)*blend;
            x+=Math.sin(local*f*.35+0.1)*amp;
            y+=Math.sin(local*f*.67+1.7)*amp;
            z+=Math.sin(local*f*.41+4.4)*amp;
            yaw+=Math.sin(local*f*.53+0.2)*rad;
            roll+=Math.sin(local*f*.4+2.1)*rad;break;
        }
        case ORBIT:
        {
            double radius=c.value("radius",4);
            double radians=Math.toRadians(c.value("degrees",360)*progress+c.value("yaw",0));
            double cx=c.value("x",prev.x),cy=c.value("y",prev.y),cz=c.value("z",prev.z);
            x=cx+Math.sin(radians)*radius;y=cy+c.value("height",0);z=cz+Math.cos(radians)*radius;
            yaw=(float)Math.toDegrees(Math.atan2(cx-x,cz-z));
            pitch=(float)-Math.toDegrees(Math.atan2(cy-y,Math.hypot(cx-x,cz-z)));
            return PreviewCameraPose.interpolate(prev,pose(x,y,z,yaw,pitch,roll,fov,prev),blend);
        }
        case LOOK:
        {
            double tx=c.value("x",prev.x),ty=c.value("y",prev.y),tz=c.value("z",prev.z+1);
            double dx=tx-x,dy=ty-y,dz=tz-z;
            if(dx*dx+dy*dy+dz*dz>1e-6)
            {
                yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
                pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
            }
            return PreviewCameraPose.interpolate(prev,pose(x,y,z,yaw,pitch,roll,fov,prev),blend);
        }
        case DOLLY_ZOOM:
        {
            double focus=Math.max(.01,c.value("focus",4));
            double newFov=Math.max(1,Math.min(170,c.value("fov",prev.fov)));
            double distance=focus*Math.tan(Math.toRadians(prev.fov/2))/Math.tan(Math.toRadians(newFov/2));
            double shift=(focus-distance)*blend;
            double yr=Math.toRadians(yaw),pr=Math.toRadians(pitch);
            x-=Math.sin(yr)*Math.cos(pr)*shift;
            y-=Math.sin(pr)*shift;
            z+=Math.cos(yr)*Math.cos(pr)*shift;
            fov=(float)newFov;break;
        }
        case DRAG:
            // Stateless smoothing of the lower-layer camera toward clip rest.
            return PreviewCameraPose.interpolate(prev,c.rest,
                Math.min(1,Math.max(0,c.value("strength",0.6)))*blend);
        case REMAPPER: case MATH: case TRACKER: case CURVE:
        case AUDIO: case SUBTITLE: case IMAGE: case VIDEO:
            // Data is preserved; specialized evaluator/render runtime is separate.
            break;
        }
        return pose(x,y,z,yaw,pitch,roll,fov,prev);
    }

    private static double smooth(double t,double duration)
    {
        double u=Math.max(0,Math.min(1,t/Math.max(1,duration)));
        return (u*u*(3-2*u))*duration;
    }
    private static PreviewCameraPose sampleKeys(Clip c,double local)
    {
        if(c.keys.isEmpty())return c.rest;
        int k=(int)Math.floor(local);
        Map.Entry<Integer,PreviewCameraPose> lo=c.keys.floorEntry(k);
        Map.Entry<Integer,PreviewCameraPose> hi=c.keys.ceilingEntry(k);
        if(lo==null)return c.keys.firstEntry().getValue();
        if(hi==null)return c.keys.lastEntry().getValue();
        if(lo.getKey().equals(hi.getKey()))return lo.getValue();
        double t=(local-lo.getKey())/(hi.getKey()-lo.getKey());
        return PreviewCameraPose.interpolate(lo.getValue(),hi.getValue(),t);
    }
    private static PreviewCameraPose pose(double x,double y,double z,
        float yaw,float pitch,float roll,float fov,PreviewCameraPose p)
    {
        return new PreviewCameraPose(x,y,z,yaw,pitch,roll,
            Math.max(1,Math.min(170,fov)),p.nearPlane,p.farPlane);
    }
}

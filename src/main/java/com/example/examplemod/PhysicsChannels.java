package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Independent PHYSICS channels, stored inside the existing CHARACTER
 * main track's universal keys for backward-compatible scene persistence.
 * PHYSICS owns its GUI; CHARACTER only retains legacy read compatibility.
 *
 * Impulses are events, not interpolated forces. Every key can contain
 * one impulse (later expandable to a list without changing the timeline).
 */
public final class PhysicsChannels
{
    public static final int RAGDOLL=0,IMPULSE=1,GRAVITY=2,WATER=3;
    public static final String[] LABELS={"Ragdoll","Impulse","Gravity","Water"};
    public static final String TAG="Ragdoll";
    private static final float DEFAULT_GRAVITY=9.81F;
    private PhysicsChannels(){}

    public static final class Impact
    {
        public String target="chest";
        public float x=0F,y=0F,z=-1F;
        public float strength=16F;
        // Offset relative to physical body center in model-local blocks.
        public float offsetX=0F,offsetY=0F,offsetZ=0F;
    }
    public static final class Fluid
    {
        public float amount,buoyancy=1.05F,drag=0.8F,angularDrag=0.65F;
        /** World-space height; disabled for saved scenes from Physics 2.0. */
        public float surfaceY=63F;
        public boolean surfaceEnabled;
        public Fluid copy()
        {
            Fluid out=new Fluid();
            out.amount=amount;out.buoyancy=buoyancy;
            out.drag=drag;out.angularDrag=angularDrag;
            out.surfaceY=surfaceY;out.surfaceEnabled=surfaceEnabled;
            return out;
        }
    }

    private static float safe(float value,float fallback,float low,float high)
    {
        return Float.isFinite(value)?Math.max(low,Math.min(high,value)):fallback;
    }

    public static CharacterKey at(CharacterTimelineController track,int frame)
    {
        if(track==null||frame<0)return null;
        CharacterTrack main=track.getTrackById("main");
        return main==null?null:main.getKeyAtFrame(frame);
    }
    private static CharacterKey ensure(CharacterTimelineController track,int frame)
    {
        if(track==null||frame<0)throw new IllegalArgumentException("Timeline/frame");
        CharacterTrack main=track.ensureMainTrack();
        CharacterKey key=main.getKeyAtFrame(frame);
        return key!=null?key:main.createKey(frame,CharacterKey.Type.CUSTOM);
    }
    private static NBTTagCompound data(CharacterKey key)
    {
        return key==null?new NBTTagCompound():key.getCompound(TAG);
    }
    private static void save(CharacterKey key,NBTTagCompound data)
    {
        key.setCompound(TAG,data);
    }
    public static boolean has(CharacterKey key,int row)
    {
        if(key==null)return false;
        NBTTagCompound n=data(key);
        switch(row)
        {
        case RAGDOLL:return CharacterRagdollController.keyWeight(key)>=0F;
        case IMPULSE:return n.hasKey("Impulse",10);
        case GRAVITY:return n.hasKey("Gravity",99);
        case WATER:return n.hasKey("Water",10);
        default:return false;
        }
    }

    public static void setWeight(CharacterTimelineController t,int frame,float value)
    {
        CharacterRagdollController.setWeight(t,frame,value);
    }
    public static void setGravity(CharacterTimelineController t,int frame,float gravity)
    {
        CharacterKey key=ensure(t,frame);
        NBTTagCompound n=data(key);
        n.setFloat("Gravity",safe(gravity,DEFAULT_GRAVITY,0F,100F));
        save(key,n);
    }
    public static void setWater(CharacterTimelineController t,int frame,Fluid fluid)
    {
        CharacterKey key=ensure(t,frame);
        NBTTagCompound n=data(key),w=new NBTTagCompound();
        Fluid f=fluid==null?new Fluid():fluid;
        w.setFloat("Amount",safe(f.amount,0F,0F,1F));
        w.setFloat("Buoyancy",safe(f.buoyancy,1.05F,0F,3F));
        w.setFloat("Drag",safe(f.drag,.8F,0F,5F));
        w.setFloat("AngularDrag",safe(f.angularDrag,.65F,0F,5F));
        w.setFloat("SurfaceY",safe(f.surfaceY,63F,-2048F,2048F));
        w.setBoolean("SurfaceEnabled",f.surfaceEnabled);
        n.setTag("Water",w);save(key,n);
    }
    public static void setImpulse(CharacterTimelineController t,int frame,Impact impulse)
    {
        CharacterKey key=ensure(t,frame);
        Impact i=impulse==null?new Impact():impulse;
        NBTTagCompound n=data(key),event=new NBTTagCompound();
        event.setString("Target",i.target==null?"chest":i.target);
        event.setFloat("X",safe(i.x,0F,-1F,1F));
        event.setFloat("Y",safe(i.y,0F,-1F,1F));
        event.setFloat("Z",safe(i.z,-1F,-1F,1F));
        event.setFloat("Strength",safe(i.strength,16F,0F,200F));
        event.setFloat("OffsetX",safe(i.offsetX,0F,-1.5F,1.5F));
        event.setFloat("OffsetY",safe(i.offsetY,0F,-1.5F,1.5F));
        event.setFloat("OffsetZ",safe(i.offsetZ,0F,-1.5F,1.5F));
        n.setTag("Impulse",event);save(key,n);
    }
    public static void clear(CharacterTimelineController t,int frame,int row)
    {
        CharacterKey key=at(t,frame);
        if(key==null)return;
        NBTTagCompound n=data(key);
        switch(row)
        {
        case RAGDOLL:n.removeTag("Weight");break;
        case IMPULSE:n.removeTag("Impulse");break;
        case GRAVITY:n.removeTag("Gravity");break;
        case WATER:n.removeTag("Water");break;
        default:return;
        }
        if(n.hasNoTags())key.removeData(TAG);
        else save(key,n);
        // Other CHARACTER content on the same key must survive.
        if(!key.hasCharacterData())
        {
            CharacterTrack main=t.getTrackById("main");
            if(main!=null)main.removeKey(key);
        }
    }
    /** Drag one physical channel without moving Morph/Skin/Animation keys. */
    public static void moveChannel(CharacterTimelineController t,
            int from,int to,int row)
    {
        if(t==null||from==to||to<0)return;
        CharacterKey original=at(t,from);
        if(!has(original,row))return;
        switch(row)
        {
        case RAGDOLL:
            setWeight(t,to,CharacterRagdollController.keyWeight(original));
            break;
        case IMPULSE:setImpulse(t,to,impulseAt(t,from));break;
        case GRAVITY:setGravity(t,to,
                data(original).getFloat("Gravity"));break;
        case WATER:
            NBTTagCompound w=data(original).getCompoundTag("Water");
            Fluid f=new Fluid();
            f.amount=w.getFloat("Amount");
            f.buoyancy=w.getFloat("Buoyancy");
            f.drag=w.getFloat("Drag");
            f.angularDrag=w.getFloat("AngularDrag");
            f.surfaceY=w.hasKey("SurfaceY",99)?w.getFloat("SurfaceY"):63F;
            f.surfaceEnabled=w.getBoolean("SurfaceEnabled");
            setWater(t,to,f);
            break;
        default:return;
        }
        clear(t,from,row);
    }

    public static Impact impulseAt(CharacterTimelineController t,int frame)
    {
        CharacterKey key=at(t,frame);
        if(!has(key,IMPULSE))return null;
        NBTTagCompound n=data(key).getCompoundTag("Impulse");
        Impact i=new Impact();
        i.target=n.getString("Target");
        if(i.target.isEmpty())i.target="chest";
        i.x=safe(n.getFloat("X"),0F,-1F,1F);
        i.y=safe(n.getFloat("Y"),0F,-1F,1F);
        i.z=safe(n.getFloat("Z"),-1F,-1F,1F);
        i.strength=safe(n.getFloat("Strength"),16F,0F,200F);
        i.offsetX=safe(n.getFloat("OffsetX"),0F,-1.5F,1.5F);
        i.offsetY=safe(n.getFloat("OffsetY"),0F,-1.5F,1.5F);
        i.offsetZ=safe(n.getFloat("OffsetZ"),0F,-1.5F,1.5F);
        return i;
    }
    public static Impact previousImpulse(CharacterTimelineController t,int frame)
    {
        if(t==null)return null;
        CharacterTrack main=t.getTrackById("main");
        if(main==null)return null;
        int previous=-1;
        for(CharacterKey key:main.getKeys())
            if(key!=null&&key.getFrame()<=frame&&
                    key.getFrame()>previous&&has(key,IMPULSE))
                previous=key.getFrame();
        return previous<0?null:impulseAt(t,previous);
    }

    private static float component(CharacterKey key,int channel,String field)
    {
        NBTTagCompound n=data(key);
        if(channel==GRAVITY)return n.getFloat("Gravity");
        if(channel==WATER)
        {
            NBTTagCompound w=n.getCompoundTag("Water");
            if("Amount".equals(field))return w.getFloat("Amount");
            if("Buoyancy".equals(field))return w.getFloat("Buoyancy");
            if("Drag".equals(field))return w.getFloat("Drag");
            if("AngularDrag".equals(field))return w.getFloat("AngularDrag");
            if("SurfaceY".equals(field))
                return w.hasKey("SurfaceY",99)?w.getFloat("SurfaceY"):63F;
            if("SurfaceEnabled".equals(field))
                return w.getBoolean("SurfaceEnabled")?1F:0F;
        }
        return 0F;
    }
    private static float sample(CharacterTimelineController t,int frame,
            int channel,String field,float fallback)
    {
        if(t==null||frame<0)return fallback;
        CharacterTrack main=t.getTrackById("main");
        if(main==null)return fallback;
        CharacterKey prev=null,next=null;
        for(CharacterKey key:main.getKeys())
        {
            if(key==null||!has(key,channel))continue;
            if(key.getFrame()<=frame&&
                    (prev==null||key.getFrame()>prev.getFrame()))prev=key;
            else if(key.getFrame()>frame&&
                    (next==null||key.getFrame()<next.getFrame()))next=key;
        }
        if(prev==null)return fallback;
        float a=component(prev,channel,field);
        if(next==null)return a;
        float alpha=(frame-prev.getFrame())/
                (float)(next.getFrame()-prev.getFrame());
        return a+(component(next,channel,field)-a)*alpha;
    }
    public static float gravityAt(CharacterTimelineController t,int frame)
    {
        return safe(sample(t,frame,GRAVITY,"",DEFAULT_GRAVITY),
                DEFAULT_GRAVITY,0F,100F);
    }
    public static Fluid waterAt(CharacterTimelineController t,int frame)
    {
        Fluid f=new Fluid();
        f.amount=safe(sample(t,frame,WATER,"Amount",0F),0F,0F,1F);
        f.buoyancy=safe(sample(t,frame,WATER,"Buoyancy",1.05F),1.05F,0F,3F);
        f.drag=safe(sample(t,frame,WATER,"Drag",.8F),.8F,0F,5F);
        f.angularDrag=safe(sample(t,frame,WATER,"AngularDrag",.65F),.65F,0F,5F);
        f.surfaceY=safe(sample(t,frame,WATER,"SurfaceY",63F),
                63F,-2048F,2048F);
        // Enable is a step parameter, never linearly interpolated.
        CharacterKey previous=null;
        CharacterTrack main=t==null?null:t.getTrackById("main");
        if(main!=null)
            for(CharacterKey key:main.getKeys())
                if(key!=null&&key.getFrame()<=frame&&
                        has(key,WATER)&&
                        (previous==null||key.getFrame()>previous.getFrame()))
                    previous=key;
        f.surfaceEnabled=previous!=null&&
                data(previous).getCompoundTag("Water")
                        .getBoolean("SurfaceEnabled");
        return f;
    }
    public static List<CharacterKey> keys(CharacterTimelineController t)
    {
        if(t==null||t.getTrackById("main")==null)return Collections.emptyList();
        return new ArrayList<CharacterKey>(t.getTrackById("main").getKeys());
    }
}

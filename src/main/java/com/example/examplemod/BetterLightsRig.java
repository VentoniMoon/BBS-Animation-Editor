package com.example.examplemod;

import java.lang.reflect.Method;
import net.minecraft.entity.Entity;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NBTTagCompound;

/**
 * BetterLights actor's two editor-only controllers, saved through the
 * ordinary AnimationData/Bones keyframe persistence:
 *
 * Origin - world offset from the actor's recorded position.
 * Target - WORLD direction relative to Origin (default forward +Z).
 *
 * Neither is an anatomical bone or a replacement for the actor Record.
 * Existing POSE key creation/interpolation/duplication supports both.
 */
public final class BetterLightsRig
{
    public static final String ORIGIN="Origin";
    public static final String TARGET="Target";

    private static java.lang.ref.WeakReference<EditorSceneState> active=
            new java.lang.ref.WeakReference<EditorSceneState>(null);

    public static void bind(EditorSceneState state)
    {
        active=new java.lang.ref.WeakReference<EditorSceneState>(state);
    }
    public static ActorAnimationData forActor(BlockbusterSceneActorData actor)
    {
        EditorSceneState state=active.get();
        if(state==null||actor==null||!isLight(
                actor.getActor()==null?null:actor.getActor().getMorph()))
            return null;
        ActorAnimationData data=state.getAnimationData().getActor(actor.getId());
        if(data==null)
        {
            data=new ActorAnimationData(actor.getId());
            state.getAnimationData().putActor(actor.getId(),data);
        }
        ensure(data);
        return data;
    }

    public static boolean isLight(NBTTagCompound tag)
    {
        return tag!=null&&"betterLights".equals(tag.getString("Name"));
    }

    public static void ensure(ActorAnimationData data)
    {
        if(data==null)return;
        AnimationBone origin=data.getBone(ORIGIN);
        AnimationBone target=data.getBone(TARGET);
        // Older builds saved fictional Steve limbs on BetterLights actors.
        // Retain genuine rig keys on reopen; prune only unsupported limbs.
        for(int i=data.getBones().size()-1;i>=0;i--)
        {
            AnimationBone b=data.getBones().get(i);
            if(b==null||(!ORIGIN.equals(b.getName())&&
                    !TARGET.equals(b.getName())))
                data.getBones().remove(i);
        }
        if(origin==null)
        {
            origin=new AnimationBone(ORIGIN);
            data.getBones().add(origin);
        }
        if(target==null)
        {
            target=new AnimationBone(TARGET);
            target.setLocalPosition(0F,0F,4F);
            data.getBones().add(target);
        }
    }

    public static float[] offset(ActorAnimationData data,double tick)
    {
        return vector(data,ORIGIN,tick,0F,0F,0F);
    }

    public static float[] direction(ActorAnimationData data,double tick)
    {
        float[] v=vector(data,TARGET,tick,0F,0F,4F);
        float length=(float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if(length<1E-6F)return new float[]{0F,0F,1F};
        return new float[]{v[0]/length,v[1]/length,v[2]/length};
    }

    private static float[] vector(ActorAnimationData data,String name,
            double frame,float x,float y,float z)
    {
        AnimationBone bone=data==null?null:data.getBone(name);
        if(bone==null||bone.getKeyframes().isEmpty())
            return new float[]{x,y,z};
        AnimationTransform t=bone.getTransformAt((float)frame);
        return t==null?new float[]{x,y,z}:new float[]{
                t.getPositionX(),t.getPositionY(),t.getPositionZ()};
    }

    /**
     * Sample keyed BetterLights settings from Origin without changing the
     * Blockbuster actor's saved base morph. Continuous number/color settings
     * interpolate; switches remain discrete and UUID metadata is skipped.
     */
    public static net.minecraft.nbt.NBTTagCompound settingsAt(
            ActorAnimationData data,double frame,
            net.minecraft.nbt.NBTTagCompound base)
    {
        if(base==null)return null;
        AnimationBone origin=data==null?null:data.getBone(ORIGIN);
        if(origin==null)return base.copy();
        AnimationKeyframe prev=null,next=null;
        for(AnimationKeyframe key:origin.getKeyframes())
        {
            if(key==null||key.getLightSettings()==null)continue;
            if(key.getFrame()<=frame&&
                    (prev==null||key.getFrame()>prev.getFrame()))prev=key;
            if(key.getFrame()>frame&&
                    (next==null||key.getFrame()<next.getFrame()))next=key;
        }
        if(prev==null)return base.copy();
        net.minecraft.nbt.NBTTagCompound before=prev.getLightSettings();
        if(next==null)return before;
        net.minecraft.nbt.NBTTagCompound after=next.getLightSettings();
        double alpha=Math.max(0D,Math.min(1D,
                (frame-prev.getFrame())/
                Math.max(1D,next.getFrame()-prev.getFrame())));
        if(prev.getInterpolation()!=null &&
                (prev.getInterpolation().name().contains("HOLD")||
                 prev.getInterpolation().name().contains("CONSTANT")))
            alpha=0D;
        net.minecraft.nbt.NBTTagCompound result=before.copy();
        for(String path:before.getKeySet())
        {
            if(!after.hasKey(path)||"UUID".equals(path)||"Name".equals(path))
                continue;
            net.minecraft.nbt.NBTBase from=before.getTag(path);
            net.minecraft.nbt.NBTBase to=after.getTag(path);
            if(!(from instanceof net.minecraft.nbt.NBTPrimitive)||
                    !(to instanceof net.minecraft.nbt.NBTPrimitive)||
                    from.getId()!=to.getId())continue;
            // Boolean (NBT byte) properties are stepped at the key.
            if(from.getId()==1)continue;
            net.minecraft.nbt.NBTPrimitive v=
                    (net.minecraft.nbt.NBTPrimitive)from;
            net.minecraft.nbt.NBTPrimitive w=
                    (net.minecraft.nbt.NBTPrimitive)to;
            if(from.getId()==3&&path.toLowerCase(
                    java.util.Locale.ROOT).contains("color"))
            {
                int ac=v.getInt(),bc=w.getInt(),rgb=0;
                for(int shift=0;shift<=24;shift+=8)
                {
                    int a=(ac>>>shift)&255,b=(bc>>>shift)&255;
                    rgb|=Math.max(0,Math.min(255,
                            (int)Math.round(a+(b-a)*alpha)))<<shift;
                }
                result.setInteger(path,rgb);
            }
            else if(from.getId()==3)
                result.setInteger(path,(int)Math.round(v.getDouble()+
                        (w.getDouble()-v.getDouble())*alpha));
            else if(from.getId()==5)
                result.setFloat(path,(float)(v.getDouble()+
                        (w.getDouble()-v.getDouble())*alpha));
            else if(from.getId()==6)
                result.setDouble(path,v.getDouble()+
                        (w.getDouble()-v.getDouble())*alpha);
        }
        return result;
    }

    private static final java.util.WeakHashMap<AbstractMorph,
            mchorse.mclib.utils.ValueSerializer> baselineValues=
            new java.util.WeakHashMap<AbstractMorph,
                    mchorse.mclib.utils.ValueSerializer>();

    public static void applySettings(AbstractMorph morph,
            ActorAnimationData data,double frame,
            net.minecraft.nbt.NBTTagCompound base)
    {
        if(morph==null||data==null||base==null||
                !morph.getClass().getName()
                    .endsWith(".BetterLightsMorph"))return;
        AnimationBone origin=data.getBone(ORIGIN);
        if(origin==null)return;
        boolean hasSettings=false;
        for(AnimationKeyframe key:origin.getKeyframes())
            if(key!=null&&key.getLightSettings()!=null)
            {hasSettings=true;break;}
        if(!hasSettings)return;
        try
        {
            Object values=morph.getClass().getMethod("getValueManager")
                    .invoke(morph);
            if(!(values instanceof mchorse.mclib.utils.ValueSerializer))
                return;
            mchorse.mclib.utils.ValueSerializer runtime=
                    (mchorse.mclib.utils.ValueSerializer)values;
            mchorse.mclib.utils.ValueSerializer baseline=
                    baselineValues.get(morph);
            if(baseline==null)
            {
                AbstractMorph initial=
                        mchorse.metamorph.api.MorphManager.INSTANCE
                                .morphFromNBT(base.copy());
                if(initial==null)return;
                Object initialValues=initial.getClass().getMethod(
                        "getValueManager").invoke(initial);
                if(!(initialValues instanceof
                        mchorse.mclib.utils.ValueSerializer))return;
                baseline=(mchorse.mclib.utils.ValueSerializer)initialValues;
                baselineValues.put(morph,baseline);
            }
            // Reset ALL channels before sampling any arbitrary frame.
            // NBT writes only changed ValueSerializer properties; a direct
            // fromNBT() alone would retain unsaved values from a different
            // frame after seeking backwards.
            runtime.copyValues(baseline);
            runtime.fromNBT(settingsAt(data,frame,base));
        }
        catch(ReflectiveOperationException ignored){}
    }

    /**
     * BetterLightsMorph.render recomputes world space position and direction.
     * Apply Target after that native render. The LightCaster belongs to the
     * underlying Blockbuster morph, no fake texture/posteffect is used.
     */
    public static void applyTarget(AbstractMorph morph,
            ActorAnimationData data,double frame)
    {
        if(morph==null||data==null||data.getBone(TARGET)==null||
                data.getBone(TARGET).getKeyframes().isEmpty()||
                !morph.getClass().getName()
                .endsWith(".BetterLightsMorph"))return;
        try
        {
            Object light=morph.getClass().getMethod("getLightcaster")
                    .invoke(morph);
            if(light==null)return;
            float[] dir=direction(data,frame);
            Method setter=light.getClass().getMethod(
                    "direction",float.class,float.class,float.class);
            setter.invoke(light,dir[0],dir[1],dir[2]);
        }
        catch(ReflectiveOperationException ignored)
        {
            // Optional BetterLights ABI; editor remains usable without it.
        }
    }

    private BetterLightsRig(){}
}

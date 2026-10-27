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
        // CAMERA statics are separate scene objects, not keyed actors.
        if(actor!=null && actor.getId()!=null &&
                actor.getId().startsWith("camera-light-"))return null;
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

    public static float[] targetOffset(ActorAnimationData data,double tick)
    {
        // Target's rest tip is four WORLD blocks ahead. AnimationBone's
        // getTransformAt() returns only the keyed delta, not its local/rest
        // pivot. The old sampler dropped this rest offset as soon as the
        // first key was created, collapsing the Target gizmo into Origin.
        AnimationBone bone=data==null?null:data.getBone(TARGET);
        float[] v=vector(data,TARGET,tick,0F,0F,0F);
        return new float[]{v[0]+(bone==null?0F:bone.getLocalX()),
                v[1]+(bone==null?0F:bone.getLocalY()),
                v[2]+(bone==null?4F:bone.getLocalZ())};
    }

    public static float[] direction(ActorAnimationData data,double tick)
    {
        float[] v=targetOffset(data,tick);
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
     * Blockbuster's BetterLightsMorph.render() recomputes the world position
     * from its OpenGL matrix, and applies its native local direction through
     * the entity rotation. The two editor bones have a different contract:
     * Origin is a WORLD displacement in blocks and Target is a WORLD vector.
     *
     * Synchronize the single already-registered caster after native render,
     * overriding both values together (not only direction as before).
     * Never allocate a second LightCaster or mutate a saved Record.
     */
    public static void syncNativeCaster(AbstractMorph morph,
            ActorAnimationData data,double frame,
            double worldX,double worldY,double worldZ)
    {
        boolean keyedAim=data!=null&&data.getBone(TARGET)!=null&&
                !data.getBone(TARGET).getKeyframes().isEmpty();
        syncNativeCaster(morph,worldX,worldY,worldZ,
                keyedAim?direction(data,frame):null);
    }

    public static void syncNativeCaster(AbstractMorph morph,
            double worldX,double worldY,double worldZ,float[] direction)
    {
        if(morph==null||!morph.getClass().getName()
                .endsWith(".BetterLightsMorph"))return;
        try
        {
            Object caster=morph.getClass().getMethod("getLightcaster")
                    .invoke(morph);
            if(caster==null)return;
            // Public LightCaster.pos(float,float,float), not the morph's
            // matrix-derived cached position or render-side model offset.
            caster.getClass().getMethod("pos",
                    float.class,float.class,float.class).invoke(caster,
                    (float)worldX,(float)worldY,(float)worldZ);
            if(direction!=null&&direction.length>=3)
                caster.getClass().getMethod("direction",
                        float.class,float.class,float.class).invoke(caster,
                        direction[0],direction[1],direction[2]);
        }
        catch(ReflectiveOperationException ignored)
        {
            // BetterLights is optional; don't interfere with other morphs.
        }
        catch(LinkageError ignored){}
    }

    private BetterLightsRig(){}
}

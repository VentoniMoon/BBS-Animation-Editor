package com.example.examplemod;

import java.lang.ref.WeakReference;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;

/** POSE virtual emitter bone for native Blockbuster ParticleMorph actors.
 * Uses the same keyed position timeline as the lighting Origin rig, while
 * ParticleSettings is stored on each Origin key separately from LightSettings.
 */
public final class ParticleActorRig
{
    public static final String ORIGIN="Origin";
    private static WeakReference<EditorSceneState> active=
            new WeakReference<EditorSceneState>(null);
    private ParticleActorRig(){}

    public static void bind(EditorSceneState state)
    {
        active=new WeakReference<EditorSceneState>(state);
    }
    public static boolean isParticle(NBTTagCompound nbt)
    {
        return ParticleEmitterConfig.isParticle(nbt);
    }
    public static boolean isParticleActor(BlockbusterSceneActorData actor)
    {
        return actor!=null&&actor.getActor()!=null&&
                isParticle(actor.getActor().getMorph());
    }
    public static ActorAnimationData forActor(BlockbusterSceneActorData actor)
    {
        EditorSceneState state=active.get();
        if(state==null||!isParticleActor(actor))return null;
        ActorAnimationData data=state.getAnimationData().getActor(actor.getId());
        if(data==null)
        {
            data=new ActorAnimationData(actor.getId());
            state.getAnimationData().putActor(actor.getId(),data);
        }
        ensure(data);
        return data;
    }
    public static void ensure(ActorAnimationData data)
    {
        if(data==null)return;
        for(int i=data.getBones().size()-1;i>=0;i--)
        {
            AnimationBone bone=data.getBones().get(i);
            if(bone==null||!ORIGIN.equals(bone.getName()))
                data.getBones().remove(i);
        }
        if(data.getBone(ORIGIN)==null)
            data.getBones().add(new AnimationBone(ORIGIN));
    }
    public static float[] offset(ActorAnimationData rig,double frame)
    {
        AnimationBone origin=rig==null?null:rig.getBone(ORIGIN);
        if(origin==null||origin.getKeyframes().isEmpty())
            return new float[]{0F,0F,0F};
        AnimationTransform transform=origin.getTransformAt((float)frame);
        return transform==null?new float[]{0F,0F,0F}:
                new float[]{transform.getPositionX(),
                        transform.getPositionY(),transform.getPositionZ()};
    }
    public static double yaw(ActorAnimationData rig,double frame)
    {
        AnimationBone origin=rig==null?null:rig.getBone(ORIGIN);
        if(origin==null||origin.getKeyframes().isEmpty())return 0D;
        AnimationTransform t=origin.getTransformAt((float)frame);
        return t==null?0D:t.getRotationY();
    }
    /** Native ParticleMorph numeric settings interpolate linearly. Type,
     * Args, LocalRotation and Mode are discrete at the previous key.
     * Keeping the original NBT preserves future Blockbuster properties.
     */
    public static NBTTagCompound settingsAt(ActorAnimationData rig,
            double frame,NBTTagCompound base)
    {
        NBTTagCompound original=ParticleEmitterConfig.normalize(base);
        AnimationBone bone=rig==null?null:rig.getBone(ORIGIN);
        if(bone==null)return original;
        AnimationKeyframe before=null,after=null;
        for(AnimationKeyframe key:bone.getKeyframes())
        {
            if(key==null||key.getParticleSettings()==null)continue;
            if(key.getFrame()<=frame&&
                    (before==null||key.getFrame()>before.getFrame()))
                before=key;
            if(key.getFrame()>frame&&
                    (after==null||key.getFrame()<after.getFrame()))
                after=key;
        }
        if(before==null)return original;
        NBTTagCompound start=ParticleEmitterConfig.normalize(
                before.getParticleSettings());
        if(after==null)return start;
        NBTTagCompound end=ParticleEmitterConfig.normalize(
                after.getParticleSettings());
        double fraction=Math.max(0D,Math.min(1D,
                (frame-before.getFrame())/
                Math.max(1D,after.getFrame()-before.getFrame())));
        if(before.getInterpolation()!=null &&
                (before.getInterpolation().name().contains("HOLD")||
                 before.getInterpolation().name().contains("CONSTANT")))
            fraction=0D;
        for(String name:ParticleEmitterConfig.NUMBERS)
        {
            double a=ParticleEmitterConfig.value(start,name),
                    b=ParticleEmitterConfig.value(end,name);
            boolean integral="Frequency".equals(name)||
                    "Duration".equals(name)||"Delay".equals(name)||
                    "Cap".equals(name)||"Count".equals(name);
            ParticleEmitterConfig.update(start,name,
                    integral?Math.round(a+(b-a)*fraction):
                            a+(b-a)*fraction);
        }
        return start;
    }
}

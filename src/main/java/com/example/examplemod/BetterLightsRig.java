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
     * BetterLightsMorph.render recomputes world space position and direction.
     * Apply Target after that native render. The LightCaster belongs to the
     * underlying Blockbuster morph, no fake texture/posteffect is used.
     */
    public static void applyTarget(AbstractMorph morph,
            ActorAnimationData data,double frame)
    {
        if(morph==null||data==null||!morph.getClass().getName()
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

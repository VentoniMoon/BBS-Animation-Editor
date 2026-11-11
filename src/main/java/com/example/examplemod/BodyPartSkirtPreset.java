package com.example.examplemod;

import java.util.List;

/**
 * Explicit opt-in Bone Cloth preset for a Chameleon skirt attachment.
 * The existing Jolt Chameleon preview adapter supplies the simulation,
 * bone-local writeback and parent actor body/leg contacts.
 *
 * No guessed bone names: select the actual waistband anchor in BODY PARTS.
 * The bone must have seven direct, independent linear strips (3+2+2).
 * The animated waistband remains kinematic and stays attached to the actor.
 */
public final class BodyPartSkirtPreset
{
    private BodyPartSkirtPreset(){}

    public static SkirtChainTopology.Plan<AnimationBone> inspect(
            AnimationBone waist)
    {
        return SkirtChainTopology.inspect(waist,
                AnimationBone::getChildren,AnimationBone::getName);
    }

    /** Do not modify any model unless its topology and morph are valid. */
    public static SkirtChainTopology.Plan<AnimationBone> enable(
            BodyPartModelData model,AnimationBone waist,int frame)
    {
        if(model==null||waist==null||model.getBones()==null||
                !model.getBones().contains(waist))
            throw new IllegalArgumentException("Select a skirt waist bone");
        mchorse.metamorph.api.morphs.AbstractMorph morph=
                model.getMorphAt(frame);
        if(morph==null||!morph.getClass().getName()
                .endsWith(".ChameleonMorph"))
            throw new IllegalArgumentException(
                    "Bone Cloth skirt preset requires a Chameleon model");

        SkirtChainTopology.Plan<AnimationBone> plan=inspect(waist);
        for(AnimationBone ancestor=waist.getParent();ancestor!=null;
                ancestor=ancestor.getParent())
        {
            // Never silently replace an already active parent hair/cloth
            // rig with 7 overlapping physical child rigs.
            BodyPartPhysicsSettings owner=
                    model.getPhysicsSettings(ancestor.getName());
            if(owner!=null&&owner.isEnabled())
                throw new IllegalStateException(
                        "Active physics ancestor: "+ancestor.getName());
        }
        for(List<AnimationBone> strip:plan.strands)
            for(AnimationBone bone:strip)
            {
                BodyPartPhysicsSettings settings=
                        model.getPhysicsSettings(bone.getName());
                // A waist-owned rig cannot also contain independent
                // child-owned chains. Explicit preset application retires
                // those duplicate roots; outside rigs are untouched.
                if(settings!=null)settings.setEnabled(false);
            }

        BodyPartPhysicsSettings cloth=
                model.getPhysicsSettings(waist.getName());
        cloth.setEnabled(true);
        cloth.setGravity(1.0F);
        cloth.setStiffness(.7F);
        cloth.setDamping(.40F);
        cloth.setBodyCollisions(true);
        return plan;
    }

    public static void disable(BodyPartModelData model,AnimationBone waist)
    {
        if(model==null||waist==null)return;
        if(model.getBones()!=null&&model.getBones().contains(waist))
        {
            BodyPartPhysicsSettings cloth=
                    model.getPhysicsSettings(waist.getName());
            if(cloth!=null)cloth.setEnabled(false);
        }
    }
}

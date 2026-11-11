package com.example.examplemod;

import java.util.List;

/**
 * Explicit opt-in Bone Cloth preset for a Chameleon skirt attachment.
 * The existing Jolt Chameleon preview adapter supplies the simulation,
 * bone-local writeback and parent actor body/leg contacts.
 *
 * A generic seven-way panel preset is kept for other skirt geometries.
 * The supplied skirt.geo.json uses the separate validated 20-panel profile
 * rooted at ALL (front/back 7+7, sides 3+3).
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

    /** Map the actual user-authored 44-bone, 20-panel skirt. */
    public static SkirtGeoProfile.Plan<AnimationBone> inspectFull(
            AnimationBone root)
    {
        return SkirtGeoProfile.inspect(root,
                AnimationBone::getChildren,AnimationBone::getName);
    }

    /**
     * Enable eight existing Jolt chain roots: two seven-way anchors and
     * six two-link side strips. Does NOT simulate ALL, group or either
     * group42/group43 anchor; those follow the authored Chameleon pose.
     * All validation happens before changing a single enabled flag.
     */
    public static SkirtGeoProfile.Plan<AnimationBone> enableFull(
            BodyPartModelData model,AnimationBone root,int frame)
    {
        if(model==null||root==null||model.getBones()==null||
                !model.getBones().contains(root))
            throw new IllegalArgumentException(
                    "Select the ALL bone of the attached skirt model");
        mchorse.metamorph.api.morphs.AbstractMorph morph=
                model.getMorphAt(frame);
        if(morph==null||!morph.getClass().getName()
                .endsWith(".ChameleonMorph"))
            throw new IllegalArgumentException(
                    "The supplied skirt requires a Chameleon attachment");

        SkirtGeoProfile.Plan<AnimationBone> plan=inspectFull(root);
        for(AnimationBone bone:plan.allBones)
            if(!model.getBones().contains(bone))
                throw new IllegalArgumentException(
                        "Skirt hierarchy missing model bone "+bone.getName());

        // Only this attachment's 44 recognized bones are changed.
        // Other attachments and their physics settings stay untouched.
        for(AnimationBone bone:plan.allBones)
        {
            BodyPartPhysicsSettings settings=
                    model.getPhysicsSettings(bone.getName());
            settings.setEnabled(false);
        }
        for(AnimationBone anchor:plan.physicsRoots)
        {
            BodyPartPhysicsSettings cloth=
                    model.getPhysicsSettings(anchor.getName());
            cloth.setEnabled(true);
            cloth.setGravity(1.0F);
            cloth.setStiffness(.70F);
            cloth.setDamping(.40F);
            cloth.setBodyCollisions(true);
        }
        return plan;
    }

    public static boolean isFullEnabled(BodyPartModelData model,
            AnimationBone root)
    {
        if(model==null||root==null)return false;
        SkirtGeoProfile.Plan<AnimationBone> plan;
        try{plan=inspectFull(root);}
        catch(IllegalArgumentException invalid){return false;}
        for(AnimationBone anchor:plan.physicsRoots)
        {
            BodyPartPhysicsSettings cfg=
                    model.getPhysicsSettings(anchor.getName());
            if(cfg==null||!cfg.isEnabled())return false;
        }
        return true;
    }

    /** Disable only the eight physical roots of this skirt profile. */
    public static void disableFull(BodyPartModelData model,
            AnimationBone root)
    {
        if(model==null||root==null)return;
        SkirtGeoProfile.Plan<AnimationBone> plan=inspectFull(root);
        for(AnimationBone anchor:plan.physicsRoots)
        {
            BodyPartPhysicsSettings cfg=
                    model.getPhysicsSettings(anchor.getName());
            if(cfg!=null)cfg.setEnabled(false);
        }
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

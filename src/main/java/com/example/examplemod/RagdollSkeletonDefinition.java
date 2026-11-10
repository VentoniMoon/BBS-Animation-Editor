package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Physics 2.0: a model-bone -> humanoid rigid-body binding plan.
 *
 * The renderer's native bones stay authoritative. Do not manufacture bones
 * or silently retarget a missing bone: such a rig would tear the skin.
 * Geometry dimensions are safe first-pass estimates in Minecraft blocks.
 * A later Jolt pass will fit the real model mesh before allocating bodies.
 *
 * The definition is Java 8 / Minecraft / Jolt independent, so it can be
 * tested without a running client or allocating native physics objects.
 */
public final class RagdollSkeletonDefinition
{
    public enum Preset { BLOCKBUSTER, EMOTICONS }
    public enum Joint { ROOT, CONE_TWIST, HINGE }

    /** Constraint angles are degrees in the segment's REST-POSE joint frame. */
    public static final class Segment
    {
        public final String role;
        public final String bone;
        public final String parentRole;
        public final Joint joint;
        public final float massKg, lengthBlocks, radiusBlocks;
        public final float swingDegrees, twistMinDegrees, twistMaxDegrees;
        public final float hingeMinDegrees, hingeMaxDegrees;

        private Segment(Spec spec,String bone,String parentRole)
        {
            this.role=spec.role;
            this.bone=bone;
            this.parentRole=parentRole;
            this.joint=parentRole==null?Joint.ROOT:spec.joint;
            this.massKg=spec.mass;
            this.lengthBlocks=spec.length;
            this.radiusBlocks=spec.radius;
            this.swingDegrees=spec.swing;
            this.twistMinDegrees=spec.twistMin;
            this.twistMaxDegrees=spec.twistMax;
            this.hingeMinDegrees=spec.hingeMin;
            this.hingeMaxDegrees=spec.hingeMax;
        }
    }

    private static final class Spec
    {
        final String role,parent;
        final Joint joint;
        final float mass,length,radius,swing,twistMin,twistMax,hingeMin,hingeMax;
        final String[] names;

        Spec(String role,String parent,Joint joint,float mass,float length,
                float radius,float swing,float twistMin,float twistMax,
                float hingeMin,float hingeMax,String... names)
        {
            this.role=role;this.parent=parent;this.joint=joint;
            this.mass=mass;this.length=length;this.radius=radius;
            this.swing=swing;this.twistMin=twistMin;this.twistMax=twistMax;
            this.hingeMin=hingeMin;this.hingeMax=hingeMax;this.names=names;
        }
    }

    private static Spec root(String role,float mass,float length,float radius,
            String... names)
    {
        return new Spec(role,null,Joint.ROOT,mass,length,radius,
                0F,0F,0F,0F,0F,names);
    }
    private static Spec ball(String role,String parent,float mass,
            float length,float radius,float swing,float twist,
            String... names)
    {
        return new Spec(role,parent,Joint.CONE_TWIST,mass,length,radius,
                swing,-twist,twist,0F,0F,names);
    }
    private static Spec hinge(String role,String parent,float mass,
            float length,float radius,float minimum,float maximum,
            String... names)
    {
        return new Spec(role,parent,Joint.HINGE,mass,length,radius,
                0F,0F,0F,minimum,maximum,names);
    }

    // Minimal Blockbuster biped: 6 rigid bodies. An elbow/knee joint
    // must not be generated unless the model has an actual separate bone.
    private static final Spec[] BB={
        root("pelvis",9F,0.45F,0.20F,"body","torso","pelvis"),
        ball("head","pelvis",5F,0.50F,0.24F,40F,45F,
                "head","bipedHead"),
        ball("left_upper_arm","pelvis",3F,0.70F,0.12F,85F,65F,
                "left_arm","leftArm","arm_left","armL","bipedLeftArm"),
        ball("right_upper_arm","pelvis",3F,0.70F,0.12F,85F,65F,
                "right_arm","rightArm","arm_right","armR","bipedRightArm"),
        ball("left_thigh","pelvis",11F,0.72F,0.17F,65F,35F,
                "left_leg","leftLeg","leg_left","legL","bipedLeftLeg"),
        ball("right_thigh","pelvis",11F,0.72F,0.17F,65F,35F,
                "right_leg","rightLeg","leg_right","legR","bipedRightLeg")
    };

    /*
     * Confirmed against Emoticons' original default.bobj, slim.bobj,
     * default_3d.bobj and default_simple*.bobj (all share 20 bone names).
     *
     * Native BOBJ hierarchy:
     *
     * anchor -> body -> low_body -> head / left_arm / right_arm
     *                 -> right_leg / left_leg (from body)
     * left_arm -> low_left_arm -> low_left_arm.end (attachment only)
     * right_arm -> low_right_arm -> low_right_arm.end
     * left_leg -> low_left_leg
     * right_leg -> low_leg_right
     *
     * 6 misc_* bones are animation controls/props, not rigid bodies.
     * ragdoll.bobj has two more .item attachment markers (22 total).
     *
     * Thus the stock biped has ELEVEN physical segments. There are no
     * independent hand or foot rigid bones in the actual stock armature.
     */
    // Conservative first-pass anatomical limits; use the verified
    // BOBJ hinge directions, but avoid full hyperflexion/torso clipping.
    // Direct parent/child collision stays suppressed because bound capsule
    // shapes intentionally overlap around real anatomical joints.
    private static final Spec[] EMO={
        root("pelvis",9F,0.36F,0.19F,"body","pelvis","hips"),
        ball("chest","pelvis",15F,0.42F,0.20F,25F,25F,
                "low_body","upper_body","chest","spine","torso"),
        ball("head","chest",5F,0.50F,0.22F,35F,30F,
                "head","head_main","neck"),
        ball("left_upper_arm","chest",2.4F,0.25F,0.105F,70F,45F,
                "left_arm","left_upper_arm","arm_l","upper_arm_l"),
        ball("right_upper_arm","chest",2.4F,0.25F,0.105F,70F,45F,
                "right_arm","right_upper_arm","arm_r","upper_arm_r"),
        hinge("left_forearm","left_upper_arm",1.3F,0.25F,0.09F,0F,135F,
                "low_left_arm","left_forearm","left_lower_arm","forearm_l"),
        hinge("right_forearm","right_upper_arm",1.3F,0.25F,0.09F,0F,135F,
                "low_right_arm","right_forearm","right_lower_arm","forearm_r"),
        ball("left_thigh","pelvis",8F,0.375F,0.15F,55F,30F,
                "left_leg","left_upper_leg","thigh_l","upper_leg_l"),
        ball("right_thigh","pelvis",8F,0.375F,0.15F,55F,30F,
                "right_leg","right_upper_leg","thigh_r","upper_leg_r"),
        hinge("left_shin","left_thigh",4F,0.375F,0.115F,0F,130F,
                "low_left_leg","left_shin","left_lower_leg","shin_l"),
        hinge("right_shin","right_thigh",4F,0.375F,0.115F,0F,130F,
                "low_leg_right","low_right_leg","right_shin","right_lower_leg","shin_r")
    };

    private final Preset preset;
    private final List<Segment> segments;
    private final int availableBones;
    private RagdollSkeletonDefinition(Preset preset,List<Segment> segments,
            int availableBones)
    {
        this.preset=preset;
        this.segments=Collections.unmodifiableList(segments);
        this.availableBones=availableBones;
    }

    public Preset getPreset(){return preset;}
    public List<Segment> getSegments(){return segments;}
    public int size(){return segments.size();}
    public int getAvailableBones(){return availableBones;}
    public boolean hasPhysicalSkeleton(){return size()>=5;}

    public static RagdollSkeletonDefinition bind(Preset preset,
            Collection<String> nativeBoneNames)
    {
        if(preset==null)throw new IllegalArgumentException("Missing preset");
        Map<String,String> available=new HashMap<String,String>();
        if(nativeBoneNames!=null)
            for(String name:nativeBoneNames)
                if(name!=null&&!name.trim().isEmpty())
                    available.put(normalize(name),name);
        Spec[] specs=preset==Preset.BLOCKBUSTER?BB:EMO;
        Map<String,String> bound=new HashMap<String,String>();
        Set<String> consumed=new HashSet<String>();
        List<Segment> segments=new ArrayList<Segment>();
        for(Spec spec:specs)
        {
            String nativeName=null;
            for(String alias:spec.names)
            {
                String candidate=available.get(normalize(alias));
                if(candidate!=null&&!consumed.contains(candidate))
                {nativeName=candidate;break;}
            }
            if(nativeName==null)continue;
            String parent=spec.parent;
            // A missing optional intermediary bone (e.g. neck or forearm)
            // must not create a detached free body. Reparent to its nearest
            // real ancestor, without changing any model transform.
            while(parent!=null&&!bound.containsKey(parent))
            {
                Spec parentSpec=findSpec(specs,parent);
                parent=parentSpec==null?null:parentSpec.parent;
            }
            if(spec.parent!=null&&parent==null)continue;
            segments.add(new Segment(spec,nativeName,parent));
            bound.put(spec.role,nativeName);
            consumed.add(nativeName);
        }
        return new RagdollSkeletonDefinition(preset,segments,available.size());
    }

    private static Spec findSpec(Spec[] specs,String role)
    {
        for(Spec s:specs)if(s.role.equals(role))return s;
        return null;
    }

    private static String normalize(String s)
    {
        StringBuilder result=new StringBuilder();
        s=s.toLowerCase(Locale.ROOT);
        for(int i=0;i<s.length();i++)
        {
            char c=s.charAt(i);
            if(Character.isLetterOrDigit(c))result.append(c);
        }
        return result.toString();
    }
}

package com.example.examplemod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure Java 8 invariants; no Minecraft, OptiFine or JNI required. */
public final class RagdollSkeletonDefinitionSmokeTest
{
    private static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }

    private static void verify(RagdollSkeletonDefinition rig,int minBodies)
    {
        check(rig.size()>=minBodies,"Missing expected rigid bodies");
        Set<String> roles=new HashSet<String>();
        Set<String> bones=new HashSet<String>();
        for(RagdollSkeletonDefinition.Segment body:rig.getSegments())
        {
            check(!roles.contains(body.role),"Duplicate role "+body.role);
            check(!bones.contains(body.bone),"Duplicate native bone "+body.bone);
            if(body.parentRole!=null)
                check(roles.contains(body.parentRole),
                        "Parent not registered before child "+body.role);
            else check(body.joint==RagdollSkeletonDefinition.Joint.ROOT,
                    "An unparented body must be the root");
            check(body.massKg>0F&&body.lengthBlocks>0F&&
                    body.radiusBlocks>0F,"Invalid physical primitive");
            check(body.joint!=RagdollSkeletonDefinition.Joint.HINGE||
                    body.hingeMinDegrees<body.hingeMaxDegrees,
                    "Invalid hinge range");
            check(body.joint!=RagdollSkeletonDefinition.Joint.CONE_TWIST||
                    (body.swingDegrees>0F&&
                     body.twistMinDegrees<body.twistMaxDegrees),
                    "Invalid swing/twist cone");
            roles.add(body.role);bones.add(body.bone);
        }
    }

    public static void main(String[] args)
    {
        List<String> blockbuster=Arrays.asList(
                "body","head","left_arm","right_arm",
                "left_leg","right_leg","Cape","Extra Hat");
        RagdollSkeletonDefinition bb=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.BLOCKBUSTER,blockbuster);
        check(bb.size()==6,"Vanilla Blockbuster biped should have 6 rigid bodies");
        check(bb.getAvailableBones()==8,"Preserve all source bone counts");
        verify(bb,6);

        // Upstream stock Emoticons default.bobj / slim.bobj:
        // 20 native armature bones, exactly 11 physically deformable
        // canonical body segments. No invented wrist, ankle or spine.
        List<String> emoticons=Arrays.asList(
                "anchor","body","low_body","head","left_arm",
                "low_left_arm","low_left_arm.end","right_arm",
                "low_right_arm","low_right_arm.end","right_leg",
                "low_leg_right","left_leg","low_left_leg",
                "misc_bone_1","misc_bone_2","misc_bone_3",
                "misc_gbone_1","misc_gbone_2","misc_gbone_3");
        RagdollSkeletonDefinition em=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,emoticons);
        check(em.size()==11,"Stock Emoticons must bind 11 rigid bodies");
        check(em.getAvailableBones()==20,"All BOBJ bones remain visible");
        verify(em,11);
        check(em.getSegments().get(0).bone.equals("body"),
                "Native pelvis is body, not low_body");
        check(em.getSegments().get(1).bone.equals("low_body"),
                "Native chest/spine is low_body, not upper_body");
        boolean sawElbow=false,sawKnee=false;
        for(RagdollSkeletonDefinition.Segment body:em.getSegments())
        {
            if(body.bone.equals("low_left_arm"))
                sawElbow=body.parentRole.equals("left_upper_arm") &&
                        body.joint==RagdollSkeletonDefinition.Joint.HINGE;
            if(body.bone.equals("low_leg_right"))
                sawKnee=body.parentRole.equals("right_thigh") &&
                        body.joint==RagdollSkeletonDefinition.Joint.HINGE;
            check(!body.bone.contains(".end")&&!body.bone.startsWith("misc"),
                    "Attachment/animation helper must not be a rigid body");
        }
        check(sawElbow&&sawKnee,"Actual elbow/knee aliases missing");
        // Prevent regression to loose 145-degree knee folds and
        // shoulders swinging through the chest at extreme impulses.
        for(RagdollSkeletonDefinition.Segment seg:em.getSegments())
        {
            if(seg.joint==RagdollSkeletonDefinition.Joint.HINGE)
            {
                check(seg.hingeMinDegrees>=0F,
                        "Hinge permits reversed hyperextension: "+seg.role);
                check(seg.hingeMaxDegrees<=135F,
                        "Overextended elbow/knee: "+seg.role);
            }
            if(seg.role.endsWith("_upper_arm")||
                    seg.role.endsWith("_thigh"))
                check(seg.swingDegrees<=70F,
                        "Shoulder/hip permits extreme penetration: "+seg.role);
        }


        // The stock ragdoll.bobj has 22 bones, including two .item
        // attachment points. These must not become extra physics links.
        List<String> ragdoll=new java.util.ArrayList<String>(emoticons);
        ragdoll.add("low_left_arm.item");
        ragdoll.add("low_right_arm.item");
        check(RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,ragdoll).size()==11,
                "BOBJ ragdoll items are not physical joints");

        // Partial but real stock hierarchy: without the lower arm,
        // fingers/attachment markers do not generate fake wrists.
        RagdollSkeletonDefinition partial=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,Arrays.asList(
                "anchor","body","low_body","head","left_arm",
                "right_arm","left_leg","right_leg","low_leg_right"));
        verify(partial,7);
        check(partial.size()==8,"Partial model should bind only real segments");

        // Actual native names, not guessed mannequin bones.
        RagdollSkeletonDefinition unknown=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,
                Arrays.asList("cape","cloth_1","hair_2"));
        check(unknown.size()==0&&!unknown.hasPhysicalSkeleton(),
                "Unknown model must be rejected without generating phantom rig");
        System.out.println("Ragdoll humanoid skeleton presets OK");
    }
}

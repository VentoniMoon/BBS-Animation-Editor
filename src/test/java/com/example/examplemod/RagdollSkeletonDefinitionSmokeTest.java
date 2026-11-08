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

        List<String> emoticons=Arrays.asList(
                "low_body","upper_body","head",
                "left_arm","right_arm","left_forearm","right_forearm",
                "left_hand","right_hand","left_leg","right_leg",
                "left_shin","right_shin","left_foot","right_foot");
        RagdollSkeletonDefinition em=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,emoticons);
        check(em.size()==15,"Complete Emoticons armature mapping");
        verify(em,15);

        // Partial armature: no phantom forearm, but a real hand must
        // reparent to its existing upper arm, never float in world space.
        RagdollSkeletonDefinition partial=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,Arrays.asList(
                "low_body","head","left_arm","right_arm",
                "left_hand","left_leg","right_leg"));
        verify(partial,7);
        boolean handReparented=false;
        for(RagdollSkeletonDefinition.Segment body:partial.getSegments())
            if(body.role.equals("left_hand"))
                handReparented=body.parentRole.equals("left_upper_arm");
        check(handReparented,"Partial armature must reparent to nearest bone");

        // Actual native names, not guessed mannequin bones.
        RagdollSkeletonDefinition unknown=RagdollSkeletonDefinition.bind(
                RagdollSkeletonDefinition.Preset.EMOTICONS,
                Arrays.asList("cape","cloth_1","hair_2"));
        check(unknown.size()==0&&!unknown.hasPhysicalSkeleton(),
                "Unknown model must be rejected without generating phantom rig");
        System.out.println("Ragdoll humanoid skeleton presets OK");
    }
}

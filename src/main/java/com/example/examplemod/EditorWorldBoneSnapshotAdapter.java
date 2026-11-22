package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Shared per-actor world-pin snapshot bridge for selected and background actors. */
public final class EditorWorldBoneSnapshotAdapter
{
    private EditorWorldBoneSnapshotAdapter(){}

    public static EditorWorldBonePoseRetarget.Result solve(
            EditorWorldBonePinRegistry pins,String actorId,
            List<AnimationBone> bones,float frame,
            BlockbusterRecordFrame record)
    {
        if(pins==null||pins.size()==0||actorId==null||
                bones==null||record==null)return null;
        for(AnimationBone bone:bones)
        {
            if(bone==null)continue;
            EditorWorldBonePinRegistry.Pin pin=pins.find(
                    actorId,bone.getName());
            if(pin!=null)
                return EditorWorldBonePoseRetarget.retarget(
                        bone,frame,record,pin.world);
        }
        return null;
    }

    public static List<AnimationBoneSnapshot> snapshots(
            List<AnimationBone> bones,float frame,
            EditorWorldBonePoseRetarget.Result solved)
    {
        List<AnimationBoneSnapshot> out=
                new ArrayList<AnimationBoneSnapshot>();
        if(bones==null)return out;
        for(AnimationBone bone:bones)
        {
            if(bone==null)continue;
            AnimationTransform transform=solved==null?null:
                    solved.overrides.get(bone);
            if(transform==null)transform=bone.getTransformAt(frame);
            if(transform==null)continue;
            out.add(new AnimationBoneSnapshot(
                    bone.getName(),
                    bone.getParent()==null?null:bone.getParent().getName(),
                    transform));
        }
        return out;
    }
}

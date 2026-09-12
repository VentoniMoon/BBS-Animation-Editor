package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationAdapterData
{
    private final List<AnimationBoneSnapshot> bones;

    public AnimationAdapterData()
    {
        this.bones =
                new ArrayList<AnimationBoneSnapshot>();
    }

    public void addBone(
            AnimationBoneSnapshot bone)
    {
        if (bone == null)
        {
            return;
        }

        this.bones.add(bone);
    }

    public List<AnimationBoneSnapshot> getBones()
    {
        return this.bones;
    }
}
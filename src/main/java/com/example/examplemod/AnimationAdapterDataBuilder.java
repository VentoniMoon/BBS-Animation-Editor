package com.example.examplemod;

import java.util.List;

public class AnimationAdapterDataBuilder
{
    public static AnimationAdapterData build(
            List<AnimationBone> bones,
            int frame)
    {
        AnimationAdapterData data =
                new AnimationAdapterData();

        if (bones == null)
        {
            return data;
        }

        for (
                AnimationBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            data.addBone(
                    bone.createSnapshot(frame)
            );
        }

        return data;
    }
}
package com.example.examplemod;

import java.util.List;

public interface AnimationAdapter
{
    boolean supports();

    void apply(
            List<AnimationBoneSnapshot> bones,
            int frame
    );
}
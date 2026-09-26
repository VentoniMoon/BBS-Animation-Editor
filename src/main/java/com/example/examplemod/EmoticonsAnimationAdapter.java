package com.example.examplemod;

import java.util.List;

import net.minecraftforge.fml.common.Loader;

public class EmoticonsAnimationAdapter
        implements AnimationAdapter
{
    @Override
    public boolean supports()
    {
        return Loader.isModLoaded(
                "emoticons"
        );
    }

    @Override
    public void apply(
            List<AnimationBoneSnapshot> bones,
            int frame)
    {
        if (!supports())
        {
            return;
        }

        EmoticonsPreviewAnimationState.set(
                bones,
                frame
        );
    }
}
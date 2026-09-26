package com.example.examplemod;

import java.util.List;

import net.minecraftforge.fml.common.Loader;

public class BlockbusterAnimationAdapter
        implements AnimationAdapter
{
    private final BlockbusterModelAccess modelAccess;


    public BlockbusterAnimationAdapter(
            BlockbusterModelAccess modelAccess)
    {
        this.modelAccess = modelAccess;
    }


    @Override
    public boolean supports()
    {
        return Loader.isModLoaded(
                "blockbuster"
        );
    }


    public Object getModel()
    {
        if (this.modelAccess == null)
        {
            return null;
        }

        return this.modelAccess.getModel();
    }


    public boolean hasModel()
    {
        return this.modelAccess != null
                && this.modelAccess.isValid();
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

        /*
         * =====================================================
         * PREVIEW STATE
         * =====================================================
         *
         * Передаём текущую позу Timeline
         * непосредственно в Preview Renderer.
         *
         * Preview Renderer уже сам применяет
         * эти данные к CustomMorph.
         */
        BlockbusterPreviewAnimationState.set(
                bones,
                frame
        );
    }
}
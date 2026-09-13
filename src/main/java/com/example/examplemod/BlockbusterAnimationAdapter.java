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

    /*
     * ---------------------------------------------------------
     * Blockbuster availability
     * ---------------------------------------------------------
     */

    @Override
    public boolean supports()
    {
        return Loader.isModLoaded("blockbuster");
    }

    /*
     * ---------------------------------------------------------
     * Model
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * Apply animation
     * ---------------------------------------------------------
     */

    @Override
    public void apply(
            List<AnimationBoneSnapshot> bones,
            int frame)
    {
        if (
                !supports()
                        || this.modelAccess == null
                        || !this.modelAccess.isValid()
                        || bones == null
        )
        {
            return;
        }

        for (
                AnimationBoneSnapshot bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            String boneName =
                    bone.getName();

            if (
                    boneName == null
                            || boneName.isEmpty()
            )
            {
                continue;
            }

            Object renderer =
                    this.modelAccess.findBone(
                            boneName
                    );

            if (renderer == null)
            {
                continue;
            }

            /*
             * Snapshot содержит LOCAL transform.
             *
             * Hierarchy родитель -> ребёнок
             * продолжает обрабатываться самим
             * Blockbuster через ModelCustomRenderer.
             */
            this.modelAccess.applyTransform(
                    renderer,
                    bone
            );
        }
    }
}
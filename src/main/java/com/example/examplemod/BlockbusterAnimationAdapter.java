package com.example.examplemod;

import java.util.List;

import net.minecraftforge.fml.common.Loader;

public class BlockbusterAnimationAdapter
        implements AnimationAdapter
{
    private BlockbusterModelAccess modelAccess;

    public BlockbusterAnimationAdapter()
    {
        this.modelAccess =
                new BlockbusterModelAccess(null);
    }

    /*
     * ---------------------------------------------------------
     * Blockbuster availability
     * ---------------------------------------------------------
     */

    @Override
    public boolean supports()
    {
        return Loader.isModLoaded(
                "blockbuster"
        );
    }

    /*
     * ---------------------------------------------------------
     * Model
     * ---------------------------------------------------------
     */

    public void setModel(
            Object model)
    {
        if (this.modelAccess == null)
        {
            this.modelAccess =
                    new BlockbusterModelAccess(
                            model
                    );
        }
        else
        {
            this.modelAccess.setModel(
                    model
            );
        }
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

    public boolean loadModel(
            String name)
    {
        if (!supports())
        {
            return false;
        }

        if (this.modelAccess == null)
        {
            this.modelAccess =
                    new BlockbusterModelAccess(
                            null
                    );
        }

        return this.modelAccess.loadModelByName(
                name
        );
    }

    /*
     * ---------------------------------------------------------
     * Apply animation
     * ---------------------------------------------------------
     *
     * Здесь намеренно НЕТ mapping-а:
     *
     * Anchor -> anchor
     * Body   -> body
     * ...
     *
     * AnimationBone теперь использует реальные имена
     * Blockbuster-модели.
     *
     * Поэтому:
     *
     * AnimationBone "left_arm"
     *              ↓
     * ModelCustomRenderer "left_arm"
     *
     * AnimationBone "body_armor"
     *              ↓
     * ModelCustomRenderer "body_armor"
     *
     * и так далее для любого количества костей.
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

            /*
             * AnimationBone теперь содержит
             * настоящее имя Blockbuster limb.
             */
            Object renderer =
                    this.modelAccess.findBone(
                            boneName
                    );

            if (renderer == null)
            {
                continue;
            }

            /*
             * Snapshot, который приходит сюда,
             * должен содержать LOCAL transform.
             *
             * Это важно:
             *
             * Blockbuster сам применяет parent hierarchy
             * через ModelCustomRenderer.parent.
             *
             * Поэтому мы не должны передавать сюда
             * world transform.
             */
            this.modelAccess.applyTransform(
                    renderer,
                    bone
            );
        }
    }
}
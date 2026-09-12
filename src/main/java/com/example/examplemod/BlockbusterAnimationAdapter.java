package com.example.examplemod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.fml.common.Loader;

public class BlockbusterAnimationAdapter implements AnimationAdapter
{
    private BlockbusterModelAccess modelAccess;

    /**
     * Соответствие костей нашего Animation Editor
     * костям Blockbuster.
     */
    private final Map<String, String> boneMapping;

    public BlockbusterAnimationAdapter()
    {
        this.modelAccess = new BlockbusterModelAccess(null);

        this.boneMapping = new HashMap<String, String>();

        this.boneMapping.put("Anchor", "anchor");
        this.boneMapping.put("Body", "body");
        this.boneMapping.put("Head", "head");
        this.boneMapping.put("Arm.L", "left_arm");
        this.boneMapping.put("Arm.R", "right_arm");
        this.boneMapping.put("Leg.L", "left_leg");
        this.boneMapping.put("Leg.R", "right_leg");
    }

    @Override
    public boolean supports()
    {
        return Loader.isModLoaded("blockbuster");
    }

    public void setModel(Object model)
    {
        if (this.modelAccess == null)
        {
            this.modelAccess = new BlockbusterModelAccess(model);
        }
        else
        {
            this.modelAccess.setModel(model);
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

    public boolean loadModel(String name)
    {
        if (!supports())
        {
            return false;
        }

        if (this.modelAccess == null)
        {
            this.modelAccess = new BlockbusterModelAccess(null);
        }

        return this.modelAccess.loadModelByName(name);
    }

    /**
     * Возвращает имя кости Blockbuster
     * для кости нашего редактора.
     */
    private String getBlockbusterBoneName(String editorBoneName)
    {
        if (editorBoneName == null)
        {
            return null;
        }

        String mappedName = this.boneMapping.get(editorBoneName);

        if (mappedName != null)
        {
            return mappedName;
        }

        return editorBoneName;
    }

    @Override
    public void apply(
            List<AnimationBoneSnapshot> bones,
            int frame
    )
    {
        if (!supports()
                || this.modelAccess == null
                || !this.modelAccess.isValid()
                || bones == null)
        {
            return;
        }

        for (AnimationBoneSnapshot bone : bones)
        {
            if (bone == null)
            {
                continue;
            }

            String editorBoneName = bone.getName();

            String blockbusterBoneName =
                    getBlockbusterBoneName(editorBoneName);

            if (blockbusterBoneName == null)
            {
                continue;
            }

            Object renderer =
                    this.modelAccess.findBone(blockbusterBoneName);

            if (renderer == null)
            {
                continue;
            }

            boolean applied =
                    this.modelAccess.applyTransform(
                            renderer,
                            bone
                    );

            if (applied)
            {
                System.out.println(
                        "[BBS Animation Editor] Applied "
                                + editorBoneName
                                + " -> "
                                + blockbusterBoneName
                                + " | frame="
                                + frame
                                + " | pos=("
                                + bone.getPositionX()
                                + ", "
                                + bone.getPositionY()
                                + ", "
                                + bone.getPositionZ()
                                + ")"
                                + " | rot=("
                                + bone.getRotationX()
                                + ", "
                                + bone.getRotationY()
                                + ", "
                                + bone.getRotationZ()
                                + ")"
                                + " | scale=("
                                + bone.getScaleX()
                                + ", "
                                + bone.getScaleY()
                                + ", "
                                + bone.getScaleZ()
                                + ")"
                );
            }
        }
    }
}
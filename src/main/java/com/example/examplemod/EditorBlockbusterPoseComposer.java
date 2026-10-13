package com.example.examplemod;

import java.util.Map;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Composes sampled editor-local Bone transforms with a native Blockbuster
 * base pose using the SAME offset rules as BlockbusterActorPreviewRenderer.
 *
 * Source pose is never mutated. MorphAction generation is deliberately
 * separate: it must select a real CustomMorph and preserve its other NBT.
 */
public final class EditorBlockbusterPoseComposer
{
    private EditorBlockbusterPoseComposer() {}

    public static ModelPose compose(
            ModelPose base,
            EditorPoseExportSampler.SampledFrame sampled)
    {
        if (base == null || sampled == null)
        {
            throw new IllegalArgumentException("Base pose and sample are required");
        }

        ModelPose result = base.copy();

        for (Map.Entry<String, AnimationTransform> entry
                : sampled.getBones().entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null)
            {
                continue;
            }

            // Preview deliberately skips unknown model limbs.
            ModelTransform target = result.limbs.get(entry.getKey());
            if (target == null)
            {
                continue;
            }

            AnimationTransform source = entry.getValue();
            target.translate[0] += source.getPositionX();
            target.translate[1] += source.getPositionY();
            target.translate[2] += source.getPositionZ();

            target.rotate[0] += source.getRotationX();
            target.rotate[1] += source.getRotationY();
            target.rotate[2] += source.getRotationZ();

            target.scale[0] *= source.getScaleX();
            target.scale[1] *= source.getScaleY();
            target.scale[2] *= source.getScaleZ();
        }

        return result;
    }

    public static NBTTagCompound composeNBT(
            ModelPose base,
            EditorPoseExportSampler.SampledFrame sampled)
    {
        return compose(base, sampled).toNBT(new NBTTagCompound());
    }
}

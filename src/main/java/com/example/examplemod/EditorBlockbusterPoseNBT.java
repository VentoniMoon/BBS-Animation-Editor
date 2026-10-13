package com.example.examplemod;

import java.util.Map;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Converts the local Pose samples to the native Blockbuster ModelPose NBT
 * representation. Does not inject MorphAction or alter Record files.
 *
 * These are editor-local transforms. A later morph-specific adapter must
 * combine them with the model's original pose (and verify axis conventions)
 * before emitting a playable CustomMorph.
 */
public final class EditorBlockbusterPoseNBT
{
    private EditorBlockbusterPoseNBT() {}

    public static NBTTagCompound serializeLocalPose(
            EditorPoseExportSampler.SampledFrame sample)
    {
        if (sample == null)
        {
            throw new IllegalArgumentException("Pose sample is null");
        }

        ModelPose pose = new ModelPose();

        for (Map.Entry<String, AnimationTransform> entry
                : sample.getBones().entrySet())
        {
            if (entry.getKey() == null || entry.getKey().isEmpty()
                    || entry.getValue() == null)
            {
                continue;
            }

            AnimationTransform source = entry.getValue();
            ModelTransform target = new ModelTransform();
            target.translate[0] = source.getPositionX();
            target.translate[1] = source.getPositionY();
            target.translate[2] = source.getPositionZ();
            target.rotate[0] = source.getRotationX();
            target.rotate[1] = source.getRotationY();
            target.rotate[2] = source.getRotationZ();
            target.scale[0] = source.getScaleX();
            target.scale[1] = source.getScaleY();
            target.scale[2] = source.getScaleZ();
            pose.limbs.put(entry.getKey(), target);
        }

        return pose.toNBT(new NBTTagCompound());
    }
}

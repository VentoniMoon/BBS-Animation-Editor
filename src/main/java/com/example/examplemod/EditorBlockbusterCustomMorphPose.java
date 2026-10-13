package com.example.examplemod;

import java.io.IOException;
import java.util.Map;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Creates one native Blockbuster CustomMorph snapshot containing a sampled
 * Pose. Does not write to Record or mutate the source morph.
 *
 * The caller must supply the correct morph for the tick. In particular,
 * Character morph changes must be resolved BEFORE sampling its base pose.
 */
public final class EditorBlockbusterCustomMorphPose
{
    private EditorBlockbusterCustomMorphPose() {}

    public static NBTTagCompound build(
            CustomMorph source,
            ModelPose basePose,
            EditorPoseExportSampler.SampledFrame sample)
            throws IOException
    {
        if (source == null || basePose == null || sample == null)
        {
            throw new IOException("Missing CustomMorph, base pose or sampled frame");
        }

        /*
         * Keep model-dependent LimbProperties (color/glow etc.) rather
         * than serializing a plain ModelPose and losing those fields.
         */
        CustomMorph.ModelProperties properties =
                source.convertProp(basePose.copy());

        if (properties == null)
        {
            throw new IOException("Cannot create Blockbuster ModelProperties");
        }

        for (Map.Entry<String, AnimationTransform> entry
                : sample.getBones().entrySet())
        {
            if (entry.getKey() == null || entry.getValue() == null)
            {
                continue;
            }

            ModelTransform target = properties.limbs.get(entry.getKey());

            if (target == null)
            {
                // Same behavior as BlockbusterActorPreviewRenderer.
                continue;
            }

            AnimationTransform transform = entry.getValue();
            target.translate[0] += transform.getPositionX();
            target.translate[1] += transform.getPositionY();
            target.translate[2] += transform.getPositionZ();
            target.rotate[0] += transform.getRotationX();
            target.rotate[1] += transform.getRotationY();
            target.rotate[2] += transform.getRotationZ();
            target.scale[0] *= transform.getScaleX();
            target.scale[1] *= transform.getScaleY();
            target.scale[2] *= transform.getScaleZ();
        }

        NBTTagCompound morph = new NBTTagCompound();
        source.toNBT(morph);
        morph.setTag("CustomPose", properties.toNBT(new NBTTagCompound()));
        return morph;
    }
}

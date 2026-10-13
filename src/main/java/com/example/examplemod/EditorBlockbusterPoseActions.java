package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Combines main Character morph changes and sampled Pose into one native
 * MorphAction per tick. Only native Blockbuster CustomMorph is supported.
 * Never modifies the scene actor, original morph or Record.
 */
public final class EditorBlockbusterPoseActions
{
    private EditorBlockbusterPoseActions() {}

    public static List<EditorCharacterMorphExport.PreparedAction> prepare(
            EditorSceneState state,
            BlockbusterSceneActorData actor,
            List<EditorCharacterMorphExport.PreparedAction> character)
            throws IOException
    {
        List<EditorCharacterMorphExport.PreparedAction> output =
                new ArrayList<EditorCharacterMorphExport.PreparedAction>();
        List<EditorPoseExportSampler.SampledFrame> frames =
                EditorPoseExportSampler.sampleSelected(state);

        NBTTagCompound startingMorph = actor.getActor().getMorph();
        if (startingMorph == null || startingMorph.hasNoTags())
        {
            throw new IOException("Actor has no initial morph for Pose export");
        }

        Map<Integer, NBTTagCompound> morphChanges =
                new TreeMap<Integer, NBTTagCompound>();

        /*
         * A Skin key stores a full morph snapshot. That snapshot may carry
         * a previously baked CustomPose from the editor's Preview. Skin
         * changes must not replace the current model's base pose.
         */
        Set<Integer> skinFrames = new HashSet<Integer>();
        CharacterTimelineController timeline = actor.getCharacterTimeline();
        if (timeline != null)
        {
            for (CharacterTrack track : timeline.getTracks())
            {
                if (track == null || !track.isMainTrack())
                {
                    continue;
                }
                for (CharacterKey key : track.getKeys())
                {
                    if (key != null && key.hasSkin())
                    {
                        skinFrames.add(key.getFrame());
                    }
                }
            }
        }
        byte type = 12;
        if (character != null)
        {
            for (EditorCharacterMorphExport.PreparedAction action : character)
            {
                type = action.nbt.getByte("Type");
                morphChanges.put(action.frame,
                        action.nbt.getCompoundTag("Morph").copy());
            }
        }

        if (actor.getRecord() != null
                && actor.getRecord().getActionRegistry() != null)
        {
            for (Map.Entry<Byte, String> entry
                    : actor.getRecord().getActionRegistry().getActions().entrySet())
            {
                if ("morph".equals(entry.getValue()))
                {
                    type = entry.getKey();
                    break;
                }
            }
        }

        NBTTagCompound active = startingMorph.copy();
        String lastSignature = "";
        CustomMorph current = null;

        for (EditorPoseExportSampler.SampledFrame sampled : frames)
        {
            NBTTagCompound next = morphChanges.get(sampled.getFrame());
            if (next != null)
            {
                NBTTagCompound replacement = next.copy();

                /*
                 * Keep the preceding pristine base pose when the same
                 * Blockbuster model merely changes its skin. Otherwise a
                 * Skin snapshot containing an old animated pose becomes
                 * a new base and the sampled Pose is added a second time.
                 *
                 * A real model change retains its own CustomPose.
                 */
                if (skinFrames.contains(sampled.getFrame())
                        && active.getString("Name").equals(
                                replacement.getString("Name")))
                {
                    if (active.hasKey("CustomPose", 10))
                    {
                        replacement.setTag("CustomPose",
                                active.getCompoundTag("CustomPose").copy());
                    }
                    else
                    {
                        replacement.removeTag("CustomPose");
                    }
                }
                active = replacement;
            }

            String name = active.getString("Name");
            if (!name.startsWith("blockbuster."))
            {
                throw new IOException("Pose export only supports Blockbuster CustomMorph: "
                        + name + " at tick " + sampled.getFrame());
            }

            String signature = active.toString();
            if (current == null || !signature.equals(lastSignature))
            {
                current = new CustomMorph();
                current.fromNBT(active.copy());
                current.updateModel();
                lastSignature = signature;
            }

            ModelPose basePose = current.getCurrentPose();
            if (basePose == null)
            {
                throw new IOException("Missing base pose for " + name
                        + " at tick " + sampled.getFrame());
            }

            NBTTagCompound baked = EditorBlockbusterCustomMorphPose.build(
                    current, basePose, sampled);

            /*
             * Character Bones overrides are separate from Pose keys.
             * Apply them to the baked CustomMorph, so fixed flags are
             * serialized with each frame's final ModelProperties.
             */
            CustomMorph exportedMorph = new CustomMorph();
            exportedMorph.fromNBT(baked.copy());
            CharacterBodyPartOverrideController.apply(
                    exportedMorph,
                    actor.getCharacterTimeline(),
                    sampled.getFrame()
            );
            /*
             * MorphAction.apply() uses EntityActor.morph(), which merges
             * CustomMorph animations. The default 10-tick transition
             * restarts every time a per-tick Pose snapshot is applied,
             * causing visible lag/jitter and masking sampled curves.
             *
             * All Pose interpolation is already baked by the sampler;
             * disable ONLY the extra transition on the exported snapshot.
             * Do not use ignored=true: CustomMorph.canMerge then skips
             * updating customPose entirely.
             */
            exportedMorph.animation.duration = 0;
            exportedMorph.animation.ignored = false;
            exportedMorph.animation.animates = false;

            NBTTagCompound finalMorph = new NBTTagCompound();
            exportedMorph.toNBT(finalMorph);

            NBTTagCompound actionTag = new NBTTagCompound();
            actionTag.setByte("Type", type);
            actionTag.setTag("Morph", finalMorph);

            // PreparedAction construction is centralized in Character export.
            output.add(EditorCharacterMorphExport.prepared(
                    sampled.getFrame(), actionTag));
        }
        return output;
    }
}

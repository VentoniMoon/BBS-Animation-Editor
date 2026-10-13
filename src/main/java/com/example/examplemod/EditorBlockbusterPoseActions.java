package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                active = next.copy();
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

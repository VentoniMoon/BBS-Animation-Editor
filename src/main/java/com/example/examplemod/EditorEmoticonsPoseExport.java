package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Stage 7 Emoticons Pose exporter.
 *
 * Writes native AnimatedMorph Pose and one-tick Transition, leaving the
 * Emoticons animation name, UserData and other appearance fields intact.
 * The parent Blockbuster export adapter is not used for this morph type.
 */
public final class EditorEmoticonsPoseExport
{
    private EditorEmoticonsPoseExport() {}

    public static List<EditorCharacterMorphExport.PreparedAction> prepare(
            EditorSceneState state,
            BlockbusterSceneActorData actor,
            List<EditorCharacterMorphExport.PreparedAction> character)
            throws IOException
    {
        NBTTagCompound source = actor.getActor().getMorph();
        if (source == null || source.hasNoTags())
        {
            throw new IOException("Missing initial Emoticons morph");
        }

        List<EditorPoseExportSampler.SampledFrame> frames =
                EditorPoseExportSampler.sampleSelected(state, true);
        Map<Integer, NBTTagCompound> changes =
                new TreeMap<Integer, NBTTagCompound>();
        byte morphType = 12;
        if (character != null)
        {
            for (EditorCharacterMorphExport.PreparedAction entry : character)
            {
                changes.put(entry.frame, entry.nbt.getCompoundTag("Morph").copy());
                morphType = entry.nbt.getByte("Type");
            }
        }

        if (actor.getRecord() != null &&
                actor.getRecord().getActionRegistry() != null)
        {
            for (Map.Entry<Byte, String> entry :
                    actor.getRecord().getActionRegistry().getActions().entrySet())
            {
                if ("morph".equals(entry.getValue()))
                {
                    morphType = entry.getKey();
                    break;
                }
            }
        }

        Set<Integer> skinFrames = new HashSet<Integer>();
        CharacterTimelineController timeline = actor.getCharacterTimeline();
        if (timeline != null)
        {
            for (CharacterTrack track : timeline.getTracks())
            {
                if (track == null || !track.isMainTrack()) continue;
                for (CharacterKey key : track.getKeys())
                {
                    if (key != null && key.hasSkin())
                    {
                        skinFrames.add(key.getFrame());
                    }
                }
            }
        }

        /*
         * Body Parts use the exact attachment serializer already verified
         * for Blockbuster models. The parent stays AnimatedMorph.
         */
        BodyPartsEditorController bodyParts = state.getBodyPartsController();
        boolean exportBodyParts = bodyParts != null
                && !bodyParts.getModels().isEmpty();
        BodyPartsPreviewRenderer partsSerializer = exportBodyParts
                ? new BodyPartsPreviewRenderer() : null;

        NBTTagCompound active = source.copy();
        List<EditorCharacterMorphExport.PreparedAction> output =
                new ArrayList<EditorCharacterMorphExport.PreparedAction>(frames.size());

        for (EditorPoseExportSampler.SampledFrame frame : frames)
        {
            NBTTagCompound change = changes.get(frame.getFrame());
            if (change != null)
            {
                NBTTagCompound next = change.copy();
                /* Skin keys contain full snapshots. Do not accumulate
                 * previously baked Pose when only the appearance changes. */
                if (skinFrames.contains(frame.getFrame())
                        && active.getString("Name").equals(next.getString("Name"))
                        && active.getString("Animation").equals(next.getString("Animation")))
                {
                    if (active.hasKey("Pose", 10))
                    {
                        next.setTag("Pose", active.getCompoundTag("Pose").copy());
                    }
                    else
                    {
                        next.removeTag("Pose");
                    }
                }
                active = next;
            }

            AbstractMorph loaded;
            try
            {
                loaded = MorphManager.INSTANCE.morphFromNBT(active.copy());
            }
            catch (Exception exception)
            {
                throw new IOException("Unable to read Emoticons morph at tick "
                        + frame.getFrame(), exception);
            }

            if (!(loaded instanceof AnimatedMorph))
            {
                throw new IOException("Emoticons Pose requires AnimatedMorph at tick "
                        + frame.getFrame() + ": " + active.getString("Name"));
            }

            NBTTagCompound result = active.copy();

            /*
             * Character Animation Setup lives in editor CharacterKeys,
             * not in the morph snapshot. Bake its effective values into
             * native Emoticons UserData for each exported Record tick.
             * No runtime Preview animator or source morph is modified.
             */
            result.setTag("UserData",
                    CharacterAnimationSetupController.exportUserData(
                            actor, frame.getFrame(), (AnimatedMorph) loaded));

            NBTTagCompound pose = result.hasKey("Pose", 10)
                    ? result.getCompoundTag("Pose").copy()
                    : new NBTTagCompound();

            for (Map.Entry<String, AnimationTransform> bone : frame.getBones().entrySet())
            {
                if (bone.getKey() == null || bone.getValue() == null) continue;
                AnimationTransform value = bone.getValue();
                NBTTagCompound transform = pose.hasKey(bone.getKey(), 10)
                        ? pose.getCompoundTag(bone.getKey()).copy()
                        : new NBTTagCompound();

                transform.setFloat("X", transform.getFloat("X") + value.getPositionX());
                transform.setFloat("Y", transform.getFloat("Y") + value.getPositionY());
                transform.setFloat("Z", transform.getFloat("Z") + value.getPositionZ());
                final float radians = (float) (Math.PI / 180.0D);
                transform.setFloat("RX", transform.getFloat("RX") + value.getRotationX() * radians);
                transform.setFloat("RY", transform.getFloat("RY") + value.getRotationY() * radians);
                transform.setFloat("RZ", transform.getFloat("RZ") + value.getRotationZ() * radians);
                transform.setFloat("SX", (transform.hasKey("SX", 99)
                        ? transform.getFloat("SX") : 1F) * value.getScaleX());
                transform.setFloat("SY", (transform.hasKey("SY", 99)
                        ? transform.getFloat("SY") : 1F) * value.getScaleY());
                transform.setFloat("SZ", (transform.hasKey("SZ", 99)
                        ? transform.getFloat("SZ") : 1F) * value.getScaleZ());

                int boneState = CharacterBodyPartOverrideController.getEffectiveKeyState(
                        timeline, frame.getFrame(), bone.getKey());
                if (boneState == CharacterBodyPartOverrideController.STATE_DISABLE)
                {
                    transform.setBoolean("F", false);
                }
                else if (boneState == CharacterBodyPartOverrideController.STATE_ENABLE)
                {
                    transform.removeTag("F");
                }
                pose.setTag(bone.getKey(), transform);
            }

            /*
             * Overwrite attachments at every tick, including after the
             * final active range. Otherwise the merged morph may retain
             * its previous attachments indefinitely.
             */
            if (exportBodyParts)
            {
                result.setTag("BodyParts", partsSerializer.exportParts(
                        bodyParts, frame.getFrame()));
            }

            result.setTag("Pose", pose);
            NBTTagCompound transition = new NBTTagCompound();
            transition.setBoolean("Animates", true);
            transition.setInteger("Duration", 1);
            result.setTag("Transition", transition);

            NBTTagCompound action = new NBTTagCompound();
            action.setByte("Type", morphType);
            action.setTag("Morph", result);
            output.add(EditorCharacterMorphExport.prepared(frame.getFrame(), action));
        }

        return output;
    }
}

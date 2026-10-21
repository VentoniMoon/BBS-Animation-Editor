package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Editor-only persistence for attached models. No Blockbuster Record or
 * Metamorph source morph is modified by this codec.
 */
public final class BodyPartsPersistence
{
    private BodyPartsPersistence() {}

    public static NBTTagCompound write(BodyPartsEditorController controller)
    {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList attachments = new NBTTagList();
        if (controller != null)
        {
            for (Map.Entry<String, List<BodyPartModelData>> owner
                    : controller.snapshotActorModels().entrySet())
            for (BodyPartModelData model : owner.getValue())
            {
                if (model == null) continue;
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("ActorId", owner.getKey());
                tag.setString("Model", model.getModelName());
                tag.setString("Attachment", model.getAttachmentBoneName());
                tag.setInteger("Start", model.getStartFrame());
                tag.setInteger("End", model.getEndFrame());
                tag.setBoolean("Weld", model.isWeldEnabled());

                AbstractMorph morph = model.getBaseMorph();
                if (morph != null)
                {
                    tag.setTag("Morph", morph.toNBT());
                }
                tag.setTag("Global", writeTransform(model.getGlobalTransform()));

                NBTTagCompound boneHolder = new NBTTagCompound();
                boneHolder.setTag("Bones", EditorSceneSerializer.writeBones(model.getBones()));
                tag.setTag("Skeleton", boneHolder);
                tag.setTag("Physics", model.writePhysicsSettings());
                tag.setTag("Colliders", model.writeColliderSettings());
                tag.setTag("CharacterTimeline", model.getCharacterTimeline().toNBT());

                NBTTagList morphKeys = new NBTTagList();
                for (Map.Entry<Integer, NBTTagCompound> entry :
                        model.getMorphKeysSnapshot().entrySet())
                {
                    NBTTagCompound key = new NBTTagCompound();
                    key.setInteger("Frame", entry.getKey());
                    key.setTag("Morph", entry.getValue().copy());
                    morphKeys.appendTag(key);
                }
                tag.setTag("MorphKeys", morphKeys);

                NBTTagList modelKeys = new NBTTagList();
                for (Map.Entry<Integer, String> entry :
                        model.getModelKeysSnapshot().entrySet())
                {
                    NBTTagCompound key = new NBTTagCompound();
                    key.setInteger("Frame", entry.getKey());
                    key.setString("Model", entry.getValue());
                    modelKeys.appendTag(key);
                }
                tag.setTag("ModelKeys", modelKeys);
                attachments.appendTag(tag);
            }
        }
        root.setTag("Attachments", attachments);
        return root;
    }

    public static void read(BodyPartsEditorController controller,
            NBTTagCompound root, String selectedActorId)
    {
        if (controller == null || root == null || !root.hasKey("Attachments", 9))
        {
            return;
        }

        Map<String, List<BodyPartModelData>> loaded =
                new java.util.LinkedHashMap<String, List<BodyPartModelData>>();
        NBTTagList attachments = root.getTagList("Attachments", 10);
        for (int i = 0; i < attachments.tagCount(); i++)
        {
            NBTTagCompound tag = attachments.getCompoundTagAt(i);
            String name = tag.getString("Model");
            String attachment = tag.getString("Attachment");
            if (attachment.isEmpty()) continue;

            AbstractMorph morph = null;
            if (tag.hasKey("Morph", 10))
            {
                try
                {
                    morph = MorphManager.INSTANCE.morphFromNBT(
                            tag.getCompoundTag("Morph").copy());
                }
                catch (Throwable error)
                {
                    System.err.println("[BBS Animation Editor] Invalid saved Body Part morph: " + name);
                    error.printStackTrace();
                }
            }

            BlockbusterModelAccess access = null;
            if (morph == null && !name.isEmpty())
            {
                BlockbusterModelAccess candidate = new BlockbusterModelAccess(null);
                if (candidate.loadModelByName(name)) access = candidate;
            }

            List<AnimationBone> bones = new ArrayList<AnimationBone>();
            if (tag.hasKey("Skeleton", 10))
            {
                EditorSceneSerializer.readBones(bones, tag.getCompoundTag("Skeleton"));
            }

            BodyPartModelData model = new BodyPartModelData(
                    name, attachment, access, bones,
                    tag.getInteger("Start"),
                    Math.max(tag.getInteger("Start") + 1, tag.getInteger("End")));
            if (morph != null)
            {
                model.setMorph(morph);
                model.setModel(name, null, bones);
            }
            readTransform(model.getGlobalTransform(), tag.getCompoundTag("Global"));
            model.readPhysicsSettings(tag.getTagList("Physics", 10));
            model.readColliderSettings(tag.getTagList("Colliders", 10));
            model.setWeldEnabled(tag.hasKey("Weld")
                    ? tag.getBoolean("Weld")
                    : ChameleonWeldRuntime.isMorphWeldEnabled(morph));

            if (tag.hasKey("CharacterTimeline", 10))
            {
                CharacterTimelineController timeline =
                        CharacterTimelineController.fromNBT(
                                tag.getCompoundTag("CharacterTimeline"));
                model.getCharacterTimeline().clear();
                for (CharacterTrack track : timeline.getTracks())
                {
                    if (track != null) model.getCharacterTimeline().addTrack(track);
                }
            }

            Map<Integer, NBTTagCompound> morphKeys =
                    new TreeMap<Integer, NBTTagCompound>();
            NBTTagList savedMorphKeys = tag.getTagList("MorphKeys", 10);
            for (int j = 0; j < savedMorphKeys.tagCount(); j++)
            {
                NBTTagCompound key = savedMorphKeys.getCompoundTagAt(j);
                if (key.hasKey("Morph", 10) && key.getInteger("Frame") >= 0)
                {
                    morphKeys.put(key.getInteger("Frame"),
                            key.getCompoundTag("Morph").copy());
                }
            }
            model.restoreMorphKeys(morphKeys);

            Map<Integer, String> modelKeys = new TreeMap<Integer, String>();
            NBTTagList savedModelKeys = tag.getTagList("ModelKeys", 10);
            for (int j = 0; j < savedModelKeys.tagCount(); j++)
            {
                NBTTagCompound key = savedModelKeys.getCompoundTagAt(j);
                if (key.getInteger("Frame") >= 0 && !key.getString("Model").isEmpty())
                {
                    modelKeys.put(key.getInteger("Frame"), key.getString("Model"));
                }
            }
            model.restoreModelKeys(modelKeys);
            /*
             * Older editor.dat attachments have no ActorId. Bind those
             * legacy entries to the currently selected actor once rather
             * than sharing them with every actor in the scene.
             */
            String owner = tag.hasKey("ActorId", 8)
                    ? tag.getString("ActorId")
                    : (selectedActorId == null ? "" : selectedActorId);
            if (!loaded.containsKey(owner))
            {
                loaded.put(owner, new ArrayList<BodyPartModelData>());
            }
            loaded.get(owner).add(model);
        }

        controller.restoreActorModels(loaded, selectedActorId);
    }

    private static NBTTagCompound writeTransform(AnimationTransform value)
    {
        NBTTagCompound t = new NBTTagCompound();
        t.setFloat("PX", value.getPositionX());
        t.setFloat("PY", value.getPositionY());
        t.setFloat("PZ", value.getPositionZ());
        t.setFloat("RX", value.getRotationX());
        t.setFloat("RY", value.getRotationY());
        t.setFloat("RZ", value.getRotationZ());
        t.setFloat("SX", value.getScaleX());
        t.setFloat("SY", value.getScaleY());
        t.setFloat("SZ", value.getScaleZ());
        return t;
    }

    private static void readTransform(AnimationTransform target, NBTTagCompound t)
    {
        if (t == null || t.hasNoTags()) return;
        target.setPosition(t.getFloat("PX"), t.getFloat("PY"), t.getFloat("PZ"));
        target.setRotation(t.getFloat("RX"), t.getFloat("RY"), t.getFloat("RZ"));
        target.setScale(
                t.hasKey("SX") ? t.getFloat("SX") : 1.0F,
                t.hasKey("SY") ? t.getFloat("SY") : 1.0F,
                t.hasKey("SZ") ? t.getFloat("SZ") : 1.0F);
    }
}

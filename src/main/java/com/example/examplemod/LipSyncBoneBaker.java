package com.example.examplemod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bakes Bedrock animation poses into independent Chameleon Body Part bone keys. */
public final class LipSyncBoneBaker
{
    private LipSyncBoneBaker() {}

    public static File animationFile()
    {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                "config/bbs-editor/lipsync/model.animation.json");
    }

    private static float[] vector(JsonObject bone, String channel, float[] defaults)
    {
        if (!bone.has(channel)) return defaults;
        JsonElement item = bone.get(channel);
        if (!item.isJsonObject() || !item.getAsJsonObject().has("vector"))
            throw new IllegalArgumentException("Unsupported animation channel: " + channel);
        com.google.gson.JsonArray values = item.getAsJsonObject().getAsJsonArray("vector");
        if (values.size() != 3) throw new IllegalArgumentException("Invalid " + channel + " vector");
        return new float[]{values.get(0).getAsFloat(), values.get(1).getAsFloat(),
                values.get(2).getAsFloat()};
    }

    private static JsonObject animation(JsonObject all, LipSyncViseme viseme)
    {
        String name = viseme == LipSyncViseme.IDLE ? "closed" :
                viseme == LipSyncViseme.EE ? "e" :
                viseme.name().toLowerCase(Locale.ROOT);
        if (!all.has(name)) throw new IllegalArgumentException("Missing mouth animation: " + name);
        return all.getAsJsonObject(name);
    }

    /** Explicit opt-in repair for previously generated LINEAR Mouth RIG keys. */
    public static int makeExistingKeysConstant(BodyPartModelData part) throws IOException
    {
        if (part == null || !part.hasMorphModel())
            throw new IllegalArgumentException("Select a Mouth RIG Body Part");
        File file = animationFile();
        if (!file.isFile())
            throw new IOException("Missing config/bbs-editor/lipsync/model.animation.json");
        JsonObject animations;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            animations = new JsonParser().parse(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        if (animations == null) throw new IOException("Missing animations object in JSON");
        Set<String> names = new HashSet<String>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet())
        {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject pose = entry.getValue().getAsJsonObject();
            if (pose.has("bones"))
                for (Map.Entry<String, JsonElement> boneEntry :
                        pose.getAsJsonObject("bones").entrySet())
                    names.add(boneEntry.getKey());
        }
        int modified = 0;
        for (AnimationBone bone : part.getBones())
        {
            if (!names.contains(bone.getName())) continue;
            for (AnimationKeyframe key : bone.getKeyframes())
            {
                if (key.getInterpolation() != mchorse.mclib.utils.keyframes.KeyframeInterpolation.CONST)
                {
                    key.setInterpolation(mchorse.mclib.utils.keyframes.KeyframeInterpolation.CONST);
                    modified++;
                }
            }
        }
        return modified;
    }

    public static int generate(BodyPartModelData part, List<LipSyncTimeline.Key> keys)
            throws IOException
    {
        if (part == null || !part.hasMorphModel())
            throw new IllegalArgumentException("Select the Chameleon Mouth RIG Body Part");
        if (keys == null || keys.isEmpty())
            throw new IllegalArgumentException("No speech visemes to bake");
        File file = animationFile();
        if (!file.isFile())
            throw new IOException("Missing config/bbs-editor/lipsync/model.animation.json");
        JsonObject root;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            root = new JsonParser().parse(reader).getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        if (root == null) throw new IOException("Missing animations object in JSON");

        Map<String, AnimationBone> bones = new LinkedHashMap<String, AnimationBone>();
        for (AnimationBone bone : part.getBones()) bones.put(bone.getName(), bone);
        if (bones.isEmpty()) throw new IllegalArgumentException("No internal bones found");
        // All affected bones are reset at every viseme, so previous mouth shapes
        // cannot leave stale position or scale on omitted channels.
        Set<String> names = new LinkedHashSet<String>();
        for (LipSyncTimeline.Key key : keys)
        {
            JsonObject pose = animation(root, key.viseme);
            if (pose.has("bones"))
                for (Map.Entry<String, JsonElement> e :
                        pose.getAsJsonObject("bones").entrySet()) names.add(e.getKey());
        }
        if (!bones.keySet().containsAll(names))
        {
            names.removeAll(bones.keySet());
            throw new IllegalArgumentException("Missing Chameleon bones: " + names);
        }
        // Preflight before any mutations.
        for (LipSyncTimeline.Key key : keys)
        {
            if (key.frame < part.getStartFrame() || key.frame >= part.getEndFrame())
                throw new IllegalArgumentException("Speech exceeds Mouth RIG frame range");
            for (String name : names)
                if (bones.get(name).hasKeyframe(key.frame))
                    throw new IllegalArgumentException("Existing bone key: " + name
                            + " at frame " + key.frame);
        }
        int created = 0;
        for (LipSyncTimeline.Key key : keys)
        {
            JsonObject pose = animation(root, key.viseme);
            JsonObject channels = pose.has("bones") ? pose.getAsJsonObject("bones") : new JsonObject();
            for (String name : names)
            {
                JsonObject data = channels.has(name) ? channels.getAsJsonObject(name)
                        : new JsonObject();
                float[] pos = vector(data, "position", new float[]{0, 0, 0});
                float[] scale = vector(data, "scale", new float[]{1, 1, 1});
                // The supplied "closed" animation moves Inside but does not hide it.
                // Unlike the original BBS renderer, Chameleon may render that plane
                // ahead of Closed. Hide it (and its two child corners) in silence.
                if (key.viseme == LipSyncViseme.IDLE && "Inside".equals(name))
                    scale = new float[]{1, 0, 1};
                AnimationBone bone = bones.get(name);
                bone.addKeyframe(key.frame);
                AnimationKeyframe bakedKey = bone.getKeyframeAt(key.frame);
                bakedKey.setInterpolation(mchorse.mclib.utils.keyframes.KeyframeInterpolation.CONST);
                AnimationTransform transform = bakedKey.getTransform();
                transform.setPosition(pos[0], pos[1], pos[2]);
                transform.setScale(scale[0], scale[1], scale[2]);
                created++;
            }
        }
        return created;
    }
}

package com.example.examplemod;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Blends numerical first-frame Bedrock animation poses as Chameleon pose overrides.
 * Does not mutate shared model data, Body Parts keys, or JSON assets.
 * Unsupported animated/Molang channels fall back to the native action path.
 */
public final class ChameleonVisemeBlender
{
    private ChameleonVisemeBlender() {}

    private static final class Bone
    {
        final float[] pos = {0, 0, 0};
        final float[] rot = {0, 0, 0};
        final float[] scale = {1, 1, 1};
    }

    private static Map<String, Bone> read(AbstractMorph morph, String clip) throws IOException
    {
        String name = morph.name == null ? "" : morph.name.replace('\\', '/');
        if (name.startsWith("chameleon.")) name = name.substring("chameleon.".length());

        File root = new File(Minecraft.getMinecraft().mcDataDir, "config/chameleon/models").getCanonicalFile();
        File model = new File(root, name).getCanonicalFile();
        if (!model.toPath().startsWith(root.toPath()) || !model.isDirectory())
            throw new IOException("Chameleon model folder missing: " + model);

        Map<String, Bone> result = null;
        for (File folder : new File[]{model, new File(model, "animations")})
        {
            File[] files = folder.listFiles();
            if (files == null) continue;
            Arrays.sort(files, new Comparator<File>()
            {
                public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
            });
            for (File file : files)
            {
                if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
                {
                    JsonElement parsed = new JsonParser().parse(reader);
                    if (!parsed.isJsonObject()) continue;
                    JsonObject rootJson = parsed.getAsJsonObject();
                    if (!rootJson.has("animations") || !rootJson.get("animations").isJsonObject()) continue;
                    JsonObject animations = rootJson.getAsJsonObject("animations");
                    if (!animations.has(clip) || !animations.get(clip).isJsonObject()) continue;
                    JsonObject animation = animations.getAsJsonObject(clip);
                    if (!animation.has("bones") || !animation.get("bones").isJsonObject()) continue;

                    result = new LinkedHashMap<String, Bone>();
                    for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("bones").entrySet())
                    {
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject data = entry.getValue().getAsJsonObject();
                        Bone bone = new Bone();
                        vector(data, "position", bone.pos);
                        vector(data, "rotation", bone.rot);
                        vector(data, "scale", bone.scale);
                        result.put(entry.getKey(), bone);
                    }
                    return result;
                }
                catch (RuntimeException error)
                {
                    throw new IOException("Unsupported static viseme in " + file + ": " + error, error);
                }
            }
        }
        throw new IOException("Animation not found: " + clip);
    }

    private static void vector(JsonObject bone, String channel, float[] into)
    {
        if (!bone.has(channel)) return;
        JsonElement value = bone.get(channel);
        if (value.isJsonObject())
        {
            JsonObject object = value.getAsJsonObject();
            if (object.has("vector")) value = object.get("vector");
            else if (object.has("0.0")) value = object.get("0.0");
            else if (object.has("0")) value = object.get("0");
            else throw new IllegalArgumentException("Non-static channel " + channel);
            if (value.isJsonObject() && value.getAsJsonObject().has("vector"))
                value = value.getAsJsonObject().get("vector");
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("Expected three-component static " + channel);
        for (int i = 0; i < 3; i++)
        {
            JsonElement v = value.getAsJsonArray().get(i);
            if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Non-numeric component of " + channel);
            into[i] = v.getAsFloat();
        }
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Use the Chameleon pose layer AFTER resetting native action playback.
     * Rotation units match Chameleon's pose (radians); x/y rotations follow
     * Chameleon's MolangHelper axis inversion.
     */
    public static AbstractMorph blend(AbstractMorph morph, String from, String to, float factor)
    {
        if (factor <= 0F || factor >= 1F) return null;
        try
        {
            Map<String, Bone> a = read(morph, from);
            Map<String, Bone> b = read(morph, to);
            Set<String> bones = new LinkedHashSet<String>();
            bones.addAll(a.keySet());
            bones.addAll(b.keySet());

            NBTTagCompound tag = morph.toNBT();
            NBTTagCompound pose = tag.hasKey("Pose", 10)
                    ? tag.getCompoundTag("Pose").copy() : new NBTTagCompound();
            NBTTagCompound transforms = pose.hasKey("Pose", 10)
                    ? pose.getCompoundTag("Pose").copy() : new NBTTagCompound();

            for (String name : bones)
            {
                Bone left = a.containsKey(name) ? a.get(name) : new Bone();
                Bone right = b.containsKey(name) ? b.get(name) : new Bone();
                NBTTagCompound transform = transforms.hasKey(name, 10)
                        ? transforms.getCompoundTag(name).copy() : new NBTTagCompound();

                String[] p = {"X", "Y", "Z"};
                String[] r = {"RX", "RY", "RZ"};
                String[] sc = {"SX", "SY", "SZ"};
                for (int axis = 0; axis < 3; axis++)
                {
                    transform.setFloat(p[axis], mix(left.pos[axis], right.pos[axis], factor));
                    float angle = mix(left.rot[axis], right.rot[axis], factor);
                    transform.setFloat(r[axis], (float) Math.toRadians(axis < 2 ? -angle : angle));
                    transform.setFloat(sc[axis], mix(left.scale[axis], right.scale[axis], factor));
                }
                // Replace the native action on these bones, rather than add to it.
                transform.setBoolean("F", false);
                transforms.setTag(name, transform);
            }
            pose.setTag("Pose", transforms);
            pose.setBoolean("Animated", true);
            tag.setTag("Pose", pose);
            tag.setBoolean("ActionPlayer", false);
            tag.removeTag("Transition");
            AbstractMorph out = MorphManager.INSTANCE.morphFromNBT(tag);
            return out;
        }
        catch (Exception error)
        {
            System.err.println("[BBS Editor] Direct viseme blend unavailable for " + from
                    + " -> " + to + ": " + error);
            return null;
        }
    }
}

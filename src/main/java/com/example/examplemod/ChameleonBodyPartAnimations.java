package com.example.examplemod;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

/** Manual Character-key switching of native Chameleon animation action. */
public final class ChameleonBodyPartAnimations
{
    private static final String KEY = "ChameleonAnimation";
    private ChameleonBodyPartAnimations() {}

    public static boolean isChameleon(BodyPartModelData part, int frame)
    {
        if (part == null) return false;
        AbstractMorph morph = part.getMorphAt(frame);
        return morph != null && morph.getClass().getName().endsWith(".ChameleonMorph");
    }

    public static List<String> listAnimations()
    {
        File file = LipSyncBoneBaker.animationFile();
        ArrayList<String> names = new ArrayList<String>();
        if (!file.isFile()) return names;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonObject animations = root.getAsJsonObject("animations");
            if (animations != null)
                for (Map.Entry<String, JsonElement> e : animations.entrySet())
                    names.add(e.getKey());
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        }
        catch (Exception error)
        {
            System.err.println("[BBS Editor] Failed to list Chameleon animations: " + error);
        }
        return names;
    }

    public static void set(CharacterKey key, String animation)
    {
        if (key == null || animation == null || animation.trim().isEmpty()) return;
        NBTTagCompound data = key.getData();
        data.setString(KEY, animation.trim());
        key.setData(data);
        key.setType(CharacterKey.Type.ANIMATION);
    }

    public static String get(BodyPartModelData part, int frame)
    {
        if (part == null) return "";
        String current = "";
        for (CharacterKey key : part.getCharacterTimeline().getAllKeys())
        {
            if (key == null || key.getFrame() > frame) continue;
            NBTTagCompound data = key.getData();
            if (data.hasKey(KEY, 8))
                current = data.getString(KEY);
        }
        return current;
    }

    public static AbstractMorph apply(BodyPartModelData part, int frame, AbstractMorph morph)
    {
        if (morph == null || !morph.getClass().getName().endsWith(".ChameleonMorph"))
            return morph;
        String clip = get(part, frame);
        if (clip.isEmpty()) return morph;
        try
        {
            NBTTagCompound tag = morph.toNBT();
            NBTTagCompound actions = tag.hasKey("Actions", 10)
                    ? tag.getCompoundTag("Actions").copy() : new NBTTagCompound();
            actions.setString("animation", clip);
            tag.setTag("Actions", actions);
            AbstractMorph output = MorphManager.INSTANCE.morphFromNBT(tag);
            return output == null ? morph : output;
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor] Cannot apply Chameleon animation '" + clip + "': " + error);
            return morph;
        }
    }
}

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
    private static final String MODE_KEY = "ChameleonAnimationMode";
    private static final String PLAY = "play";
    private static final String HOLD = "hold";
    private ChameleonBodyPartAnimations() {}

    public static boolean isChameleon(BodyPartModelData part, int frame)
    {
        if (part == null) return false;
        AbstractMorph morph = part.getMorphAt(frame);
        return morph != null && morph.getClass().getName().endsWith(".ChameleonMorph");
    }

    /** Read animation identifiers from the selected Chameleon model, never the Lip Sync fixture. */
    public static List<String> listAnimations(BodyPartModelData part, int frame)
    {
        TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (!isChameleon(part, frame)) return new ArrayList<String>(names);

        AbstractMorph morph = part.getMorphAt(frame);
        String model = morph.name == null ? "" : morph.name;
        if (model.startsWith("chameleon.")) model = model.substring("chameleon.".length());
        model = model.replace('\\', '/');

        File root = new File(Minecraft.getMinecraft().mcDataDir, "config/chameleon/models");
        try
        {
            File safeRoot = root.getCanonicalFile();
            File folder = new File(safeRoot, model).getCanonicalFile();
            if (!folder.toPath().startsWith(safeRoot.toPath()) || !folder.isDirectory())
            {
                System.err.println("[BBS Editor] Chameleon model folder not found: " + folder);
                return new ArrayList<String>(names);
            }

            // Chameleon allows animation files in the model folder and in animations/.
            readAnimationFiles(folder, names);
            readAnimationFiles(new File(folder, "animations"), names);
        }
        catch (IOException error)
        {
            System.err.println("[BBS Editor] Failed to locate Chameleon animations: " + error);
        }
        return new ArrayList<String>(names);
    }

    private static void readAnimationFiles(File folder, Set<String> names)
    {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files)
        {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.ROOT).endsWith(".json")
                    || file.getName().toLowerCase(Locale.ROOT).endsWith(".geo.json")) continue;
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
            {
                JsonElement parsed = new JsonParser().parse(reader);
                if (!parsed.isJsonObject()) continue;
                JsonObject animations = parsed.getAsJsonObject().getAsJsonObject("animations");
                if (animations == null) continue;
                for (Map.Entry<String, JsonElement> entry : animations.entrySet())
                    names.add(entry.getKey());
            }
            catch (Exception error)
            {
                System.err.println("[BBS Editor] Cannot read Chameleon animations from " + file + ": " + error);
            }
        }
    }

    public static void set(CharacterKey key, String animation)
    {
        if (key == null || animation == null || animation.trim().isEmpty()) return;
        NBTTagCompound data = key.getData();
        data.setString(KEY, animation.trim());
        if (!data.hasKey(MODE_KEY, 8)) data.setString(MODE_KEY, HOLD);
        key.setData(data);
        key.setType(CharacterKey.Type.ANIMATION);
    }

    public static String get(BodyPartModelData part, int frame)
    {
        if (part == null) return "";
        String current = "";
        int latest = -1;
        for (CharacterTrack track : part.getCharacterTimeline().getTracks())
        {
            if (track == null) continue;
            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > frame || key.getFrame() < latest)
                    continue;
                NBTTagCompound data = key.getData();
                if (data.hasKey(KEY, 8))
                {
                    current = data.getString(KEY);
                    latest = key.getFrame();
                }
            }
        }
        return current;
    }

    /** Resolve the last explicit animation key without creating timeline keys. */
    public static CharacterKey activeKey(BodyPartModelData part, int frame)
    {
        if (part == null) return null;
        CharacterKey latest = null;
        for (CharacterTrack track : part.getCharacterTimeline().getTracks())
        {
            if (track == null) continue;
            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() > frame || !key.getData().hasKey(KEY, 8)) continue;
                if (latest == null || key.getFrame() > latest.getFrame()) latest = key;
            }
        }
        return latest;
    }

    public static int elapsedFrames(BodyPartModelData part, int frame)
    {
        CharacterKey key = activeKey(part, frame);
        return key == null ? 0 : Math.max(0, frame - key.getFrame());
    }

    public static boolean isPlaying(BodyPartModelData part, int frame)
    {
        CharacterKey key = activeKey(part, frame);
        return key != null && PLAY.equalsIgnoreCase(key.getData().getString(MODE_KEY));
    }

    public static void setPlaying(CharacterKey key, boolean playing)
    {
        if (key == null) return;
        NBTTagCompound data = key.getData();
        data.setString(MODE_KEY, playing ? PLAY : HOLD);
        key.setData(data);
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

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

    /**
     * A viseme is a held static pose. Blend immediately BEFORE the next key,
     * as in a speech timeline, instead of changing the mouth after the sound.
     */
    private static final int PHONEME_TRANSITION_FRAMES = 3;

    private static CharacterKey nextKey(BodyPartModelData part, int frame)
    {
        CharacterKey next = null;
        for (CharacterTrack track : part.getCharacterTimeline().getTracks())
        {
            if (track == null) continue;
            for (CharacterKey key : track.getKeys())
            {
                if (key == null || key.getFrame() <= frame
                        || !key.getData().hasKey(KEY, 8)) continue;
                if (next == null || key.getFrame() < next.getFrame()) next = key;
            }
        }
        return next;
    }

    private static boolean playMode(CharacterKey key)
    {
        return key != null && PLAY.equalsIgnoreCase(key.getData().getString(MODE_KEY));
    }

    /** Native Chameleon action parameters, detached from the source morph. */
    private static AbstractMorph withClip(AbstractMorph source, String clip,
            boolean playing, int elapsed, boolean transitioning, int duration)
    {
        NBTTagCompound tag = source.toNBT();
        NBTTagCompound actions = tag.hasKey("Actions", 10)
                ? tag.getCompoundTag("Actions").copy() : new NBTTagCompound();
        NBTTagCompound config = new NBTTagCompound();
        config.setString("Name", clip);
        config.setBoolean("Clamp", true);
        config.setFloat("Speed", playing || transitioning ? 1F : 0F);
        config.setInteger("Tick", playing && !transitioning ? elapsed : 0);
        config.setInteger("Fade", transitioning ? duration : 0);
        actions.setTag("animation", config);
        tag.setTag("Actions", actions);
        tag.setBoolean("ActionPlayer", true);

        if (transitioning)
        {
            NBTTagCompound transition = new NBTTagCompound();
            transition.setBoolean("Animates", true);
            transition.setInteger("Duration", duration);
            tag.setTag("Transition", transition);
        }
        AbstractMorph output = MorphManager.INSTANCE.morphFromNBT(tag);
        return output == null ? source : output;
    }

    public static AbstractMorph apply(BodyPartModelData part, int frame, AbstractMorph morph)
    {
        if (morph == null || !morph.getClass().getName().endsWith(".ChameleonMorph"))
            return morph;

        CharacterKey current = activeKey(part, frame);
        if (current == null) return morph;
        String clip = current.getData().getString(KEY);
        if (clip.isEmpty()) return morph;

        try
        {
            CharacterKey next = nextKey(part, frame);
            boolean playing = playMode(current);
            boolean nextPlaying = playMode(next);

            // Only static phonemes are blended. Full motion clips keep their
            // native playback timing and start at their explicit key.
            if (!playing && next != null && !nextPlaying)
            {
                String target = next.getData().getString(KEY);
                int available = next.getFrame() - current.getFrame();
                // Reserve at least one full frame for the current static viseme.
                // 3-frame gaps -> 2-frame transition; 4+ gaps -> at most 3.
                // A 1-frame gap cannot contain an intermediate blend.
                int duration = Math.min(PHONEME_TRANSITION_FRAMES,
                        Math.max(0, available - 1));
                int start = next.getFrame() - duration;

                if (duration > 0 && !clip.equals(target) && frame >= start)
                {
                    // At the first transition frame, preserve the old pose
                    // exactly. The native Chameleon fade can otherwise expose
                    // the target pose immediately when its previous action
                    // has not yet been initialized by the preview renderer.
                    if (frame == start)
                    {
                        return withClip(morph, clip, false, 0, false, 0);
                    }

                    float progress = (frame - start) / (float) duration;
                    // Smoothstep-like sine easing, deterministic on scrubbing.
                    float eased = (float) (0.5D - 0.5D * Math.cos(Math.PI * progress));
                    AbstractMorph direct = ChameleonVisemeBlender.blend(morph, clip, target, eased);
                    if (direct != null) return direct;

                    // Fallback for models containing dynamic Molang animation channels.
                    int nativeDuration = 100;
                    int nativeOffset = Math.max(0, Math.min(nativeDuration,
                            Math.round(nativeDuration * eased)));

                    AbstractMorph output = withClip(morph, target, false,
                            0, true, nativeDuration);
                    AbstractMorph previous = withClip(morph, clip, false,
                            0, false, 0);
                    // Chameleon's pause() captures the old ActionPlayback and
                    // sets transition progress without adding Character keys.
                    if (output != null && previous != null)
                    {
                        try
                        {
                            java.lang.reflect.Method pause = output.getClass()
                                    .getMethod("pause", AbstractMorph.class, int.class);
                            pause.invoke(output, previous, nativeOffset);
                        }
                        catch (ReflectiveOperationException error)
                        {
                            System.err.println("[BBS Editor] Cannot initialize Chameleon transition: " + error);
                        }
                    }
                    return output;
                }
            }

            return withClip(morph, clip, playing,
                    Math.max(0, frame - current.getFrame()), false, 0);
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor] Cannot apply Chameleon animation '" + clip + "': " + error);
            return morph;
        }
    }
}

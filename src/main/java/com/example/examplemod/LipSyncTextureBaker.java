package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import mchorse.metamorph.api.morphs.AbstractMorph;

/**
 * Writes generated text visemes into the existing Body Parts Character/Skin keys.
 * It deliberately does not alter any actor track or internal bone animation.
 */
public final class LipSyncTextureBaker
{
    private LipSyncTextureBaker() {}

    public static String findTexture(NBTTagCompound morph)
    {
        return findTexture(morph, 0);
    }

    private static String findTexture(NBTTagCompound tag, int depth)
    {
        if (tag == null || depth > 10) return null;
        for (String key : tag.getKeySet())
        {
            NBTBase base = tag.getTag(key);
            if (base == null) continue;
            if (base.getId() == 8 && isTextureField(key))
            {
                String value = tag.getString(key);
                if (!value.trim().isEmpty()) return value;
            }
            if (base.getId() == 10)
            {
                String nested = findTexture((NBTTagCompound) base, depth + 1);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean isTextureField(String key)
    {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.equals("skin") || lower.equals("texture") ||
                lower.equals("texturepath") || lower.equals("texture_path");
    }

    private static boolean replaceTexture(NBTTagCompound tag, String path, int depth)
    {
        if (depth > 10) return false;
        for (String key : tag.getKeySet())
        {
            NBTBase base = tag.getTag(key);
            if (base == null) continue;
            if (base.getId() == 8 && isTextureField(key))
            {
                tag.setString(key, path);
                return true;
            }
            if (base.getId() == 10 &&
                    replaceTexture((NBTTagCompound) base, path, depth + 1))
            {
                return true;
            }
        }
        return false;
    }

    private static String filename(LipSyncViseme viseme)
    {
        return viseme == LipSyncViseme.IDLE ? "Normal.png"
                : viseme.name().toLowerCase(Locale.ROOT) + ".png";
    }

    public static int generate(BodyPartModelData model, String text,
                               int startFrame, float wpm, String idleTexture)
    {
        if (model == null || !model.hasMorphModel())
            throw new IllegalArgumentException("Choose a morph-backed Mouth Body Part");
        if (idleTexture == null || idleTexture.trim().isEmpty())
            throw new IllegalArgumentException("Enter the full idle texture resource path");

        List<LipSyncTimeline.Key> sequence =
                LipSyncTimeline.fromText(text, startFrame, wpm);
        if (sequence.isEmpty()) throw new IllegalArgumentException("Enter speech text");

        return bake(model, sequence, startFrame, idleTexture);
    }

    public static int generateTimed(BodyPartModelData model,
                                    List<LipSyncTimeline.Key> sequence,
                                    int startFrame, String idleTexture)
    {
        if (sequence == null || sequence.isEmpty())
            throw new IllegalArgumentException("No recognized speech");
        if (model == null || !model.hasMorphModel())
            throw new IllegalArgumentException("Choose a morph-backed Mouth Body Part");
        return bake(model, sequence, startFrame, idleTexture);
    }

    private static int bake(BodyPartModelData model,
                            List<LipSyncTimeline.Key> sequence,
                            int startFrame, String idleTexture)
    {
        if (idleTexture == null || idleTexture.trim().isEmpty())
            throw new IllegalArgumentException("Enter the idle texture path");
        AbstractMorph source = model.getMorphAt(startFrame);
        if (source == null) throw new IllegalArgumentException("Body Part has no source morph");
        NBTTagCompound template = source.toNBT();
        CharacterKey prior = model.getCharacterStateKeyAt(startFrame);
        if (prior != null && prior.hasSkin() && prior.getFrame() <= startFrame)
            template = prior.getSkin();
        if (findTexture(template) == null)
            throw new IllegalArgumentException("Cannot locate Skin/Texture field in morph NBT");

        String base = idleTexture.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        String folder = slash < 0 ? "" : base.substring(0, slash + 1);

        List<Integer> frames = new ArrayList<Integer>();
        List<NBTTagCompound> payloads = new ArrayList<NBTTagCompound>();
        CharacterTimelineController timeline = model.getCharacterTimeline();
        for (LipSyncTimeline.Key step : sequence)
        {
            if (step.frame < model.getStartFrame() || step.frame >= model.getEndFrame())
                throw new IllegalArgumentException("Speech extends past Body Part frame range");
            String path = step.viseme == LipSyncViseme.IDLE
                    ? base : folder + filename(step.viseme);
            NBTTagCompound nbt = template.copy();
            if (!replaceTexture(nbt, path, 0))
                throw new IllegalArgumentException("Failed to set texture in morph NBT");
            // Do not overwrite any existing manual Skin keys.
            for (CharacterTrack track : timeline.getTracks())
            {
                CharacterKey existing = track.getKeyAtFrame(step.frame);
                if (existing != null && existing.hasSkin())
                    throw new IllegalArgumentException("Manual Skin key exists at frame " + step.frame);
            }
            frames.add(step.frame);
            payloads.add(nbt);
        }
        for (int i = 0; i < frames.size(); i++)
        {
            CharacterKey key = model.getOrCreateCharacterStateKey(frames.get(i));
            key.setSkin(payloads.get(i));
        }
        return frames.size();
    }
}

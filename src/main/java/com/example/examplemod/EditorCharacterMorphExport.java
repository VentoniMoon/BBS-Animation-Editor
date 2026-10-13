package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Stage 7: builds native Blockbuster MorphAction NBT from main Character
 * timeline keys. This is a pure preparation step: it does NOT alter the
 * original record or write anything to disk.
 *
 * Pose, Body Parts, Skin and animation setup need their own morph baking
 * adapters before they can be exported.
 */
public final class EditorCharacterMorphExport
{
    private static final String MORPH_ACTION_NAME = "morph";
    private static final byte DEFAULT_MORPH_ACTION_TYPE = 12;

    private EditorCharacterMorphExport() {}

    public static final class PreparedAction
    {
        public final int frame;
        public final NBTTagCompound nbt;

        private PreparedAction(int frame, NBTTagCompound nbt)
        {
            this.frame = frame;
            this.nbt = nbt.copy();
        }
    }

    /**
     * Prepare MorphAction data without changing Record. Only the main
     * track is eligible: model/body-part tracks do not change the actor.
     *
     * The action type is resolved from this record's action registry,
     * rather than assuming the numerical ID never changes.
     */
    public static List<PreparedAction> prepare(
            BlockbusterSceneActorData actorData)
    {
        List<PreparedAction> result = new ArrayList<PreparedAction>();
        if (actorData == null || !actorData.hasRecord())
        {
            return result;
        }

        BlockbusterRecord record = actorData.getRecord();
        CharacterTimelineController timeline = actorData.getCharacterTimeline();
        if (record == null || timeline == null)
        {
            return result;
        }

        Byte morphId = null;
        BlockbusterActionRegistry registry = record.getActionRegistry();
        if (registry != null)
        {
            for (Map.Entry<Byte, String> entry : registry.getActions().entrySet())
            {
                if (MORPH_ACTION_NAME.equals(entry.getValue()))
                {
                    morphId = entry.getKey();
                    break;
                }
            }
            if (morphId == null && registry.getActions().isEmpty())
            {
                morphId = DEFAULT_MORPH_ACTION_TYPE;
            }
        }

        // An unknown custom registry must not silently be rewritten.
        if (morphId == null)
        {
            return result;
        }

        TreeMap<Integer, NBTTagCompound> byFrame =
                new TreeMap<Integer, NBTTagCompound>();

        for (CharacterTrack track : timeline.getTracks())
        {
            if (track == null || !track.isMainTrack())
            {
                continue;
            }

            for (CharacterKey key : track.getKeys())
            {
                if (key == null || !key.hasMorph())
                {
                    continue;
                }

                NBTTagCompound morph = key.getMorph();
                if (morph.hasNoTags())
                {
                    continue;
                }

                NBTTagCompound action = new NBTTagCompound();
                action.setByte("Type", morphId.byteValue());
                action.setTag("Morph", morph.copy());
                byFrame.put(key.getFrame(), action);
            }
        }

        for (Map.Entry<Integer, NBTTagCompound> entry : byFrame.entrySet())
        {
            result.add(new PreparedAction(entry.getKey(), entry.getValue()));
        }

        return result;
    }
}

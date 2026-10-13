package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Resolve Character appearance for ALL Preview actors.
 * The selected actor's CharacterStateResolver uses the latest Morph/Skin
 * payload, regardless of CharacterKey.Type; backgrounds must do the same.
 */
public final class CharacterMorphResolver
{
    private CharacterMorphResolver() {}

    public static NBTTagCompound getCurrentMorph(
            BlockbusterSceneActorData actorData, int frame)
    {
        if (actorData == null) return null;

        CharacterKey morphKey = null;
        CharacterKey skinKey = null;
        CharacterTimelineController timeline = actorData.getCharacterTimeline();

        if (timeline != null)
        {
            for (CharacterTrack track : timeline.getTracks())
            {
                if (track == null || track.getKeys() == null) continue;
                for (CharacterKey key : track.getKeys())
                {
                    if (key == null || key.getFrame() > frame) continue;

                    if (key.hasMorph() && (morphKey == null
                            || key.getFrame() > morphKey.getFrame()))
                    {
                        morphKey = key;
                    }
                    if (key.hasSkin() && (skinKey == null
                            || key.getFrame() > skinKey.getFrame()))
                    {
                        skinKey = key;
                    }
                }
            }
        }

        /* Skin takes precedence for keys at the same frame. */
        if (skinKey != null && (morphKey == null
                || skinKey.getFrame() >= morphKey.getFrame()))
        {
            NBTTagCompound skin = skinKey.getSkin();
            if (skin != null && !skin.hasNoTags()) return skin;
        }

        if (morphKey != null)
        {
            NBTTagCompound morph = morphKey.getMorph();
            if (morph != null && !morph.hasNoTags()) return morph;
        }

        BlockbusterSceneActor actor = actorData.getActor();
        return actor == null ? null : actor.getMorph();
    }
}

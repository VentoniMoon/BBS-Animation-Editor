package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

public class CharacterMorphResolver
{
    public static NBTTagCompound getCurrentMorph(
            BlockbusterSceneActorData actorData,
            int frame)
    {
        if(actorData == null)
        {
            return null;
        }


        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();


        if(timeline != null)
        {
            CharacterKey result = null;


            for(CharacterTrack track :
                    timeline.getTracks())
            {
                if(track == null)
                {
                    continue;
                }


                for(CharacterKey key :
                        track.getKeys())
                {
                    if(key == null)
                    {
                        continue;
                    }


                    if(key.getType()
                            != CharacterKey.Type.MORPH)
                    {
                        continue;
                    }


                    if(key.getFrame() > frame)
                    {
                        continue;
                    }


                    if(result == null ||
                            key.getFrame() >
                                    result.getFrame())
                    {
                        result = key;
                    }
                }
            }


            if(result != null)
            {
                return result.getCompound("Morph");
            }
        }


        BlockbusterSceneActor actor =
                actorData.getActor();


        if(actor != null)
        {
            return actor.getMorph();
        }


        return null;
    }
}
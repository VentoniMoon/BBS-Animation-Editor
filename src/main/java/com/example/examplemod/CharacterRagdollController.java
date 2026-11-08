package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Keyframed Ragdoll strength stored INSIDE the existing Character timeline.
 * No new file format, actor record, track, or legacy Type is required:
 * CharacterKey.Data.Ragdoll.Weight is an independent channel alongside
 * Morph/Skin/Bones/Animation. Thus adding physics never deletes appearance.
 *
 * The sampler is deliberately read-only and deterministic. A later Jolt
 * solver can use it at both film export and interactive preview frame time.
 */
public final class CharacterRagdollController
{
    public static final String DATA_TAG="Ragdoll";
    public static final String WEIGHT_TAG="Weight";
    private CharacterRagdollController(){}

    public static float clampWeight(float weight)
    {
        if(Float.isNaN(weight)||Float.isInfinite(weight))return 0F;
        return Math.max(0F,Math.min(1F,weight));
    }

    /** Returns -1 if the key does not explicitly author this channel. */
    public static float keyWeight(CharacterKey key)
    {
        if(key==null||!key.hasData(DATA_TAG))return -1F;
        NBTTagCompound tag=key.getCompound(DATA_TAG);
        if(!tag.hasKey(WEIGHT_TAG,99))return -1F;
        return clampWeight(tag.getFloat(WEIGHT_TAG));
    }

    /** Edit the given key in place; do not replace Morph/Skin state. */
    public static void setWeight(CharacterKey key,float weight)
    {
        if(key==null)throw new IllegalArgumentException("Missing CHARACTER key");
        NBTTagCompound tag=key.getCompound(DATA_TAG);
        tag.setFloat(WEIGHT_TAG,clampWeight(weight));
        key.setCompound(DATA_TAG,tag);
    }

    /**
     * Create or reuse the actor's main CHARACTER key at the playhead.
     * Body Part attachment tracks are intentionally never touched.
     */
    public static CharacterKey setWeight(CharacterTimelineController timeline,
            int frame,float weight)
    {
        if(timeline==null)throw new IllegalArgumentException("Missing timeline");
        if(frame<0)throw new IllegalArgumentException("Negative frame");
        CharacterTrack main=timeline.ensureMainTrack();
        CharacterKey key=main.getKeyAtFrame(frame);
        if(key==null)key=main.createKey(frame,CharacterKey.Type.CUSTOM);
        setWeight(key,weight);
        return key;
    }

    /**
     * Before the first Ragdoll key, physics is disabled. Between authored
     * keys strength interpolates linearly; after the last it is held. To
     * cut directly from animation to ragdoll, author adjacent 0/100 keys.
     * Other CHARACTER keys do not interrupt the physics channel.
     */
    public static float sampleWeight(CharacterTimelineController timeline,
            int frame)
    {
        if(timeline==null||frame<0)return 0F;
        CharacterTrack track=timeline.getTrackById("main");
        if(track==null)return 0F;
        CharacterKey previous=null,next=null;
        for(CharacterKey key:track.getKeys())
        {
            if(key==null||keyWeight(key)<0F)continue;
            if(key.getFrame()<=frame &&
                    (previous==null||key.getFrame()>previous.getFrame()))
                previous=key;
            else if(key.getFrame()>frame &&
                    (next==null||key.getFrame()<next.getFrame()))
                next=key;
        }
        if(previous==null)return 0F;
        float left=keyWeight(previous);
        if(next==null)return left;
        int interval=next.getFrame()-previous.getFrame();
        if(interval<=0)return left;
        float t=(frame-previous.getFrame())/(float)interval;
        return clampWeight(left+(keyWeight(next)-left)*t);
    }

    /** A key at the playhead is more useful than an unrelated selected key. */
    public static CharacterKey keyAtFrame(CharacterTimelineController timeline,
            int frame)
    {
        if(timeline==null)return null;
        CharacterTrack main=timeline.getTrackById("main");
        return main==null?null:main.getKeyAtFrame(frame);
    }
}

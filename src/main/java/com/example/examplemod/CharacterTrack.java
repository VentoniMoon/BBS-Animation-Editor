package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Одна безымянная дорожка Character Timeline.
 *
 * Track не знает, что именно он редактирует.
 *
 * Конкретный смысл находится в CharacterKey.
 */
public class CharacterTrack
{
    private String trackId;
    private String label;

    private final List<CharacterKey> keys =
            new ArrayList<CharacterKey>();


    public CharacterTrack()
    {
        this("", "");
    }

    public CharacterTrack(String trackId, String label)
    {
        this.trackId = trackId == null ? "" : trackId;
        this.label = label == null ? "" : label;
    }

    public String getTrackId() { return this.trackId; }
    public void setTrackId(String trackId) { this.trackId = trackId == null ? "" : trackId; }
    public String getLabel() { return this.label; }
    public void setLabel(String label) { this.label = label == null ? "" : label; }
    public boolean isMainTrack() { return "main".equals(this.trackId); }

    /*
     * =========================================================
     * KEYS
     * =========================================================
     */

    public List<CharacterKey> getKeys()
    {
        return this.keys;
    }


    public CharacterKey getKeyAtFrame(
            int frame)
    {
        for (CharacterKey key : this.keys)
        {
            if (key.getFrame() == frame)
            {
                return key;
            }
        }

        return null;
    }


    public void addKey(
            CharacterKey key)
    {
        if (key == null)
        {
            return;
        }

        /*
         * В одной дорожке не должно быть
         * двух ключей на одном кадре.
         *
         * Если ключ уже существует,
         * заменяем его.
         */
        removeKeyAtFrame(
                key.getFrame()
        );

        this.keys.add(key);

        sortKeys();
    }


    public CharacterKey createKey(
            int frame,
            CharacterKey.Type type)
    {
        CharacterKey key =
                new CharacterKey(
                        frame,
                        type
                );

        addKey(key);

        return key;
    }


    public boolean removeKey(
            CharacterKey key)
    {
        if (key == null)
        {
            return false;
        }

        return this.keys.remove(key);
    }


    public boolean removeKeyAtFrame(
            int frame)
    {
        CharacterKey key =
                getKeyAtFrame(frame);

        if (key == null)
        {
            return false;
        }

        return this.keys.remove(key);
    }


    public void clear()
    {
        this.keys.clear();
    }


    /*
     * =========================================================
     * SORTING
     * =========================================================
     */

    public void sortKeys()
    {
        Collections.sort(
                this.keys,
                new Comparator<CharacterKey>()
                {
                    @Override
                    public int compare(
                            CharacterKey a,
                            CharacterKey b)
                    {
                        return Integer.compare(
                                a.getFrame(),
                                b.getFrame()
                        );
                    }
                }
        );
    }


    /*
     * =========================================================
     * NBT
     * =========================================================
     */

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag =
                new NBTTagCompound();

        NBTTagList list =
                new NBTTagList();

        for (CharacterKey key : this.keys)
        {
            list.appendTag(
                    key.toNBT()
            );
        }

        tag.setString("TrackId", this.trackId);
        tag.setString("Label", this.label);

        tag.setTag(
                "Keys",
                list
        );

        return tag;
    }


    public static CharacterTrack fromNBT(
            NBTTagCompound tag)
    {
        CharacterTrack track =
                new CharacterTrack();

        if (tag == null)
        {
            return track;
        }

        if (tag.hasKey("TrackId", 8))
        {
            track.trackId = tag.getString("TrackId");
        }

        if (tag.hasKey("Label", 8))
        {
            track.label = tag.getString("Label");
        }

        if (tag.hasKey("Keys", 9))
        {
            NBTTagList list =
                    tag.getTagList(
                            "Keys",
                            10
                    );

            for (int i = 0;
                 i < list.tagCount();
                 i++)
            {
                CharacterKey key =
                        CharacterKey.fromNBT(
                                list.getCompoundTagAt(i)
                        );

                track.keys.add(key);
            }
        }

        track.sortKeys();

        return track;
    }
}
package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Управляет Character Timeline конкретного Actor.
 *
 * Character Timeline состоит из безымянных дорожек.
 *
 * Каждая дорожка содержит CharacterKey.
 *
 * Пример:
 *
 * Track 0
 *     ├── Key @ 20
 *     └── Key @ 50
 *
 * Track 1
 *     └── Key @ 35
 *
 * Track 2
 *     ├── Key @ 10
 *     └── Key @ 80
 *
 * Сам контроллер не знает, что означает конкретная
 * дорожка. Смысл находится внутри CharacterKey.
 */
public class CharacterTimelineController
{
    private final List<CharacterTrack> tracks =
            new ArrayList<CharacterTrack>();


    /*
     * =========================================================
     * TRACKS
     * =========================================================
     */

    /**
     * Получить все дорожки.
     */
    public List<CharacterTrack> getTracks()
    {
        return this.tracks;
    }


    /**
     * Получить количество дорожек.
     */
    public int getTrackCount()
    {
        return this.tracks.size();
    }


    /**
     * Получить дорожку по индексу.
     */
    public CharacterTrack getTrack(
            int index)
    {
        if (
                index < 0 ||
                        index >= this.tracks.size()
        )
        {
            return null;
        }

        return this.tracks.get(index);
    }


    /**
     * Создать новую безымянную дорожку.
     */
    public CharacterTrack addTrack()
    {
        return addTrack("", "");
    }

    public CharacterTrack addTrack(
            String trackId,
            String label)
    {
        CharacterTrack track =
                new CharacterTrack(
                        trackId,
                        label
                );

        this.tracks.add(track);

        return track;
    }

    public CharacterTrack getTrackById(String trackId)
    {
        if (trackId == null || trackId.length() == 0)
        {
            return null;
        }

        for (CharacterTrack track : this.tracks)
        {
            if (track != null &&
                    trackId.equals(track.getTrackId()))
            {
                return track;
            }
        }

        return null;
    }

    public CharacterTrack ensureMainTrack()
    {
        CharacterTrack main = getTrackById("main");

        if (main == null)
        {
            if (this.tracks.isEmpty())
            {
                main = addTrack("main", "Main Character");
            }
            else
            {
                main = this.tracks.get(0);
                main.setTrackId("main");
                main.setLabel("Main Character");
            }
        }
        else
        {
            main.setLabel("Main Character");
        }

        return main;
    }

    public void syncBodyPartTracks(
            List<BodyPartModelData> models)
    {
        ensureMainTrack();

        java.util.HashSet<String> activeIds =
                new java.util.HashSet<String>();

        if (models != null)
        {
            int number = 1;

            for (BodyPartModelData model : models)
            {
                if (model == null)
                {
                    continue;
                }

                String id = model.getTimelineId();

                if (id == null || id.length() == 0)
                {
                    continue;
                }

                activeIds.add(id);

                CharacterTrack track =
                        getTrackById(id);

                String label = model.getModelName();

                if (label == null || label.length() == 0)
                {
                    label = "Body Part " + number;
                }

                if (track == null)
                {
                    track = addTrack(id, label);
                }
                else
                {
                    track.setLabel(label);
                }

                number++;
            }
        }

        for (int i = this.tracks.size() - 1; i >= 0; i--)
        {
            CharacterTrack track = this.tracks.get(i);

            if (track == null || track.isMainTrack())
            {
                continue;
            }

            String id = track.getTrackId();

            if (id != null &&
                    id.startsWith("bodypart:") &&
                    !activeIds.contains(id))
            {
                this.tracks.remove(i);
            }
        }
    }


    /**
     * Добавить существующую дорожку.
     */
    public void addTrack(
            CharacterTrack track)
    {
        if (track == null)
        {
            return;
        }

        this.tracks.add(track);
    }


    /**
     * Удалить дорожку.
     */
    public boolean removeTrack(
            int index)
    {
        if (
                index < 0 ||
                        index >= this.tracks.size()
        )
        {
            return false;
        }

        this.tracks.remove(index);

        return true;
    }


    /**
     * Удалить конкретную дорожку.
     */
    public boolean removeTrack(
            CharacterTrack track)
    {
        if (track == null)
        {
            return false;
        }

        return this.tracks.remove(track);
    }


    /**
     * Удалить все дорожки.
     */
    public void clear()
    {
        this.tracks.clear();
    }


    /*
     * =========================================================
     * KEY CREATION
     * =========================================================
     */

    /**
     * Создать ключ на указанной дорожке.
     *
     * Если дорожки ещё нет, ничего не создаётся.
     */
    public CharacterKey createKey(
            int trackIndex,
            int frame,
            CharacterKey.Type type)
    {
        CharacterTrack track =
                getTrack(trackIndex);

        if (track == null)
        {
            return null;
        }

        return track.createKey(
                frame,
                type
        );
    }


    /**
     * Создать новую дорожку и сразу создать
     * на ней первый ключ.
     */
    public CharacterKey createTrackWithKey(
            int frame,
            CharacterKey.Type type)
    {
        CharacterTrack track =
                addTrack();

        return track.createKey(
                frame,
                type
        );
    }


    /*
     * =========================================================
     * KEY ACCESS
     * =========================================================
     */

    /**
     * Получить ключ на конкретной дорожке
     * и конкретном кадре.
     */
    public CharacterKey getKeyAtFrame(
            int trackIndex,
            int frame)
    {
        CharacterTrack track =
                getTrack(trackIndex);

        if (track == null)
        {
            return null;
        }

        return track.getKeyAtFrame(frame);
    }


    /**
     * Получить все ключи текущего кадра.
     */
    public List<CharacterKey> getKeysAtFrame(
            int frame)
    {
        List<CharacterKey> result =
                new ArrayList<CharacterKey>();

        for (CharacterTrack track : this.tracks)
        {
            CharacterKey key =
                    track.getKeyAtFrame(frame);

            if (key != null)
            {
                result.add(key);
            }
        }

        return result;
    }


    /**
     * Удалить ключ с конкретной дорожки
     * и кадра.
     */
    public boolean removeKeyAtFrame(
            int trackIndex,
            int frame)
    {
        CharacterTrack track =
                getTrack(trackIndex);

        if (track == null)
        {
            return false;
        }

        return track.removeKeyAtFrame(frame);
    }


    /**
     * Удалить ключ.
     */
    public boolean removeKey(
            int trackIndex,
            CharacterKey key)
    {
        CharacterTrack track =
                getTrack(trackIndex);

        if (track == null)
        {
            return false;
        }

        return track.removeKey(key);
    }


    /*
     * =========================================================
     * TIMELINE INFORMATION
     * =========================================================
     */

    /**
     * Получить последний кадр,
     * на котором есть Character Key.
     */
    public int getLength()
    {
        int length = 0;

        for (CharacterTrack track : this.tracks)
        {
            for (CharacterKey key : track.getKeys())
            {
                length = Math.max(
                        length,
                        key.getFrame() + 1
                );
            }
        }

        return length;
    }


    /**
     * Проверить, есть ли вообще Character Keys.
     */
    public boolean isEmpty()
    {
        if (this.tracks.isEmpty())
        {
            return true;
        }

        for (CharacterTrack track : this.tracks)
        {
            if (!track.getKeys().isEmpty())
            {
                return false;
            }
        }

        return true;
    }


    /*
     * =========================================================
     * NBT
     * =========================================================
     */

    /**
     * Сохранить Character Timeline.
     */
    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag =
                new NBTTagCompound();

        NBTTagList trackList =
                new NBTTagList();

        for (CharacterTrack track : this.tracks)
        {
            trackList.appendTag(
                    track.toNBT()
            );
        }

        tag.setTag(
                "Tracks",
                trackList
        );

        return tag;
    }


    /**
     * Загрузить Character Timeline.
     */
    public static CharacterTimelineController fromNBT(
            NBTTagCompound tag)
    {
        CharacterTimelineController controller =
                new CharacterTimelineController();

        if (tag == null)
        {
            return controller;
        }

        if (tag.hasKey("Tracks", 9))
        {
            NBTTagList trackList =
                    tag.getTagList(
                            "Tracks",
                            10
                    );

            for (
                    int i = 0;
                    i < trackList.tagCount();
                    i++
            )
            {
                CharacterTrack track =
                        CharacterTrack.fromNBT(
                                trackList.getCompoundTagAt(i)
                        );

                controller.addTrack(track);
            }
        }

        return controller;
    }
}
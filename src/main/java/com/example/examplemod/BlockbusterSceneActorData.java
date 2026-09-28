package com.example.examplemod;

/**
 * Связывает Actor Blockbuster Scene
 * с его Animation Record и данными редактора.
 *
 * Структура:
 *
 * BlockbusterSceneActorData
 *     |
 *     +-- BlockbusterSceneActor
 *     |
 *     +-- BlockbusterRecord
 *     |
 *     +-- CharacterTimelineController
 *
 * Character Timeline принадлежит конкретному Actor.
 */
public class BlockbusterSceneActorData
{
    private final BlockbusterSceneActor actor;

    private BlockbusterRecord record;

    /**
     * Character Timeline этого Actor.
     *
     * Это НЕ общий Timeline редактора.
     * Это набор Character Tracks, принадлежащий
     * именно этому персонажу.
     */
    private final CharacterTimelineController characterTimeline;


    public BlockbusterSceneActorData(
            BlockbusterSceneActor actor)
    {
        this.actor = actor;
        this.record = null;

        this.characterTimeline =
                new CharacterTimelineController();
    }


    /*
     * =========================================================
     * ACTOR
     * =========================================================
     */

    public BlockbusterSceneActor getActor()
    {
        return this.actor;
    }


    public String getId()
    {
        if (this.actor == null)
        {
            return "";
        }

        return this.actor.getId();
    }


    public String getName()
    {
        if (this.actor == null)
        {
            return "";
        }

        return this.actor.getName();
    }


    public String getMorphName()
    {
        if (this.actor == null)
        {
            return "";
        }

        if (this.actor.getMorph() == null)
        {
            return "";
        }

        return this.actor
                .getMorph()
                .getString("Name");
    }


    /*
     * =========================================================
     * RECORD
     * =========================================================
     */

    public BlockbusterRecord getRecord()
    {
        return this.record;
    }


    public void setRecord(
            BlockbusterRecord record)
    {
        this.record = record;
    }


    public boolean hasRecord()
    {
        return this.record != null;
    }


    public int getLength()
    {
        if (this.record == null)
        {
            return 0;
        }

        return this.record.getLength();
    }


    /*
     * =========================================================
     * CHARACTER TIMELINE
     * =========================================================
     */

    /**
     * Получить Character Timeline этого Actor.
     */
    public CharacterTimelineController getCharacterTimeline()
    {
        return this.characterTimeline;
    }


    /**
     * Проверить, есть ли Character Tracks.
     */
    public boolean hasCharacterTimeline()
    {
        return this.characterTimeline
                .getTrackCount() > 0;
    }


    /**
     * Количество Character Tracks.
     */
    public int getCharacterTrackCount()
    {
        return this.characterTimeline
                .getTrackCount();
    }


    /**
     * Добавить новую безымянную Character Track.
     */
    public CharacterTrack addCharacterTrack()
    {
        return this.characterTimeline
                .addTrack();
    }


    /**
     * Удалить Character Track.
     */
    public boolean removeCharacterTrack(
            int index)
    {
        return this.characterTimeline
                .removeTrack(index);
    }


    /*
     * =========================================================
     * CHARACTER KEYS
     * =========================================================
     */

    /**
     * Создать Character Key
     * на существующей Track.
     */
    public CharacterKey createCharacterKey(
            int trackIndex,
            int frame,
            CharacterKey.Type type)
    {
        return this.characterTimeline
                .createKey(
                        trackIndex,
                        frame,
                        type
                );
    }


    /**
     * Создать новую Track
     * и сразу добавить на неё Key.
     */
    public CharacterKey createCharacterTrackWithKey(
            int frame,
            CharacterKey.Type type)
    {
        return this.characterTimeline
                .createTrackWithKey(
                        frame,
                        type
                );
    }


    /**
     * Получить Character Key
     * на указанном кадре.
     */
    public CharacterKey getCharacterKeyAtFrame(
            int trackIndex,
            int frame)
    {
        return this.characterTimeline
                .getKeyAtFrame(
                        trackIndex,
                        frame
                );
    }


    /**
     * Получить все Character Keys
     * текущего кадра.
     */
    public java.util.List<CharacterKey> getCharacterKeysAtFrame(
            int frame)
    {
        return this.characterTimeline
                .getKeysAtFrame(frame);
    }
}
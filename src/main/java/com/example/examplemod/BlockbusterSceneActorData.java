package com.example.examplemod;

/**
 * Связывает актера Blockbuster Scene
 * с его Animation Record.
 *
 * Это промежуточный слой между:
 *
 * BlockbusterSceneActor
 * и
 * BlockbusterRecord
 */
public class BlockbusterSceneActorData
{
    /*
     * ---------------------------------------------------------
     * SCENE ACTOR
     * ---------------------------------------------------------
     */

    private final BlockbusterSceneActor actor;

    /*
     * ---------------------------------------------------------
     * RECORD
     * ---------------------------------------------------------
     */

    private BlockbusterRecord record;

    /*
     * ---------------------------------------------------------
     * CONSTRUCTOR
     * ---------------------------------------------------------
     */

    public BlockbusterSceneActorData(
            BlockbusterSceneActor actor)
    {
        this.actor = actor;

        this.record = null;
    }

    /*
     * ---------------------------------------------------------
     * ACTOR
     * ---------------------------------------------------------
     */

    public BlockbusterSceneActor getActor()
    {
        return this.actor;
    }

    /*
     * ---------------------------------------------------------
     * RECORD
     * ---------------------------------------------------------
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

    /*
     * ---------------------------------------------------------
     * RECORD LENGTH
     * ---------------------------------------------------------
     */

    public int getLength()
    {
        if (this.record == null)
        {
            return 0;
        }

        return this.record.getLength();
    }

    /*
     * ---------------------------------------------------------
     * BASIC INFO
     * ---------------------------------------------------------
     */

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
}
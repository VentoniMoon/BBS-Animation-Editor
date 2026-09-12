package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/**
 * Наша модель Blockbuster Scene.
 *
 * Соответствует оригинальному:
 *
 * mchorse.blockbuster.recording.scene.Scene
 */
public class BlockbusterScene
{
    /*
     * ---------------------------------------------------------
     * SCENE DATA
     * ---------------------------------------------------------
     */

    private String filename;

    private String title;

    private String startCommand;
    private String stopCommand;

    private boolean loops;

    /*
     * ---------------------------------------------------------
     * ACTORS
     * ---------------------------------------------------------
     */

    private final List<BlockbusterSceneActor> actors;

    /*
     * ---------------------------------------------------------
     * CONSTRUCTOR
     * ---------------------------------------------------------
     */

    public BlockbusterScene()
    {
        this.filename = "";

        this.title = "";

        this.startCommand = "";
        this.stopCommand = "";

        this.loops = false;

        this.actors =
                new ArrayList<BlockbusterSceneActor>();
    }

    public BlockbusterScene(
            String filename)
    {
        this();

        this.filename =
                filename == null
                        ? ""
                        : filename;
    }

    /*
     * ---------------------------------------------------------
     * GETTERS / SETTERS
     * ---------------------------------------------------------
     */

    public String getFilename()
    {
        return this.filename;
    }

    public void setFilename(
            String filename)
    {
        this.filename =
                filename == null
                        ? ""
                        : filename;
    }

    public String getTitle()
    {
        return this.title;
    }

    public void setTitle(
            String title)
    {
        this.title =
                title == null
                        ? ""
                        : title;
    }

    public String getStartCommand()
    {
        return this.startCommand;
    }

    public void setStartCommand(
            String startCommand)
    {
        this.startCommand =
                startCommand == null
                        ? ""
                        : startCommand;
    }

    public String getStopCommand()
    {
        return this.stopCommand;
    }

    public void setStopCommand(
            String stopCommand)
    {
        this.stopCommand =
                stopCommand == null
                        ? ""
                        : stopCommand;
    }

    public boolean isLoops()
    {
        return this.loops;
    }

    public void setLoops(
            boolean loops)
    {
        this.loops =
                loops;
    }

    public List<BlockbusterSceneActor>
    getActors()
    {
        return this.actors;
    }

    /*
     * ---------------------------------------------------------
     * ACTOR ACCESS
     * ---------------------------------------------------------
     */

    public void addActor(
            BlockbusterSceneActor actor)
    {
        if (actor == null)
        {
            return;
        }

        this.actors.add(actor);
    }

    public BlockbusterSceneActor
    getActor(
            String id)
    {
        if (id == null)
        {
            return null;
        }

        for (
                BlockbusterSceneActor actor :
                this.actors
        )
        {
            if (
                    id.equals(
                            actor.getId()
                    )
            )
            {
                return actor;
            }
        }

        return null;
    }

    public void removeActor(
            String id)
    {
        if (id == null)
        {
            return;
        }

        for (
                int i = this.actors.size() - 1;
                i >= 0;
                i--
        )
        {
            BlockbusterSceneActor actor =
                    this.actors.get(i);

            if (
                    id.equals(
                            actor.getId()
                    )
            )
            {
                this.actors.remove(i);
            }
        }
    }

    public void clearActors()
    {
        this.actors.clear();
    }

    /*
     * ---------------------------------------------------------
     * NBT LOAD
     * ---------------------------------------------------------
     */

    public void fromNBT(
            NBTTagCompound compound)
    {
        this.actors.clear();

        if (compound == null)
        {
            return;
        }

        this.title =
                compound.getString(
                        "Title"
                );

        this.startCommand =
                compound.getString(
                        "StartCommand"
                );

        this.stopCommand =
                compound.getString(
                        "StopCommand"
                );

        this.loops =
                compound.getBoolean(
                        "Loops"
                );

        NBTTagList actorList =
                compound.getTagList(
                        "Actors",
                        10
                );

        for (
                int i = 0;
                i < actorList.tagCount();
                i++
        )
        {
            NBTTagCompound actorTag =
                    actorList
                            .getCompoundTagAt(i);

            BlockbusterSceneActor actor =
                    new BlockbusterSceneActor();

            actor.fromNBT(
                    actorTag
            );

            this.actors.add(
                    actor
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * NBT SAVE
     * ---------------------------------------------------------
     */

    public NBTTagCompound toNBT()
    {
        NBTTagCompound compound =
                new NBTTagCompound();

        NBTTagList actorList =
                new NBTTagList();

        for (
                BlockbusterSceneActor actor :
                this.actors
        )
        {
            actorList.appendTag(
                    actor.toNBT()
            );
        }

        compound.setTag(
                "Actors",
                actorList
        );

        compound.setBoolean(
                "Loops",
                this.loops
        );

        compound.setString(
                "Title",
                this.title
        );

        compound.setString(
                "StartCommand",
                this.startCommand
        );

        compound.setString(
                "StopCommand",
                this.stopCommand
        );

        return compound;
    }
}
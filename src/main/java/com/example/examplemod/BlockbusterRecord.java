package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class BlockbusterRecord
{
    /*
     * Original Blockbuster record signature.
     */
    public static final short SIGNATURE = 148;

    /*
     * File identity.
     */
    private String filename;

    /*
     * Record format version.
     */
    private short version;

    /*
     * Delays before and after the actual recording.
     */
    private int preDelay;
    private int postDelay;

    /*
     * Frames and actions are deliberately stored
     * as two separate timelines.
     *
     * Their indexes correspond to ticks.
     */
    private final List<BlockbusterRecordFrame> frames =
            new ArrayList<BlockbusterRecordFrame>();

    private final List<List<BlockbusterRecordAction>> actions =
            new ArrayList<List<BlockbusterRecordAction>>();

    /*
     * Original Blockbuster player data.
     */
    private NBTTagCompound playerData;

    private BlockbusterActionRegistry actionRegistry =
            new BlockbusterActionRegistry();


    public BlockbusterRecord(
            String filename
    )
    {
        this.filename = filename;
        this.version = SIGNATURE;
    }


    /*
     * Load the in-memory model from NBT.
     */
    public static BlockbusterRecord fromNBT(
            String filename,
            NBTTagCompound nbt
    )
    {
        BlockbusterRecord record =
                new BlockbusterRecord(filename);

        /*
         * Version
         */
        if (nbt.hasKey("Version", 2))
        {
            record.version =
                    nbt.getShort("Version");
        }

        /*
         * Delays
         */
        if (nbt.hasKey("PreDelay"))
        {
            record.preDelay =
                    nbt.getInteger("PreDelay");
        }

        if (nbt.hasKey("PostDelay"))
        {
            record.postDelay =
                    nbt.getInteger("PostDelay");
        }

        /*
         * Player data
         */
        if (nbt.hasKey("PlayerData", 10))
        {
            record.playerData =
                    nbt.getCompoundTag(
                            "PlayerData"
                    ).copy();
        }

        /*
         * Action registry
         */
        if (nbt.hasKey("Actions", 10))
        {
            record.actionRegistry =
                    BlockbusterActionRegistry.fromNBT(
                            nbt.getCompoundTag(
                                    "Actions"
                            )
                    );
        }

        /*
         * Frames
         */
        if (nbt.hasKey("Frames", 9))
        {
            NBTTagList frameList =
                    nbt.getTagList(
                            "Frames",
                            10
                    );

            for (
                    int i = 0;
                    i < frameList.tagCount();
                    i++
            )
            {
                NBTTagCompound frameNBT =
                        frameList.getCompoundTagAt(i);

                BlockbusterRecordFrame frame =
                        BlockbusterRecordFrame
                                .fromNBT(
                                        frameNBT
                                );

                record.frames.add(frame);

                /*
                 * IMPORTANT:
                 *
                 * Actions are copied from the frame
                 * into their own timeline.
                 *
                 * This mirrors the original
                 * Blockbuster Record structure.
                 */
                List<BlockbusterRecordAction>
                        frameActions =
                        frame.getActions();

                if (frameActions == null)
                {
                    record.actions.add(null);
                }
                else
                {
                    record.actions.add(
                            new ArrayList<BlockbusterRecordAction>(
                                    frameActions
                            )
                    );
                }
            }
        }

        return record;
    }


    /*
     * Filename
     */

    public String getFilename()
    {
        return this.filename;
    }

    public void setFilename(
            String filename
    )
    {
        this.filename = filename;
    }


    /*
     * Version
     */

    public short getVersion()
    {
        return this.version;
    }

    public void setVersion(
            short version
    )
    {
        this.version = version;
    }


    /*
     * Delays
     */

    public int getPreDelay()
    {
        return this.preDelay;
    }

    public void setPreDelay(
            int preDelay
    )
    {
        this.preDelay =
                Math.max(
                        0,
                        preDelay
                );
    }


    public int getPostDelay()
    {
        return this.postDelay;
    }

    public void setPostDelay(
            int postDelay
    )
    {
        this.postDelay =
                Math.max(
                        0,
                        postDelay
                );
    }


    /*
     * Frames
     */

    public List<BlockbusterRecordFrame> getFrames()
    {
        return this.frames;
    }


    public BlockbusterRecordFrame getFrame(
            int tick
    )
    {
        if (
                tick < 0 ||
                        tick >= this.frames.size()
        )
        {
            return null;
        }

        return this.frames.get(tick);
    }


    public void setFrame(
            int tick,
            BlockbusterRecordFrame frame
    )
    {
        if (tick < 0)
        {
            return;
        }

        ensureSize(tick + 1);

        this.frames.set(
                tick,
                frame
        );
    }


    public BlockbusterRecordFrame getOrCreateFrame(
            int tick
    )
    {
        if (tick < 0)
        {
            return null;
        }

        ensureSize(tick + 1);

        BlockbusterRecordFrame frame =
                this.frames.get(tick);

        if (frame == null)
        {
            frame =
                    new BlockbusterRecordFrame();

            this.frames.set(
                    tick,
                    frame
            );
        }

        return frame;
    }


    /*
     * Actions
     */

    public List<List<BlockbusterRecordAction>> getActions()
    {
        return this.actions;
    }


    public List<BlockbusterRecordAction> getActions(
            int tick
    )
    {
        if (
                tick < 0 ||
                        tick >= this.actions.size()
        )
        {
            return null;
        }

        return this.actions.get(tick);
    }


    public void setActions(
            int tick,
            List<BlockbusterRecordAction> actions
    )
    {
        if (tick < 0)
        {
            return;
        }

        ensureSize(tick + 1);

        if (actions == null)
        {
            this.actions.set(
                    tick,
                    null
            );
        }
        else
        {
            this.actions.set(
                    tick,
                    new ArrayList<BlockbusterRecordAction>(
                            actions
                    )
            );
        }
    }


    public void addAction(
            int tick,
            BlockbusterRecordAction action
    )
    {
        if (
                tick < 0 ||
                        action == null
        )
        {
            return;
        }

        ensureSize(tick + 1);

        List<BlockbusterRecordAction>
                tickActions =
                this.actions.get(tick);

        if (tickActions == null)
        {
            tickActions =
                    new ArrayList<BlockbusterRecordAction>();

            this.actions.set(
                    tick,
                    tickActions
            );
        }

        tickActions.add(action);
    }


    /*
     * Length
     */

    public int getLength()
    {
        return Math.max(
                this.frames.size(),
                this.actions.size()
        );
    }


    public int getFullLength()
    {
        return this.preDelay
                + this.getLength()
                + this.postDelay;
    }


    /*
     * Player data
     */

    public NBTTagCompound getPlayerData()
    {
        if (this.playerData == null)
        {
            return null;
        }

        return this.playerData.copy();
    }


    public void setPlayerData(
            NBTTagCompound playerData
    )
    {
        if (playerData == null)
        {
            this.playerData = null;
        }
        else
        {
            this.playerData =
                    playerData.copy();
        }
    }

    /*
     * Action registry
     */

    public BlockbusterActionRegistry getActionRegistry()
    {
        return this.actionRegistry;
    }


    public void setActionRegistry(
            BlockbusterActionRegistry actionRegistry
    )
    {
        if (actionRegistry == null)
        {
            this.actionRegistry =
                    new BlockbusterActionRegistry();
        }
        else
        {
            this.actionRegistry =
                    actionRegistry;
        }
    }


    /*
     * Internal helper.
     *
     * Frames and actions always have matching
     * timeline capacity.
     */
    private void ensureSize(
            int size
    )
    {
        while (this.frames.size() < size)
        {
            this.frames.add(null);
        }

        while (this.actions.size() < size)
        {
            this.actions.add(null);
        }
    }
}
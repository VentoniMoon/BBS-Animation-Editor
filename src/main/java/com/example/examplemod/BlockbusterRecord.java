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
     * ---------------------------------------------------------
     * LOAD FROM NBT
     * ---------------------------------------------------------
     */

    /**
     * Загружает Blockbuster Record из NBT.
     *
     * Важно:
     *
     * Actions находятся внутри Frame в оригинальном
     * формате Blockbuster.
     *
     * Одновременно мы поддерживаем отдельный список
     * actions, который используется редактором.
     */
    public static BlockbusterRecord fromNBT(
            String filename,
            NBTTagCompound nbt
    )
    {
        BlockbusterRecord record =
                new BlockbusterRecord(filename);

        if (nbt == null)
        {
            return record;
        }

        /*
         * -----------------------------------------------------
         * Version
         * -----------------------------------------------------
         */

        if (nbt.hasKey("Version", 2))
        {
            record.version =
                    nbt.getShort("Version");
        }


        /*
         * -----------------------------------------------------
         * Delays
         * -----------------------------------------------------
         */

        if (nbt.hasKey("PreDelay"))
        {
            record.preDelay =
                    Math.max(
                            0,
                            nbt.getInteger("PreDelay")
                    );
        }

        if (nbt.hasKey("PostDelay"))
        {
            record.postDelay =
                    Math.max(
                            0,
                            nbt.getInteger("PostDelay")
                    );
        }


        /*
         * -----------------------------------------------------
         * Player data
         * -----------------------------------------------------
         */

        if (nbt.hasKey("PlayerData", 10))
        {
            record.playerData =
                    nbt.getCompoundTag(
                            "PlayerData"
                    ).copy();
        }
        else
        {
            record.playerData = null;
        }


        /*
         * -----------------------------------------------------
         * Action registry
         * -----------------------------------------------------
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
        else
        {
            record.actionRegistry =
                    new BlockbusterActionRegistry();
        }


        /*
         * -----------------------------------------------------
         * Frames
         * -----------------------------------------------------
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
                        BlockbusterRecordFrame.fromNBT(
                                frameNBT
                        );

                record.frames.add(frame);

                /*
                 * Actions are mirrored into the separate
                 * action timeline.
                 */
                List<BlockbusterRecordAction> frameActions =
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


        /*
         * -----------------------------------------------------
         * Separate Actions timeline
         * -----------------------------------------------------
         *
         * Если присутствует отдельный Actions список,
         * он имеет приоритет над копией из Frames.
         *
         * Это позволяет редактору сохранять изменения
         * Action Timeline без разрушения исходных кадров.
         */

        if (nbt.hasKey("ActionTimeline", 9))
        {
            NBTTagList actionTimeline =
                    nbt.getTagList(
                            "ActionTimeline",
                            10
                    );

            record.actions.clear();

            for (
                    int i = 0;
                    i < actionTimeline.tagCount();
                    i++
            )
            {
                NBTTagCompound tickNBT =
                        actionTimeline.getCompoundTagAt(i);

                List<BlockbusterRecordAction> tickActions =
                        new ArrayList<BlockbusterRecordAction>();

                if (tickNBT.hasKey("Actions", 9))
                {
                    NBTTagList actionList =
                            tickNBT.getTagList(
                                    "Actions",
                                    10
                            );

                    for (
                            int j = 0;
                            j < actionList.tagCount();
                            j++
                    )
                    {
                        tickActions.add(
                                BlockbusterRecordAction.fromNBT(
                                        actionList.getCompoundTagAt(j)
                                )
                        );
                    }
                }

                record.actions.add(tickActions);
            }

            /*
             * Frame and action timelines must have matching
             * capacity.
             */
            record.ensureSize(
                    Math.max(
                            record.frames.size(),
                            record.actions.size()
                    )
            );
        }


        /*
         * -----------------------------------------------------
         * Synchronize actions with frames
         * -----------------------------------------------------
         *
         * После загрузки приводим Frame -> Actions
         * в соответствие с отдельным Action Timeline.
         */

        record.synchronizeActionsToFrames();


        return record;
    }


    /*
     * ---------------------------------------------------------
     * SAVE TO NBT
     * ---------------------------------------------------------
     */

    /**
     * Сохраняет весь Blockbuster Record обратно в NBT.
     *
     * Этот метод является парой к fromNBT().
     *
     * В результате:
     *
     * BlockbusterRecord
     *       |
     *       +-- Version
     *       +-- PreDelay
     *       +-- PostDelay
     *       +-- PlayerData
     *       +-- Actions
     *       +-- Frames
     *       +-- ActionTimeline
     */
    public NBTTagCompound toNBT()
    {
        NBTTagCompound nbt =
                new NBTTagCompound();


        /*
         * -----------------------------------------------------
         * Version
         * -----------------------------------------------------
         */

        nbt.setShort(
                "Version",
                this.version
        );


        /*
         * -----------------------------------------------------
         * Delays
         * -----------------------------------------------------
         */

        if (this.preDelay != 0)
        {
            nbt.setInteger(
                    "PreDelay",
                    this.preDelay
            );
        }

        if (this.postDelay != 0)
        {
            nbt.setInteger(
                    "PostDelay",
                    this.postDelay
            );
        }


        /*
         * -----------------------------------------------------
         * Player data
         * -----------------------------------------------------
         */

        if (this.playerData != null)
        {
            nbt.setTag(
                    "PlayerData",
                    this.playerData.copy()
            );
        }


        /*
         * -----------------------------------------------------
         * Action registry
         * -----------------------------------------------------
         */

        if (this.actionRegistry != null)
        {
            nbt.setTag(
                    "Actions",
                    this.actionRegistry.toNBT()
            );
        }


        /*
         * -----------------------------------------------------
         * Frames
         * -----------------------------------------------------
         *
         * Перед сохранением синхронизируем Actions
         * с соответствующими Frame.
         */

        synchronizeActionsToFrames();

        NBTTagList frameList =
                new NBTTagList();

        for (
                int i = 0;
                i < this.frames.size();
                i++
        )
        {
            BlockbusterRecordFrame frame =
                    this.frames.get(i);

            /*
             * Даже null frame должен иметь
             * соответствующий пустой Compound,
             * чтобы индекс кадра сохранялся.
             */
            if (frame == null)
            {
                frame =
                        new BlockbusterRecordFrame();
            }

            /*
             * Actions должны находиться внутри
             * соответствующего Frame.
             *
             * Сам BlockbusterRecordFrame хранит список
             * действий, поэтому здесь мы просто
             * сериализуем его.
             */
            frameList.appendTag(
                    frame.toNBT()
            );
        }

        nbt.setTag(
                "Frames",
                frameList
        );


        /*
         * -----------------------------------------------------
         * Separate Action Timeline
         * -----------------------------------------------------
         *
         * Это дополнительное представление для редактора.
         *
         * Оно не заменяет оригинальный Action внутри Frame.
         */

        NBTTagList actionTimeline =
                new NBTTagList();

        for (
                int tick = 0;
                tick < this.actions.size();
                tick++
        )
        {
            List<BlockbusterRecordAction> tickActions =
                    this.actions.get(tick);

            /*
             * Пустые ticks можно не сохранять.
             */
            if (
                    tickActions == null
                            || tickActions.isEmpty()
            )
            {
                continue;
            }

            NBTTagCompound tickNBT =
                    new NBTTagCompound();

            tickNBT.setInteger(
                    "Frame",
                    tick
            );

            NBTTagList actionList =
                    new NBTTagList();

            for (
                    BlockbusterRecordAction action :
                    tickActions
            )
            {
                if (action == null)
                {
                    continue;
                }

                actionList.appendTag(
                        action.getNBT()
                );
            }

            if (actionList.tagCount() > 0)
            {
                tickNBT.setTag(
                        "Actions",
                        actionList
                );

                actionTimeline.appendTag(
                        tickNBT
                );
            }
        }

        if (actionTimeline.tagCount() > 0)
        {
            nbt.setTag(
                    "ActionTimeline",
                    actionTimeline
            );
        }


        return nbt;
    }


    /*
     * ---------------------------------------------------------
     * FILENAME
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * VERSION
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * DELAYS
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * FRAMES
     * ---------------------------------------------------------
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
                tick < 0
                        || tick >= this.frames.size()
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

        ensureSize(
                tick + 1
        );

        this.frames.set(
                tick,
                frame
        );

        /*
         * Keep action timeline synchronized.
         */
        if (frame != null)
        {
            List<BlockbusterRecordAction> frameActions =
                    frame.getActions();

            if (frameActions == null)
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
                                frameActions
                        )
                );
            }
        }
        else
        {
            this.actions.set(
                    tick,
                    null
            );
        }
    }


    public BlockbusterRecordFrame getOrCreateFrame(
            int tick
    )
    {
        if (tick < 0)
        {
            return null;
        }

        ensureSize(
                tick + 1
        );

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

        /*
         * Make sure actions also exist
         * for this tick.
         */
        if (this.actions.get(tick) == null)
        {
            this.actions.set(
                    tick,
                    new ArrayList<BlockbusterRecordAction>()
            );
        }

        return frame;
    }


    /*
     * ---------------------------------------------------------
     * ACTIONS
     * ---------------------------------------------------------
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
                tick < 0
                        || tick >= this.actions.size()
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

        ensureSize(
                tick + 1
        );

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

        /*
         * Keep Frame synchronized.
         */
        BlockbusterRecordFrame frame =
                this.frames.get(tick);

        if (frame != null)
        {
            frame.getActions().clear();

            if (actions != null)
            {
                frame.getActions().addAll(
                        actions
                );
            }
        }
    }


    public void addAction(
            int tick,
            BlockbusterRecordAction action
    )
    {
        if (
                tick < 0
                        || action == null
        )
        {
            return;
        }

        ensureSize(
                tick + 1
        );

        List<BlockbusterRecordAction> tickActions =
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

        tickActions.add(
                action
        );

        /*
         * Keep Frame synchronized.
         */
        BlockbusterRecordFrame frame =
                this.frames.get(tick);

        if (frame != null)
        {
            frame.getActions().add(
                    action
            );
        }
    }


    /*
     * ---------------------------------------------------------
     * ACTION SYNCHRONIZATION
     * ---------------------------------------------------------
     *
     * Frame is the original Blockbuster representation.
     *
     * actions[] is the editor-friendly timeline.
     *
     * Both must describe the same actions.
     */

    private void synchronizeActionsToFrames()
    {
        int size =
                Math.max(
                        this.frames.size(),
                        this.actions.size()
                );

        ensureSize(size);

        for (
                int tick = 0;
                tick < size;
                tick++
        )
        {
            BlockbusterRecordFrame frame =
                    this.frames.get(tick);

            List<BlockbusterRecordAction> tickActions =
                    this.actions.get(tick);

            if (frame == null)
            {
                continue;
            }

            frame.getActions().clear();

            if (tickActions != null)
            {
                frame.getActions().addAll(
                        tickActions
                );
            }
        }
    }


    /*
     * ---------------------------------------------------------
     * LENGTH
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * PLAYER DATA
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * ACTION REGISTRY
     * ---------------------------------------------------------
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
     * ---------------------------------------------------------
     * INTERNAL SIZE
     * ---------------------------------------------------------
     *
     * Frames and actions always have matching
     * timeline capacity.
     */

    private void ensureSize(
            int size
    )
    {
        while (
                this.frames.size()
                        < size
        )
        {
            this.frames.add(null);
        }

        while (
                this.actions.size()
                        < size
        )
        {
            this.actions.add(null);
        }
    }
}
package com.example.examplemod;

import java.util.List;

/**
 * Контроллер Character Mode.
 *
 * Character Mode работает с состоянием Actor
 * на конкретном кадре Blockbuster Record.
 *
 * Общая схема:
 *
 * Timeline
 *     |
 * currentFrame
 *     |
 * BlockbusterRecord
 *     |
 * BlockbusterRecordFrame
 *
 * Важно:
 *
 * Character Mode НЕ создаёт собственную Timeline.
 * Он использует ту же currentFrame, что и весь редактор.
 */
public class CharacterEditorController
{
    private final EditorPlaybackController playbackController;

    public CharacterEditorController(
            EditorPlaybackController playbackController)
    {
        this.playbackController =
                playbackController;
    }


    /*
     * =========================================================
     * CURRENT FRAME
     * =========================================================
     */

    /**
     * Получить текущий кадр Timeline.
     */
    public int getCurrentFrame()
    {
        if (this.playbackController == null)
        {
            return 0;
        }

        return Math.max(
                0,
                this.playbackController.getCurrentFrame()
        );
    }


    /**
     * Получить существующий Frame текущего кадра.
     *
     * Ничего не создаёт.
     */
    public BlockbusterRecordFrame getCurrentFrame(
            BlockbusterRecord record)
    {
        if (record == null)
        {
            return null;
        }

        return record.getFrame(
                getCurrentFrame()
        );
    }


    /**
     * Получить Frame текущего кадра.
     *
     * Если его ещё нет — создать.
     *
     * Именно этот метод используется,
     * когда пользователь начинает редактировать
     * состояние персонажа.
     */
    public BlockbusterRecordFrame getOrCreateCurrentFrame(
            BlockbusterRecord record)
    {
        if (record == null)
        {
            return null;
        }

        return record.getOrCreateFrame(
                getCurrentFrame()
        );
    }


    /**
     * Проверить, существует ли Frame текущего кадра.
     */
    public boolean hasCurrentFrame(
            BlockbusterRecord record)
    {
        return getCurrentFrame(record) != null;
    }


    /*
     * =========================================================
     * POSITION
     * =========================================================
     */

    public double getX(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0D
                : frame.getX();
    }

    public double getY(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0D
                : frame.getY();
    }

    public double getZ(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0D
                : frame.getZ();
    }


    public void setX(
            BlockbusterRecord record,
            double value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setX(value);
    }

    public void setY(
            BlockbusterRecord record,
            double value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setY(value);
    }

    public void setZ(
            BlockbusterRecord record,
            double value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setZ(value);
    }


    /*
     * =========================================================
     * ROTATION
     * =========================================================
     */

    public float getYaw(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getYaw();
    }

    public float getPitch(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getPitch();
    }

    public float getYawHead(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getYawHead();
    }

    public float getBodyYaw(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getBodyYaw();
    }

    public float getRoll(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getRoll();
    }


    public void setYaw(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setYaw(value);
    }

    public void setPitch(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setPitch(value);
    }

    public void setYawHead(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setYawHead(value);
    }

    public void setBodyYaw(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setBodyYaw(value);
    }

    public void setRoll(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setRoll(value);
    }


    /*
     * =========================================================
     * ENTITY STATE
     * =========================================================
     */

    public boolean isGround(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isGround();
    }

    public boolean isAirborne(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isAirborne();
    }

    public boolean isSneaking(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isSneaking();
    }

    public boolean isSprinting(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isSprinting();
    }

    public boolean isFlyingElytra(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isFlyingElytra();
    }


    public void setGround(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setGround(value);
    }

    public void setAirborne(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setAirborne(value);
    }

    public void setSneaking(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setSneaking(value);
    }

    public void setSprinting(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setSprinting(value);
    }

    public void setFlyingElytra(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setFlyingElytra(value);
    }


    /*
     * =========================================================
     * MOUNT
     * =========================================================
     */

    public boolean isMounted(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame != null &&
                frame.isMounted();
    }

    public float getMountYaw(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getMountYaw();
    }

    public float getMountPitch(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getMountPitch();
    }


    public void setMounted(
            BlockbusterRecord record,
            boolean value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMounted(value);
    }

    public void setMountYaw(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMountYaw(value);
    }

    public void setMountPitch(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMountPitch(value);
    }


    /*
     * =========================================================
     * MOTION
     * =========================================================
     */

    public float getMotionX(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getMotionX();
    }

    public float getMotionY(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getMotionY();
    }

    public float getMotionZ(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getMotionZ();
    }


    public void setMotionX(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMotionX(value);
    }

    public void setMotionY(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMotionY(value);
    }

    public void setMotionZ(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setMotionZ(value);
    }


    /*
     * =========================================================
     * FALL DISTANCE
     * =========================================================
     */

    public float getFallDistance(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0.0F
                : frame.getFallDistance();
    }

    public void setFallDistance(
            BlockbusterRecord record,
            float value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setFallDistance(value);
    }


    /*
     * =========================================================
     * HANDS
     * =========================================================
     */

    public byte getActiveHands(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0
                : frame.getActiveHands();
    }

    public void setActiveHands(
            BlockbusterRecord record,
            byte value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setActiveHands(value);
    }


    /*
     * =========================================================
     * PLAYER DATA
     * =========================================================
     */

    public int getHotbarSlot(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0
                : frame.getHotbarSlot();
    }

    public int getFoodLevel(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0
                : frame.getFoodLevel();
    }

    public int getTotalExperience(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        return frame == null
                ? 0
                : frame.getTotalExperience();
    }


    public void setHotbarSlot(
            BlockbusterRecord record,
            int value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setHotbarSlot(value);
    }

    public void setFoodLevel(
            BlockbusterRecord record,
            int value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setFoodLevel(value);
    }

    public void setTotalExperience(
            BlockbusterRecord record,
            int value)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return;
        }

        frame.setTotalExperience(value);
    }


    /*
     * =========================================================
     * ACTIONS
     * =========================================================
     */

    /**
     * Получить Actions текущего кадра.
     *
     * Если Frame не существует, возвращается null.
     */
    public List<BlockbusterRecordAction> getActions(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getCurrentFrame(record);

        if (frame == null)
        {
            return null;
        }

        return frame.getActions();
    }


    /**
     * Получить Actions текущего кадра.
     *
     * Если Frame не существует, создаётся Frame.
     */
    public List<BlockbusterRecordAction> getOrCreateActions(
            BlockbusterRecord record)
    {
        BlockbusterRecordFrame frame =
                getOrCreateCurrentFrame(record);

        if (frame == null)
        {
            return null;
        }

        return frame.getActions();
    }


    /**
     * Добавить Action на текущий кадр.
     *
     * Action хранится непосредственно внутри
     * BlockbusterRecordFrame.
     */
    public void addAction(
            BlockbusterRecord record,
            BlockbusterRecordAction action)
    {
        if (action == null)
        {
            return;
        }

        List<BlockbusterRecordAction> actions =
                getOrCreateActions(record);

        if (actions == null)
        {
            return;
        }

        actions.add(action);
    }


    /**
     * Удалить Action по индексу текущего кадра.
     */
    public boolean removeAction(
            BlockbusterRecord record,
            int index)
    {
        List<BlockbusterRecordAction> actions =
                getActions(record);

        if (actions == null)
        {
            return false;
        }

        if (
                index < 0 ||
                        index >= actions.size()
        )
        {
            return false;
        }

        actions.remove(index);

        return true;
    }


    /*
     * =========================================================
     * UTILITY
     * =========================================================
     */

    /**
     * Получить количество Actions
     * на текущем кадре.
     */
    public int getActionCount(
            BlockbusterRecord record)
    {
        List<BlockbusterRecordAction> actions =
                getActions(record);

        return actions == null
                ? 0
                : actions.size();
    }
}
package com.example.examplemod;

/**
 * Отвечает за обработку Blockbuster Record для текущего кадра редактора.
 */
public class EditorRecordController
{
    private final EditorPlaybackController playbackController;

    private final BlockbusterRecordPose currentRecordPose =
            new BlockbusterRecordPose();

    private final AnimationFrameResolver frameResolver =
            new AnimationFrameResolver();

    private final ActorPose currentActorPose =
            new ActorPose();

    public EditorRecordController(
            EditorPlaybackController playbackController)
    {
        this.playbackController = playbackController;
    }

    /**
     * Применяет текущий кадр Blockbuster Record.
     *
     * ВАЖНО:
     * Используется именно integer currentFrame.
     * Это сохраняет исправление проблемы с дёрганьем
     * актёра на паузе.
     */
    public void applyRecordFrame(
            BlockbusterRecord record)
    {
        if (record == null)
        {
            return;
        }

        int currentFrame =
                this.playbackController.getCurrentFrame();

        BlockbusterRecordPose pose =
                this.frameResolver.resolveRecordPose(
                        record,
                        currentFrame
                );

        if (pose != null)
        {
            this.currentRecordPose.setPosition(
                    pose.getX(),
                    pose.getY(),
                    pose.getZ()
            );

            this.currentRecordPose.setRotation(
                    pose.getYaw(),
                    pose.getPitch()
            );
        }

        AnimationActorTransform actorTransform =
                this.frameResolver.resolveActorTransform(
                        record,
                        currentFrame
                );

        if (actorTransform != null)
        {
            this.currentActorPose.setPosition(
                    actorTransform.getX(),
                    actorTransform.getY(),
                    actorTransform.getZ()
            );

            this.currentActorPose.setRotation(
                    actorTransform.getYaw(),
                    actorTransform.getPitch()
            );
        }
    }

    /**
     * Применяет RecordPose поверх ActorPose.
     */
    public void applyAnimationPose()
    {
        this.currentRecordPose.applyTo(
                this.currentActorPose
        );
    }

    public ActorPose getCurrentActorPose()
    {
        return this.currentActorPose;
    }

    public BlockbusterRecordPose getCurrentRecordPose()
    {
        return this.currentRecordPose;
    }

    /**
     * Возвращает кадр Record для текущего целого кадра Timeline.
     */
    public BlockbusterRecordFrame getCurrentRecordFrame(
            BlockbusterRecord record)
    {
        if (record == null)
        {
            return null;
        }

        return record.getFrame(
                this.playbackController.getCurrentFrame()
        );
    }

    public int getCurrentFrame()
    {
        return this.playbackController.getCurrentFrame();
    }

    /**
     * Сбрасывает текущее состояние Preview.
     */
    public void reset()
    {
        this.currentRecordPose.setPosition(
                0.0D,
                0.0D,
                0.0D
        );

        this.currentRecordPose.setRotation(
                0.0F,
                0.0F
        );

        this.currentActorPose.setPosition(
                0.0D,
                0.0D,
                0.0D
        );

        this.currentActorPose.setRotation(
                0.0F,
                0.0F
        );
    }
}
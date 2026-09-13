package com.example.examplemod;

/**
 * Вычисляет состояние актёра на конкретном кадре
 * на основе настоящего Blockbuster Record.
 *
 * ВАЖНО:
 *
 * RecordFrame НЕ превращается в AnimationKeyframe.
 *
 * Record является источником базового движения актёра,
 * а AnimationBone хранит только пользовательские изменения.
 */
public class AnimationFrameResolver
{
    /**
     * Вычисляет базовую позу актёра
     * непосредственно из Blockbuster Record.
     *
     * @param record настоящий Blockbuster Record
     * @param frame номер кадра
     *
     * @return базовая поза актёра
     */
    public BlockbusterRecordPose resolveRecordPose(
            BlockbusterRecord record,
            int frame)
    {
        if (record == null)
        {
            return new BlockbusterRecordPose();
        }

        if (frame < 0)
        {
            frame = 0;
        }

        BlockbusterRecordFrame recordFrame =
                record.getFrame(frame);

        /*
         * Если Record не содержит данный кадр,
         * возвращаем пустую базовую позу.
         *
         * Позже здесь можно будет добавить
         * более точный fallback к предыдущему кадру.
         */
        if (recordFrame == null)
        {
            return new BlockbusterRecordPose();
        }

        return BlockbusterRecordPoseFactory.fromFrame(
                recordFrame
        );
    }

    /**
     * Преобразует базовую RecordPose
     * в итоговое состояние актёра.
     *
     * На данном этапе пользовательские Bone transforms
     * ещё не изменяют мировое положение самого актёра.
     */
    public AnimationActorTransform resolveActorTransform(
            BlockbusterRecord record,
            int frame)
    {
        BlockbusterRecordPose pose =
                resolveRecordPose(
                        record,
                        frame
                );

        return new AnimationActorTransform(
                pose.getX(),
                pose.getY(),
                pose.getZ(),
                pose.getYaw(),
                pose.getPitch()
        );
    }
}
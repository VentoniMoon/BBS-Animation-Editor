package com.example.examplemod;

/**
 * Преобразует настоящий BlockbusterRecordFrame
 * в базовую позу редактора.
 *
 * Никаких AnimationKeyframe здесь не создаётся.
 */
public class BlockbusterRecordPoseFactory
{
    private BlockbusterRecordPoseFactory()
    {
    }

    public static BlockbusterRecordPose fromFrame(
            BlockbusterRecordFrame frame)
    {
        if (frame == null)
        {
            return new BlockbusterRecordPose();
        }

        return new BlockbusterRecordPose(
                frame.getX(),
                frame.getY(),
                frame.getZ(),
                frame.getYaw(),
                frame.getPitch()
        );
    }
}
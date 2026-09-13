package com.example.examplemod;

import java.util.List;

/**
 * Строит итоговую WORLD-позу всех костей
 * на определённом кадре.
 *
 * ВАЖНО:
 *
 * AnimationBone уже знает:
 *
 * - статическую точку привязки Blockbuster;
 * - пользовательскую AnimationTransform;
 * - родительскую кость;
 * - иерархию модели.
 *
 * Поэтому здесь НЕ нужно повторно реализовывать
 * математику иерархии.
 *
 * AnimationPoseBuilder только собирает
 * результаты AnimationBone в единую AnimationPose.
 */
public class AnimationPoseBuilder
{
    private AnimationPoseBuilder() {}

    /**
     * Строит WORLD-позу на основе AnimationFrameState.
     *
     * AnimationFrameState здесь используется
     * как источник номера текущего кадра.
     *
     * Сами transforms вычисляются AnimationBone,
     * потому что только AnimationBone знает
     * статическую точку привязки кости.
     */
    public static AnimationPose build(
            List<AnimationBone> bones,
            AnimationFrameState state)
    {
        AnimationPose pose =
                new AnimationPose();

        if (
                bones == null
                        || bones.isEmpty()
                        || state == null
        )
        {
            return pose;
        }

        int frame =
                state.getFrame();

        for (
                AnimationBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            /*
             * AnimationBone самостоятельно учитывает:
             *
             * 1. localX/Y/Z
             * 2. пользовательский keyframe
             * 3. rotation
             * 4. scale
             * 5. parent hierarchy
             */
            AnimationTransform worldTransform =
                    bone.getWorldTransformAt(
                            frame
                    );

            if (worldTransform == null)
            {
                continue;
            }

            pose.setTransform(
                    bone.getName(),
                    worldTransform
            );
        }

        return pose;
    }

    /**
     * Compatibility with старым кодом.
     */
    public static AnimationPose build(
            List<AnimationBone> bones,
            int frame)
    {
        AnimationFrameState state =
                AnimationFrameStateBuilder.build(
                        bones,
                        frame
                );

        return build(
                bones,
                state
        );
    }
}
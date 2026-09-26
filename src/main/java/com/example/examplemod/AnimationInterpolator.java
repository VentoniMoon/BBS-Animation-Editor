package com.example.examplemod;

import mchorse.mclib.utils.keyframes.Keyframe;

/**
 * Интерполяция AnimationKeyframe через оригинальную
 * систему интерполяции McLib.
 */
public class AnimationInterpolator
{
    private static final int CHANNEL_POSITION_X = 0;
    private static final int CHANNEL_POSITION_Y = 1;
    private static final int CHANNEL_POSITION_Z = 2;

    private static final int CHANNEL_ROTATION_X = 3;
    private static final int CHANNEL_ROTATION_Y = 4;
    private static final int CHANNEL_ROTATION_Z = 5;

    private static final int CHANNEL_SCALE_X = 6;
    private static final int CHANNEL_SCALE_Y = 7;
    private static final int CHANNEL_SCALE_Z = 8;

    private AnimationInterpolator()
    {
    }

    /**
     * Старый вызов с целым кадром.
     *
     * Оставляем его для совместимости
     * с остальным кодом проекта.
     */
    public static AnimationTransform interpolate(
            AnimationKeyframe previous,
            AnimationKeyframe next,
            int frame)
    {
        return interpolate(
                null,
                previous,
                next,
                null,
                (float) frame
        );
    }

    /**
     * Старый полный вызов с целым кадром.
     *
     * Оставляем его для совместимости.
     */
    public static AnimationTransform interpolate(
            AnimationKeyframe previousPrevious,
            AnimationKeyframe previous,
            AnimationKeyframe next,
            AnimationKeyframe nextNext,
            int frame)
    {
        return interpolate(
                previousPrevious,
                previous,
                next,
                nextNext,
                (float) frame
        );
    }

    /**
     * Полная интерполяция с четырьмя точками
     * и ДРОБНЫМ номером кадра.
     *
     * Например:
     *
     * 10.0
     * 10.25
     * 10.50
     * 10.75
     * 11.0
     *
     * Благодаря этому Preview больше не обязан
     * прыгать только между целыми кадрами.
     */
    public static AnimationTransform interpolate(
            AnimationKeyframe previousPrevious,
            AnimationKeyframe previous,
            AnimationKeyframe next,
            AnimationKeyframe nextNext,
            float frame)
    {
        if (previous == null)
        {
            if (next == null)
            {
                return new AnimationTransform();
            }

            return next.getTransform().copy();
        }

        if (next == null)
        {
            return previous.getTransform().copy();
        }

        AnimationTransform previousTransform =
                previous.getTransform();

        AnimationTransform nextTransform =
                next.getTransform();

        int previousFrame =
                previous.getFrame();

        int nextFrame =
                next.getFrame();

        if (previousFrame == nextFrame)
        {
            return previousTransform.copy();
        }

        float factor =
                (frame - (float) previousFrame)
                        /
                        (float)
                                (nextFrame - previousFrame);

        factor =
                Math.max(
                        0.0F,
                        Math.min(
                                1.0F,
                                factor
                        )
                );

        AnimationTransform result =
                new AnimationTransform();

        /*
         * -----------------------------------------------------
         * Position
         * -----------------------------------------------------
         */

        float positionX =
                interpolateChannel(
                        CHANNEL_POSITION_X,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float positionY =
                interpolateChannel(
                        CHANNEL_POSITION_Y,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float positionZ =
                interpolateChannel(
                        CHANNEL_POSITION_Z,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        result.setPosition(
                positionX,
                positionY,
                positionZ
        );

        /*
         * -----------------------------------------------------
         * Rotation
         * -----------------------------------------------------
         */

        float rotationX =
                interpolateChannel(
                        CHANNEL_ROTATION_X,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float rotationY =
                interpolateChannel(
                        CHANNEL_ROTATION_Y,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float rotationZ =
                interpolateChannel(
                        CHANNEL_ROTATION_Z,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        result.setRotation(
                rotationX,
                rotationY,
                rotationZ
        );

        /*
         * -----------------------------------------------------
         * Scale
         * -----------------------------------------------------
         */

        float scaleX =
                interpolateChannel(
                        CHANNEL_SCALE_X,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float scaleY =
                interpolateChannel(
                        CHANNEL_SCALE_Y,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        float scaleZ =
                interpolateChannel(
                        CHANNEL_SCALE_Z,
                        previousPrevious,
                        previous,
                        next,
                        nextNext,
                        factor
                );

        result.setScale(
                scaleX,
                scaleY,
                scaleZ
        );

        return result;
    }

    /**
     * Интерполирует один конкретный канал.
     */
    private static float interpolateChannel(
            int channel,
            AnimationKeyframe previousPrevious,
            AnimationKeyframe previous,
            AnimationKeyframe next,
            AnimationKeyframe nextNext,
            float factor)
    {
        /*
         * Если внешнего соседа нет, используем
         * ближайший существующий keyframe.
         */
        if (previousPrevious == null)
        {
            previousPrevious = previous;
        }

        if (nextNext == null)
        {
            nextNext = next;
        }

        Keyframe mcPreviousPrevious =
                createMcLibKeyframe(
                        previousPrevious,
                        channel
                );

        Keyframe mcPrevious =
                createMcLibKeyframe(
                        previous,
                        channel
                );

        Keyframe mcNext =
                createMcLibKeyframe(
                        next,
                        channel
                );

        Keyframe mcNextNext =
                createMcLibKeyframe(
                        nextNext,
                        channel
                );

        /*
         * Связываем keyframe так же,
         * как это делает оригинальная система McLib.
         */
        mcPreviousPrevious.next =
                mcPrevious;

        mcPrevious.prev =
                mcPreviousPrevious;

        mcPrevious.next =
                mcNext;

        mcNext.prev =
                mcPrevious;

        mcNext.next =
                mcNextNext;

        mcNextNext.prev =
                mcNext;

        /*
         * McLib использует interpolation/easing
         * первого keyframe пары.
         */
        return (float)
                mcPrevious.interpolate(
                        mcNext,
                        factor
                );
    }

    /**
     * Создаёт временный оригинальный McLib Keyframe
     * для одного канала AnimationTransform.
     */
    private static Keyframe createMcLibKeyframe(
            AnimationKeyframe source,
            int channel)
    {
        float value =
                getChannelValue(
                        source,
                        channel
                );

        Keyframe keyframe =
                new Keyframe(
                        source.getFrame(),
                        value
                );

        keyframe.interp =
                source.getInterpolation();

        keyframe.easing =
                source.getEasing();

        keyframe.rx =
                source.getRX();

        keyframe.ry =
                source.getRY();

        keyframe.lx =
                source.getLX();

        keyframe.ly =
                source.getLY();

        return keyframe;
    }

    /**
     * Получает конкретное значение из
     * AnimationTransform.
     */
    private static float getChannelValue(
            AnimationKeyframe keyframe,
            int channel)
    {
        AnimationTransform transform =
                keyframe.getTransform();

        switch (channel)
        {
            case CHANNEL_POSITION_X:
                return transform.getPositionX();

            case CHANNEL_POSITION_Y:
                return transform.getPositionY();

            case CHANNEL_POSITION_Z:
                return transform.getPositionZ();

            case CHANNEL_ROTATION_X:
                return transform.getRotationX();

            case CHANNEL_ROTATION_Y:
                return transform.getRotationY();

            case CHANNEL_ROTATION_Z:
                return transform.getRotationZ();

            case CHANNEL_SCALE_X:
                return transform.getScaleX();

            case CHANNEL_SCALE_Y:
                return transform.getScaleY();

            case CHANNEL_SCALE_Z:
                return transform.getScaleZ();

            default:
                return 0.0F;
        }
    }
}
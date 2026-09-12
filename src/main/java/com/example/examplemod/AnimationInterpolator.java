package com.example.examplemod;

public class AnimationInterpolator
{
    /**
     * Вычисляет трансформацию между двумя ключевыми кадрами.
     *
     * @param previous предыдущий ключевой кадр
     * @param next следующий ключевой кадр
     * @param frame текущий кадр
     *
     * @return вычисленная трансформация
     */
    public static AnimationTransform interpolate(
            AnimationKeyframe previous,
            AnimationKeyframe next,
            int frame)
    {
        if (previous == null && next == null)
        {
            return new AnimationTransform();
        }

        if (previous == null)
        {
            return copyTransform(
                    next.getTransform()
            );
        }

        if (next == null)
        {
            return copyTransform(
                    previous.getTransform()
            );
        }

        int previousFrame =
                previous.getFrame();

        int nextFrame =
                next.getFrame();

        /*
         * Если оба ключа находятся
         * на одном кадре.
         */
        if (previousFrame == nextFrame)
        {
            return copyTransform(
                    previous.getTransform()
            );
        }

        /*
         * Вычисляем положение текущего
         * кадра между двумя ключами.
         *
         * Например:
         *
         * previous = 0
         * next     = 20
         * frame    = 5
         *
         * progress = 0.25
         */
        float progress =
                (float) (
                        frame - previousFrame
                )
                        /
                        (float) (
                                nextFrame - previousFrame
                        );

        /*
         * Защита от выхода за пределы
         * диапазона.
         */
        if (progress < 0.0F)
        {
            progress = 0.0F;
        }

        if (progress > 1.0F)
        {
            progress = 1.0F;
        }

        AnimationTransform a =
                previous.getTransform();

        AnimationTransform b =
                next.getTransform();

        AnimationTransform result =
                new AnimationTransform();

        /*
         * POSITION
         */
        result.setPosition(
                lerp(
                        a.getPositionX(),
                        b.getPositionX(),
                        progress
                ),
                lerp(
                        a.getPositionY(),
                        b.getPositionY(),
                        progress
                ),
                lerp(
                        a.getPositionZ(),
                        b.getPositionZ(),
                        progress
                )
        );

        /*
         * ROTATION
         */
        result.setRotation(
                lerp(
                        a.getRotationX(),
                        b.getRotationX(),
                        progress
                ),
                lerp(
                        a.getRotationY(),
                        b.getRotationY(),
                        progress
                ),
                lerp(
                        a.getRotationZ(),
                        b.getRotationZ(),
                        progress
                )
        );

        /*
         * SCALE
         */
        result.setScale(
                lerp(
                        a.getScaleX(),
                        b.getScaleX(),
                        progress
                ),
                lerp(
                        a.getScaleY(),
                        b.getScaleY(),
                        progress
                ),
                lerp(
                        a.getScaleZ(),
                        b.getScaleZ(),
                        progress
                )
        );

        return result;
    }

    /**
     * Линейная интерполяция.
     *
     * a = начальное значение
     * b = конечное значение
     * t = положение между ними от 0 до 1
     */
    private static float lerp(
            float a,
            float b,
            float t)
    {
        return a + (b - a) * t;
    }

    /**
     * Создаёт независимую копию трансформации.
     */
    private static AnimationTransform copyTransform(
            AnimationTransform source)
    {
        AnimationTransform result =
                new AnimationTransform();

        result.setPosition(
                source.getPositionX(),
                source.getPositionY(),
                source.getPositionZ()
        );

        result.setRotation(
                source.getRotationX(),
                source.getRotationY(),
                source.getRotationZ()
        );

        result.setScale(
                source.getScaleX(),
                source.getScaleY(),
                source.getScaleZ()
        );

        return result;
    }
}
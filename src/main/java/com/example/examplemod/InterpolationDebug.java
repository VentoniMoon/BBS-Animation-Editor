package com.example.examplemod;

import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;

/**
 * Времочный отладочный тест оригинальной
 * системы интерполяции McLib.
 *
 * Этот класс не изменяет AnimationBone.
 */
public class InterpolationDebug
{
    private InterpolationDebug()
    {
    }

    /**
     * Запускает тест всех доступных
     * типов интерполяции McLib.
     */
    public static void runTest()
    {
        System.out.println(
                "========== McLib Interpolation Test =========="
        );

        for (KeyframeInterpolation interpolation :
                KeyframeInterpolation.values())
        {
            testInterpolation(
                    interpolation
            );
        }

        System.out.println(
                "========== McLib Interpolation Test END =========="
        );
    }

    /**
     * Проверяет один тип интерполяции
     * с тремя вариантами easing.
     */
    private static void testInterpolation(
            KeyframeInterpolation interpolation)
    {
        System.out.println(
                "[INTERPOLATION] "
                        + interpolation.name()
                        + " / "
                        + interpolation.getKey()
        );

        for (KeyframeEasing easing :
                KeyframeEasing.values())
        {
            AnimationKeyframe previous =
                    createKeyframe(
                            0,
                            0.0F,
                            interpolation,
                            easing
                    );

            AnimationKeyframe next =
                    createKeyframe(
                            20,
                            100.0F,
                            interpolation,
                            easing
                    );

            /*
             * Берём середину между двумя keyframe.
             */
            AnimationTransform result =
                    AnimationInterpolator.interpolate(
                            null,
                            previous,
                            next,
                            null,
                            10
                    );

            System.out.println(
                    "    easing="
                            + easing.name()
                            + " -> "
                            + result.getPositionX()
            );
        }
    }

    /**
     * Создаёт AnimationKeyframe
     * с заданными параметрами.
     */
    private static AnimationKeyframe createKeyframe(
            int frame,
            float positionX,
            KeyframeInterpolation interpolation,
            KeyframeEasing easing)
    {
        AnimationKeyframe keyframe =
                new AnimationKeyframe(
                        frame
                );

        keyframe
                .getTransform()
                .setPosition(
                        positionX,
                        0.0F,
                        0.0F
                );

        keyframe.setInterpolation(
                interpolation
        );

        keyframe.setEasing(
                easing
        );

        return keyframe;
    }
}
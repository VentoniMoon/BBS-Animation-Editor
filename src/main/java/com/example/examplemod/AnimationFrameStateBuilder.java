package com.example.examplemod;

import java.util.List;

/**
 * Вычисляет состояние всех AnimationBone
 * на конкретном кадре.
 *
 * Record сюда пока НЕ передаётся.
 *
 * Record отвечает за мировое положение актёра,
 * а этот класс отвечает только за пользовательскую
 * локальную анимацию костей.
 */
public class AnimationFrameStateBuilder
{
    private AnimationFrameStateBuilder()
    {
    }

    public static AnimationFrameState build(
            List<AnimationBone> bones,
            int frame)
    {
        AnimationFrameState state =
                new AnimationFrameState(
                        frame
                );

        if (
                bones == null
                        || bones.isEmpty()
        )
        {
            return state;
        }

        for (
                AnimationBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            AnimationTransform transform =
                    bone.getTransformAt(
                            frame
                    );

            if (transform == null)
            {
                continue;
            }

            state.setTransform(
                    bone.getName(),
                    transform
            );
        }

        return state;
    }
}
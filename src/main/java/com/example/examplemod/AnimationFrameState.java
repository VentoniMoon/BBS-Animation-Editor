package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Итоговое состояние AnimationBone на одном кадре.
 *
 * ВАЖНО:
 *
 * Этот класс НЕ хранит keyframes.
 *
 * AnimationBone хранит пользовательские keyframes,
 * а AnimationFrameState содержит только результат
 * их вычисления на конкретном кадре.
 */
public class AnimationFrameState
{
    private final int frame;

    private final Map<String, AnimationTransform> transforms =
            new LinkedHashMap<String, AnimationTransform>();

    public AnimationFrameState(int frame)
    {
        this.frame = frame;
    }

    public int getFrame()
    {
        return this.frame;
    }

    public void setTransform(
            String boneName,
            AnimationTransform transform)
    {
        if (
                boneName == null
                        || boneName.isEmpty()
                        || transform == null
        )
        {
            return;
        }

        this.transforms.put(
                boneName,
                transform.copy()
        );
    }

    public AnimationTransform getTransform(
            String boneName)
    {
        if (boneName == null)
        {
            return null;
        }

        AnimationTransform transform =
                this.transforms.get(
                        boneName
                );

        if (transform == null)
        {
            return null;
        }

        return transform.copy();
    }

    public boolean hasTransform(
            String boneName)
    {
        return boneName != null
                && this.transforms.containsKey(
                boneName
        );
    }

    public Map<String, AnimationTransform> getTransforms()
    {
        return this.transforms;
    }

    public void clear()
    {
        this.transforms.clear();
    }
}
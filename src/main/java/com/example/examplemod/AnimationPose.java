package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Итоговое состояние всех AnimationBone
 * на текущем кадре редактора.
 *
 * Важно:
 *
 * AnimationPose НЕ хранит ключевые кадры.
 * Он только вычисляет итоговые transforms
 * костей на конкретном кадре.
 */
public class AnimationPose
{
    private final Map<String, AnimationTransform> transforms =
            new LinkedHashMap<String, AnimationTransform>();

    public void clear()
    {
        this.transforms.clear();
    }

    public void setTransform(
            String boneName,
            AnimationTransform transform)
    {
        if (boneName == null || transform == null)
        {
            return;
        }

        this.transforms.put(
                boneName,
                transform
        );
    }

    public AnimationTransform getTransform(
            String boneName)
    {
        if (boneName == null)
        {
            return null;
        }

        return this.transforms.get(
                boneName
        );
    }

    public boolean hasTransform(
            String boneName)
    {
        return this.transforms.containsKey(
                boneName
        );
    }

    public Map<String, AnimationTransform> getTransforms()
    {
        return this.transforms;
    }
}
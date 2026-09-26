package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationAdapterManager
{
    private final List<AnimationAdapter> adapters;

    public AnimationAdapterManager()
    {
        this.adapters =
                new ArrayList<AnimationAdapter>();
    }

    public void register(
            AnimationAdapter adapter)
    {
        if (adapter == null)
        {
            return;
        }

        if (!this.adapters.contains(adapter))
        {
            this.adapters.add(adapter);
        }
    }

    public List<AnimationAdapter> getAdapters()
    {
        return this.adapters;
    }

    /*
     * Старый метод оставляем.
     *
     * Он всё ещё может пригодиться позже,
     * когда понадобится получить конкретный
     * подходящий адаптер.
     */
    public AnimationAdapter findSupportedAdapter()
    {
        for (
                AnimationAdapter adapter :
                this.adapters
        )
        {
            if (adapter != null &&
                    adapter.supports())
            {
                return adapter;
            }
        }

        return null;
    }

    /*
     * Новый основной метод.
     *
     * Один и тот же набор snapshots
     * передаётся ВСЕМ подходящим адаптерам.
     *
     * Это важно, потому что в редакторе одновременно
     * могут быть подключены Blockbuster и Emoticons.
     */
    public void applyAll(
            List<AnimationBoneSnapshot> bones,
            int frame)
    {
        for (
                AnimationAdapter adapter :
                this.adapters
        )
        {
            if (adapter == null)
            {
                continue;
            }

            if (!adapter.supports())
            {
                continue;
            }

            adapter.apply(
                    bones,
                    frame
            );
        }
    }
}
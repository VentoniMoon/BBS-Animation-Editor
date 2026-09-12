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

    public AnimationAdapter findSupportedAdapter()
    {
        for (
                AnimationAdapter adapter :
                this.adapters
        )
        {
            if (adapter.supports())
            {
                return adapter;
            }
        }

        return null;
    }
}
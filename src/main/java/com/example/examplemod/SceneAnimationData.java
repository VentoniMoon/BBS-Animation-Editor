package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

public class SceneAnimationData
{
    private final Map<String, ActorAnimationData> actors =
            new LinkedHashMap<String, ActorAnimationData>();

    public void clear()
    {
        this.actors.clear();
    }

    public boolean containsActor(String actorId)
    {
        return actorId != null
                && this.actors.containsKey(actorId);
    }

    public ActorAnimationData getActor(String actorId)
    {
        if (actorId == null)
        {
            return null;
        }

        return this.actors.get(actorId);
    }

    public void putActor(
            String actorId,
            ActorAnimationData animation)
    {
        if (actorId == null
                || actorId.isEmpty()
                || animation == null)
        {
            return;
        }

        this.actors.put(
                actorId,
                animation
        );
    }

    public ActorAnimationData removeActor(
            String actorId)
    {
        if (actorId == null)
        {
            return null;
        }

        return this.actors.remove(actorId);
    }

    public Map<String, ActorAnimationData> getActors()
    {
        return this.actors;
    }

    public boolean isEmpty()
    {
        return this.actors.isEmpty();
    }

    public int size()
    {
        return this.actors.size();
    }
}
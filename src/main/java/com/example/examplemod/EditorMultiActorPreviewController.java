package com.example.examplemod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;

/**
 * Stage 8 scene renderer. Each background actor receives an independent
 * EntityActor / morph renderer; the selected actor keeps the existing
 * EditorSceneViewport renderer, gizmo and BodyParts editing pipeline.
 *
 * Visibility is local to Preview and never mutates scene/Record data.
 */
public final class EditorMultiActorPreviewController
{
    private final Map<String, BlockbusterActorPreviewRenderer> renderers =
            new HashMap<String, BlockbusterActorPreviewRenderer>();

    private boolean showAllActors = true;

    public boolean isShowAllActors()
    {
        return this.showAllActors;
    }

    public void toggleShowAllActors()
    {
        this.showAllActors = !this.showAllActors;
    }

    public void drawBackgroundActors(
            Minecraft mc,
            EditorSceneViewport viewport,
            EditorSceneState scene,
            BlockbusterSceneActorData selected,
            int frame)
    {
        if (!this.showAllActors || scene == null || viewport == null)
        {
            return;
        }

        List<BlockbusterSceneActorData> actors = scene.getActors();
        if (actors == null) return;

        Set<String> active = new HashSet<String>();

        for (BlockbusterSceneActorData data : actors)
        {
            if (data == null || data.getId() == null) continue;
            active.add(data.getId());
            if (data == selected || !data.hasRecord()) continue;

            BlockbusterRecord record = data.getRecord();
            if (record == null || record.getFrames() == null
                    || record.getFrames().isEmpty()) continue;

            /* Do not draw a missing frame before an actor's recording begins. */
            BlockbusterRecordFrame current = record.getFrame(
                    Math.max(0, Math.min(frame, record.getFrames().size() - 1)));
            if (current == null) continue;

            BlockbusterActorPreviewRenderer renderer = renderers.get(data.getId());
            if (renderer == null)
            {
                renderer = new BlockbusterActorPreviewRenderer();
                renderers.put(data.getId(), renderer);
            }

            viewport.drawAdditionalActor(mc, data, current, renderer);
        }

        /* Discard stale actor state when a scene changes. */
        java.util.Iterator<Map.Entry<String, BlockbusterActorPreviewRenderer>> it =
                renderers.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, BlockbusterActorPreviewRenderer> entry = it.next();
            if (!active.contains(entry.getKey()))
            {
                entry.getValue().reset();
                it.remove();
            }
        }
    }

    public void reset()
    {
        for (BlockbusterActorPreviewRenderer renderer : renderers.values())
        {
            renderer.reset();
        }
        renderers.clear();
    }
}

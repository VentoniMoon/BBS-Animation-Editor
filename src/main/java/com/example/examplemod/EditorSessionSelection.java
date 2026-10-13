package com.example.examplemod;

import java.io.File;
import java.util.List;

/**
 * Remembers the most recently edited actor during the current Minecraft
 * session. Esc must not silently save animation edits, so this is UI-only.
 */
public final class EditorSessionSelection
{
    private static String scenePath = "";
    private static String actorId = "";

    private EditorSessionSelection() {}

    public static void remember(EditorSceneState state)
    {
        if (state == null) return;
        int index = state.getSelectedScene();
        List<File> scenes = state.getSceneFiles();
        if (scenes == null || index < 0 || index >= scenes.size()
                || scenes.get(index) == null)
        {
            return;
        }

        scenePath = scenes.get(index).getAbsolutePath();
        BlockbusterSceneActorData actor = state.getSelectedActorData();
        actorId = actor == null ? "" : actor.getId();
    }

    public static void restore(AnimationEditorScreen screen, EditorSceneState state)
    {
        if (state == null || screen == null || scenePath.isEmpty()) return;

        List<File> available = state.getSceneFiles();
        if (available == null) return;

        for (int i = 0; i < available.size(); i++)
        {
            File file = available.get(i);
            if (file == null || !scenePath.equals(file.getAbsolutePath()))
            {
                continue;
            }

            screen.loadScene(i);
            if (state.getSelectedScene() != i) return;

            List<BlockbusterSceneActorData> actors = screen.getSceneActors();
            for (int j = 0; j < actors.size(); j++)
            {
                BlockbusterSceneActorData actor = actors.get(j);
                if (actor != null && actorId.equals(actor.getId()))
                {
                    screen.selectActor(j);
                    break;
                }
            }
            return;
        }
    }
}

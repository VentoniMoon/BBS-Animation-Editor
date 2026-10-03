package com.example.examplemod;

/**
 * Runtime-контекст Character Body Parts для Preview.
 *
 * Renderer передаёт сюда текущего Actor и кадр.
 * EditorAnimatorMorphController использует этот контекст
 * непосредственно во время расчёта AnimatedMorph.
 */
public final class CharacterBodyPartPreviewState
{
    private static BlockbusterSceneActorData actorData;
    private static int frame;

    private CharacterBodyPartPreviewState()
    {
    }

    public static void set(
            BlockbusterSceneActorData newActorData,
            int newFrame)
    {
        actorData = newActorData;
        frame = Math.max(0, newFrame);
    }

    public static BlockbusterSceneActorData getActorData()
    {
        return actorData;
    }

    public static int getFrame()
    {
        return frame;
    }

    public static boolean hasState()
    {
        return actorData != null;
    }

    public static void clear()
    {
        actorData = null;
        frame = 0;
    }
}

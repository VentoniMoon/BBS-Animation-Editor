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

    /**
     * This context is static because native Emoticons hooks query it while
     * rendering. Multiple scene actors MUST restore the previous context,
     * including nested selected/background and mirror/shadow passes.
     */
    public static Scope enter(BlockbusterSceneActorData actor,int tick)
    {
        Scope scope=new Scope(actorData,frame);
        set(actor,tick);
        return scope;
    }

    public static final class Scope implements AutoCloseable
    {
        private final BlockbusterSceneActorData previousActor;
        private final int previousFrame;
        private boolean closed;

        private Scope(BlockbusterSceneActorData actor,int frame)
        {
            this.previousActor=actor;
            this.previousFrame=frame;
        }

        @Override public void close()
        {
            if(closed)return;
            actorData=previousActor;
            frame=previousFrame;
            closed=true;
        }
    }

    public static void clear()
    {
        actorData = null;
        frame = 0;
    }
}

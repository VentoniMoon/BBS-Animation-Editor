package com.example.examplemod;

class BbsFsWorldProjection
{
    static boolean captured = true;
    static boolean isCaptured(){return captured;}
}
class EditorSceneViewport
{
    int width = 900, height = 500;
    int getWidth(){return width;}
    int getHeight(){return height;}
}
class AnimationEditorScreen
{
    int renders;
    boolean shouldRender = true;
    boolean throwOnRender;
    boolean renderActorsInNativeWorld()
    {
        renders++;
        if (throwOnRender) throw new IllegalStateException("simulated morph exception");
        return shouldRender;
    }
}

/** Test-only shader stage: real implementation uses OptiFine reflection. */
class BbsFsOptiFineEntityStage implements AutoCloseable
{
    static int opened, closed;
    static BbsFsOptiFineEntityStage open()
    {
        opened++;
        return new BbsFsOptiFineEntityStage();
    }
    public void close(){closed++;}
}

/** Test-only GL guard: verifies close even on actor exception. */
class BbsFsWorldGlGuard implements AutoCloseable
{
    static int opened;
    static int closed;
    BbsFsWorldGlGuard(net.minecraft.client.Minecraft mc){opened++;}
    public void close(){closed++;}
}

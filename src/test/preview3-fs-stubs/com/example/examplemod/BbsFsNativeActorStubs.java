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

package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Minimal renderer contract: the real classes are NOT on this test classpath. */
class Preview3Probe
{
    static final List<String> order=new ArrayList<String>();
    static boolean throwActor;
    static void reset(){order.clear();throwActor=false;
        BbsFsNativeActorPass.rendered=false;}
}
/** Test-only stub. Real implementation lives in BbsFsNativeActorPass.java. */
class BbsFsNativeActorPass
{
    static boolean rendered;
    static boolean didRenderThisFrame(){return rendered;}
}
class EditorSceneState {}
class BlockbusterSceneActorData {}
class BlockbusterRecordFrame {}
class BodyPartsEditorController {}
class ActorPose {}
class AnimationBone {}
class PreviewRenderBackend
{
    boolean active;
    public boolean isPreviewRenderActive(){return active;}
}
class EditorSceneViewport
{
    private final PreviewRenderBackend backend=new PreviewRenderBackend();
    public PreviewRenderBackend getRenderBackend(){return backend;}
    public void draw(Minecraft mc,ActorPose pose,List<AnimationBone> bones,int frame)
    {
        backend.active=true;
        Preview3Probe.order.add("world");
    }
    public void drawActor(Minecraft mc,BlockbusterSceneActorData actor,
            BlockbusterRecordFrame frame)
    {
        Preview3Probe.order.add("selected");
        if(Preview3Probe.throwActor)
            throw new IllegalStateException("simulated renderer failure");
    }
    public void finishPreviewRender()
    {
        Preview3Probe.order.add("cleanup");
        backend.active=false;
    }
}
class EditorMultiActorPreviewController
{
    public void drawBackgroundActors(Minecraft mc,EditorSceneViewport viewport,
            EditorSceneState scene,BlockbusterSceneActorData selected,int frame,
            BodyPartsEditorController bodyparts)
    {
        Preview3Probe.order.add("background");
    }
}

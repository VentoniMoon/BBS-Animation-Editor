package com.example.examplemod;

import java.util.Arrays;
import net.minecraft.client.Minecraft;

public final class Preview3CoordinatorSmokeTest
{
    private static void check(String... expected)
    {
        if(!Preview3Probe.order.equals(Arrays.asList(expected)))
            throw new AssertionError(Preview3Probe.order+" != "+Arrays.asList(expected));
    }

    public static void main(String[] args)
    {
        PreviewSceneRenderCoordinator scene=new PreviewSceneRenderCoordinator();
        EditorSceneViewport viewport=new EditorSceneViewport();
        Preview3Probe.reset();
        scene.draw(new Minecraft(),viewport,new EditorMultiActorPreviewController(),
                new EditorSceneState(),new BlockbusterSceneActorData(),
                new BlockbusterRecordFrame(),new BodyPartsEditorController(),
                new ActorPose(),null,41,
                () -> Preview3Probe.order.add("selected-ready"),
                () -> Preview3Probe.order.add("gizmo"));
        check("world","background","selected","selected-ready","gizmo","cleanup");
        if(scene.isDrawing() || viewport.getRenderBackend().isPreviewRenderActive() ||
                scene.getLastFrame()!=41)
            throw new AssertionError("Scene pass not closed");

        Preview3Probe.reset();
        Preview3Probe.throwActor=true;
        try
        {
            scene.draw(new Minecraft(),viewport,new EditorMultiActorPreviewController(),
                    new EditorSceneState(),null,null,null,null,null,42,
                    () -> Preview3Probe.order.add("selected-ready"),null);
            throw new AssertionError("Expected a failed selected actor render");
        }
        catch(IllegalStateException expected)
        {
            check("world","background","selected","cleanup");
        }
        if(scene.isDrawing() || viewport.getRenderBackend().isPreviewRenderActive())
            throw new AssertionError("Exception leaked a framebuffer or scene lock");
        System.out.println("Preview 3.0 unified scene pass: ordering and cleanup PASS");
    }
}

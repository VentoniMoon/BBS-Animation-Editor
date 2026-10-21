package com.example.examplemod;

import java.util.Arrays;
import net.minecraft.client.Minecraft;

/**
 * Java 8 check: native world owns actors, GUI exclusively composites the
 * finished world/film and draws the selection overlay once.
 */
public final class Preview3CoordinatorSmokeTest
{
    private static void check(String... expected)
    {
        if (!Preview3Probe.order.equals(Arrays.asList(expected)))
            throw new AssertionError(Preview3Probe.order + " != " +
                    Arrays.asList(expected));
    }

    public static void main(String[] args)
    {
        PreviewSceneRenderCoordinator scene=new PreviewSceneRenderCoordinator();
        EditorSceneViewport viewport=new EditorSceneViewport();
        EditorMultiActorPreviewController actors=
                new EditorMultiActorPreviewController();

        Preview3Probe.reset();
        scene.draw(new Minecraft(),viewport,actors,new EditorSceneState(),
                new BlockbusterSceneActorData(),new BlockbusterRecordFrame(),
                new BodyPartsEditorController(),new ActorPose(),null,41,
                () -> Preview3Probe.order.add("selected-ready"),
                () -> Preview3Probe.order.add("gizmo"));
        check("world","cleanup");
        if (scene.isDrawing() || viewport.getRenderBackend().isPreviewRenderActive())
            throw new AssertionError("Empty native frame leaked framebuffer");

        Preview3Probe.reset();
        BbsFsNativeActorPass.rendered=true;
        scene.draw(new Minecraft(),viewport,actors,new EditorSceneState(),
                new BlockbusterSceneActorData(),new BlockbusterRecordFrame(),
                new BodyPartsEditorController(),new ActorPose(),null,42,
                () -> Preview3Probe.order.add("selected-ready"),
                () -> Preview3Probe.order.add("gizmo"));
        check("world","selected-ready","gizmo","cleanup");
        if (scene.isDrawing() || viewport.getRenderBackend().isPreviewRenderActive())
            throw new AssertionError("Completed native frame leaked framebuffer");

        Preview3Probe.reset();
        BbsFsNativeActorPass.rendered=true;
        try
        {
            scene.draw(new Minecraft(),viewport,actors,new EditorSceneState(),
                    null,null,null,null,null,43,
                    () -> Preview3Probe.order.add("selected-ready"),
                    () -> {
                        Preview3Probe.order.add("gizmo");
                        throw new IllegalStateException("simulated gizmo failure");
                    });
            throw new AssertionError("Expected a failed GUI overlay");
        }
        catch (IllegalStateException expected)
        {
            check("world","selected-ready","gizmo","cleanup");
        }
        if (scene.isDrawing() || viewport.getRenderBackend().isPreviewRenderActive())
            throw new AssertionError("Exception leaked framebuffer/scene lock");

        System.out.println("Preview FS: world-only actors, GUI overlay and cleanup PASS");
    }
}

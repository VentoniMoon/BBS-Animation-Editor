package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Preview 3.0 renderer ownership boundary. ONE scene tick and camera drive
 * world/actors/editor overlay. World and temporary EntityActor renderers
 * retain their existing tested implementations; the coordinator alone owns
 * pass order and its exception-safe teardown.
 *
 * The native-world bridge can later replace only the world stage without
 * knowing about Blockbuster records, editor selection, Jolt or Weld.
 */
public final class PreviewSceneRenderCoordinator
{
    private boolean drawing;
    private int lastFrame=-1;

    public int getLastFrame() { return lastFrame; }

    public boolean isDrawing() { return drawing; }

    public void draw(Minecraft mc,EditorSceneViewport viewport,
            EditorMultiActorPreviewController actors,
            EditorSceneState scene,BlockbusterSceneActorData selected,
            BlockbusterRecordFrame selectedRecordFrame,
            BodyPartsEditorController bodyParts,
            ActorPose pose,java.util.List<AnimationBone> bones,
            int frame,Runnable selectedReady,Runnable overlay3D)
    {
        if(drawing || mc==null || viewport==null)return;
        drawing=true;
        lastFrame=frame;
        boolean started=false;
        try
        {
            // Begin world FBO and its depth buffer first.
            viewport.draw(mc,pose,bones,frame);
            started=viewport.getRenderBackend().isPreviewRenderActive();
            if(!started)return;

            // Background actors must not overwrite selected actor animation
            // state: EditorMultiActorPreviewController restores it in finally.
            /*
             * If the actor pass already ran during RenderWorldLastEvent, it
             * is already part of the captured native Minecraft framebuffer.
             * Never invoke Body Parts/Jolt a second time for the same frame.
             */
            if (!BbsFsNativeActorPass.didRenderThisFrame())
            {
                if(actors!=null)
                    actors.drawBackgroundActors(
                            mc,viewport,scene,selected,frame,bodyParts);

                viewport.drawActor(mc,selected,selectedRecordFrame);
            }

            if(selectedReady!=null)selectedReady.run();

            // Gizmo belongs to this exact 3D camera/depth pass, not a second
            // GUI-only projection or a rerender of the actor.
            if(overlay3D!=null)overlay3D.run();
        }
        finally
        {
            try
            {
                if(started || viewport.getRenderBackend().isPreviewRenderActive())
                    viewport.finishPreviewRender();
            }
            finally
            {
                drawing=false;
            }
        }
    }
}

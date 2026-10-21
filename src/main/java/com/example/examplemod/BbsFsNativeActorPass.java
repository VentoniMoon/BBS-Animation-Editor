package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

/**
 * Optional next migration boundary: render editor-recorded actors into the
 * REAL Minecraft world pass, BEFORE the BBS FS-style film frame is captured.
 *
 * Unlike PreviewNativeWorldBridge, this class never calls renderWorld() and
 * never creates another world or shader pass. The existing actor morph,
 * Body Parts, Jolt and Weld pipelines are invoked exactly once from Forge's
 * RenderWorldLastEvent, instead of a second GUI compositing pass.
 *
 * Disabled until actual 1.12.2 OptiFine + Blockbuster visual validation:
 * pass -Dbbs.editor.preview3.nativeActors=true to the CLIENT JVM. The
 * fallback is only for EDITOR ACTORS, not for the old world renderer.
 */
public final class BbsFsNativeActorPass
{
    private static final boolean REQUESTED =
            Boolean.getBoolean("bbs.editor.preview3.nativeActors");
    private static boolean rendering;
    private static boolean renderedThisFrame;
    private static int frameSequence;
    private static int renderedSequence = -1;
    private static boolean diagnosed;

    private BbsFsNativeActorPass() {}

    public static void beginFrame()
    {
        frameSequence++;
        renderedThisFrame = false;
    }

    public static boolean isRendering()
    {
        return rendering;
    }

    public static boolean didRenderThisFrame()
    {
        return REQUESTED && renderedThisFrame &&
                renderedSequence == frameSequence;
    }

    public static boolean isRequested()
    {
        return REQUESTED;
    }

    /**
     * Called only by the camera driver's native RenderWorldLastEvent.
     * Preserve actor simulation ownership: the GUI-side coordinator skips
     * both selected/background actors if this pass completed successfully.
     */
    public static void render(Minecraft mc, AnimationEditorScreen editor,
            EditorSceneViewport viewport, float partialTicks)
    {
        if (!REQUESTED || rendering || renderedThisFrame ||
                mc == null || editor == null || viewport == null ||
                !BbsFsWorldProjection.isCaptured() || mc.world == null)
            return;

        Framebuffer target = mc.getFramebuffer();
        if (target == null || target.framebufferWidth <= 0 ||
                target.framebufferHeight <= 0) return;

        rendering = true;
        try
        {
            editor.renderActorsInNativeWorld();
            renderedThisFrame = true;
            renderedSequence = frameSequence;
            if (!diagnosed)
            {
                diagnosed = true;
                System.out.println("[BBS Editor][FS Preview] Recorded actors "
                        + "now render in the native Minecraft world pass.");
            }
        }
        catch (Throwable error)
        {
            /* Avoid drawing the same Jolt simulation twice after a partial
             * actor failure. A render exception must stay visible in logs. */
            renderedThisFrame = true;
            renderedSequence = frameSequence;
            System.err.println("[BBS Editor][FS Preview] Native actor render "
                    + "failed; aborting pass (no second Jolt tick): " + error);
            error.printStackTrace();
        }
        finally
        {
            rendering = false;
        }
    }
}

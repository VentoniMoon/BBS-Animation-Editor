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
 * This is the sole recorded-actor rendering path. On missing world frames
 * actors remain absent and diagnostics report the problem; no legacy
 * GUI-world render or duplicated physics pass is used as fallback.
 */
public final class BbsFsNativeActorPass
{
    /*
     * BBS FS preview actors now have exactly one draw owner: Minecraft's
     * native world event. No second GUI actor pass or opt-in switch.
     */
    private static boolean rendering;
    private static boolean renderedThisFrame;
    private static int frameSequence;
    private static int renderedSequence = -1;
    private static int nativeViewportWidth, nativeViewportHeight;
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
        return renderedThisFrame &&
                renderedSequence == frameSequence;
    }

    /** Actual GL viewport from RenderWorldLast, not Minecraft's GUI FBO. */
    public static int worldViewportWidth()
    {
        return nativeViewportWidth;
    }

    public static int worldViewportHeight()
    {
        return nativeViewportHeight;
    }

    /**
     * Called only by the camera driver's native RenderWorldLastEvent.
     * Preserve actor simulation ownership: the GUI-side coordinator skips
     * both selected/background actors if this pass completed successfully.
     */
    public static void render(Minecraft mc,AnimationEditorScreen editor,
            EditorSceneViewport viewport,float partialTicks,
            int worldWidth,int worldHeight)
    {
        // One mutable actor/physics owner per normal Minecraft frame.
        if (rendering || renderedThisFrame ||
                mc == null || editor == null || viewport == null ||
                !BbsFsWorldProjection.isCaptured() || mc.world == null)
            return;

        Framebuffer target = mc.getFramebuffer();
        if (target == null || target.framebufferWidth <= 0 ||
                target.framebufferHeight <= 0 ||
                viewport.getWidth() <= 0 || viewport.getHeight() <= 0 ||
                worldWidth <= 0 || worldHeight <= 0) return;

        nativeViewportWidth = worldWidth;
        nativeViewportHeight = worldHeight;
        rendering = true;
        try (BbsFsWorldGlGuard ignored = new BbsFsWorldGlGuard(mc))
        {
            /*
             * RenderWorldLastEvent fires AFTER vanilla's entity pass, which
             * has already disabled the lightmap. Re-enable the same Minecraft
             * lightmap used by RenderGlobal.renderEntities instead of drawing
             * record actors with missing/fullbright light coordinates.
             */
            boolean lightmapEnabled = false;
            try
            {
                mc.entityRenderer.enableLightmap();
                lightmapEnabled = true;

                /*
                 * Forge's RenderWorldLastEvent runs after OptiFine's normal
                 * entity stage. Re-enter that exact shader program so morphs
                 * receive the same entity lighting as the main world. The
                 * previous OptiFine program is restored on every exit.
                 */
                try (BbsFsOptiFineEntityStage shaders =
                             BbsFsOptiFineEntityStage.open())
                {
                    if (!editor.renderActorsInNativeWorld()) return;
                }
            }
            finally
            {
                if (lightmapEnabled)
                    mc.entityRenderer.disableLightmap();
            }
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
                    + "failed; aborting pass: " + error);
            error.printStackTrace();
        }
        finally
        {
            rendering = false;
            nativeViewportWidth = nativeViewportHeight = 0;
        }
    }
}

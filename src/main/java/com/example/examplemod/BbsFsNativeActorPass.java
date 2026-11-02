package com.example.examplemod;

import net.minecraft.client.Minecraft;
import java.lang.reflect.Field;
import org.lwjgl.opengl.GL11;
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
    private static boolean earlyDiagnosed;
    private static boolean earlyErrorReported;
    private static boolean earlyPrepared;
    private static boolean earlyAttempted;
    private static Framebuffer preparationFramebuffer;
    private static Field minecraftFramebuffer;

    private BbsFsNativeActorPass() {}

    public static void beginFrame()
    {
        frameSequence++;
        renderedThisFrame = false;
        earlyPrepared = false;
        earlyAttempted = false;
    }

    /** Reclaim the private preparation surface on editor/world shutdown. */
    public static void release()
    {
        if(preparationFramebuffer!=null)
        {
            preparationFramebuffer.deleteFramebuffer();
            preparationFramebuffer=null;
        }
        earlyPrepared=false;
        earlyAttempted=false;
    }

    /**
     * Prepare once BEFORE OptiFine's beginRender/shadow map. The normal
     * actor renderer still owns animation, Body Parts, Weld and Jolt;
     * we invoke it exactly once, but draw into a small PRIVATE FBO.
     * The main film and the native sun-shadow atlas then replay the
     * resulting pose, with no new animation/physics evaluation.
     *
     * A temporary Minecraft.framebufferMc redirect prevents morph
     * renderers which bind mc.getFramebuffer() internally from damaging
     * the main frame while we precompute their transforms.
     */
    public static void prepareBeforeShadows(Minecraft mc,
            AnimationEditorScreen editor,EditorSceneViewport viewport)
    {
        // The transformer may process ShadersRender lazily (after the
        // FIRST RenderTick.START), so testing its installed flag here
        // causes a permanent chicken-and-egg failure on some OptiFine
        // layouts. Prepare whenever the explicit user flag is enabled:
        // the normal film renderer replays that pose even if the native
        // shadow hook never appears, without an extra Jolt tick.
        if(!Boolean.getBoolean(
                "bbs.editor.camera.nativeActorSunShadows")||
                rendering||earlyAttempted||mc==null||mc.world==null||
                editor==null||viewport==null||mc.player==null||
                viewport.getWidth()<=0||viewport.getHeight()<=0)
            return;
        earlyAttempted=true;
        Framebuffer original=mc.getFramebuffer();
        if(original==null)return;
        // This hidden actor draw runs BEFORE vanilla renderWorld(). If a
        // third-party Morph or Body Parts renderer changes Minecraft's
        // GlStateManager cache, the raw BbsFsWorldGlGuard glPopAttrib is
        // not sufficient: the next world render can have missing blend,
        // alpha, textures or incorrect lightmap state.
        BbsFsGlStateResync.Snapshot glBefore=
                BbsFsGlStateResync.capture();
        try
        {
            if(preparationFramebuffer==null)
                preparationFramebuffer=new Framebuffer(128,72,true);
            if(minecraftFramebuffer==null)
            {
                minecraftFramebuffer=Minecraft.class
                        .getDeclaredField("framebufferMc");
                minecraftFramebuffer.setAccessible(true);
            }
            nativeViewportWidth=Math.max(1,mc.displayWidth);
            nativeViewportHeight=Math.max(1,mc.displayHeight);
            rendering=true;
            try(BbsFsWorldGlGuard guard=new BbsFsWorldGlGuard(mc))
            {
                try
                {
                    minecraftFramebuffer.set(mc,preparationFramebuffer);
                    preparationFramebuffer.bindFramebuffer(true);
                    preparationFramebuffer.framebufferClear();
                    preparationFramebuffer.bindFramebuffer(true);
                    GL11.glViewport(0,0,128,72);
                    // The current world projection is not available yet.
                    // drawActor() falls back to the camera's FOV while
                    // preparing the exact recorded pose. These pixels are
                    // deliberately discarded, not shown in the film.
                    earlyPrepared=editor.renderActorsInNativeWorld();
                }
                finally
                {
                    minecraftFramebuffer.set(mc,original);
                }
            }
            if(earlyPrepared&&!earlyDiagnosed)
            {
                earlyDiagnosed=true;
                System.out.println(
                        "[BBS Editor][Actor Sun Shadow] "+
                        "Early actor pose prepared before OptiFine shadow "+
                        "map (private FBO; Jolt once)");
            }
        }
        catch(Throwable e)
        {
            earlyPrepared=false;
            if(!earlyErrorReported)
            {
                earlyErrorReported=true;
                System.err.println(
                        "[BBS Editor][Actor Sun Shadow] "+
                        "Early pose preparation failed: "+e);
                e.printStackTrace();
            }
        }
        finally
        {
            // Do not render again after a partially failed preparation:
            // it may already have stepped a physics simulation.
            rendering=false;
            nativeViewportWidth=nativeViewportHeight=0;
            try
            {
                if(minecraftFramebuffer!=null)
                    minecraftFramebuffer.set(mc,original);
            }
            catch(IllegalAccessException unsafe)
            {
                throw new IllegalStateException(
                        "Cannot restore Minecraft framebuffer",unsafe);
            }
            finally
            {
                // Sync both real GL and Minecraft's cached state after
                // the private FBO has been unbound by WorldGlGuard.
                BbsFsGlStateResync.restore(glBefore);
            }
        }
    }

    public static boolean preparedBeforeShadows()
    {
        return earlyPrepared;
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
        // A failed early pass may already have advanced Jolt. Never
        // advance it a second time in this very same Minecraft frame.
        if(earlyAttempted&&!earlyPrepared)return;

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
                    if(earlyPrepared)
                    {
                        PreviewCameraPose pose=
                                BbsFsWorldCameraDriver.nativeActorPose(viewport);
                        if(pose==null)return;
                        editor.renderPreparedActorsInNativeWorld(mc,pose,
                                worldWidth,worldHeight);
                    }
                    else if (!editor.renderActorsInNativeWorld()) return;
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

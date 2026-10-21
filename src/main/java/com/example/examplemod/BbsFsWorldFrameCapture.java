package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * BBS FS-style native world frame capture (1.12.2).
 *
 * BBS FS renders the actual Minecraft world before its editor GUI, copies
 * the finished frame into a texture, then displays that texture in the UI.
 * Our old PreviewWorldRenderer/PreviewSkyRenderer/PreviewFogRenderer/terrain
 * re-rendering approach is deliberately NOT used here.
 *
 * This class snapshots the Minecraft framebuffer at the VERY START of
 * AnimationEditorScreen.drawScreen, before any editor GUI is rendered.
 * The snapshot then gets cropped into the editor's existing FBO so its
 * selected actors/gizmo/UI can be migrated without breaking the data layer.
 *
 * Architectural reference: Wemppy4/bbs-fs, BBSRendering + UIFilmPreview,
 * 1.12.2 branch, MIT licensed. No BBS FS binaries are bundled here.
 */
public final class BbsFsWorldFrameCapture
{
    private static Framebuffer snapshot;
    private static boolean ready;
    private static boolean reported;
    private static boolean reportedFailure;
    private static boolean warnedDepth;

    private BbsFsWorldFrameCapture() {}

    /**
     * Call before the FIRST drawRect/GUI draw in the editor per frame.
     * A late capture would record our dark GUI panels instead of the world.
     */
    public static void beginFrame(Minecraft mc)
    {
        ready = false;
        if (mc == null || mc.world == null || mc.getFramebuffer() == null)
            return;

        Framebuffer source = mc.getFramebuffer();
        int sw = source.framebufferWidth;
        int sh = source.framebufferHeight;
        if (sw <= 0 || sh <= 0) return;

        /*
         * Framebuffer allocation/resize changes the bound FBO and viewport.
         * Preserve the game render target BEFORE allocating, otherwise the
         * editor GUI may accidentally draw into the snapshot itself.
         */
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        /*
         * Vanilla Framebuffer constructors/resize modify texture binding,
         * depth mask, clear color and possibly blend/cull state. Do not leak
         * any of that into the GUI when the snapshot is first allocated.
         */
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            if (snapshot == null)
                snapshot = new Framebuffer(sw, sh, true);
            else if (snapshot.framebufferWidth != sw ||
                    snapshot.framebufferHeight != sh)
                snapshot.createBindFramebuffer(sw, sh);

            if (source != snapshot)
                ready = blit(source, snapshot,
                        0, 0, sw, sh, 0, 0, sw, sh);
        }
        catch (Throwable error)
        {
            reportFailure("World snapshot: " + error);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GL11.glViewport(viewport.get(0), viewport.get(1),
                    viewport.get(2), viewport.get(3));
            GL11.glPopAttrib();
        }
    }

    /**
     * Use only the snapshot taken before the editor GUI was drawn.
     * The destination region is the exact area sampled by the existing GUI
     * preview texture blitter, including HiDPI / GUI-scale conversions.
     */
    public static boolean copyWorld(Minecraft mc, PreviewRenderBackend backend,
            int x, int y, int width, int height)
    {
        if (!ready || snapshot == null || mc == null || backend == null
                || width <= 0 || height <= 0) return false;

        Framebuffer target = backend.getNativeTarget().getFramebuffer();
        if (target == null || target == snapshot) return false;

        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();

        int dx = x * scale;
        int dy = target.framebufferHeight - (y + height) * scale;
        int dw = width * scale;
        int dh = height * scale;
        if (dx < 0 || dy < 0 ||
                dx + dw > target.framebufferWidth ||
                dy + dh > target.framebufferHeight)
        {
            reportFailure("Preview bounds exceed destination framebuffer");
            return false;
        }

        /* Source frame and preview have different aspect ratios.
         * Use a centered crop, never distort the Minecraft world. */
        int sw = snapshot.framebufferWidth;
        int sh = snapshot.framebufferHeight;
        int srcX = 0, srcY = 0, srcW = sw, srcH = sh;
        double sourceAspect = (double) sw / sh;
        double previewAspect = (double) dw / dh;

        if (sourceAspect > previewAspect)
        {
            srcW = Math.max(1, (int) Math.round(sh * previewAspect));
            srcX = (sw - srcW) / 2;
        }
        else if (sourceAspect < previewAspect)
        {
            srcH = Math.max(1, (int) Math.round(sw / previewAspect));
            srcY = (sh - srcH) / 2;
        }

        boolean success = blit(snapshot, target, srcX, srcY,
                srcX + srcW, srcY + srcH, dx, dy, dx + dw, dy + dh);
        if (success && !reported)
        {
            reported = true;
            System.out.println("[BBS Editor][FS Preview] Native Minecraft "
                    + "frame captured BEFORE editor GUI (BBS FS-style).");
        }
        return success;
    }

    /**
     * Color is mandatory. Depth is best-effort because OptiFine can leave
     * final composited depth incompatible with the editor attachment.
     * Restore BOTH read/draw framebuffer targets on every exit.
     */
    private static boolean blit(Framebuffer source, Framebuffer target,
            int sx0, int sy0, int sx1, int sy1,
            int dx0, int dy0, int dx1, int dy1)
    {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    source.framebufferObject);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    target.framebufferObject);

            GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1,
                    dx0, dy0, dx1, dy1,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR)
            {
                reportFailure("World color blit GL error " + error);
                return false;
            }

            GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1,
                    dx0, dy0, dx1, dy1,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR && !warnedDepth)
            {
                warnedDepth = true;
                System.err.println("[BBS Editor][FS Preview] World color "
                        + "copied but depth copy failed (GL " + error
                        + "); verify actor occlusion.");
            }
            return true;
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    public static void release()
    {
        ready = false;
        if (snapshot != null)
        {
            snapshot.deleteFramebuffer();
            snapshot = null;
        }
    }

    private static void reportFailure(String reason)
    {
        if (reportedFailure) return;
        reportedFailure = true;
        System.err.println("[BBS Editor][FS Preview] " + reason);
    }
}

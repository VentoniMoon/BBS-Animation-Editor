package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * World half of the BBS FS-style preview.
 *
 * The world is rendered ONCE, in Minecraft's normal render loop using the
 * preview camera. GuiScreen then copies that completed native frame (color
 * and depth) into the editor FBO. No handmade sky, terrain, fog, cloud,
 * shader or chunk passes are used.
 *
 * This follows the world-frame-to-preview-texture mechanism used by BBS FS
 * 1.12.2 (BBSRendering / UIFilmPreview). The actor/editor overlays remain
 * separate passes over the captured world until migrated to world events.
 */
public final class BbsFsWorldFrameCapture
{
    private static boolean reported;
    private static boolean reportedFailure;
    private static boolean warnedDepth;

    private BbsFsWorldFrameCapture() {}

    public static boolean copyWorld(Minecraft mc, PreviewRenderBackend backend,
            int x, int y, int width, int height)
    {
        if (mc == null || mc.world == null || backend == null
                || width <= 0 || height <= 0) return false;

        Framebuffer source = mc.getFramebuffer();
        Framebuffer target = backend.getShaderBridge().getPreviewFramebuffer();
        if (source == null || target == null || source == target)
            return false;

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
            reportFailure("Preview bounds exceed target framebuffer");
            return false;
        }

        /* Center crop, never stretch: GUI and world camera aspects differ. */
        int sw = source.framebufferWidth;
        int sh = source.framebufferHeight;
        if (sw <= 0 || sh <= 0) return false;

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

            /* BBS FS captures the real world FBO rather than rerendering
             * chunks from a second projection. Preserve its depth where
             * the native framebuffer permits it. */
            GL30.glBlitFramebuffer(srcX, srcY, srcX + srcW, srcY + srcH,
                    dx, dy, dx + dw, dy + dh,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR)
            {
                reportFailure("OpenGL color blit error " + error);
                return false;
            }

            GL30.glBlitFramebuffer(srcX, srcY, srcX + srcW, srcY + srcH,
                    dx, dy, dx + dw, dy + dh,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR && !warnedDepth)
            {
                warnedDepth = true;
                System.err.println("[BBS Editor][FS Preview] Native color "
                        + "capture works, but depth copy failed (GL "
                        + error + "); entity occlusion must be verified.");
            }

            if (!reported)
            {
                reported = true;
                System.out.println("[BBS Editor][FS Preview] Capturing native "
                        + "Minecraft world frame, not rendering a second world.");
            }
            return true;
        }
        catch (Throwable problem)
        {
            reportFailure(problem.toString());
            return false;
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static void reportFailure(String reason)
    {
        if (reportedFailure) return;
        reportedFailure = true;
        System.err.println("[BBS Editor][FS Preview] Capture failed: " + reason);
    }
}

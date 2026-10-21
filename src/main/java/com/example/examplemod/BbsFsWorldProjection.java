package com.example.examplemod;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Snapshot of Minecraft's actual world projection at RenderWorldLastEvent.
 *
 * Captures the same perspective that wrote the native world depth buffer:
 * real fog/render distance near/far, FOV modifiers, OptiFine zoom and camera
 * projection. A second GLU.gluPerspective with fabricated near/far values
 * does not produce compatible depth for a manually rendered actor.
 *
 * Full-screen Minecraft image is center-cropped into the film viewport.
 * Scale NDC X or Y in the projection to undo that crop, preserving the
 * original world-space depth mapping exactly.
 */
public final class BbsFsWorldProjection
{
    private static final float[] matrix = new float[16];
    private static final FloatBuffer scratch =
            BufferUtils.createFloatBuffer(16);
    private static boolean captured;
    private static int displayWidth, displayHeight;

    private BbsFsWorldProjection() {}

    public static void beginFrame()
    {
        captured = false;
    }

    /** Run only within Forge's native RenderWorldLastEvent camera context. */
    public static void capture(int width, int height)
    {
        if (width <= 0 || height <= 0) return;

        scratch.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, scratch);
        scratch.rewind();

        /*
         * Standard world rendering must be a perspective matrix with a
         * negative W/Z term, not an orthographic GUI/shadow matrix.
         */
        float m00 = scratch.get(0);
        float m11 = scratch.get(5);
        float mW = scratch.get(11);
        if (!Float.isFinite(m00) || !Float.isFinite(m11)
                || m00 <= 0F || m11 <= 0F ||
                Math.abs(mW + 1F) > 0.05F) return;

        for (int i = 0; i < 16; i++) matrix[i] = scratch.get(i);
        displayWidth = width;
        displayHeight = height;
        captured = true;
    }

    /**
     * Called inside an active GL_PROJECTION matrix with glLoadIdentity done.
     * Returns false if the world pass has not provided a compatible matrix.
     */
    public static boolean loadForPreview(int width, int height)
    {
        if (!captured || width <= 0 || height <= 0 ||
                displayWidth <= 0 || displayHeight <= 0)
            return false;

        float[] copy = matrix.clone();
        double screenAspect = (double) displayWidth / displayHeight;
        double filmAspect = (double) width / height;

        if (screenAspect > filmAspect)
        {
            /* Source is cropped on the sides: expand NDC X. */
            copy[0] *= screenAspect / filmAspect;
            copy[4] *= screenAspect / filmAspect;
            copy[8] *= screenAspect / filmAspect;
            copy[12] *= screenAspect / filmAspect;
        }
        else if (screenAspect < filmAspect)
        {
            /* Source is cropped vertically: expand NDC Y. */
            copy[1] *= filmAspect / screenAspect;
            copy[5] *= filmAspect / screenAspect;
            copy[9] *= filmAspect / screenAspect;
            copy[13] *= filmAspect / screenAspect;
        }

        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(copy);
        buffer.flip();
        GL11.glLoadMatrix(buffer);
        return true;
    }

    public static boolean isCaptured()
    {
        return captured;
    }
}

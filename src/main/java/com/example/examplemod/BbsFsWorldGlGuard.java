package com.example.examplemod;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Exception-safe boundary for drawing temporary recorded actors in Forge's
 * native world event. Reusing the real world FBO must never leak matrices,
 * scissor, viewport, shader program or RenderManager viewer position.
 *
 * Actors/Body Parts may temporarily push additional matrices; unwind those
 * before popping our two outer sentinel matrices. The native world keeps
 * control of all world/shader frame begin/end operations.
 */
public final class BbsFsWorldGlGuard implements AutoCloseable
{
    private final int oldMatrixMode;
    private final int oldReadFramebuffer;
    private final int oldDrawFramebuffer;
    private final int oldShaderProgram;
    private final int oldTextureUnit;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final int projectionDepth;
    private final int modelviewDepth;
    private final RenderManager manager;
    private final double viewerX, viewerY, viewerZ;
    private boolean closed;

    public BbsFsWorldGlGuard(Minecraft mc)
    {
        oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        oldReadFramebuffer =
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        oldDrawFramebuffer =
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        oldShaderProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        oldTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        manager = mc.getRenderManager();
        viewerX = manager == null ? 0D : manager.viewerPosX;
        viewerY = manager == null ? 0D : manager.viewerPosY;
        viewerZ = manager == null ? 0D : manager.viewerPosZ;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
        GL11.glPushMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        GL11.glPushMatrix();

        GL11.glMatrixMode(oldMatrixMode);
    }

    @Override
    public void close()
    {
        if (closed) return;
        closed = true;
        try
        {
            if (manager != null)
            {
                manager.viewerPosX = viewerX;
                manager.viewerPosY = viewerY;
                manager.viewerPosZ = viewerZ;
            }

            /* Restore matrices even if a morph renderer forgot to pop its
             * own pushes after throwing partway through an actor render. */
            restoreMatrixStack(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH,
                    modelviewDepth);
            restoreMatrixStack(GL11.GL_PROJECTION,
                    GL11.GL_PROJECTION_STACK_DEPTH, projectionDepth);
        }
        finally
        {
            GL11.glMatrixMode(oldMatrixMode);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    oldReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    oldDrawFramebuffer);
            GL11.glViewport(viewport.get(0), viewport.get(1),
                    viewport.get(2), viewport.get(3));
            GL11.glPopAttrib();
            GL13.glActiveTexture(oldTextureUnit);
            GL20.glUseProgram(oldShaderProgram);
        }
    }

    private static void restoreMatrixStack(int mode, int depthEnum,
            int baseline)
    {
        GL11.glMatrixMode(mode);
        int depth = GL11.glGetInteger(depthEnum);
        /* Bound the loop defensively; a broken morph can corrupt the stack.
         * Never pop BELOW the original depth. */
        for (int i = 0; i < 64 && depth > baseline; i++, depth--)
            GL11.glPopMatrix();
    }
}

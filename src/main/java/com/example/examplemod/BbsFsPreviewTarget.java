package com.example.examplemod;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Dedicated framebuffer/presenter for the BBS FS-style film preview.
 *
 * This class DOES NOT start a second world render, Minecraft shader stage,
 * RenderGlobal chunk pass, sky/fog pass or OptiFine beginRender.
 *
 * Render ownership:
 *   native Minecraft render -> BbsFsWorldFrameCapture (clean world texture)
 *   -> this target (color+depth capture, editor actors/gizmo)
 *   -> this target's viewport texture in GuiScreen.
 *
 * Keep the full-window target size while actor/gizmo renderers migrate:
 * both currently address the preview rectangle in screen pixel space.
 */
public final class BbsFsPreviewTarget
{
    private final Minecraft mc;
    private Framebuffer framebuffer;
    private boolean active;
    private int savedRead;
    private int savedDraw;
    private int savedProgram;
    private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
    private int x, y, width, height;

    public BbsFsPreviewTarget(Minecraft mc)
    {
        this.mc = mc;
    }

    public void setBounds(int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean isActive()
    {
        return this.active;
    }

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    public boolean begin()
    {
        if (active) return true;
        if (mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0 ||
                width <= 0 || height <= 0) return false;

        savedRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        savedDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        savedViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            if (framebuffer == null)
                framebuffer = new Framebuffer(
                        mc.displayWidth, mc.displayHeight, true);
            else if (framebuffer.framebufferWidth != mc.displayWidth ||
                    framebuffer.framebufferHeight != mc.displayHeight)
                framebuffer.createBindFramebuffer(
                        mc.displayWidth, mc.displayHeight);

            framebuffer.bindFramebuffer(true);
            GL11.glViewport(0, 0, framebuffer.framebufferWidth,
                    framebuffer.framebufferHeight);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.06F, 0.07F, 0.08F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT |
                    GL11.GL_DEPTH_BUFFER_BIT |
                    GL11.GL_STENCIL_BUFFER_BIT);
            /* Temporary editor actors use Minecraft's fixed-function model
             * renderer. Avoid inheriting an OptiFine final shader program. */
            GL20.glUseProgram(0);
            active = true;
            return true;
        }
        catch (Throwable error)
        {
            System.err.println("[BBS Editor][FS Preview] Target begin failed: "
                    + error);
            restore();
            return false;
        }
    }

    public void end()
    {
        if (!active) return;
        restore();
    }

    private void restore()
    {
        active = false;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDraw);
        GL11.glViewport(savedViewport.get(0), savedViewport.get(1),
                savedViewport.get(2), savedViewport.get(3));
        GL11.glPopAttrib();
        GL20.glUseProgram(savedProgram);
    }

    /**
     * Display the selected region of the full-size editor texture.
     * The native world is already in it; no world renderer runs here.
     */
    public void present()
    {
        if (active || framebuffer == null || width <= 0 || height <= 0)
            return;
        if (framebuffer.framebufferTexture == 0) return;

        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        float fbWidth = framebuffer.framebufferWidth;
        float fbHeight = framebuffer.framebufferHeight;
        float u0 = x * scale / fbWidth;
        float u1 = (x + width) * scale / fbWidth;
        float v0 = (fbHeight - (y + height) * scale) / fbHeight;
        float v1 = (fbHeight - y * scale) / fbHeight;

        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, resolution.getScaledWidth(),
                resolution.getScaledHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    framebuffer.framebufferTexture);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(x + width, y);
            GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(x + width, y + height);
            GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(x, y + height);
            GL11.glEnd();
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(mode);
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
    }

    public void dispose()
    {
        if (active) end();
        if (framebuffer != null)
        {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }
}

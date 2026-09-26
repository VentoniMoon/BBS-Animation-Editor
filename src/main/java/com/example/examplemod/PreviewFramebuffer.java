package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

import java.nio.IntBuffer;

public class PreviewFramebuffer
{
    private final Minecraft mc;

    private Framebuffer framebuffer;

    private int framebufferWidth;
    private int framebufferHeight;

    private boolean initialized;
    private boolean renderingPreview;

    private int previousFramebuffer;

    private int previousViewportX;
    private int previousViewportY;
    private int previousViewportWidth;
    private int previousViewportHeight;

    private int previousMatrixMode;


    public PreviewFramebuffer(Minecraft mc)
    {
        this.mc = mc;

        this.framebuffer = null;

        this.framebufferWidth = 0;
        this.framebufferHeight = 0;

        this.initialized = false;
        this.renderingPreview = false;

        this.previousFramebuffer = 0;

        this.previousViewportX = 0;
        this.previousViewportY = 0;
        this.previousViewportWidth = 0;
        this.previousViewportHeight = 0;

        this.previousMatrixMode = GL11.GL_MODELVIEW;
    }


    public void ensureSize()
    {
        if (this.mc == null)
        {
            return;
        }

        int width = this.mc.displayWidth;
        int height = this.mc.displayHeight;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        if (this.initialized &&
                this.framebuffer != null &&
                this.framebufferWidth == width &&
                this.framebufferHeight == height)
        {
            return;
        }

        if (this.framebuffer != null)
        {
            this.framebuffer.deleteFramebuffer();
            this.framebuffer = null;
        }

        this.initialized = false;

        this.framebuffer = new Framebuffer(
                width,
                height,
                true
        );

        this.framebufferWidth = width;
        this.framebufferHeight = height;

        this.framebuffer.setFramebufferColor(
                0.08F,
                0.09F,
                0.11F,
                1.0F
        );

        this.initialized = true;
    }


    public void beginRender()
    {
        if (this.renderingPreview)
        {
            return;
        }

        if (this.mc == null)
        {
            return;
        }

        this.ensureSize();

        if (!this.initialized ||
                this.framebuffer == null)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * Сохраняем framebuffer.
         * ---------------------------------------------------------
         */

        this.previousFramebuffer =
                GL11.glGetInteger(
                        EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
                );


        /*
         * ---------------------------------------------------------
         * Сохраняем viewport.
         * ---------------------------------------------------------
         */

        int[] viewport =
                this.getCurrentViewport();

        this.previousViewportX =
                viewport[0];

        this.previousViewportY =
                viewport[1];

        this.previousViewportWidth =
                viewport[2];

        this.previousViewportHeight =
                viewport[3];


        /*
         * ---------------------------------------------------------
         * Сохраняем matrix mode.
         * ---------------------------------------------------------
         */

        this.previousMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );


        /*
         * ---------------------------------------------------------
         * Переключаемся на Preview FBO.
         * ---------------------------------------------------------
         */

        this.framebuffer.bindFramebuffer(true);


        /*
         * ---------------------------------------------------------
         * Viewport Preview.
         * ---------------------------------------------------------
         */

        GL11.glViewport(
                0,
                0,
                this.framebufferWidth,
                this.framebufferHeight
        );


        /*
         * ---------------------------------------------------------
         * Очистка FBO.
         *
         * НЕ используем framebufferClear().
         * ---------------------------------------------------------
         */

        GL11.glClearColor(
                0.08F,
                0.09F,
                0.11F,
                1.0F
        );

        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT |
                        GL11.GL_STENCIL_BUFFER_BIT
        );


        this.renderingPreview = true;
    }


    public void endRender()
    {
        if (!this.renderingPreview)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * Возвращаем исходный framebuffer.
         * ---------------------------------------------------------
         */

        EXTFramebufferObject.glBindFramebufferEXT(
                EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                this.previousFramebuffer
        );


        /*
         * ---------------------------------------------------------
         * Возвращаем viewport.
         * ---------------------------------------------------------
         */

        if (this.previousViewportWidth > 0 &&
                this.previousViewportHeight > 0)
        {
            GL11.glViewport(
                    this.previousViewportX,
                    this.previousViewportY,
                    this.previousViewportWidth,
                    this.previousViewportHeight
            );
        }


        /*
         * ---------------------------------------------------------
         * Возвращаем matrix mode.
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                this.previousMatrixMode
        );


        this.renderingPreview = false;
    }


    public void bindForRender()
    {
        this.beginRender();
    }


    public void unbind()
    {
        this.endRender();
    }


    public boolean isRenderingPreview()
    {
        return this.renderingPreview;
    }


    public boolean isInitialized()
    {
        return this.initialized &&
                this.framebuffer != null;
    }


    public int getTexture()
    {
        if (this.framebuffer == null)
        {
            return 0;
        }

        return this.framebuffer.framebufferTexture;
    }


    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }


    public int getFramebufferTexture()
    {
        if (this.framebuffer == null)
        {
            return 0;
        }

        return this.framebuffer.framebufferTexture;
    }


    public int getFramebufferWidth()
    {
        return this.framebufferWidth;
    }


    public int getFramebufferHeight()
    {
        return this.framebufferHeight;
    }


    private int[] getCurrentViewport()
    {
        IntBuffer buffer =
                BufferUtils.createIntBuffer(16);

        GL11.glGetInteger(
                GL11.GL_VIEWPORT,
                buffer
        );

        return new int[]
                {
                        buffer.get(0),
                        buffer.get(1),
                        buffer.get(2),
                        buffer.get(3)
                };
    }


    public void delete()
    {
        if (this.renderingPreview)
        {
            this.endRender();
        }

        if (this.framebuffer != null)
        {
            this.framebuffer.deleteFramebuffer();
            this.framebuffer = null;
        }

        this.framebufferWidth = 0;
        this.framebufferHeight = 0;

        this.initialized = false;
        this.renderingPreview = false;

        this.previousFramebuffer = 0;

        this.previousViewportX = 0;
        this.previousViewportY = 0;
        this.previousViewportWidth = 0;
        this.previousViewportHeight = 0;

        this.previousMatrixMode =
                GL11.GL_MODELVIEW;
    }
}
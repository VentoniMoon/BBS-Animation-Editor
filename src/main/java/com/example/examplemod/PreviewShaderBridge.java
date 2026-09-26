package com.example.examplemod;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;


/**
 * Центральный framebuffer-мост Preview.
 *
 * Preview использует собственный framebuffer размером
 * с экран Minecraft, но реальный 3D render viewport
 * ограничивается только областью Preview.
 *
 * После рендера в GUI выводится только эта область
 * framebuffer.
 *
 * Благодаря этому:
 *
 * 1. Preview не занимает весь framebuffer визуально;
 * 2. верхняя серая область не попадает внутрь Preview;
 * 3. aspect ratio мира остаётся равным aspect ratio Preview;
 * 4. Minecraft / OptiFine framebuffer не ломается;
 * 5. Preview не запускает отдельный OptiFine shader lifecycle;
 * 6. каждый кадр полностью очищает Preview framebuffer.
 */
public class PreviewShaderBridge
{
    private final Minecraft mc;


    /*
     * =========================================================
     * PREVIEW FRAMEBUFFER
     * =========================================================
     */

    private Framebuffer previewFramebuffer;


    /*
     * =========================================================
     * PREVIEW GUI BOUNDS
     * =========================================================
     */

    private int previewX;

    private int previewY;

    private int previewWidth;

    private int previewHeight;


    /*
     * =========================================================
     * STATUS
     * =========================================================
     */

    private boolean initialized;

    private boolean optiFinePresent;

    private boolean shaderPackLoaded;


    /*
     * =========================================================
     * RENDER STATE
     * =========================================================
     */

    private boolean rendering;


    private int previousFramebuffer;

    private int previousViewportX;

    private int previousViewportY;

    private int previousViewportWidth;

    private int previousViewportHeight;


    public PreviewShaderBridge(Minecraft mc)
    {
        this.mc = mc;

        this.initialized = false;

        this.optiFinePresent = false;

        this.shaderPackLoaded = false;

        this.rendering = false;

        this.previewX = 0;

        this.previewY = 0;

        this.previewWidth = 0;

        this.previewHeight = 0;
    }


    /*
     * =========================================================
     * PREVIEW BOUNDS
     * =========================================================
     */

    public void setPreviewBounds(
            int x,
            int y,
            int width,
            int height)
    {
        this.previewX = x;

        this.previewY = y;

        this.previewWidth = width;

        this.previewHeight = height;
    }


    /*
     * =========================================================
     * INITIALIZATION
     * =========================================================
     */

    private void initialize()
    {
        if (this.initialized)
        {
            return;
        }

        this.initialized = true;

        this.optiFinePresent = false;

        this.shaderPackLoaded = false;


        try
        {
            Class.forName(
                    "net.optifine.shaders.Shaders"
            );

            this.optiFinePresent = true;
        }
        catch (Throwable ignored)
        {
            this.optiFinePresent = false;
        }


        if (this.optiFinePresent)
        {
            try
            {
                Class<?> shadersClass =
                        Class.forName(
                                "net.optifine.shaders.Shaders"
                        );

                java.lang.reflect.Field field =
                        shadersClass.getDeclaredField(
                                "shaderPackLoaded"
                        );

                field.setAccessible(true);

                this.shaderPackLoaded =
                        field.getBoolean(null);
            }
            catch (Throwable ignored)
            {
                this.shaderPackLoaded = false;
            }
        }


        System.out.println(
                "[BBS Animation Editor] "
                        + "Preview framebuffer bridge initialized."
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "OptiFine present: "
                        + this.optiFinePresent
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Shader pack loaded: "
                        + this.shaderPackLoaded
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Preview uses isolated framebuffer: true"
        );
    }


    /*
     * =========================================================
     * STATUS
     * =========================================================
     */

    public boolean isOptiFinePresent()
    {
        this.initialize();

        return this.optiFinePresent;
    }


    public boolean isShaderPackLoaded()
    {
        this.initialize();

        if (!this.optiFinePresent)
        {
            return false;
        }

        try
        {
            Class<?> shadersClass =
                    Class.forName(
                            "net.optifine.shaders.Shaders"
                    );

            java.lang.reflect.Field field =
                    shadersClass.getDeclaredField(
                            "shaderPackLoaded"
                    );

            field.setAccessible(true);

            this.shaderPackLoaded =
                    field.getBoolean(null);
        }
        catch (Throwable ignored)
        {
            this.shaderPackLoaded = false;
        }

        return this.shaderPackLoaded;
    }


    public boolean isShadersActive()
    {
        return this.isShaderPackLoaded();
    }


    public boolean isShaderPipelineActive()
    {
        return this.isOptiFinePresent()
                && this.isShaderPackLoaded();
    }


    public boolean shouldUseShaderPipeline()
    {
        return false;
    }


    /*
     * =========================================================
     * BEGIN PREVIEW
     * =========================================================
     */

    public boolean beginPreview()
    {
        if (this.rendering)
        {
            return true;
        }

        if (this.mc == null)
        {
            return false;
        }

        if (this.previewWidth <= 0 ||
                this.previewHeight <= 0)
        {
            ScaledResolution resolution =
                    new ScaledResolution(
                            this.mc
                    );

            this.previewX = 0;

            this.previewY = 0;

            this.previewWidth =
                    resolution.getScaledWidth();

            this.previewHeight =
                    resolution.getScaledHeight();
        }

        this.initialize();

        this.ensurePreviewFramebuffer();

        if (this.previewFramebuffer == null)
        {
            return false;
        }


        this.saveRenderState();


        try
        {
            this.bindPreviewFramebuffer();

            this.clearPreviewFramebuffer();

            this.preparePreviewState();

            this.rendering = true;

            return true;
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Preview framebuffer start failed."
            );

            e.printStackTrace();

            this.rendering = false;

            this.restorePreviousFramebuffer();

            return false;
        }
    }


    /*
     * =========================================================
     * RENDER PASS
     * =========================================================
     */

    public void beginRenderPass(
            int pass,
            float partialTicks,
            long finishTimeNano)
    {
    }


    public void renderDeferred()
    {
    }


    public void renderComposites()
    {
    }


    /*
     * =========================================================
     * TEXTURE ACCESS
     * =========================================================
     */

    public int getCurrentColorTexture(
            int attachment)
    {
        if (attachment != 0)
        {
            return 0;
        }

        return this.getPreviewTexture();
    }


    public int getOptiFineFramebuffer()
    {
        return 0;
    }


    public int getOptiFineRenderWidth()
    {
        this.initialize();

        if (this.mc == null)
        {
            return 0;
        }

        return this.mc.displayWidth;
    }


    public int getOptiFineRenderHeight()
    {
        this.initialize();

        if (this.mc == null)
        {
            return 0;
        }

        return this.mc.displayHeight;
    }


    public int getUsedColorBuffers()
    {
        return 1;
    }


    /*
     * =========================================================
     * COPY SHADER RESULT
     * =========================================================
     */

    public void copyOptiFineTextureToPreview(
            int attachment)
    {
    }


    /*
     * =========================================================
     * END PREVIEW
     * =========================================================
     */

    public void endPreview()
    {
        if (!this.rendering)
        {
            return;
        }

        try
        {
            this.rendering = false;

            this.restorePreviousFramebuffer();
        }
        catch (Throwable e)
        {
            e.printStackTrace();

            this.rendering = false;

            this.restoreMinecraftFramebuffer();
        }
    }


    /*
     * =========================================================
     * RENDER PREVIEW TEXTURE
     * =========================================================
     *
     * ВАЖНО:
     *
     * Здесь мы больше не пытаемся вручную восстановить
     * каждое состояние OpenGL.
     *
     * Весь state, который меняется для вывода FBO,
     * помещается под GL_PUSH_ATTRIB.
     *
     * После quad выполняется GL_POP_ATTRIB.
     *
     * Это предотвращает утечку состояния Preview
     * в Minecraft GUI / FontRenderer.
     */

    public void renderPreviewTexture(
            int x,
            int y,
            int width,
            int height)
    {
        if (this.mc == null)
        {
            return;
        }

        if (width <= 0 ||
                height <= 0)
        {
            return;
        }

        this.ensurePreviewFramebuffer();

        if (this.previewFramebuffer == null)
        {
            return;
        }

        if (this.previewFramebuffer.framebufferTexture == 0)
        {
            return;
        }


        this.previewX = x;

        this.previewY = y;

        this.previewWidth = width;

        this.previewHeight = height;


        ScaledResolution resolution =
                new ScaledResolution(
                        this.mc
                );

        int scaleFactor =
                resolution.getScaleFactor();


        int px =
                x * scaleFactor;

        int py =
                y * scaleFactor;

        int pw =
                width * scaleFactor;

        int ph =
                height * scaleFactor;


        if (pw <= 0 ||
                ph <= 0)
        {
            return;
        }


        float framebufferWidth =
                this.previewFramebuffer.framebufferWidth;

        float framebufferHeight =
                this.previewFramebuffer.framebufferHeight;


        float regionX =
                x * scaleFactor;

        float regionY =
                framebufferHeight
                        -
                        (
                                (y + height)
                                        * scaleFactor
                        );

        float regionWidth =
                width * scaleFactor;

        float regionHeight =
                height * scaleFactor;


        float u1 =
                regionX /
                        framebufferWidth;

        float u2 =
                (
                        regionX +
                                regionWidth
                )
                        /
                        framebufferWidth;

        float v1 =
                regionY /
                        framebufferHeight;

        float v2 =
                (
                        regionY +
                                regionHeight
                )
                        /
                        framebufferHeight;


        if (u1 < 0F)
        {
            u1 = 0F;
        }

        if (u1 > 1F)
        {
            u1 = 1F;
        }

        if (u2 < 0F)
        {
            u2 = 0F;
        }

        if (u2 > 1F)
        {
            u2 = 1F;
        }

        if (v1 < 0F)
        {
            v1 = 0F;
        }

        if (v1 > 1F)
        {
            v1 = 1F;
        }

        if (v2 < 0F)
        {
            v2 = 0F;
        }

        if (v2 > 1F)
        {
            v2 = 1F;
        }


        /*
         * =========================================================
         * SAVE ALL OPENGL ATTRIBUTE STATE
         * =========================================================
         */

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );


        /*
         * =========================================================
         * SAVE MATRICES
         * =========================================================
         */

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        GL11.glOrtho(
                0,
                this.mc.displayWidth,
                this.mc.displayHeight,
                0,
                -1,
                1
        );


        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();


        /*
         * =========================================================
         * GUI TEXTURE PASS
         * =========================================================
         */

        GL20.glUseProgram(0);

        GL13.glActiveTexture(
                GL13.GL_TEXTURE0
        );

        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glDisable(
                GL11.GL_STENCIL_TEST
        );

        GL11.glDisable(
                GL11.GL_DEPTH_TEST
        );

        GL11.glDepthMask(false);

        GL11.glDisable(
                GL11.GL_CULL_FACE
        );

        GL11.glDisable(
                GL11.GL_LIGHTING
        );

        GL11.glDisable(
                GL11.GL_FOG
        );

        GL11.glEnable(
                GL11.GL_TEXTURE_2D
        );

        GL11.glDisable(
                GL11.GL_ALPHA_TEST
        );

        GL11.glEnable(
                GL11.GL_BLEND
        );

        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        GL11.glColorMask(
                true,
                true,
                true,
                true
        );

        GL11.glColor4f(
                1F,
                1F,
                1F,
                1F
        );


        /*
         * =========================================================
         * PREVIEW TEXTURE
         * =========================================================
         */

        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                this.previewFramebuffer.framebufferTexture
        );


        /*
         * =========================================================
         * QUAD
         * =========================================================
         */

        GL11.glBegin(
                GL11.GL_QUADS
        );


        GL11.glTexCoord2f(
                u1,
                v2
        );

        GL11.glVertex2f(
                px,
                py
        );


        GL11.glTexCoord2f(
                u2,
                v2
        );

        GL11.glVertex2f(
                px + pw,
                py
        );


        GL11.glTexCoord2f(
                u2,
                v1
        );

        GL11.glVertex2f(
                px + pw,
                py + ph
        );


        GL11.glTexCoord2f(
                u1,
                v1
        );

        GL11.glVertex2f(
                px,
                py + ph
        );


        GL11.glEnd();


        /*
         * =========================================================
         * RESTORE MATRICES
         * =========================================================
         */

        GL11.glPopMatrix();


        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();


        GL11.glMatrixMode(
                oldMatrixMode
        );


        /*
         * =========================================================
         * RESTORE ALL OPENGL ATTRIBUTE STATE
         * =========================================================
         */

        GL11.glPopAttrib();
    }


    /*
     * =========================================================
     * FRAMEBUFFER ACCESS
     * =========================================================
     */

    public Framebuffer getPreviewFramebuffer()
    {
        this.ensurePreviewFramebuffer();

        return this.previewFramebuffer;
    }


    public int getPreviewTexture()
    {
        this.ensurePreviewFramebuffer();

        if (this.previewFramebuffer == null)
        {
            return 0;
        }

        return this.previewFramebuffer.framebufferTexture;
    }


    public boolean isRenderingPreview()
    {
        return this.rendering;
    }


    public boolean isOptiFineRendering()
    {
        return false;
    }


    /*
     * =========================================================
     * FRAMEBUFFER CREATION
     * =========================================================
     */

    private void ensurePreviewFramebuffer()
    {
        if (this.mc == null)
        {
            return;
        }

        if (this.mc.displayWidth <= 0 ||
                this.mc.displayHeight <= 0)
        {
            return;
        }


        if (this.previewFramebuffer == null
                ||
                this.previewFramebuffer.framebufferWidth
                        != this.mc.displayWidth
                ||
                this.previewFramebuffer.framebufferHeight
                        != this.mc.displayHeight)
        {
            if (this.previewFramebuffer != null)
            {
                this.previewFramebuffer.deleteFramebuffer();

                this.previewFramebuffer = null;
            }


            this.previewFramebuffer =
                    new Framebuffer(
                            this.mc.displayWidth,
                            this.mc.displayHeight,
                            true
                    );


            this.previewFramebuffer.setFramebufferColor(
                    0.08F,
                    0.09F,
                    0.11F,
                    1F
            );
        }
    }


    /*
     * =========================================================
     * SCALE FACTOR
     * =========================================================
     */

    private int getScaleFactor()
    {
        if (this.mc == null)
        {
            return 1;
        }

        ScaledResolution resolution =
                new ScaledResolution(
                        this.mc
                );

        return resolution.getScaleFactor();
    }


    /*
     * =========================================================
     * BIND PREVIEW FRAMEBUFFER
     * =========================================================
     */

    private void bindPreviewFramebuffer()
    {
        if (this.previewFramebuffer == null)
        {
            return;
        }


        this.previewFramebuffer.bindFramebuffer(
                true
        );


        int framebufferWidth =
                this.previewFramebuffer.framebufferWidth;

        int framebufferHeight =
                this.previewFramebuffer.framebufferHeight;


        if (this.previewWidth <= 0 ||
                this.previewHeight <= 0)
        {
            GL11.glViewport(
                    0,
                    0,
                    framebufferWidth,
                    framebufferHeight
            );

            return;
        }


        int scaleFactor =
                this.getScaleFactor();


        int viewportX =
                this.previewX *
                        scaleFactor;

        int viewportWidth =
                this.previewWidth *
                        scaleFactor;

        int viewportHeight =
                this.previewHeight *
                        scaleFactor;


        int viewportY =
                framebufferHeight
                        -
                        (
                                (this.previewY
                                        +
                                        this.previewHeight)
                                        *
                                        scaleFactor
                        );


        if (viewportX < 0)
        {
            viewportX = 0;
        }

        if (viewportY < 0)
        {
            viewportY = 0;
        }

        if (viewportX >= framebufferWidth)
        {
            viewportX = 0;
        }

        if (viewportY >= framebufferHeight)
        {
            viewportY = 0;
        }


        if (viewportWidth <= 0)
        {
            viewportWidth =
                    framebufferWidth;
        }

        if (viewportHeight <= 0)
        {
            viewportHeight =
                    framebufferHeight;
        }


        if (viewportX +
                viewportWidth >
                framebufferWidth)
        {
            viewportWidth =
                    framebufferWidth -
                            viewportX;
        }


        if (viewportY +
                viewportHeight >
                framebufferHeight)
        {
            viewportHeight =
                    framebufferHeight -
                            viewportY;
        }


        if (viewportWidth <= 0 ||
                viewportHeight <= 0)
        {
            viewportX = 0;

            viewportY = 0;

            viewportWidth =
                    framebufferWidth;

            viewportHeight =
                    framebufferHeight;
        }


        GL11.glViewport(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );
    }


    /*
     * =========================================================
     * CLEAR PREVIEW FRAMEBUFFER
     * =========================================================
     */

    private void clearPreviewFramebuffer()
    {
        if (this.previewFramebuffer == null)
        {
            return;
        }


        GL11.glClearColor(
                0.08F,
                0.09F,
                0.11F,
                1F
        );


        GL11.glDepthMask(true);


        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT
                        |
                        GL11.GL_DEPTH_BUFFER_BIT
                        |
                        GL11.GL_STENCIL_BUFFER_BIT
        );


        GL11.glColor4f(
                1F,
                1F,
                1F,
                1F
        );
    }


    /*
     * =========================================================
     * SAVE STATE
     * =========================================================
     */

    private void saveRenderState()
    {
        this.previousFramebuffer =
                GL11.glGetInteger(
                        EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
                );


        IntBuffer viewport =
                BufferUtils.createIntBuffer(
                        16
                );


        GL11.glGetInteger(
                GL11.GL_VIEWPORT,
                viewport
        );


        viewport.rewind();


        this.previousViewportX =
                viewport.get();

        this.previousViewportY =
                viewport.get();

        this.previousViewportWidth =
                viewport.get();

        this.previousViewportHeight =
                viewport.get();
    }


    /*
     * =========================================================
     * RESTORE PREVIOUS FRAMEBUFFER
     * =========================================================
     */

    private void restorePreviousFramebuffer()
    {
        if (this.previousFramebuffer == 0)
        {
            this.restoreMinecraftFramebuffer();

            return;
        }


        try
        {
            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    this.previousFramebuffer
            );


            GL11.glViewport(
                    this.previousViewportX,
                    this.previousViewportY,
                    this.previousViewportWidth,
                    this.previousViewportHeight
            );


            this.restoreGuiState();
        }
        catch (Throwable e)
        {
            e.printStackTrace();

            this.restoreMinecraftFramebuffer();
        }
    }


    /*
     * =========================================================
     * RESTORE MINECRAFT FRAMEBUFFER
     * =========================================================
     */

    private void restoreMinecraftFramebuffer()
    {
        if (this.mc == null)
        {
            return;
        }


        try
        {
            this.mc.getFramebuffer().bindFramebuffer(
                    false
            );
        }
        catch (Throwable e)
        {
            EXTFramebufferObject.glBindFramebufferEXT(
                    EXTFramebufferObject.GL_FRAMEBUFFER_EXT,
                    0
            );
        }


        GL11.glViewport(
                0,
                0,
                this.mc.displayWidth,
                this.mc.displayHeight
        );


        this.restoreGuiState();
    }


    /*
     * =========================================================
     * PREVIEW STATE
     * =========================================================
     */

    private void preparePreviewState()
    {
        GL20.glUseProgram(0);

        GL13.glActiveTexture(
                GL13.GL_TEXTURE0
        );

        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glDisable(
                GL11.GL_STENCIL_TEST
        );

        GL11.glColorMask(
                true,
                true,
                true,
                true
        );

        GlStateManager.enableTexture2D();

        GlStateManager.enableAlpha();

        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

        GlStateManager.depthMask(
                true
        );

        GlStateManager.enableCull();

        GlStateManager.disableBlend();

        GlStateManager.disableLighting();

        GlStateManager.disableFog();

        GlStateManager.color(
                1F,
                1F,
                1F,
                1F
        );
    }


    /*
     * =========================================================
     * GUI STATE
     * =========================================================
     */

    private void restoreGuiState()
    {
        GL20.glUseProgram(0);

        GL13.glActiveTexture(
                GL13.GL_TEXTURE0
        );

        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glDisable(
                GL11.GL_STENCIL_TEST
        );

        GlStateManager.disableDepth();

        GL11.glDepthMask(false);

        GlStateManager.disableCull();

        GlStateManager.disableLighting();

        GlStateManager.disableFog();

        GlStateManager.enableTexture2D();

        GlStateManager.enableAlpha();

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        GL11.glColorMask(
                true,
                true,
                true,
                true
        );

        GlStateManager.color(
                1F,
                1F,
                1F,
                1F
        );

        GlStateManager.matrixMode(
                GL11.GL_MODELVIEW
        );
    }


    /*
     * =========================================================
     * DIAGNOSTICS
     * =========================================================
     */

    public void printDiagnostics()
    {
        this.initialize();

        System.out.println(
                "[BBS Animation Editor] "
                        + "PreviewShaderBridge diagnostics:"
        );

        System.out.println(
                "  OptiFine present: "
                        + this.isOptiFinePresent()
        );

        System.out.println(
                "  Shader pack loaded: "
                        + this.isShaderPackLoaded()
        );

        System.out.println(
                "  Minecraft shader pipeline active: "
                        + this.isShaderPipelineActive()
        );

        System.out.println(
                "  Preview shader pipeline enabled: "
                        + this.shouldUseShaderPipeline()
        );

        System.out.println(
                "  Rendering preview: "
                        + this.rendering
        );

        System.out.println(
                "  Preview bounds: "
                        + this.previewX
                        + ","
                        + this.previewY
                        + " "
                        + this.previewWidth
                        + "x"
                        + this.previewHeight
        );

        System.out.println(
                "  Preview texture: "
                        + this.getPreviewTexture()
        );

        if (this.mc != null)
        {
            System.out.println(
                    "  Minecraft display size: "
                            + this.mc.displayWidth
                            + "x"
                            + this.mc.displayHeight
            );
        }

        System.out.println(
                "  Previous framebuffer: "
                        + this.previousFramebuffer
        );

        System.out.println(
                "  Previous viewport: "
                        + this.previousViewportX
                        + ","
                        + this.previousViewportY
                        + " "
                        + this.previousViewportWidth
                        + "x"
                        + this.previousViewportHeight
        );

        if (this.previewFramebuffer != null)
        {
            System.out.println(
                    "  Preview framebuffer: "
                            + this.previewFramebuffer.framebufferObject
            );

            System.out.println(
                    "  Preview framebuffer size: "
                            + this.previewFramebuffer.framebufferWidth
                            + "x"
                            + this.previewFramebuffer.framebufferHeight
            );
        }
        else
        {
            System.out.println(
                    "  Preview framebuffer: null"
            );
        }
    }


    /*
     * =========================================================
     * DELETE
     * =========================================================
     */

    public void delete()
    {
        try
        {
            if (this.rendering)
            {
                this.rendering = false;

                this.restorePreviousFramebuffer();
            }
        }
        catch (Throwable ignored)
        {
            try
            {
                this.restoreMinecraftFramebuffer();
            }
            catch (Throwable ignoredAgain)
            {
            }
        }


        try
        {
            if (this.previewFramebuffer != null)
            {
                this.previewFramebuffer.deleteFramebuffer();

                this.previewFramebuffer = null;
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }


        this.rendering = false;
    }
}
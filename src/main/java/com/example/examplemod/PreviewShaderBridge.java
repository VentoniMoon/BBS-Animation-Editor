package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
 * Основная задача класса:
 *
 *     Minecraft world / Actor
 *              ↓
 *        Preview FBO
 *              ↓
 *       Preview texture
 *              ↓
 *          GUI Editor
 *
 * Дополнительно здесь находится безопасный reflection-мост
 * к OptiFine Shaders.
 *
 * OptiFine является полностью необязательной зависимостью.
 * При отсутствии OptiFine обычный Preview продолжает работать.
 */
public class PreviewShaderBridge
{
    private final Minecraft mc;

    // =========================================================
    // PREVIEW FRAMEBUFFER
    // =========================================================

    private Framebuffer previewFramebuffer;

    // =========================================================
    // PREVIEW GUI BOUNDS
    // =========================================================

    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;

    // =========================================================
    // PREVIEW STATE
    // =========================================================

    private boolean initialized;
    private boolean rendering;

    // =========================================================
    // OPTIFINE STATE
    // =========================================================

    private boolean optiFinePresent;
    private boolean shaderPackLoaded;

    private Class<?> shadersClass;

    // =========================================================
    // OPTIFINE REFLECTED FIELDS
    // =========================================================

    private Field fieldShaderPackLoaded;

    private Field fieldDfb;
    private Field fieldDfbColorTextures;
    private Field fieldDfbDepthTextures;

    private Field fieldUsedColorBuffers;
    private Field fieldUsedDepthBuffers;

    private Field fieldRenderWidth;
    private Field fieldRenderHeight;

    private Field fieldProgramFinal;
    private Field fieldProgramsComposite;
    private Field fieldProgramsDeferred;

    /*
     * Minecraft.framebuffer.
     *
     * На время полноценного OptiFine Preview pipeline
     * эта ссылка будет временно указывать на Preview FBO.
     */
    private Field fieldMinecraftFramebuffer;

    // =========================================================
    // OPTIFINE REFLECTED METHODS
    // =========================================================

    private Method methodSetCamera;

    private Method methodBeginRender;

    private Method methodBeginRenderPass;

    private Method methodEndRender;

    private Method methodRenderDeferred;

    private Method methodRenderCompositeFinal;

    // =========================================================
    // SAVED OPENGL STATE
    // =========================================================

    private int previousFramebuffer;

    private int previousViewportX;
    private int previousViewportY;
    private int previousViewportWidth;
    private int previousViewportHeight;

    // =========================================================
    // SAVED MINECRAFT FRAMEBUFFER
    // =========================================================

    private Framebuffer previousMinecraftFramebuffer;

    private boolean optiFineRenderActive;

    // =========================================================
    // LOGGING
    // =========================================================

    private boolean loggedPipelineState;
    private boolean loggedOptiFineResources;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public PreviewShaderBridge(Minecraft mc)
    {
        this.mc = mc;

        this.initialized = false;
        this.rendering = false;

        this.optiFinePresent = false;
        this.shaderPackLoaded = false;

        this.loggedPipelineState = false;
        this.loggedOptiFineResources = false;

        this.previewX = 0;
        this.previewY = 0;
        this.previewWidth = 0;
        this.previewHeight = 0;

        this.previousMinecraftFramebuffer = null;
        this.optiFineRenderActive = false;
    }

    // =========================================================
    // PREVIEW BOUNDS
    // =========================================================

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

    public int getPreviewX()
    {
        return this.previewX;
    }

    public int getPreviewY()
    {
        return this.previewY;
    }

    public int getPreviewWidth()
    {
        return this.previewWidth;
    }

    public int getPreviewHeight()
    {
        return this.previewHeight;
    }

    // =========================================================
    // STATUS
    // =========================================================

    public boolean isOptiFinePresent()
    {
        this.initialize();

        return this.optiFinePresent;
    }

    public boolean isShaderPackLoaded()
    {
        this.initialize();

        this.updateShaderPackState();

        return this.shaderPackLoaded;
    }

    public boolean isShadersActive()
    {
        return this.isShaderPackLoaded();
    }

    public boolean shouldUseShaderPipeline()
    {
        return this.isOptiFinePresent()
                && this.isShaderPackLoaded();
    }

    public boolean isShaderPipelineActive()
    {
        return this.shouldUseShaderPipeline();
    }

    public boolean isRenderingPreview()
    {
        return this.rendering;
    }

    /**
     * Показывает, находится ли Preview сейчас
     * внутри полноценного OptiFine render lifecycle.
     */
    public boolean isOptiFineRendering()
    {
        return this.optiFineRenderActive;
    }

    // =========================================================
    // PREVIEW FRAMEBUFFER API
    // =========================================================

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

    // =========================================================
    // BEGIN PREVIEW
    // =========================================================

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

        if (this.previewWidth <= 0
                || this.previewHeight <= 0)
        {
            ScaledResolution resolution =
                    new ScaledResolution(this.mc);

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
            /*
             * =====================================================
             * PREVIEW FBO
             * =====================================================
             */

            this.bindPreviewFramebuffer();

            this.clearPreviewFramebuffer();

            this.preparePreviewState();


            /*
             * =====================================================
             * DIAGNOSTICS
             * =====================================================
             */

            if (!this.loggedPipelineState)
            {
                System.out.println(
                        "[BBS Animation Editor] PreviewShaderBridge:"
                );

                System.out.println(
                        "  OptiFine present: "
                                + this.optiFinePresent
                );

                System.out.println(
                        "  Shader pack loaded: "
                                + this.shaderPackLoaded
                );

                System.out.println(
                        "  Shader pipeline available: "
                                + this.shouldUseShaderPipeline()
                );

                this.loggedPipelineState = true;
            }


            /*
             * =====================================================
             * OPTIFINE RESOURCES
             * =====================================================
             */

            if (this.shouldUseShaderPipeline())
            {
                this.logOptiFineResources();
            }


            /*
             * =====================================================
             * FULL OPTIFINE PIPELINE
             * =====================================================
             *
             * Раньше здесь pipeline вообще НЕ запускался.
             *
             * Теперь Preview FBO временно становится
             * Minecraft framebuffer, после чего OptiFine
             * запускает свой настоящий world render lifecycle.
             *
             * Это принципиально важно для:
             *
             *     gbuffers_skybasic
             *     gbuffers_skytextured
             *     deferred
             *     composite
             *     final
             *
             */

            if (this.shouldUseShaderPipeline())
            {
                float partialTicks =
                        this.mc.getRenderPartialTicks();

                /*
                 * Даём OptiFine небольшой запас времени.
                 *
                 * Это значение используется OptiFine как
                 * finishTimeNano во время render lifecycle.
                 */
                long finishTimeNano =
                        System.nanoTime()
                                + 1000000000L;


                boolean shaderStarted =
                        this.beginOptiFineRender(
                                partialTicks,
                                finishTimeNano
                        );


                if (!shaderStarted)
                {
                    System.out.println(
                            "[BBS Animation Editor] "
                                    + "OptiFine pipeline could not be started."
                    );

                    /*
                     * В случае ошибки НЕ ломаем Preview.
                     *
                     * Просто продолжаем без shader pipeline.
                     */
                    this.optiFineRenderActive = false;
                }
            }


            /*
             * =====================================================
             * PREVIEW ACTIVE
             * =====================================================
             */

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

            if (this.optiFineRenderActive)
            {
                try
                {
                    this.endOptiFineRender();
                }
                catch (Throwable ignored)
                {
                }
            }

            this.restorePreviousFramebuffer();

            return false;
        }
    }

    // =========================================================
    // END PREVIEW
    // =========================================================

    public void endPreview()
    {
        if (!this.rendering)
        {
            return;
        }

        try
        {
            /*
             * Если полноценный OptiFine pipeline уже был
             * запущен, сначала корректно завершаем его.
             */
            if (this.optiFineRenderActive)
            {
                this.endOptiFineRender();
            }

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

    // =========================================================
    // OPTIFINE PREVIEW RENDER
    // =========================================================

    /**
     * Запускает штатный OptiFine world render lifecycle,
     * но временно заставляет Minecraft считать наш Preview FBO
     * своим главным framebuffer.
     *
     * Это позволяет штатному OptiFine renderFinal()
     * вывести результат не на экран, а в Preview FBO.
     *
     * ВАЖНО:
     *
     * Метод пока НЕ вызывается автоматически из beginPreview().
     * Он будет подключён следующим этапом после проверки
     * этого моста.
     */
    public boolean beginOptiFineRender(
            float partialTicks,
            long finishTimeNano)
    {
        this.initialize();

        if (!this.shouldUseShaderPipeline())
        {
            return false;
        }

        if (this.methodBeginRender == null)
        {
            return false;
        }

        if (this.fieldMinecraftFramebuffer == null)
        {
            return false;
        }

        if (this.previewFramebuffer == null)
        {
            this.ensurePreviewFramebuffer();
        }

        if (this.previewFramebuffer == null)
        {
            return false;
        }

        if (this.optiFineRenderActive)
        {
            return true;
        }

        try
        {
            /*
             * Запоминаем настоящий Minecraft framebuffer.
             */
            Object framebuffer =
                    this.fieldMinecraftFramebuffer.get(
                            this.mc
                    );

            if (framebuffer instanceof Framebuffer)
            {
                this.previousMinecraftFramebuffer =
                        (Framebuffer) framebuffer;
            }
            else
            {
                this.previousMinecraftFramebuffer = null;
            }


            /*
             * Временно подменяем Minecraft framebuffer
             * нашим Preview FBO.
             *
             * Preview FBO имеет полный размер displayWidth x displayHeight,
             * поэтому OptiFine renderFinal() сможет использовать
             * его как обычный экранный framebuffer.
             */
            this.fieldMinecraftFramebuffer.set(
                    this.mc,
                    this.previewFramebuffer
            );


            /*
             * Очень важно:
             *
             * Minecraft.framebuffer теперь указывает на Preview FBO,
             * но OpenGL framebuffer тоже должен быть явно привязан.
             */
            this.previewFramebuffer.bindFramebuffer(true);


            /*
             * После bindFramebuffer Minecraft может изменить viewport,
             * поэтому возвращаем viewport Preview.
             */
            this.bindPreviewFramebuffer();


            /*
             * Запускаем настоящий OptiFine pipeline.
             */
            this.methodBeginRender.invoke(
                    null,
                    this.mc,
                    Float.valueOf(partialTicks),
                    Long.valueOf(finishTimeNano)
            );


            this.optiFineRenderActive = true;

            return true;
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine beginRender failed."
            );

            e.printStackTrace();

            this.restoreMinecraftFramebufferReference();

            this.optiFineRenderActive = false;

            return false;
        }
    }

    // =========================================================
    // END OPTIFINE PREVIEW RENDER
    // =========================================================

    /**
     * Завершает штатный OptiFine world render lifecycle.
     *
     * OptiFine сам выполнит свои deferred/composite/final stages
     * внутри endRender().
     */
    public void endOptiFineRender()
    {
        if (!this.optiFineRenderActive)
        {
            return;
        }

        try
        {
            if (this.methodEndRender != null)
            {
                this.methodEndRender.invoke(
                        null
                );
            }
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine endRender failed."
            );

            e.printStackTrace();
        }
        finally
        {
            this.restoreMinecraftFramebufferReference();

            this.optiFineRenderActive = false;

            /*
             * Возвращаем Preview FBO после OptiFine final/composite,
             * пока endPreview() ещё не восстановил основной Minecraft FBO.
             *
             * Это особенно важно для custom Preview pipeline.
             */
            if (this.rendering
                    && this.previewFramebuffer != null)
            {
                this.previewFramebuffer.bindFramebuffer(true);

                this.bindPreviewFramebuffer();
            }
        }
    }

    // =========================================================
    // OPTIFINE RENDER PASS
    // =========================================================

    /**
     * Запускает штатный OptiFine render pass.
     *
     * Этот метод будет использоваться после подключения
     * полного Preview world lifecycle.
     */
    public void beginRenderPass(
            int pass,
            float partialTicks,
            long finishTimeNano)
    {
        if (!this.shouldUseShaderPipeline())
        {
            return;
        }

        if (this.methodBeginRenderPass == null)
        {
            return;
        }

        if (!this.optiFineRenderActive)
        {
            return;
        }

        try
        {
            this.methodBeginRenderPass.invoke(
                    null,
                    Integer.valueOf(pass),
                    Float.valueOf(partialTicks),
                    Long.valueOf(finishTimeNano)
            );
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine beginRenderPass failed."
            );

            e.printStackTrace();
        }
    }

    // =========================================================
    // DEFERRED
    // =========================================================

    /**
     * Пока напрямую не вызывается.
     *
     * Штатный OptiFine endRender() самостоятельно
     * управляет deferred/composite/final stages.
     */
    public void renderDeferred()
    {
        if (!this.shouldUseShaderPipeline())
        {
            return;
        }

        if (!this.optiFineRenderActive)
        {
            return;
        }

        if (this.methodRenderDeferred == null)
        {
            return;
        }

        try
        {
            this.methodRenderDeferred.invoke(
                    null
            );
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine renderDeferred failed."
            );

            e.printStackTrace();
        }
    }

    // =========================================================
    // COMPOSITE
    // =========================================================

    /**
     * Пока напрямую не вызывается.
     *
     * Штатный OptiFine endRender() должен выполнять
     * необходимую последовательность самостоятельно.
     */
    public void renderComposites()
    {
        if (!this.shouldUseShaderPipeline())
        {
            return;
        }

        if (!this.optiFineRenderActive)
        {
            return;
        }

        if (this.methodRenderCompositeFinal == null)
        {
            return;
        }

        try
        {
            this.methodRenderCompositeFinal.invoke(
                    null
            );
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine renderCompositeFinal failed."
            );

            e.printStackTrace();
        }
    }

    // =========================================================
    // FUTURE TEXTURE COPY
    // =========================================================

    public void copyOptiFineTextureToPreview(
            int attachment)
    {
        /*
         * На этом этапе не требуется.
         *
         * Полный pipeline будет писать непосредственно
         * в Preview FBO через временную подмену
         * Minecraft.framebuffer.
         */
    }

    // =========================================================
    // PREVIEW COLOR TEXTURE
    // =========================================================

    public int getCurrentColorTexture(
            int attachment)
    {
        if (attachment != 0)
        {
            return 0;
        }

        return this.getPreviewTexture();
    }

    // =========================================================
    // OPTIFINE FRAMEBUFFER INFO
    // =========================================================

    public int getOptiFineFramebuffer()
    {
        this.initialize();

        if (!this.optiFinePresent)
        {
            return 0;
        }

        Object dfb =
                this.getStaticFieldValue(
                        this.fieldDfb
                );

        if (dfb == null)
        {
            return 0;
        }

        Integer id =
                this.findIntegerField(
                        dfb,
                        "framebuffer",
                        "framebufferObject",
                        "framebufferID",
                        "id"
                );

        return id == null
                ? 0
                : id.intValue();
    }

    // =========================================================
    // OPTIFINE RENDER SIZE
    // =========================================================

    public int getOptiFineRenderWidth()
    {
        this.initialize();

        Integer value =
                this.getStaticInteger(
                        this.fieldRenderWidth
                );

        if (value != null
                && value.intValue() > 0)
        {
            return value.intValue();
        }

        return this.mc == null
                ? 0
                : this.mc.displayWidth;
    }

    public int getOptiFineRenderHeight()
    {
        this.initialize();

        Integer value =
                this.getStaticInteger(
                        this.fieldRenderHeight
                );

        if (value != null
                && value.intValue() > 0)
        {
            return value.intValue();
        }

        return this.mc == null
                ? 0
                : this.mc.displayHeight;
    }

    // =========================================================
    // OPTIFINE COLOR BUFFER COUNT
    // =========================================================

    public int getUsedColorBuffers()
    {
        this.initialize();

        Integer value =
                this.getStaticInteger(
                        this.fieldUsedColorBuffers
                );

        if (value == null)
        {
            return 0;
        }

        return value.intValue();
    }

    public int getUsedDepthBuffers()
    {
        this.initialize();

        Integer value =
                this.getStaticInteger(
                        this.fieldUsedDepthBuffers
                );

        if (value == null)
        {
            return 0;
        }

        return value.intValue();
    }

    // =========================================================
    // OPTIFINE TEXTURES
    // =========================================================

    public int getOptiFineColorTexture(
            int attachment)
    {
        this.initialize();

        if (!this.optiFinePresent
                || attachment < 0)
        {
            return 0;
        }

        Object textures =
                this.getStaticFieldValue(
                        this.fieldDfbColorTextures
                );

        return this.getTextureFromArray(
                textures,
                attachment
        );
    }

    public int getOptiFineDepthTexture(
            int attachment)
    {
        this.initialize();

        if (!this.optiFinePresent
                || attachment < 0)
        {
            return 0;
        }

        Object textures =
                this.getStaticFieldValue(
                        this.fieldDfbDepthTextures
                );

        return this.getTextureFromArray(
                textures,
                attachment
        );
    }

    // =========================================================
    // CAMERA BRIDGE
    // =========================================================

    public boolean setOptiFineCamera(
            float partialTicks)
    {
        this.initialize();

        if (!this.optiFinePresent)
        {
            return false;
        }

        if (this.methodSetCamera == null)
        {
            return false;
        }

        try
        {
            this.methodSetCamera.invoke(
                    null,
                    Float.valueOf(partialTicks)
            );

            return true;
        }
        catch (Throwable e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine setCamera failed."
            );

            e.printStackTrace();

            return false;
        }
    }

    // =========================================================
    // RENDER PREVIEW TO GUI
    // =========================================================

    public void renderPreviewTexture(
            int x,
            int y,
            int width,
            int height)
    {
        if (this.mc == null
                || width <= 0
                || height <= 0)
        {
            return;
        }

        this.ensurePreviewFramebuffer();

        if (this.previewFramebuffer == null
                || this.previewFramebuffer.framebufferTexture == 0)
        {
            return;
        }

        this.previewX = x;
        this.previewY = y;
        this.previewWidth = width;
        this.previewHeight = height;

        ScaledResolution resolution =
                new ScaledResolution(this.mc);

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

        if (pw <= 0 || ph <= 0)
        {
            return;
        }

        float fbWidth =
                this.previewFramebuffer.framebufferWidth;

        float fbHeight =
                this.previewFramebuffer.framebufferHeight;

        float regionX =
                x * scaleFactor;

        float regionY =
                fbHeight
                        - ((y + height) * scaleFactor);

        float regionWidth =
                width * scaleFactor;

        float regionHeight =
                height * scaleFactor;

        float u1 =
                regionX / fbWidth;

        float u2 =
                (regionX + regionWidth) / fbWidth;

        float v1 =
                regionY / fbHeight;

        float v2 =
                (regionY + regionHeight) / fbHeight;

        u1 = clamp01(u1);
        u2 = clamp01(u2);
        v1 = clamp01(v1);
        v2 = clamp01(v2);


        /*
         * НЕ МЕНЯТЬ ЭТУ СХЕМУ БЕЗ НЕОБХОДИМОСТИ.
         *
         * GL_ALL_ATTRIB_BITS нужен для того,
         * чтобы Preview не оставлял после себя состояние,
         * которое портит GUI.
         */

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

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


        // =====================================================
        // GUI STATE
        // =====================================================

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


        // =====================================================
        // DRAW
        // =====================================================

        GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                this.previewFramebuffer.framebufferTexture
        );

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


        // =====================================================
        // RESTORE
        // =====================================================

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                oldMatrixMode
        );

        GL11.glPopAttrib();
    }

    // =========================================================
    // INITIALIZATION
    // =========================================================

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
            this.shadersClass =
                    Class.forName(
                            "net.optifine.shaders.Shaders"
                    );

            this.optiFinePresent = true;

            this.prepareOptiFineReflection();
        }
        catch (Throwable e)
        {
            this.optiFinePresent = false;
            this.shadersClass = null;
        }

        if (this.optiFinePresent)
        {
            this.updateShaderPackState();
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "PreviewShaderBridge initialized."
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
    }

    // =========================================================
    // OPTIFINE REFLECTION SETUP
    // =========================================================

    private void prepareOptiFineReflection()
    {
        if (this.shadersClass == null)
        {
            return;
        }

        this.fieldShaderPackLoaded =
                findField(
                        this.shadersClass,
                        "shaderPackLoaded"
                );

        this.fieldDfb =
                findField(
                        this.shadersClass,
                        "dfb"
                );

        this.fieldDfbColorTextures =
                findField(
                        this.shadersClass,
                        "dfbColorTextures"
                );

        this.fieldDfbDepthTextures =
                findField(
                        this.shadersClass,
                        "dfbDepthTextures"
                );

        this.fieldUsedColorBuffers =
                findField(
                        this.shadersClass,
                        "usedColorBuffers"
                );

        this.fieldUsedDepthBuffers =
                findField(
                        this.shadersClass,
                        "usedDepthBuffers"
                );

        this.fieldRenderWidth =
                findField(
                        this.shadersClass,
                        "renderWidth"
                );

        this.fieldRenderHeight =
                findField(
                        this.shadersClass,
                        "renderHeight"
                );

        this.fieldProgramFinal =
                findField(
                        this.shadersClass,
                        "ProgramFinal"
                );

        this.fieldProgramsComposite =
                findField(
                        this.shadersClass,
                        "ProgramsComposite"
                );

        this.fieldProgramsDeferred =
                findField(
                        this.shadersClass,
                        "ProgramsDeferred"
                );

        this.methodSetCamera =
                findMethod(
                        this.shadersClass,
                        "setCamera",
                        float.class
                );

        this.methodBeginRender =
                findMethod(
                        this.shadersClass,
                        "beginRender",
                        Minecraft.class,
                        float.class,
                        long.class
                );

        this.methodBeginRenderPass =
                findMethod(
                        this.shadersClass,
                        "beginRenderPass",
                        int.class,
                        float.class,
                        long.class
                );

        this.methodEndRender =
                findMethod(
                        this.shadersClass,
                        "endRender"
                );

        this.methodRenderDeferred =
                findMethod(
                        this.shadersClass,
                        "renderDeferred"
                );

        this.methodRenderCompositeFinal =
                findMethod(
                        this.shadersClass,
                        "renderCompositeFinal"
                );

        this.fieldMinecraftFramebuffer =
                findField(
                        Minecraft.class,
                        "framebuffer"
                );
    }

    // =========================================================
    // SHADER PACK STATE
    // =========================================================

    private void updateShaderPackState()
    {
        if (!this.optiFinePresent
                || this.fieldShaderPackLoaded == null)
        {
            this.shaderPackLoaded = false;

            return;
        }

        try
        {
            Object value =
                    this.fieldShaderPackLoaded.get(null);

            this.shaderPackLoaded =
                    value instanceof Boolean
                            && ((Boolean) value).booleanValue();
        }
        catch (Throwable ignored)
        {
            this.shaderPackLoaded = false;
        }
    }

    // =========================================================
    // OPTIFINE DIAGNOSTICS
    // =========================================================

    private void logOptiFineResources()
    {
        if (this.loggedOptiFineResources)
        {
            return;
        }

        this.loggedOptiFineResources = true;

        System.out.println(
                "[BBS Animation Editor] "
                        + "===== OptiFine Preview Bridge ====="
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "DFB = "
                        + this.getOptiFineFramebuffer()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "render size = "
                        + this.getOptiFineRenderWidth()
                        + "x"
                        + this.getOptiFineRenderHeight()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "used color buffers = "
                        + this.getUsedColorBuffers()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "used depth buffers = "
                        + this.getUsedDepthBuffers()
        );

        for (int i = 0; i < 16; i++)
        {
            int color =
                    this.getOptiFineColorTexture(i);

            if (color != 0)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "colortex"
                                + i
                                + " = "
                                + color
                );
            }
        }

        for (int i = 0; i < 8; i++)
        {
            int depth =
                    this.getOptiFineDepthTexture(i);

            if (depth != 0)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "depthtex"
                                + i
                                + " = "
                                + depth
                );
            }
        }

        Object programFinal =
                this.getStaticFieldValue(
                        this.fieldProgramFinal
                );

        Object programsComposite =
                this.getStaticFieldValue(
                        this.fieldProgramsComposite
                );

        Object programsDeferred =
                this.getStaticFieldValue(
                        this.fieldProgramsDeferred
                );

        System.out.println(
                "[BBS Animation Editor] "
                        + "ProgramFinal = "
                        + describeObject(
                        programFinal
                )
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "ProgramsComposite = "
                        + describeObject(
                        programsComposite
                )
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "ProgramsDeferred = "
                        + describeObject(
                        programsDeferred
                )
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "===================================="
        );
    }

    // =========================================================
    // FRAMEBUFFER CREATION
    // =========================================================

    private void ensurePreviewFramebuffer()
    {
        if (this.mc == null
                || this.mc.displayWidth <= 0
                || this.mc.displayHeight <= 0)
        {
            return;
        }

        if (this.previewFramebuffer == null
                || this.previewFramebuffer.framebufferWidth
                != this.mc.displayWidth
                || this.previewFramebuffer.framebufferHeight
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

    // =========================================================
    // BIND PREVIEW FRAMEBUFFER
    // =========================================================

    private void bindPreviewFramebuffer()
    {
        if (this.previewFramebuffer == null)
        {
            return;
        }

        this.previewFramebuffer.bindFramebuffer(true);

        int fbWidth =
                this.previewFramebuffer.framebufferWidth;

        int fbHeight =
                this.previewFramebuffer.framebufferHeight;

        if (this.previewWidth <= 0
                || this.previewHeight <= 0)
        {
            GL11.glViewport(
                    0,
                    0,
                    fbWidth,
                    fbHeight
            );

            return;
        }

        int scale =
                this.getScaleFactor();

        int vx =
                this.previewX * scale;

        int vy =
                fbHeight
                        - ((this.previewY
                        + this.previewHeight) * scale);

        int vw =
                this.previewWidth * scale;

        int vh =
                this.previewHeight * scale;

        vx = Math.max(0, vx);
        vy = Math.max(0, vy);

        if (vx + vw > fbWidth)
        {
            vw = fbWidth - vx;
        }

        if (vy + vh > fbHeight)
        {
            vh = fbHeight - vy;
        }

        if (vw <= 0 || vh <= 0)
        {
            vx = 0;
            vy = 0;
            vw = fbWidth;
            vh = fbHeight;
        }

        GL11.glViewport(
                vx,
                vy,
                vw,
                vh
        );
    }

    // =========================================================
    // CLEAR PREVIEW
    // =========================================================

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
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_STENCIL_BUFFER_BIT
        );

        GL11.glColor4f(
                1F,
                1F,
                1F,
                1F
        );
    }

    // =========================================================
    // PREVIEW GL STATE
    // =========================================================

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

        GlStateManager.depthMask(true);

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

    // =========================================================
    // SAVE GL STATE
    // =========================================================

    private void saveRenderState()
    {
        this.previousFramebuffer =
                GL11.glGetInteger(
                        EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT
                );

        IntBuffer viewport =
                BufferUtils.createIntBuffer(16);

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

    // =========================================================
    // RESTORE PREVIOUS FRAMEBUFFER
    // =========================================================

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

    // =========================================================
    // RESTORE MINECRAFT FRAMEBUFFER REFERENCE
    // =========================================================

    private void restoreMinecraftFramebufferReference()
    {
        if (this.mc == null)
        {
            return;
        }

        if (this.fieldMinecraftFramebuffer == null)
        {
            return;
        }

        try
        {
            this.fieldMinecraftFramebuffer.set(
                    this.mc,
                    this.previousMinecraftFramebuffer
            );
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }

        this.previousMinecraftFramebuffer = null;
    }

    // =========================================================
    // RESTORE MINECRAFT FRAMEBUFFER
    // =========================================================

    private void restoreMinecraftFramebuffer()
    {
        if (this.mc == null)
        {
            return;
        }

        try
        {
            this.mc.getFramebuffer()
                    .bindFramebuffer(false);
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

    // =========================================================
    // GUI STATE
    // =========================================================

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

    // =========================================================
    // SCALE
    // =========================================================

    private int getScaleFactor()
    {
        if (this.mc == null)
        {
            return 1;
        }

        return new ScaledResolution(this.mc)
                .getScaleFactor();
    }

    // =========================================================
    // REFLECTION HELPERS
    // =========================================================

    private static Field findField(
            Class<?> owner,
            String name)
    {
        if (owner == null)
        {
            return null;
        }

        try
        {
            Field field =
                    owner.getDeclaredField(name);

            field.setAccessible(true);

            return field;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static Method findMethod(
            Class<?> owner,
            String name,
            Class<?>... parameterTypes)
    {
        if (owner == null)
        {
            return null;
        }

        try
        {
            Method method =
                    owner.getDeclaredMethod(
                            name,
                            parameterTypes
                    );

            method.setAccessible(true);

            return method;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private Object getStaticFieldValue(
            Field field)
    {
        if (field == null)
        {
            return null;
        }

        try
        {
            return field.get(null);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private Integer getStaticInteger(
            Field field)
    {
        Object value =
                this.getStaticFieldValue(field);

        if (value instanceof Integer)
        {
            return (Integer) value;
        }

        return null;
    }

    // =========================================================
    // TEXTURE ARRAY ACCESS
    // =========================================================

    private int getTextureFromArray(
            Object array,
            int index)
    {
        if (array == null
                || index < 0)
        {
            return 0;
        }

        try
        {
            if (array instanceof int[])
            {
                int[] values =
                        (int[]) array;

                if (index >= values.length)
                {
                    return 0;
                }

                return values[index];
            }

            if (array instanceof Integer[])
            {
                Integer[] values =
                        (Integer[]) array;

                if (index >= values.length
                        || values[index] == null)
                {
                    return 0;
                }

                return values[index].intValue();
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0;
    }

    // =========================================================
    // GENERIC OBJECT FIELD SEARCH
    // =========================================================

    private Integer findIntegerField(
            Object object,
            String... names)
    {
        if (object == null)
        {
            return null;
        }

        Class<?> type =
                object.getClass();

        for (String name : names)
        {
            try
            {
                Field field =
                        type.getDeclaredField(name);

                field.setAccessible(true);

                Object value =
                        field.get(object);

                if (value instanceof Integer)
                {
                    return (Integer) value;
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        return null;
    }

    // =========================================================
    // OBJECT DESCRIPTION
    // =========================================================

    private static String describeObject(
            Object object)
    {
        if (object == null)
        {
            return "null";
        }

        if (object.getClass().isArray())
        {
            return object.getClass()
                    .getComponentType()
                    .getName()
                    + "[]";
        }

        return object.getClass().getName();
    }

    // =========================================================
    // CLAMP
    // =========================================================

    private static float clamp01(
            float value)
    {
        if (value < 0F)
        {
            return 0F;
        }

        if (value > 1F)
        {
            return 1F;
        }

        return value;
    }

    // =========================================================
    // DIAGNOSTICS
    // =========================================================

    public void printDiagnostics()
    {
        this.initialize();

        this.updateShaderPackState();

        System.out.println(
                "[BBS Animation Editor] "
                        + "PreviewShaderBridge diagnostics:"
        );

        System.out.println(
                "  OptiFine present: "
                        + this.optiFinePresent
        );

        System.out.println(
                "  Shader pack loaded: "
                        + this.shaderPackLoaded
        );

        System.out.println(
                "  Shader pipeline available: "
                        + this.shouldUseShaderPipeline()
        );

        System.out.println(
                "  Rendering preview: "
                        + this.rendering
        );

        System.out.println(
                "  OptiFine rendering: "
                        + this.optiFineRenderActive
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

        System.out.println(
                "  OptiFine DFB: "
                        + this.getOptiFineFramebuffer()
        );

        System.out.println(
                "  OptiFine render size: "
                        + this.getOptiFineRenderWidth()
                        + "x"
                        + this.getOptiFineRenderHeight()
        );

        System.out.println(
                "  OptiFine color buffers: "
                        + this.getUsedColorBuffers()
        );

        System.out.println(
                "  OptiFine depth buffers: "
                        + this.getUsedDepthBuffers()
        );

        if (this.mc != null)
        {
            System.out.println(
                    "  Minecraft display: "
                            + this.mc.displayWidth
                            + "x"
                            + this.mc.displayHeight
            );
        }

        if (this.previewFramebuffer != null)
        {
            System.out.println(
                    "  Preview FBO size: "
                            + this.previewFramebuffer.framebufferWidth
                            + "x"
                            + this.previewFramebuffer.framebufferHeight
            );
        }
        else
        {
            System.out.println(
                    "  Preview FBO: null"
            );
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    public void delete()
    {
        try
        {
            if (this.optiFineRenderActive)
            {
                this.endOptiFineRender();
            }
        }
        catch (Throwable ignored)
        {
            this.restoreMinecraftFramebufferReference();
        }

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

        this.optiFineRenderActive = false;

        this.previousMinecraftFramebuffer = null;
    }
}
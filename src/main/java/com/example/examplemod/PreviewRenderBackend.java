package com.example.examplemod;

import net.minecraft.client.Minecraft;


/**
 * Центральный менеджер 3D Preview.
 *
 * Отвечает только за общий lifecycle Preview:
 *
 *     beginPreviewRender()
 *             ↓
 *     подготовка EditorCamera
 *             ↓
 *     beginOptiFineRender()
 *             ↓
 *     Minecraft / Actor / Weather rendering
 *             ↓
 *     OptiFine endRender()
 *             ↓
 *     endPreviewRender()
 *
 * Вся работа с framebuffer и OptiFine находится
 * внутри PreviewShaderBridge.
 */
public class PreviewRenderBackend
{
    private final Minecraft mc;

    private final PreviewShaderBridge shaderBridge;

    private boolean diagnosticsPrinted;

    private boolean previewRenderActive;


    public PreviewRenderBackend(Minecraft mc)
    {
        this.mc = mc;

        this.shaderBridge =
                new PreviewShaderBridge(mc);

        this.diagnosticsPrinted = false;

        this.previewRenderActive = false;
    }


    /*
     * =========================================================
     * ACCESS
     * =========================================================
     */

    public Minecraft getMinecraft()
    {
        return this.mc;
    }


    public PreviewShaderBridge getShaderBridge()
    {
        return this.shaderBridge;
    }


    public void setPreviewBounds(
            int x,
            int y,
            int width,
            int height)
    {
        this.shaderBridge.setPreviewBounds(
                x,
                y,
                width,
                height
        );
    }


    /*
     * =========================================================
     * LEGACY ACCESS
     * =========================================================
     */

    public PreviewFramebuffer getPreviewFramebuffer()
    {
        return null;
    }


    /*
     * =========================================================
     * OPTIFINE
     * =========================================================
     */

    public boolean isOptiFinePresent()
    {
        return this.shaderBridge.isOptiFinePresent();
    }


    public boolean isShadersActive()
    {
        return this.shaderBridge.isShaderPackLoaded();
    }


    public boolean shouldUseShaderPipeline()
    {
        return this.shaderBridge.isShaderPipelineActive();
    }


    /*
     * =========================================================
     * BEGIN PREVIEW
     * =========================================================
     */

    public void beginPreviewRender()
    {
        if (this.previewRenderActive)
        {
            return;
        }


        if (this.mc == null)
        {
            return;
        }


        /*
         * Только создаём Preview FBO
         * и подготавливаем базовое состояние.
         *
         * OptiFine здесь намеренно НЕ запускается.
         *
         * EditorSceneViewport сначала должен установить
         * projection/modelview EditorCamera.
         */
        if (!this.shaderBridge.beginPreview())
        {
            return;
        }


        this.previewRenderActive = true;
    }


    /*
     * =========================================================
     * BEGIN OPTIFINE
     * =========================================================
     *
     * Этот метод вызывается ПОСЛЕ того, как
     * PreviewWorldRenderer установил EditorCamera.
     */

    public boolean beginOptiFineRender()
    {
        if (!this.previewRenderActive)
        {
            return false;
        }

        if (this.mc == null)
        {
            return false;
        }

        if (!this.shaderBridge.shouldUseShaderPipeline())
        {
            return false;
        }


        /*
         * Если OptiFine уже запущен,
         * повторно beginRender() не вызываем.
         */
        if (this.shaderBridge.isOptiFineRendering())
        {
            return true;
        }


        float partialTicks =
                this.mc.getRenderPartialTicks();

        long finishTimeNano =
                System.nanoTime();


        boolean shaderStarted =
                this.shaderBridge.beginOptiFineRender(
                        partialTicks,
                        finishTimeNano
                );


        if (!shaderStarted)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "OptiFine Preview pipeline "
                            + "could not be started. "
                            + "Continuing with normal Preview."
            );
        }


        return shaderStarted;
    }


    /*
     * =========================================================
     * END PREVIEW
     * =========================================================
     */

    public void endPreviewRender()
    {
        if (!this.previewRenderActive)
        {
            return;
        }


        try
        {
            /*
             * PreviewShaderBridge.endPreview()
             * самостоятельно завершит OptiFine lifecycle,
             * если он был запущен.
             */
            this.shaderBridge.endPreview();
        }
        finally
        {
            this.previewRenderActive = false;
        }
    }


    public boolean isPreviewRenderActive()
    {
        return this.previewRenderActive;
    }


    /*
     * =========================================================
     * DRAW PREVIEW TO GUI
     * =========================================================
     */

    public void renderPreviewToScreen(
            int x,
            int y,
            int width,
            int height)
    {
        if (this.mc == null)
        {
            return;
        }


        if (width <= 0 || height <= 0)
        {
            return;
        }


        this.shaderBridge.renderPreviewTexture(
                x,
                y,
                width,
                height
        );
    }


    /*
     * =========================================================
     * DIAGNOSTICS
     * =========================================================
     */

    public void printDiagnostics()
    {
        if (this.diagnosticsPrinted)
        {
            return;
        }


        this.diagnosticsPrinted = true;


        System.out.println(
                "[BBS Animation Editor] "
                        + "===== PreviewRenderBackend ====="
        );


        System.out.println(
                "[BBS Animation Editor] display="
                        +
                        (
                                this.mc == null
                                        ? "null"
                                        : this.mc.displayWidth
                                        + "x"
                                        + this.mc.displayHeight
                        )
        );


        System.out.println(
                "[BBS Animation Editor] OptiFine="
                        + this.isOptiFinePresent()
        );


        System.out.println(
                "[BBS Animation Editor] Shaders="
                        + this.isShadersActive()
        );


        System.out.println(
                "[BBS Animation Editor] Shader pipeline="
                        + this.shouldUseShaderPipeline()
        );


        this.shaderBridge.printDiagnostics();


        System.out.println(
                "[BBS Animation Editor] "
                        + "================================="
        );
    }


    /*
     * =========================================================
     * DELETE
     * =========================================================
     */

    public void delete()
    {
        if (this.previewRenderActive)
        {
            this.endPreviewRender();
        }


        this.shaderBridge.delete();
    }
}
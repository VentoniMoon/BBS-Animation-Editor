package com.example.examplemod;

import net.minecraft.client.Minecraft;


/**
 * Центральный менеджер 3D Preview.
 *
 * Этот класс намеренно не занимается OpenGL state.
 *
 * Его задача только:
 *
 *     beginPreviewRender()
 *             ↓
 *     Minecraft / Actor / Weather rendering
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
     *
     * Старый PreviewFramebuffer больше не принадлежит Backend.
     *
     * Метод оставлен для совместимости с остальным проектом.
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


        if (!this.shaderBridge.beginPreview())
        {
            return;
        }


        this.previewRenderActive = true;
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


        /*
         * PreviewShaderBridge полностью отвечает
         * за вывод Preview в GUI.
         */
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
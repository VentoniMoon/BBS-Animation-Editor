package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Preview FS compositor facade.
 *
 * The original PreviewShaderBridge was responsible for nested Minecraft /
 * OptiFine world passes, 2500 lines of shader hooks and legacy FBO state.
 * That is no longer the active preview pipeline.
 *
 * Our world image comes from the finished native Minecraft framebuffer.
 * BbsFsPreviewTarget owns ONLY the editor composition target: world image,
 * recorded actors, body parts and editor gizmos. It presents that target as
 * an ordinary GUI texture, following BBSRendering/UIFilmPreview in BBS FS.
 */
public final class PreviewRenderBackend
{
    private final Minecraft mc;
    private final BbsFsPreviewTarget target;

    /**
     * Retained strictly for code compatibility with the retired
     * PreviewNativeWorldBridge. Normal Preview FS never touches it.
     */
    private PreviewShaderBridge legacyBridge;

    public PreviewRenderBackend(Minecraft mc)
    {
        this.mc = mc;
        this.target = new BbsFsPreviewTarget(mc);
    }

    public Minecraft getMinecraft()
    {
        return this.mc;
    }

    public BbsFsPreviewTarget getNativeTarget()
    {
        return this.target;
    }

    @Deprecated
    public PreviewShaderBridge getShaderBridge()
    {
        if (legacyBridge == null)
            legacyBridge = new PreviewShaderBridge(mc);
        return legacyBridge;
    }

    public void setPreviewBounds(int x, int y, int width, int height)
    {
        target.setBounds(x, y, width, height);
    }

    /** Old preview FBO abstraction is no longer part of rendering. */
    @Deprecated
    public PreviewFramebuffer getPreviewFramebuffer()
    {
        return null;
    }

    public boolean isOptiFinePresent()
    {
        return legacyBridge != null && legacyBridge.isOptiFinePresent();
    }

    public boolean isShadersActive()
    {
        return legacyBridge != null && legacyBridge.isShaderPackLoaded();
    }

    public boolean shouldUseShaderPipeline()
    {
        return false; // Minecraft/OptiFine already rendered the world.
    }

    public void beginPreviewRender()
    {
        target.begin();
    }

    /** World shader lifecycle belongs to Minecraft, not the GUI. */
    @Deprecated
    public boolean beginOptiFineRender()
    {
        return false;
    }

    public void endPreviewRender()
    {
        target.end();
    }

    public boolean isPreviewRenderActive()
    {
        return target.isActive();
    }

    public void renderPreviewToScreen(int x, int y, int width, int height)
    {
        target.present();
    }

    public void printDiagnostics()
    {
        System.out.println("[BBS Editor][FS Preview] Native Minecraft world "
                + "capture, dedicated film texture compositor, no duplicate "
                + "OptiFine pipeline. " + (mc == null ? "Minecraft=null" :
                ("display=" + mc.displayWidth + "x" + mc.displayHeight)));
    }

    public void delete()
    {
        target.dispose();
        if (legacyBridge != null)
        {
            legacyBridge.delete();
            legacyBridge = null;
        }
    }
}

package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Sole composition backend for the BBS FS-style editor preview.
 *
 * Minecraft/OptiFine renders the world once through the normal game
 * lifecycle; BbsFsWorldFrameCapture copies that completed frame into
 * BbsFsPreviewTarget. Native actor rendering occurs before this capture,
 * during RenderWorldLastEvent. No world/chunk/shader pass runs from here.
 */
public final class PreviewRenderBackend
{
    private final Minecraft mc;
    private final BbsFsPreviewTarget target;

    public PreviewRenderBackend(Minecraft mc)
    {
        this.mc=mc;
        this.target=new BbsFsPreviewTarget(mc);
    }

    public Minecraft getMinecraft() { return mc; }

    public BbsFsPreviewTarget getNativeTarget() { return target; }

    public void setPreviewBounds(int x, int y, int width, int height)
    {
        target.setBounds(x,y,width,height);
    }

    public void beginPreviewRender()
    {
        target.begin();
    }

    public void endPreviewRender()
    {
        target.end();
    }

    public boolean isPreviewRenderActive()
    {
        return target.isActive();
    }

    public void renderPreviewToScreen(int x,int y,int width,int height)
    {
        target.present();
    }

    public void printDiagnostics()
    {
        System.out.println("[BBS Editor][FS Preview] Native world "
                + "capture and film compositor; no nested OptiFine pass; "
                + (mc==null?"Minecraft=null":
                ("display="+mc.displayWidth+"x"+mc.displayHeight)));
    }

    public void delete()
    {
        target.dispose();
    }
}

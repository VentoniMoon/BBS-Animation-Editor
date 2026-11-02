package com.example.examplemod;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Final film-to-GUI restoration only for CAMERA mirror visibility.
 * Isolated from export-probe runtime and its Java 8 smoke-test stubs.
 */
public final class CameraMirrorGuiFontState
{
    private CameraMirrorGuiFontState(){}

    private static boolean mirrorFontSyncReported;

    /**
     * Restore the fixed-function GUI/font pipeline after a visible BSL
     * mirror. The secondary mirror renderer and its late composite use
     * raw OpenGL texture/program/blend calls; glPopAttrib restores the
     * DRIVER but not Minecraft 1.12's GlStateManager caches.
     *
     * Result when a cached "blend enabled" disagrees with GL_BLEND=false:
     * transparent pixels of every FontRenderer glyph quad become opaque
     * black rectangles. Unlike a per-widget label background, this affects
     * ANY font drawn after the visible mirror.
     *
     * This runs only AFTER native BSL has completed and its film frame
     * has been captured, at the beginning of the editor GUI draw. Never
     * change these states inside the reflection, GBuffer or sun shadow
     * passes (they have independent render-state requirements).
     */
    public static void resync()
    {
        if("false".equalsIgnoreCase(System.getProperty(
                "bbs.editor.camera.mirrorGuiFontState","true")))return;
        int oldProgram=GL11.glGetInteger(
                org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
        boolean oldBlend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean oldAlpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        // At the film-to-GUI boundary, custom BSL terrain/final shaders
        // must no longer process FontRenderer glyphs.
        org.lwjgl.opengl.GL20.glUseProgram(0);

        // Force both the real driver and Java texture cache onto the
        // default glyph sampler; the normal resync is ordinarily used
        // only for an explicit native-resolution export.
        CameraNativeFrameProbe.resyncDefaultTextureForGui();

        // Force a change in both directions to repair stale Java-side
        // cache booleans. Force actual GL afterwards as a final safeguard
        // even when the cache skipped one of the commands.
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
        org.lwjgl.opengl.GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,GL11.GL_ZERO);

        GlStateManager.disableAlpha();
        GlStateManager.enableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.alphaFunc(GL11.GL_GREATER,0.1F);
        GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);

        // Font atlas is an alpha-textured quad in fixed-function GUI
        // rendering; REPLACE/COMBINE can lose the vertex's color/alpha.
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV,
                GL11.GL_TEXTURE_ENV_MODE,GL11.GL_MODULATE);
        GlStateManager.colorMask(true,true,true,true);
        GL11.glColorMask(true,true,true,true);
        GlStateManager.color(1F,1F,1F,1F);
        GL11.glColor4f(1F,1F,1F,1F);

        if(!mirrorFontSyncReported)
        {
            mirrorFontSyncReported=true;
            System.out.println("[BBS Editor][Mirror][GUI Font] "+
                    "Restored GUI state after mirror: priorProgram="+
                    oldProgram+" priorBlend="+oldBlend+
                    " priorAlpha="+oldAlpha+" priorActiveTexture="+
                    oldActive+" (disable via "+
                    "-Dbbs.editor.camera.mirrorGuiFontState=false)");
        }
    }


}

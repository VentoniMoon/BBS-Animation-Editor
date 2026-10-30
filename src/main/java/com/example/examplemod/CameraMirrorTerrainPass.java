package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/**
 * Render the EXISTING WorldClient and the EXISTING RenderGlobal's already
 * compiled chunk buffers from a second view. No extra world, RenderGlobal,
 * chunk compilation, or EntityRenderer frame is created.
 *
 * Called after the normal world pass (RenderWorldLast) so that the normal
 * camera has populated the visible chunks. The reflection shares that
 * draw list: geometry outside the main camera's chunk visibility may be
 * missing, but the primary scene's culling state is never overwritten.
 * Recorded actors are drawn using prepared poses in a separate pass.
 */
public final class CameraMirrorTerrainPass
{
    private static boolean reported;
    private CameraMirrorTerrainPass(){}

    public static void render(Minecraft mc,PreviewCameraPose camera,
            float partialTicks)
    {
        if(mc==null||camera==null||mc.world==null||
                mc.renderGlobal==null||mc.getRenderViewEntity()==null)
            throw new IllegalStateException("Mirror terrain not ready");
        final RenderGlobal world=mc.renderGlobal;
        final Entity eye=mc.getRenderViewEntity();
        final int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final float aspect=(float)Math.max(1,mc.getFramebuffer().framebufferWidth)/
                Math.max(1,mc.getFramebuffer().framebufferHeight);
        try
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            // The sky has no physical mirror plane. Only clip terrain.
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            setCamera(camera,aspect,false);
            try
            {
                world.renderSky(partialTicks,0);
            }
            catch(Throwable skyProblem)
            {
                if(!reported)
                    System.err.println("[BBS Editor][Mirror] Sky skipped: "+
                            skyProblem);
            }

            // RenderGlobal.renderBlockLayer and its VBO render container
            // perform the -cameraXYZ translation themselves. Vanilla's
            // EntityRenderer.orientCamera leaves the modelview with camera
            // rotation only. Translating here double-offsets the chunks,
            // making the reflection appear displaced/partly empty.
            setCamera(camera,aspect,false);
            CameraSceneMirrorRenderer.installFrontClipPlane(camera);
            // IMPORTANT: do not call setupTerrain(), updateChunks() or
            // setWorldAndLoadRenderers() here. Those would mutate/replace
            // the native world frame's culling and render chunks. Reuse the
            // renderInfo list prepared by vanilla just before WorldLast.

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            RenderHelper.disableStandardItemLighting();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            world.renderBlockLayer(BlockRenderLayer.SOLID,partialTicks,0,eye);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            world.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED,
                    partialTicks,0,eye);
            world.renderBlockLayer(BlockRenderLayer.CUTOUT,partialTicks,0,eye);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            world.renderBlockLayer(BlockRenderLayer.TRANSLUCENT,
                    partialTicks,0,eye);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            if(!reported)
            {
                reported=true;
                System.out.println("[BBS Editor][Mirror] Shared RenderGlobal "+
                        "draw buffers active; no second world/chunk renderer.");
            }
        }
        finally
        {
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL11.glMatrixMode(mode);
        }
    }

    private static void setCamera(PreviewCameraPose pose,float aspect,
            boolean translate)
    {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(pose.fov,aspect,
                Math.max(0.02F,pose.nearPlane),
                Math.max(64F,pose.farPlane));
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glRotated(-pose.roll,0,0,1);
        GL11.glRotated(-pose.pitch,1,0,0);
        // Match PreviewCameraProjection: world-to-view rotates by
        // -yaw, -pitch, -roll (the order is right-to-left in OpenGL).
        GL11.glRotated(-pose.yaw,0,1,0);
        if(translate)
            GL11.glTranslated(-pose.x,-pose.y,-pose.z);
    }
}

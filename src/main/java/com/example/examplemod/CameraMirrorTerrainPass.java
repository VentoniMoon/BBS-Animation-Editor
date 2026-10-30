package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/**
 * Render only the isolated reflection world's sky and chunk layers.
 *
 * A second Minecraft.entityRenderer.renderWorld() is NOT reentrant: it
 * overwrites camera/global state belonging to the main frame. Rendering
 * chunk layers directly avoids restarting the game's world/actor pipeline.
 * Only native terrain is reflected at this stage, not recorded actors.
 */
public final class CameraMirrorTerrainPass
{
    private static int sequence;
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

            // Sky can replace projection and textures; reconstruct the exact
            // reflected camera for the world chunks and the culling frustum.
            setCamera(camera,aspect,true);
            CameraSceneMirrorRenderer.installFrontClipPlane(camera);
            Frustum frustum=new Frustum();
            frustum.setPosition(camera.x,camera.y,camera.z);
            world.setupTerrain(eye,partialTicks,frustum,++sequence,false);
            world.updateChunks(System.nanoTime()+3000000L);

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
                System.out.println("[BBS Editor][Mirror] Isolated terrain "+
                        "capture active: no second EntityRenderer.renderWorld.");
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

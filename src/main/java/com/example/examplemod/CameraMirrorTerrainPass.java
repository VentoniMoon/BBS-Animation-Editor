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
 * Render the EXISTING WorldClient and EXISTING RenderGlobal's compiled
 * chunk buffers from a second camera. No extra world or chunk compilation.
 * CameraMirrorChunkVisibility supplies a temporary reflected frustum list
 * and restores the main world's list and VBO origin on every return.
 * Recorded actors are drawn using prepared poses in a separate pass.
 */
public final class CameraMirrorTerrainPass
{
    private static boolean reported;
    private CameraMirrorTerrainPass(){}

    public static void render(Minecraft mc,PreviewCameraPose camera,
            PreviewCameraPose mainCamera,float partialTicks)
    {
        if(mc==null||camera==null||mainCamera==null||mc.world==null||
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
            // The normal BBS FS world pass uses the private native
            // EntityRenderer updateFogColor / setupFog lifecycle. Borrow
            // EXACTLY that same biome/weather/fog calculation for the mirror.
            // The scope restores cached fog colors after the pass.
            try(CameraMirrorNativeAtmosphere atmosphere=
                    CameraMirrorNativeAtmosphere.begin(mc,partialTicks))
            {
                if(atmosphere==null)
                    throw new IllegalStateException(
                            "Native Minecraft fog pipeline unavailable");
                // The sky has no physical mirror plane. Clip ONLY terrain.
                GL11.glDisable(GL11.GL_CLIP_PLANE0);
                setCamera(camera,aspect,false,2F);
                atmosphere.sky(partialTicks);
                try
                {
                    // pass=2 is vanilla's non-anaglyph world/sky pass.
                    world.renderSky(partialTicks,2);
                }
                catch(Throwable skyProblem)
                {
                    if(!reported)
                        System.err.println("[BBS Editor][Mirror] Sky skipped: "+
                                skyProblem);
                }

                // Vanilla renders clouds before terrain when camera eye
                // is below y=128, and afterwards when above it.
                if(camera.y<128D)renderClouds(mc,world,camera,aspect,
                        partialTicks,atmosphere);

                // RenderGlobal VBO container supplies -cameraXYZ. Reuse
                // existing chunk buffers, never another world renderer.
                setCamera(camera,aspect,false,1F);
                atmosphere.terrain(partialTicks);
                try(CameraMirrorChunkVisibility.Scope mirrorChunks=
                        CameraMirrorChunkVisibility.begin(mc,camera,mainCamera))
                {
                    if(mirrorChunks==null)
                        throw new IllegalStateException(
                                "Mirror RenderGlobal visibility unavailable");
                    CameraSceneMirrorRenderer.installFrontClipPlane(camera);
                    drawWorldLayers(mc,world,eye,partialTicks);
                }
                GL11.glDisable(GL11.GL_CLIP_PLANE0);
                if(camera.y>=128D)renderClouds(mc,world,camera,aspect,
                        partialTicks,atmosphere);
            }
            if(!reported)
            {
                reported=true;
                System.out.println("[BBS Editor][Mirror] Native fog and "+
                        "shared render chunks; no second world renderer.");
            }
        }
        finally
        {
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL11.glMatrixMode(mode);
        }
    }

    private static void renderClouds(Minecraft mc,RenderGlobal world,
            PreviewCameraPose camera,float aspect,float partialTicks,
            CameraMirrorNativeAtmosphere atmosphere)
    {
        if(mc.gameSettings.shouldRenderClouds()==0||
                !mc.world.provider.isSurfaceWorld())return;
        // Vanilla uses a farther projection for clouds. Keep the mirror's
        // camera rotation while supplying its actual world position, just
        // like EntityRenderer.renderCloudsCheck().
        setCamera(camera,aspect,false,4F);
        atmosphere.terrain(partialTicks);
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDepthMask(false);
        try
        {
            world.renderClouds(partialTicks,2,
                    camera.x,camera.y,camera.z);
        }
        finally
        {
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
        }
    }

    private static void drawWorldLayers(Minecraft mc,RenderGlobal world,
            Entity eye,float partialTicks)
    {
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
    }

    private static void setCamera(PreviewCameraPose pose,float aspect,
            boolean translate,float farMultiplier)
    {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GLU.gluPerspective(pose.fov,aspect,
                Math.max(0.02F,pose.nearPlane),
                Math.max(64F,pose.farPlane*farMultiplier));
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

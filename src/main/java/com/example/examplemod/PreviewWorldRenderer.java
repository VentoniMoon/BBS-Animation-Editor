package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.nio.IntBuffer;


/**
 * Отдельный renderer настоящего Minecraft World
 * для Preview framebuffer.
 *
 * Использует:
 *
 * - настоящий Minecraft RenderGlobal
 * - настоящий ViewFrustum
 * - настоящие RenderChunk
 * - настоящие Minecraft VBO
 * - отдельный PreviewSkyRenderer
 * - отдельный PreviewFogRenderer
 * - настоящий vanilla Cloud Renderer
 * - настоящий lightmap
 *
 * PreviewWorldRenderer не создаёт собственную
 * геометрию блоков.
 */
public class PreviewWorldRenderer
{
    private final Minecraft mc;

    private final VboRenderList renderList;

    private final PreviewSkyRenderer skyRenderer;

    private final PreviewFogRenderer fogRenderer;

    private Field viewFrustumField;

    private ViewFrustum viewFrustum;

    private Frustum frustum;

    private boolean initialized;

    private double cameraX;
    private double cameraY;
    private double cameraZ;


    /*
     * =========================================================
     * SPLIT RENDER STATE
     * =========================================================
     */

    private boolean splitRenderActive;

    private int savedMatrixMode;

    private int savedViewportX;
    private int savedViewportY;
    private int savedViewportWidth;
    private int savedViewportHeight;

    private float renderAspect;
    private float renderFov;
    private float renderNearPlane;
    private float renderTerrainFarPlane;


    public PreviewWorldRenderer(Minecraft mc)
    {
        this.mc = mc;

        this.renderList =
                new VboRenderList();

        this.skyRenderer =
                new PreviewSkyRenderer(
                        mc
                );

        this.fogRenderer =
                new PreviewFogRenderer(
                        mc
                );

        this.initializeReflection();
    }


    /*
     * =========================================================
     * REFLECTION
     * =========================================================
     */

    private void initializeReflection()
    {
        try
        {
            this.viewFrustumField =
                    RenderGlobal.class.getDeclaredField(
                            "viewFrustum"
                    );

            this.viewFrustumField.setAccessible(true);

            this.initialized = true;
        }
        catch (Exception e)
        {
            this.initialized = false;

            e.printStackTrace();
        }
    }


    /*
     * =========================================================
     * MAIN RENDER
     * =========================================================
     */

    public void render(
            EditorCamera camera,
            int width,
            int height)
    {
        if (!this.beginSplitRender(
                camera,
                width,
                height))
        {
            return;
        }

        this.renderOpaqueWorld();

        this.renderAfterActor();

        this.renderClouds();

        this.endSplitRender();
    }


    /*
     * =========================================================
     * BEGIN SPLIT RENDER
     * =========================================================
     */

    public boolean beginSplitRender(
            EditorCamera camera,
            int width,
            int height)
    {
        if (this.splitRenderActive)
        {
            return false;
        }

        if (camera == null)
        {
            return false;
        }

        if (width <= 0 ||
                height <= 0)
        {
            return false;
        }

        if (this.mc == null)
        {
            return false;
        }

        if (this.mc.world == null)
        {
            return false;
        }

        if (this.mc.renderGlobal == null)
        {
            return false;
        }

        if (!this.initialized)
        {
            return false;
        }


        /*
         * ---------------------------------------------------------
         * ViewFrustum
         * ---------------------------------------------------------
         */

        if (!this.getViewFrustum())
        {
            return false;
        }


        /*
         * ---------------------------------------------------------
         * Camera position
         * ---------------------------------------------------------
         */

        this.cameraX =
                camera.getCameraX();

        this.cameraY =
                camera.getCameraY();

        this.cameraZ =
                camera.getCameraZ();


        /*
         * ---------------------------------------------------------
         * Perspective
         * ---------------------------------------------------------
         */

        this.renderAspect =
                (float) width /
                        (float) height;

        this.renderFov =
                60.0F;

        this.renderNearPlane =
                0.05F;

        this.renderTerrainFarPlane =
                Math.max(
                        256.0F,
                        this.mc.gameSettings.renderDistanceChunks
                                * 16.0F
                );

        float skyFarPlane =
                this.renderTerrainFarPlane * 2.0F;


        /*
         * =========================================================
         * SAVE OPENGL STATE
         * =========================================================
         */

        this.savedMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        IntBuffer viewportBuffer =
                BufferUtils.createIntBuffer(
                        16
                );

        GL11.glGetInteger(
                GL11.GL_VIEWPORT,
                viewportBuffer
        );

        this.savedViewportX =
                viewportBuffer.get(0);

        this.savedViewportY =
                viewportBuffer.get(1);

        this.savedViewportWidth =
                viewportBuffer.get(2);

        this.savedViewportHeight =
                viewportBuffer.get(3);

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );


        /*
         * =========================================================
         * PROJECTION
         * =========================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        this.setupPerspective(
                this.renderAspect,
                this.renderFov,
                this.renderNearPlane,
                skyFarPlane
        );


        /*
         * =========================================================
         * MODELVIEW
         * =========================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();


        /*
         * =========================================================
         * CAMERA ROTATION
         * =========================================================
         */

        this.applyCameraRotation(
                camera
        );


        /*
         * =========================================================
         * SKY
         * =========================================================
         */

        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                skyFarPlane
        );

        this.skyRenderer.render(
                camera,
                this.mc.getRenderPartialTicks(),
                this.renderAspect,
                this.renderFov,
                this.renderNearPlane,
                skyFarPlane
        );


        /*
         * =========================================================
         * TERRAIN PROJECTION
         * =========================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glLoadIdentity();

        this.setupPerspective(
                this.renderAspect,
                this.renderFov,
                this.renderNearPlane,
                this.renderTerrainFarPlane
        );

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );


        /*
         * =========================================================
         * TERRAIN STATE
         * =========================================================
         */

        this.prepareTerrainState();


        /*
         * =========================================================
         * FOG FOR TERRAIN
         * =========================================================
         */

        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                this.renderTerrainFarPlane
        );


        /*
         * =========================================================
         * FRUSTUM
         * =========================================================
         */

        this.frustum =
                new Frustum();

        this.frustum.setPosition(
                this.cameraX,
                this.cameraY,
                this.cameraZ
        );


        /*
         * =========================================================
         * BLOCK ATLAS
         * =========================================================
         */

        this.bindBlockTexture();


        /*
         * =========================================================
         * LIGHTMAP
         * =========================================================
         */

        EntityRenderer entityRenderer =
                this.mc.entityRenderer;

        entityRenderer.enableLightmap();


        /*
         * =========================================================
         * ACTIVE
         * =========================================================
         */

        this.splitRenderActive = true;

        return true;
    }


    /*
     * =========================================================
     * OPAQUE WORLD
     * =========================================================
     */

    public void renderOpaqueWorld()
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        this.prepareTerrainState();

        this.bindBlockTexture();

        this.renderLayer(
                BlockRenderLayer.SOLID
        );

        this.bindBlockTexture();

        this.renderLayer(
                BlockRenderLayer.CUTOUT_MIPPED
        );

        this.bindBlockTexture();

        this.renderLayer(
                BlockRenderLayer.CUTOUT
        );
    }


    /*
     * =========================================================
     * AFTER ACTOR
     * =========================================================
     *
     * Actor уже отрисован.
     *
     * Здесь рисуется только TRANSLUCENT terrain.
     *
     * Clouds намеренно вынесены в отдельный метод
     * renderClouds().
     *
     * Это необходимо для порядка:
     *
     * Actor
     * TRANSLUCENT
     * Weather
     * Clouds
     */

    public void renderAfterActor()
    {
        if (!this.splitRenderActive)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * TERRAIN STATE
         * ---------------------------------------------------------
         */

        this.prepareTerrainState();

        EntityRenderer entityRenderer =
                this.mc.entityRenderer;

        entityRenderer.enableLightmap();


        /*
         * ---------------------------------------------------------
         * CRITICAL:
         * RESTORE BLOCK ATLAS AFTER ACTOR
         * ---------------------------------------------------------
         */

        this.bindBlockTexture();


        /*
         * ---------------------------------------------------------
         * TRANSLUCENT STATE
         * ---------------------------------------------------------
         */

        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

        GlStateManager.enableTexture2D();

        GlStateManager.enableAlpha();

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.1F
        );

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );

        /*
         * Прозрачный terrain не записывает
         * собственную глубину.
         */

        GlStateManager.depthMask(false);

        GlStateManager.enableCull();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );


        /*
         * ---------------------------------------------------------
         * FOG
         * ---------------------------------------------------------
         */

        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                this.renderTerrainFarPlane
        );


        /*
         * ---------------------------------------------------------
         * TRANSLUCENT TERRAIN
         * ---------------------------------------------------------
         */

        this.bindBlockTexture();

        this.renderLayer(
                BlockRenderLayer.TRANSLUCENT
        );


        /*
         * ---------------------------------------------------------
         * RESTORE TERRAIN STATE
         * ---------------------------------------------------------
         */

        this.prepareTerrainState();
    }


    /*
     * =========================================================
     * CLOUDS
     * =========================================================
     *
     * Отдельный этап рендера облаков.
     *
     * Вызывается ПОСЛЕ Weather.
     *
     * Здесь split-render НЕ завершается.
     */

    public void renderClouds()
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        this.renderVanillaClouds(
                this.renderAspect,
                this.renderFov,
                this.renderNearPlane,
                this.renderTerrainFarPlane
        );
    }


    /*
     * =========================================================
     * END SPLIT RENDER
     * =========================================================
     */

    public void endSplitRender()
    {
        if (!this.splitRenderActive)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * LIGHTMAP OFF
         * ---------------------------------------------------------
         */

        EntityRenderer entityRenderer =
                this.mc.entityRenderer;

        entityRenderer.disableLightmap();


        /*
         * ---------------------------------------------------------
         * ACTIVE TEXTURE
         * ---------------------------------------------------------
         */

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );


        /*
         * ---------------------------------------------------------
         * CLEANUP
         * ---------------------------------------------------------
         */

        this.frustum = null;


        /*
         * ---------------------------------------------------------
         * FOG OFF
         * ---------------------------------------------------------
         */

        this.fogRenderer.end();


        /*
         * ---------------------------------------------------------
         * RESTORE MODELVIEW
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();


        /*
         * ---------------------------------------------------------
         * RESTORE PROJECTION
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();


        /*
         * ---------------------------------------------------------
         * RESTORE VIEWPORT
         * ---------------------------------------------------------
         */

        GL11.glViewport(
                this.savedViewportX,
                this.savedViewportY,
                this.savedViewportWidth,
                this.savedViewportHeight
        );


        /*
         * ---------------------------------------------------------
         * RESTORE OPENGL ATTRIBUTES
         * ---------------------------------------------------------
         */

        GL11.glPopAttrib();


        /*
         * ---------------------------------------------------------
         * RESTORE MATRIX MODE
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                this.savedMatrixMode
        );


        this.splitRenderActive = false;
    }


    /*
     * =========================================================
     * VANILLA CLOUDS
     * =========================================================
     */

    private void renderVanillaClouds(
            float aspect,
            float fov,
            float nearPlane,
            float terrainFarPlane)
    {
        int cloudMode =
                this.mc.gameSettings.shouldRenderClouds();

        if (cloudMode == 0)
        {
            return;
        }


        float cloudFarPlane =
                terrainFarPlane * 4.0F;


        /*
         * =========================================================
         * PROJECTION
         * =========================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        this.setupPerspective(
                aspect,
                fov,
                nearPlane,
                cloudFarPlane
        );


        /*
         * =========================================================
         * MODELVIEW
         * =========================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();


        /*
         * ---------------------------------------------------------
         * FOG
         * ---------------------------------------------------------
         */

        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                terrainFarPlane
        );


        /*
         * ---------------------------------------------------------
         * CLOUD STATE
         * ---------------------------------------------------------
         */

        GlStateManager.enableFog();

        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

        GlStateManager.depthMask(true);

        GlStateManager.enableTexture2D();

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableCull();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );


        /*
         * ---------------------------------------------------------
         * CLOUDS
         * ---------------------------------------------------------
         */

        this.mc.renderGlobal.renderClouds(
                this.mc.getRenderPartialTicks(),
                2,
                this.cameraX,
                this.cameraY,
                this.cameraZ
        );


        /*
         * ---------------------------------------------------------
         * RESTORE MODELVIEW
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();


        /*
         * ---------------------------------------------------------
         * RESTORE PROJECTION
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();


        /*
         * ---------------------------------------------------------
         * RETURN TO MODELVIEW
         * ---------------------------------------------------------
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );


        /*
         * ---------------------------------------------------------
         * RESTORE TERRAIN STATE
         * ---------------------------------------------------------
         */

        this.prepareTerrainState();
    }


    /*
     * =========================================================
     * VIEW FRUSTUM
     * =========================================================
     */

    private boolean getViewFrustum()
    {
        try
        {
            this.viewFrustum =
                    (ViewFrustum)
                            this.viewFrustumField.get(
                                    this.mc.renderGlobal
                            );

            return this.viewFrustum != null;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.viewFrustum = null;

            return false;
        }
    }


    /*
     * =========================================================
     * PERSPECTIVE
     * =========================================================
     */

    private void setupPerspective(
            float aspect,
            float fov,
            float nearPlane,
            float farPlane)
    {
        float halfFov =
                fov * 0.5F;

        float top =
                (float) Math.tan(
                        Math.toRadians(halfFov)
                ) * nearPlane;

        float bottom =
                -top;

        float left =
                bottom * aspect;

        float right =
                top * aspect;

        GL11.glFrustum(
                left,
                right,
                bottom,
                top,
                nearPlane,
                farPlane
        );
    }


    /*
     * =========================================================
     * TERRAIN STATE
     * =========================================================
     */

    private void prepareTerrainState()
    {
        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

        GlStateManager.depthMask(true);

        GlStateManager.enableTexture2D();

        GlStateManager.enableCull();

        GlStateManager.enableAlpha();

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.1F
        );

        GlStateManager.disableBlend();

        GlStateManager.disableLighting();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );
    }


    /*
     * =========================================================
     * BLOCK TEXTURE
     * =========================================================
     */

    private void bindBlockTexture()
    {
        if (this.mc == null)
        {
            return;
        }

        if (this.mc.getTextureManager() == null)
        {
            return;
        }

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        this.mc.getTextureManager().bindTexture(
                TextureMap.LOCATION_BLOCKS_TEXTURE
        );
    }


    /*
     * =========================================================
     * BLOCK LAYER
     * =========================================================
     */

    private void renderLayer(
            BlockRenderLayer layer)
    {
        if (this.viewFrustum == null)
        {
            return;
        }

        if (this.frustum == null)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * CRITICAL:
         * Terrain должен всегда использовать block atlas.
         * ---------------------------------------------------------
         */

        this.bindBlockTexture();


        boolean translucent =
                layer ==
                        BlockRenderLayer.TRANSLUCENT;


        /*
         * ---------------------------------------------------------
         * LAYER STATE
         * ---------------------------------------------------------
         */

        if (translucent)
        {
            GlStateManager.enableBlend();

            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
            );

            GlStateManager.depthMask(false);
        }
        else
        {
            GlStateManager.disableBlend();

            GlStateManager.depthMask(true);
        }


        /*
         * ---------------------------------------------------------
         * VBO CAMERA POSITION
         * ---------------------------------------------------------
         */

        this.renderList.initialize(
                this.cameraX,
                this.cameraY,
                this.cameraZ
        );


        /*
         * ---------------------------------------------------------
         * COLLECT VISIBLE CHUNKS
         * ---------------------------------------------------------
         */

        for (
                RenderChunk renderChunk :
                this.viewFrustum.renderChunks
        )
        {
            if (renderChunk == null)
            {
                continue;
            }


            BlockPos position =
                    renderChunk.getPosition();

            if (position == null)
            {
                continue;
            }


            AxisAlignedBB boundingBox =
                    renderChunk.boundingBox;

            if (boundingBox == null)
            {
                continue;
            }


            if (!this.frustum.isBoundingBoxInFrustum(
                    boundingBox))
            {
                continue;
            }


            CompiledChunk compiledChunk =
                    renderChunk.getCompiledChunk();

            if (compiledChunk == null)
            {
                continue;
            }


            if (compiledChunk ==
                    CompiledChunk.DUMMY)
            {
                continue;
            }


            if (compiledChunk.isLayerEmpty(
                    layer))
            {
                continue;
            }


            if (renderChunk.getVertexBufferByLayer(
                    layer.ordinal()) == null)
            {
                continue;
            }


            this.renderList.addRenderChunk(
                    renderChunk,
                    layer
            );
        }


        /*
         * ---------------------------------------------------------
         * DRAW VBOs
         * ---------------------------------------------------------
         */

        this.renderPreparedChunks(
                layer
        );


        /*
         * ---------------------------------------------------------
         * RESTORE TRANSLUCENT STATE
         * ---------------------------------------------------------
         */

        if (translucent)
        {
            GlStateManager.depthMask(true);

            GlStateManager.disableBlend();
        }
    }


    /*
     * =========================================================
     * VBO
     * =========================================================
     */

    private void renderPreparedChunks(
            BlockRenderLayer layer)
    {
        if (!OpenGlHelper.useVbo())
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * VERTEX ARRAY
         * ---------------------------------------------------------
         */

        GlStateManager.glEnableClientState(
                GL11.GL_VERTEX_ARRAY
        );


        /*
         * ---------------------------------------------------------
         * TEXTURE COORDINATES
         * ---------------------------------------------------------
         */

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );


        /*
         * ---------------------------------------------------------
         * LIGHTMAP COORDINATES
         * ---------------------------------------------------------
         */

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.lightmapTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );


        /*
         * ---------------------------------------------------------
         * COLOR ARRAY
         * ---------------------------------------------------------
         */

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_COLOR_ARRAY
        );


        /*
         * ---------------------------------------------------------
         * ACTUAL MINECRAFT VBO RENDERING
         * ---------------------------------------------------------
         */

        this.renderList.renderChunkLayer(
                layer
        );


        /*
         * ---------------------------------------------------------
         * CLEANUP
         * ---------------------------------------------------------
         */

        GlStateManager.glDisableClientState(
                GL11.GL_VERTEX_ARRAY
        );


        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glDisableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );


        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.lightmapTexUnit
        );

        GlStateManager.glDisableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );


        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glDisableClientState(
                GL11.GL_COLOR_ARRAY
        );


        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.resetColor();
    }


    /*
     * =========================================================
     * CAMERA
     * =========================================================
     */

    private void applyCameraRotation(
            EditorCamera camera)
    {
        GlStateManager.rotate(
                -camera.getPitch(),
                1.0F,
                0.0F,
                0.0F
        );

        GlStateManager.rotate(
                -camera.getYaw(),
                0.0F,
                1.0F,
                0.0F
        );
    }


    /*
     * =========================================================
     * ACCESS
     * =========================================================
     */

    public double getCameraX()
    {
        return this.cameraX;
    }


    public double getCameraY()
    {
        return this.cameraY;
    }


    public double getCameraZ()
    {
        return this.cameraZ;
    }


    public PreviewSkyRenderer getSkyRenderer()
    {
        return this.skyRenderer;
    }


    public PreviewFogRenderer getFogRenderer()
    {
        return this.fogRenderer;
    }
}
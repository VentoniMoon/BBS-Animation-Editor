package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;


/**
 * Рендер настоящего Minecraft World внутри Preview.
 *
 * =========================================================
 * ОСНОВНАЯ АРХИТЕКТУРА
 * =========================================================
 *
 * Preview имеет собственный render pipeline.
 *
 * OptiFine:
 *
 *     Shaders.beginRender()
 *             ↓
 *     Preview Sky / Shader Sky
 *             ↓
 *     Minecraft terrain
 *             ↓
 *     Blockbuster actor
 *             ↓
 *     другие элементы Preview
 *             ↓
 *     Shaders.endRender()
 *
 *
 * SKY:
 *
 * Без shaderpack:
 *
 *     PreviewSkyRenderer
 *
 * С shaderpack:
 *
 *     RenderGlobal.renderSky()
 *             ↓
 *     Shaders.beginSky()
 *             ↓
 *     shaderpack sky
 *             ↓
 *     Shaders.endSky()
 *
 *
 * ВАЖНО:
 *
 * Наличие OptiFine само по себе НЕ означает,
 * что Preview должен обращаться к OptiFine shader API.
 *
 * Все OptiFine hooks выполняются только если
 * Config.isShaders() == true.
 */
public class PreviewWorldRenderer
{
    private final Minecraft mc;

    private final VboRenderList renderList;

    /*
     * =========================================================
     * CUSTOM PREVIEW SKY
     * =========================================================
     *
     * Используется только когда shaderpack НЕ активен.
     */
    private final PreviewSkyRenderer skyRenderer;

    private final PreviewFogRenderer fogRenderer;


    /*
     * =========================================================
     * MINECRAFT VIEW FRUSTUM
     * =========================================================
     */

    private Field viewFrustumField;

    private ViewFrustum viewFrustum;

    private Frustum frustum;

    private boolean initialized;


    /*
     * =========================================================
     * CAMERA
     * =========================================================
     */

    private double cameraX;

    private double cameraY;

    private double cameraZ;


    /*
     * =========================================================
     * SPLIT RENDER
     * =========================================================
     */

    private boolean splitRenderActive;

    private boolean cameraPrepared;

    private int savedMatrixMode;

    private int savedViewportX;

    private int savedViewportY;

    private int savedViewportWidth;

    private int savedViewportHeight;

    private float renderAspect;

    private float renderFov;

    private float renderNearPlane;

    private float renderTerrainFarPlane;


    /*
     * =========================================================
     * OPTIFINE
     * =========================================================
     */

    private boolean optiFineChecked;

    private boolean optiFinePresent;

    /*
     * Config.isShaders()
     */
    private Method optiFineIsShadersMethod;

    /*
     * Shaders.setCamera()
     */
    private Method optiFineSetCameraMethod;

    private Field optiFineCameraPositionXField;

    private Field optiFineCameraPositionYField;

    private Field optiFineCameraPositionZField;


    /*
     * =========================================================
     * OPTIFINE SKY
     * =========================================================
     */

    private Method optiFineBeginSkyMethod;

    private Method optiFineEndSkyMethod;


    /*
     * =========================================================
     * OPTIFINE ENTITIES
     * =========================================================
     */

    private Method optiFineBeginEntitiesMethod;

    private Method optiFineEndEntitiesMethod;

    private Method optiFineNextEntityMethod;

    private boolean optiFineEntitiesActive;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

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

        this.optiFineEntitiesActive = false;

        this.cameraPrepared = false;

        this.splitRenderActive = false;

        this.initializeReflection();

        this.initializeOptiFineReflection();
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

            this.viewFrustumField.setAccessible(
                    true
            );

            this.initialized = true;
        }
        catch (Throwable e)
        {
            this.initialized = false;

            System.out.println(
                    "[BBS Animation Editor] Failed to access Minecraft ViewFrustum."
            );

            e.printStackTrace();
        }
    }


    /*
     * =========================================================
     * OPTIFINE REFLECTION
     * =========================================================
     */

    private void initializeOptiFineReflection()
    {
        if (this.optiFineChecked)
        {
            return;
        }

        this.optiFineChecked = true;

        this.optiFinePresent = false;


        try
        {
            Class<?> shadersClass =
                    Class.forName(
                            "net.optifine.shaders.Shaders"
                    );

            Class<?> configClass =
                    Class.forName(
                            "net.optifine.Config"
                    );


            /*
             * =====================================================
             * CONFIG.isShaders()
             * =====================================================
             *
             * Это проверка именно активного shaderpack.
             */

            try
            {
                this.optiFineIsShadersMethod =
                        configClass.getDeclaredMethod(
                                "isShaders"
                        );

                this.optiFineIsShadersMethod.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineIsShadersMethod =
                        null;
            }


            /*
             * =====================================================
             * CAMERA
             * =====================================================
             */

            try
            {
                this.optiFineSetCameraMethod =
                        shadersClass.getDeclaredMethod(
                                "setCamera",
                                float.class
                        );

                this.optiFineSetCameraMethod.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineSetCameraMethod =
                        null;
            }


            /*
             * =====================================================
             * CAMERA POSITION
             * =====================================================
             */

            try
            {
                this.optiFineCameraPositionXField =
                        shadersClass.getDeclaredField(
                                "cameraPositionX"
                        );

                this.optiFineCameraPositionXField.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineCameraPositionXField =
                        null;
            }


            try
            {
                this.optiFineCameraPositionYField =
                        shadersClass.getDeclaredField(
                                "cameraPositionY"
                        );

                this.optiFineCameraPositionYField.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineCameraPositionYField =
                        null;
            }


            try
            {
                this.optiFineCameraPositionZField =
                        shadersClass.getDeclaredField(
                                "cameraPositionZ"
                        );

                this.optiFineCameraPositionZField.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineCameraPositionZField =
                        null;
            }


            /*
             * =====================================================
             * SKY
             * =====================================================
             */

            try
            {
                this.optiFineBeginSkyMethod =
                        shadersClass.getDeclaredMethod(
                                "beginSky"
                        );

                this.optiFineBeginSkyMethod.setAccessible(
                        true
                );

                this.optiFineEndSkyMethod =
                        shadersClass.getDeclaredMethod(
                                "endSky"
                        );

                this.optiFineEndSkyMethod.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineBeginSkyMethod =
                        null;

                this.optiFineEndSkyMethod =
                        null;
            }


            /*
             * =====================================================
             * ENTITIES
             * =====================================================
             */

            try
            {
                this.optiFineBeginEntitiesMethod =
                        shadersClass.getDeclaredMethod(
                                "beginEntities"
                        );

                this.optiFineBeginEntitiesMethod.setAccessible(
                        true
                );

                this.optiFineEndEntitiesMethod =
                        shadersClass.getDeclaredMethod(
                                "endEntities"
                        );

                this.optiFineEndEntitiesMethod.setAccessible(
                        true
                );
            }
            catch (Throwable ignored)
            {
                this.optiFineBeginEntitiesMethod =
                        null;

                this.optiFineEndEntitiesMethod =
                        null;
            }


            /*
             * =====================================================
             * nextEntity(Entity)
             * =====================================================
             */

            for (
                    Method method :
                    shadersClass.getDeclaredMethods())
            {
                if (!method.getName().equals(
                        "nextEntity"))
                {
                    continue;
                }

                if (method.getParameterTypes().length != 1)
                {
                    continue;
                }

                this.optiFineNextEntityMethod =
                        method;

                this.optiFineNextEntityMethod.setAccessible(
                        true
                );

                break;
            }


            this.optiFinePresent = true;
        }
        catch (Throwable e)
        {
            this.optiFinePresent = false;

            this.optiFineIsShadersMethod = null;

            this.optiFineSetCameraMethod = null;

            this.optiFineCameraPositionXField = null;

            this.optiFineCameraPositionYField = null;

            this.optiFineCameraPositionZField = null;

            this.optiFineBeginSkyMethod = null;

            this.optiFineEndSkyMethod = null;

            this.optiFineBeginEntitiesMethod = null;

            this.optiFineEndEntitiesMethod = null;

            this.optiFineNextEntityMethod = null;
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

        try
        {
            this.renderOpaqueWorld();

            this.renderAfterActor();

            this.renderClouds();
        }
        finally
        {
            this.endSplitRender();
        }
    }


    /*
     * =========================================================
     * PREPARE SHADER CAMERA
     * =========================================================
     */

    public boolean prepareShaderCamera(
            EditorCamera camera,
            int width,
            int height)
    {
        if (this.splitRenderActive)
        {
            return false;
        }

        if (this.cameraPrepared)
        {
            return true;
        }

        if (camera == null)
        {
            return false;
        }

        if (width <= 0 || height <= 0)
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

        if (!this.getViewFrustum())
        {
            return false;
        }


        this.cameraX =
                camera.getCameraX();

        this.cameraY =
                camera.getCameraY();

        this.cameraZ =
                camera.getCameraZ();


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
                        this.mc.gameSettings
                                .renderDistanceChunks
                                * 16.0F
                );


        this.saveOpenGLState();

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );


        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

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

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        this.applyCameraRotation(
                camera
        );


        /*
         * ВАЖНО:
         *
         * OptiFine здесь будет затронут только если
         * shaderpack действительно активен.
         */
        this.updateOptiFineCamera();

        this.cameraPrepared = true;

        return true;
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

        if (width <= 0 || height <= 0)
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

        if (!this.getViewFrustum())
        {
            return false;
        }


        boolean prepared =
                this.cameraPrepared;


        if (!prepared)
        {
            this.cameraX =
                    camera.getCameraX();

            this.cameraY =
                    camera.getCameraY();

            this.cameraZ =
                    camera.getCameraZ();


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
                            this.mc.gameSettings
                                    .renderDistanceChunks
                                    * 16.0F
                    );


            this.saveOpenGLState();

            GL11.glPushAttrib(
                    GL11.GL_ALL_ATTRIB_BITS
            );


            /*
             * PROJECTION
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
                    this.renderTerrainFarPlane
            );


            /*
             * MODELVIEW
             */

            GL11.glMatrixMode(
                    GL11.GL_MODELVIEW
            );

            GL11.glPushMatrix();

            GL11.glLoadIdentity();

            this.applyCameraRotation(
                    camera
            );

            this.updateOptiFineCamera();
        }


        /*
         * =========================================================
         * SKY
         * =========================================================
         */

        if (this.isShaderPackActive())
        {
            this.renderShaderSky();
        }
        else
        {
            this.renderPreviewSky(
                    camera,
                    width,
                    height
            );
        }


        /*
         * =========================================================
         * TERRAIN STATE
         * =========================================================
         */

        this.prepareTerrainState();


        /*
         * =========================================================
         * FOG
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

        this.mc.entityRenderer.enableLightmap();


        /*
         * =========================================================
         * ACTIVE
         * =========================================================
         */

        this.splitRenderActive = true;

        this.cameraPrepared = false;

        return true;
    }


    /*
     * =========================================================
     * DETECT ACTIVE SHADERPACK
     * =========================================================
     */

    private boolean isShaderPackActive()
    {
        if (!this.optiFinePresent)
        {
            return false;
        }

        if (this.optiFineIsShadersMethod == null)
        {
            return false;
        }

        try
        {
            Object result =
                    this.optiFineIsShadersMethod.invoke(
                            null
                    );

            return result instanceof Boolean &&
                    ((Boolean) result).booleanValue();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }


    /*
     * =========================================================
     * SHADER SKY
     * =========================================================
     */

    private void renderShaderSky()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.mc == null)
        {
            return;
        }

        if (this.mc.world == null)
        {
            return;
        }

        if (this.mc.renderGlobal == null)
        {
            return;
        }


        float partialTicks =
                this.mc.getRenderPartialTicks();


        this.updateOptiFineCamera();


        this.beginOptiFineSky();

        try
        {
            this.mc.renderGlobal.renderSky(
                    partialTicks,
                    2
            );
        }
        finally
        {
            this.endOptiFineSky();
        }


        this.updateOptiFineCamera();
    }


    /*
     * =========================================================
     * CUSTOM PREVIEW SKY
     * =========================================================
     */

    private void renderPreviewSky(
            EditorCamera camera,
            int width,
            int height)
    {
        if (this.mc == null)
        {
            return;
        }

        if (this.mc.world == null)
        {
            return;
        }

        if (camera == null)
        {
            return;
        }


        float partialTicks =
                this.mc.getRenderPartialTicks();


        float aspect =
                height > 0
                        ? (float) width /
                        (float) height
                        : 1.0F;


        /*
         * ВАЖНО:
         *
         * Здесь updateOptiFineCamera() больше ничего
         * не делает, если shaderpack выключен.
         */
        this.updateOptiFineCamera();


        this.skyRenderer.render(
                camera,
                partialTicks,
                aspect,
                this.renderFov,
                this.renderNearPlane,
                this.renderTerrainFarPlane
        );


        this.updateOptiFineCamera();
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
     * ACTOR SHADER PASS
     * =========================================================
     */

    public void beginActorRender()
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        if (this.optiFineEntitiesActive)
        {
            this.endActorRender();
        }


        /*
         * Если shaderpack выключен, OptiFine здесь вообще
         * не должен участвовать.
         */
        if (!this.isShaderPackActive())
        {
            this.optiFineEntitiesActive = false;

            return;
        }


        this.updateOptiFineCamera();

        this.beginOptiFineEntities();


        if (this.optiFineBeginEntitiesMethod != null)
        {
            this.optiFineEntitiesActive = true;
        }
    }


    public void endActorRender()
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        if (!this.optiFineEntitiesActive)
        {
            return;
        }

        try
        {
            this.endOptiFineEntities();
        }
        finally
        {
            this.optiFineEntitiesActive = false;

            this.updateOptiFineCamera();
        }
    }


    /*
     * =========================================================
     * ACTOR ENTITY HOOK
     * =========================================================
     */

    public void nextActor(Entity entity)
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        if (entity == null)
        {
            return;
        }

        /*
         * Самая важная проверка:
         *
         * OptiFine nextEntity() вызывается только
         * при реально активном shaderpack.
         */
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.optiFineNextEntityMethod == null)
        {
            return;
        }

        try
        {
            this.optiFineNextEntityMethod.invoke(
                    null,
                    entity
            );
        }
        catch (Throwable ignored)
        {
        }
    }


    /*
     * =========================================================
     * AFTER ACTOR
     * =========================================================
     */

    public void renderAfterActor()
    {
        if (!this.splitRenderActive)
        {
            return;
        }

        if (this.optiFineEntitiesActive)
        {
            this.endActorRender();
        }

        this.prepareTerrainState();

        this.mc.entityRenderer.enableLightmap();

        this.bindBlockTexture();


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
                GlStateManager.DestFactor.ZERO
        );

        GlStateManager.depthMask(false);

        GlStateManager.enableCull();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );


        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                this.renderTerrainFarPlane
        );


        this.bindBlockTexture();

        this.renderLayer(
                BlockRenderLayer.TRANSLUCENT
        );


        this.prepareTerrainState();

        this.updateOptiFineCamera();
    }


    /*
     * =========================================================
     * CLOUDS
     * =========================================================
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
            if (this.cameraPrepared)
            {
                try
                {
                    GL11.glMatrixMode(
                            GL11.GL_MODELVIEW
                    );

                    GL11.glPopMatrix();

                    GL11.glMatrixMode(
                            GL11.GL_PROJECTION
                    );

                    GL11.glPopMatrix();

                    GL11.glPopAttrib();

                    GL11.glMatrixMode(
                            this.savedMatrixMode
                    );
                }
                catch (Throwable ignored)
                {
                }

                this.cameraPrepared = false;
            }

            this.optiFineEntitiesActive = false;

            return;
        }


        try
        {
            /*
             * Если Actor pass ещё открыт,
             * обязательно закрываем его.
             */
            if (this.optiFineEntitiesActive)
            {
                this.endActorRender();
            }


            /*
             * Minecraft lightmap.
             */
            this.mc.entityRenderer.disableLightmap();


            /*
             * Возвращаем стандартный texture unit.
             */
            OpenGlHelper.setActiveTexture(
                    OpenGlHelper.defaultTexUnit
            );


            this.frustum = null;


            /*
             * Preview fog.
             */
            this.fogRenderer.end();


            /*
             * =====================================================
             * MODELVIEW
             * =====================================================
             */

            GL11.glMatrixMode(
                    GL11.GL_MODELVIEW
            );

            GL11.glPopMatrix();


            /*
             * =====================================================
             * PROJECTION
             * =====================================================
             */

            GL11.glMatrixMode(
                    GL11.GL_PROJECTION
            );

            GL11.glPopMatrix();


            /*
             * =====================================================
             * VIEWPORT
             * =====================================================
             */

            GL11.glViewport(
                    this.savedViewportX,
                    this.savedViewportY,
                    this.savedViewportWidth,
                    this.savedViewportHeight
            );


            /*
             * =====================================================
             * ATTRIBUTES
             * =====================================================
             */

            GL11.glPopAttrib();


            /*
             * =====================================================
             * MATRIX MODE
             * =====================================================
             */

            GL11.glMatrixMode(
                    this.savedMatrixMode
            );
        }
        finally
        {
            /*
             * OptiFine больше не должен считаться активным
             * внутри Preview.
             */
            this.optiFineEntitiesActive = false;

            this.frustum = null;

            this.splitRenderActive = false;

            this.cameraPrepared = false;
        }
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
         * PROJECTION
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
         * MODELVIEW
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();


        /*
         * FOG
         */

        this.fogRenderer.begin(
                this.mc.getRenderPartialTicks(),
                terrainFarPlane
        );


        /*
         * CLOUD STATE
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
         * OPTIFINE CAMERA
         *
         * Ничего не делает при выключенном shaderpack.
         */

        this.updateOptiFineCamera();


        /*
         * =====================================================
         * VANILLA CLOUD PASS
         * =====================================================
         *
         * beginSky/endSky являются OptiFine hooks.
         *
         * При отключенном shaderpack они НЕ вызываются.
         */

        if (this.isShaderPackActive())
        {
            this.beginOptiFineSky();

            try
            {
                this.mc.renderGlobal.renderClouds(
                        this.mc.getRenderPartialTicks(),
                        2,
                        this.cameraX,
                        this.cameraY,
                        this.cameraZ
                );
            }
            finally
            {
                this.endOptiFineSky();
            }
        }
        else
        {
            this.mc.renderGlobal.renderClouds(
                    this.mc.getRenderPartialTicks(),
                    2,
                    this.cameraX,
                    this.cameraY,
                    this.cameraZ
            );
        }


        /*
         * RESTORE MATRICES
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();


        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();


        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );


        this.prepareTerrainState();

        this.updateOptiFineCamera();
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
        catch (Throwable e)
        {
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
                (float)
                        Math.tan(
                                Math.toRadians(
                                        halfFov
                                )
                        )
                        * nearPlane;

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

        this.bindBlockTexture();

        boolean translucent =
                layer == BlockRenderLayer.TRANSLUCENT;


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


        this.renderList.initialize(
                this.cameraX,
                this.cameraY,
                this.cameraZ
        );


        for (
                RenderChunk renderChunk :
                this.viewFrustum.renderChunks)
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

            if (compiledChunk == CompiledChunk.DUMMY)
            {
                continue;
            }

            if (compiledChunk.isLayerEmpty(layer))
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


        this.renderPreparedChunks(
                layer
        );


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

        GlStateManager.glEnableClientState(
                GL11.GL_VERTEX_ARRAY
        );

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.lightmapTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_TEXTURE_COORD_ARRAY
        );

        OpenGlHelper.setClientActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.glEnableClientState(
                GL11.GL_COLOR_ARRAY
        );


        this.renderList.renderChunkLayer(
                layer
        );


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

        OpenGlHelper.setActiveTexture(
                OpenGlHelper.defaultTexUnit
        );

        GlStateManager.resetColor();
    }


    /*
     * =========================================================
     * CAMERA ROTATION
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
     * OPTIFINE CAMERA
     * =========================================================
     *
     * КРИТИЧЕСКОЕ ИЗМЕНЕНИЕ:
     *
     * OptiFine camera state изменяется ТОЛЬКО при
     * реально активном shaderpack.
     *
     * Если OptiFine установлен, но shaders выключены,
     * этот метод полностью ничего не делает.
     */

    private void updateOptiFineCamera()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }


        if (this.mc == null)
        {
            return;
        }


        if (this.optiFineSetCameraMethod != null)
        {
            try
            {
                this.optiFineSetCameraMethod.invoke(
                        null,
                        Float.valueOf(
                                this.mc.getRenderPartialTicks()
                        )
                );
            }
            catch (Throwable ignored)
            {
            }
        }


        try
        {
            if (this.optiFineCameraPositionXField != null)
            {
                this.optiFineCameraPositionXField.setDouble(
                        null,
                        this.cameraX
                );
            }

            if (this.optiFineCameraPositionYField != null)
            {
                this.optiFineCameraPositionYField.setDouble(
                        null,
                        this.cameraY
                );
            }

            if (this.optiFineCameraPositionZField != null)
            {
                this.optiFineCameraPositionZField.setDouble(
                        null,
                        this.cameraZ
                );
            }
        }
        catch (Throwable ignored)
        {
        }
    }


    /*
     * =========================================================
     * OPTIFINE SKY
     * =========================================================
     */

    private void beginOptiFineSky()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.optiFineBeginSkyMethod == null)
        {
            return;
        }

        try
        {
            this.optiFineBeginSkyMethod.invoke(
                    null
            );
        }
        catch (Throwable ignored)
        {
        }
    }


    private void endOptiFineSky()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.optiFineEndSkyMethod == null)
        {
            return;
        }

        try
        {
            this.optiFineEndSkyMethod.invoke(
                    null
            );
        }
        catch (Throwable ignored)
        {
        }
    }


    /*
     * =========================================================
     * OPTIFINE ENTITIES
     * =========================================================
     */

    private void beginOptiFineEntities()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.optiFineBeginEntitiesMethod == null)
        {
            return;
        }

        try
        {
            this.optiFineBeginEntitiesMethod.invoke(
                    null
            );
        }
        catch (Throwable ignored)
        {
        }
    }


    private void endOptiFineEntities()
    {
        if (!this.isShaderPackActive())
        {
            return;
        }

        if (this.optiFineEndEntitiesMethod == null)
        {
            return;
        }

        try
        {
            this.optiFineEndEntitiesMethod.invoke(
                    null
            );
        }
        catch (Throwable ignored)
        {
        }
    }


    /*
     * =========================================================
     * OPENGL STATE
     * =========================================================
     */

    private void saveOpenGLState()
    {
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


    public boolean isOptiFinePresent()
    {
        return this.optiFinePresent;
    }


    public boolean isSplitRenderActive()
    {
        return this.splitRenderActive;
    }
}
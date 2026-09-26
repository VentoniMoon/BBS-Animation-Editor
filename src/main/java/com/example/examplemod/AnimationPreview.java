package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import org.lwjgl.opengl.GL11;

public class AnimationPreview
{
    private int x;
    private int y;
    private int width;
    private int height;

    /*
     * =========================================================
     * PREVIEW SCENE POSITION
     * =========================================================
     */

    private double referenceX;
    private double referenceY;
    private double referenceZ;

    private double worldX;
    private double worldY;
    private double worldZ;

    /*
     * =========================================================
     * RENDERERS
     * =========================================================
     */

    private PreviewShaderBridge shaderBridge;

    private PreviewWorldRenderer worldRenderer;

    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public AnimationPreview()
    {
        this.x = 0;
        this.y = 0;
        this.width = 300;
        this.height = 300;

        this.referenceX = 0.0D;
        this.referenceY = 0.0D;
        this.referenceZ = 0.0D;

        this.worldX = 0.0D;
        this.worldY = 0.0D;
        this.worldZ = 0.0D;

        this.shaderBridge = null;

        this.worldRenderer = null;
    }

    /*
     * =========================================================
     * BOUNDS
     * =========================================================
     */

    public void setBounds(
            int x,
            int y,
            int width,
            int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /*
     * =========================================================
     * REFERENCE POSITION
     * =========================================================
     */

    public void setReferencePosition(
            double x,
            double y,
            double z)
    {
        this.referenceX = x;
        this.referenceY = y;
        this.referenceZ = z;

        this.worldX = 0.0D;
        this.worldY = 0.0D;
        this.worldZ = 0.0D;
    }

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            Minecraft mc,
            ActorPose actorPose,
            List<AnimationBone> bones,
            int currentFrame,
            EditorCamera camera)
    {
        if (mc == null)
        {
            return;
        }

        if (camera == null)
        {
            return;
        }

        if (this.width <= 0
                || this.height <= 0)
        {
            return;
        }

        /*
         * -----------------------------------------------------
         * INITIALIZE RENDERERS
         * -----------------------------------------------------
         */

        this.ensureRenderers(mc);

        if (this.shaderBridge == null)
        {
            return;
        }

        if (this.worldRenderer == null)
        {
            return;
        }

        /*
         * -----------------------------------------------------
         * PREVIEW BOUNDS
         * -----------------------------------------------------
         */

        this.shaderBridge.setPreviewBounds(
                this.x,
                this.y,
                this.width,
                this.height
        );

        /*
         * -----------------------------------------------------
         * BEGIN PREVIEW
         * -----------------------------------------------------
         */

        if (!this.shaderBridge.beginPreview())
        {
            return;
        }

        boolean optiFineRenderStarted = false;

        boolean worldRenderStarted = false;

        try
        {
            /*
             * =================================================
             * OPTIFINE SHADER PIPELINE
             * =================================================
             *
             * Если shader pack активен, временно запускаем
             * настоящий OptiFine render lifecycle.
             */

            if (this.shaderBridge.shouldUseShaderPipeline())
            {
                long finishTimeNano =
                        System.nanoTime()
                                + 100000000L;

                optiFineRenderStarted =
                        this.shaderBridge.beginOptiFineRender(
                                mc.getRenderPartialTicks(),
                                finishTimeNano
                        );
            }

            /*
             * =================================================
             * MINECRAFT WORLD
             * =================================================
             */

            worldRenderStarted =
                    this.worldRenderer.beginSplitRender(
                            camera,
                            this.width,
                            this.height
                    );

            if (worldRenderStarted)
            {
                /*
                 * -------------------------------------------------
                 * OPAQUE WORLD
                 * -------------------------------------------------
                 */

                this.worldRenderer.renderOpaqueWorld();

                /*
                 * -------------------------------------------------
                 * AFTER ACTOR
                 * -------------------------------------------------
                 *
                 * Пока actor ещё не встроен в этот этап.
                 * Метод оставляет правильное место для:
                 *
                 *     Actor
                 *       ↓
                 *     translucent terrain
                 */

                this.worldRenderer.renderAfterActor();

                /*
                 * -------------------------------------------------
                 * CLOUDS
                 * -------------------------------------------------
                 */

                this.worldRenderer.renderClouds();
            }
        }
        finally
        {
            /*
             * =================================================
             * END WORLD RENDER
             * =================================================
             */

            if (worldRenderStarted)
            {
                this.worldRenderer.endSplitRender();
            }

            /*
             * =================================================
             * END OPTIFINE
             * =================================================
             *
             * Здесь OptiFine выполняет свой оставшийся
             * deferred/composite/final lifecycle.
             */

            if (optiFineRenderStarted)
            {
                this.shaderBridge.endOptiFineRender();
            }

            /*
             * =================================================
             * END PREVIEW
             * =================================================
             */

            this.shaderBridge.endPreview();
        }
    }

    /*
     * =========================================================
     * RENDERER INITIALIZATION
     * =========================================================
     */

    private void ensureRenderers(
            Minecraft mc)
    {
        if (this.shaderBridge == null)
        {
            this.shaderBridge =
                    new PreviewShaderBridge(mc);
        }

        if (this.worldRenderer == null)
        {
            this.worldRenderer =
                    new PreviewWorldRenderer(mc);
        }
    }

    /*
     * =========================================================
     * WORLD POSITION
     * =========================================================
     */

    public void setWorldPosition(
            double x,
            double y,
            double z)
    {
        this.worldX = x;
        this.worldY = y;
        this.worldZ = z;
    }

    public double getWorldX()
    {
        return this.worldX;
    }

    public double getWorldY()
    {
        return this.worldY;
    }

    public double getWorldZ()
    {
        return this.worldZ;
    }

    /*
     * =========================================================
     * ACCESS
     * =========================================================
     */

    public PreviewShaderBridge getShaderBridge()
    {
        return this.shaderBridge;
    }

    public PreviewWorldRenderer getWorldRenderer()
    {
        return this.worldRenderer;
    }

    /*
     * =========================================================
     * CLEANUP
     * =========================================================
     */

    public void delete()
    {
        if (this.shaderBridge != null)
        {
            this.shaderBridge.delete();

            this.shaderBridge = null;
        }

        this.worldRenderer = null;
    }
}
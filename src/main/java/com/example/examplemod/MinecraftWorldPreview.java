package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Связка EditorSceneViewport с настоящим Minecraft renderer.
 *
 * Ручной рендер блоков здесь не используется.
 *
 * Порядок рендера:
 *
 * 1. Sky
 * 2. SOLID
 * 3. CUTOUT_MIPPED
 * 4. CUTOUT
 *
 * После Actor:
 *
 * 5. TRANSLUCENT
 * 6. Weather
 * 7. Clouds
 * 8. End split render
 */
public class MinecraftWorldPreview
{
    private final Minecraft mc;

    private final PreviewWorldRenderer worldRenderer;

    private boolean sceneAvailable;


    public MinecraftWorldPreview()
    {
        this.mc =
                Minecraft.getMinecraft();

        this.worldRenderer =
                new PreviewWorldRenderer(
                        this.mc
                );

        this.sceneAvailable =
                this.mc.world != null;
    }


    /**
     * Первый проход World.
     *
     * Здесь рисуются:
     *
     * Sky
     * SOLID
     * CUTOUT_MIPPED
     * CUTOUT
     *
     * TRANSLUCENT пока не рисуется.
     */
    public void draw(
            Minecraft mc,
            EditorCamera camera,
            int x,
            int y,
            int width,
            int height)
    {
        if (mc == null)
        {
            return;
        }

        if (camera == null)
        {
            return;
        }

        if (width <= 0 ||
                height <= 0)
        {
            return;
        }


        /*
         * =========================================================
         * WORLD CHECK
         * =========================================================
         */

        if (mc.world == null)
        {
            this.sceneAvailable = false;

            return;
        }

        this.sceneAvailable = true;


        /*
         * =========================================================
         * BEGIN SPLIT RENDER
         * =========================================================
         */

        boolean started =
                this.worldRenderer.beginSplitRender(
                        camera,
                        width,
                        height
                );


        if (!started)
        {
            return;
        }


        /*
         * =========================================================
         * OPAQUE WORLD
         * =========================================================
         *
         * Sky
         * SOLID
         * CUTOUT_MIPPED
         * CUTOUT
         */

        this.worldRenderer.renderOpaqueWorld();
    }


    /**
     * Второй проход World.
     *
     * Вызывается ПОСЛЕ Actor.
     *
     * Здесь:
     *
     * TRANSLUCENT
     *
     * Облака здесь больше НЕ рисуются.
     *
     * Это позволяет поставить Weather
     * между translucent terrain и clouds.
     */
    public void drawAfterActor()
    {
        if (!this.sceneAvailable)
        {
            return;
        }


        /*
         * =========================================================
         * TRANSLUCENT WORLD
         * =========================================================
         */

        this.worldRenderer.renderAfterActor();


        /*
         * =========================================================
         * SPLIT RENDER ОСТАЁТСЯ АКТИВНЫМ
         * =========================================================
         *
         * Здесь специально НЕ вызывается
         * renderClouds() и НЕ вызывается
         * endSplitRender().
         *
         * Сначала должен отрисоваться дождь.
         */
    }


    /**
     * Отдельно рисует облака.
     *
     * Этот метод вызывается ПОСЛЕ Weather.
     *
     * Порядок:
     *
     * TRANSLUCENT
     *      ↓
     * Weather
     *      ↓
     * Clouds
     */
    public void renderClouds()
    {
        if (!this.sceneAvailable)
        {
            return;
        }


        this.worldRenderer.renderClouds();
    }


    /**
     * Полностью завершает split-render.
     *
     * Вызывать ПОСЛЕ Actor + Weather + Clouds.
     */
    public void endRender()
    {
        if (!this.sceneAvailable)
        {
            return;
        }

        this.worldRenderer.endSplitRender();
    }


    public PreviewWorldRenderer getWorldRenderer()
    {
        return this.worldRenderer;
    }


    public boolean isSceneAvailable()
    {
        return this.sceneAvailable;
    }


    /**
     * Оставлено для совместимости.
     *
     * Реальная позиция теперь определяется
     * Minecraft World.
     */
    public void setWorldPosition(
            double x,
            double y,
            double z)
    {
        /*
         * Ничего не делаем.
         */
    }


    public void setSceneAvailable(
            boolean available)
    {
        this.sceneAvailable =
                available;
    }


    public void clearScene()
    {
        this.sceneAvailable =
                false;
    }


    public void invalidateCache()
    {
        /*
         * RenderGlobal самостоятельно
         * управляет chunk cache.
         */
    }
}
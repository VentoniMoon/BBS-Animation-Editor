package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;


/**
 * Fog renderer для Minecraft World Preview.
 *
 * Работает только внутри Preview viewport.
 *
 * Не изменяет постоянное состояние Minecraft:
 * после завершения rendering fog отключается.
 */
public class PreviewFogRenderer
{
    private final Minecraft mc;

    /*
     * Минимальные значения fog.
     */
    private static final float DEFAULT_FOG_START = 0.0F;

    private static final float DEFAULT_FOG_END = 256.0F;


    public PreviewFogRenderer(
            Minecraft mc)
    {
        this.mc = mc;
    }


    /*
     * =========================================================
     * BEGIN
     * =========================================================
     */

    public void begin(
            float partialTicks,
            float farPlane)
    {
        this.setup(
                partialTicks,
                farPlane
        );
    }


    /*
     * =========================================================
     * SETUP
     * =========================================================
     */

    public void setup(
            float partialTicks,
            float farPlane)
    {
        if (this.mc == null)
        {
            return;
        }

        if (this.mc.world == null)
        {
            return;
        }


        /*
         * ---------------------------------------------------------
         * FOG COLOR
         * ---------------------------------------------------------
         */

        Vec3d fogColor =
                this.getFogColor(
                        partialTicks
                );


        /*
         * ---------------------------------------------------------
         * FOG DISTANCE
         * ---------------------------------------------------------
         */

        float fogEnd =
                Math.max(
                        DEFAULT_FOG_END,
                        farPlane
                );


        /*
         * Туман начинает появляться примерно
         * после 55% дальности rendering.
         *
         * Это пока базовый вариант.
         *
         * Позже сюда можно добавить:
         *
         * - rain
         * - thunder
         * - biome
         * - water
         * - lava
         * - blindness
         * - shader fog
         */
        float fogStart =
                Math.max(
                        DEFAULT_FOG_START,
                        fogEnd * 0.55F
                );


        /*
         * ---------------------------------------------------------
         * ENABLE FOG
         * ---------------------------------------------------------
         */

        GlStateManager.enableFog();


        /*
         * ---------------------------------------------------------
         * FOG MODE
         * ---------------------------------------------------------
         *
         * Используем glFogi напрямую.
         *
         * В Forge 1.12.2 это надёжнее,
         * чем GlStateManager.glFog(...).
         */

        GL11.glFogi(
                GL11.GL_FOG_MODE,
                GL11.GL_LINEAR
        );


        /*
         * ---------------------------------------------------------
         * FOG START
         * ---------------------------------------------------------
         */

        GL11.glFogf(
                GL11.GL_FOG_START,
                fogStart
        );


        /*
         * ---------------------------------------------------------
         * FOG END
         * ---------------------------------------------------------
         */

        GL11.glFogf(
                GL11.GL_FOG_END,
                fogEnd
        );


        /*
         * ---------------------------------------------------------
         * FOG COLOR
         * ---------------------------------------------------------
         */

        FloatBuffer colorBuffer =
                this.createColorBuffer(
                        fogColor
                );

        GL11.glFog(
                GL11.GL_FOG_COLOR,
                colorBuffer
        );
    }


    /*
     * =========================================================
     * FOG COLOR
     * =========================================================
     */

    private Vec3d getFogColor(
            float partialTicks)
    {
        Vec3d color =
                this.mc.world.getFogColor(
                        partialTicks
                );

        if (color == null)
        {
            return new Vec3d(
                    0.75D,
                    0.80D,
                    0.85D
            );
        }

        return color;
    }


    /*
     * =========================================================
     * COLOR BUFFER
     * =========================================================
     */

    private FloatBuffer createColorBuffer(
            Vec3d color)
    {
        /*
         * LWJGL BufferUtils создаёт native FloatBuffer.
         *
         * Используем его для GL11.glFog().
         */
        FloatBuffer buffer =
                org.lwjgl.BufferUtils.createFloatBuffer(
                        4
                );

        buffer.put(
                (float) color.x
        );

        buffer.put(
                (float) color.y
        );

        buffer.put(
                (float) color.z
        );

        buffer.put(
                1.0F
        );

        buffer.flip();

        return buffer;
    }


    /*
     * =========================================================
     * END
     * =========================================================
     */

    public void end()
    {
        this.reset();
    }


    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        /*
         * Самое главное:
         *
         * Preview fog никогда не должен
         * остаться включённым после rendering.
         */
        GlStateManager.disableFog();


        /*
         * Возвращаем базовые параметры.
         */

        GL11.glFogi(
                GL11.GL_FOG_MODE,
                GL11.GL_LINEAR
        );

        GL11.glFogf(
                GL11.GL_FOG_START,
                0.0F
        );

        GL11.glFogf(
                GL11.GL_FOG_END,
                256.0F
        );
    }
}
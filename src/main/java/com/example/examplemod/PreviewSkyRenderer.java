package com.example.examplemod;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.opengl.GL11;

public class PreviewSkyRenderer
{
    private static final ResourceLocation SUN_TEXTURES =
            new ResourceLocation(
                    "textures/environment/sun.png"
            );

    private static final ResourceLocation MOON_PHASES_TEXTURES =
            new ResourceLocation(
                    "textures/environment/moon_phases.png"
            );


    /*
     * =========================================================
     * SKY GEOMETRY
     * =========================================================
     */

    private static final float SKY_RADIUS = 256.0F;

    private static final int SKY_SEGMENTS = 96;

    private static final int SKY_RINGS = 48;


    /*
     * =========================================================
     * STARS
     * =========================================================
     */

    private static final float STAR_FADE_START = 0.15F;

    private static final float STAR_FADE_END = 0.55F;


    /*
     * =========================================================
     * ATMOSPHERIC HORIZON
     * =========================================================
     */

    private static final float ATMOSPHERE_HORIZON_HEIGHT = 0.30F;

    private static final float ATMOSPHERE_HORIZON_POWER = 1.35F;

    private static final float ATMOSPHERE_STRENGTH = 0.90F;


    /*
     * =========================================================
     * SUNSET
     * =========================================================
     */

    private static final float SUNSET_HORIZON_HEIGHT = 0.35F;

    private static final float SUNSET_HORIZON_POWER = 1.35F;

    private static final float SUNSET_DIRECTION_START_DEGREES = 85.0F;

    private static final float SUNSET_DIRECTION_END_DEGREES = 8.0F;

    private static final float SUNSET_DIRECTION_POWER = 1.15F;

    private static final float SUNSET_STRENGTH = 0.85F;


    private final Minecraft mc;


    public PreviewSkyRenderer(
            Minecraft mc)
    {
        this.mc = mc;
    }


    /*
     * =========================================================
     * RENDER
     * =========================================================
     */

    public void render(
            EditorCamera camera,
            float partialTicks,
            float aspect,
            float fov,
            float nearPlane,
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

        if (camera == null)
        {
            return;
        }


        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT |
                        GL11.GL_COLOR_BUFFER_BIT |
                        GL11.GL_DEPTH_BUFFER_BIT |
                        GL11.GL_TEXTURE_BIT |
                        GL11.GL_FOG_BIT
        );

        int previousShadeModel =
                GL11.glGetInteger(
                        GL11.GL_SHADE_MODEL
                );


        try
        {
            /*
             * =====================================================
             * SKY STATE
             * =====================================================
             */

            GlStateManager.disableTexture2D();

            GlStateManager.disableAlpha();

            GlStateManager.enableFog();

            GlStateManager.disableDepth();

            GlStateManager.depthMask(false);

            GlStateManager.shadeModel(
                    GL11.GL_SMOOTH
            );

            GlStateManager.disableBlend();

            GlStateManager.color(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );


            /*
             * =====================================================
             * SKY SPHERE
             * =====================================================
             */

            this.renderSkySphere(
                    partialTicks
            );


            /*
             * =====================================================
             * CELESTIAL OBJECTS
             * =====================================================
             */

            RenderHelper.disableStandardItemLighting();

            GlStateManager.disableFog();

            this.renderCelestialObjects(
                    partialTicks
            );


            /*
             * =====================================================
             * RESTORE LOCAL STATE
             * =====================================================
             */

            GlStateManager.color(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );

            GlStateManager.disableBlend();

            GlStateManager.enableTexture2D();

            GlStateManager.enableDepth();

            GlStateManager.depthMask(true);

            GlStateManager.enableFog();

            GlStateManager.enableAlpha();

            GlStateManager.alphaFunc(
                    GL11.GL_GREATER,
                    0.1F
            );
        }
        finally
        {
            GlStateManager.shadeModel(
                    previousShadeModel
            );

            GL11.glPopAttrib();

            GlStateManager.color(
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F
            );
        }
    }


    /*
     * =========================================================
     * SKY COLOR
     * =========================================================
     */

    private Vec3d getSkyColor(
            float partialTicks)
    {
        Vec3d skyColor =
                this.mc.world.getSkyColor(
                        this.mc.getRenderViewEntity(),
                        partialTicks
                );

        if (skyColor == null)
        {
            return new Vec3d(
                    0.5D,
                    0.7D,
                    1.0D
            );
        }

        return skyColor;
    }


    /*
     * =========================================================
     * FOG COLOR
     * =========================================================
     */

    private Vec3d getFogColor(
            float partialTicks)
    {
        Vec3d fogColor =
                this.mc.world.getFogColor(
                        partialTicks
                );

        if (fogColor == null)
        {
            return new Vec3d(
                    0.75D,
                    0.80D,
                    0.85D
            );
        }

        return fogColor;
    }


    /*
     * =========================================================
     * SKY SPHERE
     * =========================================================
     */

    private void renderSkySphere(
            float partialTicks)
    {
        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();

        Vec3d skyColor =
                this.getSkyColor(
                        partialTicks
                );

        float skyRed =
                MathHelper.clamp(
                        (float) skyColor.x,
                        0.0F,
                        1.0F
                );

        float skyGreen =
                MathHelper.clamp(
                        (float) skyColor.y,
                        0.0F,
                        1.0F
                );

        float skyBlue =
                MathHelper.clamp(
                        (float) skyColor.z,
                        0.0F,
                        1.0F
                );


        Vec3d fogColor =
                this.getFogColor(
                        partialTicks
                );

        float fogRed =
                MathHelper.clamp(
                        (float) fogColor.x,
                        0.0F,
                        1.0F
                );

        float fogGreen =
                MathHelper.clamp(
                        (float) fogColor.y,
                        0.0F,
                        1.0F
                );

        float fogBlue =
                MathHelper.clamp(
                        (float) fogColor.z,
                        0.0F,
                        1.0F
                );


        float[] sunriseColors =
                this.mc.world.provider
                        .calcSunriseSunsetColors(
                                this.mc.world.getCelestialAngle(
                                        partialTicks
                                ),
                                partialTicks
                        );

        float sunriseStrength =
                0.0F;

        if (sunriseColors != null)
        {
            sunriseStrength =
                    MathHelper.clamp(
                            sunriseColors[3],
                            0.0F,
                            1.0F
                    );
        }


        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION_COLOR
        );


        for (
                int ring = 0;
                ring < SKY_RINGS;
                ++ring)
        {
            float v0 =
                    (float) ring /
                            (float) SKY_RINGS;

            float v1 =
                    (float) (ring + 1) /
                            (float) SKY_RINGS;


            double latitude0 =
                    -Math.PI * 0.5D +
                            v0 * Math.PI;

            double latitude1 =
                    -Math.PI * 0.5D +
                            v1 * Math.PI;


            double cosLatitude0 =
                    Math.cos(
                            latitude0
                    );

            double sinLatitude0 =
                    Math.sin(
                            latitude0
                    );

            double cosLatitude1 =
                    Math.cos(
                            latitude1
                    );

            double sinLatitude1 =
                    Math.sin(
                            latitude1
                    );


            for (
                    int segment = 0;
                    segment < SKY_SEGMENTS;
                    ++segment)
            {
                float u0 =
                        (float) segment /
                                (float) SKY_SEGMENTS;

                float u1 =
                        (float) (segment + 1) /
                                (float) SKY_SEGMENTS;


                double longitude0 =
                        u0 *
                                Math.PI *
                                2.0D;

                double longitude1 =
                        u1 *
                                Math.PI *
                                2.0D;


                double cosLongitude0 =
                        Math.cos(
                                longitude0
                        );

                double sinLongitude0 =
                        Math.sin(
                                longitude0
                        );

                double cosLongitude1 =
                        Math.cos(
                                longitude1
                        );

                double sinLongitude1 =
                        Math.sin(
                                longitude1
                        );


                this.addSkyVertex(
                        buffer,
                        cosLatitude0 *
                                cosLongitude0 *
                                SKY_RADIUS,
                        sinLatitude0 *
                                SKY_RADIUS,
                        cosLatitude0 *
                                sinLongitude0 *
                                SKY_RADIUS,
                        partialTicks,
                        skyRed,
                        skyGreen,
                        skyBlue,
                        fogRed,
                        fogGreen,
                        fogBlue,
                        sunriseColors,
                        sunriseStrength
                );


                this.addSkyVertex(
                        buffer,
                        cosLatitude0 *
                                cosLongitude1 *
                                SKY_RADIUS,
                        sinLatitude0 *
                                SKY_RADIUS,
                        cosLatitude0 *
                                sinLongitude1 *
                                SKY_RADIUS,
                        partialTicks,
                        skyRed,
                        skyGreen,
                        skyBlue,
                        fogRed,
                        fogGreen,
                        fogBlue,
                        sunriseColors,
                        sunriseStrength
                );


                this.addSkyVertex(
                        buffer,
                        cosLatitude1 *
                                cosLongitude1 *
                                SKY_RADIUS,
                        sinLatitude1 *
                                SKY_RADIUS,
                        cosLatitude1 *
                                sinLongitude1 *
                                SKY_RADIUS,
                        partialTicks,
                        skyRed,
                        skyGreen,
                        skyBlue,
                        fogRed,
                        fogGreen,
                        fogBlue,
                        sunriseColors,
                        sunriseStrength
                );


                this.addSkyVertex(
                        buffer,
                        cosLatitude1 *
                                cosLongitude0 *
                                SKY_RADIUS,
                        sinLatitude1 *
                                SKY_RADIUS,
                        cosLatitude1 *
                                sinLongitude0 *
                                SKY_RADIUS,
                        partialTicks,
                        skyRed,
                        skyGreen,
                        skyBlue,
                        fogRed,
                        fogGreen,
                        fogBlue,
                        sunriseColors,
                        sunriseStrength
                );
            }
        }

        tessellator.draw();
    }


    /*
     * =========================================================
     * SKY VERTEX COLOR
     * =========================================================
     */

    private void addSkyVertex(
            BufferBuilder buffer,
            double x,
            double y,
            double z,
            float partialTicks,
            float skyRed,
            float skyGreen,
            float skyBlue,
            float fogRed,
            float fogGreen,
            float fogBlue,
            float[] sunriseColors,
            float sunriseStrength)
    {
        float red =
                skyRed;

        float green =
                skyGreen;

        float blue =
                skyBlue;


        double length =
                Math.sqrt(
                        x * x +
                                y * y +
                                z * z
                );

        if (length > 0.0001D)
        {
            double nx =
                    x / length;

            double ny =
                    y / length;

            double nz =
                    z / length;


            float horizon =
                    1.0F -
                            (
                                    (float)
                                            Math.abs(ny) /
                                            ATMOSPHERE_HORIZON_HEIGHT
                            );

            horizon =
                    MathHelper.clamp(
                            horizon,
                            0.0F,
                            1.0F
                    );

            horizon =
                    horizon *
                            horizon *
                            (
                                    3.0F -
                                            2.0F *
                                                    horizon
                            );

            horizon =
                    (float)
                            Math.pow(
                                    horizon,
                                    ATMOSPHERE_HORIZON_POWER
                            );


            float atmosphereFactor =
                    horizon *
                            ATMOSPHERE_STRENGTH;

            atmosphereFactor =
                    MathHelper.clamp(
                            atmosphereFactor,
                            0.0F,
                            1.0F
                    );


            red =
                    red *
                            (1.0F - atmosphereFactor) +
                            fogRed *
                                    atmosphereFactor;

            green =
                    green *
                            (1.0F - atmosphereFactor) +
                            fogGreen *
                                    atmosphereFactor;

            blue =
                    blue *
                            (1.0F - atmosphereFactor) +
                            fogBlue *
                                    atmosphereFactor;


            if (
                    sunriseColors != null &&
                            sunriseStrength > 0.0F)
            {
                float sunsetHorizon =
                        1.0F -
                                (
                                        (float)
                                                Math.abs(ny) /
                                                SUNSET_HORIZON_HEIGHT
                                );

                sunsetHorizon =
                        MathHelper.clamp(
                                sunsetHorizon,
                                0.0F,
                                1.0F
                        );

                sunsetHorizon =
                        sunsetHorizon *
                                sunsetHorizon *
                                (
                                        3.0F -
                                                2.0F *
                                                        sunsetHorizon
                                );

                sunsetHorizon =
                        (float)
                                Math.pow(
                                        sunsetHorizon,
                                        SUNSET_HORIZON_POWER
                                );


                float celestialAngle =
                        this.mc.world.getCelestialAngle(
                                partialTicks
                        );

                double celestialRadians =
                        celestialAngle *
                                Math.PI *
                                2.0D;


                double sunX =
                        -Math.sin(
                                celestialRadians
                        );

                double sunY =
                        Math.cos(
                                celestialRadians
                        );

                double sunZ =
                        0.0D;


                double dot =
                        nx * sunX +
                                ny * sunY +
                                nz * sunZ;


                float sunsetStart =
                        (float)
                                Math.cos(
                                        Math.toRadians(
                                                SUNSET_DIRECTION_START_DEGREES
                                        )
                                );

                float sunsetEnd =
                        (float)
                                Math.cos(
                                        Math.toRadians(
                                                SUNSET_DIRECTION_END_DEGREES
                                        )
                                );


                float sunFactor =
                        (float)
                                MathHelper.clamp(
                                        (
                                                dot -
                                                        sunsetStart
                                        ) /
                                                (
                                                        sunsetEnd -
                                                                sunsetStart
                                                ),
                                        0.0F,
                                        1.0F
                                );


                sunFactor =
                        sunFactor *
                                sunFactor *
                                (
                                        3.0F -
                                                2.0F *
                                                        sunFactor
                                );

                sunFactor =
                        (float)
                                Math.pow(
                                        sunFactor,
                                        SUNSET_DIRECTION_POWER
                                );


                float factor =
                        sunriseStrength *
                                sunsetHorizon *
                                sunFactor *
                                SUNSET_STRENGTH;

                factor =
                        MathHelper.clamp(
                                factor,
                                0.0F,
                                1.0F
                        );


                red =
                        red *
                                (1.0F - factor) +
                                sunriseColors[0] *
                                        factor;

                green =
                        green *
                                (1.0F - factor) +
                                sunriseColors[1] *
                                        factor;

                blue =
                        blue *
                                (1.0F - factor) +
                                sunriseColors[2] *
                                        factor;
            }
        }


        buffer.pos(
                x,
                y,
                z
        ).color(
                red,
                green,
                blue,
                1.0F
        ).endVertex();
    }


    /*
     * =========================================================
     * CELESTIAL OBJECTS
     * =========================================================
     */

    private void renderCelestialObjects(
            float partialTicks)
    {
        float rainStrength =
                this.mc.world.getRainStrength(
                        partialTicks
                );

        float rainFactor =
                1.0F -
                        MathHelper.clamp(
                                rainStrength,
                                0.0F,
                                1.0F
                        );


        GlStateManager.pushMatrix();


        /*
         * =====================================================
         * VANILLA CELESTIAL TRANSFORM
         * =====================================================
         */

        GlStateManager.rotate(
                -90.0F,
                0.0F,
                1.0F,
                0.0F
        );

        GlStateManager.rotate(
                this.mc.world.getCelestialAngle(
                        partialTicks
                ) * 360.0F,
                1.0F,
                0.0F,
                0.0F
        );


        /*
         * =====================================================
         * SUN + MOON STATE
         * =====================================================
         *
         * Это состояние максимально близко к ванильному
         * RenderGlobal 1.12.2.
         */

        GlStateManager.enableTexture2D();

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        GlStateManager.enableAlpha();

        /*
         * Ванильный alpha test.
         *
         * Полностью прозрачные пиксели sun.png/moon_phases.png
         * не должны превращаться в чёрный прямоугольник.
         */

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.01F
        );


        /*
         * =====================================================
         * COLOR / RAIN
         * =====================================================
         */

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                rainFactor
        );


        /*
         * =====================================================
         * SUN
         * =====================================================
         */

        this.renderSun();


        /*
         * =====================================================
         * MOON
         * =====================================================
         */

        this.renderMoon();


        /*
         * =====================================================
         * STARS
         * =====================================================
         */

        GlStateManager.disableTexture2D();

        float vanillaStarBrightness =
                this.mc.world.getStarBrightness(
                        partialTicks
                );

        float starBrightness =
                vanillaStarBrightness *
                        rainFactor;

        float starFade =
                this.calculateStarFade(
                        starBrightness
                );

        if (starFade > 0.0F)
        {
            GlStateManager.color(
                    starFade,
                    starFade,
                    starFade,
                    starFade
            );

            this.renderStars();
        }


        /*
         * =====================================================
         * CELESTIAL RESTORE
         * =====================================================
         */

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GlStateManager.disableBlend();

        GlStateManager.enableTexture2D();

        GlStateManager.enableAlpha();

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.1F
        );

        GlStateManager.popMatrix();
    }


    /*
     * =========================================================
     * STAR FADE
     * =========================================================
     */

    private float calculateStarFade(
            float brightness)
    {
        if (brightness <= STAR_FADE_START)
        {
            return 0.0F;
        }

        if (brightness >= STAR_FADE_END)
        {
            return MathHelper.clamp(
                    brightness,
                    0.0F,
                    1.0F
            );
        }

        float normalized =
                (
                        brightness -
                                STAR_FADE_START
                ) /
                        (
                                STAR_FADE_END -
                                        STAR_FADE_START
                        );

        float smooth =
                normalized *
                        normalized *
                        (
                                3.0F -
                                        2.0F *
                                                normalized
                        );

        return smooth *
                MathHelper.clamp(
                        brightness,
                        0.0F,
                        1.0F
                );
    }


    /*
     * =========================================================
     * SUN
     * =========================================================
     */

    private void renderSun()
    {
        float size =
                30.0F;


        /*
         * Используем именно ванильную геометрию.
         */

        this.mc.getTextureManager().bindTexture(
                SUN_TEXTURES
        );


        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();


        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION_TEX
        );


        buffer.pos(
                -size,
                100.0D,
                -size
        ).tex(
                0.0D,
                0.0D
        ).endVertex();


        buffer.pos(
                size,
                100.0D,
                -size
        ).tex(
                1.0D,
                0.0D
        ).endVertex();


        buffer.pos(
                size,
                100.0D,
                size
        ).tex(
                1.0D,
                1.0D
        ).endVertex();


        buffer.pos(
                -size,
                100.0D,
                size
        ).tex(
                0.0D,
                1.0D
        ).endVertex();


        tessellator.draw();
    }


    /*
     * =========================================================
     * MOON
     * =========================================================
     */

    private void renderMoon()
    {
        float size =
                20.0F;


        this.mc.getTextureManager().bindTexture(
                MOON_PHASES_TEXTURES
        );


        int moonPhase =
                this.mc.world.getMoonPhase();


        int phaseX =
                moonPhase % 4;

        int phaseY =
                moonPhase / 4 % 2;


        float minU =
                (float) phaseX /
                        4.0F;

        float minV =
                (float) phaseY /
                        2.0F;

        float maxU =
                (float) (phaseX + 1) /
                        4.0F;

        float maxV =
                (float) (phaseY + 1) /
                        2.0F;


        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();


        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION_TEX
        );


        /*
         * Это соответствует ванильному порядку UV.
         */

        buffer.pos(
                -size,
                -100.0D,
                size
        ).tex(
                maxU,
                maxV
        ).endVertex();


        buffer.pos(
                size,
                -100.0D,
                size
        ).tex(
                minU,
                maxV
        ).endVertex();


        buffer.pos(
                size,
                -100.0D,
                -size
        ).tex(
                minU,
                minV
        ).endVertex();


        buffer.pos(
                -size,
                -100.0D,
                -size
        ).tex(
                maxU,
                minV
        ).endVertex();


        tessellator.draw();
    }


    /*
     * =========================================================
     * STARS
     * =========================================================
     */

    private void renderStars()
    {
        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder buffer =
                tessellator.getBuffer();

        Random random =
                new Random(
                        10842L
                );


        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION
        );


        for (
                int i = 0;
                i < 1500;
                ++i)
        {
            double d0 =
                    random.nextFloat() *
                            2.0F -
                            1.0F;

            double d1 =
                    random.nextFloat() *
                            2.0F -
                            1.0F;

            double d2 =
                    random.nextFloat() *
                            2.0F -
                            1.0F;

            double d3 =
                    0.15F +
                            random.nextFloat() *
                                    0.1F;

            double d4 =
                    d0 * d0 +
                            d1 * d1 +
                            d2 * d2;


            if (
                    d4 < 1.0D &&
                            d4 > 0.01D)
            {
                d4 =
                        1.0D /
                                Math.sqrt(
                                        d4
                                );

                d0 *= d4;
                d1 *= d4;
                d2 *= d4;


                double d5 =
                        d0 * 100.0D;

                double d6 =
                        d1 * 100.0D;

                double d7 =
                        d2 * 100.0D;


                double d8 =
                        Math.atan2(
                                d0,
                                d2
                        );

                double d9 =
                        Math.sin(
                                d8
                        );

                double d10 =
                        Math.cos(
                                d8
                        );


                double d11 =
                        Math.atan2(
                                Math.sqrt(
                                        d0 * d0 +
                                                d2 * d2
                                ),
                                d1
                        );

                double d12 =
                        Math.sin(
                                d11
                        );

                double d13 =
                        Math.cos(
                                d11
                        );


                double d14 =
                        random.nextDouble() *
                                Math.PI *
                                2.0D;

                double d15 =
                        Math.sin(
                                d14
                        );

                double d16 =
                        Math.cos(
                                d14
                        );


                for (
                        int j = 0;
                        j < 4;
                        ++j)
                {
                    double d18 =
                            (double)
                                    ((j & 2) - 1) *
                                    d3;

                    double d19 =
                            (double)
                                    ((j + 1 & 2) - 1) *
                                    d3;


                    double d21 =
                            d18 * d16 -
                                    d19 * d15;

                    double d22 =
                            d19 * d16 +
                                    d18 * d15;


                    double d23 =
                            d21 * d12;

                    double d24 =
                            -d21 * d13;


                    double d25 =
                            d24 * d9 -
                                    d22 * d10;

                    double d26 =
                            d22 * d9 +
                                    d24 * d10;


                    buffer.pos(
                            d5 + d25,
                            d6 + d23,
                            d7 + d26
                    ).endVertex();
                }
            }
        }


        tessellator.draw();
    }
}
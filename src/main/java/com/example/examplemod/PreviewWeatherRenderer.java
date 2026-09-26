package com.example.examplemod;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

import org.lwjgl.opengl.GL11;

public class PreviewWeatherRenderer
{
    private final Minecraft mc;
    private final Random random = new Random();

    private static final ResourceLocation RAIN_TEXTURES =
            new ResourceLocation("textures/environment/rain.png");

    private static final ResourceLocation SNOW_TEXTURES =
            new ResourceLocation("textures/environment/snow.png");

    private final float[] rainXCoords = new float[1024];
    private final float[] rainYCoords = new float[1024];

    private int rendererUpdateCount;

    private long lastUpdateTime =
            System.currentTimeMillis();

    private long lastDebugTime;


    public PreviewWeatherRenderer(Minecraft mc)
    {
        this.mc = mc;

        /*
         * =========================================================
         * WEATHER DIRECTION TABLE
         * =========================================================
         */

        for (int i = 0; i < 32; ++i)
        {
            for (int j = 0; j < 32; ++j)
            {
                float f = (float) (j - 16);
                float f1 = (float) (i - 16);

                float f2 =
                        MathHelper.sqrt(
                                f * f +
                                        f1 * f1
                        );

                if (f2 == 0.0F)
                {
                    this.rainXCoords[i << 5 | j] = 0.0F;
                    this.rainYCoords[i << 5 | j] = 0.0F;
                    continue;
                }

                this.rainXCoords[i << 5 | j] =
                        -f1 / f2;

                this.rainYCoords[i << 5 | j] =
                        f / f2;
            }
        }
    }


    public void render(
            EditorCamera camera,
            float partialTicks)
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


        /*
         * =========================================================
         * WEATHER STRENGTH
         * =========================================================
         */

        float precipitationStrength =
                this.mc.world.getRainStrength(
                        partialTicks
                );

        if (precipitationStrength <= 0.0F)
        {
            return;
        }


        /*
         * =========================================================
         * CAMERA
         * =========================================================
         */

        double cameraX = camera.getCameraX();
        double cameraY = camera.getCameraY();
        double cameraZ = camera.getCameraZ();


        /*
         * =========================================================
         * ANIMATION TIMER
         * =========================================================
         */

        long now = System.currentTimeMillis();
        long elapsed = now - this.lastUpdateTime;

        if (elapsed >= 50L)
        {
            int ticks = (int) (elapsed / 50L);

            if (ticks > 5)
            {
                ticks = 5;
            }

            this.rendererUpdateCount += ticks;

            this.lastUpdateTime =
                    now - (elapsed % 50L);
        }


        /*
         * =========================================================
         * DEBUG
         * =========================================================
         */

        if (now - this.lastDebugTime > 1000L)
        {
            this.lastDebugTime = now;

            System.out.println(
                    "[BBS PREVIEW WEATHER]"
                            + " | strength="
                            + precipitationStrength
                            + " | camera="
                            + cameraX
                            + ", "
                            + cameraY
                            + ", "
                            + cameraZ
            );
        }


        /*
         * =========================================================
         * CAMERA BLOCK
         * =========================================================
         */

        int cameraBlockX =
                MathHelper.floor(cameraX);

        int cameraBlockY =
                MathHelper.floor(cameraY);

        int cameraBlockZ =
                MathHelper.floor(cameraZ);


        Tessellator tessellator =
                Tessellator.getInstance();

        BufferBuilder bufferbuilder =
                tessellator.getBuffer();


        /*
         * =========================================================
         * OPENGL STATE
         * =========================================================
         */

        GlStateManager.disableCull();

        GlStateManager.glNormal3f(
                0.0F,
                1.0F,
                0.0F
        );

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.1F
        );

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );


        /*
         * =========================================================
         * WORLD TRANSLATION
         * =========================================================
         */

        bufferbuilder.setTranslation(
                -cameraX,
                -cameraY,
                -cameraZ
        );


        /*
         * =========================================================
         * RENDER DISTANCE
         * =========================================================
         */

        int radius =
                this.mc.gameSettings.fancyGraphics
                        ? 10
                        : 5;

        int currentLayer = -1;

        BlockPos.MutableBlockPos blockPos =
                new BlockPos.MutableBlockPos();


        /*
         * =========================================================
         * WEATHER GRID
         * =========================================================
         */

        for (
                int worldZ = cameraBlockZ - radius;
                worldZ <= cameraBlockZ + radius;
                worldZ++
        )
        {
            for (
                    int worldX = cameraBlockX - radius;
                    worldX <= cameraBlockX + radius;
                    worldX++
            )
            {
                int index =
                        (worldZ - cameraBlockZ + 16) * 32
                                +
                                (worldX - cameraBlockX + 16);

                if (
                        index < 0 ||
                                index >= this.rainXCoords.length
                )
                {
                    continue;
                }


                /*
                 * =================================================
                 * DIRECTION
                 * =================================================
                 */

                double d3 =
                        (double)
                                this.rainXCoords[index]
                                * 0.5D;

                double d4 =
                        (double)
                                this.rainYCoords[index]
                                * 0.5D;


                blockPos.setPos(
                        worldX,
                        0,
                        worldZ
                );


                Biome biome =
                        this.mc.world.getBiome(
                                blockPos
                        );

                if (biome == null)
                {
                    continue;
                }


                /*
                 * =================================================
                 * PRECIPITATION
                 * =================================================
                 */

                if (
                        !biome.canRain() &&
                                !biome.getEnableSnow()
                )
                {
                    continue;
                }


                /*
                 * =================================================
                 * PRECIPITATION HEIGHT
                 * =================================================
                 */

                int precipitationY =
                        this.mc.world
                                .getPrecipitationHeight(
                                        blockPos
                                )
                                .getY();

                int minY =
                        cameraBlockY - radius;

                int maxY =
                        cameraBlockY + radius;


                if (minY < precipitationY)
                {
                    minY = precipitationY;
                }

                if (maxY < precipitationY)
                {
                    maxY = precipitationY;
                }

                if (minY == maxY)
                {
                    continue;
                }


                int startY =
                        precipitationY;

                if (
                        precipitationY <
                                cameraBlockY - radius
                )
                {
                    startY =
                            cameraBlockY - radius;
                }


                /*
                 * =================================================
                 * RANDOM SEED
                 * =================================================
                 */

                this.random.setSeed(
                        (long)
                                (
                                        worldX * worldX * 3121
                                                +
                                                worldX * 45238971
                                                ^
                                                worldZ * worldZ * 418711
                                                        +
                                                        worldZ * 13761
                                )
                );


                /*
                 * =================================================
                 * TEMPERATURE
                 * =================================================
                 */

                blockPos.setPos(
                        worldX,
                        minY,
                        worldZ
                );

                float temperature =
                        biome.getTemperature(
                                blockPos
                        );

                float temperatureAtHeight =
                        this.mc.world
                                .getBiomeProvider()
                                .getTemperatureAtHeight(
                                        temperature,
                                        precipitationY
                                );


                /*
                 * =================================================
                 * SNOW OR RAIN
                 * =================================================
                 */

                boolean snow =
                        temperatureAtHeight < 0.15F;


                /*
                 * =================================================
                 * DISTANCE
                 * =================================================
                 */

                double distanceX =
                        (double) worldX
                                + 0.5D
                                - cameraX;

                double distanceZ =
                        (double) worldZ
                                + 0.5D
                                - cameraZ;

                float distance =
                        MathHelper.sqrt(
                                distanceX * distanceX
                                        +
                                        distanceZ * distanceZ
                        )
                                / (float) radius;


                /*
                 * =================================================
                 * SNOW
                 * =================================================
                 */

                if (snow)
                {
                    if (currentLayer != 1)
                    {
                        if (currentLayer >= 0)
                        {
                            tessellator.draw();
                        }

                        currentLayer = 1;

                        this.mc.getTextureManager()
                                .bindTexture(
                                        SNOW_TEXTURES
                                );

                        bufferbuilder.begin(
                                GL11.GL_QUADS,
                                DefaultVertexFormats
                                        .PARTICLE_POSITION_TEX_COLOR_LMAP
                        );
                    }


                    /*
                     * -------------------------------------------------
                     * SNOW ANIMATION
                     * -------------------------------------------------
                     *
                     * Intentionally kept simple.
                     * This avoids unnecessary floating-point
                     * expressions and keeps compatibility with
                     * the old Java 8 / Forge 1.12.2 toolchain.
                     */

                    double snowOffset =
                            (
                                    (double)
                                            (
                                                    this.rendererUpdateCount
                                                            & 511
                                            )
                                            +
                                            (double) partialTicks
                            )
                                    / 512.0D;


                    double snowU =
                            this.random.nextDouble();


                    double snowV =
                            this.random.nextDouble();


                    /*
                     * -------------------------------------------------
                     * SNOW ALPHA
                     * -------------------------------------------------
                     */

                    float snowAlpha =
                            (
                                    1.0F -
                                            distance * distance
                            )
                                    * 0.3F
                                    +
                                    0.5F;

                    snowAlpha *=
                            precipitationStrength;


                    /*
                     * -------------------------------------------------
                     * LIGHT
                     * -------------------------------------------------
                     */

                    blockPos.setPos(
                            worldX,
                            startY,
                            worldZ
                    );

                    int combinedLight =
                            (
                                    this.mc.world
                                            .getCombinedLight(
                                                    blockPos,
                                                    0
                                            )
                                            * 3
                                            +
                                            15728880
                            )
                                    / 4;

                    int blockLight =
                            combinedLight
                                    >> 16
                                    & 65535;

                    int skyLight =
                            combinedLight
                                    & 65535;


                    /*
                     * -------------------------------------------------
                     * SNOW TOP LEFT
                     * -------------------------------------------------
                     */

                    bufferbuilder
                            .pos(
                                    (double) worldX
                                            - d3
                                            + 0.5D,
                                    (double) maxY,
                                    (double) worldZ
                                            - d4
                                            + 0.5D
                            )
                            .tex(
                                    snowU,
                                    (double) maxY
                                            * 0.25D
                                            + snowOffset
                                            + snowV
                            )
                            .color(
                                    1.0F,
                                    1.0F,
                                    1.0F,
                                    snowAlpha
                            )
                            .lightmap(
                                    blockLight,
                                    skyLight
                            )
                            .endVertex();


                    /*
                     * -------------------------------------------------
                     * SNOW TOP RIGHT
                     * -------------------------------------------------
                     */

                    bufferbuilder
                            .pos(
                                    (double) worldX
                                            + d3
                                            + 0.5D,
                                    (double) maxY,
                                    (double) worldZ
                                            + d4
                                            + 0.5D
                            )
                            .tex(
                                    1.0D + snowU,
                                    (double) maxY
                                            * 0.25D
                                            + snowOffset
                                            + snowV
                            )
                            .color(
                                    1.0F,
                                    1.0F,
                                    1.0F,
                                    snowAlpha
                            )
                            .lightmap(
                                    blockLight,
                                    skyLight
                            )
                            .endVertex();


                    /*
                     * -------------------------------------------------
                     * SNOW BOTTOM RIGHT
                     * -------------------------------------------------
                     */

                    bufferbuilder
                            .pos(
                                    (double) worldX
                                            + d3
                                            + 0.5D,
                                    (double) minY,
                                    (double) worldZ
                                            + d4
                                            + 0.5D
                            )
                            .tex(
                                    1.0D + snowU,
                                    (double) minY
                                            * 0.25D
                                            + snowOffset
                                            + snowV
                            )
                            .color(
                                    1.0F,
                                    1.0F,
                                    1.0F,
                                    snowAlpha
                            )
                            .lightmap(
                                    blockLight,
                                    skyLight
                            )
                            .endVertex();


                    /*
                     * -------------------------------------------------
                     * SNOW BOTTOM LEFT
                     * -------------------------------------------------
                     */

                    bufferbuilder
                            .pos(
                                    (double) worldX
                                            - d3
                                            + 0.5D,
                                    (double) minY,
                                    (double) worldZ
                                            - d4
                                            + 0.5D
                            )
                            .tex(
                                    snowU,
                                    (double) minY
                                            * 0.25D
                                            + snowOffset
                                            + snowV
                            )
                            .color(
                                    1.0F,
                                    1.0F,
                                    1.0F,
                                    snowAlpha
                            )
                            .lightmap(
                                    blockLight,
                                    skyLight
                            )
                            .endVertex();

                    continue;
                }


                /*
                 * =================================================
                 * RAIN
                 * =================================================
                 */

                if (currentLayer != 0)
                {
                    if (currentLayer >= 0)
                    {
                        tessellator.draw();
                    }

                    currentLayer = 0;

                    this.mc.getTextureManager()
                            .bindTexture(
                                    RAIN_TEXTURES
                            );

                    bufferbuilder.begin(
                            GL11.GL_QUADS,
                            DefaultVertexFormats
                                    .PARTICLE_POSITION_TEX_COLOR_LMAP
                    );
                }


                /*
                 * -------------------------------------------------
                 * RAIN ANIMATION
                 * -------------------------------------------------
                 */

                int rainSeed =
                        (
                                this.rendererUpdateCount
                                        +
                                        worldX * worldX * 3121
                                        +
                                        worldX * 45238971
                                        +
                                        worldZ * worldZ * 418711
                                        +
                                        worldZ * 13761
                        )
                                & 31;

                double rainOffset =
                        -(
                                (
                                        (double) rainSeed
                                                +
                                                (double) partialTicks
                                )
                        )
                                / 32.0D
                                *
                                (
                                        3.0D
                                                +
                                                this.random.nextDouble()
                                );


                /*
                 * -------------------------------------------------
                 * RAIN ALPHA
                 * -------------------------------------------------
                 */

                float rainAlpha =
                        (
                                1.0F -
                                        distance * distance
                        )
                                * 0.5F
                                +
                                0.5F;

                rainAlpha *=
                        precipitationStrength;


                /*
                 * -------------------------------------------------
                 * LIGHT
                 * -------------------------------------------------
                 */

                blockPos.setPos(
                        worldX,
                        startY,
                        worldZ
                );

                int combinedLight =
                        this.mc.world.getCombinedLight(
                                blockPos,
                                0
                        );

                int blockLight =
                        combinedLight
                                >> 16
                                & 65535;

                int skyLight =
                        combinedLight
                                & 65535;


                /*
                 * -------------------------------------------------
                 * RAIN TOP LEFT
                 * -------------------------------------------------
                 */

                bufferbuilder
                        .pos(
                                (double) worldX
                                        - d3
                                        + 0.5D,
                                (double) maxY,
                                (double) worldZ
                                        - d4
                                        + 0.5D
                        )
                        .tex(
                                0.0D,
                                (double) minY
                                        * 0.25D
                                        + rainOffset
                        )
                        .color(
                                1.0F,
                                1.0F,
                                1.0F,
                                rainAlpha
                        )
                        .lightmap(
                                blockLight,
                                skyLight
                        )
                        .endVertex();


                /*
                 * -------------------------------------------------
                 * RAIN TOP RIGHT
                 * -------------------------------------------------
                 */

                bufferbuilder
                        .pos(
                                (double) worldX
                                        + d3
                                        + 0.5D,
                                (double) maxY,
                                (double) worldZ
                                        + d4
                                        + 0.5D
                        )
                        .tex(
                                1.0D,
                                (double) minY
                                        * 0.25D
                                        + rainOffset
                        )
                        .color(
                                1.0F,
                                1.0F,
                                1.0F,
                                rainAlpha
                        )
                        .lightmap(
                                blockLight,
                                skyLight
                        )
                        .endVertex();


                /*
                 * -------------------------------------------------
                 * RAIN BOTTOM RIGHT
                 * -------------------------------------------------
                 */

                bufferbuilder
                        .pos(
                                (double) worldX
                                        + d3
                                        + 0.5D,
                                (double) minY,
                                (double) worldZ
                                        + d4
                                        + 0.5D
                        )
                        .tex(
                                1.0D,
                                (double) maxY
                                        * 0.25D
                                        + rainOffset
                        )
                        .color(
                                1.0F,
                                1.0F,
                                1.0F,
                                rainAlpha
                        )
                        .lightmap(
                                blockLight,
                                skyLight
                        )
                        .endVertex();


                /*
                 * -------------------------------------------------
                 * RAIN BOTTOM LEFT
                 * -------------------------------------------------
                 */

                bufferbuilder
                        .pos(
                                (double) worldX
                                        - d3
                                        + 0.5D,
                                (double) minY,
                                (double) worldZ
                                        - d4
                                        + 0.5D
                        )
                        .tex(
                                0.0D,
                                (double) maxY
                                        * 0.25D
                                        + rainOffset
                        )
                        .color(
                                1.0F,
                                1.0F,
                                1.0F,
                                rainAlpha
                        )
                        .lightmap(
                                blockLight,
                                skyLight
                        )
                        .endVertex();
            }
        }


        /*
         * =========================================================
         * FINISH TESSELLATION
         * =========================================================
         */

        if (currentLayer >= 0)
        {
            tessellator.draw();
        }


        /*
         * =========================================================
         * RESTORE TRANSLATION
         * =========================================================
         */

        bufferbuilder.setTranslation(
                0.0D,
                0.0D,
                0.0D
        );


        /*
         * =========================================================
         * RESTORE OPENGL STATE
         * =========================================================
         */

        GlStateManager.enableCull();

        GlStateManager.disableBlend();

        GlStateManager.alphaFunc(
                GL11.GL_GREATER,
                0.1F
        );

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );
    }
}
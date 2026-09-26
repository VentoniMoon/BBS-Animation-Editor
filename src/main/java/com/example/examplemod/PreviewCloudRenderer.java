package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public class PreviewCloudRenderer
{
    private static final ResourceLocation CLOUD_TEXTURE =
            new ResourceLocation(
                    "minecraft",
                    "textures/environment/clouds.png"
            );

    /*
     * Высота облаков.
     */
    private static final double CLOUD_HEIGHT = 110.0D;

    /*
     * Радиус области вокруг камеры, в которой
     * создаются облачные формации.
     */
    private static final int CLOUD_RADIUS = 1024;

    /*
     * Расстояние между потенциальными облачными формациями.
     *
     * Больше значение = облака встречаются реже.
     */
    private static final int FORMATION_SPACING = 420;

    /*
     * Размер отдельной облачной формации.
     *
     * Именно этот параметр отвечает за то,
     * насколько крупными будут сами облака.
     */
    private static final double FORMATION_SIZE = 260.0D;

    /*
     * Разброс размеров облаков.
     */
    private static final double FORMATION_VARIATION = 0.45D;

    /*
     * Вероятность появления облака
     * в каждой ячейке сетки.
     */
    private static final double FORMATION_DENSITY = 0.30D;

    /*
     * Скорость движения облаков.
     */
    private static final double CLOUD_SPEED = 0.03D;

    /*
     * Прозрачность облаков.
     */
    private static final float CLOUD_ALPHA = 0.82F;

    private final Minecraft mc;

    public PreviewCloudRenderer(Minecraft mc)
    {
        this.mc = mc;
    }

    public void render(
            EditorCamera camera,
            float partialTicks,
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

        if (width <= 0 || height <= 0)
        {
            return;
        }

        TextureManager textureManager =
                this.mc.getTextureManager();

        if (textureManager == null)
        {
            return;
        }

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

        try
        {
            this.prepareState();

            textureManager.bindTexture(
                    CLOUD_TEXTURE
            );

            this.drawCloudLayer(
                    camera,
                    partialTicks
            );
        }
        finally
        {
            GL11.glPopAttrib();
        }
    }

    private void prepareState()
    {
        /*
         * =========================================================
         * TEXTURE
         * =========================================================
         */

        GlStateManager.enableTexture2D();


        /*
         * =========================================================
         * FOG
         * =========================================================
         *
         * PreviewWorldRenderer включает fog перед вызовом
         * cloudRenderer.render().
         *
         * Явно включаем его здесь ещё раз, чтобы состояние
         * гарантированно соответствовало облакам.
         */

        GlStateManager.enableFog();

        GL11.glFogi(
                GL11.GL_FOG_MODE,
                GL11.GL_LINEAR
        );


        /*
         * =========================================================
         * BLENDING
         * =========================================================
         */

        GlStateManager.enableBlend();

        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );


        /*
         * =========================================================
         * DEPTH
         * =========================================================
         */

        GlStateManager.enableDepth();

        /*
         * Облака не должны записывать глубину.
         */
        GlStateManager.depthMask(false);


        /*
         * =========================================================
         * CULLING
         * =========================================================
         */

        GlStateManager.disableCull();


        /*
         * =========================================================
         * LIGHTING
         * =========================================================
         */

        GlStateManager.disableLighting();


        /*
         * =========================================================
         * COLOR
         * =========================================================
         */

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }

    private void drawCloudLayer(
            EditorCamera camera,
            float partialTicks)
    {
        double cameraX =
                camera.getCameraX();

        double cameraY =
                camera.getCameraY();

        double cameraZ =
                camera.getCameraZ();

        double worldTime =
                (double) this.mc.world.getTotalWorldTime()
                        + (double) partialTicks;

        /*
         * Облака постепенно движутся по X.
         */
        double movement =
                worldTime * CLOUD_SPEED;

        movement %= 100000.0D;

        double relativeY =
                CLOUD_HEIGHT - cameraY;

        if (Math.abs(relativeY) > 2048.0D)
        {
            return;
        }

        int startGridX =
                (int) Math.floor(
                        (cameraX - CLOUD_RADIUS)
                                / FORMATION_SPACING
                );

        int endGridX =
                (int) Math.floor(
                        (cameraX + CLOUD_RADIUS)
                                / FORMATION_SPACING
                );

        int startGridZ =
                (int) Math.floor(
                        (cameraZ - CLOUD_RADIUS)
                                / FORMATION_SPACING
                );

        int endGridZ =
                (int) Math.floor(
                        (cameraZ + CLOUD_RADIUS)
                                / FORMATION_SPACING
                );

        GL11.glBegin(
                GL11.GL_QUADS
        );

        for (
                int gridX = startGridX;
                gridX <= endGridX;
                gridX++
        )
        {
            for (
                    int gridZ = startGridZ;
                    gridZ <= endGridZ;
                    gridZ++
            )
            {
                double density =
                        this.getDeterministicValue(
                                gridX,
                                gridZ
                        );

                if (density > FORMATION_DENSITY)
                {
                    continue;
                }

                this.drawFormation(
                        gridX,
                        gridZ,
                        relativeY,
                        cameraX,
                        cameraZ,
                        movement
                );
            }
        }

        GL11.glEnd();
    }

    private void drawFormation(
            int gridX,
            int gridZ,
            double relativeY,
            double cameraX,
            double cameraZ,
            double movement)
    {
        double offsetX =
                this.getDeterministicValue(
                        gridX,
                        gridZ,
                        1
                ) - 0.5D;

        double offsetZ =
                this.getDeterministicValue(
                        gridX,
                        gridZ,
                        2
                ) - 0.5D;

        double sizeRandom =
                this.getDeterministicValue(
                        gridX,
                        gridZ,
                        3
                );

        double rotationRandom =
                this.getDeterministicValue(
                        gridX,
                        gridZ,
                        4
                );

        /*
         * Центр облака в мировых координатах.
         */
        double centerX =
                (double) gridX
                        * FORMATION_SPACING
                        + offsetX
                        * FORMATION_SPACING
                        * 0.65D
                        + movement;

        double centerZ =
                (double) gridZ
                        * FORMATION_SPACING
                        + offsetZ
                        * FORMATION_SPACING
                        * 0.65D;

        /*
         * Размер конкретной формации.
         */
        double size =
                FORMATION_SIZE
                        * (
                        1.0D
                                + (
                                sizeRandom - 0.5D
                        )
                                * FORMATION_VARIATION
                );

        /*
         * Облака немного вытянуты.
         */
        double width =
                size * 1.35D;

        double depth =
                size * 0.85D;

        /*
         * Случайный поворот формации.
         */
        double angle =
                rotationRandom
                        * Math.PI
                        * 2.0D;

        double cos =
                Math.cos(angle);

        double sin =
                Math.sin(angle);

        double[] x =
                new double[4];

        double[] z =
                new double[4];

        this.setCorner(
                x,
                z,
                0,
                -width,
                -depth,
                cos,
                sin,
                centerX,
                centerZ,
                cameraX,
                cameraZ
        );

        this.setCorner(
                x,
                z,
                1,
                width,
                -depth,
                cos,
                sin,
                centerX,
                centerZ,
                cameraX,
                cameraZ
        );

        this.setCorner(
                x,
                z,
                2,
                width,
                depth,
                cos,
                sin,
                centerX,
                centerZ,
                cameraX,
                cameraZ
        );

        this.setCorner(
                x,
                z,
                3,
                -width,
                depth,
                cos,
                sin,
                centerX,
                centerZ,
                cameraX,
                cameraZ
        );

        /*
         * ВАЖНО:
         *
         * Теперь каждая формация использует ВСЮ clouds.png.
         *
         * Поэтому мы гарантированно видим её облачный рисунок,
         * а не случайный прозрачный кусочек текстуры.
         */
        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                CLOUD_ALPHA
        );

        GL11.glTexCoord2f(
                0.0F,
                0.0F
        );

        GL11.glVertex3d(
                x[0],
                relativeY,
                z[0]
        );

        GL11.glTexCoord2f(
                1.0F,
                0.0F
        );

        GL11.glVertex3d(
                x[1],
                relativeY,
                z[1]
        );

        GL11.glTexCoord2f(
                1.0F,
                1.0F
        );

        GL11.glVertex3d(
                x[2],
                relativeY,
                z[2]
        );

        GL11.glTexCoord2f(
                0.0F,
                1.0F
        );

        GL11.glVertex3d(
                x[3],
                relativeY,
                z[3]
        );
    }

    private void setCorner(
            double[] x,
            double[] z,
            int index,
            double localX,
            double localZ,
            double cos,
            double sin,
            double centerX,
            double centerZ,
            double cameraX,
            double cameraZ)
    {
        double rotatedX =
                localX * cos
                        - localZ * sin;

        double rotatedZ =
                localX * sin
                        + localZ * cos;

        x[index] =
                centerX
                        + rotatedX
                        - cameraX;

        z[index] =
                centerZ
                        + rotatedZ
                        - cameraZ;
    }

    private double getDeterministicValue(
            int x,
            int z)
    {
        return this.getDeterministicValue(
                x,
                z,
                0
        );
    }

    private double getDeterministicValue(
            int x,
            int z,
            int salt)
    {
        long value =
                (long) x * 341873128712L
                        + (long) z * 132897987541L
                        + (long) salt * 42317861L;

        value =
                (
                        value
                                ^ (value >> 13)
                )
                        * 1274126177L;

        value =
                value
                        ^ (value >> 16);

        long positive =
                value
                        & 0x7FFFFFFFFFFFFFFFL;

        return
                (double) positive
                        / (double) Long.MAX_VALUE;
    }
}
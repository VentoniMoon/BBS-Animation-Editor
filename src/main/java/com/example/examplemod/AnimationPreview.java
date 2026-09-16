package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class AnimationPreview
{
    private int x;
    private int y;
    private int width;
    private int height;

    private double referenceX;
    private double referenceY;
    private double referenceZ;
    private double worldX;
    private double worldY;
    private double worldZ;

    public AnimationPreview()
    {
        this.x = 0;
        this.y = 0;
        this.width = 300;
        this.height = 300;

        this.referenceX = 0.0D;
        this.referenceY = 0.0D;
        this.referenceZ = 0.0D;
    }

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

    /**
     * Настоящий 3D viewport.
     *
     * На этом этапе здесь находится
     * техническая сцена:
     *
     * - перспективная камера;
     * - сетка;
     * - мировые оси;
     * - центральный куб.
     *
     * Позже сюда будет подключён настоящий
     * Minecraft world + Blockbuster actors.
     */
    public void draw(
            Minecraft mc,
            ActorPose actorPose,
            List<AnimationBone> bones,
            int currentFrame,
            EditorCamera camera)
    {
        if (mc == null || camera == null)
        {
            return;
        }

        if (this.width <= 0 || this.height <= 0)
        {
            return;
        }

        /*
         * =========================
         * GUI SCALE
         * =========================
         *
         * Координаты нашего редактора
         * находятся в системе GuiScreen.
         *
         * OpenGL viewport использует
         * реальные пиксели дисплея.
         */
        int scaleFactor =
                mc.gameSettings.guiScale;

        /*
         * Minecraft internally determines
         * the actual scale factor.
         *
         * Для GuiScreen надёжнее вычислить
         * его через displayWidth / scaledWidth.
         */
        net.minecraft.client.gui.ScaledResolution scaledResolution =
                new net.minecraft.client.gui.ScaledResolution(
                        mc
                );

        int scaledWidth =
                scaledResolution.getScaledWidth();

        int scaledHeight =
                scaledResolution.getScaledHeight();

        float scaleX =
                (float) mc.displayWidth
                        / (float) scaledWidth;

        float scaleY =
                (float) mc.displayHeight
                        / (float) scaledHeight;

        /*
         * Перевод GUI-координат в реальные
         * координаты OpenGL.
         */
        int viewportX =
                Math.round(
                        this.x * scaleX
                );

        int viewportWidth =
                Math.round(
                        this.width * scaleX
                );

        int viewportHeight =
                Math.round(
                        this.height * scaleY
                );

        /*
         * OpenGL считает Y снизу вверх.
         */
        int viewportY =
                mc.displayHeight
                        - Math.round(
                        (this.y + this.height)
                                * scaleY
                );

        /*
         * =========================
         * SAVE STATE
         * =========================
         */

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

        /*
         * =========================
         * VIEWPORT
         * =========================
         */

        GL11.glViewport(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );

        GL11.glEnable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glScissor(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );

        /*
         * =========================
         * CLEAR
         * =========================
         */

        GL11.glClearColor(
                0.08F,
                0.09F,
                0.11F,
                1.0F
        );

        GL11.glClear(
                GL11.GL_COLOR_BUFFER_BIT
                        |
                        GL11.GL_DEPTH_BUFFER_BIT
        );

        /*
         * =========================
         * DEPTH
         * =========================
         */

        GL11.glEnable(
                GL11.GL_DEPTH_TEST
        );

        GL11.glDepthFunc(
                GL11.GL_LEQUAL
        );

        /*
         * =========================
         * PROJECTION
         * =========================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        float aspect =
                (float) this.width
                        /
                        (float) this.height;

        GLU.gluPerspective(
                60.0F,
                aspect,
                0.05F,
                500.0F
        );

        /*
         * =========================
         * MODELVIEW
         * =========================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        GL11.glLoadIdentity();

        /*
         * =========================
         * CAMERA
         * =========================
         */

        GL11.glRotatef(
                -camera.getPitch(),
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glRotatef(
                -camera.getYaw(),
                0.0F,
                1.0F,
                0.0F
        );

        GL11.glTranslated(
                -camera.getTargetX(),
                -camera.getTargetY(),
                -camera.getTargetZ()
        );

        GL11.glTranslated(
                0.0D,
                0.0D,
                -camera.getDistance()
        );

        /*
         * =========================
         * RENDER
         * =========================
         */

        GL11.glDisable(
                GL11.GL_TEXTURE_2D
        );

        GL11.glDisable(
                GL11.GL_LIGHTING
        );

        GL11.glDisable(
                GL11.GL_CULL_FACE
        );

        drawGrid();

        drawAxes();

        drawTestCube();

        /*
         * =========================
         * RESTORE MATRICES
         * =========================
         *
         * Это КРИТИЧЕСКИ важно.
         *
         * После popMatrix()
         * Minecraft получает свои исходные
         * GUI projection/modelview matrices.
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();

        /*
         * Возвращаем исходный matrix mode.
         */
        GL11.glMatrixMode(
                oldMatrixMode
        );

        /*
         * =========================
         * RESTORE ATTRIBUTES
         * =========================
         */

        GL11.glPopAttrib();

        /*
         * =========================
         * RESTORE FULL SCREEN VIEWPORT
         * =========================
         *
         * Здесь возвращаем полный физический
         * viewport Minecraft.
         */
        GL11.glViewport(
                0,
                0,
                mc.displayWidth,
                mc.displayHeight
        );

        /*
         * Scissor должен быть выключен
         * после восстановления viewport.
         */
        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        /*
         * Minecraft GUI снова получает
         * обычное 2D-состояние.
         *
         * При этом мы НЕ трогаем projection
         * и modelview через glLoadIdentity().
         */
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();

        GL11.glMatrixMode(
                oldMatrixMode
        );
    }

    /**
     * Сетка XZ.
     */
    private void drawGrid()
    {
        GL11.glLineWidth(
                1.0F
        );

        GL11.glColor3f(
                0.35F,
                0.35F,
                0.35F
        );

        GL11.glBegin(
                GL11.GL_LINES
        );

        for (int i = -20; i <= 20; i++)
        {
            /*
             * Линии вдоль Z.
             */
            GL11.glVertex3d(
                    i,
                    0.0D,
                    -20.0D
            );

            GL11.glVertex3d(
                    i,
                    0.0D,
                    20.0D
            );

            /*
             * Линии вдоль X.
             */
            GL11.glVertex3d(
                    -20.0D,
                    0.0D,
                    i
            );

            GL11.glVertex3d(
                    20.0D,
                    0.0D,
                    i
            );
        }

        GL11.glEnd();

        GL11.glColor3f(
                1.0F,
                1.0F,
                1.0F
        );
    }

    /**
     * Мировые оси:
     *
     * X = красная
     * Y = зелёная
     * Z = синяя
     */
    private void drawAxes()
    {
        GL11.glLineWidth(
                3.0F
        );

        GL11.glBegin(
                GL11.GL_LINES
        );

        /*
         * X.
         */
        GL11.glColor3f(
                1.0F,
                0.2F,
                0.2F
        );

        GL11.glVertex3d(
                0.0D,
                0.0D,
                0.0D
        );

        GL11.glVertex3d(
                5.0D,
                0.0D,
                0.0D
        );

        /*
         * Y.
         */
        GL11.glColor3f(
                0.2F,
                1.0F,
                0.2F
        );

        GL11.glVertex3d(
                0.0D,
                0.0D,
                0.0D
        );

        GL11.glVertex3d(
                0.0D,
                5.0D,
                0.0D
        );

        /*
         * Z.
         */
        GL11.glColor3f(
                0.2F,
                0.4F,
                1.0F
        );

        GL11.glVertex3d(
                0.0D,
                0.0D,
                0.0D
        );

        GL11.glVertex3d(
                0.0D,
                0.0D,
                5.0D
        );

        GL11.glEnd();

        GL11.glColor3f(
                1.0F,
                1.0F,
                1.0F
        );
    }

    /**
     * Тестовый куб в мировом центре.
     */
    private void drawTestCube()
    {
        float size = 1.0F;

        GL11.glColor3f(
                0.75F,
                0.75F,
                0.80F
        );

        GL11.glBegin(
                GL11.GL_QUADS
        );

        /*
         * Передняя грань.
         */
        GL11.glVertex3f(
                -size,
                -size,
                size
        );

        GL11.glVertex3f(
                size,
                -size,
                size
        );

        GL11.glVertex3f(
                size,
                size,
                size
        );

        GL11.glVertex3f(
                -size,
                size,
                size
        );

        /*
         * Задняя грань.
         */
        GL11.glVertex3f(
                size,
                -size,
                -size
        );

        GL11.glVertex3f(
                -size,
                -size,
                -size
        );

        GL11.glVertex3f(
                -size,
                size,
                -size
        );

        GL11.glVertex3f(
                size,
                size,
                -size
        );

        /*
         * Левая грань.
         */
        GL11.glVertex3f(
                -size,
                -size,
                -size
        );

        GL11.glVertex3f(
                -size,
                -size,
                size
        );

        GL11.glVertex3f(
                -size,
                size,
                size
        );

        GL11.glVertex3f(
                -size,
                size,
                -size
        );

        /*
         * Правая грань.
         */
        GL11.glVertex3f(
                size,
                -size,
                size
        );

        GL11.glVertex3f(
                size,
                -size,
                -size
        );

        GL11.glVertex3f(
                size,
                size,
                -size
        );

        GL11.glVertex3f(
                size,
                size,
                size
        );

        /*
         * Верхняя грань.
         */
        GL11.glVertex3f(
                -size,
                size,
                size
        );

        GL11.glVertex3f(
                size,
                size,
                size
        );

        GL11.glVertex3f(
                size,
                size,
                -size
        );

        GL11.glVertex3f(
                -size,
                size,
                -size
        );

        /*
         * Нижняя грань.
         */
        GL11.glVertex3f(
                -size,
                -size,
                -size
        );

        GL11.glVertex3f(
                size,
                -size,
                -size
        );

        GL11.glVertex3f(
                size,
                -size,
                size
        );

        GL11.glVertex3f(
                -size,
                -size,
                size
        );

        GL11.glEnd();

        GL11.glColor3f(
                1.0F,
                1.0F,
                1.0F
        );
    }
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
}
package com.example.examplemod;

import java.nio.FloatBuffer;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

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
     * =========================================================
     * TECHNICAL PREVIEW SCENE
     * =========================================================
     *
     * Preview временно меняет OpenGL-состояние, но перед
     * завершением полностью возвращает его в исходное состояние.
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

        int viewportWidth = mc.displayWidth;
        int viewportHeight = mc.displayHeight;

        if (viewportWidth <= 0 || viewportHeight <= 0)
        {
            return;
        }

        /*
         * =========================================================
         * SAVE OPENGL CAPABILITIES
         * =========================================================
         */

        boolean oldTexture =
                GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        boolean oldLighting =
                GL11.glIsEnabled(GL11.GL_LIGHTING);

        boolean oldCull =
                GL11.glIsEnabled(GL11.GL_CULL_FACE);

        boolean oldBlend =
                GL11.glIsEnabled(GL11.GL_BLEND);

        boolean oldAlpha =
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST);

        boolean oldFog =
                GL11.glIsEnabled(GL11.GL_FOG);

        boolean oldDepth =
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        boolean oldScissor =
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        int oldMatrixMode =
                GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        /*
         * =========================================================
         * SAVE CURRENT COLOR
         * =========================================================
         *
         * LWJGL 2 требует FloatBuffer для glGetFloat().
         */

        FloatBuffer oldColorBuffer =
                BufferUtils.createFloatBuffer(4);

        GL11.glGetFloat(
                GL11.GL_CURRENT_COLOR,
                oldColorBuffer
        );

        float oldRed =
                oldColorBuffer.get(0);

        float oldGreen =
                oldColorBuffer.get(1);

        float oldBlue =
                oldColorBuffer.get(2);

        float oldAlphaValue =
                oldColorBuffer.get(3);

        /*
         * =========================================================
         * SCISSOR
         * =========================================================
         */

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        /*
         * =========================================================
         * PROJECTION
         * =========================================================
         */

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();

        GlStateManager.loadIdentity();

        float aspect =
                (float) viewportWidth /
                        (float) viewportHeight;

        GLU.gluPerspective(
                60.0F,
                aspect,
                0.05F,
                500.0F
        );

        /*
         * =========================================================
         * MODELVIEW
         * =========================================================
         */

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();

        GlStateManager.loadIdentity();

        /*
         * =========================================================
         * CAMERA
         * =========================================================
         */

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

        GlStateManager.translate(
                -camera.getTargetX(),
                -camera.getTargetY(),
                -camera.getTargetZ()
        );

        GlStateManager.translate(
                0.0D,
                0.0D,
                -camera.getDistance()
        );

        /*
         * =========================================================
         * PREVIEW OPENGL STATE
         * =========================================================
         */

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        /*
         * =========================================================
         * GRID
         * =========================================================
         */

        drawGrid();

        /*
         * =========================================================
         * AXES
         * =========================================================
         */

        drawAxes();

        /*
         * =========================================================
         * DIAGNOSTIC CUBE
         * =========================================================
         */

        drawDiagnosticCube();

        /*
         * =========================================================
         * RESTORE MODELVIEW
         * =========================================================
         */

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        /*
         * =========================================================
         * RESTORE PROJECTION
         * =========================================================
         */

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();

        /*
         * =========================================================
         * RESTORE MATRIX MODE
         * =========================================================
         */

        GlStateManager.matrixMode(oldMatrixMode);

        /*
         * =========================================================
         * RESTORE OPENGL CAPABILITIES
         * =========================================================
         */

        restoreCapability(
                GL11.GL_TEXTURE_2D,
                oldTexture
        );

        restoreCapability(
                GL11.GL_LIGHTING,
                oldLighting
        );

        restoreCapability(
                GL11.GL_CULL_FACE,
                oldCull
        );

        restoreCapability(
                GL11.GL_BLEND,
                oldBlend
        );

        restoreCapability(
                GL11.GL_ALPHA_TEST,
                oldAlpha
        );

        restoreCapability(
                GL11.GL_FOG,
                oldFog
        );

        restoreCapability(
                GL11.GL_DEPTH_TEST,
                oldDepth
        );

        restoreCapability(
                GL11.GL_SCISSOR_TEST,
                oldScissor
        );

        /*
         * =========================================================
         * RESTORE COLOR
         * =========================================================
         */

        GlStateManager.color(
                oldRed,
                oldGreen,
                oldBlue,
                oldAlphaValue
        );
    }

    /**
     * =========================================================
     * RESTORE CAPABILITY
     * =========================================================
     */
    private void restoreCapability(
            int capability,
            boolean enabled)
    {
        if (enabled)
        {
            GL11.glEnable(capability);
        }
        else
        {
            GL11.glDisable(capability);
        }
    }

    /**
     * =========================================================
     * GRID
     * =========================================================
     */
    private void drawGrid()
    {
        GL11.glLineWidth(1.0F);

        GL11.glColor3f(
                0.35F,
                0.35F,
                0.35F
        );

        GL11.glBegin(GL11.GL_LINES);

        for (int i = -20; i <= 20; i++)
        {
            /*
             * X
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
             * Z
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
     * =========================================================
     * AXES
     * =========================================================
     */
    private void drawAxes()
    {
        GL11.glLineWidth(3.0F);

        GL11.glBegin(GL11.GL_LINES);

        /*
         * X — RED
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
         * Y — GREEN
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
         * Z — BLUE
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
     * =========================================================
     * DIAGNOSTIC CUBE
     * =========================================================
     */
    private void drawDiagnosticCube()
    {
        GlStateManager.pushMatrix();

        /*
         * CAMERA SPACE
         */

        GlStateManager.translate(
                0.0D,
                0.0D,
                -5.0D
        );

        /*
         * ABSOLUTELY BRIGHT
         */

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        /*
         * CUBE
         */

        float s = 1.0F;

        GL11.glBegin(GL11.GL_QUADS);

        /*
         * FRONT
         */

        GL11.glVertex3f(-s, -s, s);
        GL11.glVertex3f( s, -s, s);
        GL11.glVertex3f( s,  s, s);
        GL11.glVertex3f(-s,  s, s);

        /*
         * BACK
         */

        GL11.glVertex3f( s, -s, -s);
        GL11.glVertex3f(-s, -s, -s);
        GL11.glVertex3f(-s,  s, -s);
        GL11.glVertex3f( s,  s, -s);

        /*
         * LEFT
         */

        GL11.glVertex3f(-s, -s, -s);
        GL11.glVertex3f(-s, -s,  s);
        GL11.glVertex3f(-s,  s,  s);
        GL11.glVertex3f(-s,  s, -s);

        /*
         * RIGHT
         */

        GL11.glVertex3f(s, -s,  s);
        GL11.glVertex3f(s, -s, -s);
        GL11.glVertex3f(s,  s, -s);
        GL11.glVertex3f(s,  s,  s);

        /*
         * TOP
         */

        GL11.glVertex3f(-s, s,  s);
        GL11.glVertex3f( s, s,  s);
        GL11.glVertex3f( s, s, -s);
        GL11.glVertex3f(-s, s, -s);

        /*
         * BOTTOM
         */

        GL11.glVertex3f(-s, -s, -s);
        GL11.glVertex3f( s, -s, -s);
        GL11.glVertex3f( s, -s,  s);
        GL11.glVertex3f(-s, -s,  s);

        GL11.glEnd();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GlStateManager.popMatrix();
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
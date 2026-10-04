package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Stage 5 - 3D transform gizmo.
 *
 * The gizmo is an editor overlay. It never owns animation data:
 * all edits are written directly into the currently selected
 * AnimationKeyframe transform.
 */
public class EditorGizmoController
{
    public enum Mode
    {
        POSITION,
        ROTATION,
        SCALE
    }

    private static final int AXIS_X = 0;
    private static final int AXIS_Y = 1;
    private static final int AXIS_Z = 2;
    private static final int PLANE_XY = 3;
    private static final int PLANE_XZ = 4;
    private static final int PLANE_YZ = 5;

    private static final int BUTTON_SIZE = 26;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTON_MARGIN = 8;

    private static final int AXIS_X_COLOR = 0xFFE85C5C;
    private static final int AXIS_Y_COLOR = 0xFF65D37A;
    private static final int AXIS_Z_COLOR = 0xFF5CA8E8;
    private static final int PLANE_COLOR = 0xFFBFC5CA;

    private Mode mode = Mode.POSITION;

    private int activePart = -1;
    private boolean dragging;

    private int dragStartX;
    private int dragStartY;

    private float dragStartPX;
    private float dragStartPY;
    private float dragStartPZ;

    private float dragStartRX;
    private float dragStartRY;
    private float dragStartRZ;

    private float dragStartSX;
    private float dragStartSY;
    private float dragStartSZ;

    private AnimationKeyframe activeKeyframe;

    public Mode getMode()
    {
        return this.mode;
    }

    public void setMode(Mode mode)
    {
        if (mode != null)
        {
            this.mode = mode;
        }

        this.activePart = -1;
        this.dragging = false;
    }

    public boolean isDragging()
    {
        return this.dragging;
    }

    public boolean isButtonHit(
            int mouseX,
            int mouseY,
            int viewportX,
            int viewportY,
            int viewportWidth)
    {
        int x = viewportX + viewportWidth - BUTTON_MARGIN - BUTTON_SIZE;
        int y = viewportY + BUTTON_MARGIN;

        return mouseX >= x
                && mouseX < x + BUTTON_SIZE
                && mouseY >= y
                && mouseY < y + BUTTON_SIZE * 3 + BUTTON_GAP * 2;
    }

    /**
     * Handles the three editor buttons and gizmo hit testing.
     */
    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame,
            EditorCamera camera,
            boolean enabled)
    {
        if (!enabled || mouseButton != 0)
        {
            return false;
        }

        int buttonX =
                viewportX + viewportWidth - BUTTON_MARGIN - BUTTON_SIZE;
        int buttonY =
                viewportY + BUTTON_MARGIN;

        for (int i = 0; i < 3; i++)
        {
            int y = buttonY + i * (BUTTON_SIZE + BUTTON_GAP);

            if (mouseX >= buttonX
                    && mouseX < buttonX + BUTTON_SIZE
                    && mouseY >= y
                    && mouseY < y + BUTTON_SIZE)
            {
                if (i == 0)
                {
                    setMode(Mode.POSITION);
                }
                else if (i == 1)
                {
                    setMode(Mode.ROTATION);
                }
                else
                {
                    setMode(Mode.SCALE);
                }

                return true;
            }
        }

        if (bone == null || keyframe == null
                || recordFrame == null || camera == null)
        {
            return false;
        }

        ScreenPoint center =
                getGizmoCenter(
                        viewportX,
                        viewportY,
                        viewportWidth,
                        viewportHeight,
                        bone,
                        keyframe,
                        recordFrame,
                        camera
                );

        if (center == null)
        {
            return false;
        }

        double[] gizmoWorld =
                getBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        if (gizmoWorld == null)
        {
            return false;
        }

        float gizmoSize =
                getWorldGizmoSize(
                        camera,
                        gizmoWorld[0],
                        gizmoWorld[1],
                        gizmoWorld[2]
                );

        int hit =
                hitTest(
                        mouseX,
                        mouseY,
                        viewportX,
                        viewportY,
                        viewportWidth,
                        viewportHeight,
                        center,
                        gizmoWorld,
                        gizmoSize,
                        camera
                );

        if (hit < 0)
        {
            return false;
        }

        this.activePart = hit;
        this.dragging = true;
        this.activeKeyframe = keyframe;

        this.dragStartX = mouseX;
        this.dragStartY = mouseY;

        AnimationTransform transform =
                keyframe.getTransform();

        this.dragStartPX = transform.getPositionX();
        this.dragStartPY = transform.getPositionY();
        this.dragStartPZ = transform.getPositionZ();

        this.dragStartRX = transform.getRotationX();
        this.dragStartRY = transform.getRotationY();
        this.dragStartRZ = transform.getRotationZ();

        this.dragStartSX = transform.getScaleX();
        this.dragStartSY = transform.getScaleY();
        this.dragStartSZ = transform.getScaleZ();

        return true;
    }

    public boolean mouseDragged(
            int mouseX,
            int mouseY)
    {
        if (!this.dragging || this.activeKeyframe == null)
        {
            return false;
        }

        AnimationTransform transform =
                this.activeKeyframe.getTransform();

        int dx = mouseX - this.dragStartX;
        int dy = mouseY - this.dragStartY;

        if (this.mode == Mode.POSITION)
        {
            float amountX = dx * 0.10F;
            float amountY = -dy * 0.10F;

            float px = this.dragStartPX;
            float py = this.dragStartPY;
            float pz = this.dragStartPZ;

            if (this.activePart == AXIS_X)
            {
                px += amountX;
            }
            else if (this.activePart == AXIS_Y)
            {
                py += amountY;
            }
            else if (this.activePart == AXIS_Z)
            {
                pz += amountX;
            }
            else if (this.activePart == PLANE_XY)
            {
                px += amountX;
                py += amountY;
            }
            else if (this.activePart == PLANE_XZ)
            {
                px += amountX;
                pz += amountY;
            }
            else if (this.activePart == PLANE_YZ)
            {
                py += amountY;
                pz += amountX;
            }

            transform.setPosition(px, py, pz);
        }
        else if (this.mode == Mode.ROTATION)
        {
            float amount =
                    (dx - dy) * 0.5F;

            if (this.activePart == AXIS_X)
            {
                transform.setRotation(
                        this.dragStartRX + amount,
                        this.dragStartRY,
                        this.dragStartRZ
                );
            }
            else if (this.activePart == AXIS_Y)
            {
                transform.setRotation(
                        this.dragStartRX,
                        this.dragStartRY + amount,
                        this.dragStartRZ
                );
            }
            else
            {
                transform.setRotation(
                        this.dragStartRX,
                        this.dragStartRY,
                        this.dragStartRZ + amount
                );
            }
        }
        else
        {
            float amount =
                    (dx - dy) * 0.01F;

            float sx = this.dragStartSX;
            float sy = this.dragStartSY;
            float sz = this.dragStartSZ;

            if (this.activePart == AXIS_X)
            {
                sx = Math.max(0.01F, sx + amount);
            }
            else if (this.activePart == AXIS_Y)
            {
                sy = Math.max(0.01F, sy + amount);
            }
            else
            {
                sz = Math.max(0.01F, sz + amount);
            }

            transform.setScale(sx, sy, sz);
        }

        return true;
    }

    public boolean mouseReleased()
    {
        if (!this.dragging)
        {
            return false;
        }

        this.dragging = false;
        this.activePart = -1;
        this.activeKeyframe = null;

        return true;
    }

    /**
     * Draw only the three tool buttons in GUI space.
     *
     * The actual transform gizmo is rendered by draw3D() inside the
     * Preview render target. This is important: the gizmo must live in
     * the same perspective/camera space as the actor instead of being a
     * flat 2D decoration over the Preview texture.
     */
    public void draw(
            Minecraft mc,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame,
            EditorCamera camera,
            boolean enabled)
    {
        if (mc == null
                || !enabled
                || viewportWidth <= 0
                || viewportHeight <= 0)
        {
            return;
        }

        int oldMatrixMode =
                GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        ScaledResolution resolution =
                new ScaledResolution(mc);

        GL11.glOrtho(
                0,
                resolution.getScaledWidth(),
                resolution.getScaledHeight(),
                0,
                -1,
                1
        );

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        drawButtons(
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight
        );

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glMatrixMode(oldMatrixMode);
    }

    /**
     * Render the actual gizmo in the Preview's 3D coordinate system.
     *
     * This pass is called while the Preview framebuffer is still bound,
     * after the actor has been rendered and before the Preview is copied
     * to the editor GUI. The gizmo therefore gets real perspective:
     * camera movement, depth and distance all affect its appearance.
     */
    public void draw3D(
            Minecraft mc,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame,
            EditorCamera camera,
            boolean enabled)
    {
        if (mc == null
                || !enabled
                || bone == null
                || keyframe == null
                || recordFrame == null
                || camera == null
                || viewportWidth <= 0
                || viewportHeight <= 0)
        {
            return;
        }

        double[] world =
                getBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        if (world == null)
        {
            return;
        }

        /*
         * The Preview FBO is full-screen sized, but the Preview renderer
         * does NOT render into the whole FBO. PreviewShaderBridge binds
         * the FBO with a viewport corresponding to the GUI Preview
         * rectangle multiplied by the Minecraft scale factor.
         *
         * PreviewWorldRenderer then uses that same viewport while the
         * actor is rendered. We must use exactly the same viewport here.
         * Using displayWidth/displayHeight was the reason the gizmo
         * appeared to drift and react to the camera differently from
         * the actor.
         */
        ScaledResolution scaledResolution =
                new ScaledResolution(mc);

        int scaleFactor =
                Math.max(1, scaledResolution.getScaleFactor());

        int framebufferWidth =
                Math.max(1, mc.displayWidth);

        int framebufferHeight =
                Math.max(1, mc.displayHeight);

        int glX =
                Math.max(0, viewportX * scaleFactor);

        int glY =
                Math.max(
                        0,
                        framebufferHeight
                                - (
                                        viewportY
                                                + viewportHeight
                                ) * scaleFactor
                );

        int glWidth =
                Math.max(
                        1,
                        viewportWidth * scaleFactor
                );

        int glHeight =
                Math.max(
                        1,
                        viewportHeight * scaleFactor
                );

        if (glX + glWidth > framebufferWidth)
        {
            glWidth =
                    framebufferWidth - glX;
        }

        if (glY + glHeight > framebufferHeight)
        {
            glHeight =
                    framebufferHeight - glY;
        }

        if (glWidth <= 0 || glHeight <= 0)
        {
            return;
        }

        int oldMatrixMode =
                GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        IntBuffer viewportBuffer =
                BufferUtils.createIntBuffer(16);

        GL11.glGetInteger(
                GL11.GL_VIEWPORT,
                viewportBuffer
        );

        int oldViewportX = viewportBuffer.get(0);
        int oldViewportY = viewportBuffer.get(1);
        int oldViewportW = viewportBuffer.get(2);
        int oldViewportH = viewportBuffer.get(3);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glViewport(
                glX,
                glY,
                glWidth,
                glHeight
        );

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        /*
         * The actor renderer uses the Preview GUI aspect ratio here,
         * not the physical framebuffer aspect ratio. The viewport
         * already contains the scale factor, so the ratio is identical
         * to viewportWidth / viewportHeight.
         */
        GLU.gluPerspective(
                60.0F,
                (float) viewportWidth
                        / (float) viewportHeight,
                0.05F,
                500.0F
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

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
                -camera.getCameraX(),
                -camera.getCameraY(),
                -camera.getCameraZ()
        );

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GL11.glLineWidth(5.0F);

        /*
         * AnimationBone coordinates come from Blockbuster's model
         * rotation points. Those values are model-space pixels, not
         * world-space blocks.
         *
         * RenderCustomModel uses Minecraft's normal living-entity
         * transform before rendering the custom model:
         *
         *   rotate(180 - entityYaw)
         *   scale(-1, -1, 1)
         *   translate(0, -1.501, 0)
         *   model coordinates / 16
         *
         * Reproduce that exact transform here. This is the important
         * difference between a gizmo that merely follows the actor and
         * one that actually sits on the rendered bone.
         */
        double localX = world[0] - recordFrame.getX();
        double localY = world[1] - recordFrame.getY();
        double localZ = world[2] - recordFrame.getZ();

        double modelX = localX / 16.0D;
        double modelY = localY / 16.0D;
        double modelZ = localZ / 16.0D;

        /*
         * RenderLivingBase applies scale(-1,-1,1), then the model
         * origin offset of -1.501 blocks on Y, then rotates the model
         * by 180 - entity yaw.
         */
        double transformedX = -modelX;
        double transformedY = -modelY + 1.501D;
        double transformedZ = modelZ;

        double yaw = Math.toRadians(
                180.0D - recordFrame.getYaw()
        );

        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double rotatedX =
                cos * transformedX
                        + sin * transformedZ;

        double rotatedZ =
                -sin * transformedX
                        + cos * transformedZ;

        double gizmoX =
                recordFrame.getX() + rotatedX;

        double gizmoY =
                recordFrame.getY() + transformedY;

        double gizmoZ =
                recordFrame.getZ() + rotatedZ;

        GL11.glPushMatrix();
        GL11.glTranslated(gizmoX, gizmoY, gizmoZ);

        float size =
                getWorldGizmoSize(
                        camera,
                        gizmoX,
                        gizmoY,
                        gizmoZ
                );

        if (this.mode == Mode.POSITION)
        {
            drawPositionGizmo3D(size);
        }
        else if (this.mode == Mode.ROTATION)
        {
            drawRotationGizmo3D(size);
        }
        else
        {
            drawScaleGizmo3D(size);
        }

        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glViewport(
                oldViewportX,
                oldViewportY,
                oldViewportW,
                oldViewportH
        );

        GL11.glPopAttrib();
        GL11.glMatrixMode(oldMatrixMode);
    }

    private float getWorldGizmoSize(
            EditorCamera camera,
            double x,
            double y,
            double z)
    {
        double dx = x - camera.getCameraX();
        double dy = y - camera.getCameraY();
        double dz = z - camera.getCameraZ();

        double distance =
                Math.sqrt(dx * dx + dy * dy + dz * dz);

        return (float) Math.max(
                0.35D,
                Math.min(2.5D, distance * 0.10D)
        );
    }

    private void drawPositionGizmo3D(float size)
    {
        drawAxis3D(
                1, 0, 0,
                AXIS_X_COLOR,
                size
        );
        drawAxis3D(
                0, 1, 0,
                AXIS_Y_COLOR,
                size
        );
        drawAxis3D(
                0, 0, 1,
                AXIS_Z_COLOR,
                size
        );

        drawPlane3D(1, 1, 0, size);
        drawPlane3D(1, 0, 1, size);
        drawPlane3D(0, 1, 1, size);
    }

    private void drawRotationGizmo3D(float size)
    {
        drawRing3D(1, 0, 0, size * 0.95F, AXIS_X_COLOR);
        drawRing3D(0, 1, 0, size * 0.95F, AXIS_Y_COLOR);
        drawRing3D(0, 0, 1, size * 0.95F, AXIS_Z_COLOR);
    }

    private void drawScaleGizmo3D(float size)
    {
        drawAxis3D(1, 0, 0, AXIS_X_COLOR, size);
        drawAxis3D(0, 1, 0, AXIS_Y_COLOR, size);
        drawAxis3D(0, 0, 1, AXIS_Z_COLOR, size);

        drawCubeHandle(size, 0, 0, AXIS_X_COLOR);
        drawCubeHandle(0, size, 0, AXIS_Y_COLOR);
        drawCubeHandle(0, 0, size, AXIS_Z_COLOR);
    }

    private void drawAxis3D(
            float x,
            float y,
            float z,
            int color,
            float size)
    {
        float length = 1.8F * size;
        int finalColor =
                this.activePart == (x != 0 ? AXIS_X : y != 0 ? AXIS_Y : AXIS_Z)
                        ? EditorThemeManager.get().getAccentBright()
                        : color;

        setColor(finalColor);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(x * length, y * length, z * length);
        GL11.glEnd();

        float head = 0.18F * size;
        float bx = x * length;
        float by = y * length;
        float bz = z * length;

        GL11.glBegin(GL11.GL_TRIANGLES);

        if (x != 0)
        {
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx - x * head, by + head, bz);
            GL11.glVertex3f(bx - x * head, by - head, bz);
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx - x * head, by, bz + head);
            GL11.glVertex3f(bx - x * head, by, bz - head);
        }
        else if (y != 0)
        {
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx + head, by - y * head, bz);
            GL11.glVertex3f(bx - head, by - y * head, bz);
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx, by - y * head, bz + head);
            GL11.glVertex3f(bx, by - y * head, bz - head);
        }
        else
        {
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx + head, by, bz - z * head);
            GL11.glVertex3f(bx - head, by, bz - z * head);
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f(bx, by + head, bz - z * head);
            GL11.glVertex3f(bx, by - head, bz - z * head);
        }

        GL11.glEnd();
    }

    private void drawPlane3D(
            float x,
            float y,
            float z,
            float size)
    {
        float offset = 0.48F * size;
        float extent = 0.38F * size;
        int color = 0x99BFC5CA;

        setColor(color);
        GL11.glBegin(GL11.GL_QUADS);

        if (z == 0)
        {
            GL11.glVertex3f(offset, offset, 0);
            GL11.glVertex3f(offset + extent, offset, 0);
            GL11.glVertex3f(offset + extent, offset + extent, 0);
            GL11.glVertex3f(offset, offset + extent, 0);
        }
        else if (y == 0)
        {
            GL11.glVertex3f(offset, 0, offset);
            GL11.glVertex3f(offset + extent, 0, offset);
            GL11.glVertex3f(offset + extent, 0, offset + extent);
            GL11.glVertex3f(offset, 0, offset + extent);
        }
        else
        {
            GL11.glVertex3f(0, offset, offset);
            GL11.glVertex3f(0, offset + extent, offset);
            GL11.glVertex3f(0, offset + extent, offset + extent);
            GL11.glVertex3f(0, offset, offset + extent);
        }

        GL11.glEnd();
    }

    private void drawRing3D(
            float nx,
            float ny,
            float nz,
            float radius,
            int color)
    {
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);

        for (int i = 0; i < 64; i++)
        {
            double a =
                    Math.PI * 2.0D * i / 64.0D;

            float c = (float) Math.cos(a) * radius;
            float s = (float) Math.sin(a) * radius;

            if (nx != 0)
            {
                GL11.glVertex3f(0, c, s);
            }
            else if (ny != 0)
            {
                GL11.glVertex3f(c, 0, s);
            }
            else
            {
                GL11.glVertex3f(c, s, 0);
            }
        }

        GL11.glEnd();
    }

    private void drawCubeHandle(
            float x,
            float y,
            float z,
            int color)
    {
        float h = 0.11F;
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x - h, y - h, z - h);
        GL11.glVertex3f(x + h, y - h, z - h);
        GL11.glVertex3f(x + h, y + h, z - h);
        GL11.glVertex3f(x - h, y + h, z - h);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3f(x - h, y - h, z + h);
        GL11.glVertex3f(x + h, y - h, z + h);
        GL11.glVertex3f(x + h, y + h, z + h);
        GL11.glVertex3f(x - h, y + h, z + h);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(x - h, y - h, z - h);
        GL11.glVertex3f(x - h, y - h, z + h);
        GL11.glVertex3f(x + h, y - h, z - h);
        GL11.glVertex3f(x + h, y - h, z + h);
        GL11.glVertex3f(x + h, y + h, z - h);
        GL11.glVertex3f(x + h, y + h, z + h);
        GL11.glVertex3f(x - h, y + h, z - h);
        GL11.glVertex3f(x - h, y + h, z + h);
        GL11.glEnd();
    }

    private void drawButtons(
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight)
    {
        int x =
                viewportX + viewportWidth - BUTTON_MARGIN - BUTTON_SIZE;
        int y =
                viewportY + BUTTON_MARGIN;

        int accent =
                EditorThemeManager.get().getAccent();

        int bright =
                EditorThemeManager.get().getAccentBright();

        for (int i = 0; i < 3; i++)
        {
            int top =
                    y + i * (BUTTON_SIZE + BUTTON_GAP);

            Mode buttonMode =
                    i == 0
                            ? Mode.POSITION
                            : i == 1
                                    ? Mode.ROTATION
                                    : Mode.SCALE;

            boolean selected =
                    this.mode == buttonMode;

            Gui.drawRect(
                    x,
                    top,
                    x + BUTTON_SIZE,
                    top + BUTTON_SIZE,
                    selected
                            ? accent
                            : 0xAA111111
            );

            Gui.drawRect(
                    x,
                    top,
                    x + BUTTON_SIZE,
                    top + 1,
                    selected
                            ? bright
                            : 0xAA303030
            );

            Gui.drawRect(
                    x,
                    top + BUTTON_SIZE - 1,
                    x + BUTTON_SIZE,
                    top + BUTTON_SIZE,
                    0xAA0D0F10
            );

            int icon =
                    selected
                            ? 0xFFFFFFFF
                            : 0xFFB8BEC3;

            drawIcon(i, x, top, icon);
        }
    }

    private void drawIcon(
            int index,
            int x,
            int y,
            int color)
    {
        float cx = x + BUTTON_SIZE / 2.0F;
        float cy = y + BUTTON_SIZE / 2.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(2.0F);

        if (index == 0)
        {
            drawLine(cx - 7, cy, cx + 7, cy, color);
            drawLine(cx, cy - 7, cx, cy + 7, color);
            drawTriangle(cx + 7, cy, cx + 3, cy - 3, cx + 3, cy + 3, color);
            drawTriangle(cx - 7, cy, cx - 3, cy - 3, cx - 3, cy + 3, color);
            drawTriangle(cx, cy - 7, cx - 3, cy - 3, cx + 3, cy - 3, color);
            drawTriangle(cx, cy + 7, cx - 3, cy + 3, cx + 3, cy + 3, color);
        }
        else if (index == 1)
        {
            drawCircle(cx, cy, 7, color, 24);
        }
        else
        {
            drawLine(cx - 6, cy - 6, cx + 6, cy + 6, color);
            drawLine(cx - 6, cy + 6, cx + 6, cy - 6, color);
            drawRectOutline(cx + 3, cy - 8, 5, 5, color);
            drawRectOutline(cx - 8, cy + 3, 5, 5, color);
        }

        GL11.glPopAttrib();
    }

    private void drawPositionGizmo(
            ScreenPoint c,
            float scale)
    {
        float len = 58.0F * scale;

        drawAxis(c, c.x + len, c.y, AXIS_X_COLOR, AXIS_X, false);
        drawAxis(c, c.x, c.y - len, AXIS_Y_COLOR, AXIS_Y, false);
        drawAxis(c, c.x - len * 0.70F, c.y + len * 0.70F, AXIS_Z_COLOR, AXIS_Z, false);

        drawPlaneSquare(
                c.x + 18,
                c.y - 18,
                14,
                PLANE_COLOR,
                0.45F
        );

        drawPlaneSquare(
                c.x - 18,
                c.y + 18,
                14,
                PLANE_COLOR,
                0.45F
        );

        drawPlaneSquare(
                c.x + 17,
                c.y + 17,
                14,
                PLANE_COLOR,
                0.45F
        );
    }

    private void drawRotationGizmo(
            ScreenPoint c,
            float scale)
    {
        float radius = 48.0F * scale;

        drawCircle(
                c.x,
                c.y,
                radius,
                AXIS_X_COLOR,
                48
        );

        drawCircle(
                c.x,
                c.y,
                radius - 8.0F,
                AXIS_Y_COLOR,
                48
        );

        drawCircle(
                c.x,
                c.y,
                radius - 16.0F,
                AXIS_Z_COLOR,
                48
        );

        if (this.activePart >= 0)
        {
            drawCircle(
                    c.x,
                    c.y,
                    radius + 3.0F,
                    EditorThemeManager.get().getAccentBright(),
                    48
            );
        }
    }

    private void drawScaleGizmo(
            ScreenPoint c,
            float scale)
    {
        float len = 58.0F * scale;

        drawScaleAxis(
                c,
                c.x + len,
                c.y,
                AXIS_X_COLOR
        );

        drawScaleAxis(
                c,
                c.x,
                c.y - len,
                AXIS_Y_COLOR
        );

        drawScaleAxis(
                c,
                c.x - len * 0.70F,
                c.y + len * 0.70F,
                AXIS_Z_COLOR
        );
    }

    private void drawAxis(
            ScreenPoint c,
            float x,
            float y,
            int color,
            int axis,
            boolean selected)
    {
        int finalColor =
                this.activePart == axis
                        ? EditorThemeManager.get().getAccentBright()
                        : color;

        drawLine(
                c.x,
                c.y,
                x,
                y,
                finalColor
        );

        float dx = x - c.x;
        float dy = y - c.y;
        float length =
                (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.001F)
        {
            return;
        }

        dx /= length;
        dy /= length;

        float px = -dy;
        float py = dx;

        float size = 8.0F;

        drawTriangle(
                x,
                y,
                x - dx * size + px * 3.5F,
                y - dy * size + py * 3.5F,
                x - dx * size - px * 3.5F,
                y - dy * size - py * 3.5F,
                finalColor
        );
    }

    private void drawScaleAxis(
            ScreenPoint c,
            float x,
            float y,
            int color)
    {
        int finalColor =
                this.activePart == AXIS_X && x != c.x
                        || this.activePart == AXIS_Y && y != c.y
                        ? EditorThemeManager.get().getAccentBright()
                        : color;

        drawLine(
                c.x,
                c.y,
                x,
                y,
                finalColor
        );

        drawRectOutline(
                x - 5,
                y - 5,
                10,
                10,
                finalColor
        );
    }

    private void drawPlaneSquare(
            float x,
            float y,
            float size,
            int color,
            float alpha)
    {
        int a =
                Math.max(
                        0,
                        Math.min(
                                255,
                                (int) (alpha * 255.0F)
                        )
                );

        int finalColor =
                (a << 24)
                        | (color & 0x00FFFFFF);

        drawRectFilled(
                x - size / 2.0F,
                y - size / 2.0F,
                x + size / 2.0F,
                y + size / 2.0F,
                finalColor
        );
    }

    private int hitTest(
            int mouseX,
            int mouseY,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            ScreenPoint center,
            double[] world,
            float size,
            EditorCamera camera)
    {
        /*
         * Hit testing is performed against the projected 3D handles,
         * not against a fixed 2D cross. This keeps the clickable area
         * attached to the geometry when the camera rotates or zooms.
         */
        float length = 1.8F * size;

        if (this.mode == Mode.ROTATION)
        {
            float radius = size * 0.95F;

            int bestAxis = -1;
            double bestDistance = 12.0D;

            for (int axis = 0; axis < 3; axis++)
            {
                double minDistance = Double.MAX_VALUE;

                for (int i = 0; i < 64; i++)
                {
                    double angle =
                            Math.PI * 2.0D * i / 64.0D;

                    double px;
                    double py;
                    double pz;

                    if (axis == AXIS_X)
                    {
                        px = world[0];
                        py = world[1] + Math.cos(angle) * radius;
                        pz = world[2] + Math.sin(angle) * radius;
                    }
                    else if (axis == AXIS_Y)
                    {
                        px = world[0] + Math.cos(angle) * radius;
                        py = world[1];
                        pz = world[2] + Math.sin(angle) * radius;
                    }
                    else
                    {
                        px = world[0] + Math.cos(angle) * radius;
                        py = world[1] + Math.sin(angle) * radius;
                        pz = world[2];
                    }

                    ScreenPoint point =
                            project(
                                    px,
                                    py,
                                    pz,
                                    viewportX,
                                    viewportY,
                                    viewportWidth,
                                    viewportHeight,
                                    camera
                            );

                    if (point == null)
                    {
                        continue;
                    }

                    double dx =
                            mouseX - point.x;
                    double dy =
                            mouseY - point.y;

                    double distance =
                            Math.sqrt(dx * dx + dy * dy);

                    if (distance < minDistance)
                    {
                        minDistance = distance;
                    }
                }

                if (minDistance < bestDistance)
                {
                    bestDistance = minDistance;
                    bestAxis = axis;
                }
            }

            return bestAxis;
        }

        int bestAxis = -1;
        double bestDistance = 11.0D;

        double[][] axes =
                new double[][]
                {
                    {length, 0.0D, 0.0D},
                    {0.0D, length, 0.0D},
                    {0.0D, 0.0D, length}
                };

        for (int axis = 0; axis < 3; axis++)
        {
            ScreenPoint endpoint =
                    project(
                            world[0] + axes[axis][0],
                            world[1] + axes[axis][1],
                            world[2] + axes[axis][2],
                            viewportX,
                            viewportY,
                            viewportWidth,
                            viewportHeight,
                            camera
                    );

            if (endpoint == null)
            {
                continue;
            }

            double distance =
                    distanceToSegment(
                            mouseX,
                            mouseY,
                            center.x,
                            center.y,
                            endpoint.x,
                            endpoint.y
                    );

            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestAxis = axis;
            }
        }

        /*
         * Plane handles are projected from their real 3D positions.
         * They are deliberately tested after the axes so the larger
         * axis handles retain priority at their intersection.
         */
        float offset = 0.48F * size;
        float extent = 0.38F * size;

        double[][] planeCenters =
                new double[][]
                {
                    {offset + extent * 0.5D, offset + extent * 0.5D, 0.0D},
                    {offset + extent * 0.5D, 0.0D, offset + extent * 0.5D},
                    {0.0D, offset + extent * 0.5D, offset + extent * 0.5D}
                };

        for (int plane = 0; plane < 3; plane++)
        {
            ScreenPoint point =
                    project(
                            world[0] + planeCenters[plane][0],
                            world[1] + planeCenters[plane][1],
                            world[2] + planeCenters[plane][2],
                            viewportX,
                            viewportY,
                            viewportWidth,
                            viewportHeight,
                            camera
                    );

            if (point == null)
            {
                continue;
            }

            double dx =
                    mouseX - point.x;
            double dy =
                    mouseY - point.y;

            if (dx * dx + dy * dy <= 12.0D * 12.0D)
            {
                return PLANE_XY + plane;
            }
        }

        return bestAxis;
    }

    private double distanceToSegment(
            double px,
            double py,
            double x1,
            double y1,
            double x2,
            double y2)
    {
        double dx = x2 - x1;
        double dy = y2 - y1;

        double lengthSquared =
                dx * dx + dy * dy;

        if (lengthSquared <= 0.000001D)
        {
            double ex = px - x1;
            double ey = py - y1;

            return Math.sqrt(
                    ex * ex + ey * ey
            );
        }

        double t =
                (
                        (px - x1) * dx
                                + (py - y1) * dy
                )
                        / lengthSquared;

        t =
                Math.max(
                        0.0D,
                        Math.min(
                                1.0D,
                                t
                        )
                );

        double closestX =
                x1 + t * dx;
        double closestY =
                y1 + t * dy;

        double ex =
                px - closestX;
        double ey =
                py - closestY;

        return Math.sqrt(
                ex * ex + ey * ey
        );
    }

    private boolean insideSquare(
            float x,
            float y,
            float centerX,
            float centerY,
            float halfSize)
    {
        return x >= centerX - halfSize
                && x <= centerX + halfSize
                && y >= centerY - halfSize
                && y <= centerY + halfSize;
    }

    private ScreenPoint getGizmoCenter(
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame,
            EditorCamera camera)
    {
        double[] world =
                getBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        /*
         * A gizmo draw pass can happen before the editor has a complete
         * bone/keyframe target.  In that case there is no world position
         * to project.  Return null and let draw() use its safe fallback
         * position instead of dereferencing world[0] here.
         */
        if (world == null)
        {
            return null;
        }

        return project(
                world[0],
                world[1],
                world[2],
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );
    }

    private double[] getBoneWorldPosition(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null)
        {
            return null;
        }

        /*
         * AnimationBone already owns the authoritative local/world
         * transform calculation used by the Preview animation.
         *
         * Do NOT divide these values by 16 here. The same coordinate
         * space is used when the AnimationBone snapshot is applied to
         * Blockbuster's ModelTransform.
         */
        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : 0;

        AnimationTransform world =
                bone.getWorldPivotAt(frame);

        if (world == null)
        {
            return null;
        }

        double recordX =
                recordFrame != null
                        ? recordFrame.getX()
                        : 0.0D;

        double recordY =
                recordFrame != null
                        ? recordFrame.getY()
                        : 0.0D;

        double recordZ =
                recordFrame != null
                        ? recordFrame.getZ()
                        : 0.0D;

        /*
         * The values stored in AnimationBone are the same model-space
         * coordinates used by Blockbuster's ModelCustomRenderer.
         * Keep them in that space here; draw3D() performs the exact
         * model-to-world conversion used by the actor renderer.
         */
        return new double[]
        {
            recordX + world.getPositionX(),
            recordY + world.getPositionY(),
            recordZ + world.getPositionZ()
        };
    }

    private float[] rotateVector(
            float x,
            float y,
            float z,
            float rx,
            float ry,
            float rz)
    {
        double ax = Math.toRadians(rx);
        double ay = Math.toRadians(ry);
        double az = Math.toRadians(rz);

        float cx = (float) Math.cos(ax);
        float sx = (float) Math.sin(ax);
        float cy = (float) Math.cos(ay);
        float sy = (float) Math.sin(ay);
        float cz = (float) Math.cos(az);
        float sz = (float) Math.sin(az);

        float ny = y * cx - z * sx;
        float nz = y * sx + z * cx;
        y = ny;
        z = nz;

        float nx = x * cy + z * sy;
        nz = -x * sy + z * cy;
        x = nx;
        z = nz;

        nx = x * cz - y * sz;
        ny = x * sz + y * cz;

        return new float[] {nx, ny, z};
    }

    private ScreenPoint project(
            double x,
            double y,
            double z,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        double dx = x - camera.getCameraX();
        double dy = y - camera.getCameraY();
        double dz = z - camera.getCameraZ();

        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());

        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);

        double cameraX = cy * dx - sy * dz;
        double cameraZ = sy * dx + cy * dz;

        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);

        double cameraY = cp * dy + sp * cameraZ;
        cameraZ = -sp * dy + cp * cameraZ;

        if (cameraZ >= -0.05D)
        {
            return null;
        }

        double fov =
                Math.toRadians(60.0D);

        double focal =
                viewportHeight /
                        (2.0D * Math.tan(fov / 2.0D));

        float screenX =
                (float) (
                        viewportX
                                + viewportWidth / 2.0D
                                + cameraX * focal / -cameraZ
                );

        float screenY =
                (float) (
                        viewportY
                                + viewportHeight / 2.0D
                                - cameraY * focal / -cameraZ
                );

        return new ScreenPoint(screenX, screenY);
    }

    private float getGizmoScreenScale(
            ScreenPoint center,
            EditorCamera camera)
    {
        return 1.0F;
    }

    private void drawLine(
            float x1,
            float y1,
            float x2,
            float y2,
            int color)
    {
        /*
         * Use Minecraft's GUI tessellation path instead of the fixed
         * function GL line primitive. The Preview may have just rendered
         * through an OptiFine/FBO pipeline, and relying on GL_LINES here
         * can leave the gizmo invisible even though the GUI itself is
         * still rendering correctly.
         */
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length =
                (float) Math.sqrt(dx * dx + dy * dy);

        if (length < 0.001F)
        {
            return;
        }

        float halfWidth = 1.5F;
        float px = -dy / length * halfWidth;
        float py = dx / length * halfWidth;

        drawRectFilled(
                x1 + px,
                y1 + py,
                x2 - px,
                y2 - py,
                color
        );
    }

    private void drawTriangle(
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            int color)
    {
        drawLine(x1, y1, x2, y2, color);
        drawLine(x2, y2, x3, y3, color);
        drawLine(x3, y3, x1, y1, color);
    }

    private void drawCircle(
            float cx,
            float cy,
            float radius,
            int color,
            int segments)
    {
        int count = Math.max(12, segments);

        float previousX =
                cx + radius;
        float previousY =
                cy;

        for (int i = 1; i <= count; i++)
        {
            double angle =
                    Math.PI * 2.0D * i / count;

            float currentX =
                    cx + (float) Math.cos(angle) * radius;

            float currentY =
                    cy + (float) Math.sin(angle) * radius;

            drawLine(
                    previousX,
                    previousY,
                    currentX,
                    currentY,
                    color
            );

            previousX = currentX;
            previousY = currentY;
        }
    }

    private void drawRectOutline(
            float x,
            float y,
            float width,
            float height,
            int color)
    {
        drawRectFilled(x, y, x + width, y + 1.5F, color);
        drawRectFilled(x, y + height - 1.5F, x + width, y + height, color);
        drawRectFilled(x, y, x + 1.5F, y + height, color);
        drawRectFilled(x + width - 1.5F, y, x + width, y + height, color);
    }

    private void drawRectFilled(
            float x1,
            float y1,
            float x2,
            float y2,
            int color)
    {
        int left = (int) Math.floor(Math.min(x1, x2));
        int top = (int) Math.floor(Math.min(y1, y2));
        int right = (int) Math.ceil(Math.max(x1, x2));
        int bottom = (int) Math.ceil(Math.max(y1, y2));

        if (right <= left)
        {
            right = left + 1;
        }

        if (bottom <= top)
        {
            bottom = top + 1;
        }

        Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }

    private void setColor(int color)
    {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        GL11.glColor4f(r, g, b, a);
    }

    private static class ScreenPoint
    {
        private final float x;
        private final float y;

        private ScreenPoint(float x, float y)
        {
            this.x = x;
            this.y = y;
        }
    }
}

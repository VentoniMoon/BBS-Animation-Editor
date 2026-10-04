package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;

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

        int hit =
                hitTest(
                        mouseX,
                        mouseY,
                        center
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

        if (bone == null
                || keyframe == null
                || recordFrame == null
                || camera == null)
        {
            GL11.glPopMatrix();

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();

            GL11.glPopAttrib();
            GL11.glMatrixMode(oldMatrixMode);
            return;
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

        /*
         * The gizmo must remain visible even when the selected actor
         * does not currently have a Record frame.  The animation bone
         * itself is still a valid target, so fall back to the centre of
         * the Preview instead of silently hiding the gizmo.
         */
        if (center == null)
        {
            center =
                    new ScreenPoint(
                            viewportX + viewportWidth / 2.0F,
                            viewportY + viewportHeight / 2.0F
                    );
        }

        float scale =
                getGizmoScreenScale(
                        center,
                        camera
                );

        if (this.mode == Mode.POSITION)
        {
            drawPositionGizmo(center, scale);
        }
        else if (this.mode == Mode.ROTATION)
        {
            drawRotationGizmo(center, scale);
        }
        else
        {
            drawScaleGizmo(center, scale);
        }

        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glPopAttrib();
        GL11.glMatrixMode(oldMatrixMode);
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
            ScreenPoint center)
    {
        float dx = mouseX - center.x;
        float dy = mouseY - center.y;

        if (this.mode == Mode.ROTATION)
        {
            float distance =
                    (float) Math.sqrt(dx * dx + dy * dy);

            if (Math.abs(distance - 48.0F) < 9.0F)
            {
                return AXIS_X;
            }

            if (Math.abs(distance - 40.0F) < 9.0F)
            {
                return AXIS_Y;
            }

            if (Math.abs(distance - 32.0F) < 9.0F)
            {
                return AXIS_Z;
            }

            return -1;
        }

        /*
         * Plane handles are small squares between the axes.
         */
        if (insideSquare(dx, dy, 18.0F, -18.0F, 9.0F))
        {
            return PLANE_XY;
        }

        if (insideSquare(dx, dy, 17.0F, 17.0F, 9.0F))
        {
            return PLANE_XZ;
        }

        if (insideSquare(dx, dy, -18.0F, 18.0F, 9.0F))
        {
            return PLANE_YZ;
        }

        float length = 58.0F;

        if (Math.abs(dy) < 10.0F
                && dx > 12.0F
                && dx < length + 12.0F)
        {
            return AXIS_X;
        }

        if (Math.abs(dx) < 10.0F
                && dy < -12.0F
                && dy > -length - 12.0F)
        {
            return AXIS_Y;
        }

        if (dx < -8.0F
                && dy > 8.0F
                && Math.abs(Math.abs(dx) - Math.abs(dy)) < 15.0F)
        {
            return AXIS_Z;
        }

        return -1;
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
        setColor(color);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
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
        setColor(color);

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
    }

    private void drawCircle(
            float cx,
            float cy,
            float radius,
            int color,
            int segments)
    {
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);

        for (int i = 0; i < segments; i++)
        {
            double angle =
                    Math.PI * 2.0D * i / segments;

            GL11.glVertex2f(
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius
            );
        }

        GL11.glEnd();
    }

    private void drawRectOutline(
            float x,
            float y,
            float width,
            float height,
            int color)
    {
        setColor(color);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }

    private void drawRectFilled(
            float x1,
            float y1,
            float x2,
            float y2,
            int color)
    {
        setColor(color);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x1, y2);
        GL11.glEnd();
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

package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;

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

    private double[] dragAxisX = new double[] {1.0D, 0.0D, 0.0D};
    private double[] dragAxisY = new double[] {0.0D, 1.0D, 0.0D};
    private double[] dragAxisZ = new double[] {0.0D, 0.0D, 1.0D};
    private double[][] dragScreenAxes = new double[][]
    {
        {1.0D, 0.0D}, {0.0D, -1.0D}, {1.0D, 0.0D}
    };
    private double[] dragPixelsPerWorld = new double[] {1.0D, 1.0D, 1.0D};

    private BodyPartModelData bodyPartTarget;
    private AnimationBone bodyPartAttachmentBone;
    private AnimationBone gizmoAttachmentBone;
    private AnimationTransform globalTransformTarget;
    private int gizmoFrame;
    private boolean chameleonCoordinateSpace;
    private EntityActor previewActor;

    public void setBodyPartTarget(
            BodyPartModelData model,
            AnimationBone attachmentBone)
    {
        this.bodyPartTarget = model;
        this.bodyPartAttachmentBone = attachmentBone;
    }

    public void setGizmoAttachmentBone(AnimationBone bone)
    {
        this.gizmoAttachmentBone = bone;
    }

    public void setGlobalTransformTarget(AnimationTransform transform)
    {
        this.globalTransformTarget = transform;
    }

    public void setPreviewActor(EntityActor actor)
    {
        this.previewActor = actor;
    }

    public void setChameleonCoordinateSpace(boolean value)
    {
        this.chameleonCoordinateSpace = value;
    }

    public void setGizmoFrame(int frame)
    {
        this.gizmoFrame = Math.max(0, frame);
    }

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

        if (bone == null || camera == null
                || keyframe == null)
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
                getNativeBoneWorldPosition(
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
                        mouseX, mouseY,
                        viewportX, viewportY, viewportWidth, viewportHeight,
                        center, gizmoWorld, gizmoSize, camera,
                        getNativeBoneWorldAxes(bone) != null
                        ? getNativeBoneWorldAxes(bone)
                        : getBoneWorldAxes(bone, keyframe, recordFrame)
                );

        if (hit < 0)
        {
            return false;
        }

        this.activePart = hit;
        this.dragging = true;
        this.activeKeyframe = keyframe;

        double[][] axes =
                getNativeBoneWorldAxes(bone);

        if (axes == null)
        {
            axes = getBoneWorldAxes(bone, keyframe, recordFrame);
        }
        if (axes != null)
        {
            this.dragAxisX = axes[0].clone();
            this.dragAxisY = axes[1].clone();
            this.dragAxisZ = axes[2].clone();

            double[][] captured = new double[][]
            {
                this.dragAxisX, this.dragAxisY, this.dragAxisZ
            };

            for (int axis = 0; axis < 3; axis++)
            {
                ScreenPoint endpoint = project(
                        gizmoWorld[0] + captured[axis][0],
                        gizmoWorld[1] + captured[axis][1],
                        gizmoWorld[2] + captured[axis][2],
                        viewportX, viewportY, viewportWidth, viewportHeight, camera
                );
                if (endpoint != null)
                {
                    double sx = endpoint.x - center.x;
                    double sy = endpoint.y - center.y;
                    double length = Math.sqrt(sx * sx + sy * sy);
                    if (length > 0.0001D)
                    {
                        this.dragScreenAxes[axis][0] = sx / length;
                        this.dragScreenAxes[axis][1] = sy / length;
                        this.dragPixelsPerWorld[axis] = length;
                    }
                }
            }
        }

        this.dragStartX = mouseX;
        this.dragStartY = mouseY;

        AnimationTransform transform =
                this.globalTransformTarget != null
                        ? this.globalTransformTarget
                        : keyframe.getTransform();

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

    public boolean mouseDragged(int mouseX, int mouseY)
    {
        if (!this.dragging || (this.activeKeyframe == null && this.globalTransformTarget == null))
        {
            return false;
        }

        AnimationTransform transform =
                this.globalTransformTarget != null
                        ? this.globalTransformTarget
                        : this.activeKeyframe.getTransform();
        double mouseDX = mouseX - this.dragStartX;
        double mouseDY = mouseY - this.dragStartY;

        if (this.mode == Mode.POSITION)
        {
            float px = this.dragStartPX;
            float py = this.dragStartPY;
            float pz = this.dragStartPZ;

            if (this.activePart == AXIS_X)
            {
                px += (float) getDataAxisDragAmount(AXIS_X, mouseDX, mouseDY);
            }
            else if (this.activePart == AXIS_Y)
            {
                py += (float) getDataAxisDragAmount(AXIS_Y, mouseDX, mouseDY);
            }
            else if (this.activePart == AXIS_Z)
            {
                pz += (float) getDataAxisDragAmount(AXIS_Z, mouseDX, mouseDY);
            }
            else if (this.activePart == PLANE_XY)
            {
                double[] d = getPlaneDragAmount(AXIS_X, AXIS_Y, mouseDX, mouseDY);
                px += (float) getDataAxisDelta(AXIS_X, d[0]);
                py += (float) getDataAxisDelta(AXIS_Y, d[1]);
            }
            else if (this.activePart == PLANE_XZ)
            {
                double[] d = getPlaneDragAmount(AXIS_X, AXIS_Z, mouseDX, mouseDY);
                px += (float) getDataAxisDelta(AXIS_X, d[0]);
                pz += (float) getDataAxisDelta(AXIS_Z, d[1]);
            }
            else if (this.activePart == PLANE_YZ)
            {
                double[] d = getPlaneDragAmount(AXIS_Y, AXIS_Z, mouseDX, mouseDY);
                py += (float) getDataAxisDelta(AXIS_Y, d[0]);
                pz += (float) getDataAxisDelta(AXIS_Z, d[1]);
            }
            transform.setPosition(px, py, pz);
        }
        else if (this.mode == Mode.ROTATION)
        {
            float amount = (float) getRotationDragAngle(this.activePart, mouseDX, mouseDY);
            if (this.activePart == AXIS_X)
            {
                transform.setRotation(this.dragStartRX + amount, this.dragStartRY, this.dragStartRZ);
            }
            else if (this.activePart == AXIS_Y)
            {
                transform.setRotation(this.dragStartRX, this.dragStartRY + amount, this.dragStartRZ);
            }
            else
            {
                transform.setRotation(this.dragStartRX, this.dragStartRY, this.dragStartRZ + amount);
            }
        }
        else
        {
            float amount = (float) (getAxisDragAmount(this.activePart, mouseDX, mouseDY) * 0.25D);
            float sx = this.dragStartSX;
            float sy = this.dragStartSY;
            float sz = this.dragStartSZ;
            if (this.activePart == AXIS_X) sx = Math.max(0.01F, sx + amount);
            else if (this.activePart == AXIS_Y) sy = Math.max(0.01F, sy + amount);
            else sz = Math.max(0.01F, sz + amount);
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
        this.globalTransformTarget = null;

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
    /**
     * Draw the GUI portion of the gizmo.
     *
     * The handles themselves are rendered by draw3D() inside the Preview
     * framebuffer, following the architecture of the original BBS editor.
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
        if (mc == null || !enabled || viewportWidth <= 0 || viewportHeight <= 0)
        {
            return;
        }

        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        ScaledResolution resolution = new ScaledResolution(mc);
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
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        /*
         * The Preview itself is already copied to the normal Minecraft GUI
         * framebuffer when this method is called. Render the visible gizmo
         * here as a true GUI overlay. This deliberately does NOT depend on
         * the Preview FBO, OptiFine shader state, or the modelview matrix
         * left behind by the actor renderer.
         */
        drawButtons(viewportX, viewportY, viewportWidth, viewportHeight);

        /* Transform handles are rendered only by draw3D(). */
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glMatrixMode(oldMatrixMode);
    }

    /**
     * Rebuild of the visible gizmo.  World-space geometry is projected only
     * to determine the orientation on screen; the actual handle size is
     * deliberately pixel based.  This keeps the gizmo readable at every
     * camera distance and, more importantly, makes it independent of the
     * Preview FBO, OptiFine shaders and depth-buffer state.
     */
    private void drawScreenSpaceGizmo(
            ScreenPoint center,
            double[] world,
            double[][] axes,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        final float axisLength = 72.0F;
        final float planeOffset = 24.0F;
        final float planeSize = 24.0F;
        final float ringRadius = 62.0F;

        ScreenDirection[] directions = new ScreenDirection[3];

        for (int i = 0; i < 3; i++)
        {
            directions[i] = getScreenDirection(
                    center,
                    world,
                    axes[i],
                    viewportX,
                    viewportY,
                    viewportWidth,
                    viewportHeight,
                    camera
            );

            if (directions[i] == null)
            {
                directions[i] = fallbackScreenDirection(i);
            }
        }

        if (this.mode == Mode.ROTATION)
        {
            drawScreenRing(center, directions[1], directions[2], ringRadius, AXIS_X_COLOR);
            drawScreenRing(center, directions[2], directions[0], ringRadius, AXIS_Y_COLOR);
            drawScreenRing(center, directions[0], directions[1], ringRadius, AXIS_Z_COLOR);
            drawScreenCenter(center);
        }
        else
        {
            drawScreenAxis(center, directions[0], axisLength, AXIS_X_COLOR, AXIS_X);
            drawScreenAxis(center, directions[1], axisLength, AXIS_Y_COLOR, AXIS_Y);
            drawScreenAxis(center, directions[2], axisLength, AXIS_Z_COLOR, AXIS_Z);

            if (this.mode == Mode.POSITION)
            {
                drawScreenPlane(center, directions[0], directions[1], planeOffset, planeSize, PLANE_XY);
                drawScreenPlane(center, directions[0], directions[2], planeOffset, planeSize, PLANE_XZ);
                drawScreenPlane(center, directions[1], directions[2], planeOffset, planeSize, PLANE_YZ);
            }
            else
            {
                drawScreenHandle(center, directions[0], axisLength, AXIS_X_COLOR);
                drawScreenHandle(center, directions[1], axisLength, AXIS_Y_COLOR);
                drawScreenHandle(center, directions[2], axisLength, AXIS_Z_COLOR);
            }

            drawScreenCenter(center);
        }
    }

    private ScreenDirection getScreenDirection(
            ScreenPoint center,
            double[] world,
            double[] axis,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        if (axis == null)
        {
            return null;
        }

        double sample = 0.35D;
        ScreenPoint positive = project(
                world[0] + axis[0] * sample,
                world[1] + axis[1] * sample,
                world[2] + axis[2] * sample,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenPoint negative = project(
                world[0] - axis[0] * sample,
                world[1] - axis[1] * sample,
                world[2] - axis[2] * sample,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );

        double dx;
        double dy;

        if (positive != null)
        {
            dx = positive.x - center.x;
            dy = positive.y - center.y;
        }
        else if (negative != null)
        {
            dx = center.x - negative.x;
            dy = center.y - negative.y;
        }
        else
        {
            return null;
        }

        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001D)
        {
            return null;
        }

        return new ScreenDirection(dx / length, dy / length);
    }

    private ScreenDirection fallbackScreenDirection(int axis)
    {
        if (axis == AXIS_X)
        {
            return new ScreenDirection(1.0D, 0.0D);
        }
        if (axis == AXIS_Y)
        {
            return new ScreenDirection(0.0D, -1.0D);
        }
        return new ScreenDirection(-0.72D, 0.72D);
    }

    private void drawScreenAxis(
            ScreenPoint center,
            ScreenDirection direction,
            float length,
            int color,
            int axisId)
    {
        int finalColor = this.activePart == axisId
                ? EditorThemeManager.get().getAccentBright()
                : color;

        float endX = center.x + (float) direction.x * length;
        float endY = center.y + (float) direction.y * length;

        drawLine(center.x, center.y, endX, endY, finalColor);
        drawProjectedArrow(center, new ScreenPoint(endX, endY), 9.0F, finalColor);
    }

    private void drawScreenPlane(
            ScreenPoint center,
            ScreenDirection a,
            ScreenDirection b,
            float offset,
            float size,
            int planeId)
    {
        float x1 = center.x + (float) (a.x * offset + b.x * offset);
        float y1 = center.y + (float) (a.y * offset + b.y * offset);
        float x2 = center.x + (float) (a.x * (offset + size) + b.x * offset);
        float y2 = center.y + (float) (a.y * (offset + size) + b.y * offset);
        float x3 = center.x + (float) (a.x * (offset + size) + b.x * (offset + size));
        float y3 = center.y + (float) (a.y * (offset + size) + b.y * (offset + size));
        float x4 = center.x + (float) (a.x * offset + b.x * (offset + size));
        float y4 = center.y + (float) (a.y * offset + b.y * (offset + size));

        int color = this.activePart == planeId
                ? EditorThemeManager.get().getAccentBright()
                : 0xCCBFC5CA;

        drawLine(x1, y1, x2, y2, color);
        drawLine(x2, y2, x3, y3, color);
        drawLine(x3, y3, x4, y4, color);
        drawLine(x4, y4, x1, y1, color);
    }

    private void drawScreenHandle(
            ScreenPoint center,
            ScreenDirection direction,
            float length,
            int color)
    {
        float x = center.x + (float) direction.x * length;
        float y = center.y + (float) direction.y * length;
        float half = 5.0F;
        drawRectOutline(x - half, y - half, half * 2.0F, half * 2.0F, color);
    }

    private void drawScreenRing(
            ScreenPoint center,
            ScreenDirection a,
            ScreenDirection b,
            float radius,
            int color)
    {
        float previousX = center.x + (float) a.x * radius;
        float previousY = center.y + (float) a.y * radius;

        for (int i = 1; i <= 64; i++)
        {
            double angle = Math.PI * 2.0D * i / 64.0D;
            float x = center.x + (float) (a.x * Math.cos(angle) + b.x * Math.sin(angle)) * radius;
            float y = center.y + (float) (a.y * Math.cos(angle) + b.y * Math.sin(angle)) * radius;
            drawLine(previousX, previousY, x, y, color);
            previousX = x;
            previousY = y;
        }
    }

    private void drawScreenCenter(ScreenPoint center)
    {
        int bright = EditorThemeManager.get().getAccentBright();
        drawCircle(center.x, center.y, 5.0F, bright, 20);
        drawLine(center.x - 7.0F, center.y, center.x + 7.0F, center.y, bright);
        drawLine(center.x, center.y - 7.0F, center.x, center.y + 7.0F, bright);
    }

    private static class ScreenDirection
    {
        private final double x;
        private final double y;

        private ScreenDirection(double x, double y)
        {
            double length = Math.sqrt(x * x + y * y);
            if (length < 0.000001D)
            {
                this.x = 1.0D;
                this.y = 0.0D;
            }
            else
            {
                this.x = x / length;
                this.y = y / length;
            }
        }
    }

    private void drawProjectedGizmo(
            ScreenPoint center,
            double[] world,
            float size,
            double[][] axes,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        if (center == null || world == null || axes == null)
        {
            return;
        }

        if (this.mode == Mode.ROTATION)
        {
            drawProjectedRotationGizmo(
                    center,
                    world,
                    size,
                    axes,
                    viewportX,
                    viewportY,
                    viewportWidth,
                    viewportHeight,
                    camera
            );
            return;
        }

        drawProjectedAxis(
                center,
                world,
                axes[0],
                size,
                AXIS_X_COLOR,
                AXIS_X,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );

        drawProjectedAxis(
                center,
                world,
                axes[1],
                size,
                AXIS_Y_COLOR,
                AXIS_Y,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );

        drawProjectedAxis(
                center,
                world,
                axes[2],
                size,
                AXIS_Z_COLOR,
                AXIS_Z,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );

        if (this.mode == Mode.POSITION)
        {
            drawProjectedPlane(
                    center, world, axes[0], axes[1],
                    size, PLANE_XY,
                    viewportX, viewportY,
                    viewportWidth, viewportHeight, camera
            );

            drawProjectedPlane(
                    center, world, axes[0], axes[2],
                    size, PLANE_XZ,
                    viewportX, viewportY,
                    viewportWidth, viewportHeight, camera
            );

            drawProjectedPlane(
                    center, world, axes[1], axes[2],
                    size, PLANE_YZ,
                    viewportX, viewportY,
                    viewportWidth, viewportHeight, camera
            );
        }
        else
        {
            drawProjectedCubeHandle(
                    center, world, axes[0], size, AXIS_X_COLOR,
                    viewportX, viewportY, viewportWidth, viewportHeight, camera
            );
            drawProjectedCubeHandle(
                    center, world, axes[1], size, AXIS_Y_COLOR,
                    viewportX, viewportY, viewportWidth, viewportHeight, camera
            );
            drawProjectedCubeHandle(
                    center, world, axes[2], size, AXIS_Z_COLOR,
                    viewportX, viewportY, viewportWidth, viewportHeight, camera
            );
        }

        drawProjectedCenter(center);
    }

    private void drawProjectedAxis(
            ScreenPoint center,
            double[] world,
            double[] axis,
            float size,
            int color,
            int axisId,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        if (axis == null)
        {
            return;
        }

        float length = 2.15F * size;

        ScreenPoint end =
                project(
                        world[0] + axis[0] * length,
                        world[1] + axis[1] * length,
                        world[2] + axis[2] * length,
                        viewportX,
                        viewportY,
                        viewportWidth,
                        viewportHeight,
                        camera
                );

        if (end == null)
        {
            return;
        }

        int finalColor =
                this.activePart == axisId
                        ? EditorThemeManager.get().getAccentBright()
                        : color;

        drawLine(
                center.x,
                center.y,
                end.x,
                end.y,
                finalColor
        );

        drawProjectedArrow(
                center,
                end,
                8.0F,
                finalColor
        );
    }

    private void drawProjectedPlane(
            ScreenPoint center,
            double[] world,
            double[] axisA,
            double[] axisB,
            float size,
            int planeId,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        float offset = 0.48F * size;
        float extent = 0.38F * size;

        ScreenPoint p1 = projectOffset(
                world, axisA, axisB,
                offset, offset,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenPoint p2 = projectOffset(
                world, axisA, axisB,
                offset + extent, offset,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenPoint p3 = projectOffset(
                world, axisA, axisB,
                offset + extent, offset + extent,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenPoint p4 = projectOffset(
                world, axisA, axisB,
                offset, offset + extent,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );

        if (p1 == null || p2 == null || p3 == null || p4 == null)
        {
            return;
        }

        int color =
                this.activePart == planeId
                        ? EditorThemeManager.get().getAccentBright()
                        : 0x99BFC5CA;

        drawLine(p1.x, p1.y, p2.x, p2.y, color);
        drawLine(p2.x, p2.y, p3.x, p3.y, color);
        drawLine(p3.x, p3.y, p4.x, p4.y, color);
        drawLine(p4.x, p4.y, p1.x, p1.y, color);
    }

    private ScreenPoint projectOffset(
            double[] world,
            double[] axisA,
            double[] axisB,
            double amountA,
            double amountB,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        return project(
                world[0] + axisA[0] * amountA + axisB[0] * amountB,
                world[1] + axisA[1] * amountA + axisB[1] * amountB,
                world[2] + axisA[2] * amountA + axisB[2] * amountB,
                viewportX,
                viewportY,
                viewportWidth,
                viewportHeight,
                camera
        );
    }

    private void drawProjectedRotationGizmo(
            ScreenPoint center,
            double[] world,
            float size,
            double[][] axes,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        drawProjectedRing(
                center, world, axes[1], axes[2],
                size * 0.95F, AXIS_X_COLOR,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        drawProjectedRing(
                center, world, axes[2], axes[0],
                size * 0.95F, AXIS_Y_COLOR,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        drawProjectedRing(
                center, world, axes[0], axes[1],
                size * 0.95F, AXIS_Z_COLOR,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        drawProjectedCenter(center);
    }

    private void drawProjectedRing(
            ScreenPoint center,
            double[] world,
            double[] axisA,
            double[] axisB,
            float radius,
            int color,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        ScreenPoint previous = null;
        int segments = 48;

        for (int i = 0; i <= segments; i++)
        {
            double angle =
                    Math.PI * 2.0D * i / segments;

            double x =
                    world[0]
                            + (axisA[0] * Math.cos(angle)
                            + axisB[0] * Math.sin(angle)) * radius;

            double y =
                    world[1]
                            + (axisA[1] * Math.cos(angle)
                            + axisB[1] * Math.sin(angle)) * radius;

            double z =
                    world[2]
                            + (axisA[2] * Math.cos(angle)
                            + axisB[2] * Math.sin(angle)) * radius;

            ScreenPoint current =
                    project(
                            x, y, z,
                            viewportX, viewportY,
                            viewportWidth, viewportHeight,
                            camera
                    );

            if (current != null && previous != null)
            {
                drawLine(
                        previous.x,
                        previous.y,
                        current.x,
                        current.y,
                        color
                );
            }

            previous = current;
        }
    }

    private void drawProjectedArrow(
            ScreenPoint from,
            ScreenPoint to,
            float size,
            int color)
    {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length =
                (float)Math.sqrt(dx * dx + dy * dy);

        if (length < 2.0F)
        {
            return;
        }

        float nx = dx / length;
        float ny = dy / length;

        float px = -ny;
        float py = nx;

        float bx = to.x - nx * size;
        float by = to.y - ny * size;

        drawLine(
                to.x,
                to.y,
                bx + px * size * 0.55F,
                by + py * size * 0.55F,
                color
        );

        drawLine(
                to.x,
                to.y,
                bx - px * size * 0.55F,
                by - py * size * 0.55F,
                color
        );
    }

    private void drawProjectedCubeHandle(
            ScreenPoint center,
            double[] world,
            double[] axis,
            float size,
            int color,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            EditorCamera camera)
    {
        ScreenPoint end =
                project(
                        world[0] + axis[0] * size,
                        world[1] + axis[1] * size,
                        world[2] + axis[2] * size,
                        viewportX, viewportY,
                        viewportWidth, viewportHeight, camera
                );

        if (end == null)
        {
            return;
        }

        float half = 5.0F;

        drawRectOutline(
                end.x - half,
                end.y - half,
                half * 2.0F,
                half * 2.0F,
                color
        );
    }

    private void drawProjectedCenter(ScreenPoint center)
    {
        drawCircle(
                center.x,
                center.y,
                4.0F,
                EditorThemeManager.get().getAccentBright(),
                16
        );
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
                || (keyframe == null && this.globalTransformTarget == null)
                || camera == null
                || viewportWidth <= 0
                || viewportHeight <= 0)
        {
            return;
        }

        double[] world =
                getNativeBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        if (world == null)
        {
            world =
                    getBoneWorldPosition(
                            bone,
                            keyframe,
                            recordFrame
                    );
        }

        if (world == null)
        {
            return;
        }

        /*
         * PreviewShaderBridge.bindPreviewFramebuffer() renders the actor
         * into the Preview rectangle inside the Preview FBO. The gizmo
         * must use that exact same OpenGL viewport and aspect ratio.
         */
        ScaledResolution resolution =
                new ScaledResolution(mc);

        int scaleFactor =
                Math.max(1, resolution.getScaleFactor());

        int framebufferWidth =
                Math.max(1, mc.displayWidth);

        int framebufferHeight =
                Math.max(1, mc.displayHeight);

        int glX =
                Math.max(
                        0,
                        viewportX * scaleFactor
                );

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

        /*
         * The Preview may have just been rendered through OptiFine.
         * That render path can leave a GLSL program bound. The gizmo
         * below uses the legacy fixed-function GL11 pipeline, so an
         * active shader program can make all glBegin/glVertex geometry
         * disappear even though the viewport and projection are valid.
         *
         * Explicitly return to the fixed-function pipeline here.
         */
        GL20.glUseProgram(0);

        GL13.glActiveTexture(
                GL13.GL_TEXTURE0
        );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        /*
         * This is the important part of the original BBS approach:
         * the gizmo is real 3D geometry, but depth comparison is
         * disabled for the gizmo pass so it is always visible over
         * the actor.
         */
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GL11.glLineWidth(7.0F);

        GL11.glPushMatrix();

        /*
         * Native adapters already return world-space coordinates.
         * Do not apply another actor/model transform here.
         */
        GL11.glTranslated(
                world[0],
                world[1],
                world[2]
        );

        float size =
                getWorldGizmoSize(
                        camera,
                        world[0],
                        world[1],
                        world[2]
                );

        double[][] axes =
                getNativeBoneWorldAxes(bone);

        if (axes == null)
        {
            axes =
                    getBoneWorldAxes(
                            bone,
                            keyframe,
                            recordFrame
                    );
        }

        if (axes == null)
        {
            axes =
                    new double[][]
                    {
                        {1.0D, 0.0D, 0.0D},
                        {0.0D, 1.0D, 0.0D},
                        {0.0D, 0.0D, 1.0D}
                    };
        }

        if (this.mode == Mode.POSITION)
        {
            drawPositionGizmo3D(
                    size,
                    axes
            );
        }
        else if (this.mode == Mode.ROTATION)
        {
            drawRotationGizmo3D(
                    size,
                    axes
            );
        }
        else
        {
            drawScaleGizmo3D(
                    size,
                    axes
            );
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

    private float[] beginExactChameleonBoneTransform(
            AnimationBone bone)
    {
        if (bone == null ||
                this.previewActor == null ||
                this.previewActor.getMorph() == null)
        {
            return null;
        }

        if (!this.previewActor.getMorph().getClass()
                .getName().endsWith(".ChameleonMorph"))
        {
            return null;
        }

        try
        {
            Object morph =
                    this.previewActor.getMorph();

            Object chameleonModel =
                    morph.getClass()
                            .getMethod("getModel")
                            .invoke(morph);

            if (chameleonModel == null)
            {
                return null;
            }

            Object model =
                    chameleonModel.getClass()
                            .getField("model")
                            .get(chameleonModel);

            Class<?> modelClass =
                    Class.forName(
                            "mchorse.chameleon.lib.data.model.Model"
                    );

            Class<?> rendererClass =
                    Class.forName(
                            "mchorse.chameleon.lib.render.ChameleonRenderer"
                    );

            java.lang.reflect.Method postRender =
                    rendererClass.getMethod(
                            "postRender",
                            modelClass,
                            String.class
                    );

            GL11.glPushMatrix();

            GL11.glTranslated(
                    this.previewActor.posX,
                    this.previewActor.posY,
                    this.previewActor.posZ
            );

            float scale = 1.0F;

            try
            {
                Object value =
                        morph.getClass()
                                .getMethod(
                                        "getScale",
                                        float.class
                                )
                                .invoke(
                                        morph,
                                        0.0F
                                );

                if (value instanceof Number)
                {
                    scale =
                            ((Number) value).floatValue();
                }
            }
            catch (Throwable ignored)
            {
            }

            GL11.glScalef(
                    scale,
                    scale,
                    scale
            );

            GL11.glRotatef(
                    -this.previewActor.renderYawOffset
                            + 180.0F,
                    0.0F,
                    1.0F,
                    0.0F
            );

            Object result =
                    postRender.invoke(
                            null,
                            model,
                            bone.getName()
                    );

            if (result instanceof Boolean &&
                    !((Boolean) result).booleanValue())
            {
                GL11.glPopMatrix();
                return null;
            }

            FloatBuffer buffer =
                    BufferUtils.createFloatBuffer(16);

            GL11.glGetFloat(
                    GL11.GL_MODELVIEW_MATRIX,
                    buffer
            );

            float[] m =
                    new float[16];

            buffer.get(m);

            float sx =
                    (float) Math.sqrt(
                            m[0] * m[0]
                                    + m[1] * m[1]
                                    + m[2] * m[2]
                    );

            float sy =
                    (float) Math.sqrt(
                            m[4] * m[4]
                                    + m[5] * m[5]
                                    + m[6] * m[6]
                    );

            float sz =
                    (float) Math.sqrt(
                            m[8] * m[8]
                                    + m[9] * m[9]
                                    + m[10] * m[10]
                    );

            return new float[]
            {
                m[12],
                m[13],
                m[14],
                Math.max(0.0001F, sx),
                Math.max(0.0001F, sy),
                Math.max(0.0001F, sz)
            };
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[] getExactChameleonBoneWorldPosition(AnimationBone bone, BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null) return null;
        if (!this.previewActor.getMorph().getClass().getName().endsWith(".ChameleonMorph")) return null;
        try
        {
            Object morph = this.previewActor.getMorph();
            Object chameleonModel = morph.getClass().getMethod("getModel").invoke(morph);
            if (chameleonModel == null) return null;
            Object model = chameleonModel.getClass().getField("model").get(chameleonModel);
            Class<?> modelClass = Class.forName("mchorse.chameleon.lib.data.model.Model");
            Class<?> rendererClass = Class.forName("mchorse.chameleon.lib.render.ChameleonRenderer");
            java.lang.reflect.Method postRender = rendererClass.getMethod("postRender", modelClass, String.class);
            GL11.glPushMatrix();
            float scale = 1.0F;
            Object value = morph.getClass().getMethod("getScale", float.class).invoke(morph, 0.0F);
            if (value instanceof Number) scale = ((Number) value).floatValue();
            GL11.glScalef(scale, scale, scale);
            /* Keep the bone matrix local; actor placement is applied below. */
            Object result = postRender.invoke(null, model, bone.getName());
            if (result instanceof Boolean && !((Boolean) result).booleanValue())
            {
                GL11.glPopMatrix();
                return null;
            }
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] matrix = new float[16];
            buffer.get(matrix);
            GL11.glPopMatrix();
            double localX = matrix[12];
            double localY = matrix[13];
            double localZ = matrix[14];

            double actorX = recordFrame != null ? recordFrame.getX() : this.previewActor.posX;
            double actorY = recordFrame != null ? recordFrame.getY() : this.previewActor.posY;
            double actorZ = recordFrame != null ? recordFrame.getZ() : this.previewActor.posZ;
            double yaw = Math.toRadians(180.0D - (recordFrame != null ? recordFrame.getYaw() : this.previewActor.renderYawOffset));
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            return new double[]
            {
                actorX + cos * localX + sin * localZ,
                actorY + localY,
                actorZ - sin * localX + cos * localZ
            };
        }
        catch (Throwable ignored) { return null; }
    }

    /**
     * Reads the real orientation of the rendered native bone.
     */
    private double[][] getNativeBoneWorldAxes(AnimationBone bone)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        double[][] result = getExactChameleonBoneWorldAxes(bone, recordFrame);

        if (result != null)
        {
            return result;
        }

        return getExactBlockbusterBoneWorldAxes(bone);
    }

    private double[][] axesFromMatrix(float[] matrix)
    {
        if (matrix == null || matrix.length < 16)
        {
            return null;
        }

        double[] x = {matrix[0], matrix[1], matrix[2]};
        double[] y = {matrix[4], matrix[5], matrix[6]};
        double[] z = {matrix[8], matrix[9], matrix[10]};

        normalize(x);
        normalize(y);
        normalize(z);

        return new double[][] {x, y, z};
    }

    private double[][] getExactBlockbusterBoneWorldAxes(AnimationBone bone)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        try
        {
            Object morph = this.previewActor.getMorph();

            if (!(morph instanceof mchorse.blockbuster_pack.morphs.CustomMorph))
            {
                return null;
            }

            mchorse.blockbuster_pack.morphs.CustomMorph customMorph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph) morph;

            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(customMorph.getKey());

            if (model == null)
            {
                return null;
            }

            mchorse.blockbuster.client.model.ModelCustomRenderer renderer =
                    model.get(bone.getName());

            if (renderer == null)
            {
                return null;
            }

            int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glTranslated(this.previewActor.posX, this.previewActor.posY, this.previewActor.posZ);
            GL11.glRotatef(-this.previewActor.renderYawOffset + 180.0F, 0.0F, 1.0F, 0.0F);

            mchorse.blockbuster.api.Model sourceModel = model.model;
            float morphScale = customMorph.scale;

            GL11.glScalef(
                    sourceModel.scale[0] * morphScale,
                    sourceModel.scale[1] * morphScale,
                    sourceModel.scale[2] * morphScale
            );

            renderer.postRender(0.0625F);

            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] matrix = new float[16];
            buffer.get(matrix);

            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMatrixMode);

            return axesFromMatrix(matrix);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[][] getExactChameleonBoneWorldAxes(AnimationBone bone, BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        if (!this.previewActor.getMorph().getClass().getName().endsWith(".ChameleonMorph"))
        {
            return null;
        }

        try
        {
            Object morph = this.previewActor.getMorph();
            Object chameleonModel = morph.getClass().getMethod("getModel").invoke(morph);

            if (chameleonModel == null)
            {
                return null;
            }

            Object model = chameleonModel.getClass().getField("model").get(chameleonModel);

            Class<?> modelClass = Class.forName("mchorse.chameleon.lib.data.model.Model");
            Class<?> rendererClass = Class.forName("mchorse.chameleon.lib.render.ChameleonRenderer");

            java.lang.reflect.Method postRender =
                    rendererClass.getMethod("postRender", modelClass, String.class);

            GL11.glPushMatrix();

            float scale = 1.0F;

            try
            {
                Object value = morph.getClass().getMethod("getScale", float.class).invoke(morph, 0.0F);

                if (value instanceof Number)
                {
                    scale = ((Number) value).floatValue();
                }
            }
            catch (Throwable ignored)
            {
            }

            GL11.glScalef(scale, scale, scale);
            /* Actor yaw is applied to the returned local basis below. */

            Object result = postRender.invoke(null, model, bone.getName());

            if (result instanceof Boolean && !((Boolean) result).booleanValue())
            {
                GL11.glPopMatrix();
                return null;
            }

            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] matrix = new float[16];
            buffer.get(matrix);

            GL11.glPopMatrix();

            double[][] localAxes = axesFromMatrix(matrix);
            if (localAxes == null)
            {
                return null;
            }

            double yaw = Math.toRadians(180.0D - (recordFrame != null ? recordFrame.getYaw() : this.previewActor.renderYawOffset));
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double[][] worldAxes = new double[3][3];
            for (int i = 0; i < 3; i++)
            {
                double x = localAxes[i][0];
                double z = localAxes[i][2];
                worldAxes[i][0] = cos * x + sin * z;
                worldAxes[i][1] = localAxes[i][1];
                worldAxes[i][2] = -sin * x + cos * z;
                normalize(worldAxes[i]);
            }

            return worldAxes;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[] getNativeBoneWorldPosition(AnimationBone bone, AnimationKeyframe keyframe, BlockbusterRecordFrame recordFrame)
    {
        double[] result = getExactChameleonBoneWorldPosition(bone, recordFrame);
        if (result != null) return result;
        result = getExactEmoticonsBoneWorldPosition(bone, recordFrame);
        if (result != null) return result;
        result = getExactBlockbusterBoneWorldPosition(bone);
        if (result != null) return result;
        return getBoneWorldPosition(bone, keyframe, recordFrame);
    }
    private double[] getExactBlockbusterBoneWorldPosition(AnimationBone bone)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null) return null;
        try
        {
            Object morph = this.previewActor.getMorph();
            if (!(morph instanceof mchorse.blockbuster_pack.morphs.CustomMorph)) return null;
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph) morph;
            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(customMorph.getKey());
            if (model == null) return null;
            mchorse.blockbuster.client.model.ModelCustomRenderer renderer = model.get(bone.getName());
            if (renderer == null) return null;
            /*
             * postRender() is a real model-space transform operation.
             * It must start from an identity modelview matrix here.
             *
             * The gizmo anchor is queried after the Preview world has
             * rendered, so inheriting the Preview camera matrix would
             * bake camera rotation into matrix[12..14]. draw3D() then
             * applies the camera again and the gizmo can end up completely
             * outside the viewport.
             *
             * The original BBS renderer obtains bone matrices from its
             * model renderer rather than from whatever GL matrix happens
             * to be active at the time of the query.
             */
            int oldMatrixMode =
                    GL11.glGetInteger(GL11.GL_MATRIX_MODE);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glTranslated(this.previewActor.posX, this.previewActor.posY, this.previewActor.posZ);
            GL11.glRotatef(-this.previewActor.renderYawOffset + 180.0F, 0.0F, 1.0F, 0.0F);
            mchorse.blockbuster.api.Model sourceModel = model.model;
            float morphScale = customMorph.scale;
            GL11.glScalef(sourceModel.scale[0] * morphScale, sourceModel.scale[1] * morphScale, sourceModel.scale[2] * morphScale);
            renderer.postRender(0.0625F);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] matrix = new float[16];
            buffer.get(matrix);
            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMatrixMode);
            return new double[] {matrix[12], matrix[13], matrix[14]};
        }
        catch (Throwable ignored) { return null; }
    }

    private double[] getExactEmoticonsBoneWorldPosition(AnimationBone bone, BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null) return null;
        try
        {
            if (!(this.previewActor.getMorph() instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)) return null;
            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                    (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph) this.previewActor.getMorph();
            if (morph.animator == null) return null;
            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone sourceBone =
                    EmoticonsModelAccess.findBone(morph, bone.getName());
            if (sourceBone == null) return null;
            java.lang.reflect.Method method = morph.animator.getClass().getMethod(
                    "calcPosition",
                    net.minecraft.entity.EntityLivingBase.class,
                    mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone.class,
                    float.class, float.class, float.class, float.class);
            Object result = method.invoke(morph.animator, this.previewActor, sourceBone, 0.0F, 0.0F, 0.0F, 0.0F);
            if (!(result instanceof javax.vecmath.Vector4f)) return null;
            javax.vecmath.Vector4f position = (javax.vecmath.Vector4f) result;
            double actorX = recordFrame != null ? recordFrame.getX() : this.previewActor.posX;
            double actorY = recordFrame != null ? recordFrame.getY() : this.previewActor.posY;
            double actorZ = recordFrame != null ? recordFrame.getZ() : this.previewActor.posZ;

            double yaw = Math.toRadians(180.0D - (recordFrame != null ? recordFrame.getYaw() : this.previewActor.renderYawOffset));
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            return new double[]
            {
                actorX + cos * position.x + sin * position.z,
                actorY + position.y,
                actorZ - sin * position.x + cos * position.z
            };
        }
        catch (Throwable ignored) { return null; }
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

    private void drawPositionGizmo3D(float size, double[][] axes)
    {
        if (axes == null) return;
        drawAxisVector3D(axes[0], AXIS_X_COLOR, size, AXIS_X);
        drawAxisVector3D(axes[1], AXIS_Y_COLOR, size, AXIS_Y);
        drawAxisVector3D(axes[2], AXIS_Z_COLOR, size, AXIS_Z);
        drawPlaneVector3D(axes[0], axes[1], size, PLANE_XY);
        drawPlaneVector3D(axes[0], axes[2], size, PLANE_XZ);
        drawPlaneVector3D(axes[1], axes[2], size, PLANE_YZ);
    }

    private void drawRotationGizmo3D(float size, double[][] axes)
    {
        if (axes == null) return;
        drawRingVector3D(axes[1], axes[2], size * 1.05F, AXIS_X_COLOR);
        drawRingVector3D(axes[2], axes[0], size * 1.05F, AXIS_Y_COLOR);
        drawRingVector3D(axes[0], axes[1], size * 1.05F, AXIS_Z_COLOR);
    }

    private void drawScaleGizmo3D(float size, double[][] axes)
    {
        if (axes == null) return;
        drawAxisVector3D(axes[0], AXIS_X_COLOR, size, AXIS_X);
        drawAxisVector3D(axes[1], AXIS_Y_COLOR, size, AXIS_Y);
        drawAxisVector3D(axes[2], AXIS_Z_COLOR, size, AXIS_Z);
        drawCubeHandleVector(axes[0], size, AXIS_X_COLOR);
        drawCubeHandleVector(axes[1], size, AXIS_Y_COLOR);
        drawCubeHandleVector(axes[2], size, AXIS_Z_COLOR);
    }

    private void drawAxisVector3D(double[] axis, int color, float size, int axisId)
    {
        float length = 1.8F * size;
        int finalColor = this.activePart == axisId
                ? EditorThemeManager.get().getAccentBright() : color;
        float ex = (float) (axis[0] * length);
        float ey = (float) (axis[1] * length);
        float ez = (float) (axis[2] * length);
        setColor(finalColor);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3f(0, 0, 0);
        GL11.glVertex3f(ex, ey, ez);
        GL11.glEnd();
        drawArrowHeadVector(axis, ex, ey, ez, 0.28F * size, finalColor);
    }

    private void drawArrowHeadVector(double[] axis, float x, float y, float z, float head, int color)
    {
        double[] reference = Math.abs(axis[1]) < 0.9D
                ? new double[] {0,1,0} : new double[] {1,0,0};
        double[] side = cross(axis, reference); normalize(side);
        double[] up = cross(side, axis); normalize(up);
        double bx = x - axis[0] * head;
        double by = y - axis[1] * head;
        double bz = z - axis[2] * head;
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3f(x,y,z);
        GL11.glVertex3f((float)(bx+side[0]*head*.65D),(float)(by+side[1]*head*.65D),(float)(bz+side[2]*head*.65D));
        GL11.glVertex3f((float)(bx-side[0]*head*.65D),(float)(by-side[1]*head*.65D),(float)(bz-side[2]*head*.65D));
        GL11.glVertex3f(x,y,z);
        GL11.glVertex3f((float)(bx+up[0]*head*.65D),(float)(by+up[1]*head*.65D),(float)(bz+up[2]*head*.65D));
        GL11.glVertex3f((float)(bx-up[0]*head*.65D),(float)(by-up[1]*head*.65D),(float)(bz-up[2]*head*.65D));
        GL11.glEnd();
    }

    private void drawPlaneVector3D(double[] a, double[] b, float size, int planeId)
    {
        float offset=.52F*size, extent=.48F*size;
        int color=this.activePart==planeId ? EditorThemeManager.get().getAccentBright() : 0x99BFC5CA;
        double[] p1=addScaled(a,offset); addScaledInPlace(p1,b,offset);
        double[] p2=addScaled(a,offset+extent); addScaledInPlace(p2,b,offset);
        double[] p3=addScaled(a,offset+extent); addScaledInPlace(p3,b,offset+extent);
        double[] p4=addScaled(a,offset); addScaledInPlace(p4,b,offset+extent);
        setColor(color); GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(p1[0],p1[1],p1[2]); GL11.glVertex3d(p2[0],p2[1],p2[2]);
        GL11.glVertex3d(p3[0],p3[1],p3[2]); GL11.glVertex3d(p4[0],p4[1],p4[2]);
        GL11.glEnd();
    }

    private void drawRingVector3D(double[] a, double[] b, float radius, int color)
    {
        setColor(color); GL11.glBegin(GL11.GL_LINE_LOOP);
        for(int i=0;i<64;i++)
        {
            double angle=Math.PI*2.0D*i/64.0D;
            double c=Math.cos(angle)*radius, d=Math.sin(angle)*radius;
            GL11.glVertex3d(a[0]*c+b[0]*d,a[1]*c+b[1]*d,a[2]*c+b[2]*d);
        }
        GL11.glEnd();
    }

    private void drawCubeHandleVector(double[] axis, float size, int color)
    {
        float center=2.15F*size, h=.16F;
        double[] ref=Math.abs(axis[1])<.9D ? new double[]{0,1,0} : new double[]{1,0,0};
        double[] side=cross(axis,ref); normalize(side);
        double[] up=cross(side,axis); normalize(up);
        double[] c=addScaled(axis,center);
        double[][] p=new double[8][3]; int n=0;
        for(int a=-1;a<=1;a+=2) for(int b=-1;b<=1;b+=2) for(int d=-1;d<=1;d+=2)
        {
            p[n++]=new double[]{c[0]+side[0]*h*a+up[0]*h*b+axis[0]*h*d,c[1]+side[1]*h*a+up[1]*h*b+axis[1]*h*d,c[2]+side[2]*h*a+up[2]*h*b+axis[2]*h*d};
        }
        int[][] e={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
        setColor(color); GL11.glBegin(GL11.GL_LINES);
        for(int[] q:e){GL11.glVertex3d(p[q[0]][0],p[q[0]][1],p[q[0]][2]);GL11.glVertex3d(p[q[1]][0],p[q[1]][1],p[q[1]][2]);}
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
            int mouseX, int mouseY,
            int viewportX, int viewportY, int viewportWidth, int viewportHeight,
            ScreenPoint center, double[] world, float size, EditorCamera camera,
            double[][] axes)
    {
        if (axes == null) return -1;
        float length=2.15F*size;
        if (this.mode == Mode.ROTATION)
        {
            int best=-1; double bestDistance=20.0D;
            for(int axis=0;axis<3;axis++)
            {
                double[] a=axes[(axis+1)%3], b=axes[(axis+2)%3];
                double min=Double.MAX_VALUE;
                for(int i=0;i<64;i++)
                {
                    double angle=Math.PI*2.0D*i/64.0D;
                    double c=Math.cos(angle)*size*1.05D, d=Math.sin(angle)*size*1.05D;
                    ScreenPoint p=project(world[0]+a[0]*c+b[0]*d,world[1]+a[1]*c+b[1]*d,world[2]+a[2]*c+b[2]*d,viewportX,viewportY,viewportWidth,viewportHeight,camera);
                    if(p!=null){double dx=mouseX-p.x,dy=mouseY-p.y;min=Math.min(min,Math.sqrt(dx*dx+dy*dy));}
                }
                if(min<bestDistance){bestDistance=min;best=axis;}
            }
            return best;
        }
        int best=-1; double bestDistance=20.0D;
        for(int axis=0;axis<3;axis++)
        {
            ScreenPoint p=project(world[0]+axes[axis][0]*length,world[1]+axes[axis][1]*length,world[2]+axes[axis][2]*length,viewportX,viewportY,viewportWidth,viewportHeight,camera);
            if(p==null) continue;
            double d=distanceToSegment(mouseX,mouseY,center.x,center.y,p.x,p.y);
            if(d<bestDistance){bestDistance=d;best=axis;}
        }
        float offset=.48F*size, extent=.38F*size;
        int[] ids={PLANE_XY,PLANE_XZ,PLANE_YZ}; int[][] pairs={{0,1},{0,2},{1,2}};
        for(int i=0;i<3;i++)
        {
            double[] p=addScaled(axes[pairs[i][0]],offset+extent*.5D);
            addScaledInPlace(p,axes[pairs[i][1]],offset+extent*.5D);
            ScreenPoint sp=project(world[0]+p[0],world[1]+p[1],world[2]+p[2],viewportX,viewportY,viewportWidth,viewportHeight,camera);
            if(sp!=null){double dx=mouseX-sp.x,dy=mouseY-sp.y;if(dx*dx+dy*dy<=324.0D)return ids[i];}
        }
        return best;
    }

    private double getDataAxisDragAmount(
            int axisId,
            double mouseDX,
            double mouseDY)
    {
        return getAxisDragAmount(axisId, mouseDX, mouseDY)
                * getDataAxisSign(axisId);
    }

    private double getDataAxisDelta(
            int axisId,
            double visualDelta)
    {
        return visualDelta * getDataAxisSign(axisId);
    }

    private double getDataAxisSign(int axisId)
    {
        return 1.0D;
    }

    private double getRotationDragAngle(
            int axisId,
            double mouseDX,
            double mouseDY)
    {
        return getAxisDragAngle(axisId, mouseDX, mouseDY)
                * getDataAxisSign(axisId);
    }

    private double getAxisDragAmount(int axisId,double mouseDX,double mouseDY)
    {
        if(axisId<0||axisId>2)return 0.0D;
        double ux=this.dragScreenAxes[axisId][0], uy=this.dragScreenAxes[axisId][1];
        double scale=Math.max(.0001D,this.dragPixelsPerWorld[axisId]);
        return (mouseDX*ux+mouseDY*uy)/scale*.10D;
    }

    private double getAxisDragAngle(int axisId,double mouseDX,double mouseDY)
    {
        if(axisId<0||axisId>2)return 0.0D;
        return (mouseDX*this.dragScreenAxes[axisId][0]+mouseDY*this.dragScreenAxes[axisId][1])*.5D;
    }

    private double[] getPlaneDragAmount(int axisA,int axisB,double mouseDX,double mouseDY)
    {
        double ax=this.dragScreenAxes[axisA][0]*this.dragPixelsPerWorld[axisA];
        double ay=this.dragScreenAxes[axisA][1]*this.dragPixelsPerWorld[axisA];
        double bx=this.dragScreenAxes[axisB][0]*this.dragPixelsPerWorld[axisB];
        double by=this.dragScreenAxes[axisB][1]*this.dragPixelsPerWorld[axisB];
        double det=ax*by-ay*bx;
        if(Math.abs(det)<.0001D)return new double[]{0,0};
        return new double[]{(mouseDX*by-mouseDY*bx)/det*.10D,(ax*mouseDY-ay*mouseDX)/det*.10D};
    }

    private double[][] getBoneWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null)
        {
            return null;
        }

        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : this.gizmoFrame;

        AnimationTransform pivot =
                this.chameleonCoordinateSpace
                        ? getChameleonWorldPivot(bone, frame)
                        : bone.getWorldPivotAt(frame);

        if (pivot == null)
        {
            return null;
        }

        float rx;
        float ry;
        float rz;

        /*
         * Chameleon keyframes contain LOCAL transforms.
         * The gizmo is displayed in world space, so its visual basis is
         * the selected bone's local basis converted through its parent.
         */
        if (this.chameleonCoordinateSpace)
        {
            AnimationTransform local = bone.getTransformAt(frame);

            if (local == null)
            {
                local = new AnimationTransform();
            }

            rx = local.getRotationX();
            ry = local.getRotationY();
            rz = local.getRotationZ();

            /*
             * Build the visual basis from the actual rotation matrices.
             *
             * The previous implementation converted the composed world
             * matrix to Euler angles and then reconstructed the axes.
             * That loses the exact basis for some X/Y/Z combinations.
             *
             * Chameleon stores this bone transform locally. Therefore:
             *
             *     WORLD_BASIS = PARENT_WORLD_BASIS * LOCAL_BASIS
             *
             * We keep the matrix itself all the way to the gizmo. This
             * makes the displayed axes follow the real local bone
             * orientation instead of falling back to global X/Y/Z.
             */
            float[][] localMatrix =
                    createRotationMatrix(
                            bone.getBaseRotationX() + local.getRotationX(),
                            bone.getBaseRotationY() + local.getRotationY(),
                            bone.getBaseRotationZ() + local.getRotationZ()
                    );

            float[][] worldMatrix =
                    localMatrix;

            AnimationBone parent = bone.getParent();

            if (parent != null)
            {
                float[][] parentWorldMatrix =
                        getWorldRotationMatrix(parent, frame);

                if (parentWorldMatrix != null)
                {
                    worldMatrix =
                            multiplyMatrix(
                                    parentWorldMatrix,
                                    localMatrix
                            );
                }
            }

            float[] worldX =
                    new float[]
                    {
                            worldMatrix[0][0],
                            worldMatrix[1][0],
                            worldMatrix[2][0]
                    };

            float[] worldY =
                    new float[]
                    {
                            worldMatrix[0][1],
                            worldMatrix[1][1],
                            worldMatrix[2][1]
                    };

            float[] worldZ =
                    new float[]
                    {
                            worldMatrix[0][2],
                            worldMatrix[1][2],
                            worldMatrix[2][2]
                    };

            return new double[][]
            {
                transformBoneDirection(worldX, recordFrame),
                transformBoneDirection(worldY, recordFrame),
                transformBoneDirection(worldZ, recordFrame)
            };
        }
        else
        {
            AnimationTransform world = bone.getWorldTransformAt(frame);

            if (world == null)
            {
                world = pivot;
            }

            rx = world.getRotationX();
            ry = world.getRotationY();
            rz = world.getRotationZ();

            /*
             * Body Part bones are rotated by the attachment bone and the
             * Body Part global transform before entering actor space.
             */
            if (this.bodyPartAttachmentBone != null &&
                    (this.bodyPartTarget != null ||
                     this.globalTransformTarget != null))
            {
                AnimationTransform attachment =
                        this.bodyPartAttachmentBone.getWorldPivotAt(frame);

                if (attachment != null)
                {
                    rx += attachment.getRotationX();
                    ry += attachment.getRotationY();
                    rz += attachment.getRotationZ();

                    AnimationTransform global =
                            this.globalTransformTarget != null
                                    ? this.globalTransformTarget
                                    : this.bodyPartTarget.getGlobalTransform();

                    if (global != null)
                    {
                        rx += global.getRotationX();
                        ry += global.getRotationY();
                        rz += global.getRotationZ();
                    }
                }
            }
        }

        return new double[][]
        {
            transformBoneDirection(
                    rotateVector(1, 0, 0, rx, ry, rz),
                    recordFrame
            ),
            transformBoneDirection(
                    rotateVector(0, 1, 0, rx, ry, rz),
                    recordFrame
            ),
            transformBoneDirection(
                    rotateVector(0, 0, 1, rx, ry, rz),
                    recordFrame
            )
        };
    }

    private float[][] getWorldRotationMatrix(
            AnimationBone bone,
            float frame)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationTransform local =
                bone.getTransformAt(frame);

        if (local == null)
        {
            local = new AnimationTransform();
        }

        float[][] localMatrix =
                createRotationMatrix(
                        local.getRotationX(),
                        local.getRotationY(),
                        local.getRotationZ()
                );

        AnimationBone parent =
                bone.getParent();

        if (parent == null)
        {
            return localMatrix;
        }

        float[][] parentMatrix =
                getWorldRotationMatrix(parent, frame);

        if (parentMatrix == null)
        {
            return localMatrix;
        }

        return multiplyMatrix(
                parentMatrix,
                localMatrix
        );
    }

    private float[][] createRotationMatrix(
            float rotationX,
            float rotationY,
            float rotationZ)
    {
        double rx = Math.toRadians(rotationX);
        double ry = Math.toRadians(rotationY);
        double rz = Math.toRadians(rotationZ);

        float cx = (float) Math.cos(rx);
        float sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry);
        float sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz);
        float sz = (float) Math.sin(rz);

        float[][] matrixX =
                new float[][]
                {
                    {1.0F, 0.0F, 0.0F},
                    {0.0F, cx, -sx},
                    {0.0F, sx, cx}
                };

        float[][] matrixY =
                new float[][]
                {
                    {cy, 0.0F, sy},
                    {0.0F, 1.0F, 0.0F},
                    {-sy, 0.0F, cy}
                };

        float[][] matrixZ =
                new float[][]
                {
                    {cz, -sz, 0.0F},
                    {sz, cz, 0.0F},
                    {0.0F, 0.0F, 1.0F}
                };

        return multiplyMatrix(
                multiplyMatrix(matrixZ, matrixY),
                matrixX
        );
    }

    private float[][] multiplyMatrix(
            float[][] a,
            float[][] b)
    {
        float[][] result =
                new float[3][3];

        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                float value = 0.0F;

                for (int i = 0; i < 3; i++)
                {
                    value +=
                            a[row][i]
                                    * b[i][column];
                }

                result[row][column] = value;
            }
        }

        return result;
    }

    private double[] transformBoneDirection(float[] d,BlockbusterRecordFrame recordFrame)
    {
        double x = this.chameleonCoordinateSpace ? d[0] : -d[0];
        double y = d[1];
        double z = this.chameleonCoordinateSpace ? d[2] : -d[2];
        double yaw=Math.toRadians(180.0D-(recordFrame!=null?recordFrame.getYaw():0.0D));
        double c=Math.cos(yaw), s=Math.sin(yaw);
        double wx=c*x+s*z, wz=-s*x+c*z;
        double len=Math.sqrt(wx*wx+y*y+wz*wz);
        if(len<.000001D)return new double[]{0,0,0};
        return new double[]{wx/len,y/len,wz/len};
    }

    private double[] cross(double[] a,double[] b)
    {
        return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
    }

    private void normalize(double[] v)
    {
        double len=Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if(len<.000001D)return;
        v[0]/=len;v[1]/=len;v[2]/=len;
    }

    private double[] addScaled(double[] v,double amount)
    {
        return new double[]{v[0]*amount,v[1]*amount,v[2]*amount};
    }

    private void addScaledInPlace(double[] base,double[] v,double amount)
    {
        base[0]+=v[0]*amount;base[1]+=v[1]*amount;base[2]+=v[2]*amount;
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
                getNativeBoneWorldPosition(
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

        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : this.gizmoFrame;

        /*
         * AnimationBone stores pivots in Blockbuster model pixels.
         * getWorldPivotAt() includes the complete parent hierarchy and
         * the selected keyframe transform.
         */
        AnimationTransform pivot =
                bone.getWorldPivotAt(frame);

        if (pivot == null)
        {
            return null;
        }

        /*
         * Use the exact actor bone resolved by the editor's Body Parts
         * attachment mechanism as the Gizmo anchor.
         */
        if (this.gizmoAttachmentBone != null &&
                (this.bodyPartTarget == null ||
                 this.bodyPartAttachmentBone == null ||
                 bone == this.bodyPartAttachmentBone))
        {
            AnimationTransform attachmentPivot =
                    this.chameleonCoordinateSpace
                            ? getChameleonWorldPivot(this.gizmoAttachmentBone, frame)
                            : this.gizmoAttachmentBone.getWorldPivotAt(frame);

            if (attachmentPivot != null)
            {
                pivot = attachmentPivot;
            }
        }

        /*
         * Body Part model bones are local to the attached model.
         * Resolve them through the actor attachment bone before
         * converting model pixels into world blocks.
         */
        if (this.bodyPartTarget != null &&
                this.bodyPartAttachmentBone != null &&
                bone != this.bodyPartAttachmentBone)
        {
            AnimationTransform attachment =
                    this.chameleonCoordinateSpace
                            ? getChameleonWorldPivot(this.bodyPartAttachmentBone, frame)
                            : this.bodyPartAttachmentBone.getWorldPivotAt(frame);

            if (attachment == null)
            {
                return null;
            }

            double x = pivot.getPositionX();
            double y = pivot.getPositionY();
            double z = pivot.getPositionZ();

            AnimationTransform global =
                    this.bodyPartTarget.getGlobalTransform();

            if (global != null)
            {
                x *= global.getScaleX();
                y *= global.getScaleY();
                z *= global.getScaleZ();

                float[] p = rotateVector(
                        (float) x, (float) y, (float) z,
                        global.getRotationX(),
                        global.getRotationY(),
                        global.getRotationZ()
                );

                x = p[0] + global.getPositionX();
                y = p[1] + global.getPositionY();
                z = p[2] + global.getPositionZ();
            }

            float[] p = rotateVector(
                    (float) x, (float) y, (float) z,
                    attachment.getRotationX(),
                    attachment.getRotationY(),
                    attachment.getRotationZ()
            );

            double[] base =
                    getWorldFromPivot(
                            attachment,
                            recordFrame
                    );

            double[] offset =
                    transformBoneDirection(
                            p,
                            recordFrame
                    );

            return new double[]
            {
                base[0] + offset[0] / 16.0D,
                base[1] + offset[1] / 16.0D,
                base[2] + offset[2] / 16.0D
            };
        }

        /*
         * Global Body Part transform is located at the actor attachment
         * pivot and translated in that attachment's local space.
         */
        if (this.globalTransformTarget != null &&
                this.bodyPartAttachmentBone != null)
        {
            AnimationTransform attachment =
                    this.bodyPartAttachmentBone.getWorldPivotAt(frame);

            if (attachment == null)
            {
                return null;
            }

            float[] p = rotateVector(
                    this.globalTransformTarget.getPositionX(),
                    this.globalTransformTarget.getPositionY(),
                    this.globalTransformTarget.getPositionZ(),
                    attachment.getRotationX(),
                    attachment.getRotationY(),
                    attachment.getRotationZ()
            );

            double[] base =
                    getWorldFromPivot(
                            attachment,
                            recordFrame
                    );

            double[] offset =
                    transformBoneDirection(
                            p,
                            recordFrame
                    );

            return new double[]
            {
                base[0] + offset[0] / 16.0D,
                base[1] + offset[1] / 16.0D,
                base[2] + offset[2] / 16.0D
            };
        }

        return getWorldFromPivot(
                pivot,
                recordFrame
        );
    }

    /**
     * Reconstructs the bone pivot exactly in the coordinate convention
     * used by Chameleon's MatrixStack.translateBone().
     *
     * Chameleon does NOT apply the animation translation as
     * (+x,+y,+z). Its renderer applies:
     *   x = -deltaX, y = +deltaY, z = +deltaZ.
     * The old Gizmo path used AnimationBone.getWorldPivotAt(), which added
     * deltaX directly and therefore put the Gizmo away from the real pivot.
     */
    private AnimationTransform getChameleonWorldPivot(
            AnimationBone bone,
            float frame)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationTransform animation = bone.getTransformAt(frame);
        if (animation == null)
        {
            animation = new AnimationTransform();
        }

        AnimationTransform local = new AnimationTransform();
        local.setPosition(
                bone.getLocalX() - animation.getPositionX(),
                bone.getLocalY() + animation.getPositionY(),
                bone.getLocalZ() + animation.getPositionZ()
        );
        local.setRotation(
                bone.getBaseRotationX() + animation.getRotationX(),
                bone.getBaseRotationY() + animation.getRotationY(),
                bone.getBaseRotationZ() + animation.getRotationZ()
        );
        local.setScale(
                animation.getScaleX(),
                animation.getScaleY(),
                animation.getScaleZ()
        );

        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return local;
        }

        AnimationTransform parentWorld =
                getChameleonWorldPivot(parent, frame);

        return parentWorld == null
                ? local
                : parentWorld.combine(local);
    }

    private double[] getWorldFromPivot(
            AnimationTransform pivot,
            BlockbusterRecordFrame recordFrame)
    {
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

        double modelX = pivot.getPositionX() / 16.0D;
        double modelY = pivot.getPositionY() / 16.0D;
        double modelZ = pivot.getPositionZ() / 16.0D;

        double transformedX =
                this.chameleonCoordinateSpace ? modelX : -modelX;

        double transformedY =
                this.chameleonCoordinateSpace
                        ? modelY
                        : -modelY + 1.501D;

        double transformedZ =
                this.chameleonCoordinateSpace ? modelZ : -modelZ;

        double yaw =
                Math.toRadians(
                        180.0D
                                - (
                                        recordFrame != null
                                                ? recordFrame.getYaw()
                                                : 0.0D
                                )
                );

        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        double rotatedX =
                cos * transformedX
                        + sin * transformedZ;

        double rotatedZ =
                -sin * transformedX
                        + cos * transformedZ;

        return new double[]
        {
            recordX + rotatedX,
            recordY + transformedY,
            recordZ + rotatedZ
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

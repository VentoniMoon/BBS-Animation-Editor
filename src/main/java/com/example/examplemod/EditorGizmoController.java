package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final int ACTIVE_RING_COLOR = 0xFFFFD84A;

    private Mode mode = Mode.POSITION;

    private int activePart = -1;
    private boolean dragging;
    /* Rotation is accumulated as an unwrapped angle around the projected pivot. */
    private double rotationDragCenterX;
    private double rotationDragCenterY;
    private double rotationDragLastAngle;
    private double rotationDragAccumulatedDegrees;
    private boolean rotationDragAngleValid;
    /* Screen-space basis of the selected ring, matching drawScreenRing(). */
    private double rotationBasisAX = 1.0D;
    private double rotationBasisAY = 0.0D;
    private double rotationBasisBX = 0.0D;
    private double rotationBasisBY = 1.0D;

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
    private AnimationBone activeBone;

    private double[] dragAxisX = new double[] {1.0D, 0.0D, 0.0D};
    private double[] dragAxisY = new double[] {0.0D, 1.0D, 0.0D};
    private double[] dragAxisZ = new double[] {0.0D, 0.0D, 1.0D};
    private double[][] dragScreenAxes = new double[][]
    {
        {1.0D, 0.0D}, {0.0D, -1.0D}, {1.0D, 0.0D}
    };
    private double[] dragPixelsPerWorld = new double[] {1.0D, 1.0D, 1.0D};

    private BodyPartModelData bodyPartTarget;
    private mchorse.metamorph.api.morphs.AbstractMorph bodyPartRuntimeMorph;
    private final Set<String> bodyPartMatrixDiagnosticKeys = new HashSet<String>();
    private long lastBlockbusterRotationPoseDiagnosticMillis;
    private long lastBlockbusterWorldAxesDiagnosticMillis;
    private AnimationBone bodyPartAttachmentBone;
    private AnimationBone gizmoAttachmentBone;
    private AnimationTransform globalTransformTarget;
    private int gizmoFrame;
    private boolean chameleonCoordinateSpace;
    private EntityActor previewActor;

    public void setBodyPartRuntimeMorph(
            mchorse.metamorph.api.morphs.AbstractMorph morph)
    {
        this.bodyPartRuntimeMorph = morph;
    }

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
        this.activeBone = null;
        this.activeKeyframe = null;
        this.rotationDragAngleValid = false;
        this.rotationDragAccumulatedDegrees = 0.0D;
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
                || (keyframe == null && this.globalTransformTarget == null))
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
                        gizmoWorld[2],
                        viewportHeight
                );

        double[][] axes =
                getGizmoWorldAxes(
                        bone,
                        keyframe,
                        recordFrame
                );
        double[][] displayAxes = getRotationDisplayAxes(axes, bone);

        int hit =
                hitTest(
                        mouseX, mouseY,
                        viewportX, viewportY, viewportWidth, viewportHeight,
                        center, gizmoWorld, gizmoSize, camera,
                        displayAxes
                );

        if (hit < 0)
        {
            return false;
        }

        this.activePart = hit;
        this.dragging = true;
        this.activeKeyframe = keyframe;
        this.activeBone = bone;
        this.rotationDragAngleValid = false;
        this.rotationDragAccumulatedDegrees = 0.0D;
        if (this.mode == Mode.ROTATION)
        {
            this.rotationDragCenterX = center.x;
            this.rotationDragCenterY = center.y;
            boolean accurateChildRingRotation =
                    isBodyPartInternalBoneTarget(bone)
                            && (isBlockbusterBodyPartTarget()
                                || this.bodyPartRuntimeMorph
                                instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph);

            if (accurateChildRingRotation)
            {
                /*
                 * The evaluated BOBJ basis of Emoticons limbs can tilt the
                 * projected Z ring into a steep perspective ellipse (notably
                 * low_left_arm, low_right_arm and low_leg_*). Interpreting
                 * mouse motion through unit-length 2D axes loses the actual
                 * ring projection and can reverse its apparent drag direction.
                 *
                 * Use the same 3D projected-radius basis already used for
                 * Blockbuster. This changes only mouse-angle measurement,
                 * never bone-specific signs, key writeback, or visual axes.
                 */
                setBlockbusterBodyPartRotationDragBasis(
                        this.activePart,
                        gizmoWorld,
                        displayAxes,
                        gizmoSize,
                        viewportX, viewportY, viewportWidth, viewportHeight,
                        center,
                        camera
                );
            }
            else
            {
                setRotationDragBasis(
                        this.activePart,
                        gizmoWorld,
                        displayAxes,
                        viewportX, viewportY, viewportWidth, viewportHeight,
                        center,
                        camera
                );
            }

            double startDX = mouseX - center.x;
            double startDY = mouseY - center.y;
            if (startDX * startDX + startDY * startDY >= 64.0D)
            {
                double[] ringPoint = getRotationRingCoordinates(startDX, startDY);
                this.rotationDragLastAngle = Math.atan2(ringPoint[1], ringPoint[0]);
                this.rotationDragAngleValid = true;
            }
        }

        if (axes != null)
        {
            /*
             * Keep the data-space axes unchanged for bone edits, but use the
             * displayed directions when mapping mouse movement to screen space.
             * This makes Blockbuster's flipped Y/Z handles clickable and usable.
             */
            this.dragAxisX = axes[0].clone();
            this.dragAxisY = axes[1].clone();
            this.dragAxisZ = axes[2].clone();

            double[][] captured = new double[][]
            {
                this.dragAxisX, this.dragAxisY, this.dragAxisZ
            };
            double[][] screenAxes = displayAxes != null ? displayAxes : captured;

            for (int axis = 0; axis < 3; axis++)
            {
                ScreenPoint endpoint = project(
                        gizmoWorld[0] + screenAxes[axis][0],
                        gizmoWorld[1] + screenAxes[axis][1],
                        gizmoWorld[2] + screenAxes[axis][2],
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

            if (isBodyPartInternalBoneTarget(this.activeBone))
            {
                /*
                 * A local gizmo axis follows the selected bone's rotation,
                 * but the renderer applies the bone's translation before
                 * that bone's own rotation. Convert the visible-axis delta
                 * back into the key's parent-space translation coordinates.
                 */
                double[] delta = new double[] {0.0D, 0.0D, 0.0D};

                if (this.activePart == AXIS_X)
                {
                    addBodyPartLocalAxisDelta(delta, AXIS_X,
                            getDataAxisDragAmount(AXIS_X, mouseDX, mouseDY));
                }
                else if (this.activePart == AXIS_Y)
                {
                    addBodyPartLocalAxisDelta(delta, AXIS_Y,
                            getDataAxisDragAmount(AXIS_Y, mouseDX, mouseDY));
                }
                else if (this.activePart == AXIS_Z)
                {
                    addBodyPartLocalAxisDelta(delta, AXIS_Z,
                            getDataAxisDragAmount(AXIS_Z, mouseDX, mouseDY));
                }
                else if (this.activePart == PLANE_XY)
                {
                    double[] d = getPlaneDragAmount(AXIS_X, AXIS_Y, mouseDX, mouseDY);
                    addBodyPartLocalAxisDelta(delta, AXIS_X, getDataAxisDelta(AXIS_X, d[0]));
                    addBodyPartLocalAxisDelta(delta, AXIS_Y, getDataAxisDelta(AXIS_Y, d[1]));
                }
                else if (this.activePart == PLANE_XZ)
                {
                    double[] d = getPlaneDragAmount(AXIS_X, AXIS_Z, mouseDX, mouseDY);
                    addBodyPartLocalAxisDelta(delta, AXIS_X, getDataAxisDelta(AXIS_X, d[0]));
                    addBodyPartLocalAxisDelta(delta, AXIS_Z, getDataAxisDelta(AXIS_Z, d[1]));
                }
                else if (this.activePart == PLANE_YZ)
                {
                    double[] d = getPlaneDragAmount(AXIS_Y, AXIS_Z, mouseDX, mouseDY);
                    addBodyPartLocalAxisDelta(delta, AXIS_Y, getDataAxisDelta(AXIS_Y, d[0]));
                    addBodyPartLocalAxisDelta(delta, AXIS_Z, getDataAxisDelta(AXIS_Z, d[1]));
                }

                transform.setPosition(
                        px + (float) delta[0],
                        py + (float) delta[1],
                        pz + (float) delta[2]
                );
            }
            else
            {
                if (this.activePart == AXIS_X)
                {
                    double delta = getDataAxisDragAmount(AXIS_X, mouseDX, mouseDY);
                    px += (float) getBodyPartPositionDelta(AXIS_X, delta);
                }
                else if (this.activePart == AXIS_Y)
                {
                    double delta = getDataAxisDragAmount(AXIS_Y, mouseDX, mouseDY);
                    py += (float) getBodyPartPositionDelta(AXIS_Y, delta);
                }
                else if (this.activePart == AXIS_Z)
                {
                    double delta = getDataAxisDragAmount(AXIS_Z, mouseDX, mouseDY);
                    pz += (float) getBodyPartPositionDelta(AXIS_Z, delta);
                }
                else if (this.activePart == PLANE_XY)
                {
                    double[] d = getPlaneDragAmount(AXIS_X, AXIS_Y, mouseDX, mouseDY);
                    px += (float) getBodyPartPositionDelta(AXIS_X, getDataAxisDelta(AXIS_X, d[0]));
                    py += (float) getBodyPartPositionDelta(AXIS_Y, getDataAxisDelta(AXIS_Y, d[1]));
                }
                else if (this.activePart == PLANE_XZ)
                {
                    double[] d = getPlaneDragAmount(AXIS_X, AXIS_Z, mouseDX, mouseDY);
                    px += (float) getBodyPartPositionDelta(AXIS_X, getDataAxisDelta(AXIS_X, d[0]));
                    pz += (float) getBodyPartPositionDelta(AXIS_Z, getDataAxisDelta(AXIS_Z, d[1]));
                }
                else if (this.activePart == PLANE_YZ)
                {
                    double[] d = getPlaneDragAmount(AXIS_Y, AXIS_Z, mouseDX, mouseDY);
                    py += (float) getBodyPartPositionDelta(AXIS_Y, getDataAxisDelta(AXIS_Y, d[0]));
                    pz += (float) getBodyPartPositionDelta(AXIS_Z, getDataAxisDelta(AXIS_Z, d[1]));
                }

                transform.setPosition(px, py, pz);
            }
        }
        else if (this.mode == Mode.ROTATION)
        {
            if (isBodyPartInternalBoneTarget(this.activeBone)
                    && isBlockbusterBodyPartTarget())
            {
                applyBlockbusterBodyPartRotation(
                        transform,
                        this.activePart,
                        mouseX,
                        mouseY
                );
            }
            else
            {
                float amount = (float) getRotationDragAngle(
                        this.activePart, mouseX, mouseY
                );

                if (this.bodyPartTarget != null || this.globalTransformTarget != null)
                {
                    applyBodyPartLocalRotationDelta(
                            transform,
                            this.activeBone,
                            this.activePart,
                            amount
                    );
                }
                else if (this.activePart == AXIS_X)
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
        this.activeBone = null;
        this.globalTransformTarget = null;
        this.rotationDragAngleValid = false;
        this.rotationDragAccumulatedDegrees = 0.0D;

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
                        world[2],
                        viewportHeight
                );

        double[][] axes =
                getGizmoWorldAxes(
                        bone,
                        keyframe,
                        recordFrame
                );

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

        /*
         * Position keeps its validated data-space basis. Rotation uses a
         * separate display basis for Blockbuster Body Parts so the displayed
         * X/Z rings follow the mouse convention without changing the bone
         * rotation result. The paired drag-angle conversion is handled by
         * the same basis used for drawing and hit testing.
         */
        double[][] displayAxes = getRotationDisplayAxes(axes, bone);

        /*
         * Rotation rings deliberately use the live native bone basis every
         * frame. As the selected key changes, all three rings follow the new
         * orientation instead of freezing at the drag-start pose.
         */

        if (this.mode == Mode.POSITION)
        {
            drawPositionGizmo3D(
                    size,
                    displayAxes
            );
        }
        else if (this.mode == Mode.ROTATION)
        {
            drawRotationGizmo3D(
                    size,
                    displayAxes
            );
        }
        else
        {
            drawScaleGizmo3D(
                    size,
                    displayAxes
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
        if (bone == null || !isChameleonMorph())
        {
            return null;
        }

        float[] matrix = getExactChameleonBoneMatrix(bone.getName());

        if (matrix == null || !isFinite(matrix[12]) || !isFinite(matrix[13]) || !isFinite(matrix[14]))
        {
            return null;
        }

        return new double[] {matrix[12], matrix[13], matrix[14]};
    }

    /**
     * Reconstruct the exact root transform used by ChameleonMorph.render(),
     * then ask Chameleon's own renderer to evaluate the requested bone.
     *
     * ChameleonRenderer.postRender() multiplies its private MatrixStack onto
     * the current OpenGL model-view matrix. The matrix must therefore start
     * from identity; inheriting the Preview camera matrix and then adding the
     * actor position/yaw a second time produces a displaced gizmo.
     */
    private float[] getExactChameleonBoneMatrix(String boneName)
    {
        if (boneName == null || !isChameleonMorph())
        {
            return null;
        }

        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean pushed = false;

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
            java.lang.reflect.Method postRender = rendererClass.getMethod("postRender", modelClass, String.class);

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
                /* A scale of one matches Chameleon's default morph scale. */
            }

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushed = true;
            GL11.glLoadIdentity();

            /* Match ChameleonMorph.render(): translate, scale, then rotate. */
            GL11.glTranslated(
                    this.previewActor.posX,
                    this.previewActor.posY,
                    this.previewActor.posZ
            );
            GL11.glScalef(scale, scale, scale);
            GL11.glRotatef(
                    -this.previewActor.renderYawOffset + 180.0F,
                    0.0F,
                    1.0F,
                    0.0F
            );

            Object result = postRender.invoke(null, model, boneName);

            if (result instanceof Boolean && !((Boolean) result).booleanValue())
            {
                return null;
            }

            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);

            float[] matrix = new float[16];
            buffer.get(matrix);

            return matrix;
        }
        catch (Throwable ignored)
        {
            return null;
        }
        finally
        {
            if (pushed)
            {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }

            GL11.glMatrixMode(oldMatrixMode);
        }
    }

    private boolean isChameleonMorph()
    {
        return this.previewActor != null
                && this.previewActor.getMorph() != null
                && this.previewActor.getMorph().getClass().getName().endsWith(".ChameleonMorph");
    }

    /**
     * Reads the real orientation of the rendered native bone.
     */
    private double[][] getExactBlockbusterBoneTranslationWorldAxes(
            AnimationBone bone,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null ||
                this.previewActor == null ||
                !(this.previewActor.getMorph() instanceof mchorse.blockbuster_pack.morphs.CustomMorph))
        {
            return null;
        }

        try
        {
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph) this.previewActor.getMorph();

            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(
                            customMorph.getKey()
                    );

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

            /*
             * Blockbuster translates a limb in its parent's coordinate
             * system, BEFORE applying the limb's own rotation. Therefore
             * Position handles must use the parent's actual rendered basis.
             *
             * Do not use getWorldTransformation() here. That cache is
             * camera-dependent in the editor preview because the preview
             * uses its own EditorCamera rather than Minecraft's normal
             * camera matrix.
             */
            mchorse.blockbuster.client.model.ModelCustomRenderer basisRenderer =
                    renderer.parent != null ? renderer.parent : renderer;

            double[][] axes =
                    getBlockbusterRendererBasis(
                            model,
                            customMorph,
                            basisRenderer
                    );

            if (axes == null)
            {
                return null;
            }

            /*
             * A root limb has no parent. Its translation is applied directly
             * in the actor/model basis, so remove the root limb's own
             * rotation from that basis. For a root renderer postRender()
             * already includes its rotation; reconstruct the actor basis
             * explicitly in that case.
             */
            if (renderer.parent == null)
            {
                /*
                 * Root renderer has no parent matrix. Reconstruct the
                 * actor basis from Blockbuster's actual GL yaw convention.
                 * AnimationTransform +X maps to renderer +X, while +Y/+Z
                 * map to renderer -Y/-Z in ModelCustomRenderer.applyTransform.
                 */
                double yaw = Math.toRadians(
                        -this.previewActor.renderYawOffset
                );
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);

                double[] x = new double[] {cos, 0.0D, -sin};
                double[] y = new double[] {0.0D, 1.0D, 0.0D};
                double[] z = new double[] {sin, 0.0D, cos};

                /*
                 * Keep the actor basis in the same convention as the native
                 * renderer. Do not apply a second horizontal flip here:
                 * the renderer yaw already defines the preview orientation.
                 */

                normalize(x);
                normalize(y);
                normalize(z);

                return new double[][] {x, y, z};
            }

            /*
             * Keep the original actor yaw used by the bone anchor. Do not
             * negate the X or Z basis here: those directions must retain
             * their established behavior. Correct only the complete Y axis
             * for Blockbuster's inverted local translation convention.
             */
            for (int component = 0; component < 3; component++)
            {
                axes[1][component] = -axes[1][component];
                axes[2][component] = -axes[2][component];
            }

            normalize(axes[0]);
            normalize(axes[1]);
            normalize(axes[2]);

            return axes;
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[][] getBlockbusterRendererBasis(
            mchorse.blockbuster.client.model.ModelCustom model,
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph,
            mchorse.blockbuster.client.model.ModelCustomRenderer renderer)
    {
        if (model == null || customMorph == null || renderer == null)
        {
            return null;
        }

        int oldMatrixMode =
                GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        try
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glTranslated(
                    this.previewActor.posX,
                    this.previewActor.posY,
                    this.previewActor.posZ
            );

            GL11.glRotatef(
                    -this.previewActor.renderYawOffset + 180.0F,
                    0.0F,
                    1.0F,
                    0.0F
            );

            mchorse.blockbuster.api.Model sourceModel = model.model;

            GL11.glScalef(
                    sourceModel.scale[0] * customMorph.scale,
                    sourceModel.scale[1] * customMorph.scale,
                    sourceModel.scale[2] * customMorph.scale
            );

            /*
             * Match RenderLivingBase.renderModel(): Blockbuster mirrors the
             * model on X/Y and shifts it down by 1.501 before evaluating the
             * limb hierarchy. Without this outer transform the gizmo's X/Y
             * directions disagree with the rendered model and its pivot is
             * vertically/horizontally displaced.
             */
            GL11.glScalef(-1.0F, -1.0F, 1.0F);
            GL11.glTranslatef(0.0F, -1.501F, 0.0F);

            renderer.postRender(0.0625F);

            FloatBuffer buffer =
                    BufferUtils.createFloatBuffer(16);

            GL11.glGetFloat(
                    GL11.GL_MODELVIEW_MATRIX,
                    buffer
            );

            float[] matrix = new float[16];
            buffer.get(matrix);

            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMatrixMode);

            double[] x =
                    new double[]
                    {
                        matrix[0],
                        matrix[1],
                        matrix[2]
                    };

            double[] y =
                    new double[]
                    {
                        matrix[4],
                        matrix[5],
                        matrix[6]
                    };

            double[] z =
                    new double[]
                    {
                        matrix[8],
                        matrix[9],
                        matrix[10]
                    };

            normalize(x);
            normalize(y);
            normalize(z);

            /*
             * Keep the rendered Blockbuster basis untouched here.
             * The native renderer already gives us the actual local
             * orientation of the parent/bone. Coordinate conversion for
             * editor translation is handled separately below.
             */
            return new double[][] {x, y, z};
        }
        catch (Throwable ignored)
        {
            GL11.glMatrixMode(oldMatrixMode);
            return null;
        }
    }

    private boolean isFinite(double value)
    {
        return !Double.isNaN(value) &&
                !Double.isInfinite(value);
    }

    private double[][] getNativeBoneWorldAxes(AnimationBone bone, BlockbusterRecordFrame recordFrame)
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

        /*
         * Rotation/scale need the fully evaluated Emoticons bone matrix.
         * The translation adapter intentionally uses parentBone.mat *
         * relBoneMat because Emoticons applies editable translation before
         * the selected bone's own rotation. Reusing that parent-space basis
         * here makes the rotation rings stay aligned to the pre-rotation
         * orientation even while the selected bone itself rotates.
         */
        result = getExactEmoticonsBoneWorldAxes(bone, recordFrame);

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
        if (bone == null ||
                this.previewActor == null ||
                !(this.previewActor.getMorph() instanceof mchorse.blockbuster_pack.morphs.CustomMorph))
        {
            return null;
        }

        try
        {
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph) this.previewActor.getMorph();

            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(
                            customMorph.getKey()
                    );

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

            /*
             * postRender() gives us the exact same parent/rotation/scale
             * hierarchy as the rendered Blockbuster limb.  Unlike
             * worldTransformation, this calculation is independent of
             * Minecraft's camera matrix and therefore remains stable when
             * the editor's custom camera rotates.
             */
            return getBlockbusterRendererBasis(
                    model,
                    customMorph,
                    renderer
            );
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[][] getExactChameleonBoneWorldAxes(AnimationBone bone, BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || !isChameleonMorph())
        {
            return null;
        }

        float[] matrix = getExactChameleonBoneMatrix(bone.getName());

        return axesFromMatrix(matrix);
    }

    /**
     * Chameleon applies a bone's translation before its own rotation.
     * Position handles must therefore follow the parent's basis. Its
     * translateBone() also negates the X translation delta, while Y and Z
     * retain their signs; encode that convention in the visible X handle
     * instead of trying to compensate again during dragging.
     */
    private double[][] getExactChameleonBoneTranslationWorldAxes(
            AnimationBone bone,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || !isChameleonMorph())
        {
            return null;
        }

        double[][] axes;
        AnimationBone parent = bone.getParent();

        if (parent != null)
        {
            axes = getExactChameleonBoneWorldAxes(parent, recordFrame);
        }
        else
        {
            double yaw = Math.toRadians(-this.previewActor.renderYawOffset + 180.0D);
            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            axes = new double[][]
            {
                {cos, 0.0D, -sin},
                {0.0D, 1.0D, 0.0D},
                {sin, 0.0D, cos}
            };
        }

        if (axes == null)
        {
            return null;
        }

        axes[0][0] = -axes[0][0];
        axes[0][1] = -axes[0][1];
        axes[0][2] = -axes[0][2];

        normalize(axes[0]);
        normalize(axes[1]);
        normalize(axes[2]);

        return axes;
    }

    private double[] getGizmoWorldPosition(
            AnimationBone bone,
            AnimationKeyframe keyframe,
            BlockbusterRecordFrame recordFrame)
    {
        /*
         * Body Parts have their own transform hierarchy.  In particular,
         * an internal bone may have the same name as an actor bone, but it
         * must never borrow that actor bone's native render pivot.
         *
         * Resolve Body Parts through the editor hierarchy first so both
         * the model root gizmo and its internal-bone gizmos follow the
         * selected model's global transform and attachment point.
         */
        if ((this.bodyPartTarget != null &&
             bone != this.bodyPartAttachmentBone) ||
                (this.bodyPartAttachmentBone != null &&
                 this.globalTransformTarget != null))
        {
            return getBoneWorldPosition(
                    bone,
                    keyframe,
                    recordFrame
            );
        }

        /*
         * For ordinary Pose editing, keep using the model engine's
         * evaluated bone matrix whenever available.  This avoids changing
         * the already-working Blockbuster, Emoticons and Chameleon paths.
         */
        if (isChameleonMorph())
        {
            double[] nativeWorld =
                    getExactChameleonBoneWorldPosition(bone, recordFrame);

            if (nativeWorld != null)
            {
                return nativeWorld;
            }
        }

        if (isBlockbusterCustomMorph())
        {
            double[] nativeWorld =
                    getExactBlockbusterBoneWorldPosition(bone);

            if (nativeWorld != null)
            {
                return nativeWorld;
            }
        }

        double[] world =
                getBoneWorldPosition(
                        bone,
                        keyframe,
                        recordFrame
                );

        if (world != null)
        {
            return world;
        }

        return getNativeBoneWorldPosition(
                bone,
                keyframe,
                recordFrame
        );
    }

    private double[] getNativeBoneWorldPosition(AnimationBone bone, AnimationKeyframe keyframe, BlockbusterRecordFrame recordFrame)
    {
        /*
         * Body Parts use a separate transform hierarchy from the actor.
         * Always resolve their gizmo pivot through that hierarchy before
         * consulting native actor-render matrices. Otherwise a same-named
         * actor bone (or the untransformed attachment pivot) can win and
         * make the gizmo stay behind while the added model moves.
         */
        if ((this.bodyPartTarget != null &&
             bone != this.bodyPartAttachmentBone) ||
                (this.bodyPartAttachmentBone != null &&
                 this.globalTransformTarget != null))
        {
            return getBoneWorldPosition(
                    bone,
                    keyframe,
                    recordFrame
            );
        }

        double[] result = getExactChameleonBoneWorldPosition(bone, recordFrame);
        if (result != null) return result;
        result = getExactEmoticonsBoneWorldPosition(bone, recordFrame);
        if (result != null) return result;

        /*
         * As with Emoticons, prefer the engine's evaluated bone transform.
         * The renderer's postRender() walks the real Blockbuster hierarchy
         * and avoids a second, potentially mismatched transform calculation.
         */
        result = getExactBlockbusterBoneWorldPosition(bone);
        if (result != null) return result;
        return getBoneWorldPosition(bone, keyframe, recordFrame);
    }
    private double[] getExactBlockbusterBoneWorldPosition(AnimationBone bone)
    {
        if (bone == null ||
                this.previewActor == null ||
                this.previewActor.getMorph() == null)
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
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(
                            customMorph.getKey()
                    );

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

            /*
             * Do not use ModelCustomRenderer.worldTransformation here.
             * That cache is maintained by Blockbuster's render pipeline and
             * is not guaranteed to contain a usable value when the editor
             * asks for the Gizmo anchor.  In particular, a freshly created
             * renderer starts with an identity matrix.
             *
             * postRender() is the same hierarchy traversal Blockbuster uses
             * for the actual limb: parent -> rotationPoint -> rotation ->
             * scale.  We reproduce only the actor/model root transform
             * before calling it, so the resulting translation is the real
             * rendered bone pivot.
             */
            int oldMatrixMode =
                    GL11.glGetInteger(GL11.GL_MATRIX_MODE);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glTranslated(
                    this.previewActor.posX,
                    this.previewActor.posY,
                    this.previewActor.posZ
            );

            GL11.glRotatef(
                    -this.previewActor.renderYawOffset + 180.0F,
                    0.0F,
                    1.0F,
                    0.0F
            );

            mchorse.blockbuster.api.Model sourceModel =
                    model.model;

            float morphScale =
                    customMorph.scale;

            GL11.glScalef(
                    sourceModel.scale[0] * morphScale,
                    sourceModel.scale[1] * morphScale,
                    sourceModel.scale[2] * morphScale
            );

            /*
             * Match RenderLivingBase.renderModel(): Blockbuster mirrors the
             * model on X/Y and shifts it down by 1.501 before evaluating the
             * limb hierarchy. Without this outer transform the gizmo's X/Y
             * directions disagree with the rendered model and its pivot is
             * vertically/horizontally displaced.
             */
            GL11.glScalef(-1.0F, -1.0F, 1.0F);
            GL11.glTranslatef(0.0F, -1.501F, 0.0F);

            renderer.postRender(0.0625F);

            FloatBuffer buffer =
                    BufferUtils.createFloatBuffer(16);

            GL11.glGetFloat(
                    GL11.GL_MODELVIEW_MATRIX,
                    buffer
            );

            float[] matrix =
                    new float[16];

            buffer.get(matrix);

            GL11.glPopMatrix();
            GL11.glMatrixMode(oldMatrixMode);

            if (!isFinite(matrix[12]) ||
                    !isFinite(matrix[13]) ||
                    !isFinite(matrix[14]))
            {
                return null;
            }

            return new double[]
            {
                matrix[12],
                matrix[13],
                matrix[14]
            };
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private boolean isIdentityMatrix(javax.vecmath.Matrix4d matrix)
    {
        if (matrix == null)
        {
            return true;
        }

        return Math.abs(matrix.m00 - 1.0D) < 0.000001D
                && Math.abs(matrix.m11 - 1.0D) < 0.000001D
                && Math.abs(matrix.m22 - 1.0D) < 0.000001D
                && Math.abs(matrix.m33 - 1.0D) < 0.000001D
                && Math.abs(matrix.m01) < 0.000001D
                && Math.abs(matrix.m02) < 0.000001D
                && Math.abs(matrix.m03) < 0.000001D
                && Math.abs(matrix.m10) < 0.000001D
                && Math.abs(matrix.m12) < 0.000001D
                && Math.abs(matrix.m13) < 0.000001D
                && Math.abs(matrix.m20) < 0.000001D
                && Math.abs(matrix.m21) < 0.000001D
                && Math.abs(matrix.m23) < 0.000001D
                && Math.abs(matrix.m30) < 0.000001D
                && Math.abs(matrix.m31) < 0.000001D
                && Math.abs(matrix.m32) < 0.000001D;
    }

    private double[] getExactEmoticonsBoneWorldPosition(AnimationBone bone, BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        try
        {
            if (!(this.previewActor.getMorph() instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph))
            {
                return null;
            }

            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                    (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph) this.previewActor.getMorph();

            if (morph.animator == null)
            {
                return null;
            }

            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone sourceBone =
                    EmoticonsModelAccess.findBone(morph, bone.getName());

            if (sourceBone == null)
            {
                return null;
            }

            /*
             * This is intentionally the same transformation used by
             * Emoticons AnimatorController.calcPosition().
             *
             * calcPosition() does:
             *
             *   bone.mat.transform(point)
             *   rotateY(360 - renderYawOffset)
             *   scale(0.9375)
             *   add entity position
             *
             * The previous Gizmo path used (180 - recordYaw), which
             * introduced the observed X/Z displacement in the opposite
             * direction from the rendered Emoticons bone.
             */
            javax.vecmath.Vector4f position =
                    new javax.vecmath.Vector4f(0.0F, 0.0F, 0.0F, 1.0F);

            sourceBone.mat.transform(position);

            double yaw =
                    Math.toRadians(
                            -this.previewActor.renderYawOffset
                    );

            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double localX = position.x * 0.9375D;
            double localY = position.y * 0.9375D;
            double localZ = position.z * 0.9375D;

            double worldX =
                    cos * localX + sin * localZ;
            double worldZ =
                    -sin * localX + cos * localZ;

            return new double[]
            {
                this.previewActor.posX + worldX,
                this.previewActor.posY + localY,
                this.previewActor.posZ + worldZ
            };
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Exact Emoticons orientation basis for rotation and scale gizmos.
     *
     * Unlike translation, rotation must follow the fully evaluated bone
     * matrix so the rings visibly turn with the selected bone's pose.
     */
    private double[][] getExactEmoticonsBoneWorldAxes(
            AnimationBone bone,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        try
        {
            if (!(this.previewActor.getMorph() instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph))
            {
                return null;
            }

            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                    (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph) this.previewActor.getMorph();

            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone sourceBone =
                    EmoticonsModelAccess.findBone(morph, bone.getName());

            if (sourceBone == null || sourceBone.mat == null)
            {
                return null;
            }

            javax.vecmath.Matrix4f basis =
                    new javax.vecmath.Matrix4f(sourceBone.mat);

            double yaw =
                    Math.toRadians(
                            -this.previewActor.renderYawOffset
                    );

            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double[] x = transformEmoticonsDirection(
                    basis.m00, basis.m10, basis.m20,
                    cos, sin
            );
            double[] y = transformEmoticonsDirection(
                    basis.m01, basis.m11, basis.m21,
                    cos, sin
            );
            double[] z = transformEmoticonsDirection(
                    basis.m02, basis.m12, basis.m22,
                    cos, sin
            );

            normalize(x);
            normalize(y);
            normalize(z);

            return new double[][] {x, y, z};
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Exact Emoticons translation basis.
     *
     * BOBJBone.applyTransformations() applies the editable x/y/z
     * translation before the selected bone's own rotation. Therefore
     * the translation axes are parentBone.mat * relBoneMat, not the
     * final bone.mat.
     *
     * This is the same local model hierarchy used when
     * EditorAnimatorMorphController writes snapshot X/Y/Z directly to
     * BOBJBone.x/y/z.
     */
    private double[][] getExactEmoticonsBoneTranslationWorldAxes(
            AnimationBone bone,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null || this.previewActor == null || this.previewActor.getMorph() == null)
        {
            return null;
        }

        try
        {
            if (!(this.previewActor.getMorph() instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph))
            {
                return null;
            }

            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                    (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph) this.previewActor.getMorph();

            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone sourceBone =
                    EmoticonsModelAccess.findBone(morph, bone.getName());

            if (sourceBone == null)
            {
                return null;
            }

            javax.vecmath.Matrix4f basis =
                    new javax.vecmath.Matrix4f(sourceBone.relBoneMat);

            if (sourceBone.parentBone != null)
            {
                basis.set(sourceBone.parentBone.mat);
                basis.mul(sourceBone.relBoneMat);
            }

            double yaw =
                    Math.toRadians(
                            -this.previewActor.renderYawOffset
                    );

            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double[] x = transformEmoticonsDirection(
                    basis.m00, basis.m10, basis.m20,
                    cos, sin
            );
            double[] y = transformEmoticonsDirection(
                    basis.m01, basis.m11, basis.m21,
                    cos, sin
            );
            double[] z = transformEmoticonsDirection(
                    basis.m02, basis.m12, basis.m22,
                    cos, sin
            );

            normalize(x);
            normalize(y);
            normalize(z);

            return new double[][] {x, y, z};
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private double[] transformEmoticonsDirection(
            double x,
            double y,
            double z,
            double cos,
            double sin)
    {
        return new double[]
        {
            cos * x + sin * z,
            y,
            -sin * x + cos * z
        };
    }

    private float getWorldGizmoSize(
            EditorCamera camera,
            double x,
            double y,
            double z,
            int viewportHeight)
    {
        if (camera == null || viewportHeight <= 0)
        {
            return 1.0F;
        }

        /*
         * Keep the gizmo at a predictable screen size. Euclidean distance
         * is not the correct perspective depth when the camera is pitched:
         * two points at the same view depth should project to the same size.
         * Use the same 60-degree vertical FOV as project() and target an
         * approximately 90-pixel axis length (1.8 * worldSize).
         */
        double dx = x - camera.getCameraX();
        double dy = y - camera.getCameraY();
        double dz = z - camera.getCameraZ();

        double yaw = Math.toRadians(camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());

        double cy = Math.cos(yaw);
        double sy = Math.sin(yaw);
        double cameraZ = sy * dx + cy * dz;

        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        cameraZ = -sp * dy + cp * cameraZ;

        double depth = -cameraZ;
        if (depth <= 0.05D)
        {
            return 0.35F;
        }

        double focal =
                viewportHeight /
                        (2.0D * Math.tan(Math.toRadians(60.0D) / 2.0D));

        double worldSize = 50.0D * depth / focal;

        return (float) Math.max(
                0.02D,
                Math.min(100.0D, worldSize)
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

        float radius = size * 1.05F;
        drawRingVector3D(axes[1], axes[2], radius,
                this.activePart == AXIS_X ? ACTIVE_RING_COLOR : AXIS_X_COLOR,
                this.activePart == AXIS_X);
        drawRingVector3D(axes[2], axes[0], radius,
                this.activePart == AXIS_Y ? ACTIVE_RING_COLOR : AXIS_Y_COLOR,
                this.activePart == AXIS_Y);
        drawRingVector3D(axes[0], axes[1], radius,
                this.activePart == AXIS_Z ? ACTIVE_RING_COLOR : AXIS_Z_COLOR,
                this.activePart == AXIS_Z);

        /* Clear, visible pivot marker inspired by BBS FS's central grab area. */
        GL11.glPointSize(9.0F);
        GL11.glEnable(GL11.GL_POINT_SMOOTH);
        setColor(0xFFF1F3F5);
        GL11.glBegin(GL11.GL_POINTS);
        GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POINT_SMOOTH);
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

    private void drawRingVector3D(double[] a, double[] b, float radius, int color, boolean active)
    {
        /* A dark under-stroke gives the rings separation from bright model textures. */
        GL11.glLineWidth(active ? 12.0F : 9.0F);
        setColor(0xB8000000);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 96; i++)
        {
            double angle = Math.PI * 2.0D * i / 96.0D;
            double c = Math.cos(angle) * radius;
            double d = Math.sin(angle) * radius;
            GL11.glVertex3d(a[0] * c + b[0] * d, a[1] * c + b[1] * d, a[2] * c + b[2] * d);
        }
        GL11.glEnd();

        /* Selected ring turns yellow and grows thicker, like BBS FS feedback. */
        GL11.glLineWidth(active ? 7.0F : 4.0F);
        setColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 96; i++)
        {
            double angle = Math.PI * 2.0D * i / 96.0D;
            double c = Math.cos(angle) * radius;
            double d = Math.sin(angle) * radius;
            GL11.glVertex3d(a[0] * c + b[0] * d, a[1] * c + b[1] * d, a[2] * c + b[2] * d);
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
            int best=-1; double bestDistance=27.0D;
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
        /*
         * The screen-space projection was calculated from the same
         * data-space basis used to render the position handles. Therefore
         * its signed scalar already tells us how far to change the matching
         * AnimationTransform coordinate. Do not apply a second X-only sign
         * correction here: that makes the bone move opposite to its handle.
         */
        return getAxisDragAmount(axisId, mouseDX, mouseDY)
                * getTranslationAxisSign(axisId);
    }

    private double getDataAxisDelta(
            int axisId,
            double visualDelta)
    {
        return visualDelta * getTranslationAxisSign(axisId);
    }

    private double getTranslationAxisSign(int axisId)
    {
        if (!isBlockbusterCustomMorph())
        {
            return 1.0D;
        }

        /*
         * getExactBlockbusterBoneTranslationWorldAxes() already converts
         * the renderer basis into AnimationTransform data-space directions.
         * Applying another sign here would make the bone move opposite to
         * the visible handle (and would also break plane XY dragging).
         */
        return 1.0D;
    }

    private boolean isBlockbusterCustomMorph()
    {
        return this.previewActor != null
                && this.previewActor.getMorph()
                        instanceof mchorse.blockbuster_pack.morphs.CustomMorph;
    }

    /**
     * Reconstruct the animated Blockbuster pivot without using the renderer's
     * camera-dependent matrix. This mirrors ModelCustomRenderer.applyTransform
     * and ModelCustomRenderer.render() at the transform level.
     */
    private double[] getBlockbusterAnimationBoneWorldPosition(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe)
    {
        if (bone == null || !isBlockbusterCustomMorph())
        {
            return null;
        }

        try
        {
            mchorse.blockbuster_pack.morphs.CustomMorph customMorph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph)
                            this.previewActor.getMorph();

            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(
                            customMorph.getKey()
                    );

            if (model == null || model.model == null)
            {
                return null;
            }

            int frame =
                    selectedKeyframe != null
                            ? selectedKeyframe.getFrame()
                            : this.gizmoFrame;

            AnimationTransform pivot =
                    getBlockbusterAnimationBonePivot(
                            bone,
                            frame
                    );

            if (pivot == null)
            {
                return null;
            }

            mchorse.blockbuster.api.Model sourceModel = model.model;

            double x =
                    pivot.getPositionX()
                            * sourceModel.scale[0]
                            * customMorph.scale
                            * 0.0625D;

            double y =
                    pivot.getPositionY()
                            * sourceModel.scale[1]
                            * customMorph.scale
                            * 0.0625D;

            double z =
                    pivot.getPositionZ()
                            * sourceModel.scale[2]
                            * customMorph.scale
                            * 0.0625D;

            double yaw =
                    Math.toRadians(
                            -this.previewActor.renderYawOffset
                                    + 180.0D
                    );

            double cos = Math.cos(yaw);
            double sin = Math.sin(yaw);

            double worldX = cos * x + sin * z;
            double worldZ = -sin * x + cos * z;

            return new double[]
            {
                this.previewActor.posX + worldX,
                this.previewActor.posY + y,
                this.previewActor.posZ + worldZ
            };
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private AnimationTransform getBlockbusterAnimationBonePivot(
            AnimationBone bone,
            int frame)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationTransform animation =
                bone.getTransformAt(frame);

        if (animation == null)
        {
            animation = new AnimationTransform();
        }

        AnimationTransform local =
                new AnimationTransform();

        /*
         * Exact ModelCustomRenderer.applyTransform() convention.
         */
        local.setPosition(
                bone.getLocalX() + animation.getPositionX(),
                -(bone.getLocalY() + animation.getPositionY()),
                -(bone.getLocalZ() + animation.getPositionZ())
        );

        /*
         * ModelCustomRenderer.applyTransform() gives root limbs the
         * additional +24 model-pixel origin.
         */
        if (bone.getParent() == null)
        {
            local.setPosition(
                    local.getPositionX(),
                    local.getPositionY() + 24.0F,
                    local.getPositionZ()
            );
        }

        local.setRotation(
                animation.getRotationX(),
                -animation.getRotationY(),
                -animation.getRotationZ()
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
                getBlockbusterAnimationBonePivot(
                        parent,
                        frame
                );

        return parentWorld == null
                ? local
                : parentWorld.combine(local);
    }

    private double getDataAxisSign(int axisId)
    {
        /*
         * Match the rotation-channel convention used when reconstructing
         * Blockbuster's rendered bone hierarchy in getBlockbusterAnimationBonePivot():
         *
         *     renderer X rotation =  AnimationTransform X
         *     renderer Y rotation = -AnimationTransform Y
         *     renderer Z rotation = -AnimationTransform Z
         *
         * The rotation drag angle is measured in the displayed ring basis,
         * so convert it back to the animation-data convention per channel.
         * Do not infer these signs from the translation handle signs: those
         * are derived from the parent's rendered basis and follow a different
         * transform stage. Emoticons remains on its own native convention.
         */
        /*
         * In Body Parts mode the selected child morph can be a Blockbuster
         * CustomMorph even when the actor preview itself is another morph.
         * Use the actual edit target first; otherwise the Y/Z data-channel
         * conversion can silently fall back to the actor's unrelated type.
         */
        boolean blockbusterBodyPart =
                isBodyPartInternalBoneTarget(this.activeBone)
                        && isBlockbusterBodyPartTarget();

        if (blockbusterBodyPart)
        {
            /*
             * Use the same mouse-angle -> animation-channel conversion as
             * the working Blockbuster Pose gizmo. ModelCustomRenderer keeps
             * X positive and negates the stored Y/Z rotation channels.
             * Body Parts writes these channels directly (the special case in
             * mouse-drag handling), so it must use the same signs as Pose;
             * the former X-/Y-/Z+ mapping made the displayed basis drift
             * opposite to the rendered bone as its key changed.
             */
            return axisId == AXIS_X ? 1.0D : -1.0D;
        }

        if (isBlockbusterCustomMorph())
        {
            if (axisId == AXIS_Y || axisId == AXIS_Z)
            {
                return -1.0D;
            }

            return 1.0D;
        }

        return 1.0D;
    }

    /**
     * Accumulate angular mouse movement around the projected gizmo pivot.
     *
     * Unlike a linear pixels-to-degrees mapping, this has no artificial
     * travel limit: the angle can pass 360 degrees repeatedly. Each mouse
     * sample is unwrapped against the previous sample so crossing the
     * atan2 +/-PI seam does not produce a full-turn jump.
     */
    private void setRotationDragBasis(
            int axisId,
            double[] world,
            double[][] axes,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            ScreenPoint center,
            EditorCamera camera)
    {
        if (world == null || axes == null || axisId < 0 || axisId > 2)
        {
            return;
        }

        /*
         * The visible rings are drawn in screen space from the two projected
         * directions perpendicular to the selected axis. Use that same basis
         * to interpret the mouse position; raw atan2(mouseY, mouseX) ignores
         * the ring's orientation and reverses the apparent direction on some
         * bones/camera angles.
         */
        int basisA = (axisId + 1) % 3;
        int basisB = (axisId + 2) % 3;
        ScreenDirection directionA = getScreenDirection(
                center, world, axes[basisA],
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenDirection directionB = getScreenDirection(
                center, world, axes[basisB],
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );

        if (directionA == null || directionB == null)
        {
            return;
        }

        /* Match the normalized basis used to draw the visible ring exactly. */
        this.rotationBasisAX = directionA.x;
        this.rotationBasisAY = directionA.y;
        this.rotationBasisBX = directionB.x;
        this.rotationBasisBY = directionB.y;
    }

    /**
     * Body Parts / Blockbuster rotation input basis.
     *
     * Build the mouse basis from the same world-space ring radius used by
     * drawRotationGizmo3D(). The ordinary gizmo path intentionally remains
     * unchanged so Pose and the other morph adapters keep their existing
     * behavior.
     */
    private void setBlockbusterBodyPartRotationDragBasis(
            int axisId,
            double[] world,
            double[][] axes,
            float gizmoSize,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            ScreenPoint center,
            EditorCamera camera)
    {
        if (world == null || axes == null || axisId < 0 || axisId > 2
                || center == null || gizmoSize <= 0.0F)
        {
            return;
        }

        int basisA = (axisId + 1) % 3;
        int basisB = (axisId + 2) % 3;
        double radius = gizmoSize * 1.05D;

        ScreenPoint pointA = project(
                world[0] + axes[basisA][0] * radius,
                world[1] + axes[basisA][1] * radius,
                world[2] + axes[basisA][2] * radius,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );
        ScreenPoint pointB = project(
                world[0] + axes[basisB][0] * radius,
                world[1] + axes[basisB][1] * radius,
                world[2] + axes[basisB][2] * radius,
                viewportX, viewportY, viewportWidth, viewportHeight, camera
        );

        if (pointA == null || pointB == null)
        {
            return;
        }

        this.rotationBasisAX = pointA.x - center.x;
        this.rotationBasisAY = pointA.y - center.y;
        this.rotationBasisBX = pointB.x - center.x;
        this.rotationBasisBY = pointB.y - center.y;

        double determinant =
                this.rotationBasisAX * this.rotationBasisBY
                        - this.rotationBasisBX * this.rotationBasisAY;

        if (Math.abs(determinant) < 0.0001D)
        {
            /* Nearly edge-on: fall back to the existing stable basis. */
            setRotationDragBasis(
                    axisId, world, axes,
                    viewportX, viewportY, viewportWidth, viewportHeight,
                    center, camera
            );
        }
    }

    private double[] getRotationRingCoordinates(double dx, double dy)
    {
        double determinant =
                this.rotationBasisAX * this.rotationBasisBY
                        - this.rotationBasisBX * this.rotationBasisAY;

        if (Math.abs(determinant) < 0.0001D)
        {
            /* Edge-on ring: retain a stable fallback instead of exploding. */
            return new double[] {dx, dy};
        }

        double a = (dx * this.rotationBasisBY - dy * this.rotationBasisBX) / determinant;
        double b = (this.rotationBasisAX * dy - this.rotationBasisAY * dx) / determinant;

        return new double[] {a, b};
    }

    private double getRotationDragAngle(int axisId, double mouseX, double mouseY)
    {
        return accumulateRotationDragAngle(mouseX, mouseY)
                * getDataAxisSign(axisId);
    }

    /**
     * Blockbuster Body Parts already measures angles in renderer-local ring
     * space. Keep that angle raw; channel signs are applied only when the
     * renderer-space rotation matrix is converted back to AnimationTransform.
     */
    private double getBlockbusterBodyPartRotationDragAngle(
            double mouseX,
            double mouseY)
    {
        return accumulateRotationDragAngle(mouseX, mouseY);
    }

    private double accumulateRotationDragAngle(double mouseX, double mouseY)
    {
        double dx = mouseX - this.rotationDragCenterX;
        double dy = mouseY - this.rotationDragCenterY;
        double radiusSquared = dx * dx + dy * dy;

        /* The angular direction is undefined directly at the pivot. */
        if (radiusSquared < 64.0D)
        {
            this.rotationDragAngleValid = false;
            return this.rotationDragAccumulatedDegrees;
        }

        double[] ringPoint = getRotationRingCoordinates(dx, dy);
        double angle = Math.atan2(ringPoint[1], ringPoint[0]);

        if (!this.rotationDragAngleValid)
        {
            this.rotationDragLastAngle = angle;
            this.rotationDragAngleValid = true;
            return this.rotationDragAccumulatedDegrees;
        }

        double delta = angle - this.rotationDragLastAngle;

        while (delta > Math.PI) delta -= Math.PI * 2.0D;
        while (delta < -Math.PI) delta += Math.PI * 2.0D;

        this.rotationDragAccumulatedDegrees += Math.toDegrees(delta);
        this.rotationDragLastAngle = angle;

        return this.rotationDragAccumulatedDegrees;
    }

    private double getAxisDragAmount(int axisId,double mouseDX,double mouseDY)
    {
        if(axisId<0||axisId>2)return 0.0D;
        double ux=this.dragScreenAxes[axisId][0], uy=this.dragScreenAxes[axisId][1];
        double scale=Math.max(.0001D,this.dragPixelsPerWorld[axisId]);
        return (mouseDX*ux+mouseDY*uy)/scale;
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

        /*
         * Solve the 2D least-squares problem for movement in the selected
         * plane. The direct inverse becomes unstable when the two projected
         * axes are almost parallel (for example, when the plane faces the
         * camera edge-on). Instead of dropping the entire drag to zero,
         * switch to a damped normal-equation solve, matching the strategy
         * used by BBS FS's GizmoJacobian.
         */
        double aa = ax * ax + ay * ay;
        double ab = ax * bx + ay * by;
        double bb = bx * bx + by * by;
        double rhsA = ax * mouseDX + ay * mouseDY;
        double rhsB = bx * mouseDX + by * mouseDY;

        double det = aa * bb - ab * ab;
        double scale = Math.max(1.0D, aa + bb);

        if (Math.abs(det) > 1.0E-8D * scale * scale)
        {
            return new double[]
            {
                (rhsA * bb - rhsB * ab) / det,
                (rhsB * aa - rhsA * ab) / det
            };
        }

        /*
         * Damping regularizes the nearly singular system. It deliberately
         * favors a small, stable movement over a huge jump or a dead drag.
         */
        double lambda = 1.0E-4D * scale;
        double dampedAA = aa + lambda;
        double dampedBB = bb + lambda;
        double dampedDet = dampedAA * dampedBB - ab * ab;

        if (Math.abs(dampedDet) < 1.0E-12D)
        {
            return new double[] {0.0D, 0.0D};
        }

        double deltaA =
                (rhsA * dampedBB - rhsB * ab) / dampedDet;
        double deltaB =
                (rhsB * dampedAA - rhsA * ab) / dampedDet;

        /*
         * The projected basis vectors already encode the model's native
         * translation convention. Do not apply an extra axis sign here.
         */
        return new double[] {deltaA, deltaB};
    }

    /*
     * Translation gizmo axes must describe the coordinate space in which
     * AnimationTransform.positionX/Y/Z are actually stored.
     *
     * A bone's OWN rotation must not be used for its translation axes.
     * AnimationBone.combine() rotates a child's position by the PARENT
     * transform, then applies the child's rotation to the bone itself.
     *
     * The previous gizmo used the fully composed bone rotation here.
     * For an arm (or any rotated bone) that makes editor X/Z appear on
     * the wrong world axes: dragging the red X handle changes position X,
     * while the rendered handle can point along world Z.
     *
     * Keep rotation/scale gizmos on the native bone basis, but make the
     * position gizmo follow the actual translation basis.
     */
    /**
     * Position axes for a Body Part target. The added model's root
     * translation is expressed in the attachment's local space; an
     * internal bone's translation is expressed in its parent-bone space.
     * In both cases, do not include the selected bone's own rotation.
     */
    private double[][] getBodyPartTranslationWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        /*
         * The renderer applies a bone's translation BEFORE applying that
         * bone's own rotation. Therefore the visible translation axes must
         * follow the parent hierarchy, not the selected bone's full matrix.
         *
         * Using the selected bone's native matrix here makes its position
         * handles rotate with the bone itself. On mirrored Blockbuster
         * models this can make a left-side limb's handles appear on the
         * opposite side; Chameleon has the same parent-before-local-rotation
         * rule even though its root coordinate convention differs.
         *
         * Keep the selected bone as the fallback for a root bone, because
         * there is no parent renderer to provide a basis in that case.
         */
        AnimationBone parent = bone == null ? null : bone.getParent();
        AnimationBone basisBone = parent != null ? parent : bone;

        return getBodyPartComposedWorldAxes(
                basisBone,
                selectedKeyframe,
                recordFrame
        );
    }

    /**
     * Compose the Body Part's rendered orientation in hierarchy order:
     *
     * actor attachment * whole-model rotation * internal bone hierarchy.
     *
     * Do not add Euler angles. Rotations are non-commutative, and adding
     * them makes local axes drift after combined X/Y/Z rotations.
     */
    private double[][] getBodyPartComposedWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        if (bone == null)
        {
            return null;
        }

        int frame = selectedKeyframe != null
                ? selectedKeyframe.getFrame()
                : this.gizmoFrame;

        /*
         * The attachment basis is already in world space and comes from the
         * actual parent renderer whenever that adapter is available.
         */
        double[][] attachmentAxes =
                getBodyPartAttachmentWorldAxes(frame, recordFrame);

        if (attachmentAxes == null)
        {
            return null;
        }

        float[][] attachmentMatrix = new float[][]
        {
            {(float) attachmentAxes[0][0], (float) attachmentAxes[1][0], (float) attachmentAxes[2][0]},
            {(float) attachmentAxes[0][1], (float) attachmentAxes[1][1], (float) attachmentAxes[2][1]},
            {(float) attachmentAxes[0][2], (float) attachmentAxes[1][2], (float) attachmentAxes[2][2]}
        };

        AnimationTransform global = this.globalTransformTarget != null
                ? this.globalTransformTarget
                : (this.bodyPartTarget != null
                   ? this.bodyPartTarget.getGlobalTransform()
                   : null);

        float[][] globalRotation = global == null
                ? identityRotationMatrix()
                : createRotationMatrix(
                        global.getRotationX(),
                        global.getRotationY(),
                        global.getRotationZ()
                );

        float[][] globalScale = new float[][]
        {
            {global == null ? 1.0F : global.getScaleX(), 0.0F, 0.0F},
            {0.0F, global == null ? 1.0F : global.getScaleY(), 0.0F},
            {0.0F, 0.0F, global == null ? 1.0F : global.getScaleZ()}
        };

        float[] childScaleValues = getBodyPartChildModelScale(frame);
        float[][] childScale = new float[][]
        {
            {childScaleValues[0], 0.0F, 0.0F},
            {0.0F, childScaleValues[1], 0.0F},
            {0.0F, 0.0F, childScaleValues[2]}
        };

        /*
         * Blockbuster's CustomMorph applies a root mirror before rendering
         * its limb hierarchy. Other child morph types keep their own local
         * basis. Keep this adapter between the Body Part transform and the
         * child bone matrix, where it exists in the actual render pipeline.
         */
        float[][] childRootMatrix = identityRotationMatrix();

        if (isBlockbusterBodyPartTarget())
        {
            childRootMatrix = new float[][]
            {
                {-1.0F, 0.0F, 0.0F},
                {0.0F, -1.0F, 0.0F},
                {0.0F, 0.0F, 1.0F}
            };
        }

        float[][] internalMatrix = identityRotationMatrix();

        if (this.bodyPartTarget != null &&
                bone != this.bodyPartAttachmentBone)
        {
            /*
             * Unlike Pose mode, the child morph's native renderer matrix is
             * a rest-pose snapshot: Body Parts keyframe edits are stored in
             * AnimationBone and are not applied back into that renderer.
             * Using nativeAxes here therefore freezes the gizmo orientation
             * while the edited bone rotates. Evaluate the same local bone
             * hierarchy that receives the drag writes, then compose it with
             * the attachment and Body Part transforms below. This keeps the
             * displayed gizmo basis synchronized with the actual edited pose.
             */
            float[][] evaluated;
            if (this.mode == Mode.ROTATION
                    && isBlockbusterBodyPartTarget())
            {
                evaluated = getBlockbusterBodyPartRotationDisplayMatrix(bone, frame);
            }
            else if (this.mode == Mode.ROTATION
                    && isChameleonBodyPartTarget())
            {
                evaluated = getChameleonBodyPartRotationDisplayMatrix(bone, frame);
            }
            else if (this.mode == Mode.ROTATION
                    && this.bodyPartRuntimeMorph
                    instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
            {
                /*
                 * Emoticons BOBJ computes the complete animated hierarchy
                 * in BOBJBone.mat (including all parent bones and per-bone
                 * rest transforms). The previous editor-only reconstruction
                 * conjugated just the selected bone's delta and then combined
                 * a differently evaluated parent hierarchy. That could work
                 * for one limb but counter-rotate others.
                 *
                 * Use the evaluated CHILD armature basis for display, not a
                 * manually mirrored keyframe matrix. The native probe starts
                 * from identity and includes the same morph scale as the
                 * position gizmo; normalize its columns to remove scale.
                 */
                double[][] nativeChildAxes = getBodyPartNativeLocalBoneAxes(bone);
                if (nativeChildAxes != null)
                {
                    evaluated = new float[][]
                    {
                        {(float) nativeChildAxes[0][0], (float) nativeChildAxes[1][0], (float) nativeChildAxes[2][0]},
                        {(float) nativeChildAxes[0][1], (float) nativeChildAxes[1][1], (float) nativeChildAxes[2][1]},
                        {(float) nativeChildAxes[0][2], (float) nativeChildAxes[1][2], (float) nativeChildAxes[2][2]}
                    };
                }
                else
                {
                    evaluated = getEmoticonsBodyPartRotationDisplayMatrix(bone, frame);
                }
            }
            else
            {
                evaluated = getBodyPartInternalRotationMatrix(bone, frame);
            }
            if (evaluated != null)
            {
                internalMatrix = evaluated;
            }
        }

        diagnoseBlockbusterBodyPartRotationPose(bone, frame);

        float[][] worldMatrix = multiplyMatrix(
                attachmentMatrix,
                multiplyMatrix(
                        globalRotation,
                        multiplyMatrix(
                                globalScale,
                                multiplyMatrix(
                                        childScale,
                                        multiplyMatrix(childRootMatrix, internalMatrix)
                                )
                        )
                )
        );

        double[] worldX = normalizeCopy(new double[]
        {
            worldMatrix[0][0], worldMatrix[1][0], worldMatrix[2][0]
        });
        double[] worldY = normalizeCopy(new double[]
        {
            worldMatrix[0][1], worldMatrix[1][1], worldMatrix[2][1]
        });
        double[] worldZ = normalizeCopy(new double[]
        {
            worldMatrix[0][2], worldMatrix[1][2], worldMatrix[2][2]
        });

        /*
         * Keep the displayed basis aligned with the mouse-driven handles.
         * Blockbuster's sign correction belongs in the data-space writeback,
         * not here; flipping the drawn axes would make the handle itself
         * appear to run opposite to the drag.
         */

        diagnoseBlockbusterWorldAxes(bone, frame,
                new double[][] {worldX, worldY, worldZ});

        return new double[][] {worldX, worldY, worldZ};
    }

    /**
     * Compare the values used for the rotation gizmo with the ACTUAL
     * CustomMorph pose passed to Blockbuster's preview renderer.
     *
     * The old native postRender() probe can observe a cached/rest pose, so
     * it cannot prove that the animated CustomMorph is using the same
     * rotation as the editor. Read customPose directly instead.
     * Diagnostic only: never modify keys, bones, or renderer state.
     */
    /**
     * Compare the drawn gizmo basis with the matrix actually cached by
     * ModelCustomRenderer while rendering this child bone.
     *
     * Unlike postRender() from an isolated identity matrix, the cached
     * worldTransformation includes the live render hierarchy (Body Part,
     * child model root, parent limbs, and selected limb). This is READ-ONLY
     * diagnostics, and only for the Blockbuster Body Parts rotation target.
     */
    private void diagnoseBlockbusterWorldAxes(
            AnimationBone bone, int frame, double[][] gizmoAxes)
    {
        if (this.mode != Mode.ROTATION
                || !isBodyPartInternalBoneTarget(bone)
                || !isBlockbusterBodyPartTarget()
                || !(this.bodyPartRuntimeMorph
                     instanceof mchorse.blockbuster_pack.morphs.CustomMorph))
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastBlockbusterWorldAxesDiagnosticMillis < 1000L)
        {
            return;
        }
        this.lastBlockbusterWorldAxesDiagnosticMillis = now;

        try
        {
            mchorse.blockbuster_pack.morphs.CustomMorph morph =
                    (mchorse.blockbuster_pack.morphs.CustomMorph)
                            this.bodyPartRuntimeMorph;
            mchorse.blockbuster.client.model.ModelCustom model =
                    mchorse.blockbuster.client.model.ModelCustom.MODELS.get(morph.getKey());

            if (model == null)
            {
                System.out.println("[BBS WorldAxes] model-cache missing: "
                        + morph.getKey());
                return;
            }

            mchorse.blockbuster.client.model.ModelCustomRenderer renderer =
                    model.get(bone.getName());
            if (renderer == null)
            {
                System.out.println("[BBS WorldAxes] renderer missing: "
                        + bone.getName());
                return;
            }

            javax.vecmath.Matrix4d world = renderer.getWorldTransformation();
            double[][] actualAxes = new double[][]
            {
                {world.m00, world.m10, world.m20},
                {world.m01, world.m11, world.m21},
                {world.m02, world.m12, world.m22}
            };

            double[] matches = new double[3];
            double[] lengths = new double[3];
            for (int axis = 0; axis < 3; axis++)
            {
                double x = actualAxes[axis][0];
                double y = actualAxes[axis][1];
                double z = actualAxes[axis][2];
                lengths[axis] = Math.sqrt(x * x + y * y + z * z);
                if (lengths[axis] < 0.000001D)
                {
                    System.out.println("[BBS WorldAxes] zero cached axis: "
                            + bone.getName() + " axis=" + axis);
                    return;
                }
                for (int row = 0; row < 3; row++)
                {
                    actualAxes[axis][row] /= lengths[axis];
                    matches[axis] += actualAxes[axis][row]
                            * gizmoAxes[axis][row];
                }
            }

            System.out.println("[BBS WorldAxes] bone=" + bone.getName()
                    + " frame=" + frame
                    + " dragging=" + this.dragging
                    + " selectedAxis=" + this.activePart
                    + " dotXYZ=(" + matches[0] + ","
                            + matches[1] + "," + matches[2] + ")"
                    + " scales=(" + lengths[0] + ","
                            + lengths[1] + "," + lengths[2] + ")"
                    + " gizmoX=" + java.util.Arrays.toString(gizmoAxes[0])
                    + " actualX=" + java.util.Arrays.toString(actualAxes[0])
                    + " gizmoY=" + java.util.Arrays.toString(gizmoAxes[1])
                    + " actualY=" + java.util.Arrays.toString(actualAxes[1])
                    + " gizmoZ=" + java.util.Arrays.toString(gizmoAxes[2])
                    + " actualZ=" + java.util.Arrays.toString(actualAxes[2])
            );
        }
        catch (Throwable error)
        {
            System.err.println("[BBS WorldAxes] failed: "
                    + error.getClass().getName() + ": "
                    + String.valueOf(error.getMessage()));
        }
    }

    private void diagnoseBlockbusterBodyPartRotationPose(
            AnimationBone bone, int frame)
    {
        if (this.mode != Mode.ROTATION
                || !isBodyPartInternalBoneTarget(bone)
                || !isBlockbusterBodyPartTarget()
                || !(this.bodyPartRuntimeMorph
                        instanceof mchorse.blockbuster_pack.morphs.CustomMorph))
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastBlockbusterRotationPoseDiagnosticMillis < 900L)
        {
            return;
        }
        this.lastBlockbusterRotationPoseDiagnosticMillis = now;

        mchorse.blockbuster_pack.morphs.CustomMorph morph =
                (mchorse.blockbuster_pack.morphs.CustomMorph)
                        this.bodyPartRuntimeMorph;

        mchorse.blockbuster.api.ModelPose pose = morph.customPose;
        if (pose == null || pose.limbs == null)
        {
            System.out.println(
                    "[BBS RotationCheck] bone=" + bone.getName()
                    + " frame=" + frame + " customPose=missing");
            return;
        }

        mchorse.blockbuster.api.ModelTransform actual =
                pose.limbs.get(bone.getName());
        if (actual == null)
        {
            for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelTransform>
                    entry : pose.limbs.entrySet())
            {
                if (entry.getKey() != null &&
                        entry.getKey().equalsIgnoreCase(bone.getName()))
                {
                    actual = entry.getValue();
                    break;
                }
            }
        }

        if (actual == null || actual.rotate == null
                || actual.rotate.length < 3)
        {
            System.out.println(
                    "[BBS RotationCheck] bone=" + bone.getName()
                    + " frame=" + frame + " rendered limb pose=missing");
            return;
        }

        AnimationTransform key = bone.getTransformAt(frame);
        if (key == null)
        {
            key = new AnimationTransform();
        }

        float expectedX = bone.getBaseRotationX() + key.getRotationX();
        float expectedY = bone.getBaseRotationY() + key.getRotationY();
        float expectedZ = bone.getBaseRotationZ() + key.getRotationZ();

        float[][] expectedMatrix = createRotationMatrix(
                expectedX, -expectedY, -expectedZ);
        float[][] poseMatrix = createRotationMatrix(
                actual.rotate[0], -actual.rotate[1], -actual.rotate[2]);

        /*
         * Compare the direction of each renderer-space local basis vector.
         * A negative dot means a disagreement greater than 90 degrees.
         * The raw Euler differences are also printed for small deviations.
         */
        double dotX = 0.0D;
        double dotY = 0.0D;
        double dotZ = 0.0D;
        for (int row = 0; row < 3; row++)
        {
            dotX += expectedMatrix[row][0] * poseMatrix[row][0];
            dotY += expectedMatrix[row][1] * poseMatrix[row][1];
            dotZ += expectedMatrix[row][2] * poseMatrix[row][2];
        }

        System.out.println(
                "[BBS RotationCheck] bone=" + bone.getName()
                + " frame=" + frame
                + " dragging=" + this.dragging
                + " axis=" + this.activePart
                + " key=(" + key.getRotationX() + ","
                        + key.getRotationY() + ","
                        + key.getRotationZ() + ")"
                + " expected=(" + expectedX + ","
                        + expectedY + "," + expectedZ + ")"
                + " rendered=(" + actual.rotate[0] + ","
                        + actual.rotate[1] + ","
                        + actual.rotate[2] + ")"
                + " axisDot=(" + dotX + ","
                        + dotY + "," + dotZ + ")"
        );
    }

    private float[][] identityRotationMatrix()
    {
        return new float[][]
        {
            {1.0F, 0.0F, 0.0F},
            {0.0F, 1.0F, 0.0F},
            {0.0F, 0.0F, 1.0F}
        };
    }

    private double[] normalizeCopy(double[] axis)
    {
        normalize(axis);
        return axis;
    }

    /**
     * Native local rotations for the selected child model. Blockbuster
     * reverses its Y/Z renderer angles; the other supported model adapters
     * retain the editor's imported rotation convention.
     */
    private float[][] getBodyPartInternalRotationMatrix(
            AnimationBone bone,
            float frame)
    {
        if (bone == null)
        {
            return identityRotationMatrix();
        }

        AnimationTransform key = bone.getTransformAt(frame);

        if (key == null)
        {
            key = new AnimationTransform();
        }

        boolean blockbuster = isBlockbusterBodyPartTarget();

        float rx = bone.getBaseRotationX() + key.getRotationX();
        float ry = bone.getBaseRotationY() + key.getRotationY();
        float rz = bone.getBaseRotationZ() + key.getRotationZ();

        if (blockbuster)
        {
            ry = -ry;
            rz = -rz;
        }

        float[][] local = createRotationMatrix(rx, ry, rz);
        AnimationBone parent = bone.getParent();

        if (parent == null)
        {
            return local;
        }

        float[][] parentWorld =
                getBodyPartInternalRotationMatrix(parent, frame);

        return parentWorld == null
                ? local
                : multiplyMatrix(parentWorld, local);
    }

    /**
     * Rotation gizmo DISPLAY basis only, for internal Blockbuster Body Parts
     * bones. The key writeback was confirmed correct for X/Y/Z, but X/Z
     * rotation rings visually advance opposite to the actual animated limb.
     *
     * Preserve the imported rest orientation and the actual parent hierarchy.
     * Conjugating only the selected bone's delta by a local 180-degree Y
     * rotation reverses visual X/Z delta while leaving Y unchanged:
     *
     *     Ddisplay = S * (Rrest^-1 * Ranimated) * S
     *     S = diag(-1, 1, -1)
     *
     * Unlike negating stored Euler channels, this also works after combined
     * rotations and does not disturb a non-zero imported rest pose. Do not
     * use this matrix for model pose writes, Position, Scale or other morphs.
     */
    private float[][] getBlockbusterBodyPartRotationDisplayMatrix(
            AnimationBone bone, float frame)
    {
        if (bone == null)
        {
            return identityRotationMatrix();
        }

        AnimationTransform key = bone.getTransformAt(frame);
        if (key == null)
        {
            key = new AnimationTransform();
        }

        float[][] rest = createRotationMatrix(
                bone.getBaseRotationX(),
                -bone.getBaseRotationY(),
                -bone.getBaseRotationZ());
        float[][] animated = createRotationMatrix(
                bone.getBaseRotationX() + key.getRotationX(),
                -(bone.getBaseRotationY() + key.getRotationY()),
                -(bone.getBaseRotationZ() + key.getRotationZ()));

        /* Rotation matrices are orthogonal, so inverse(rest) = transpose(rest). */
        float[][] restInverse = new float[3][3];
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                restInverse[row][column] = rest[column][row];
            }
        }

        float[][] delta = multiplyMatrix(restInverse, animated);
        float[] signs = {-1.0F, 1.0F, -1.0F};
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                delta[row][column] *= signs[row] * signs[column];
            }
        }

        float[][] displayLocal = multiplyMatrix(rest, delta);
        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return displayLocal;
        }

        float[][] parentActual =
                getBodyPartInternalRotationMatrix(parent, frame);
        return parentActual == null
                ? displayLocal
                : multiplyMatrix(parentActual, displayLocal);
    }

    /**
     * Chameleon child-bone rotation rings: compensate only the visual X/Z
     * deltas, leaving the source rest pose and parent hierarchy unchanged.
     * The key writeback is handled separately in applyBodyPartLocalRotationDelta.
     */
    private float[][] getChameleonBodyPartRotationDisplayMatrix(
            AnimationBone bone, float frame)
    {
        if (bone == null)
        {
            return identityRotationMatrix();
        }

        AnimationTransform key = bone.getTransformAt(frame);
        if (key == null)
        {
            key = new AnimationTransform();
        }

        float[][] rest = createRotationMatrix(
                bone.getBaseRotationX(),
                bone.getBaseRotationY(),
                bone.getBaseRotationZ());
        float[][] animated = createRotationMatrix(
                bone.getBaseRotationX() + key.getRotationX(),
                bone.getBaseRotationY() + key.getRotationY(),
                bone.getBaseRotationZ() + key.getRotationZ());

        float[][] restInverse = new float[3][3];
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                restInverse[row][column] = rest[column][row];
            }
        }

        float[][] delta = multiplyMatrix(restInverse, animated);
        float[] signs = {-1.0F, 1.0F, -1.0F};
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                delta[row][column] *= signs[row] * signs[column];
            }
        }

        float[][] displayLocal = multiplyMatrix(rest, delta);
        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return displayLocal;
        }

        float[][] parentActual = getBodyPartInternalRotationMatrix(parent, frame);
        return parentActual == null
                ? displayLocal : multiplyMatrix(parentActual, displayLocal);
    }

    /**
     * The Emoticons child BOBJ bone is rendered with X rotation in the
     * editor's visual convention, while its Y/Z stored rotation deltas are
     * opposite the projected gizmo basis. Model key writeback is already
     * correct: adjust ONLY the displayed incremental rotation. Conjugating
     * the local animation delta with S=(1,-1,-1) keeps X as-is and reverses
     * the visual response of Y and Z without mirroring the rest-pose pivot.
     */
    private float[][] getEmoticonsBodyPartRotationDisplayMatrix(
            AnimationBone bone, float frame)
    {
        if (bone == null)
        {
            return identityRotationMatrix();
        }

        AnimationTransform key = bone.getTransformAt(frame);
        if (key == null)
        {
            key = new AnimationTransform();
        }

        float[][] rest = createRotationMatrix(
                bone.getBaseRotationX(),
                bone.getBaseRotationY(),
                bone.getBaseRotationZ());
        float[][] animated = createRotationMatrix(
                bone.getBaseRotationX() + key.getRotationX(),
                bone.getBaseRotationY() + key.getRotationY(),
                bone.getBaseRotationZ() + key.getRotationZ());

        float[][] restInverse = new float[3][3];
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                restInverse[row][column] = rest[column][row];
            }
        }

        float[][] delta = multiplyMatrix(restInverse, animated);
        float[] signs = {1.0F, -1.0F, -1.0F};
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 3; column++)
            {
                delta[row][column] *= signs[row] * signs[column];
            }
        }

        float[][] displayLocal = multiplyMatrix(rest, delta);
        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return displayLocal;
        }

        float[][] parentActual =
                getBodyPartInternalRotationMatrix(parent, frame);
        return parentActual == null
                ? displayLocal
                : multiplyMatrix(parentActual, displayLocal);
    }

    /**
     * Blockbuster Body Parts rotation in the same LOCAL renderer basis used
     * by getBodyPartInternalRotationMatrix(). The rings are projected from
     * that matrix, so a rotation must post-multiply the starting bone matrix,
     * not increment an Euler component in isolation.
     *
     * Renderer-local angles are (X, -Y, -Z); convert back to stored animation
     * channels only after the composed local rotation is decomposed.
     * Evaluate from drag-start values every frame to avoid accumulating
     * floating-point drift or Euler decomposition errors.
     */
    private void applyBlockbusterBodyPartRotation(
            AnimationTransform transform,
            int axisId,
            double mouseX,
            double mouseY)
    {
        if (transform == null || this.activeBone == null
                || axisId < AXIS_X || axisId > AXIS_Z)
        {
            return;
        }

        float baseX = this.activeBone.getBaseRotationX();
        float baseY = this.activeBone.getBaseRotationY();
        float baseZ = this.activeBone.getBaseRotationZ();

        float[][] start = createRotationMatrix(
                baseX + this.dragStartRX,
                -(baseY + this.dragStartRY),
                -(baseZ + this.dragStartRZ)
        );

        double angle = Math.toRadians(
                accumulateRotationDragAngle(mouseX, mouseY)
        );

        /*
         * Body Parts / Blockbuster uses renderer-space angles (X, -Y, -Z),
         * whereas the projected ring's signed mouse angle is measured in
         * the editor's displayed basis. Runtime tests established that
         * X/Z writeback rotated the model opposite to the ring; Y's older
         * extra inversion made both model and ring move opposite the mouse.
         *
         * Convert the measured ring angle into the renderer-local delta
         * here, without changing the ring geometry, hit testing, or the
         * functioning Pose/Emoticons paths. Keep Euler conversion below.
         */
        if (axisId == AXIS_X || axisId == AXIS_Z)
        {
            angle = -angle;
        }

        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float[][] delta = identityRotationMatrix();

        if (axisId == AXIS_X)
        {
            delta[1][1] = cosine;
            delta[1][2] = -sine;
            delta[2][1] = sine;
            delta[2][2] = cosine;
        }
        else if (axisId == AXIS_Y)
        {
            delta[0][0] = cosine;
            delta[0][2] = sine;
            delta[2][0] = -sine;
            delta[2][2] = cosine;
        }
        else
        {
            delta[0][0] = cosine;
            delta[0][1] = -sine;
            delta[1][0] = sine;
            delta[1][1] = cosine;
        }

        float[][] composed = multiplyMatrix(start, delta);
        float[] renderedEuler = rotationMatrixToEulerDegrees(composed);

        transform.setRotation(
                renderedEuler[0] - baseX,
                -renderedEuler[1] - baseY,
                -renderedEuler[2] - baseZ
        );
    }

    private long lastEmoticonsRotationZDiagnosticMillis;

    /**
     * Compare good and reversed Emoticons Z rotations using actual BOBJ
     * armature geometry, without changing their existing key writeback.
     * The child matrix contains the parent hierarchy and may have a
     * different local handedness for arms/legs than for *.end bones.
     */
    private void diagnoseEmoticonsRotationZ(AnimationBone bone, float mouseAngle)
    {
        if (bone == null || !(this.bodyPartRuntimeMorph
                instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph))
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastEmoticonsRotationZDiagnosticMillis < 350L)
        {
            return;
        }
        this.lastEmoticonsRotationZDiagnosticMillis = now;

        mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                        this.bodyPartRuntimeMorph;
        mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone nativeBone =
                EmoticonsModelAccess.findBone(morph, bone.getName());
        if (nativeBone == null || nativeBone.mat == null
                || nativeBone.relBoneMat == null)
        {
            return;
        }

        javax.vecmath.Matrix4f m = nativeBone.mat;
        javax.vecmath.Matrix4f rel = nativeBone.relBoneMat;
        float determinant =
                m.m00 * (m.m11 * m.m22 - m.m12 * m.m21)
                - m.m01 * (m.m10 * m.m22 - m.m12 * m.m20)
                + m.m02 * (m.m10 * m.m21 - m.m11 * m.m20);

        System.out.println("[BBS EmoticonsRotationZ] bone=" + bone.getName()
                + " parent=" + (nativeBone.parentBone == null
                        ? "<root>" : nativeBone.parentBone.name)
                + " mouseAngle=" + mouseAngle
                + " startZ=" + this.dragStartRZ
                + " currentZ=" + (bone.getTransformAt(this.gizmoFrame) == null
                        ? 0.0F : bone.getTransformAt(this.gizmoFrame).getRotationZ())
                + " determinant=" + determinant
                + " nativeZ=(" + m.m02 + "," + m.m12 + "," + m.m22 + ")"
                + " relX=(" + rel.m00 + "," + rel.m10 + "," + rel.m20 + ")"
                + " relY=(" + rel.m01 + "," + rel.m11 + "," + rel.m21 + ")"
                + " relZ=(" + rel.m02 + "," + rel.m12 + "," + rel.m22 + ")");
    }

    private void applyBodyPartLocalRotationDelta(
            AnimationTransform transform,
            AnimationBone bone,
            int axisId,
            float amount)
    {
        boolean internalBone = isBodyPartInternalBoneTarget(bone);
        boolean blockbuster = internalBone && isBlockbusterBodyPartTarget();

        float baseX = internalBone ? bone.getBaseRotationX() : 0.0F;
        float baseY = internalBone ? bone.getBaseRotationY() : 0.0F;
        float baseZ = internalBone ? bone.getBaseRotationZ() : 0.0F;

        float startX = baseX + this.dragStartRX;
        float startY = baseY + this.dragStartRY;
        float startZ = baseZ + this.dragStartRZ;

        if (blockbuster)
        {
            startY = -startY;
            startZ = -startZ;
        }

        float[][] current = createRotationMatrix(startX, startY, startZ);
        float[][] delta = identityRotationMatrix();

        /*
         * Build the drag delta in the gizmo's canonical basis first.
         * Blockbuster's ModelCustomRenderer.applyTransform maps its stored
         * channels into renderer space with S = diag(1, -1, -1): X is kept,
         * Y/Z are negated. Convert the DELTA MATRIX between those bases by
         * conjugation (S * delta * inverse(S)), rather than changing the
         * mouse angle or patching channel signs. Since S is its own inverse,
         * this is S * delta * S. Other adapters remain untouched.
         */
        /*
         * The ring mapping has already been corrected in the Blockbuster
         * drag path. Keep that visible interaction unchanged and reverse only
         * the delta applied to the rendered bone: the current renderer-space
         * conversion produces the opposite bone rotation on all three axes.
         */
        /*
         * Only the Chameleon child-bone X/Z key convention is reversed
         * relative to its projected rings. Keep its working Y untouched,
         * as well as every other morph type.
         */
        boolean chameleonInternal = internalBone && isChameleonBodyPartTarget();
        boolean emoticonsInternal = internalBone
                && this.bodyPartRuntimeMorph
                instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
        /*
         * In an attached Emoticons BOBJ armature, the rendered Y/Z rotations
         * are opposite the projected ring drag direction, while X is already
         * correct. Reverse only the editor-to-BOBJ drag delta for Y/Z.
         * The rendered child pivot and Position key writeback stay unchanged.
         */
        if (emoticonsInternal && axisId == AXIS_Z)
        {
            diagnoseEmoticonsRotationZ(bone, amount);
        }

        /*
         * BOBJ can encode opposite rest orientations for neighboring
         * bones. The observed lower arms and lower legs have rest +Z,
         * whereas low_left_arm.end has rest -Z. The former need the
         * opposite drag-to-key mapping from the latter.
         *
         * Derive this from the native relative rest matrix instead of
         * hardcoding names; leave X/Y and all other morphs unchanged.
         */
        boolean invertEmoticonsZ = emoticonsInternal && axisId == AXIS_Z;
        if (invertEmoticonsZ)
        {
            mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph morph =
                    (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                            this.bodyPartRuntimeMorph;
            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone nativeBone =
                    EmoticonsModelAccess.findBone(morph, bone.getName());
            if (nativeBone != null && nativeBone.relBoneMat != null)
            {
                /*
                 * For an unambiguous positive rest Z, do not invert the
                 * drag angle. Negative rest Z retains the previous mapping
                 * which was confirmed working for end bones.
                 */
                float localRestZ = nativeBone.relBoneMat.m22;
                if (localRestZ > 0.5F)
                {
                    invertEmoticonsZ = false;
                }
            }
        }

        double angle = Math.toRadians(
                (blockbuster && axisId == AXIS_Y)
                        || (chameleonInternal
                            && (axisId == AXIS_X || axisId == AXIS_Z))
                        || (emoticonsInternal && axisId == AXIS_Y)
                        || invertEmoticonsZ
                        ? -amount
                        : amount
        );
        float c = (float) Math.cos(angle);
        float sn = (float) Math.sin(angle);

        if (axisId == AXIS_X)
        {
            delta = new float[][]
            {
                {1.0F, 0.0F, 0.0F},
                {0.0F, c, -sn},
                {0.0F, sn, c}
            };
        }
        else if (axisId == AXIS_Y)
        {
            delta = new float[][]
            {
                {c, 0.0F, sn},
                {0.0F, 1.0F, 0.0F},
                {-sn, 0.0F, c}
            };
        }
        else
        {
            delta = new float[][]
            {
                {c, -sn, 0.0F},
                {sn, c, 0.0F},
                {0.0F, 0.0F, 1.0F}
            };
        }

        if (blockbuster)
        {
            /*
             * Match ModelCustomRenderer.applyTransform(): stored X is kept,
             * while stored Y and Z are negated in renderer space. Conjugate
             * the incremental rotation by this same basis conversion.
             */
            int[] basisSigns = new int[] {1, -1, -1};
            float[][] convertedDelta = identityRotationMatrix();

            for (int row = 0; row < 3; row++)
            {
                for (int column = 0; column < 3; column++)
                {
                    convertedDelta[row][column] =
                            delta[row][column] * basisSigns[row] * basisSigns[column];
                }
            }

            delta = convertedDelta;
        }

        float[][] result = multiplyMatrix(current, delta);
        float[] euler = rotationMatrixToEulerDegrees(result);

        float rx = euler[0] - baseX;
        float ry = (blockbuster ? -euler[1] : euler[1]) - baseY;
        float rz = (blockbuster ? -euler[2] : euler[2]) - baseZ;

        transform.setRotation(rx, ry, rz);
    }

    private float[] rotationMatrixToEulerDegrees(float[][] matrix)
    {
        double sy = Math.max(-1.0D, Math.min(1.0D, -matrix[2][0]));
        double y = Math.asin(sy);
        double cy = Math.cos(y);
        double x;
        double z;

        if (Math.abs(cy) > 0.00001D)
        {
            x = Math.atan2(matrix[2][1], matrix[2][2]);
            z = Math.atan2(matrix[1][0], matrix[0][0]);
        }
        else
        {
            x = Math.atan2(-matrix[1][2], matrix[1][1]);
            z = 0.0D;
        }

        return new float[]
        {
            (float) Math.toDegrees(x),
            (float) Math.toDegrees(y),
            (float) Math.toDegrees(z)
        };
    }

    private boolean isBodyPartInternalBoneTarget(AnimationBone bone)
    {
        return this.bodyPartTarget != null
                && this.globalTransformTarget == null
                && bone != null
                && bone != this.bodyPartAttachmentBone;
    }

    private void addBodyPartLocalAxisDelta(
            double[] target,
            int axisId,
            double worldDelta)
    {
        if (target == null || this.activeBone == null || this.activeKeyframe == null)
        {
            return;
        }

        int frame = this.activeKeyframe.getFrame();
        float[][] localRotation =
                getBodyPartLocalRotationMatrix(this.activeBone, frame);

        if (localRotation == null)
        {
            return;
        }

        double vx = localRotation[0][axisId];
        double vy = localRotation[1][axisId];
        double vz = localRotation[2][axisId];

        /*
         * The data adapter may invert one or more translation components,
         * even though the displayed axis is already in the rendered basis.
         */
        if (isBlockbusterBodyPartTarget())
        {
            /*
             * The displayed gizmo follows the mouse correctly. The mismatch
             * is in the Blockbuster key's local translation convention:
             * X and Z need their data-space deltas reversed, while Y is
             * already correct. Do this only when writing the internal bone
             * key; never change the visible axes or other model adapters.
             */
            vx = -vx;
            vy = -vy;
            vz = -vz;

            /*
             * The X correction above is confirmed by in-game testing.
             * Blockbuster's Z handle still applies the opposite data-space
             * displacement, so reverse this drag vector only for Z. Keep
             * X and Y behavior unchanged.
             */
            if (axisId == AXIS_Z)
            {
                vx = -vx;
                vy = -vy;
                vz = -vz;
            }
        }
        else if (isChameleonBodyPartTarget())
        {
            /*
             * Chameleon's translateBone() negates model-space X. Keep that
             * conversion, but compensate the confirmed reversed rendered
             * displacement along the displayed X/Z handles. Negating the
             * entire projected vector preserves the same movement distance
             * even when the bone or its attachment is rotated.
             * Y and plane drags involving Y keep the existing convention.
             */
            vx = -vx;
            if (axisId == AXIS_X || axisId == AXIS_Z)
            {
                vx = -vx;
                vy = -vy;
                vz = -vz;
            }
        }

        float[][] internal =
                getBodyPartInternalRotationMatrix(this.activeBone, frame);

        if (internal == null)
        {
            return;
        }

        float[] axisInModel = new float[]
        {
            internal[0][axisId],
            internal[1][axisId],
            internal[2][axisId]
        };

        if (isBlockbusterBodyPartTarget())
        {
            axisInModel[0] = -axisInModel[0];
            axisInModel[1] = -axisInModel[1];
        }

        AnimationTransform global =
                this.bodyPartTarget.getGlobalTransform();

        float sx = global == null ? 1.0F : global.getScaleX();
        float sy = global == null ? 1.0F : global.getScaleY();
        float sz = global == null ? 1.0F : global.getScaleZ();

        float[] childScale = getBodyPartChildModelScale(frame);
        sx *= childScale[0];
        sy *= childScale[1];
        sz *= childScale[2];

        double effectiveScale = Math.sqrt(
                axisInModel[0] * axisInModel[0] * sx * sx
                + axisInModel[1] * axisInModel[1] * sy * sy
                + axisInModel[2] * axisInModel[2] * sz * sz
        );

        if (effectiveScale < 0.0001D)
        {
            effectiveScale = 1.0D;
        }

        /*
         * Blockbuster and Chameleon bone offsets are measured in model
         * pixels, requiring 16 pixels per world block. Emoticons BOBJ
         * translation channels are already in model/world blocks:
         * BOBJBone.applyTransformations() directly writes x/y/z into the
         * translation matrix with no 1/16 conversion. Multiplying their
         * drag by 16 makes even a short mouse movement throw the gizmo far
         * past its intended screen distance. Do not change any non-Emoticons
         * data adapter or rotation channel.
         */
        boolean emoticonsInternal = this.bodyPartTarget != null
                && this.bodyPartRuntimeMorph
                instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
        double amount = worldDelta * (emoticonsInternal ? 1.0D : 16.0D)
                / effectiveScale;

        target[0] += vx * amount;
        target[1] += vy * amount;
        target[2] += vz * amount;
    }

    private float[][] getBodyPartLocalRotationMatrix(
            AnimationBone bone,
            float frame)
    {
        if (bone == null)
        {
            return identityRotationMatrix();
        }

        AnimationTransform key = bone.getTransformAt(frame);

        if (key == null)
        {
            key = new AnimationTransform();
        }

        float rx = bone.getBaseRotationX() + key.getRotationX();
        float ry = bone.getBaseRotationY() + key.getRotationY();
        float rz = bone.getBaseRotationZ() + key.getRotationZ();

        if (isBlockbusterBodyPartTarget())
        {
            ry = -ry;
            rz = -rz;
        }

        return createRotationMatrix(rx, ry, rz);
    }

    private double getBodyPartPositionDelta(int axisId, double worldDelta)
    {
        if (this.globalTransformTarget != null)
        {
            /*
             * Whole-model translation is stored in model pixels, while the
             * gizmo drag is measured in world blocks (one block = 16 pixels).
             */
            return worldDelta * 16.0D;
        }

        if (this.bodyPartTarget != null)
        {
            AnimationTransform global = this.bodyPartTarget.getGlobalTransform();
            float scale = 1.0F;

            if (global != null)
            {
                if (axisId == AXIS_X) scale = global.getScaleX();
                else if (axisId == AXIS_Y) scale = global.getScaleY();
                else scale = global.getScaleZ();
            }

            if (Math.abs(scale) < 0.0001F)
            {
                scale = 1.0F;
            }

            /*
             * Internal-bone offsets are also stored in model pixels and are
             * subsequently multiplied by the Body Part's global scale.
             */
            return worldDelta * 16.0D / scale;
        }

        return worldDelta;
    }

    /**
     * Blockbuster Body Parts store X/Y/Z rotation channels in a convention
     * that differs from the local axes returned by the native child renderer.
     * Flip only the displayed X and Z basis vectors for rotation gizmos; use
     * that same basis for ring drawing, hit testing, and mouse-angle capture.
     * This keeps the green Y ring unchanged and leaves model/data transforms,
     * position handles, scale handles, and non-Blockbuster morphs untouched.
     */
    private double[][] getRotationDisplayAxes(double[][] axes, AnimationBone bone)
    {
        /*
         * Rotation rings must follow the same evaluated bone basis as the
         * rendered child model. The previous Blockbuster-only X/Z axis flip
         * (a 180-degree Y rotation) made the visible gizmo counter-rotate
         * around X and Z while the bone itself rotated correctly.
         *
         * Keep the renderer-space basis intact for drawing and hit testing.
         * The Blockbuster drag path compensates for the corresponding mouse
         * angle handedness change before writing the bone rotation.
         */
        return axes;
    }

    private double[][] getGizmoWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        /*
         * Body Parts must never borrow native matrices from the parent
         * actor morph. Their pivot and orientation belong to the selected
         * child model, represented by BodyPartModelData's bone hierarchy.
         * Keep this branch ahead of every native actor adapter so the
         * behavior is consistent for Blockbuster, Emoticons and Chameleon.
         */
        if (this.bodyPartTarget != null || this.globalTransformTarget != null)
        {
            if (this.mode == Mode.POSITION)
            {
                return getBodyPartTranslationWorldAxes(
                        bone,
                        selectedKeyframe,
                        recordFrame
                );
            }

            return getBoneWorldAxes(
                    bone,
                    selectedKeyframe,
                    recordFrame
            );
        }

        /*
         * POSITION is different from ROTATION/SCALE.
         *
         * Blockbuster applies a limb's translation before that limb's own
         * rotation:
         *
         *     translate(rotationPoint) -> rotate -> scale
         *
         * Therefore a position handle must use the parent's rendered
         * basis, not the selected limb's rotated basis.  Using the full
         * bone matrix here is what caused the red/blue handles to become
         * inverted or to point into the X/Z plane on arms and legs.
         *
         * The translation basis is still LOCAL to the model hierarchy:
         * parent rotation/body-part rotation is preserved, but the
         * selected bone's own rotation is intentionally not applied.
         */
        if (this.mode == Mode.POSITION)
        {
            double[][] translationAxes =
                    getExactChameleonBoneTranslationWorldAxes(
                            bone,
                            recordFrame
                    );

            if (translationAxes != null)
            {
                return translationAxes;
            }

            translationAxes =
                    getExactEmoticonsBoneTranslationWorldAxes(
                            bone,
                            recordFrame
                    );

            if (translationAxes != null)
            {
                return translationAxes;
            }

            translationAxes =
                    getExactBlockbusterBoneTranslationWorldAxes(
                            bone,
                            recordFrame
                    );

            if (translationAxes != null)
            {
                return translationAxes;
            }
        }

        /*
         * Level 1 Body Part edits the whole model, not the attachment
         * bone. Rotation/scale axes therefore follow the attachment basis
         * composed with the model's own global rotation, rather than the
         * unmodified rendered parent-bone axes.
         */
        if (this.globalTransformTarget != null)
        {
            return getBoneWorldAxes(
                    bone,
                    selectedKeyframe,
                    recordFrame
            );
        }

        /*
         * Rotation and scale continue to use the exact rendered bone
         * orientation.  This keeps the existing local behaviour of those
         * tools unchanged.
         */
        double[][] nativeAxes =
                getNativeBoneWorldAxes(
                        bone,
                        recordFrame
                );

        if (nativeAxes != null)
        {
            return nativeAxes;
        }

        return getBoneWorldAxes(
                bone,
                selectedKeyframe,
                recordFrame
        );
    }

    private double[][] getBoneTranslationWorldAxes(
            AnimationBone bone,
            AnimationKeyframe selectedKeyframe,
            BlockbusterRecordFrame recordFrame)
    {
        /*
         * Position mode is a WORLD gizmo.
         *
         * The keyframe values are still written to local X/Y/Z, but the
         * visible handles must stay aligned with the actor/world axes.
         * Using the parent or the selected bone rotation here makes the
         * red X handle become a mixture of X/Z for arm/head bones. That is
         * exactly the symptom where dragging X changes the bone correctly
         * while the gizmo appears to travel forward.
         *
         * The actor yaw is already part of the Preview camera/model
         * transform. Therefore the basis below is deliberately the plain
         * world basis and is not rotated by the selected bone.
         */
        return new double[][]
        {
            {1.0D, 0.0D, 0.0D},
            {0.0D, 1.0D, 0.0D},
            {0.0D, 0.0D, 1.0D}
        };
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

        /*
         * Body Parts use their own attachment/global/internal hierarchy.
         * Resolve all three axes through the same composed basis for every
         * tool mode, rather than using a native matrix from the parent actor
         * or reconstructing orientation by adding Euler angles.
         */
        if (this.bodyPartTarget != null || this.globalTransformTarget != null)
        {
            return getBodyPartComposedWorldAxes(
                    bone,
                    selectedKeyframe,
                    recordFrame
            );
        }

        int frame =
                selectedKeyframe != null
                        ? selectedKeyframe.getFrame()
                        : this.gizmoFrame;

        AnimationTransform pivot =
                this.chameleonCoordinateSpace &&
                        this.bodyPartTarget != null &&
                        bone != this.bodyPartAttachmentBone
                        ? getChameleonWorldPivot(bone, frame)
                        : (this.bodyPartTarget != null &&
                           bone != this.bodyPartAttachmentBone
                           ? getBodyPartBonePivot(bone, frame)
                           : bone.getWorldPivotAt(frame));

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
            AnimationTransform world =
                    this.bodyPartTarget != null &&
                            bone != this.bodyPartAttachmentBone
                            ? getBodyPartBonePivot(bone, frame)
                            : bone.getWorldTransformAt(frame);

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

        float baseX = this.bodyPartTarget != null
                ? bone.getBaseRotationX() : 0.0F;
        float baseY = this.bodyPartTarget != null
                ? bone.getBaseRotationY() : 0.0F;
        float baseZ = this.bodyPartTarget != null
                ? bone.getBaseRotationZ() : 0.0F;

        float[][] localMatrix =
                createRotationMatrix(
                        baseX + local.getRotationX(),
                        baseY + local.getRotationY(),
                        baseZ + local.getRotationZ()
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
        double yaw=Math.toRadians(180.0D-(this.previewActor != null ? this.previewActor.renderYawOffset : (recordFrame != null ? recordFrame.getYaw() : 0.0D)));
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

    /**
     * Evaluate a Body Part's internal bone pivot from the imported rest
     * transform plus the editor key. AnimationBone.getWorldPivotAt() is
     * intentionally kept unchanged because Pose uses its existing contract;
     * Body Parts need the source model's base/rest rotations as well.
     */
    /*
     * Evaluate an internal Body Parts bone using the renderer hierarchy of
     * the prepared child morph. The result excludes the parent actor and
     * attachment, which are composed separately by the Body Parts adapter.
     */
    private void logBodyPartMatrixDiagnostic(AnimationBone bone, String reason, Throwable error)
    {
        String morphName = this.bodyPartRuntimeMorph == null
                ? "<none>"
                : this.bodyPartRuntimeMorph.getClass().getName();
        String boneName = bone == null ? "<none>" : bone.getName();
        String category = reason;
        int detailSeparator = category.indexOf(";");
        if (detailSeparator >= 0) category = category.substring(0, detailSeparator);
        String key = morphName + "|" + boneName + "|" + category;
        if (!this.bodyPartMatrixDiagnosticKeys.add(key))
        {
            return;
        }

        System.err.println("[BBS Animation Editor][BodyPartsGizmo] native-matrix "
                + "morph=" + morphName
                + ", bone=" + boneName
                + ", result=" + reason
                + (error == null ? "" : ", error="
                    + error.getClass().getName() + ": " + String.valueOf(error.getMessage())));
    }

    private long lastEmoticonsChildPivotDiagnosticMillis;

    private void diagnoseEmoticonsChildPivot(
            AnimationBone bone,
            mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone nativeBone,
            float scale)
    {
        if (bone == null || nativeBone == null || nativeBone.mat == null
                || this.mode != Mode.POSITION)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastEmoticonsChildPivotDiagnosticMillis < 750L)
        {
            return;
        }
        this.lastEmoticonsChildPivotDiagnosticMillis = now;

        AnimationTransform key = bone.getTransformAt(this.gizmoFrame);
        float kx = key == null ? 0.0F : key.getPositionX();
        float ky = key == null ? 0.0F : key.getPositionY();
        float kz = key == null ? 0.0F : key.getPositionZ();
        javax.vecmath.Matrix4f matrix = nativeBone.mat;
        javax.vecmath.Matrix4f rest = nativeBone.boneMat;

        System.out.println("[BBS EmoticonsChildPivot] bone=" + bone.getName()
                + " frame=" + this.gizmoFrame
                + " key=(" + kx + "," + ky + "," + kz + ")"
                + " native=(" + matrix.m03 + "," + matrix.m13
                + "," + matrix.m23 + ")"
                + " rest=("
                + (rest == null ? "null" : Float.toString(rest.m03)) + ","
                + (rest == null ? "null" : Float.toString(rest.m13)) + ","
                + (rest == null ? "null" : Float.toString(rest.m23)) + ")"
                + " childScale=" + scale
                + " boneXYZ=(" + nativeBone.x + ","
                + nativeBone.y + "," + nativeBone.z + ")"
                + " parent=" + (nativeBone.parentBone == null
                        ? "<root>" : nativeBone.parentBone.name));
    }

    private float[] getBodyPartNativeLocalBoneMatrix(AnimationBone bone)
    {
        if (bone == null || this.bodyPartRuntimeMorph == null)
        {
            logBodyPartMatrixDiagnostic(bone, "missing bone or prepared child morph", null);
            return null;
        }
        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean pushed = false;
        try
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushed = true;
            GL11.glLoadIdentity();

            if (this.bodyPartRuntimeMorph instanceof mchorse.blockbuster_pack.morphs.CustomMorph)
            {
                mchorse.blockbuster_pack.morphs.CustomMorph morph =
                        (mchorse.blockbuster_pack.morphs.CustomMorph) this.bodyPartRuntimeMorph;
                if (morph.model == null) morph.updateModel(true);
                mchorse.blockbuster.client.model.ModelCustom model =
                        mchorse.blockbuster.client.model.ModelCustom.MODELS.get(morph.getKey());
                if (model == null || model.model == null)
                {
                    logBodyPartMatrixDiagnostic(bone, "Blockbuster model cache/model data missing for key " + morph.getKey(), null);
                    return null;
                }
                mchorse.blockbuster.client.model.ModelCustomRenderer renderer =
                        model.get(bone.getName());
                if (renderer == null)
                {
                    logBodyPartMatrixDiagnostic(bone, "Blockbuster renderer not found in child model", null);
                    return null;
                }
                mchorse.blockbuster.api.Model sourceModel = model.model;
                GL11.glScalef(sourceModel.scale[0] * morph.scale,
                        sourceModel.scale[1] * morph.scale,
                        sourceModel.scale[2] * morph.scale);
                GL11.glScalef(-1.0F, -1.0F, 1.0F);
                GL11.glTranslatef(0.0F, -1.501F, 0.0F);
                renderer.postRender(0.0625F);
            }
            else if ((this.mode == Mode.POSITION
                    || this.mode == Mode.ROTATION
                    || this.mode == Mode.SCALE)
                    && this.bodyPartRuntimeMorph
                    instanceof mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
            {
                /*
                 * An attached Emoticons morph has its OWN BOBJ armature.
                 * The parent's previewActor morph must not be consulted here.
                 *
                 * BOBJBone.mat is the fully evaluated internal bone matrix
                 * (including imported pivot, ancestor transforms and current
                 * AnimatedPose). AnimatorController.render() then applies
                 * userConfig.scale and a root yaw of 360 degrees because
                 * Metamorph BodyPart.render() temporarily sets the child's
                 * renderYawOffset to zero. 360 degrees is identity.
                 *
                 * Unlike the Chameleon branch, the resulting matrix already
                 * includes animation offsets: do not append a second key delta.
                 */
                mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph child =
                        (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                                this.bodyPartRuntimeMorph;
                mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone sourceBone =
                        EmoticonsModelAccess.findBone(child, bone.getName());

                if (sourceBone == null || sourceBone.mat == null
                        || child.animator == null || child.animator.userConfig == null)
                {
                    logBodyPartMatrixDiagnostic(
                            bone, "Emoticons child BOBJ bone/matrix unavailable", null);
                    return null;
                }

                float scale = child.animator.userConfig.scale;
                javax.vecmath.Matrix4f childMatrix = sourceBone.mat;

                /*
                 * Compare native child translation against the selected
                 * editor key and original BOBJ pivot. This probe deliberately
                 * does not alter the successful model writeback or rotation.
                 */
                diagnoseEmoticonsChildPivot(bone, sourceBone, scale);

                /* Native Emoticons matrices are model-space block units. */
                GL11.glScalef(scale, scale, scale);
                FloatBuffer nativeBuffer = BufferUtils.createFloatBuffer(16);
                float[] childMatrixColumnMajor = new float[]
                {
                    childMatrix.m00, childMatrix.m10, childMatrix.m20, childMatrix.m30,
                    childMatrix.m01, childMatrix.m11, childMatrix.m21, childMatrix.m31,
                    childMatrix.m02, childMatrix.m12, childMatrix.m22, childMatrix.m32,
                    childMatrix.m03, childMatrix.m13, childMatrix.m23, childMatrix.m33
                };
                nativeBuffer.put(childMatrixColumnMajor);
                nativeBuffer.flip();
                GL11.glMultMatrix(nativeBuffer);
            }
            else if (this.bodyPartRuntimeMorph.getClass().getName().endsWith(".ChameleonMorph"))
            {
                Object morph = this.bodyPartRuntimeMorph;
                Object chameleonModel = morph.getClass().getMethod("getModel").invoke(morph);
                if (chameleonModel == null)
                {
                    logBodyPartMatrixDiagnostic(bone, "Chameleon getModel() returned null", null);
                    return null;
                }
                Object model = chameleonModel.getClass().getField("model").get(chameleonModel);
                Class<?> modelClass = Class.forName("mchorse.chameleon.lib.data.model.Model");
                Class<?> rendererClass = Class.forName("mchorse.chameleon.lib.render.ChameleonRenderer");
                java.lang.reflect.Method postRender =
                        rendererClass.getMethod("postRender", modelClass, String.class);
                float scale = 1.0F;
                try
                {
                    Object value = morph.getClass().getMethod("getScale", float.class).invoke(morph, 0.0F);
                    if (value instanceof Number) scale = ((Number) value).floatValue();
                }
                catch (Throwable ignored) {}
                GL11.glScalef(scale, scale, scale);
                /*
                 * Metamorph BodyPart.render() temporarily forces the child's
                 * renderYawOffset to zero. ChameleonMorph.render() then applies
                 * -renderYawOffset + 180 degrees around Y before its bones.
                 * Match that root transform when resolving the native pivot.
                 * This reverses the rest X/Z anchor without altering the
                 * already-working animation writeback.
                 */
                GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
                Object result = postRender.invoke(null, model, bone.getName());
                if (result instanceof Boolean && !((Boolean) result).booleanValue())
                {
                    logBodyPartMatrixDiagnostic(bone, "Chameleon renderer rejected bone name", null);
                    return null;
                }
            }
            else
            {
                logBodyPartMatrixDiagnostic(bone, "unsupported child morph type", null);
                return null;
            }

            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
            float[] matrix = new float[16];
            buffer.get(matrix);
            for (int i = 0; i < matrix.length; i++)
            {
                if (!isFinite(matrix[i]))
                {
                    logBodyPartMatrixDiagnostic(bone, "native matrix contains non-finite values", null);
                    return null;
                }
            }
            logBodyPartMatrixDiagnostic(bone, "native matrix computed; translation=("
                    + matrix[12] + ", " + matrix[13] + ", " + matrix[14] + ")", null);
            return matrix;
        }
        catch (Throwable error)
        {
            logBodyPartMatrixDiagnostic(bone, "native matrix evaluation failed", error);
            return null;
        }
        finally
        {
            if (pushed)
            {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(oldMatrixMode);
        }
    }

    private double[][] getBodyPartNativeLocalBoneAxes(AnimationBone bone)
    {
        float[] matrix = getBodyPartNativeLocalBoneMatrix(bone);
        if (matrix == null) return null;
        double[] x = new double[] {matrix[0], matrix[1], matrix[2]};
        double[] y = new double[] {matrix[4], matrix[5], matrix[6]};
        double[] z = new double[] {matrix[8], matrix[9], matrix[10]};
        normalize(x);
        normalize(y);
        normalize(z);
        return new double[][] {x, y, z};
    }

    private AnimationTransform getBodyPartBonePivot(
            AnimationBone bone,
            float frame)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationTransform key = bone.getTransformAt(frame);
        if (key == null)
        {
            key = new AnimationTransform();
        }

        /*
         * The Blockbuster model API and the editor do not use identical
         * signs for limb translation/rotation. ModelCustomRenderer maps
         * translation to (x, -y, -z) and rotation to (x, -y, -z).
         * Apply those signs to animation offsets as well as the imported
         * rest pose; otherwise the rendered limb moves while the calculated
         * gizmo pivot drifts in another direction.
         */
        boolean blockbusterTarget = isBlockbusterBodyPartTarget();
        float localY = bone.getLocalY()
                + (blockbusterTarget ? -key.getPositionY() : key.getPositionY());

        /*
         * This is the editor-side pivot, not the key writeback convention.
         * The Blockbuster renderer and the Body Parts attachment apply their
         * root mirror at different stages. Keep the imported rest pivot intact,
         * but map the animated X/Z offsets into the rendered pivot convention:
         * X is mirrored by the root conversion later in getBoneWorldPosition,
         * while Z is not. Without these offset signs the gizmo walks away from
         * the correctly moving rendered bone on X and Z.
         */
        /*
         * Blockbuster's rendered child model mirrors X at the root. The
         * generic world-pivot path mirrors the completed local pivot again,
         * so the rest-pose X must be converted separately from the animated
         * key offset. Keep the key's established sign (validated by drag
         * tests) and unmirror only the imported rest-pose coordinate here;
         * otherwise left/right limb pivots are exchanged.
         */
        float localX = (blockbusterTarget ? -bone.getLocalX() : bone.getLocalX())
                + (blockbusterTarget ? -key.getPositionX()
                        : (isChameleonBodyPartTarget()
                           ? -key.getPositionX()
                           : key.getPositionX()));
        float localZ = bone.getLocalZ() + key.getPositionZ();

        AnimationTransform local = new AnimationTransform();
        local.setPosition(localX, localY, localZ);
        local.setRotation(
                bone.getBaseRotationX() + key.getRotationX(),
                (blockbusterTarget ? -1.0F : 1.0F)
                        * (bone.getBaseRotationY() + key.getRotationY()),
                (blockbusterTarget ? -1.0F : 1.0F)
                        * (bone.getBaseRotationZ() + key.getRotationZ())
        );
        local.setScale(
                key.getScaleX(),
                key.getScaleY(),
                key.getScaleZ()
        );

        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return local;
        }

        AnimationTransform parentWorld = getBodyPartBonePivot(parent, frame);
        return parentWorld == null ? local : parentWorld.combine(local);
    }

    /**
     * The parent attachment is the only part of a Body Part pivot that
     * belongs to the actor skeleton. Prefer the engine-evaluated parent
     * bone position for the attachment origin, then add the child model's
     * own local offset separately.
     */
    private double[] getBodyPartAttachmentWorldPosition(
            AnimationTransform attachment,
            BlockbusterRecordFrame recordFrame)
    {
        if (this.bodyPartAttachmentBone != null)
        {
            double[] exact = getExactChameleonBoneWorldPosition(
                    this.bodyPartAttachmentBone, recordFrame);
            if (exact != null) return exact;

            exact = getExactEmoticonsBoneWorldPosition(
                    this.bodyPartAttachmentBone, recordFrame);
            if (exact != null) return exact;

            exact = getExactBlockbusterBoneWorldPosition(
                    this.bodyPartAttachmentBone);
            if (exact != null) return exact;
        }

        return attachment == null
                ? null
                : getWorldFromPivot(attachment, recordFrame);
    }

    /**
     * Native model scale applied by the attached child morph. Blockbuster's
     * source Model scale and CustomMorph scale are both applied before its
     * limb hierarchy, so the gizmo pivot must include them as well.
     */
    private float[] getBodyPartChildModelScale(int frame)
    {
        if (!isBlockbusterBodyPartTarget() || this.bodyPartTarget == null)
        {
            return new float[] {1.0F, 1.0F, 1.0F};
        }

        float sx = 1.0F;
        float sy = 1.0F;
        float sz = 1.0F;
        float morphScale = 1.0F;
        mchorse.blockbuster.api.Model model = null;

        try
        {
            mchorse.metamorph.api.morphs.AbstractMorph resolved =
                    this.bodyPartTarget.getMorphAt(frame);

            if (resolved instanceof mchorse.blockbuster_pack.morphs.CustomMorph)
            {
                mchorse.blockbuster_pack.morphs.CustomMorph custom =
                        (mchorse.blockbuster_pack.morphs.CustomMorph) resolved;

                morphScale = custom.scale;
                if (custom.model == null)
                {
                    custom.updateModel(true);
                }
                model = custom.model;
            }
        }
        catch (Throwable ignored)
        {
        }

        if (model == null)
        {
            try
            {
                Object candidate = this.bodyPartTarget.getModelAccess() == null
                        ? null
                        : this.bodyPartTarget.getModelAccess().getApiModel();

                if (candidate instanceof mchorse.blockbuster.api.Model)
                {
                    model = (mchorse.blockbuster.api.Model) candidate;
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        if (model != null && model.scale != null && model.scale.length >= 3)
        {
            sx = model.scale[0] * morphScale;
            sy = model.scale[1] * morphScale;
            sz = model.scale[2] * morphScale;
        }

        return new float[]
        {
            Math.abs(sx) < 0.0001F ? 1.0F : sx,
            Math.abs(sy) < 0.0001F ? 1.0F : sy,
            Math.abs(sz) < 0.0001F ? 1.0F : sz
        };
    }

    private boolean isChameleonBodyPartTarget()
    {
        if (this.bodyPartTarget == null)
        {
            return false;
        }

        mchorse.metamorph.api.morphs.AbstractMorph morph =
                this.bodyPartTarget.getMorphAt(this.gizmoFrame);

        if (morph == null)
        {
            morph = this.bodyPartTarget.getBaseMorph();
        }

        return morph != null &&
                morph.getClass().getName().endsWith(".ChameleonMorph");
    }

    private boolean isBlockbusterBodyPartTarget()
    {
        if (this.bodyPartTarget == null)
        {
            return false;
        }

        mchorse.metamorph.api.morphs.AbstractMorph morph =
                this.bodyPartTarget.getMorphAt(this.gizmoFrame);

        if (morph == null)
        {
            morph = this.bodyPartTarget.getBaseMorph();
        }

        if (morph != null)
        {
            return morph instanceof mchorse.blockbuster_pack.morphs.CustomMorph;
        }

        return this.bodyPartTarget.getModelAccess() != null;
    }

    /**
     * Resolve the actual world-space axes of the actor bone that carries
     * this Body Part. Prefer the source renderer's evaluated basis; use the
     * editor hierarchy only as a fallback for morphs without a native adapter.
     */
    private double[][] getBodyPartAttachmentWorldAxes(
            int frame,
            BlockbusterRecordFrame recordFrame)
    {
        AnimationBone attachmentBone = this.bodyPartAttachmentBone;

        if (attachmentBone == null)
        {
            return new double[][]
            {
                {1.0D, 0.0D, 0.0D},
                {0.0D, 1.0D, 0.0D},
                {0.0D, 0.0D, 1.0D}
            };
        }

        double[][] nativeAxes =
                getNativeBoneWorldAxes(attachmentBone, recordFrame);

        if (nativeAxes != null)
        {
            return nativeAxes;
        }

        AnimationTransform attachment =
                attachmentBone.getWorldPivotAt(frame);

        if (attachment == null)
        {
            return null;
        }

        return new double[][]
        {
            transformBoneDirection(
                    rotateVector(1.0F, 0.0F, 0.0F,
                            attachment.getRotationX(),
                            attachment.getRotationY(),
                            attachment.getRotationZ()),
                    recordFrame),
            transformBoneDirection(
                    rotateVector(0.0F, 1.0F, 0.0F,
                            attachment.getRotationX(),
                            attachment.getRotationY(),
                            attachment.getRotationZ()),
                    recordFrame),
            transformBoneDirection(
                    rotateVector(0.0F, 0.0F, 1.0F,
                            attachment.getRotationX(),
                            attachment.getRotationY(),
                            attachment.getRotationZ()),
                    recordFrame)
        };
    }

    /**
     * Transform an offset in the attachment's local model-pixel coordinates
     * through the evaluated attachment basis and convert it to world blocks.
     */
    private double[] addLocalOffsetToWorld(
            double[] base,
            double[][] axes,
            double x,
            double y,
            double z)
    {
        return new double[]
        {
            base[0] + (axes[0][0] * x + axes[1][0] * y + axes[2][0] * z) / 16.0D,
            base[1] + (axes[0][1] * x + axes[1][1] * y + axes[2][1] * z) / 16.0D,
            base[2] + (axes[0][2] * x + axes[1][2] * y + axes[2][2] * z) / 16.0D
        };
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
         * Body Part internal bones must use the same coordinate adapter
         * as their model renderer. Chameleon applies an inverted local X
         * translation, so its evaluated hierarchy cannot use the generic
         * AnimationBone pivot calculation.
         */
        boolean chameleonTarget = this.bodyPartTarget != null
                ? isChameleonBodyPartTarget()
                : this.chameleonCoordinateSpace;

        boolean blockbusterBodyPart = isBlockbusterBodyPartTarget();
        float[] nativeChildMatrix =
                this.bodyPartTarget != null &&
                bone != this.bodyPartAttachmentBone
                        ? getBodyPartNativeLocalBoneMatrix(bone)
                        : null;

        AnimationTransform pivot;
        /*
         * The native child-renderer matrix is a reliable rest-pose anchor,
         * but it does not include the editor's current AnimationTransform
         * key. Using its translation directly therefore disconnects the
         * gizmo from an edited internal bone (most visibly on Blockbuster Z).
         *
         * For Blockbuster Body Parts, evaluate the pivot from the editor bone
         * hierarchy so keyframe translation is included. The conversion below
         * applies the same root mirror and child scale as the non-native path.
         * Keep the native matrix path for Chameleon, whose renderer pivot is
         * needed to match its own model hierarchy.
         */
        if (nativeChildMatrix != null && !blockbusterBodyPart)
        {
            pivot = new AnimationTransform();

            /*
             * Chameleon postRender() supplies the unmodified native pivot,
             * but the editor's Body Parts keys are evaluated separately.
             * Preserve that exact native rest anchor and add the evaluated
             * offset relative to the imported rest-pose hierarchy.
             *
             * Do not replace the native origin with AnimationBone local
             * coordinates: those can differ due to Chameleon's own model
             * hierarchy/centering. The delta also includes parent bone
             * motion, so the gizmo follows the selected bone after edits.
             */
            float px = nativeChildMatrix[12] * 16.0F;
            float py = nativeChildMatrix[13] * 16.0F;
            float pz = nativeChildMatrix[14] * 16.0F;

            /*
             * Emoticons' native BOBJ matrix already contains the current pose
             * and gives the selected CHILD bone pivot. Chameleon alone needs
             * its separate animated-delta correction below.
             */
            if (chameleonTarget)
            {
                AnimationTransform evaluated = getChameleonWorldPivot(bone, frame);
                AnimationTransform rest = getChameleonRestPivot(bone);
                if (evaluated != null && rest != null)
                {
                    /*
                     * ChameleonRenderer.postRender() already contains the
                     * current ModelBone translation and the initial bone pivot.
                     * In the Body Parts attachment basis the X/Z displacement
                     * of that native position is mirrored relative to the
                     * correctly moving rendered child. A single evaluated
                     * X/Z delta cancels the native displacement (the reported
                     * frozen gizmo). Add twice the desired delta to reflect
                     * the live native displacement around its rest anchor.
                     *
                     * Y is already synchronized, so its established path
                     * must not be modified.
                     */
                    float dx = evaluated.getPositionX() - rest.getPositionX();
                    float dy = evaluated.getPositionY() - rest.getPositionY();
                    float dz = evaluated.getPositionZ() - rest.getPositionZ();
                    /*
                     * With the missing ChameleonMorph root Y=180 transform
                     * restored in the native matrix above, native X/Z now
                     * advance with the bone. The old doubled corrections
                     * would move the gizmo too far; keep only the existing
                     * Y compensation, which was already correct.
                     */
                    py += dy;

                    diagnoseChameleonBodyPartPivot(
                            bone, frame, nativeChildMatrix, dx, dy, dz,
                            px, py, pz);
                }
            }

            pivot.setPosition(px, py, pz);
        }
        else
        {
            pivot = chameleonTarget
                    ? getChameleonWorldPivot(bone, frame)
                    : (this.bodyPartTarget != null
                       ? getBodyPartBonePivot(bone, frame)
                       : bone.getWorldPivotAt(frame));
        }

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
                 bone == this.bodyPartAttachmentBone))
        {
            AnimationTransform attachmentPivot =
                    this.gizmoAttachmentBone.getWorldPivotAt(frame);

            if (attachmentPivot != null)
            {
                pivot = attachmentPivot;
            }
        }

        /*
         * Internal Body Part pivots are evaluated in the child model's own
         * coordinates, then transformed by the child's root convention,
         * Body Part global transform, and the actual rendered attachment
         * basis. This mirrors the runtime order:
         *
         * attachment -> BodyPart translate/rotate/scale -> child morph.
         */
        if (this.bodyPartTarget != null &&
                this.bodyPartAttachmentBone != null &&
                bone != this.bodyPartAttachmentBone)
        {
            AnimationTransform attachment =
                    this.bodyPartAttachmentBone.getWorldPivotAt(frame);

            double[] base = getBodyPartAttachmentWorldPosition(
                    attachment,
                    recordFrame
            );

            double[][] attachmentAxes =
                    getBodyPartAttachmentWorldAxes(frame, recordFrame);

            if (base == null || attachmentAxes == null)
            {
                return null;
            }

            double x = pivot.getPositionX();
            double y = pivot.getPositionY();
            double z = pivot.getPositionZ();

            /*
             * Blockbuster's child CustomMorph mirrors X/Y at its model root.
             * This must happen BEFORE the Body Part's global rotation/scale,
             * not as a final sign flip after attachment rotation.
             */
            if (nativeChildMatrix == null || blockbusterBodyPart)
            {
                if (blockbusterBodyPart)
                {
                    x = -x;
                    y = -y;
                }
                float[] childScale = getBodyPartChildModelScale(frame);
                x *= childScale[0];
                y *= childScale[1];
                z *= childScale[2];
            }

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

            return addLocalOffsetToWorld(base, attachmentAxes, x, y, z);
        }

        /*
         * The whole-model gizmo origin is the attachment pivot plus the
         * Body Part's local translation. Use the attachment's rendered
         * basis and world position; rebuilding it from editor Euler angles
         * loses the parent's actual pose/yaw.
         */
        if (this.globalTransformTarget != null &&
                this.bodyPartAttachmentBone != null)
        {
            AnimationTransform attachment =
                    this.bodyPartAttachmentBone.getWorldPivotAt(frame);

            double[] base = getBodyPartAttachmentWorldPosition(
                    attachment,
                    recordFrame
            );

            double[][] attachmentAxes =
                    getBodyPartAttachmentWorldAxes(frame, recordFrame);

            if (base == null || attachmentAxes == null)
            {
                return null;
            }

            return addLocalOffsetToWorld(
                    base,
                    attachmentAxes,
                    this.globalTransformTarget.getPositionX(),
                    this.globalTransformTarget.getPositionY(),
                    this.globalTransformTarget.getPositionZ()
            );
        }

        return getWorldFromPivot(
                pivot,
                recordFrame
        );
    }

    private long lastChameleonBodyPartPivotDiagnosticMillis;

    /**
     * Diagnostic only. Distinguish a wrong native rest anchor (pivot offset
     * even at zero key) from a wrong animated displacement (gizmo drift).
     */
    private void diagnoseChameleonBodyPartPivot(
            AnimationBone bone, int frame, float[] nativeMatrix,
            float dx, float dy, float dz,
            float px, float py, float pz)
    {
        if (this.mode != Mode.POSITION || bone == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastChameleonBodyPartPivotDiagnosticMillis < 1000L)
        {
            return;
        }
        this.lastChameleonBodyPartPivotDiagnosticMillis = now;
        System.out.println("[BBS ChameleonPivot] bone=" + bone.getName()
                + " frame=" + frame
                + " native=(" + nativeMatrix[12] * 16.0F + ","
                + nativeMatrix[13] * 16.0F + ","
                + nativeMatrix[14] * 16.0F + ")"
                + " keyDelta=(" + dx + "," + dy + "," + dz + ")"
                + " resolved=(" + px + "," + py + "," + pz + ")");
    }

    /**
     * Imported Chameleon rest pivot in the same hierarchy coordinate space
     * as getChameleonWorldPivot(), but without animation-key translations.
     * Used only to compute the live offset from a native renderer anchor.
     */
    private AnimationTransform getChameleonRestPivot(AnimationBone bone)
    {
        if (bone == null)
        {
            return null;
        }

        AnimationTransform rest = new AnimationTransform();
        rest.setPosition(
                bone.getLocalX(), bone.getLocalY(), bone.getLocalZ());
        rest.setRotation(
                bone.getBaseRotationX(),
                bone.getBaseRotationY(),
                bone.getBaseRotationZ());

        AnimationBone parent = bone.getParent();
        if (parent == null)
        {
            return rest;
        }

        AnimationTransform parentRest = getChameleonRestPivot(parent);
        return parentRest == null ? rest : parentRest.combine(rest);
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
        /*
         * Body Parts uses an isolated native Chameleon postRender() matrix
         * as its REST pivot, then adds the key displacement computed here.
         * After the X/Z translation writeback correction, the animated bone
         * advances with +keyX and -keyZ in this editor-side pivot space.
         * The previous -keyX/+keyZ reconstruction made the position gizmo
         * move opposite the correctly moving rendered bone.
         *
         * Do not modify the native/rest pivot, Y or rotation here.
         */
        local.setPosition(
                bone.getLocalX() + animation.getPositionX(),
                bone.getLocalY() + animation.getPositionY(),
                bone.getLocalZ() - animation.getPositionZ()
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
        if(camera==null)return null;
        float[] pixel=PreviewCameraProjection.project(
                x,y,z,camera.sample(),
                viewportX,viewportY,viewportWidth,viewportHeight);
        return pixel==null?null:new ScreenPoint(pixel[0],pixel[1]);
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

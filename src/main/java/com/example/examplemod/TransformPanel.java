package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class TransformPanel
{
    private static final int PANEL_WIDTH = 175;
    private static final int PANEL_HEIGHT = 245;

    private static final float POSITION_STEP = 0.1F;
    private static final float ROTATION_STEP = 1.0F;
    private static final float SCALE_STEP = 0.01F;

    private int x;
    private int y;

    /*
     * POSITION
     */

    private final AnimationValueControl positionXControl =
            new AnimationValueControl(
                    "X",
                    0.0F,
                    POSITION_STEP
            );

    private final AnimationValueControl positionYControl =
            new AnimationValueControl(
                    "Y",
                    0.0F,
                    POSITION_STEP
            );

    private final AnimationValueControl positionZControl =
            new AnimationValueControl(
                    "Z",
                    0.0F,
                    POSITION_STEP
            );

    /*
     * ROTATION
     */

    private final AnimationValueControl rotationXControl =
            new AnimationValueControl(
                    "X",
                    0.0F,
                    ROTATION_STEP
            );

    private final AnimationValueControl rotationYControl =
            new AnimationValueControl(
                    "Y",
                    0.0F,
                    ROTATION_STEP
            );

    private final AnimationValueControl rotationZControl =
            new AnimationValueControl(
                    "Z",
                    0.0F,
                    ROTATION_STEP
            );

    /*
     * SCALE
     */

    private final AnimationValueControl scaleXControl =
            new AnimationValueControl(
                    "X",
                    1.0F,
                    SCALE_STEP
            );

    private final AnimationValueControl scaleYControl =
            new AnimationValueControl(
                    "Y",
                    1.0F,
                    SCALE_STEP
            );

    private final AnimationValueControl scaleZControl =
            new AnimationValueControl(
                    "Z",
                    1.0F,
                    SCALE_STEP
            );

    /*
     * Currently edited control.
     */

    private AnimationValueControl activeControl;

    public TransformPanel()
    {
    }

    public void setPosition(
            int x,
            int y)
    {
        this.x = x;
        this.y = y;
    }

    public void draw(
            Minecraft mc,
            AnimationKeyframe keyframe,
            AnimationTransform currentTransform,
            int currentFrame)
    {
        if (keyframe == null)
        {
            return;
        }

        FontRenderer font =
                mc.fontRenderer;

        AnimationTransform transform =
                keyframe.getTransform();

        /*
         * PANEL
         */

        drawRect(
                mc,
                x,
                y,
                x + PANEL_WIDTH,
                y + PANEL_HEIGHT,
                0xFF292A2D
        );

        /*
         * HEADER
         */

        drawRect(
                mc,
                x,
                y,
                x + PANEL_WIDTH,
                y + 20,
                0xFF303134
        );

        font.drawString(
                "Transform",
                x + 8,
                y + 6,
                0xFFFFFF
        );

        font.drawString(
                "Key: " + keyframe.getFrame(),
                x + 95,
                y + 6,
                0xAAAAAA
        );

        /*
         * POSITION
         */

        font.drawString(
                "Position",
                x + 8,
                y + 28,
                0x55FFFF
        );

        positionXControl.setPosition(
                x + 8,
                y + 43
        );

        positionYControl.setPosition(
                x + 8,
                y + 61
        );

        positionZControl.setPosition(
                x + 8,
                y + 79
        );

        positionXControl.setValue(
                transform.getPositionX()
        );

        positionYControl.setValue(
                transform.getPositionY()
        );

        positionZControl.setValue(
                transform.getPositionZ()
        );

        positionXControl.draw(mc);
        positionYControl.draw(mc);
        positionZControl.draw(mc);

        /*
         * ROTATION
         */

        font.drawString(
                "Rotation",
                x + 8,
                y + 101,
                0x55FF55
        );

        rotationXControl.setPosition(
                x + 8,
                y + 116
        );

        rotationYControl.setPosition(
                x + 8,
                y + 134
        );

        rotationZControl.setPosition(
                x + 8,
                y + 152
        );

        rotationXControl.setValue(
                transform.getRotationX()
        );

        rotationYControl.setValue(
                transform.getRotationY()
        );

        rotationZControl.setValue(
                transform.getRotationZ()
        );

        rotationXControl.draw(mc);
        rotationYControl.draw(mc);
        rotationZControl.draw(mc);

        /*
         * SCALE
         */

        font.drawString(
                "Scale",
                x + 8,
                y + 174,
                0xFFFF55
        );

        scaleXControl.setPosition(
                x + 8,
                y + 189
        );

        scaleYControl.setPosition(
                x + 8,
                y + 207
        );

        scaleZControl.setPosition(
                x + 8,
                y + 225
        );

        scaleXControl.setValue(
                transform.getScaleX()
        );

        scaleYControl.setValue(
                transform.getScaleY()
        );

        scaleZControl.setValue(
                transform.getScaleZ()
        );

        scaleXControl.draw(mc);
        scaleYControl.draw(mc);
        scaleZControl.draw(mc);
    }

    /*
     * =========================
     * CLICK
     * =========================
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            AnimationKeyframe keyframe)
    {
        if (keyframe == null)
        {
            return false;
        }

        if (mouseButton != 0)
        {
            return false;
        }

        this.activeControl = null;

        if (positionXControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    positionXControl;

            return true;
        }

        if (positionYControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    positionYControl;

            return true;
        }

        if (positionZControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    positionZControl;

            return true;
        }

        if (rotationXControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    rotationXControl;

            return true;
        }

        if (rotationYControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    rotationYControl;

            return true;
        }

        if (rotationZControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    rotationZControl;

            return true;
        }

        if (scaleXControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    scaleXControl;

            return true;
        }

        if (scaleYControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    scaleYControl;

            return true;
        }

        if (scaleZControl.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.activeControl =
                    scaleZControl;

            return true;
        }

        return false;
    }

    /*
     * =========================
     * DRAG
     * =========================
     */

    public void mouseDragged(
            int mouseX,
            int mouseY,
            AnimationKeyframe keyframe)
    {
        if (keyframe == null)
        {
            return;
        }

        if (this.activeControl == null)
        {
            return;
        }

        this.activeControl.mouseDragged(
                mouseX,
                mouseY
        );

        writeControlsToKeyframe(
                keyframe
        );
    }

    /*
     * =========================
     * RELEASE
     * =========================
     */

    public void mouseReleased(
            int mouseButton)
    {
        if (this.activeControl != null)
        {
            this.activeControl.mouseReleased(
                    mouseButton
            );
        }

        if (mouseButton == 0)
        {
            this.activeControl = null;
        }
    }

    /*
     * =========================
     * KEYBOARD
     * =========================
     */

    public void keyTyped(
            char typedChar,
            int keyCode,
            AnimationKeyframe keyframe)
    {
        if (keyframe == null)
        {
            return;
        }

        if (this.activeControl == null)
        {
            return;
        }

        this.activeControl.keyTyped(
                typedChar,
                keyCode
        );

        writeControlsToKeyframe(
                keyframe
        );
    }

    /*
     * =========================
     * SAVE VALUES
     * =========================
     */

    private void writeControlsToKeyframe(
            AnimationKeyframe keyframe)
    {
        AnimationTransform transform =
                keyframe.getTransform();

        transform.setPosition(
                positionXControl.getValue(),
                positionYControl.getValue(),
                positionZControl.getValue()
        );

        transform.setRotation(
                rotationXControl.getValue(),
                rotationYControl.getValue(),
                rotationZControl.getValue()
        );

        transform.setScale(
                scaleXControl.getValue(),
                scaleYControl.getValue(),
                scaleZControl.getValue()
        );
    }

    /*
     * =========================
     * DRAW RECT
     * =========================
     */

    private void drawRect(
            Minecraft mc,
            int left,
            int top,
            int right,
            int bottom,
            int color)
    {
        net.minecraft.client.gui.Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }
}
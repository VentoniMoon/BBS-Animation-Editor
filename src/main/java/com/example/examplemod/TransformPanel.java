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

    /*
     * =========================================================
     * COLORS
     * =========================================================
     */

    private static final int PANEL_BACKGROUND = 0xFF252629;
    private static final int PANEL_BORDER = 0xFF111214;

    private static final int HEADER_BACKGROUND = 0xFF303134;
    private static final int HEADER_BOTTOM = 0xFF18191B;

    private static final int SECTION_LINE = 0xFF3A3B3E;

    private static final int TEXT = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFF999999;

    /*
     * Section accents
     *
     * Эти цвета намеренно не зависят от темы.
     * Они различают Position / Rotation / Scale.
     */

    private static final int POSITION_ACCENT = 0xFF55FFFF;
    private static final int ROTATION_ACCENT = 0xFF55FF55;
    private static final int SCALE_ACCENT = 0xFFFFFF55;

    /*
     * =========================================================
     * THEME ACCENT
     * =========================================================
     */

    private static int getAccentColor()
    {
        return EditorThemeManager
                .get()
                .getAccent();
    }

    /*
     * =========================================================
     * POSITION
     * =========================================================
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
     * =========================================================
     * ROTATION
     * =========================================================
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
     * =========================================================
     * SCALE
     * =========================================================
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
     * =========================================================
     * CURRENT CONTROL
     * =========================================================
     */

    private AnimationValueControl activeControl;

    private int x;
    private int y;

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

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

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
                x - 1,
                y - 1,
                x + PANEL_WIDTH + 1,
                y + PANEL_HEIGHT + 1,
                PANEL_BORDER
        );

        drawRect(
                mc,
                x,
                y,
                x + PANEL_WIDTH,
                y + PANEL_HEIGHT,
                PANEL_BACKGROUND
        );

        /*
         * HEADER
         */

        drawRect(
                mc,
                x,
                y,
                x + PANEL_WIDTH,
                y + 22,
                HEADER_BACKGROUND
        );

        drawRect(
                mc,
                x,
                y + 21,
                x + PANEL_WIDTH,
                y + 22,
                HEADER_BOTTOM
        );

        /*
         * Header accent.
         *
         * Теперь использует текущую тему редактора.
         */

        drawRect(
                mc,
                x,
                y,
                x + 3,
                y + 22,
                getAccentColor()
        );

        font.drawString(
                "Transform",
                x + 9,
                y + 6,
                TEXT
        );

        String keyText =
                "Key " +
                        keyframe.getFrame();

        int keyWidth =
                font.getStringWidth(
                        keyText
                );

        font.drawString(
                keyText,
                x + PANEL_WIDTH - keyWidth - 8,
                y + 6,
                TEXT_SECONDARY
        );

        /*
         * =====================================================
         * POSITION
         * =====================================================
         */

        drawSectionHeader(
                mc,
                font,
                "Position",
                x + 7,
                y + 28,
                POSITION_ACCENT
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
         * Separator.
         */

        drawRect(
                mc,
                x + 8,
                y + 96,
                x + PANEL_WIDTH - 8,
                y + 97,
                SECTION_LINE
        );

        /*
         * =====================================================
         * ROTATION
         * =====================================================
         */

        drawSectionHeader(
                mc,
                font,
                "Rotation",
                x + 7,
                y + 101,
                ROTATION_ACCENT
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
         * Separator.
         */

        drawRect(
                mc,
                x + 8,
                y + 169,
                x + PANEL_WIDTH - 8,
                y + 170,
                SECTION_LINE
        );

        /*
         * =====================================================
         * SCALE
         * =====================================================
         */

        drawSectionHeader(
                mc,
                font,
                "Scale",
                x + 7,
                y + 174,
                SCALE_ACCENT
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
     * =========================================================
     * SECTION HEADER
     * =========================================================
     */

    private void drawSectionHeader(
            Minecraft mc,
            FontRenderer font,
            String title,
            int drawX,
            int drawY,
            int accent)
    {
        drawRect(
                mc,
                drawX,
                drawY + 1,
                drawX + 3,
                drawY + 10,
                accent
        );

        font.drawString(
                title,
                drawX + 7,
                drawY,
                accent
        );
    }

    /*
     * =========================================================
     * FINISH EDITING
     * =========================================================
     */

    public void finishEditing()
    {
        if (this.activeControl == null)
        {
            return;
        }

        if (this.activeControl.isEditing())
        {
            this.activeControl.finishEditing();
        }

        this.activeControl = null;
    }

    /*
     * =========================================================
     * CLICK
     * =========================================================
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

        if (
                this.activeControl != null &&
                        this.activeControl.isEditing()
        )
        {
            boolean insideActiveControl =
                    this.activeControl.contains(
                            mouseX,
                            mouseY
                    );

            if (!insideActiveControl)
            {
                this.activeControl.finishEditing();
                this.activeControl = null;
            }
        }

        if (
                this.positionXControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.positionXControl;

            this.positionXControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.positionYControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.positionYControl;

            this.positionYControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.positionZControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.positionZControl;

            this.positionZControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.rotationXControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.rotationXControl;

            this.rotationXControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.rotationYControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.rotationYControl;

            this.rotationYControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.rotationZControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.rotationZControl;

            this.rotationZControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.scaleXControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.scaleXControl;

            this.scaleXControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.scaleYControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.scaleYControl;

            this.scaleYControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        if (
                this.scaleZControl.contains(
                        mouseX,
                        mouseY
                )
        )
        {
            this.activeControl =
                    this.scaleZControl;

            this.scaleZControl.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * DRAG
     * =========================================================
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

        if (this.activeControl.isEditing())
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
     * =========================================================
     * RELEASE
     * =========================================================
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

        if (
                mouseButton == 0 &&
                        (
                                this.activeControl == null ||
                                        !this.activeControl.isEditing()
                        )
        )
        {
            this.activeControl = null;
        }
    }

    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

    public boolean keyTyped(
            char typedChar,
            int keyCode,
            AnimationKeyframe keyframe)
    {
        if (keyframe == null)
        {
            return false;
        }

        if (this.activeControl == null)
        {
            return false;
        }

        boolean handled =
                this.activeControl.keyTyped(
                        typedChar,
                        keyCode
                );

        if (handled)
        {
            if (!this.activeControl.isEditing())
            {
                writeControlsToKeyframe(
                        keyframe
                );
            }

            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * SAVE VALUES
     * =========================================================
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
     * =========================================================
     * DRAW RECT
     * =========================================================
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
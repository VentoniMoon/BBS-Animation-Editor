package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

public class AnimationValueControl
{
    private int x;
    private int y;

    private int width = 85;
    private int height = 16;

    private String label;

    private float value;
    private float step;

    private boolean dragging = false;
    private boolean editing = false;

    private int lastMouseX;

    private String inputText = "";

    private long lastClickTime = 0L;

    private static final long DOUBLE_CLICK_TIME = 300L;

    public AnimationValueControl(
            String label,
            float value,
            float step)
    {
        this.label = label;
        this.value = value;
        this.step = step;
    }

    public void setPosition(
            int x,
            int y)
    {
        this.x = x;
        this.y = y;
    }

    public void setValue(float value)
    {
        if (!this.dragging && !this.editing)
        {
            this.value = value;
        }
    }

    public float getValue()
    {
        return this.value;
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (mouseButton != 0)
        {
            return false;
        }

        if (!isInside(mouseX, mouseY))
        {
            return false;
        }

        long currentTime =
                System.currentTimeMillis();

        if (
                currentTime - this.lastClickTime
                        <= DOUBLE_CLICK_TIME
        )
        {
            startEditing();
        }
        else
        {
            this.dragging = true;
            this.lastMouseX = mouseX;
        }

        this.lastClickTime = currentTime;

        return true;
    }

    public void mouseDragged(
            int mouseX,
            int mouseY)
    {
        if (!this.dragging || this.editing)
        {
            return;
        }

        /*
         * Horizontal movement controls the value.
         *
         * Mouse right  -> increase
         * Mouse left   -> decrease
         */

        int difference =
                mouseX - this.lastMouseX;

        if (difference != 0)
        {
            this.value +=
                    difference * this.step;

            this.lastMouseX = mouseX;
        }
    }

    public void mouseReleased(
            int mouseButton)
    {
        if (mouseButton == 0)
        {
            this.dragging = false;
        }
    }

    public void keyTyped(
            char typedChar,
            int keyCode)
    {
        if (!this.editing)
        {
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            this.editing = false;
            this.inputText = "";

            return;
        }

        if (
                keyCode == Keyboard.KEY_RETURN ||
                        keyCode == Keyboard.KEY_NUMPADENTER
        )
        {
            applyInput();

            return;
        }

        if (keyCode == Keyboard.KEY_BACK)
        {
            if (this.inputText.length() > 0)
            {
                this.inputText =
                        this.inputText.substring(
                                0,
                                this.inputText.length() - 1
                        );
            }

            return;
        }

        if (
                Character.isDigit(typedChar) ||
                        typedChar == '-' ||
                        typedChar == '+' ||
                        typedChar == '.' ||
                        typedChar == ','
        )
        {
            if (typedChar == ',')
            {
                typedChar = '.';
            }

            this.inputText += typedChar;
        }
    }

    private void startEditing()
    {
        this.dragging = false;
        this.editing = true;

        this.inputText =
                format(this.value);
    }

    private void applyInput()
    {
        try
        {
            this.value =
                    Float.parseFloat(
                            this.inputText
                    );
        }
        catch (NumberFormatException e)
        {
            /*
             * Leave the old value unchanged.
             */
        }

        this.editing = false;
        this.inputText = "";
    }

    public boolean isEditing()
    {
        return this.editing;
    }

    private boolean isInside(
            int mouseX,
            int mouseY)
    {
        return mouseX >= this.x
                && mouseX <= this.x + this.width
                && mouseY >= this.y
                && mouseY <= this.y + this.height;
    }

    public void draw(
            Minecraft mc)
    {
        FontRenderer font =
                mc.fontRenderer;

        int backgroundColor =
                this.editing || this.dragging
                        ? 0xFF55575A
                        : 0xFF3A3B3E;

        net.minecraft.client.gui.Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                backgroundColor
        );

        font.drawString(
                this.label + ":",
                this.x + 5,
                this.y + 4,
                0xFFFFFF
        );

        String text =
                this.editing
                        ? this.inputText + "_"
                        : format(this.value);

        int valueWidth =
                font.getStringWidth(text);

        font.drawString(
                text,
                this.x + this.width
                        - valueWidth
                        - 5,
                this.y + 4,
                this.editing
                        ? 0xFFFFFF
                        : 0xCCCCCC
        );
    }

    private String format(
            float value)
    {
        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        );
    }
}
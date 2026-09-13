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

    private boolean selectAll = false;

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

    public void setValue(
            float value)
    {
        /*
         * Никогда не перезаписываем значение,
         * пока пользователь его редактирует
         * или двигает мышью.
         */
        if (
                !this.dragging &&
                        !this.editing
        )
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

        /*
         * Второй клик быстро после первого
         * переводит контрол в текстовый режим.
         */
        if (
                currentTime -
                        this.lastClickTime
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

        this.lastClickTime =
                currentTime;

        return true;
    }

    public void mouseDragged(
            int mouseX,
            int mouseY)
    {
        if (
                !this.dragging ||
                        this.editing
        )
        {
            return;
        }

        int difference =
                mouseX -
                        this.lastMouseX;

        if (difference != 0)
        {
            this.value +=
                    difference *
                            this.step;

            this.lastMouseX =
                    mouseX;
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

        /*
         * ESCAPE
         *
         * Отмена текущего ввода.
         */
        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            this.editing = false;
            this.inputText = "";
            this.selectAll = false;

            return;
        }

        /*
         * ENTER
         *
         * Подтвердить значение.
         */
        if (
                keyCode == Keyboard.KEY_RETURN ||
                        keyCode ==
                                Keyboard.KEY_NUMPADENTER
        )
        {
            applyInput();

            return;
        }

        /*
         * CTRL+A
         *
         * Выделить всё.
         */
        boolean ctrlDown =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                )
                        ||
                        Keyboard.isKeyDown(
                                Keyboard.KEY_RCONTROL
                        );

        if (
                ctrlDown &&
                        keyCode == Keyboard.KEY_A
        )
        {
            this.selectAll = true;

            return;
        }

        /*
         * BACKSPACE
         */
        if (keyCode == Keyboard.KEY_BACK)
        {
            if (this.selectAll)
            {
                this.inputText = "";
                this.selectAll = false;

                return;
            }

            if (
                    this.inputText.length() >
                            0
            )
            {
                this.inputText =
                        this.inputText.substring(
                                0,
                                this.inputText.length() - 1
                        );
            }

            return;
        }

        /*
         * DELETE
         *
         * Если выделено всё — очищаем поле.
         */
        if (keyCode == Keyboard.KEY_DELETE)
        {
            if (this.selectAll)
            {
                this.inputText = "";
                this.selectAll = false;
            }

            return;
        }

        /*
         * HOME / END
         *
         * Сейчас полноценного курсора нет,
         * поэтому эти клавиши просто
         * снимают выделение.
         */
        if (
                keyCode == Keyboard.KEY_HOME ||
                        keyCode == Keyboard.KEY_END
        )
        {
            this.selectAll = false;

            return;
        }

        /*
         * DIGITS
         */
        if (Character.isDigit(typedChar))
        {
            appendInputChar(
                    typedChar
            );

            return;
        }

        /*
         * MINUS
         */
        if (typedChar == '-')
        {
            if (
                    this.inputText.length() == 0
            )
            {
                appendInputChar(
                        typedChar
                );
            }

            return;
        }

        /*
         * PLUS
         */
        if (typedChar == '+')
        {
            if (
                    this.inputText.length() == 0
            )
            {
                appendInputChar(
                        typedChar
                );
            }

            return;
        }

        /*
         * DECIMAL POINT
         */
        if (
                typedChar == '.' ||
                        typedChar == ','
        )
        {
            if (
                    this.inputText.indexOf(
                            '.'
                    ) == -1
            )
            {
                appendInputChar(
                        '.'
                );
            }
        }
    }

    private void appendInputChar(
            char character)
    {
        if (this.selectAll)
        {
            this.inputText = "";
            this.selectAll = false;
        }

        this.inputText +=
                character;
    }

    private void startEditing()
    {
        this.dragging = false;
        this.editing = true;

        this.inputText =
                format(
                        this.value
                );

        /*
         * При первом вводе следующая
         * напечатанная цифра заменит
         * старое значение.
         */
        this.selectAll = true;
    }

    private void applyInput()
    {
        if (
                this.inputText == null ||
                        this.inputText.length() == 0 ||
                        this.inputText.equals("-") ||
                        this.inputText.equals("+") ||
                        this.inputText.equals(".") ||
                        this.inputText.equals("-.") ||
                        this.inputText.equals("+.")
        )
        {
            this.editing = false;
            this.inputText = "";
            this.selectAll = false;

            return;
        }

        try
        {
            this.value =
                    Float.parseFloat(
                            this.inputText
                    );
        }
        catch (NumberFormatException exception)
        {
            /*
             * Оставляем старое значение.
             */
        }

        this.editing = false;
        this.inputText = "";
        this.selectAll = false;
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
                && mouseX <=
                this.x + this.width
                && mouseY >= this.y
                && mouseY <=
                this.y + this.height;
    }

    public void draw(
            Minecraft mc)
    {
        FontRenderer font =
                mc.fontRenderer;

        int backgroundColor =
                this.editing ||
                        this.dragging
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

        String text;

        if (this.editing)
        {
            text =
                    this.inputText +
                            "_";
        }
        else
        {
            text =
                    format(
                            this.value
                    );
        }

        int valueWidth =
                font.getStringWidth(
                        text
                );

        font.drawString(
                text,
                this.x +
                        this.width -
                        valueWidth -
                        5,
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
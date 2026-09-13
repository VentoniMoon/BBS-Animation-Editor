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
         * Пока пользователь редактирует значение
         * или двигает его мышью, внешний transform
         * не должен перезаписывать состояние контрола.
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

        if (!contains(mouseX, mouseY))
        {
            return false;
        }

        long currentTime =
                System.currentTimeMillis();

        /*
         * Второй клик превращает контрол
         * в текстовое поле.
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
            /*
             * Обычный клик начинает drag.
             */
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

    /**
     * Обрабатывает клавиатуру.
     *
     * @return true если клавиша была обработана
     */
    public boolean keyTyped(
            char typedChar,
            int keyCode)
    {
        if (!this.editing)
        {
            return false;
        }

        /*
         * ESCAPE
         *
         * Отменяем только редактирование
         * этого значения.
         */
        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            cancelEditing();

            return true;
        }

        /*
         * ENTER
         *
         * Применяем введённое значение.
         */
        if (
                keyCode == Keyboard.KEY_RETURN ||
                        keyCode ==
                                Keyboard.KEY_NUMPADENTER
        )
        {
            applyInput();

            return true;
        }

        boolean ctrlDown =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                )
                        ||
                        Keyboard.isKeyDown(
                                Keyboard.KEY_RCONTROL
                        );

        /*
         * CTRL + A
         */
        if (
                ctrlDown &&
                        keyCode == Keyboard.KEY_A
        )
        {
            this.selectAll = true;

            return true;
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

                return true;
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

            return true;
        }

        /*
         * DELETE
         */
        if (keyCode == Keyboard.KEY_DELETE)
        {
            if (this.selectAll)
            {
                this.inputText = "";
                this.selectAll = false;
            }

            return true;
        }

        /*
         * HOME / END
         *
         * Полноценного курсора пока нет.
         */
        if (
                keyCode == Keyboard.KEY_HOME ||
                        keyCode == Keyboard.KEY_END
        )
        {
            this.selectAll = false;

            return true;
        }

        /*
         * ЦИФРЫ
         */
        if (Character.isDigit(typedChar))
        {
            appendInputChar(
                    typedChar
            );

            return true;
        }

        /*
         * МИНУС
         */
        if (typedChar == '-')
        {
            if (
                    this.inputText.length() == 0 ||
                            this.selectAll
            )
            {
                appendInputChar('-');
            }

            return true;
        }

        /*
         * ПЛЮС
         */
        if (typedChar == '+')
        {
            if (
                    this.inputText.length() == 0 ||
                            this.selectAll
            )
            {
                appendInputChar('+');
            }

            return true;
        }

        /*
         * ДЕСЯТИЧНАЯ ТОЧКА
         */
        if (
                typedChar == '.' ||
                        typedChar == ','
        )
        {
            if (
                    this.inputText.indexOf('.') == -1
            )
            {
                appendInputChar('.');
            }

            return true;
        }

        /*
         * Любая другая клавиша во время
         * редактирования также считается
         * обработанной, чтобы она не уходила
         * дальше в AnimationEditorScreen.
         */
        return true;
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
         * Первая введённая цифра заменит
         * текущее значение.
         */
        this.selectAll = true;
    }

    private void cancelEditing()
    {
        this.editing = false;
        this.dragging = false;
        this.inputText = "";
        this.selectAll = false;
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
            cancelEditing();

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
             * Если число некорректное,
             * оставляем старое значение.
             */
        }

        this.editing = false;
        this.dragging = false;
        this.inputText = "";
        this.selectAll = false;
    }

    public boolean isEditing()
    {
        return this.editing;
    }

    public boolean contains(
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
    public boolean finishEditing()
    {
        if (!this.editing)
        {
            return false;
        }

        this.applyInput();

        return true;
    }

}
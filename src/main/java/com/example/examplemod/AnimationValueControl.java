package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

public class AnimationValueControl
{
    private int x;
    private int y;

    private int width = 159;
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

    /*
     * =========================================================
     * COLORS
     * =========================================================
     */

    private static final int BACKGROUND =
            0xFF303134;

    private static final int BACKGROUND_HOVER =
            0xFF3A3B3E;

    private static final int BACKGROUND_ACTIVE =
            0xFF4A4C4F;

    private static final int BACKGROUND_EDITING =
            0xFF55575A;

    private static final int BORDER =
            0xFF18191B;

    private static final int BORDER_HOVER =
            0xFF55575A;

    private static final int LABEL_BACKGROUND =
            0xFF292A2D;

    private static final int LABEL_TEXT =
            0xFFAAAAAA;

    private static final int VALUE_TEXT =
            0xFFD6D6D6;

    private static final int ACTIVE_TEXT =
            0xFFFFFFFF;

    /*
     * =========================================================
     * THEME ACCENT
     * =========================================================
     */

    private static int getAccent()
    {
        return EditorThemeManager
                .get()
                .getAccent();
    }

    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public AnimationValueControl(
            String label,
            float value,
            float step)
    {
        this.label = label;
        this.value = value;
        this.step = step;
    }

    /*
     * =========================================================
     * POSITION
     * =========================================================
     */

    public void setPosition(
            int x,
            int y)
    {
        this.x = x;
        this.y = y;
    }

    /*
     * =========================================================
     * VALUE
     * =========================================================
     */

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

    /*
     * =========================================================
     * CLICK
     * =========================================================
     */

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

    /*
     * =========================================================
     * DRAG
     * =========================================================
     */

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

    /*
     * =========================================================
     * RELEASE
     * =========================================================
     */

    public void mouseReleased(
            int mouseButton)
    {
        if (mouseButton == 0)
        {
            this.dragging = false;
        }
    }

    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

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
         * редактирования считается обработанной.
         */

        return true;
    }

    /*
     * =========================================================
     * INPUT
     * =========================================================
     */

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

    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    public boolean isEditing()
    {
        return this.editing;
    }

    /*
     * =========================================================
     * HITBOX
     * =========================================================
     */

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

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            Minecraft mc)
    {
        FontRenderer font =
                mc.fontRenderer;

        /*
         * Получаем положение мыши.
         *
         * Нам нужен только hover-визуал,
         * он никак не влияет на механику.
         */

        int mouseX =
                MouseHelper.getMouseX(
                        mc
                );

        int mouseY =
                MouseHelper.getMouseY(
                        mc
                );

        boolean hovered =
                contains(
                        mouseX,
                        mouseY
                );

        /*
         * =====================================================
         * BACKGROUND
         * =====================================================
         */

        int backgroundColor;

        if (this.editing)
        {
            backgroundColor =
                    BACKGROUND_EDITING;
        }
        else if (this.dragging)
        {
            backgroundColor =
                    BACKGROUND_ACTIVE;
        }
        else if (hovered)
        {
            backgroundColor =
                    BACKGROUND_HOVER;
        }
        else
        {
            backgroundColor =
                    BACKGROUND;
        }

        /*
         * Outer border.
         */

        int borderColor;

        if (this.editing)
        {
            borderColor =
                    getAccent();
        }
        else if (hovered || this.dragging)
        {
            borderColor =
                    BORDER_HOVER;
        }
        else
        {
            borderColor =
                    BORDER;
        }

        net.minecraft.client.gui.Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                borderColor
        );

        /*
         * Inner background.
         */

        net.minecraft.client.gui.Gui.drawRect(
                this.x + 1,
                this.y + 1,
                this.x + this.width - 1,
                this.y + this.height - 1,
                backgroundColor
        );

        /*
         * =====================================================
         * LABEL AREA
         * =====================================================
         */

        net.minecraft.client.gui.Gui.drawRect(
                this.x + 1,
                this.y + 1,
                this.x + 25,
                this.y + this.height - 1,
                LABEL_BACKGROUND
        );

        /*
         * Vertical separator between label and value.
         */

        net.minecraft.client.gui.Gui.drawRect(
                this.x + 25,
                this.y + 3,
                this.x + 26,
                this.y + this.height - 3,
                0xFF45474A
        );

        /*
         * =====================================================
         * LABEL
         * =====================================================
         */

        font.drawString(
                this.label,
                this.x + 9,
                this.y + 4,
                this.editing
                        ? ACTIVE_TEXT
                        : LABEL_TEXT
        );

        /*
         * =====================================================
         * VALUE
         * =====================================================
         */

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
                        valueWidth - 6,
                this.y + 4,
                this.editing
                        ? ACTIVE_TEXT
                        : VALUE_TEXT
        );

        /*
         * =====================================================
         * ACTIVE INDICATOR
         * =====================================================
         */

        if (this.editing)
        {
            net.minecraft.client.gui.Gui.drawRect(
                    this.x + 1,
                    this.y + this.height - 2,
                    this.x + this.width - 1,
                    this.y + this.height - 1,
                    getAccent()
            );
        }
        else if (this.dragging)
        {
            net.minecraft.client.gui.Gui.drawRect(
                    this.x + 1,
                    this.y + this.height - 2,
                    this.x + this.width - 1,
                    this.y + this.height - 1,
                    0xFF888A8D
            );
        }
    }

    /*
     * =========================================================
     * FORMAT
     * =========================================================
     */

    private String format(
            float value)
    {
        return String.format(
                java.util.Locale.US,
                "%.2f",
                value
        );
    }

    /*
     * =========================================================
     * FINISH EDITING
     * =========================================================
     */

    public boolean finishEditing()
    {
        if (!this.editing)
        {
            return false;
        }

        this.applyInput();

        return true;
    }

    /*
     * =========================================================
     * MOUSE POSITION HELPER
     * =========================================================
     *
     * Отдельный маленький helper нужен только для hover.
     * Он не участвует в обработке кликов.
     *
     * ВАЖНО:
     * класс находится внутри AnimationValueControl,
     * поэтому никаких дополнительных файлов не требуется.
     */

    private static class MouseHelper
    {
        public static int getMouseX(
                Minecraft mc)
        {
            return org.lwjgl.input.Mouse.getX()
                    *
                    mc.currentScreen.width
                    /
                    mc.displayWidth;
        }

        public static int getMouseY(
                Minecraft mc)
        {
            return mc.currentScreen.height
                    -
                    org.lwjgl.input.Mouse.getY()
                            *
                            mc.currentScreen.height
                            /
                            mc.displayHeight
                    - 1;
        }
    }
}
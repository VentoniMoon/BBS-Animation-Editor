package com.example.examplemod;

/**
 * Управляет режимом редактирования Animation Editor.
 *
 * Здесь хранится только состояние режима и логика,
 * связанная с кнопкой выбора режима.
 *
 * Сам EditorModeController НЕ знает ничего о:
 * - Timeline
 * - AnimationBone
 * - AnimationKeyframe
 * - Preview
 * - Actor
 *
 * Это специально сделано так, чтобы режимы Character / Pose /
 * Body Parts можно было дальше развивать независимо.
 */
public class EditorModeController
{
    public enum EditorMode
    {
        CHARACTER,
        POSE,
        BODY_PARTS
    }

    public static final int TOP_BAR_HEIGHT = 25;
    public static final int MODE_BUTTON_WIDTH = 130;

    private EditorMode mode =
            EditorMode.POSE;

    private boolean dropdownOpen = false;


    /*
     * =========================================================
     * MODE
     * =========================================================
     */

    public EditorMode getMode()
    {
        return this.mode;
    }


    public void setMode(
            EditorMode mode)
    {
        if (mode == null)
        {
            return;
        }

        this.mode = mode;
        this.dropdownOpen = false;
    }


    public String getModeName()
    {
        if (this.mode == EditorMode.CHARACTER)
        {
            return "Character";
        }

        if (this.mode == EditorMode.BODY_PARTS)
        {
            return "Body Parts";
        }

        return "Pose";
    }


    /*
     * =========================================================
     * BUTTON
     * =========================================================
     */

    public int getButtonX(
            int screenWidth)
    {
        /*
         * Кнопка находится перед Frame справа.
         *
         * Это та же самая позиция, которая использовалась
         * AnimationEditorScreen раньше.
         */
        return screenWidth - 250;
    }


    public boolean isMouseOverButton(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        int x =
                getButtonX(
                        screenWidth
                );

        return mouseX >= x
                &&
                mouseX <
                        x +
                                MODE_BUTTON_WIDTH
                &&
                mouseY >= 3
                &&
                mouseY <=
                        TOP_BAR_HEIGHT - 3;
    }


    public void toggleDropdown()
    {
        this.dropdownOpen =
                !this.dropdownOpen;
    }


    public boolean isDropdownOpen()
    {
        return this.dropdownOpen;
    }


    public void closeDropdown()
    {
        this.dropdownOpen = false;
    }


    /*
     * =========================================================
     * DROPDOWN
     * =========================================================
     */

    public int getDropdownX(
            int screenWidth)
    {
        return getButtonX(
                screenWidth
        );
    }


    public int getDropdownY()
    {
        return TOP_BAR_HEIGHT + 2;
    }


    public int getDropdownWidth()
    {
        return MODE_BUTTON_WIDTH;
    }


    public int getDropdownRowHeight()
    {
        return 18;
    }


    public int getDropdownHeight()
    {
        return getDropdownRowHeight() * 3;
    }


    public EditorMode getModeAtRow(
            int row)
    {
        if (row == 0)
        {
            return EditorMode.CHARACTER;
        }

        if (row == 1)
        {
            return EditorMode.POSE;
        }

        if (row == 2)
        {
            return EditorMode.BODY_PARTS;
        }

        return null;
    }


    public String getModeName(
            EditorMode mode)
    {
        if (mode == EditorMode.CHARACTER)
        {
            return "Character";
        }

        if (mode == EditorMode.BODY_PARTS)
        {
            return "Body Parts";
        }

        return "Pose";
    }


    public boolean isModeSelected(
            EditorMode mode)
    {
        return this.mode == mode;
    }


    public boolean isMouseOverDropdownRow(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        int x =
                getDropdownX(
                        screenWidth
                );

        int y =
                getDropdownY();

        int width =
                getDropdownWidth();

        int rowHeight =
                getDropdownRowHeight();

        int height =
                getDropdownHeight();

        return mouseX >= x
                &&
                mouseX <
                        x + width
                &&
                mouseY >= y
                &&
                mouseY <
                        y + height;
    }


    public EditorMode getModeAtMouse(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        if (!isMouseOverDropdownRow(
                screenWidth,
                mouseX,
                mouseY
        ))
        {
            return null;
        }

        int y =
                getDropdownY();

        int rowHeight =
                getDropdownRowHeight();

        int row =
                (mouseY - y) /
                        rowHeight;

        return getModeAtRow(
                row
        );
    }
}
package com.example.examplemod;

/**
 * Управляет выбором визуальной темы редактора.
 *
 * Контроллер отвечает только за:
 * - текущую тему;
 * - открытие/закрытие dropdown;
 * - размеры и положение Theme-кнопки;
 * - определение выбранной темы по строке dropdown.
 *
 * Самими цветами управляет EditorThemeManager.
 */
public class EditorThemeController
{
    public static final int BUTTON_WIDTH = 130;
    public static final int BUTTON_HEIGHT = 19;

    private static final int DROPDOWN_ROW_HEIGHT = 18;

    private boolean dropdownOpen = false;

    public EditorTheme getCurrentTheme()
    {
        return EditorThemeManager.get();
    }

    public String getThemeName()
    {
        return getCurrentTheme().getName();
    }

    public void setTheme(EditorTheme theme)
    {
        if (theme == null)
        {
            return;
        }

        EditorThemeManager.set(theme);
        this.dropdownOpen = false;
    }

    public void toggleDropdown()
    {
        this.dropdownOpen = !this.dropdownOpen;
    }

    public void closeDropdown()
    {
        this.dropdownOpen = false;
    }

    public boolean isDropdownOpen()
    {
        return this.dropdownOpen;
    }

    /**
     * Theme располагается непосредственно слева от MODE.
     */
    public int getButtonX(int screenWidth)
    {
        return screenWidth - 250 - BUTTON_WIDTH - 5;
    }

    public int getButtonY()
    {
        return 3;
    }

    public int getDropdownX(int screenWidth)
    {
        return getButtonX(screenWidth);
    }

    public int getDropdownY()
    {
        return EditorModeController.TOP_BAR_HEIGHT + 2;
    }

    public int getDropdownWidth()
    {
        return BUTTON_WIDTH;
    }

    public int getDropdownRowHeight()
    {
        return DROPDOWN_ROW_HEIGHT;
    }

    public int getDropdownHeight()
    {
        return DROPDOWN_ROW_HEIGHT * 4;
    }

    public boolean isMouseOverButton(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        int x = getButtonX(screenWidth);
        int y = getButtonY();

        return mouseX >= x
                && mouseX < x + BUTTON_WIDTH
                && mouseY >= y
                && mouseY < y + BUTTON_HEIGHT;
    }

    public boolean isMouseOverDropdown(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        int x = getDropdownX(screenWidth);
        int y = getDropdownY();

        return mouseX >= x
                && mouseX < x + getDropdownWidth()
                && mouseY >= y
                && mouseY < y + getDropdownHeight();
    }

    public EditorTheme getThemeAtRow(int row)
    {
        if (row == 0)
        {
            return EditorThemeManager.getOcean();
        }

        if (row == 1)
        {
            return EditorThemeManager.getEmerald();
        }

        if (row == 2)
        {
            return EditorThemeManager.getRuby();
        }

        if (row == 3)
        {
            return EditorThemeManager.getAmethyst();
        }

        return null;
    }

    public EditorTheme getThemeAtMouse(
            int screenWidth,
            int mouseX,
            int mouseY)
    {
        if (!isMouseOverDropdown(
                screenWidth,
                mouseX,
                mouseY))
        {
            return null;
        }

        int y = getDropdownY();

        int row =
                (mouseY - y)
                        /
                        getDropdownRowHeight();

        return getThemeAtRow(row);
    }

    public boolean isThemeSelected(
            EditorTheme theme)
    {
        if (theme == null)
        {
            return false;
        }

        return getCurrentTheme().getId()
                .equalsIgnoreCase(
                        theme.getId()
                );
    }
}
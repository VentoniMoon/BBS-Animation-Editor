package com.example.examplemod;

/**
 * Управляет текущей визуальной темой редактора.
 *
 * Пока тема хранится только во время работы клиента.
 * Сохранение в Minecraft Options добавим позже.
 */
public final class EditorThemeManager
{
    private static final EditorTheme OCEAN =
            EditorTheme.ocean();

    private static final EditorTheme EMERALD =
            EditorTheme.emerald();

    private static final EditorTheme RUBY =
            EditorTheme.ruby();

    private static final EditorTheme AMETHYST =
            EditorTheme.amethyst();

    private static EditorTheme currentTheme =
            OCEAN;


    private EditorThemeManager()
    {
    }


    /*
     * =========================================================
     * CURRENT THEME
     * =========================================================
     */

    public static EditorTheme get()
    {
        return currentTheme;
    }

    public static void set(
            EditorTheme theme)
    {
        if (theme == null)
        {
            return;
        }

        currentTheme = theme;
    }


    /*
     * =========================================================
     * PREDEFINED THEMES
     * =========================================================
     */

    public static EditorTheme getOcean()
    {
        return OCEAN;
    }

    public static EditorTheme getEmerald()
    {
        return EMERALD;
    }

    public static EditorTheme getRuby()
    {
        return RUBY;
    }

    public static EditorTheme getAmethyst()
    {
        return AMETHYST;
    }


    /*
     * =========================================================
     * BY ID
     * =========================================================
     */

    public static EditorTheme getById(
            String id)
    {
        if (id == null)
        {
            return OCEAN;
        }

        if ("emerald".equalsIgnoreCase(id))
        {
            return EMERALD;
        }

        if ("ruby".equalsIgnoreCase(id))
        {
            return RUBY;
        }

        if ("amethyst".equalsIgnoreCase(id))
        {
            return AMETHYST;
        }

        return OCEAN;
    }
}
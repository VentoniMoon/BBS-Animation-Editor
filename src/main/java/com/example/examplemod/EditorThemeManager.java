package com.example.examplemod;

/**
 * Управляет текущей визуальной темой редактора.
 *
 * Выбранная тема сохраняется в config/bbs-editor/editor-ui.properties
 * и загружается при открытии редактора после перезапуска игры.
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

    private static volatile EditorTheme currentTheme = OCEAN;

    /** Minecraft game directory; null until the editor GUI is initialized. */
    private static java.io.File preferencesDirectory;

    private EditorThemeManager()
    {
    }

    /**
     * Load the saved theme once for this Minecraft game directory.
     * Repeated GuiScreen.initGui() calls must not reset a newly chosen theme.
     */
    public static synchronized void initialize(java.io.File gameDirectory)
    {
        if (gameDirectory == null)
        {
            return;
        }
        java.io.File directory = gameDirectory.getAbsoluteFile();
        if (directory.equals(preferencesDirectory))
        {
            return;
        }
        preferencesDirectory = directory;
        currentTheme = getById(
                EditorThemePreferences.loadThemeId(directory));
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

    public static synchronized void set(EditorTheme theme)
    {
        if (theme == null)
        {
            return;
        }
        if (currentTheme.getId().equalsIgnoreCase(theme.getId()))
        {
            return;
        }
        currentTheme = theme;
        if (preferencesDirectory != null)
        {
            try
            {
                EditorThemePreferences.saveThemeId(
                        preferencesDirectory, theme.getId());
            }
            catch (java.io.IOException | SecurityException error)
            {
                // A read-only directory must not prevent changing the theme
                // in the current session.
                System.err.println("[BBS Editor][Theme] Cannot save theme: "
                        + error.getMessage());
            }
        }
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
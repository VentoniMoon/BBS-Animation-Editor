package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Client-local visual preferences, independent of scene saves and worlds.
 *
 * The theme is stored under Minecraft's game directory so it survives
 * restarting the game and is shared by all BBS Animation Editor scenes.
 */
public final class EditorThemePreferences
{
    private static final String FILE = "config/bbs-editor/editor-ui.properties";
    private static final String THEME = "theme";
    private static final long MAX_FILE_BYTES = 65536L;

    private EditorThemePreferences() {}

    /** Returns null when no valid preference was saved (Ocean is the default). */
    public static String loadThemeId(File gameDirectory)
    {
        if (gameDirectory == null)
        {
            return null;
        }
        File file = new File(gameDirectory, FILE);
        if (!file.isFile() || file.length() > MAX_FILE_BYTES)
        {
            return null;
        }
        Properties preferences = new Properties();
        try (FileInputStream input = new FileInputStream(file))
        {
            preferences.load(input);
            String id = preferences.getProperty(THEME, "").trim();
            if ("ocean".equalsIgnoreCase(id) ||
                    "emerald".equalsIgnoreCase(id) ||
                    "ruby".equalsIgnoreCase(id) ||
                    "amethyst".equalsIgnoreCase(id))
            {
                return id;
            }
        }
        catch (IOException | IllegalArgumentException error)
        {
            System.err.println("[BBS Editor][Theme] Could not read theme: "
                    + error.getMessage());
        }
        return null;
    }

    public static void saveThemeId(File gameDirectory, String id)
            throws IOException
    {
        if (gameDirectory == null ||
                !("ocean".equalsIgnoreCase(id) ||
                  "emerald".equalsIgnoreCase(id) ||
                  "ruby".equalsIgnoreCase(id) ||
                  "amethyst".equalsIgnoreCase(id)))
        {
            throw new IOException("Invalid editor theme preference");
        }

        File target = new File(gameDirectory, FILE);
        File folder = target.getParentFile();
        if (!folder.isDirectory() && !folder.mkdirs())
        {
            throw new IOException("Could not create BBS Editor config directory");
        }

        Properties preferences = new Properties();
        // Preserve future UI preferences when saving only the theme.
        if (target.isFile() && target.length() <= MAX_FILE_BYTES)
        {
            try (FileInputStream input = new FileInputStream(target))
            {
                preferences.load(input);
            }
            catch (IOException | IllegalArgumentException error)
            {
                System.err.println("[BBS Editor][Theme] Recreating unreadable UI "
                        + "preferences: " + error.getMessage());
            }
        }
        preferences.setProperty(THEME, id.toLowerCase(java.util.Locale.ROOT));

        File temp = File.createTempFile("editor-ui-", ".tmp", folder);
        try
        {
            try (FileOutputStream output = new FileOutputStream(temp))
            {
                preferences.store(output,
                        "BBS Animation Editor: client-local interface preferences");
            }
            try
            {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException unsupported)
            {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            if (temp.exists())
            {
                temp.delete();
            }
        }
    }
}

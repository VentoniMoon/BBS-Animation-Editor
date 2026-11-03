package com.example.examplemod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Properties;

/** No Minecraft/Forge dependencies: checks persistence with Java 8 alone. */
public final class EditorThemePersistenceSmokeTest
{
    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception
    {
        File gameDir = Files.createTempDirectory("bbs-theme-test-").toFile();
        File secondGameDir = Files.createTempDirectory("bbs-theme-other-").toFile();

        EditorThemeManager.initialize(gameDir);
        check("ocean".equals(EditorThemeManager.get().getId()),
                "Initial editor theme must be Ocean");

        EditorThemeManager.set(EditorThemeManager.getRuby());
        File stored = new File(gameDir,
                "config/bbs-editor/editor-ui.properties");
        check(stored.isFile(), "Changing theme must immediately save preferences");
        check("ruby".equals(EditorThemePreferences.loadThemeId(gameDir)),
                "Ruby theme must be persisted");

        // GuiScreen.initGui can be called repeatedly when resizing/returning.
        EditorThemeManager.initialize(gameDir);
        check("ruby".equals(EditorThemeManager.get().getId()),
                "Reopening the same GUI must not reset its current theme");

        // Simulate switching to a fresh client and loading saved preferences.
        EditorThemeManager.initialize(secondGameDir);
        check("ocean".equals(EditorThemeManager.get().getId()),
                "Independent game directory must keep default theme");
        EditorThemeManager.initialize(gameDir);
        check("ruby".equals(EditorThemeManager.get().getId()),
                "A fresh initialization must restore the saved theme");

        // Unknown future settings survive changing the current theme.
        Properties other = new Properties();
        try (FileInputStream in = new FileInputStream(stored))
        {
            other.load(in);
        }
        other.setProperty("ui.example", "preserved");
        try (FileOutputStream out = new FileOutputStream(stored))
        {
            other.store(out, "Test forward-compatible properties");
        }
        EditorThemeManager.set(EditorThemeManager.getAmethyst());
        Properties restored = new Properties();
        try (FileInputStream in = new FileInputStream(stored))
        {
            restored.load(in);
        }
        check("amethyst".equals(restored.getProperty("theme")),
                "Amethyst selection must be saved");
        check("preserved".equals(restored.getProperty("ui.example")),
                "Unrelated UI preferences must survive a theme change");

        // Damaged/unknown theme values must never break the editor.
        restored.setProperty("theme", "invalid-theme");
        try (FileOutputStream out = new FileOutputStream(stored))
        {
            restored.store(out, "Invalid theme regression");
        }
        EditorThemeManager.initialize(secondGameDir);
        EditorThemeManager.initialize(gameDir);
        check("ocean".equals(EditorThemeManager.get().getId()),
                "Unknown theme preference must fall back to Ocean");

        System.out.println("EditorThemePersistenceSmokeTest PASS");
    }
}

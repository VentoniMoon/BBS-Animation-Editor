package com.example.examplemod;

/** Standalone Java 8 smoke test, with src/main/resources on the classpath. */
public final class EditorLanguageCatalogSmokeTest
{
    private EditorLanguageCatalogSmokeTest() {}

    public static void main(String[] args)
    {
        require(EditorLanguageCatalog.russianEntryCount() >= 420,
                "Bundled ru_ru catalog not loaded");
        equal("АКТЁРЫ", value("actors"));
        equal("ТАЙМЛАЙН", value("timeline"));
        equal("ПОЗИЦИЯ", value("transform.position"));
        equal("ИНТЕРПОЛЯЦИЯ", value("interpolation.heading"));
        equal("ПРЕДПРОСМОТР", value("preview"));
        equal("СЦЕНА: ", value("scene.prefix"));
        equal("ЗЕРКАЛА", value("camera.mirrors"));
        equal("Найдено WAV-файлов: 7",
                String.format(java.util.Locale.ROOT,
                        value("audio.found_count"), "7"));
        require(EditorLanguageCatalog.russian("missing.key") == null,
                "Unknown keys must remain untouched");
        System.out.println("Editor ru_ru catalog OK: " +
                EditorLanguageCatalog.russianEntryCount() + " entries");
    }

    private static String value(String id)
    {
        String text = EditorLanguageCatalog.russian("bbsanimationeditor.ui." + id);
        require(text != null, "Missing Russian translation: " + id);
        return text;
    }

    private static void equal(String expected, String actual)
    {
        require(expected.equals(actual),
                "Expected '" + expected + "', got '" + actual + "'");
    }

    private static void require(boolean test, String message)
    {
        if (!test) throw new AssertionError(message);
    }
}

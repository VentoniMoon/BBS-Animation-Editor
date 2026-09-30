package com.example.examplemod;

/**
 * Визуальная тема редактора.
 *
 * Здесь хранятся только цвета, которые меняются
 * при переключении визуальной темы.
 *
 * Все остальные нейтральные цвета интерфейса
 * остаются общими для всех тем.
 */
public class EditorTheme
{
    private final String id;
    private final String name;

    private final int accent;
    private final int accentBright;


    public EditorTheme(
            String id,
            String name,
            int accent,
            int accentBright)
    {
        this.id = id;
        this.name = name;
        this.accent = accent;
        this.accentBright = accentBright;
    }


    public String getId()
    {
        return this.id;
    }


    public String getName()
    {
        return this.name;
    }


    public int getAccent()
    {
        return this.accent;
    }


    public int getAccentBright()
    {
        return this.accentBright;
    }


    /*
     * =========================================================
     * DEFAULT THEMES
     * =========================================================
     */

    /**
     * Оригинальная синяя тема редактора.
     */
    public static EditorTheme ocean()
    {
        return new EditorTheme(
                "ocean",
                "Ocean",
                0xFF66CCFF,
                0xFF8BE1FF
        );
    }


    /**
     * Зелёная тема.
     */
    public static EditorTheme emerald()
    {
        return new EditorTheme(
                "emerald",
                "Emerald",
                0xFF66DD88,
                0xFF8BFFAA
        );
    }


    /**
     * Красная тема.
     */
    public static EditorTheme ruby()
    {
        return new EditorTheme(
                "ruby",
                "Ruby",
                0xFFFF6677,
                0xFFFF8B99
        );
    }


    /**
     * Фиолетовая тема.
     */
    public static EditorTheme amethyst()
    {
        return new EditorTheme(
                "amethyst",
                "Amethyst",
                0xFFCC88FF,
                0xFFE0B0FF
        );
    }
}
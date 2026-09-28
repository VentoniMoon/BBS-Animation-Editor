package com.example.examplemod;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Единый визуальный стиль BBS Animation Editor.
 *
 * Здесь находятся только цвета и небольшие примитивы интерфейса.
 * Логика редактора здесь отсутствует.
 */
public final class EditorVisualStyle
{
    private EditorVisualStyle()
    {
    }

    /*
     * =========================================================
     * BACKGROUND
     * =========================================================
     */

    public static final int BACKGROUND =
            0xFF191B1E;

    public static final int TOP_BAR =
            0xFF24262A;

    public static final int PANEL =
            0xFF202226;

    public static final int PANEL_LIGHT =
            0xFF292B2F;

    public static final int PANEL_DARK =
            0xFF1B1D20;

    public static final int INPUT =
            0xFF303237;

    public static final int INPUT_HOVER =
            0xFF393C42;

    public static final int SELECTED =
            0xFF3B3E43;

    public static final int SELECTED_DARK =
            0xFF34373C;

    public static final int BORDER =
            0xFF34373A;

    public static final int BORDER_LIGHT =
            0xFF46494E;

    /*
     * =========================================================
     * TEXT
     * =========================================================
     */

    public static final int TEXT =
            0xFFE4E4E4;

    public static final int TEXT_SECONDARY =
            0xFFB0B3B8;

    public static final int TEXT_DIM =
            0xFF777B81;

    public static final int TEXT_DISABLED =
            0xFF55585D;

    /*
     * =========================================================
     * ACCENTS
     * =========================================================
     */

    public static final int CYAN =
            0xFF4CC9F0;

    public static final int CYAN_BRIGHT =
            0xFF62D5F5;

    public static final int GREEN =
            0xFF73E06C;

    public static final int YELLOW =
            0xFFE5D84A;

    public static final int RED =
            0xFFD85C5C;

    /*
     * =========================================================
     * TIMELINE
     * =========================================================
     */

    public static final int TIMELINE_BACKGROUND =
            0xFF1B1D20;

    public static final int TIMELINE_HEADER =
            0xFF24262A;

    public static final int TIMELINE_TRACK =
            0xFF202226;

    public static final int TIMELINE_TRACK_ALT =
            0xFF232529;

    public static final int TIMELINE_LINE =
            0xFF393C40;

    public static final int TIMELINE_RULER =
            0xFF4A4D52;

    public static final int TIMELINE_CURRENT =
            0xFFFF5555;

    public static final int KEYFRAME =
            0xFFF0A51A;

    public static final int KEYFRAME_SELECTED =
            0xFFFFC43A;

    /*
     * =========================================================
     * PREVIEW
     * =========================================================
     */

    public static final int PREVIEW_BORDER =
            0xFF3A3D42;

    public static final int PREVIEW_BACKGROUND =
            0xFF17191C;


    /*
     * =========================================================
     * BASIC RECTANGLES
     * =========================================================
     */

    public static void panel(
            Gui gui,
            int x,
            int y,
            int width,
            int height)
    {
        gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                PANEL
        );
    }


    public static void panelWithBorder(
            Gui gui,
            int x,
            int y,
            int width,
            int height)
    {
        gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                BORDER
        );

        gui.drawRect(
                x + 1,
                y + 1,
                x + width - 1,
                y + height - 1,
                PANEL
        );
    }


    public static void input(
            Gui gui,
            int x,
            int y,
            int width,
            int height,
            boolean hovered)
    {
        gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                hovered
                        ? INPUT_HOVER
                        : INPUT
        );

        gui.drawRect(
                x,
                y,
                x + width,
                y + 1,
                hovered
                        ? BORDER_LIGHT
                        : BORDER
        );
    }


    public static void selected(
            Gui gui,
            int x,
            int y,
            int width,
            int height)
    {
        gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                SELECTED
        );

        gui.drawRect(
                x,
                y,
                x + 2,
                y + height,
                CYAN
        );
    }


    /*
     * =========================================================
     * TEXT HELPERS
     * =========================================================
     */

    public static void title(
            Gui gui,
            FontRenderer font,
            String text,
            int x,
            int y)
    {
        gui.drawString(
                font,
                text,
                x,
                y,
                CYAN
        );
    }


    public static void sectionTitle(
            Gui gui,
            FontRenderer font,
            String text,
            int x,
            int y)
    {
        gui.drawString(
                font,
                text,
                x,
                y,
                CYAN
        );
    }


    public static void secondary(
            Gui gui,
            FontRenderer font,
            String text,
            int x,
            int y)
    {
        gui.drawString(
                font,
                text,
                x,
                y,
                TEXT_SECONDARY
        );
    }


    /*
     * =========================================================
     * BUTTON
     * =========================================================
     */

    public static void button(
            Gui gui,
            FontRenderer font,
            int x,
            int y,
            int width,
            int height,
            String text,
            boolean hovered)
    {
        int background =
                hovered
                        ? INPUT_HOVER
                        : INPUT;

        gui.drawRect(
                x,
                y,
                x + width,
                y + height,
                background
        );

        gui.drawRect(
                x,
                y,
                x + width,
                y + 1,
                hovered
                        ? BORDER_LIGHT
                        : BORDER
        );

        gui.drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                PANEL_DARK
        );

        int textWidth =
                font.getStringWidth(text);

        gui.drawString(
                font,
                text,
                x + (width - textWidth) / 2,
                y + 6,
                hovered
                        ? TEXT
                        : TEXT_SECONDARY
        );
    }


    /*
     * =========================================================
     * DIVIDER
     * =========================================================
     */

    public static void horizontalLine(
            Gui gui,
            int x,
            int y,
            int width)
    {
        gui.drawRect(
                x,
                y,
                x + width,
                y + 1,
                BORDER
        );
    }


    public static void verticalLine(
            Gui gui,
            int x,
            int y,
            int height)
    {
        gui.drawRect(
                x,
                y,
                x + 1,
                y + height,
                BORDER
        );
    }
}
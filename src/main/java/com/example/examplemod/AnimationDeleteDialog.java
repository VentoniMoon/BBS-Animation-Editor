package com.example.examplemod;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Окно подтверждения удаления keyframe.
 *
 * Отвечает только за:
 * - отображение диалога;
 * - обработку кнопок Cancel / Delete;
 * - состояние отображения диалога.
 *
 * Состояние pending-delete самого keyframe
 * по-прежнему хранится в EditorKeyframeController.
 */
public class AnimationDeleteDialog
{
    /*
     * =========================================================
     * DIMENSIONS
     * =========================================================
     */

    private static final int DELETE_DIALOG_WIDTH = 280;
    private static final int DELETE_DIALOG_HEIGHT = 105;

    private static final int DELETE_BUTTON_WIDTH = 90;
    private static final int DELETE_BUTTON_HEIGHT = 20;


    /*
     * =========================================================
     * COLORS
     * =========================================================
     */

    private static final int COLOR_PANEL =
            0xFF17191B;

    private static final int COLOR_PANEL_DARK =
            0xFF111315;

    private static final int COLOR_PANEL_HOVER =
            0xFF25292D;

    private static final int COLOR_PANEL_LIGHT =
            0xFF1D2023;

    private static final int COLOR_BORDER =
            0xFF303438;

    private static final int COLOR_BORDER_DARK =
            0xFF202326;

    private static final int COLOR_TEXT =
            0xFFE2E5E7;

    private static final int COLOR_TEXT_SECONDARY =
            0xFF9AA1A6;


    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    private boolean open;

    private int pendingFrame;


    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
     */

    public AnimationDeleteDialog()
    {
        this.open = false;
        this.pendingFrame = -1;
    }


    /*
     * =========================================================
     * STATE
     * =========================================================
     */

    public boolean isOpen()
    {
        return this.open;
    }

    public int getPendingFrame()
    {
        return this.pendingFrame;
    }

    public void open(int frame)
    {
        this.pendingFrame = frame;
        this.open = true;
    }

    public void close()
    {
        this.open = false;
        this.pendingFrame = -1;
    }


    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            GuiScreen screen,
            FontRenderer fontRenderer,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            int accentColor)
    {
        if (!this.open)
        {
            return;
        }

        if (screen == null)
        {
            return;
        }

        if (fontRenderer == null)
        {
            return;
        }


        /*
         * -----------------------------------------------------
         * DARK OVERLAY
         * -----------------------------------------------------
         */

        screen.drawRect(
                0,
                0,
                screenWidth,
                screenHeight,
                0x99000000
        );


        /*
         * -----------------------------------------------------
         * DIALOG POSITION
         * -----------------------------------------------------
         */

        int x =
                getDialogX(
                        screenWidth
                );

        int y =
                getDialogY(
                        screenHeight
                );


        /*
         * -----------------------------------------------------
         * OUTER SHADOW
         * -----------------------------------------------------
         */

        screen.drawRect(
                x - 4,
                y - 4,
                x + DELETE_DIALOG_WIDTH + 4,
                y + DELETE_DIALOG_HEIGHT + 4,
                0xDD070808
        );


        /*
         * -----------------------------------------------------
         * MAIN PANEL
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + DELETE_DIALOG_HEIGHT,
                COLOR_PANEL
        );


        /*
         * -----------------------------------------------------
         * HEADER
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + 26,
                COLOR_PANEL_DARK
        );


        /*
         * -----------------------------------------------------
         * ACCENT LINE
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + 1,
                accentColor
        );


        /*
         * -----------------------------------------------------
         * HEADER BORDER
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y + 25,
                x + DELETE_DIALOG_WIDTH,
                y + 26,
                COLOR_BORDER
        );


        /*
         * -----------------------------------------------------
         * TITLE
         * -----------------------------------------------------
         */

        screen.drawString(
                fontRenderer,
                "DELETE KEYFRAME",
                x + 10,
                y + 8,
                COLOR_TEXT
        );


        /*
         * -----------------------------------------------------
         * MESSAGE
         * -----------------------------------------------------
         */

        String message =
                "Delete keyframe at frame "
                        + this.pendingFrame
                        + "?";

        screen.drawString(
                fontRenderer,
                message,
                x + 10,
                y + 39,
                COLOR_TEXT_SECONDARY
        );


        /*
         * -----------------------------------------------------
         * BUTTON POSITIONS
         * -----------------------------------------------------
         */

        int cancelX =
                getCancelX(
                        screenWidth
                );

        int confirmX =
                getConfirmX(
                        screenWidth
                );

        int buttonY =
                y
                        + DELETE_DIALOG_HEIGHT
                        - DELETE_BUTTON_HEIGHT
                        - 10;


        /*
         * -----------------------------------------------------
         * HOVER
         * -----------------------------------------------------
         */

        boolean cancelHovered =
                isInside(
                        mouseX,
                        mouseY,
                        cancelX,
                        buttonY,
                        DELETE_BUTTON_WIDTH,
                        DELETE_BUTTON_HEIGHT
                );

        boolean confirmHovered =
                isInside(
                        mouseX,
                        mouseY,
                        confirmX,
                        buttonY,
                        DELETE_BUTTON_WIDTH,
                        DELETE_BUTTON_HEIGHT
                );


        /*
         * -----------------------------------------------------
         * CANCEL
         * -----------------------------------------------------
         */

        drawButton(
                screen,
                fontRenderer,
                cancelX,
                buttonY,
                "Cancel",
                cancelHovered,
                false,
                accentColor
        );


        /*
         * -----------------------------------------------------
         * DELETE
         * -----------------------------------------------------
         */

        drawButton(
                screen,
                fontRenderer,
                confirmX,
                buttonY,
                "Delete",
                confirmHovered,
                true,
                accentColor
        );
    }


    /*
     * =========================================================
     * INPUT
     * =========================================================
     *
     * Return values:
     *
     * 0 = nothing
     * 1 = event handled / cancel
     * 2 = confirm delete
     * 3 = click outside dialog
     */

    public int mouseClicked(
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (!this.open)
        {
            return 0;
        }


        /*
         * -----------------------------------------------------
         * DIALOG POSITION
         * -----------------------------------------------------
         */

        int dialogX =
                getDialogX(
                        screenWidth
                );

        int dialogY =
                getDialogY(
                        screenHeight
                );

        int dialogRight =
                dialogX
                        + DELETE_DIALOG_WIDTH;

        int dialogBottom =
                dialogY
                        + DELETE_DIALOG_HEIGHT;


        /*
         * -----------------------------------------------------
         * BUTTON POSITIONS
         * -----------------------------------------------------
         */

        int buttonY =
                dialogY
                        + DELETE_DIALOG_HEIGHT
                        - DELETE_BUTTON_HEIGHT
                        - 10;

        int cancelX =
                getCancelX(
                        screenWidth
                );

        int confirmX =
                getConfirmX(
                        screenWidth
                );


        /*
         * -----------------------------------------------------
         * NON-LEFT CLICK
         * -----------------------------------------------------
         */

        if (mouseButton != 0)
        {
            if (
                    mouseX < dialogX
                            || mouseX > dialogRight
                            || mouseY < dialogY
                            || mouseY > dialogBottom
            )
            {
                this.close();

                return 3;
            }

            return 1;
        }


        /*
         * -----------------------------------------------------
         * DELETE
         * -----------------------------------------------------
         */

        if (
                mouseX >= confirmX
                        && mouseX < confirmX + DELETE_BUTTON_WIDTH
                        && mouseY >= buttonY
                        && mouseY < buttonY + DELETE_BUTTON_HEIGHT
        )
        {
            this.open = false;

            return 2;
        }


        /*
         * -----------------------------------------------------
         * CANCEL
         * -----------------------------------------------------
         */

        if (
                mouseX >= cancelX
                        && mouseX < cancelX + DELETE_BUTTON_WIDTH
                        && mouseY >= buttonY
                        && mouseY < buttonY + DELETE_BUTTON_HEIGHT
        )
        {
            this.close();

            return 1;
        }


        /*
         * -----------------------------------------------------
         * CLICK OUTSIDE
         * -----------------------------------------------------
         */

        if (
                mouseX < dialogX
                        || mouseX > dialogRight
                        || mouseY < dialogY
                        || mouseY > dialogBottom
        )
        {
            this.close();

            return 3;
        }


        /*
         * -----------------------------------------------------
         * CLICK INSIDE DIALOG
         * -----------------------------------------------------
         */

        return 1;
    }


    /*
     * =========================================================
     * POSITION
     * =========================================================
     */

    public int getDialogX(int screenWidth)
    {
        return (
                screenWidth
                        - DELETE_DIALOG_WIDTH
        ) / 2;
    }

    public int getDialogY(int screenHeight)
    {
        return (
                screenHeight
                        - DELETE_DIALOG_HEIGHT
        ) / 2;
    }


    private int getCancelX(int screenWidth)
    {
        int dialogX =
                getDialogX(
                        screenWidth
                );

        return dialogX
                + DELETE_DIALOG_WIDTH
                - DELETE_BUTTON_WIDTH * 2
                - 20;
    }


    private int getConfirmX(int screenWidth)
    {
        return getCancelX(
                screenWidth
        )
                + DELETE_BUTTON_WIDTH
                + 8;
    }


    /*
     * =========================================================
     * BUTTON
     * =========================================================
     */

    private void drawButton(
            GuiScreen screen,
            FontRenderer fontRenderer,
            int x,
            int y,
            String text,
            boolean hovered,
            boolean destructive,
            int accentColor)
    {
        int background;

        if (destructive)
        {
            background =
                    hovered
                            ? 0xFF8A3A3A
                            : 0xFF5C3030;
        }
        else
        {
            background =
                    hovered
                            ? COLOR_PANEL_HOVER
                            : COLOR_PANEL_LIGHT;
        }


        /*
         * -----------------------------------------------------
         * BACKGROUND
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y,
                x + DELETE_BUTTON_WIDTH,
                y + DELETE_BUTTON_HEIGHT,
                background
        );


        /*
         * -----------------------------------------------------
         * TOP BORDER
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y,
                x + DELETE_BUTTON_WIDTH,
                y + 1,
                destructive
                        ? 0xFFB75A5A
                        : hovered
                        ? accentColor
                        : COLOR_BORDER
        );


        /*
         * -----------------------------------------------------
         * BOTTOM BORDER
         * -----------------------------------------------------
         */

        screen.drawRect(
                x,
                y + DELETE_BUTTON_HEIGHT - 1,
                x + DELETE_BUTTON_WIDTH,
                y + DELETE_BUTTON_HEIGHT,
                COLOR_BORDER_DARK
        );


        /*
         * -----------------------------------------------------
         * TEXT
         * -----------------------------------------------------
         */

        int textWidth =
                fontRenderer.getStringWidth(
                        text
                );

        screen.drawString(
                fontRenderer,
                text,
                x
                        + (
                        DELETE_BUTTON_WIDTH
                                - textWidth
                ) / 2,
                y + 6,
                destructive && hovered
                        ? 0xFFFFFFFF
                        : COLOR_TEXT
        );
    }


    /*
     * =========================================================
     * HIT TEST
     * =========================================================
     */

    private boolean isInside(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height)
    {
        return mouseX >= x
                && mouseX < x + width
                && mouseY >= y
                && mouseY < y + height;
    }
}
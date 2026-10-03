package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import mchorse.blockbuster.common.entity.EntityActor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

public class CharacterAnimationPickerPopup
{
    public interface SelectionListener
    {
        void onAnimationSelected(String animation);
    }

    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 7;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SCROLLBAR_WIDTH = 7;

    private static final int COLOR_PANEL = 0xFF181818;
    private static final int COLOR_PANEL_DARK = 0xFF111111;
    private static final int COLOR_PANEL_HOVER = 0xFF252525;
    private static final int COLOR_BORDER = 0xFF303030;
    private static final int COLOR_TEXT = 0xFFE2E5E7;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9AA1A6;

    private final SelectionListener listener;
    private final GuiTextField searchField;

    private EntityActor runtimeActor;
    private List<String> animations =
            new ArrayList<String>();
    private List<String> filtered =
            new ArrayList<String>();

    private int x;
    private int y;
    private int width;
    private int scrollOffset;
    private boolean open;

    public CharacterAnimationPickerPopup(
            Minecraft mc,
            SelectionListener listener)
    {
        this.listener = listener;

        this.searchField =
                new GuiTextField(
                        7301,
                        mc.fontRenderer,
                        0,
                        0,
                        10,
                        SEARCH_HEIGHT
                );

        this.searchField.setMaxStringLength(128);
        this.searchField.setCanLoseFocus(true);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setTextColor(COLOR_TEXT);
        this.searchField.setVisible(true);
    }

    public void setBounds(
            int x,
            int y,
            int width)
    {
        this.x = x;
        this.y = y;
        this.width = Math.max(80, width);

        positionSearchField();
    }

    public boolean isOpen()
    {
        return this.open;
    }

    public void open(
            EntityActor runtimeActor,
            String action)
    {
        this.runtimeActor = runtimeActor;
        this.scrollOffset = 0;
        this.open = true;

        this.searchField.setText("");
        this.searchField.setFocused(true);

        refreshList();
        positionSearchField();
    }

    public void close()
    {
        this.open = false;
        this.searchField.setFocused(false);
    }

    public void draw(
            Minecraft mc,
            int mouseX,
            int mouseY)
    {
        if (!this.open)
        {
            return;
        }

        refreshList();
        positionSearchField();

        int popupHeight =
                SEARCH_HEIGHT +
                4 +
                VISIBLE_ROWS * ROW_HEIGHT +
                4;

        int right = this.x + this.width;
        int bottom = this.y + popupHeight;

        GuiScreen.drawRect(
                this.x - 2,
                this.y - 2,
                right + 2,
                bottom + 2,
                COLOR_BORDER
        );

        GuiScreen.drawRect(
                this.x,
                this.y,
                right,
                bottom,
                COLOR_PANEL
        );

        GuiScreen.drawRect(
                this.x,
                this.y,
                right,
                this.y + SEARCH_HEIGHT,
                COLOR_PANEL_DARK
        );

        this.searchField.drawTextBox();

        int listY =
                this.y +
                SEARCH_HEIGHT +
                4;

        int listWidth =
                this.width -
                SCROLLBAR_WIDTH -
                4;

        GuiScreen.drawRect(
                this.x,
                listY,
                this.x + listWidth,
                listY + VISIBLE_ROWS * ROW_HEIGHT,
                COLOR_PANEL_DARK
        );

        for (int i = 0; i < VISIBLE_ROWS; i++)
        {
            int index = this.scrollOffset + i;

            if (index >= this.filtered.size())
            {
                break;
            }

            String animation =
                    this.filtered.get(index);

            boolean hovered =
                    mouseX >= this.x &&
                    mouseX < this.x + listWidth &&
                    mouseY >= listY + i * ROW_HEIGHT &&
                    mouseY < listY + (i + 1) * ROW_HEIGHT;

            if (hovered)
            {
                GuiScreen.drawRect(
                        this.x,
                        listY + i * ROW_HEIGHT,
                        this.x + listWidth,
                        listY + (i + 1) * ROW_HEIGHT,
                        COLOR_PANEL_HOVER
                );
            }

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            animation,
                            listWidth - 10
                    ),
                    this.x + 5,
                    listY + i * ROW_HEIGHT + 4,
                    hovered
                            ? EditorThemeManager.get().getAccentBright()
                            : COLOR_TEXT_SECONDARY
            );
        }

        drawScrollbar(
                listY,
                VISIBLE_ROWS * ROW_HEIGHT,
                mouseX,
                mouseY
        );
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (!this.open)
        {
            return false;
        }

        if (this.searchField.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        ))
        {
            return true;
        }

        int popupHeight =
                SEARCH_HEIGHT +
                4 +
                VISIBLE_ROWS * ROW_HEIGHT +
                4;

        if (mouseX < this.x ||
                mouseX >= this.x + this.width ||
                mouseY < this.y ||
                mouseY >= this.y + popupHeight)
        {
            close();
            return true;
        }

        int listY =
                this.y +
                SEARCH_HEIGHT +
                4;

        int listWidth =
                this.width -
                SCROLLBAR_WIDTH -
                4;

        if (mouseX >= this.x &&
                mouseX < this.x + listWidth &&
                mouseY >= listY &&
                mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT)
        {
            int index =
                    this.scrollOffset +
                    (mouseY - listY) /
                            ROW_HEIGHT;

            if (index >= 0 &&
                    index < this.filtered.size())
            {
                String animation =
                        this.filtered.get(index);

                close();

                if (this.listener != null)
                {
                    this.listener.onAnimationSelected(
                            animation
                    );
                }

                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int direction)
    {
        if (!this.open)
        {
            return false;
        }

        int popupHeight =
                SEARCH_HEIGHT +
                4 +
                VISIBLE_ROWS * ROW_HEIGHT +
                4;

        if (mouseX < this.x ||
                mouseX >= this.x + this.width ||
                mouseY < this.y ||
                mouseY >= this.y + popupHeight)
        {
            return false;
        }

        if (direction > 0)
        {
            this.scrollOffset =
                    Math.max(
                            0,
                            this.scrollOffset - 1
                    );
        }
        else if (direction < 0)
        {
            this.scrollOffset =
                    Math.min(
                            getMaxOffset(),
                            this.scrollOffset + 1
                    );
        }

        return true;
    }

    public boolean keyTyped(
            char typedChar,
            int keyCode)
    {
        if (!this.open)
        {
            return false;
        }

        if (this.searchField.isFocused() &&
                this.searchField.textboxKeyTyped(
                        typedChar,
                        keyCode
                ))
        {
            refreshList();
            return true;
        }

        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            close();
            return true;
        }

        return false;
    }

    public void updateCursorCounter()
    {
        this.searchField.updateCursorCounter();
    }

    private void positionSearchField()
    {
        this.searchField.x = this.x + 5;
        this.searchField.y = this.y + 1;
        this.searchField.width = Math.max(20, this.width - 10);
        this.searchField.height = SEARCH_HEIGHT;
    }

    private void refreshList()
    {
        if (!this.open)
        {
            return;
        }

        this.animations =
                CharacterAnimationSetupController
                        .getAvailableAnimations(
                                this.runtimeActor
                        );

        String filter =
                this.searchField.getText();

        this.filtered.clear();

        if (filter == null ||
                filter.trim().isEmpty())
        {
            this.filtered.addAll(this.animations);
        }
        else
        {
            String lower = filter.toLowerCase();

            for (String animation : this.animations)
            {
                if (animation.toLowerCase().contains(lower))
                {
                    this.filtered.add(animation);
                }
            }
        }

        this.scrollOffset =
                Math.max(
                        0,
                        Math.min(
                                getMaxOffset(),
                                this.scrollOffset
                        )
                );
    }

    private int getMaxOffset()
    {
        return Math.max(
                0,
                this.filtered.size() - VISIBLE_ROWS
        );
    }

    private void drawScrollbar(
            int listY,
            int listHeight,
            int mouseX,
            int mouseY)
    {
        if (this.filtered.size() <= VISIBLE_ROWS)
        {
            return;
        }

        int scrollbarX =
                this.x +
                this.width -
                SCROLLBAR_WIDTH;

        float ratio =
                (float) VISIBLE_ROWS /
                (float) this.filtered.size();

        int thumbHeight =
                Math.max(
                        12,
                        (int) (listHeight * ratio)
                );

        int travel =
                listHeight - thumbHeight;

        int thumbY =
                listY;

        if (getMaxOffset() > 0)
        {
            thumbY +=
                    (int) (
                            travel *
                            ((float) this.scrollOffset /
                                    (float) getMaxOffset())
                    );
        }

        boolean hovered =
                mouseX >= scrollbarX &&
                mouseX < scrollbarX + SCROLLBAR_WIDTH &&
                mouseY >= thumbY &&
                mouseY < thumbY + thumbHeight;

        GuiScreen.drawRect(
                scrollbarX,
                thumbY,
                scrollbarX + SCROLLBAR_WIDTH,
                thumbY + thumbHeight,
                hovered
                        ? EditorThemeManager.get().getAccent()
                        : COLOR_BORDER
        );
    }

    private String trim(
            Minecraft mc,
            String text,
            int width)
    {
        if (text == null)
        {
            return "";
        }

        if (mc.fontRenderer.getStringWidth(text) <= width)
        {
            return text;
        }

        return mc.fontRenderer.trimStringToWidth(
                text,
                Math.max(1, width - 10)
        ) + "...";
    }
}

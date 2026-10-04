package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * Scrollable actor list shared by Character, Pose and Body Parts modes.
 */
public class EditorActorListPanel
{
    private static final int ROW_HEIGHT = 31;
    private static final int CONTENT_TOP = 30;
    private static final int SCROLLBAR_WIDTH = 7;
    private static final int SCROLLBAR_GAP = 3;

    private int scroll;

    public void reset()
    {
        this.scroll = 0;
    }

    public void clamp(List<BlockbusterSceneActorData> actors)
    {
        int max = getMaxScroll(actors);
        this.scroll = Math.max(0, Math.min(this.scroll, max));
    }

    public int getScroll()
    {
        return this.scroll;
    }

    public void scroll(int direction, List<BlockbusterSceneActorData> actors)
    {
        if (direction == 0)
        {
            return;
        }

        int max = getMaxScroll(actors);

        this.scroll = Math.max(
                0,
                Math.min(
                        max,
                        this.scroll - direction
                )
        );
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            List<BlockbusterSceneActorData> actors,
            int panelWidth,
            int panelHeight)
    {
        if (mouseX < 0 || mouseX > panelWidth ||
                mouseY < CONTENT_TOP || mouseY >= panelHeight)
        {
            return false;
        }

        int relativeY = mouseY - CONTENT_TOP;
        int visibleRow = relativeY / ROW_HEIGHT;
        int actorIndex = this.scroll + visibleRow;

        if (visibleRow < 0 ||
                actorIndex < 0 ||
                actorIndex >= actors.size())
        {
            return false;
        }

        return true;
    }

    public int getActorIndexAt(
            int mouseX,
            int mouseY,
            List<BlockbusterSceneActorData> actors,
            int panelWidth,
            int panelHeight)
    {
        if (mouseX < 0 || mouseX > panelWidth ||
                mouseY < CONTENT_TOP || mouseY >= panelHeight)
        {
            return -1;
        }

        int relativeY = mouseY - CONTENT_TOP;
        int visibleRow = relativeY / ROW_HEIGHT;
        int actorIndex = this.scroll + visibleRow;

        if (visibleRow < 0 ||
                actorIndex < 0 ||
                actorIndex >= actors.size())
        {
            return -1;
        }

        return actorIndex;
    }

    public void draw(
            Minecraft mc,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            List<BlockbusterSceneActorData> actors,
            int selectedIndex,
            int colorPanel,
            int colorPanelDark,
            int colorSelected,
            int colorBorder,
            int colorText,
            int colorTextSecondary,
            int colorTextMuted,
            int accentColor)
    {
        if (mc == null)
        {
            return;
        }

        clamp(actors);

        drawRect(
                mc,
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                colorPanel
        );

        drawRect(
                mc,
                panelX + panelWidth - 1,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                colorBorder
        );

        drawPanelHeader(
                mc,
                "ACTORS",
                panelX,
                panelY,
                panelWidth,
                colorPanelDark,
                colorBorder,
                colorText,
                accentColor
        );

        if (actors.isEmpty())
        {
            mc.fontRenderer.drawString(
                    "No actors",
                    panelX + 12,
                    panelY + 38,
                    colorTextMuted
            );
            return;
        }

        int contentHeight = panelHeight - CONTENT_TOP;
        int visibleRows = Math.max(
                1,
                (contentHeight - 3) / ROW_HEIGHT
        );

        int start = this.scroll;
        int end = Math.min(
                actors.size(),
                start + visibleRows
        );

        int actorY = panelY + CONTENT_TOP;

        for (int i = start; i < end; i++)
        {
            BlockbusterSceneActorData actor = actors.get(i);

            if (actor == null)
            {
                actorY += ROW_HEIGHT;
                continue;
            }

            boolean selected = i == selectedIndex;

            if (selected)
            {
                drawRect(
                        mc,
                        panelX + 5,
                        actorY - 2,
                        panelX + panelWidth - 5,
                        actorY + ROW_HEIGHT - 2,
                        colorSelected
                );

                drawRect(
                        mc,
                        panelX + 5,
                        actorY - 2,
                        panelX + 7,
                        actorY + ROW_HEIGHT - 2,
                        accentColor
                );
            }

            String id = actor.getId();
            String name = actor.getName();

            if (id == null || id.length() == 0)
            {
                id = "Actor " + (i + 1);
            }

            if (name == null || name.length() == 0)
            {
                name = actor.getMorphName();
            }

            if (name == null || name.length() == 0)
            {
                name = "Unnamed Actor";
            }

            if (id.length() > 22)
            {
                id = id.substring(0, 19) + "...";
            }

            if (name.length() > 22)
            {
                name = name.substring(0, 19) + "...";
            }

            mc.fontRenderer.drawString(
                    id,
                    panelX + 13,
                    actorY + 1,
                    selected ? colorText : colorTextSecondary
            );

            mc.fontRenderer.drawString(
                    name,
                    panelX + 13,
                    actorY + 13,
                    selected ? accentColor : colorTextMuted
            );

            actorY += ROW_HEIGHT;
        }

        drawScrollbar(
                mc,
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                actors.size(),
                visibleRows,
                colorPanelDark,
                colorBorder,
                accentColor
        );
    }

    private int getMaxScroll(List<BlockbusterSceneActorData> actors)
    {
        int visibleRows = Math.max(
                1,
                (150 - CONTENT_TOP - 3) / ROW_HEIGHT
        );

        return Math.max(
                0,
                actors.size() - visibleRows
        );
    }

    private void drawScrollbar(
            Minecraft mc,
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int actorCount,
            int visibleRows,
            int colorPanelDark,
            int colorBorder,
            int accentColor)
    {
        if (actorCount <= visibleRows)
        {
            return;
        }

        int trackX =
                panelX + panelWidth -
                        SCROLLBAR_WIDTH -
                        SCROLLBAR_GAP;

        int trackTop =
                panelY + CONTENT_TOP;

        int trackBottom =
                panelY + panelHeight - 4;

        int trackHeight =
                Math.max(1, trackBottom - trackTop);

        drawRect(
                mc,
                trackX,
                trackTop,
                trackX + SCROLLBAR_WIDTH,
                trackBottom,
                colorPanelDark
        );

        int thumbHeight =
                Math.max(
                        16,
                        trackHeight * visibleRows / actorCount
                );

        int maxScroll = actorCount - visibleRows;

        int thumbTravel =
                Math.max(0, trackHeight - thumbHeight);

        int thumbY =
                trackTop +
                        (maxScroll == 0
                                ? 0
                                : thumbTravel * this.scroll / maxScroll);

        drawRect(
                mc,
                trackX,
                thumbY,
                trackX + SCROLLBAR_WIDTH,
                thumbY + thumbHeight,
                accentColor
        );
    }

    private void drawPanelHeader(
            Minecraft mc,
            String text,
            int x,
            int y,
            int width,
            int colorPanelDark,
            int colorBorder,
            int colorText,
            int accentColor)
    {
        drawRect(
                mc,
                x,
                y,
                x + width,
                y + 25,
                colorPanelDark
        );

        drawRect(
                mc,
                x,
                y + 24,
                x + width,
                y + 25,
                colorBorder
        );

        drawRect(
                mc,
                x + 9,
                y + 7,
                x + 11,
                y + 18,
                accentColor
        );

        mc.fontRenderer.drawString(
                text,
                x + 16,
                y + 8,
                colorText
        );
    }

    private void drawRect(
            Minecraft mc,
            int left,
            int top,
            int right,
            int bottom,
            int color)
    {
        net.minecraft.client.gui.Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }
}

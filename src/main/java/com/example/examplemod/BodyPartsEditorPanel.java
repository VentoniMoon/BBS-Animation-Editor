package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class BodyPartsEditorPanel
{
    public static final int WIDTH = 185;
    public static final int HEIGHT = 245;

    private int x;
    private int y;

    private final BodyPartsEditorController controller;
    private int modelPickerScroll;
    private boolean pickerOpen;

    public BodyPartsEditorPanel(
            BodyPartsEditorController controller)
    {
        this.controller = controller;
    }

    public void setPosition(int x, int y)
    {
        this.x = x;
        this.y = y;
    }

    public void draw(
            Minecraft mc,
            int mouseX,
            int mouseY,
            int sceneLength)
    {
        if (mc == null)
        {
            return;
        }

        drawRect(
                mc,
                x,
                y,
                x + WIDTH,
                y + HEIGHT,
                0xFF181818
        );

        drawRect(
                mc,
                x,
                y,
                x + WIDTH,
                y + 25,
                0xFF111111
        );

        drawRect(
                mc,
                x,
                y + 24,
                x + WIDTH,
                y + 25,
                0xFF303030
        );

        drawRect(
                mc,
                x + 9,
                y + 7,
                x + 11,
                y + 18,
                EditorThemeManager.get().getAccent()
        );

        mc.fontRenderer.drawString(
                "BODY PARTS",
                x + 16,
                y + 8,
                0xFFE2E5E7
        );

        int cy = y + 34;

        String target =
                controller.getSelectedActorBoneName();

        mc.fontRenderer.drawString(
                "ATTACH TO",
                x + 9,
                cy,
                0xFF9AA1A6
        );

        mc.fontRenderer.drawString(
                target.length() == 0 ? "Select a bone" : target,
                x + 9,
                cy + 14,
                target.length() == 0
                        ? 0xFF666D72
                        : EditorThemeManager.get().getAccentBright()
        );

        cy += 34;

        drawButton(
                mc,
                "ADD MODEL",
                x + 9,
                cy,
                WIDTH - 18,
                20,
                mouseX,
                mouseY
        );

        cy += 27;

        drawButton(
                mc,
                controller.getSelectedModel() == null
                        ? "SELECT MODEL"
                        : controller.getSelectedModel().getModelName(),
                x + 9,
                cy,
                WIDTH - 18,
                20,
                mouseX,
                mouseY
        );

        if (pickerOpen)
        {
            drawPicker(
                    mc,
                    mouseX,
                    mouseY,
                    cy + 22
            );
        }

        BodyPartModelData selected =
                controller.getSelectedModel();

        if (selected != null)
        {
            cy += 29;

            mc.fontRenderer.drawString(
                    "MODEL",
                    x + 9,
                    cy,
                    0xFF9AA1A6
            );

            mc.fontRenderer.drawString(
                    selected.getModelName(),
                    x + 9,
                    cy + 14,
                    EditorThemeManager.get().getAccentBright()
            );

            cy += 34;

            drawButton(
                    mc,
                    "REMOVE MODEL",
                    x + 9,
                    cy,
                    WIDTH - 18,
                    18,
                    mouseX,
                    mouseY
            );

            cy += 26;

            mc.fontRenderer.drawString(
                    "Click a model bar to edit its bones.",
                    x + 9,
                    cy,
                    0xFF666D72
            );

            mc.fontRenderer.drawString(
                    "Timeline keys are local to the model.",
                    x + 9,
                    cy + 12,
                    0xFF666D72
            );

            AnimationKeyframe keyframe =
                    controller.getKeyframeController()
                            .getSelectedKeyframe();

            if (keyframe != null)
            {
                controller.getTransformPanel().setPosition(
                        x + 5,
                        y + 174
                );

                controller.getTransformPanel().draw(
                        mc,
                        keyframe,
                        keyframe.getTransform(),
                        controller.getTimeline().getTick()
                );
            }
        }
    }

    private void drawPicker(
            Minecraft mc,
            int mouseX,
            int mouseY,
            int top)
    {
        List<String> names =
                controller.getAvailableModelNames();

        int width = WIDTH - 18;
        int rowHeight = 16;
        int maxRows = 8;

        int visible = Math.min(
                maxRows,
                Math.max(1, names.size())
        );

        drawRect(
                mc,
                x + 9,
                top,
                x + 9 + width,
                top + visible * rowHeight,
                0xFF101010
        );

        if (names.isEmpty())
        {
            mc.fontRenderer.drawString(
                    "No models",
                    x + 16,
                    top + 4,
                    0xFF666D72
            );
            return;
        }

        for (int i = 0; i < visible; i++)
        {
            String name = names.get(
                    i + modelPickerScroll
            );

            boolean hovered =
                    mouseX >= x + 9 &&
                    mouseX < x + 9 + width &&
                    mouseY >= top + i * rowHeight &&
                    mouseY < top + (i + 1) * rowHeight;

            if (hovered)
            {
                drawRect(
                        mc,
                        x + 9,
                        top + i * rowHeight,
                        x + 9 + width,
                        top + (i + 1) * rowHeight,
                        0xFF252525
                );
            }

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            name,
                            width - 10
                    ),
                    x + 14,
                    top + i * rowHeight + 4,
                    0xFFE2E5E7
            );
        }
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int sceneLength)
    {
        if (mouseButton != 0)
        {
            return false;
        }

        int addY = y + 68;

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= addY &&
                mouseY < addY + 20)
        {
            pickerOpen = true;
            return true;
        }

        int pickerY = y + 115;

        if (pickerOpen)
        {
            List<String> names =
                    controller.getAvailableModelNames();

            int width = WIDTH - 18;
            int rowHeight = 16;
            int visible = Math.min(8, names.size());

            for (int i = 0; i < visible; i++)
            {
                int rowY = pickerY + i * rowHeight;

                if (mouseX >= x + 9 &&
                        mouseX < x + 9 + width &&
                        mouseY >= rowY &&
                        mouseY < rowY + rowHeight)
                {
                    if (controller.addModel(
                            names.get(i),
                            sceneLength) != null)
                    {
                        pickerOpen = false;
                    }

                    return true;
                }
            }

            pickerOpen = false;
            return true;
        }

        BodyPartModelData selected =
                controller.getSelectedModel();

        int modelButtonY = y + 95;

        if (selected != null &&
                mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= y + 148 &&
                mouseY < y + 166)
        {
            controller.removeSelectedModel();
            return true;
        }

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= modelButtonY &&
                mouseY < modelButtonY + 20)
        {
            pickerOpen = !pickerOpen;
            return true;
        }

        return false;
    }

    private void drawButton(
            Minecraft mc,
            String text,
            int bx,
            int by,
            int bw,
            int bh,
            int mouseX,
            int mouseY)
    {
        boolean hovered =
                mouseX >= bx &&
                mouseX < bx + bw &&
                mouseY >= by &&
                mouseY < by + bh;

        drawRect(
                mc,
                bx,
                by,
                bx + bw,
                by + bh,
                hovered
                        ? 0xFF252525
                        : 0xFF202020
        );

        drawRect(
                mc,
                bx,
                by,
                bx + bw,
                by + 1,
                hovered
                        ? EditorThemeManager.get().getAccent()
                        : 0xFF303030
        );

        mc.fontRenderer.drawString(
                trim(mc, text, bw - 12),
                bx + 6,
                by + 6,
                hovered
                        ? EditorThemeManager.get().getAccentBright()
                        : 0xFFE2E5E7
        );
    }

    private String trim(
            Minecraft mc,
            String text,
            int maxWidth)
    {
        if (text == null)
        {
            return "";
        }

        if (mc.fontRenderer.getStringWidth(text) <= maxWidth)
        {
            return text;
        }

        String value = text;

        while (value.length() > 3 &&
                mc.fontRenderer.getStringWidth(value + "...") > maxWidth)
        {
            value = value.substring(0, value.length() - 1);
        }

        return value + "...";
    }

    public boolean mouseClickedTransform(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        AnimationKeyframe keyframe =
                controller.getKeyframeController()
                        .getSelectedKeyframe();

        if (keyframe == null)
        {
            return false;
        }

        return controller.getTransformPanel().mouseClicked(
                mouseX,
                mouseY,
                mouseButton,
                keyframe
        );
    }

    public void mouseDraggedTransform(
            int mouseX,
            int mouseY)
    {
        AnimationKeyframe keyframe =
                controller.getKeyframeController()
                        .getSelectedKeyframe();

        if (keyframe == null)
        {
            return;
        }

        controller.getTransformPanel().mouseDragged(
                mouseX,
                mouseY,
                keyframe
        );
    }

    public void mouseReleased(int mouseButton)
    {
        controller.getTransformPanel().mouseReleased(
                mouseButton
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
        Gui.drawRect(
                left,
                top,
                right,
                bottom,
                color
        );
    }
}

package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class BodyPartsEditorPanel
{
    public static final int WIDTH = 185;
    public static final int HEIGHT = 430;

    private int x;
    private int y;

    private final BodyPartsEditorController controller;
    private AnimationEditorScreen screen;

    public BodyPartsEditorPanel(
            BodyPartsEditorController controller)
    {
        this.controller = controller;
    }

    public void setScreen(AnimationEditorScreen screen)
    {
        this.screen = screen;
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

        BodyPartModelData selectedModel =
                controller.getSelectedModel();

        int cy = y + 34;

        /*
         * Level 2 is a dedicated model-bone editor.  Do not keep the
         * Level 1 attachment controls visible underneath TransformPanel:
         * they belong to a different editing context and visually collide
         * with the keyframe editor.
         */
        if (selectedModel != null)
        {
            cy = y + 34;

            mc.fontRenderer.drawString(
                    "MODEL",
                    x + 9,
                    cy,
                    0xFF9AA1A6
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            selectedModel.getModelName(),
                            WIDTH - 18
                    ),
                    x + 9,
                    cy + 14,
                    EditorThemeManager.get().getAccentBright()
            );

            cy += 34;

            mc.fontRenderer.drawString(
                    "EDIT MODEL BONES",
                    x + 9,
                    cy,
                    0xFFE2E5E7
            );

            mc.fontRenderer.drawString(
                    "Select or create keys on the timeline.",
                    x + 9,
                    cy + 14,
                    0xFF666D72
            );

            this.controller.getTransformPanel().setPosition(
                    x + 5,
                    y + 68
            );

            this.controller.getTransformPanel().draw(
                    mc,
                    controller.getKeyframeController()
                            .getSelectedKeyframe(),
                    controller.getKeyframeController()
                            .getSelectedKeyframe() == null
                            ? null
                            : controller.getKeyframeController()
                                    .getSelectedKeyframe()
                                    .getTransform(),
                    controller.getTimeline().getTick()
            );

            return;
        }

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

        mc.fontRenderer.drawString(
                "Double-click a frame to create",
                x + 9,
                cy + 1,
                0xFF666D72
        );

        mc.fontRenderer.drawString(
                "a 20-tick attachment.",
                x + 9,
                cy + 13,
                0xFF666D72
        );

        cy += 27;

        drawButton(
                mc,
                controller.getSelectedAttachment() == null
                        ? "SELECT ATTACHMENT"
                        : (controller.getSelectedAttachment().hasModel()
                                ? controller.getSelectedAttachment().getModelName()
                                : "SELECT MODEL"),
                x + 9,
                cy,
                WIDTH - 18,
                20,
                mouseX,
                mouseY
        );

        BodyPartModelData selected =
                controller.getSelectedAttachment();

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
                    selected.hasModel()
                        ? selected.getModelName()
                        : "No model assigned",
                    x + 9,
                    cy + 14,
                    selected.hasModel()
                        ? EditorThemeManager.get().getAccentBright()
                        : 0xFF666D72
            );

            cy += 34;

            drawButton(
                    mc,
                    "REMOVE ATTACHMENT",
                    x + 9,
                    cy,
                    WIDTH - 18,
                    18,
                    mouseX,
                    mouseY
            );

            cy += 26;

            mc.fontRenderer.drawString(
                    selected.hasModel()
                            ? "Double-click the model bar to edit."
                            : "Choose a model for this attachment.",
                    x + 9,
                    cy,
                    0xFF666D72
            );

            if (selected.hasModel())
            {
                mc.fontRenderer.drawString(
                        "Keys are local to the model.",
                        x + 9,
                        cy + 12,
                        0xFF666D72
                );
            }
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

        if (controller.getSelectedModel() != null)
        {
            return false;
        }

        int addY = y + 68;

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= addY &&
                mouseY < addY + 20)
        {
            controller.createAttachment(
                    controller.getTimeline().getTick(),
                    sceneLength
            );

            return true;
        }

        BodyPartModelData selected =
                controller.getSelectedAttachment();

        int modelButtonY = y + 95;

        if (selected != null &&
                mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= y + 148 &&
                mouseY < y + 166)
        {
            controller.removeSelectedAttachment();
            return true;
        }

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= modelButtonY &&
                mouseY < modelButtonY + 20)
        {
            if (this.screen != null &&
                    controller.getSelectedAttachment() != null)
            {
                Minecraft.getMinecraft().displayGuiScreen(
                        new BodyPartModelPickerScreen(
                                Minecraft.getMinecraft(),
                                this.screen,
                                (modelName) -> controller.assignModelToSelected(
                                        modelName
                                )
                        )
                );
            }

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

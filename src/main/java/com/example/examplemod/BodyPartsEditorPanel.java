package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class BodyPartsEditorPanel
{
    public static final int WIDTH = 185;
    public static final int HEIGHT = 330;

    private static final float POSITION_STEP = 0.1F;
    private static final float ROTATION_STEP = 1.0F;
    private static final float SCALE_STEP = 0.01F;

    private int x;
    private int y;

    private final BodyPartsEditorController controller;
    private AnimationEditorScreen screen;

    private final AnimationValueControl globalPositionX =
            new AnimationValueControl("X", 0.0F, POSITION_STEP);
    private final AnimationValueControl globalPositionY =
            new AnimationValueControl("Y", 0.0F, POSITION_STEP);
    private final AnimationValueControl globalPositionZ =
            new AnimationValueControl("Z", 0.0F, POSITION_STEP);

    private final AnimationValueControl globalRotationX =
            new AnimationValueControl("X", 0.0F, ROTATION_STEP);
    private final AnimationValueControl globalRotationY =
            new AnimationValueControl("Y", 0.0F, ROTATION_STEP);
    private final AnimationValueControl globalRotationZ =
            new AnimationValueControl("Z", 0.0F, ROTATION_STEP);

    private final AnimationValueControl globalScaleX =
            new AnimationValueControl("X", 1.0F, SCALE_STEP);
    private final AnimationValueControl globalScaleY =
            new AnimationValueControl("Y", 1.0F, SCALE_STEP);
    private final AnimationValueControl globalScaleZ =
            new AnimationValueControl("Z", 1.0F, SCALE_STEP);

    private AnimationValueControl activeGlobalControl;

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

        drawRect(mc, x, y, x + WIDTH, y + HEIGHT, 0xFF181818);
        drawRect(mc, x, y, x + WIDTH, y + 25, 0xFF111111);
        drawRect(mc, x, y + 24, x + WIDTH, y + 25, 0xFF303030);

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

        /*
         * Level 2 remains the local bone/keyframe editor.
         * The global model transform belongs to Level 1.
         */
        if (selectedModel != null)
        {
            controller.getTransformPanel().setPosition(
                    x + 5,
                    y + 34
            );

            controller.getTransformPanel().draw(
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
                y + 34,
                0xFF9AA1A6
        );

        mc.fontRenderer.drawString(
                target.length() == 0
                        ? "Select a bone"
                        : target,
                x + 9,
                y + 48,
                target.length() == 0
                        ? 0xFF666D72
                        : EditorThemeManager.get().getAccentBright()
        );

        BodyPartModelData selected =
                controller.getSelectedAttachment();

        if (selected == null)
        {
            mc.fontRenderer.drawString(
                    "Double-click an actor-bone row to create.",
                    x + 9,
                    y + 75,
                    0xFF666D72
            );

            return;
        }

        /*
         * ---------------------------------------------------------
         * MODEL
         * ---------------------------------------------------------
         */
        drawSection(
                mc,
                "MODEL",
                y + 67
        );

        drawButton(
                mc,
                selected.hasModel()
                        ? selected.getModelName()
                        : "SELECT MODEL",
                x + 9,
                y + 80,
                WIDTH - 18,
                20,
                mouseX,
                mouseY
        );

        drawButton(
                mc,
                "REMOVE ATTACHMENT",
                x + 9,
                y + 104,
                WIDTH - 18,
                17,
                mouseX,
                mouseY
        );

        /*
         * ---------------------------------------------------------
         * GLOBAL TRANSFORM
         * ---------------------------------------------------------
         */
        drawSection(
                mc,
                "GLOBAL TRANSFORM",
                y + 128
        );

        AnimationTransform transform =
                selected.getGlobalTransform();

        syncGlobalControls(transform);

        drawGlobalControls(
                mc,
                mouseX,
                mouseY
        );

        writeGlobalTransform(transform);
    }

    private void drawSection(
            Minecraft mc,
            String title,
            int drawY)
    {
        drawRect(
                mc,
                x + 8,
                drawY,
                x + WIDTH - 8,
                drawY + 1,
                0xFF303030
        );

        drawRect(
                mc,
                x + 8,
                drawY + 6,
                x + 11,
                drawY + 15,
                EditorThemeManager.get().getAccent()
        );

        mc.fontRenderer.drawString(
                title,
                x + 16,
                drawY + 5,
                EditorThemeManager.get().getAccentBright()
        );
    }

    private void syncGlobalControls(
            AnimationTransform transform)
    {
        globalPositionX.setValue(transform.getPositionX());
        globalPositionY.setValue(transform.getPositionY());
        globalPositionZ.setValue(transform.getPositionZ());

        globalRotationX.setValue(transform.getRotationX());
        globalRotationY.setValue(transform.getRotationY());
        globalRotationZ.setValue(transform.getRotationZ());

        globalScaleX.setValue(transform.getScaleX());
        globalScaleY.setValue(transform.getScaleY());
        globalScaleZ.setValue(transform.getScaleZ());

        int cx = x + 9;

        globalPositionX.setPosition(cx, y + 148);
        globalPositionY.setPosition(cx, y + 166);
        globalPositionZ.setPosition(cx, y + 184);

        globalRotationX.setPosition(cx, y + 207);
        globalRotationY.setPosition(cx, y + 225);
        globalRotationZ.setPosition(cx, y + 243);

        globalScaleX.setPosition(cx, y + 266);
        globalScaleY.setPosition(cx, y + 284);
        globalScaleZ.setPosition(cx, y + 302);
    }

    private void drawGlobalControls(
            Minecraft mc,
            int mouseX,
            int mouseY)
    {
        mc.fontRenderer.drawString(
                "Position",
                x + 9,
                y + 137,
                0xFF55FFFF
        );

        globalPositionX.draw(mc);
        globalPositionY.draw(mc);
        globalPositionZ.draw(mc);

        mc.fontRenderer.drawString(
                "Rotation",
                x + 9,
                y + 196,
                0xFF55FF55
        );

        globalRotationX.draw(mc);
        globalRotationY.draw(mc);
        globalRotationZ.draw(mc);

        mc.fontRenderer.drawString(
                "Scale",
                x + 9,
                y + 255,
                0xFFFFFF55
        );

        globalScaleX.draw(mc);
        globalScaleY.draw(mc);
        globalScaleZ.draw(mc);
    }

    private void writeGlobalTransform(
            AnimationTransform transform)
    {
        if (activeGlobalControl != null &&
                activeGlobalControl.isEditing())
        {
            return;
        }

        transform.setPosition(
                globalPositionX.getValue(),
                globalPositionY.getValue(),
                globalPositionZ.getValue()
        );

        transform.setRotation(
                globalRotationX.getValue(),
                globalRotationY.getValue(),
                globalRotationZ.getValue()
        );

        transform.setScale(
                globalScaleX.getValue(),
                globalScaleY.getValue(),
                globalScaleZ.getValue()
        );
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

        if (controller.getSelectedAttachment() == null &&
                mouseX >= x + 9 &&
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

        if (selected == null)
        {
            return false;
        }

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= y + 104 &&
                mouseY < y + 121)
        {
            controller.removeSelectedAttachment();
            return true;
        }

        if (mouseX >= x + 9 &&
                mouseX < x + WIDTH - 9 &&
                mouseY >= y + 80 &&
                mouseY < y + 100)
        {
            if (this.screen != null)
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

    private boolean globalContains(
            int mouseX,
            int mouseY)
    {
        return mouseX >= x + 8 &&
                mouseX < x + WIDTH - 8 &&
                mouseY >= y + 145 &&
                mouseY < y + HEIGHT - 4;
    }

    private AnimationValueControl findGlobalControl(
            int mouseX,
            int mouseY)
    {
        AnimationValueControl[] controls =
                new AnimationValueControl[]
                {
                        globalPositionX,
                        globalPositionY,
                        globalPositionZ,
                        globalRotationX,
                        globalRotationY,
                        globalRotationZ,
                        globalScaleX,
                        globalScaleY,
                        globalScaleZ
                };

        for (AnimationValueControl control : controls)
        {
            if (control.contains(mouseX, mouseY))
            {
                return control;
            }
        }

        return null;
    }

    private boolean mouseClickedGlobalTransform(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (controller.getSelectedAttachment() == null ||
                !globalContains(mouseX, mouseY))
        {
            return false;
        }

        AnimationValueControl control =
                findGlobalControl(mouseX, mouseY);

        if (control == null)
        {
            return false;
        }

        if (activeGlobalControl != null &&
                activeGlobalControl != control &&
                activeGlobalControl.isEditing())
        {
            activeGlobalControl.finishEditing();
        }

        activeGlobalControl = control;

        return control.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    private void mouseDraggedGlobalTransform(
            int mouseX,
            int mouseY)
    {
        if (activeGlobalControl == null)
        {
            return;
        }

        if (activeGlobalControl.isEditing())
        {
            return;
        }

        activeGlobalControl.mouseDragged(
                mouseX,
                mouseY
        );

        AnimationTransform transform =
                controller.getSelectedAttachment()
                        .getGlobalTransform();

        transform.setPosition(
                globalPositionX.getValue(),
                globalPositionY.getValue(),
                globalPositionZ.getValue()
        );

        transform.setRotation(
                globalRotationX.getValue(),
                globalRotationY.getValue(),
                globalRotationZ.getValue()
        );

        transform.setScale(
                globalScaleX.getValue(),
                globalScaleY.getValue(),
                globalScaleZ.getValue()
        );
    }

    public boolean mouseClickedTransform(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        if (controller.getSelectedModel() == null &&
                mouseClickedGlobalTransform(
                        mouseX,
                        mouseY,
                        mouseButton))
        {
            return true;
        }

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
        if (controller.getSelectedModel() == null &&
                activeGlobalControl != null)
        {
            mouseDraggedGlobalTransform(
                    mouseX,
                    mouseY
            );
            return;
        }

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
        if (activeGlobalControl != null)
        {
            activeGlobalControl.mouseReleased(
                    mouseButton
            );
        }

        controller.getTransformPanel().mouseReleased(
                mouseButton
        );
    }

    public boolean keyTyped(
            char typedChar,
            int keyCode)
    {
        if (controller.getSelectedModel() == null &&
                activeGlobalControl != null)
        {
            boolean handled =
                    activeGlobalControl.keyTyped(
                            typedChar,
                            keyCode
                    );

            if (handled &&
                    !activeGlobalControl.isEditing() &&
                    controller.getSelectedAttachment() != null)
            {
                writeGlobalTransform(
                        controller.getSelectedAttachment()
                                .getGlobalTransform()
                );
            }

            return handled;
        }

        AnimationKeyframe keyframe =
                controller.getKeyframeController()
                        .getSelectedKeyframe();

        if (keyframe != null)
        {
            return controller.getTransformPanel().keyTyped(
                    typedChar,
                    keyCode,
                    keyframe
            );
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
                by + bh,
                0xFF303030
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
                by + 5,
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

    private void drawRect(
            Minecraft mc,
            int left,
            int top,
            int right,
            int bottom,
            int color)
    {
        Gui.drawRect(left, top, right, bottom, color);
    }
}

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

    private boolean modelSectionOpen = true;
    private boolean globalSectionOpen = false;
    private int globalScroll = 0;
    private static final int SECTION_HEIGHT = 22;
    private static final int GLOBAL_CONTENT_HEIGHT = 230;

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
        if (mc == null) return;

        drawRect(mc, x, y, x + WIDTH, y + HEIGHT, 0xFF181818);
        BodyPartModelData selectedModel = controller.getSelectedModel();
        if (selectedModel != null)
        {
            /*
             * When editing an individual Body Part key, give the
             * transform panel the full upper area. The BODY PARTS
             * header is no longer needed here and was wasting 25 px.
             */
            controller.getTransformPanel().setPosition(x + 5, y + 5);
            controller.getTransformPanel().draw(
                    mc,
                    controller.getKeyframeController().getSelectedKeyframe(),
                    controller.getKeyframeController().getSelectedKeyframe() == null
                            ? null
                            : controller.getKeyframeController().getSelectedKeyframe().getTransform(),
                    controller.getTimeline().getTick()
            );
            return;
        }

        drawRect(mc, x, y, x + WIDTH, y + 25, 0xFF111111);
        drawRect(mc, x, y + 24, x + WIDTH, y + 25, 0xFF303030);
        drawRect(mc, x + 9, y + 7, x + 11, y + 18,
                EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString("BODY PARTS", x + 16, y + 8, 0xFFE2E5E7);

        String target = controller.getSelectedActorBoneName();
        mc.fontRenderer.drawString("ATTACH TO", x + 9, y + 34, 0xFF9AA1A6);
        mc.fontRenderer.drawString(
                target.length() == 0 ? "Select a bone" : target,
                x + 9, y + 48,
                target.length() == 0 ? 0xFF666D72 : EditorThemeManager.get().getAccentBright()
        );

        BodyPartModelData selected = controller.getSelectedAttachment();
        if (selected == null)
        {
            mc.fontRenderer.drawString(
                    "Double-click an actor-bone row to create.",
                    x + 9, y + 75, 0xFF666D72
            );
            return;
        }

        int modelY = y + 67;
        int globalY = modelY + SECTION_HEIGHT + (modelSectionOpen ? 47 : 0);

        drawAccordionHeader(mc, "MODEL", modelY, modelSectionOpen, mouseX, mouseY);
        if (modelSectionOpen)
        {
            drawButton(mc,
                    selected.hasModel() ? selected.getModelName() : "SELECT MODEL",
                    x + 9, modelY + SECTION_HEIGHT, WIDTH - 18, 20, mouseX, mouseY);
            drawButton(mc, "REMOVE ATTACHMENT",
                    x + 9, modelY + SECTION_HEIGHT + 24, WIDTH - 18, 17, mouseX, mouseY);
        }

        drawAccordionHeader(mc, "GLOBAL TRANSFORM", globalY, globalSectionOpen, mouseX, mouseY);
        if (globalSectionOpen)
        {
            AnimationTransform transform = selected.getGlobalTransform();
            int viewportTop = globalY + SECTION_HEIGHT;
            int viewportBottom = y + HEIGHT - 5;
            drawRect(mc, x + 6, viewportTop, x + WIDTH - 6, viewportBottom, 0xFF151515);
            syncGlobalControls(transform, viewportTop);
            drawGlobalControls(mc, viewportTop, viewportBottom);
            writeGlobalTransform(transform);
        }
    }

    private void drawAccordionHeader(
            Minecraft mc, String title, int drawY, boolean open,
            int mouseX, int mouseY)
    {
        boolean hovered = mouseX >= x + 7 && mouseX < x + WIDTH - 7
                && mouseY >= drawY && mouseY < drawY + SECTION_HEIGHT;
        drawRect(mc, x + 7, drawY, x + WIDTH - 7, drawY + SECTION_HEIGHT,
                hovered ? 0xFF252525 : 0xFF1D1D1D);
        drawRect(mc, x + 7, drawY, x + 9, drawY + SECTION_HEIGHT,
                EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString(title, x + 15, drawY + 6,
                hovered ? EditorThemeManager.get().getAccentBright() : 0xFFE2E5E7);
        mc.fontRenderer.drawString(open ? "-" : "+", x + WIDTH - 18, drawY + 6,
                EditorThemeManager.get().getAccentBright());
    }

    private void syncGlobalControls(AnimationTransform transform, int viewportTop)
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
        int contentY = viewportTop + 4 - globalScroll;
        globalPositionX.setPosition(cx, contentY + 15);
        globalPositionY.setPosition(cx, contentY + 36);
        globalPositionZ.setPosition(cx, contentY + 57);
        globalRotationX.setPosition(cx, contentY + 89);
        globalRotationY.setPosition(cx, contentY + 110);
        globalRotationZ.setPosition(cx, contentY + 131);
        globalScaleX.setPosition(cx, contentY + 163);
        globalScaleY.setPosition(cx, contentY + 184);
        globalScaleZ.setPosition(cx, contentY + 205);
    }

    private void drawGlobalControls(Minecraft mc, int viewportTop, int viewportBottom)
    {
        int contentY = viewportTop + 4 - globalScroll;
        drawGlobalLabel(mc, "Position", contentY + 1, viewportTop, viewportBottom);
        globalPositionX.draw(mc); globalPositionY.draw(mc); globalPositionZ.draw(mc);
        drawGlobalLabel(mc, "Rotation", contentY + 75, viewportTop, viewportBottom);
        globalRotationX.draw(mc); globalRotationY.draw(mc); globalRotationZ.draw(mc);
        drawGlobalLabel(mc, "Scale", contentY + 150, viewportTop, viewportBottom);
        globalScaleX.draw(mc); globalScaleY.draw(mc); globalScaleZ.draw(mc);
    }

    private void drawGlobalLabel(Minecraft mc, String text, int drawY,
            int viewportTop, int viewportBottom)
    {
        if (drawY + 10 < viewportTop || drawY > viewportBottom) return;
        mc.fontRenderer.drawString(text, x + 9, drawY,
                EditorThemeManager.get().getAccentBright());
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
            int mouseX, int mouseY, int mouseButton, int sceneLength)
    {
        if (mouseButton != 0 || controller.getSelectedModel() != null) return false;

        BodyPartModelData selected = controller.getSelectedAttachment();
        if (selected == null)
        {
            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9
                    && mouseY >= y + 68 && mouseY < y + 88)
            {
                controller.createAttachment(controller.getTimeline().getTick(), sceneLength);
                return true;
            }
            return false;
        }

        int modelY = y + 67;
        int globalY = modelY + SECTION_HEIGHT + (modelSectionOpen ? 47 : 0);

        if (insideSectionHeader(mouseX, mouseY, modelY))
        {
            modelSectionOpen = !modelSectionOpen;
            if (modelSectionOpen) globalSectionOpen = false;
            activeGlobalControl = null;
            return true;
        }

        if (insideSectionHeader(mouseX, mouseY, globalY))
        {
            globalSectionOpen = !globalSectionOpen;
            if (globalSectionOpen) modelSectionOpen = false;
            globalScroll = 0;
            activeGlobalControl = null;
            return true;
        }

        if (modelSectionOpen)
        {
            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9
                    && mouseY >= modelY + SECTION_HEIGHT
                    && mouseY < modelY + SECTION_HEIGHT + 20)
            {
                if (this.screen != null)
                {
                    Minecraft.getMinecraft().displayGuiScreen(
                            new BodyPartModelPickerScreen(
                                    Minecraft.getMinecraft(), this.screen,
                                    (modelName) -> controller.assignModelToSelected(modelName)
                            )
                    );
                }
                return true;
            }

            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9
                    && mouseY >= modelY + SECTION_HEIGHT + 24
                    && mouseY < modelY + SECTION_HEIGHT + 41)
            {
                controller.removeSelectedAttachment();
                return true;
            }
        }

        return false;
    }

    private boolean insideSectionHeader(int mouseX, int mouseY, int sectionY)
    {
        return mouseX >= x + 7 && mouseX < x + WIDTH - 7
                && mouseY >= sectionY && mouseY < sectionY + SECTION_HEIGHT;
    }

    private boolean globalContains(int mouseX, int mouseY)
    {
        if (!globalSectionOpen) return false;
        int globalY = y + 67 + SECTION_HEIGHT + (modelSectionOpen ? 47 : 0);
        int viewportTop = globalY + SECTION_HEIGHT;
        int viewportBottom = y + HEIGHT - 5;
        return mouseX >= x + 6 && mouseX < x + WIDTH - 6
                && mouseY >= viewportTop && mouseY < viewportBottom;
    }

    private AnimationValueControl findGlobalControl(int mouseX, int mouseY)
    {
        if (!globalContains(mouseX, mouseY)) return null;
        AnimationValueControl[] controls = new AnimationValueControl[] {
                globalPositionX, globalPositionY, globalPositionZ,
                globalRotationX, globalRotationY, globalRotationZ,
                globalScaleX, globalScaleY, globalScaleZ
        };
        for (AnimationValueControl control : controls)
        {
            if (control.contains(mouseX, mouseY)) return control;
        }
        return null;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheel)
    {
        if (!globalContains(mouseX, mouseY) || wheel == 0) return false;
        int globalY = y + 67 + SECTION_HEIGHT + (modelSectionOpen ? 47 : 0);
        int viewportTop = globalY + SECTION_HEIGHT;
        int viewportBottom = y + HEIGHT - 5;
        int maxScroll = Math.max(0,
                GLOBAL_CONTENT_HEIGHT - (viewportBottom - viewportTop));
        globalScroll = Math.max(0, Math.min(maxScroll, globalScroll - wheel * 18));
        return true;
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

    public boolean isGlobalTransformActive()
    {
        return this.activeGlobalControl != null;
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

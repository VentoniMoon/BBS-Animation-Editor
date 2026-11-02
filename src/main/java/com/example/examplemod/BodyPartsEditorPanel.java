package com.example.examplemod;

import net.minecraft.client.Minecraft;
import mchorse.metamorph.api.morphs.AbstractMorph;
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
    private int panelHeight = HEIGHT;

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

    private final BodyPartPhysicsPanel physicsPanel = new BodyPartPhysicsPanel();
    private boolean physicsView;

    private AnimationBone selectedInternalBone()
    {
        BodyPartModelData model = controller.getSelectedModel();
        return model == null ? null :
                model.getBone(controller.getKeyframeController().getSelectedBoneIndex());
    }

    private void configurePhysicsPanel()
    {
        BodyPartModelData model = controller.getSelectedModel();
        AnimationBone bone = selectedInternalBone();
        if (model != null && bone != null)
        {
            physicsPanel.setTarget(x + 5, y + 28,
                    bone.getName(), model.getPhysicsSettings(bone.getName()),
                    model.getCollisionSettings(bone.getName()),
                    countChainBones(bone));
        }
    }

    private static int countChainBones(AnimationBone root)
    {
        if (root == null) return 0;
        int count = 1;
        for (AnimationBone child : root.getChildren())
            count += countChainBones(child);
        return count;
    }

    private boolean modelSectionOpen = true;
    private boolean globalSectionOpen = false;
    private int globalScroll = 0;
    private static final int SECTION_HEIGHT = 22;

    /* Compact layout: last Scale Z starts at +170 and occupies 16px.
     * Allow a small bottom margin; only scroll on unusually short screens. */
    private static final int GLOBAL_CONTENT_HEIGHT = 194;

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
        this.panelHeight = HEIGHT;
    }

    public void setBounds(int x, int y, int height)
    {
        this.x = x;
        this.y = y;
        this.panelHeight = Math.max(1, height);
    }

    public void draw(
            Minecraft mc,
            int mouseX,
            int mouseY,
            int sceneLength)
    {
        if (mc == null) return;

        drawRect(mc, x, y, x + WIDTH, y + panelHeight,
                EditorVisualStyle.themedSurface(0.075F));
        BodyPartModelData selectedModel = controller.getSelectedModel();
        if (selectedModel != null)
        {
            /* Physics is isolated from the shared Pose TransformPanel. */
            drawRect(mc, x + 5, y + 5, x + 91, y + 25,
                    EditorVisualStyle.themedSurface(physicsView ? 0.11F : 0.26F));
            drawRect(mc, x + 94, y + 5, x + 180, y + 25,
                    EditorVisualStyle.themedSurface(physicsView ? 0.26F : 0.11F));
            mc.fontRenderer.drawString("TRANSFORM", x + 13, y + 11, 0xFFFFFFFF);
            mc.fontRenderer.drawString("PHYSICS", x + 112, y + 11, 0xFFFFFFFF);

            if (physicsView)
            {
                configurePhysicsPanel();
                if (selectedInternalBone() != null)
                    physicsPanel.draw(mc, mouseX, mouseY);
            }
            else
            {
                // TransformPanel now fills the complete 185px inspector.
                // Do not inset it by 5px: that clips its right border.
                controller.getTransformPanel().setPosition(x, y + 28);
                controller.getTransformPanel().draw(
                        mc,
                        controller.getKeyframeController().getSelectedKeyframe(),
                        controller.getKeyframeController().getSelectedKeyframe() == null
                                ? null
                                : controller.getKeyframeController().getSelectedKeyframe().getTransform(),
                        controller.getTimeline().getTick()
                );
            }
            return;
        }

        drawRect(mc, x, y, x + WIDTH, y + 25,
                EditorVisualStyle.themedSurface(0.15F));
        drawRect(mc, x, y + 24, x + WIDTH, y + 25,
                EditorVisualStyle.themedSurface(0.22F));
        drawRect(mc, x + 9, y + 7, x + 11, y + 18,
                EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString("BODY PARTS", x + 16, y + 8, 0xFFE2E5E7);

        BodyPartModelData selected = controller.getSelectedAttachment();
        if (selected == null)
        {
            mc.fontRenderer.drawString(
                    "Double-click an actor-bone row to create.",
                    x + 9, y + 75, 0xFF666D72
            );
            return;
        }

        /*
         * The old layout reserved space for "ATTACH TO" and the selected
         * bone name. That made MODEL start far too low and left the rest
         * of the inspector cramped.
         *
         * MODEL now starts directly below the BODY PARTS title bar.
         * The selected actor bone is still used internally by the
         * controller, but it is no longer rendered in this inspector.
         */
        int modelY = y + 30;
        int globalY = getGlobalY(modelY);

        drawAccordionHeader(mc, "MODEL", modelY, modelSectionOpen, mouseX, mouseY);
        if (modelSectionOpen)
        {
            drawButton(mc,
                    selected.hasModel() ? selected.getModelName() : "SELECT MODEL",
                    x + 9, modelY + SECTION_HEIGHT, WIDTH - 18, 20, mouseX, mouseY);
            drawButton(mc, "REMOVE ATTACHMENT",
                    x + 9, modelY + SECTION_HEIGHT + 24, WIDTH - 18, 17, mouseX, mouseY);
            boolean chameleon=isChameleonWeldCandidate(selected);
            drawButton(mc,
                    chameleon
                            ? "WELD GEOMETRY: " + (selected.isWeldEnabled() ? "ON" : "OFF")
                            : "WELD: CHAMELEON ONLY",
                    x + 9, modelY + SECTION_HEIGHT + 46, WIDTH - 18, 18,
                    mouseX, mouseY);
        }

        drawAccordionHeader(mc, "GLOBAL TRANSFORM", globalY, globalSectionOpen, mouseX, mouseY);
        if (globalSectionOpen)
        {
            AnimationTransform transform = selected.getGlobalTransform();
            int viewportTop = globalY + SECTION_HEIGHT;
            int viewportBottom = y + panelHeight - 5;
            drawRect(mc, x + 6, viewportTop, x + WIDTH - 6, viewportBottom,
                    EditorVisualStyle.themedSurface(0.05F));
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
                EditorVisualStyle.themedSurface(hovered ? 0.25F : 0.16F));
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
        int contentY = viewportTop + 3 - globalScroll;
        // Compact 17 px pitch and 11 px group labels. All nine controls
        // fit within 194 px instead of extending below the timeline.
        AnimationValueControl[] controls = {
                globalPositionX, globalPositionY, globalPositionZ,
                globalRotationX, globalRotationY, globalRotationZ,
                globalScaleX, globalScaleY, globalScaleZ
        };
        int[] offsets = {12,29,46,74,91,108,136,153,170};
        for (int i=0;i<controls.length;i++)
        {
            controls[i].setPosition(cx, contentY + offsets[i]);
            controls[i].setWidth(WIDTH - 18);
        }
    }

    private void drawGlobalControls(Minecraft mc, int viewportTop, int viewportBottom)
    {
        int contentY = viewportTop + 3 - globalScroll;
        drawGlobalLabel(mc, "POSITION", contentY, viewportTop, viewportBottom);
        globalPositionX.draw(mc); globalPositionY.draw(mc); globalPositionZ.draw(mc);
        drawGlobalLabel(mc, "ROTATION", contentY + 62, viewportTop, viewportBottom);
        globalRotationX.draw(mc); globalRotationY.draw(mc); globalRotationZ.draw(mc);
        drawGlobalLabel(mc, "SCALE", contentY + 124, viewportTop, viewportBottom);
        globalScaleX.draw(mc); globalScaleY.draw(mc); globalScaleZ.draw(mc);
    }

    private void drawGlobalLabel(Minecraft mc, String text, int drawY,
            int viewportTop, int viewportBottom)
    {
        if (drawY + 10 < viewportTop || drawY > viewportBottom) return;
        drawRect(mc, x + 9, drawY - 1, x + WIDTH - 9, drawY + 11,
                EditorVisualStyle.themedSurface(0.16F));
        drawRect(mc, x + 9, drawY - 1, x + 11, drawY + 11,
                EditorThemeManager.get().getAccent());
        mc.fontRenderer.drawString(text, x + 15, drawY + 1,
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

        int modelY = y + 30;
        int globalY = getGlobalY(modelY);

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
                                    (morph) -> controller.assignMorphToSelected((AbstractMorph) morph)
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

            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9
                    && mouseY >= modelY + SECTION_HEIGHT + 46
                    && mouseY < modelY + SECTION_HEIGHT + 64)
            {
                if (isChameleonWeldCandidate(selected))
                    selected.setWeldEnabled(!selected.isWeldEnabled());
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

    /**
     * Weld's current geometry solver supports Chameleon cuboid bones.
     * Avoid exposing an active switch for unrelated Emoticons/Blockbuster
     * attachments until they have their own renderer implementations.
     */
    private static boolean isChameleonWeldCandidate(BodyPartModelData model)
    {
        if (model == null) return false;
        AbstractMorph morph=model.getBaseMorph();
        return morph!=null &&
                "mchorse.chameleon.metamorph.ChameleonMorph"
                        .equals(morph.getClass().getName());
    }

    private int getGlobalY(int modelY)
    {
        return modelY + SECTION_HEIGHT + (modelSectionOpen ? 70 : 0);
    }

    private int getGlobalViewportTop(int modelY)
    {
        return getGlobalY(modelY) + SECTION_HEIGHT;
    }

    private int getGlobalViewportBottom()
    {
        return y + panelHeight - 5;
    }

    private int getGlobalViewportHeight(int modelY)
    {
        return Math.max(
                0,
                getGlobalViewportBottom()
                        - getGlobalViewportTop(modelY)
        );
    }

    private int getGlobalMaxScroll(int modelY)
    {
        return Math.max(
                0,
                GLOBAL_CONTENT_HEIGHT
                        - getGlobalViewportHeight(modelY)
        );
    }

    private boolean globalContains(int mouseX, int mouseY)
    {
        if (!globalSectionOpen) return false;

        int modelY = y + 30;
        int viewportTop = getGlobalViewportTop(modelY);
        int viewportBottom = getGlobalViewportBottom();

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
        int modelY = y + 30;
        int maxScroll = getGlobalMaxScroll(modelY);

        globalScroll = Math.max(
                0,
                Math.min(
                        maxScroll,
                        globalScroll - wheel * 18
                )
        );
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

        boolean handled = control.mouseClicked(mouseX, mouseY, mouseButton);
        if (handled && control.isStepperHit(mouseX, mouseY))
            writeGlobalTransform(controller.getSelectedAttachment().getGlobalTransform());
        return handled;
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
        if (controller.getSelectedModel() != null)
        {
            if (mouseButton == 0 && mouseY >= y + 5 && mouseY < y + 25)
            {
                if (mouseX >= x + 5 && mouseX < x + 91)
                {
                    physicsView = false;
                    return true;
                }
                if (mouseX >= x + 94 && mouseX < x + 180)
                {
                    physicsView = true;
                    return true;
                }
            }
            if (physicsView)
            {
                configurePhysicsPanel();
                return physicsPanel.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
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
        if (controller.getSelectedModel() != null && physicsView) return;
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
        if (controller.getSelectedModel() != null && physicsView)
            return physicsPanel.keyTyped(typedChar, keyCode);
        if (controller.getSelectedModel() == null &&
                activeGlobalControl != null)
        {
            boolean handled =
                    activeGlobalControl.keyTyped(
                            typedChar,
                            keyCode
                    );

            if (handled &&
                    (!activeGlobalControl.isEditing() || keyCode == org.lwjgl.input.Keyboard.KEY_UP
                        || keyCode == org.lwjgl.input.Keyboard.KEY_DOWN) &&
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
                EditorVisualStyle.themedSurface(hovered ? 0.23F : 0.13F)
        );

        // Only a thin border: don't paint over the hover fill.
        drawRect(mc, bx, by + bh - 1, bx + bw, by + bh,
                EditorVisualStyle.themedSurface(0.25F));

        drawRect(
                mc,
                bx,
                by,
                bx + bw,
                by + 1,
                hovered
                        ? EditorThemeManager.get().getAccent()
                        : EditorVisualStyle.themedSurface(0.21F)
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

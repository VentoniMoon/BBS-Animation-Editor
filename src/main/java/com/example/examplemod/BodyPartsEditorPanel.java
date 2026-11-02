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
    private static final int TAB_TOP = 29;
    private static final int TAB_HEIGHT = 21;
    private static final int TAB_LEFT = 7;
    private static final int MODEL_TAB_WIDTH = 57;
    private static final int TAB_GAP = 3;
    private static final int CONTENT_TOP = TAB_TOP + TAB_HEIGHT + 3;

    // Three density presets are selected by the REAL available height.
    // In the common 1.12.2 GUI scale the compact preset fits without
    // scrolling. Larger windows use more breathing room between fields.
    private static final int[] COMPACT_OFFSETS = {
            10, 28, 46, 75, 93, 111, 140, 158, 176
    };
    private static final int[] MEDIUM_OFFSETS = {
            15, 34, 53, 89, 108, 127, 163, 182, 201
    };
    private static final int[] ROOMY_OFFSETS = {
            17, 39, 61, 105, 127, 149, 193, 215, 237
    };
    private static final int[] COMPACT_LABELS = {0, 65, 130};
    private static final int[] MEDIUM_LABELS = {0, 74, 148};
    private static final int[] ROOMY_LABELS = {0, 88, 176};

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

        // Tabs share ONE row, rather than stacking two accordion headers.
        // The selected view owns the full vertical space below the row.
        drawModeTab(mc, "MODEL", TAB_LEFT, MODEL_TAB_WIDTH,
                modelSectionOpen, mouseX, mouseY);
        int globalTabX = TAB_LEFT + MODEL_TAB_WIDTH + TAB_GAP;
        drawModeTab(mc, "GLOBAL TRANSFORM", globalTabX,
                WIDTH - TAB_LEFT - globalTabX, globalSectionOpen,
                mouseX, mouseY);

        if (modelSectionOpen)
        {
            int contentY = y + CONTENT_TOP + 3;
            drawButton(mc,
                    selected.hasModel() ? selected.getModelName() : "SELECT MODEL",
                    x + 9, contentY, WIDTH - 18, 22, mouseX, mouseY);
            drawButton(mc, "REMOVE ATTACHMENT",
                    x + 9, contentY + 27, WIDTH - 18, 20, mouseX, mouseY);
            boolean chameleon=isChameleonWeldCandidate(selected);
            drawButton(mc,
                    chameleon
                            ? "WELD GEOMETRY: " + (selected.isWeldEnabled() ? "ON" : "OFF")
                            : "WELD: CHAMELEON ONLY",
                    x + 9, contentY + 52, WIDTH - 18, 20, mouseX, mouseY);
        }
        else if (globalSectionOpen)
        {
            AnimationTransform transform = selected.getGlobalTransform();
            int viewportTop = getGlobalViewportTop();
            int viewportBottom = getGlobalViewportBottom();
            int maxScroll = getGlobalMaxScroll();
            globalScroll = Math.max(0, Math.min(maxScroll, globalScroll));
            drawRect(mc, x + 6, viewportTop, x + WIDTH - 6, viewportBottom,
                    EditorVisualStyle.themedSurface(0.05F));
            syncGlobalControls(transform, viewportTop);
            drawGlobalControls(mc, viewportTop, viewportBottom);
            writeGlobalTransform(transform);
        }
    }

    private void drawModeTab(Minecraft mc, String title, int relativeX,
            int width, boolean active, int mouseX, int mouseY)
    {
        int left = x + relativeX;
        int top = y + TAB_TOP;
        boolean hover = mouseX >= left && mouseX < left + width
                && mouseY >= top && mouseY < top + TAB_HEIGHT;
        drawRect(mc, left, top, left + width, top + TAB_HEIGHT,
                EditorVisualStyle.themedSurface(active ? 0.27F :
                        (hover ? 0.20F : 0.12F)));
        drawRect(mc, left, top + TAB_HEIGHT - 2, left + width,
                top + TAB_HEIGHT, active ? EditorThemeManager.get().getAccent()
                        : EditorVisualStyle.themedSurface(0.23F));
        int availableWidth = width - 6;
        String display = mc.fontRenderer.trimStringToWidth(title, availableWidth);
        int textWidth = mc.fontRenderer.getStringWidth(display);
        mc.fontRenderer.drawString(display,
                left + (width - textWidth) / 2, top + 6,
                active ? EditorThemeManager.get().getAccentBright()
                        : (hover ? 0xFFE8E8E8 : 0xFFB9BCC2));
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
        AnimationValueControl[] controls = {
                globalPositionX, globalPositionY, globalPositionZ,
                globalRotationX, globalRotationY, globalRotationZ,
                globalScaleX, globalScaleY, globalScaleZ
        };
        int[] offsets = getGlobalControlOffsets();
        for (int i = 0; i < controls.length; i++)
        {
            controls[i].setPosition(cx, contentY + offsets[i]);
            controls[i].setWidth(WIDTH - 18);
        }
    }

    private int[] getGlobalControlOffsets()
    {
        int available = getGlobalViewportHeight();
        return available >= 267 ? ROOMY_OFFSETS :
                available >= 229 ? MEDIUM_OFFSETS : COMPACT_OFFSETS;
    }

    private int[] getGlobalLabelOffsets()
    {
        int available = getGlobalViewportHeight();
        return available >= 267 ? ROOMY_LABELS :
                available >= 229 ? MEDIUM_LABELS : COMPACT_LABELS;
    }

    private void drawGlobalControls(Minecraft mc, int viewportTop, int viewportBottom)
    {
        int contentY = viewportTop + 3 - globalScroll;
        int[] labels = getGlobalLabelOffsets();
        drawGlobalLabel(mc, "POSITION", contentY + labels[0], viewportTop, viewportBottom);
        globalPositionX.draw(mc); globalPositionY.draw(mc); globalPositionZ.draw(mc);
        drawGlobalLabel(mc, "ROTATION", contentY + labels[1], viewportTop, viewportBottom);
        globalRotationX.draw(mc); globalRotationY.draw(mc); globalRotationZ.draw(mc);
        drawGlobalLabel(mc, "SCALE", contentY + labels[2], viewportTop, viewportBottom);
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

        int tabY = y + TAB_TOP;
        int firstTabLeft = x + TAB_LEFT;
        int secondTabLeft = firstTabLeft + MODEL_TAB_WIDTH + TAB_GAP;
        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT)
        {
            boolean clickModel = mouseX >= firstTabLeft &&
                    mouseX < firstTabLeft + MODEL_TAB_WIDTH;
            boolean clickGlobal = mouseX >= secondTabLeft &&
                    mouseX < x + WIDTH - TAB_LEFT;
            if (clickModel || clickGlobal)
            {
                boolean switching = (clickModel && !modelSectionOpen) ||
                        (clickGlobal && !globalSectionOpen);
                if (switching)
                {
                    // Preserve the typed transform before hiding its tab.
                    if (activeGlobalControl != null)
                    {
                        activeGlobalControl.finishEditing();
                        writeGlobalTransform(selected.getGlobalTransform());
                        activeGlobalControl = null;
                    }
                    modelSectionOpen = clickModel;
                    globalSectionOpen = clickGlobal;
                    globalScroll = 0;
                }
                return true;
            }
        }

        if (modelSectionOpen)
        {
            int contentY = y + CONTENT_TOP + 3;
            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9 &&
                    mouseY >= contentY && mouseY < contentY + 22)
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
            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9 &&
                    mouseY >= contentY + 27 && mouseY < contentY + 47)
            {
                controller.removeSelectedAttachment();
                return true;
            }
            if (mouseX >= x + 9 && mouseX < x + WIDTH - 9 &&
                    mouseY >= contentY + 52 && mouseY < contentY + 72)
            {
                if (isChameleonWeldCandidate(selected))
                    selected.setWeldEnabled(!selected.isWeldEnabled());
                return true;
            }
        }

        return false;
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

    private int getGlobalViewportTop()
    {
        return y + CONTENT_TOP;
    }

    private int getGlobalViewportBottom()
    {
        return y + panelHeight - 5;
    }

    private int getGlobalViewportHeight()
    {
        return Math.max(0, getGlobalViewportBottom() - getGlobalViewportTop());
    }

    private int getGlobalMaxScroll()
    {
        int[] offsets = getGlobalControlOffsets();
        // Last field has 16px height; include a 3px bottom inset.
        int contentHeight = 3 + offsets[offsets.length - 1] + 16 + 3;
        return Math.max(0, contentHeight - getGlobalViewportHeight());
    }

    private boolean globalContains(int mouseX, int mouseY)
    {
        if (!globalSectionOpen) return false;

        int viewportTop = getGlobalViewportTop();
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
        int maxScroll = getGlobalMaxScroll();

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

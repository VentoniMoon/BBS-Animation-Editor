package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

public class BodyPartsTimelineController
{
    private static final int HEADER_HEIGHT = 24;
    private static final int TRACK_HEIGHT = 24;
    private static final int BONE_TRACK_HEIGHT = 22;
    private static final int TIMELINE_START_X = 185;

    private final BodyPartsEditorController controller;
    private final EditorTimeline timeline;

    public BodyPartsTimelineController(
            BodyPartsEditorController controller)
    {
        this.controller = controller;
        this.timeline = controller.getTimeline();
    }

    public int getTimelineHeight()
    {
        return 205;
    }

    public void draw(
            Minecraft mc,
            int width,
            int height,
            int sceneLength)
    {
        int top = height - getTimelineHeight();

        Gui.drawRect(
                0,
                top,
                width,
                height,
                0xFF111111
        );

        Gui.drawRect(
                0,
                top,
                width,
                top + 1,
                EditorThemeManager.get().getAccent()
        );

        drawHeader(
                mc,
                top,
                width
        );

        BodyPartModelData selected =
                controller.getSelectedModel();

        if (selected == null)
        {
            drawModelTracks(
                    mc,
                    top + HEADER_HEIGHT,
                    width,
                    height,
                    sceneLength
            );
        }
        else
        {
            drawBoneTracks(
                    mc,
                    top + HEADER_HEIGHT,
                    width,
                    height,
                    sceneLength,
                    selected
            );
        }

        drawPlayhead(
                top,
                height,
                width
        );
    }

    private void drawHeader(
            Minecraft mc,
            int top,
            int width)
    {
        BodyPartModelData selected =
                controller.getSelectedModel();

        String title =
                selected == null
                        ? "BODY PART MODELS"
                        : "MODEL: " + selected.getModelName();

        mc.fontRenderer.drawString(
                title,
                10,
                top + 7,
                0xFFE2E5E7
        );

        if (selected != null)
        {
            mc.fontRenderer.drawString(
                    "← MODELS",
                    width - 74,
                    top + 7,
                    EditorThemeManager.get().getAccentBright()
            );
        }
    }

    private void drawModelTracks(
            Minecraft mc,
            int top,
            int width,
            int height,
            int sceneLength)
    {
        int y = top;

        Gui.drawRect(
                0,
                y,
                width,
                y + TRACK_HEIGHT,
                0xFF181818
        );

        mc.fontRenderer.drawString(
                "MODELS",
                10,
                y + 7,
                0xFF9AA1A6
        );

        int row = 1;

        for (BodyPartModelData model : controller.getModels())
        {
            int rowY = y + row * TRACK_HEIGHT;

            if (rowY + TRACK_HEIGHT > height)
            {
                break;
            }

            boolean selectedModel =
                    model == controller.getSelectedModel();

            Gui.drawRect(
                    0,
                    rowY,
                    width,
                    rowY + TRACK_HEIGHT,
                    row % 2 == 0
                            ? 0xFF151515
                            : 0xFF191919
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.getAttachmentBoneName(),
                            110
                    ),
                    8,
                    rowY + 7,
                    0xFF9AA1A6
            );

            int startX =
                    timeline.getFrameX(
                            model.getStartFrame(),
                            TIMELINE_START_X
                    );

            int endX =
                    timeline.getFrameX(
                            model.getEndFrame(),
                            TIMELINE_START_X
                    );

            Gui.drawRect(
                    Math.max(TIMELINE_START_X, startX),
                    rowY + 4,
                    Math.min(width - 4, endX),
                    rowY + TRACK_HEIGHT - 4,
                    model == selected
                            ? EditorThemeManager.get().getAccentBright()
                            : EditorThemeManager.get().getAccent()
            );

            String label =
                    model.getModelName();

            mc.fontRenderer.drawString(
                    trim(mc, label, Math.max(20, endX - startX - 8)),
                    Math.max(TIMELINE_START_X + 4, startX + 4),
                    rowY + 7,
                    0xFF111111
            );

            row++;
        }

        drawRuler(
                mc,
                top - 1,
                width,
                sceneLength
        );
    }

    private void drawBoneTracks(
            Minecraft mc,
            int top,
            int width,
            int height,
            int sceneLength,
            BodyPartModelData model)
    {
        int y = top;

        Gui.drawRect(
                0,
                y,
                width,
                y + TRACK_HEIGHT,
                0xFF181818
        );

        mc.fontRenderer.drawString(
                "BONES",
                10,
                y + 7,
                0xFF9AA1A6
        );

        int row = 1;

        for (AnimationBone bone : model.getBones())
        {
            int rowY = y + row * BONE_TRACK_HEIGHT;

            if (rowY + BONE_TRACK_HEIGHT > height)
            {
                break;
            }

            boolean selected =
                    bone == controller.getKeyframeController()
                            .getSelectedBone(
                                    model.getBones()
                            );

            mc.fontRenderer.drawString(
                    trim(mc, bone.getName(), 145),
                    10,
                    rowY + 6,
                    selected
                            ? EditorThemeManager.get().getAccentBright()
                            : 0xFF9AA1A6
            );

            for (AnimationKeyframe keyframe : bone.getKeyframes())
            {
                if (keyframe == null)
                {
                    continue;
                }

                int keyX =
                        timeline.getFrameX(
                                keyframe.getFrame(),
                                TIMELINE_START_X
                        );

                if (keyX < TIMELINE_START_X - 5 ||
                        keyX > width + 5)
                {
                    continue;
                }

                drawKey(
                        keyX,
                        rowY + 11,
                        keyframe ==
                                controller.getKeyframeController()
                                        .getSelectedKeyframe()
                );
            }

            row++;
        }

        drawRuler(
                mc,
                top - 1,
                width,
                sceneLength
        );
    }

    private void drawRuler(
            Minecraft mc,
            int y,
            int width,
            int sceneLength)
    {
        int rulerY = y;

        for (int frame = 0; frame <= sceneLength; frame++)
        {
            int x =
                    timeline.getFrameX(
                            frame,
                            TIMELINE_START_X
                    );

            if (x < TIMELINE_START_X - 10 ||
                    x > width + 10)
            {
                continue;
            }

            int tickHeight =
                    frame % 5 == 0 ? 7 : 4;

            Gui.drawRect(
                    x,
                    rulerY,
                    x + 1,
                    rulerY + tickHeight,
                    0xFF444444
            );

            if (frame % 5 == 0)
            {
                mc.fontRenderer.drawString(
                        String.valueOf(frame),
                        x + 3,
                        rulerY + 7,
                        0xFF666D72
                );
            }
        }
    }

    private void drawPlayhead(
            int top,
            int height,
            int width)
    {
        int x =
                timeline.getFrameX(
                        timeline.getTick(),
                        TIMELINE_START_X
                );

        if (x < TIMELINE_START_X ||
                x > width)
        {
            return;
        }

        Gui.drawRect(
                x,
                top,
                x + 2,
                height,
                EditorThemeManager.get().getAccentBright()
        );
    }

    private void drawKey(
            int x,
            int y,
            boolean selected)
    {
        int color =
                selected
                        ? EditorThemeManager.get().getAccentBright()
                        : EditorThemeManager.get().getAccent();

        Gui.drawRect(x - 3, y - 3, x + 4, y + 4, color);
        Gui.drawRect(x - 1, y - 1, x + 2, y + 2, 0xFF111111);
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int width,
            int height,
            int sceneLength)
    {
        int top = height - getTimelineHeight();

        if (mouseY < top || mouseY > height)
        {
            return false;
        }

        if (controller.getSelectedModel() != null &&
                mouseY < top + HEADER_HEIGHT)
        {
            if (mouseX > width - 90)
            {
                controller.backToModelTracks();
                return true;
            }
        }

        int relativeY =
                mouseY - top - HEADER_HEIGHT;

        if (relativeY < 0)
        {
            return false;
        }

        int row =
                relativeY / TRACK_HEIGHT;

        if (controller.getSelectedModel() == null)
        {
            if (row <= 0)
            {
                return true;
            }

            int index = row - 1;

            if (index >= 0 &&
                    index < controller.getModels().size())
            {
                BodyPartModelData model =
                        controller.getModels().get(index);

                int startX =
                        timeline.getFrameX(
                                model.getStartFrame(),
                                TIMELINE_START_X
                        );

                int endX =
                        timeline.getFrameX(
                                model.getEndFrame(),
                                TIMELINE_START_X
                        );

                if (mouseX >= startX &&
                        mouseX <= endX)
                {
                    controller.selectModel(model);
                    return true;
                }
            }

            return true;
        }

        int boneIndex =
                relativeY / BONE_TRACK_HEIGHT - 1;

        if (boneIndex < 0 ||
                boneIndex >= controller.getSelectedModel().getBones().size())
        {
            return true;
        }

        controller.getKeyframeController().setSelectedBoneIndex(
                boneIndex,
                controller.getSelectedModel().getBones()
        );

        AnimationBone bone =
                controller.getSelectedModel().getBone(
                        boneIndex
                );

        AnimationKeyframe key =
                controller.getKeyframeController().findKeyframeAt(
                        bone,
                        mouseX,
                        6
                );

        if (key != null)
        {
            controller.getKeyframeController().setSelectedKeyframe(key);
            timeline.setTick(key.getFrame());
            return true;
        }

        int frame =
                timeline.getFrameFromMouseX(
                        mouseX,
                        TIMELINE_START_X
                );

        frame = Math.max(
                0,
                Math.min(
                        sceneLength,
                        frame
                )
        );

        timeline.setTick(frame);

        if (mouseButtonDoubleClick(mouseX, mouseY))
        {
            controller.getKeyframeController().createKeyframe(
                    bone,
                    frame
            );

            controller.getKeyframeController().setSelectedKeyframe(
                    controller.getKeyframeController().findKeyframe(
                            bone,
                            frame
                    )
            );
        }

        return true;
    }

    private long lastClickTime;
    private int lastClickX;
    private int lastClickY;

    private boolean mouseButtonDoubleClick(int x, int y)
    {
        long now = System.currentTimeMillis();

        boolean same =
                Math.abs(this.lastClickX - x) <= 3 &&
                Math.abs(this.lastClickY - y) <= 3 &&
                now - this.lastClickTime <= 250;

        this.lastClickX = x;
        this.lastClickY = y;
        this.lastClickTime = now;

        return same;
    }

    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            int width,
            int height,
            int sceneLength)
    {
        return false;
    }

    public void mouseReleased()
    {
        this.controller.getKeyframeController()
                .stopKeyframeDragging();
    }

    private String trim(Minecraft mc, String text, int maxWidth)
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
}

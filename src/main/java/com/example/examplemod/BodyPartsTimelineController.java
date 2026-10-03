package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Local Timeline used by the Body Parts editor.
 *
 * Level 1: attached models are displayed as long duration bars.
 * Level 2: the selected model exposes its bones and ordinary keyframes.
 *
 * The visual language intentionally follows the existing Character/Pose
 * timelines: same header height, track height, ruler, grid, text colors,
 * theme-derived backgrounds and accent/playhead colors.
 */
public class BodyPartsTimelineController
{
    private static final int HEADER_HEIGHT = 35;
    private static final int TRACK_HEIGHT = 20;
    private static final int TIMELINE_START_X =
            AnimationEditorScreen.LEFT_PANEL_WIDTH;

    private final BodyPartsEditorController controller;
    private final EditorTimeline timeline;

    private long lastClickTime;
    private int lastClickX;
    private int lastClickY;

    public BodyPartsTimelineController(
            BodyPartsEditorController controller)
    {
        this.controller = controller;
        this.timeline = controller.getTimeline();
    }

    public int getTimelineHeight()
    {
        return 180;
    }

    private int themeColor(float strength)
    {
        int accent = EditorThemeManager.get().getAccent();

        int r = (accent >> 16) & 0xFF;
        int g = (accent >> 8) & 0xFF;
        int b = accent & 0xFF;

        r = Math.max(0, Math.min(255, Math.round(r * strength)));
        g = Math.max(0, Math.min(255, Math.round(g * strength)));
        b = Math.max(0, Math.min(255, Math.round(b * strength)));

        return 0xFF000000 |
                (r << 16) |
                (g << 8) |
                b;
    }

    private int getBackground()
    {
        return themeColor(0.07F);
    }

    private int getHeader()
    {
        return themeColor(0.12F);
    }

    private int getTrack()
    {
        return themeColor(0.12F);
    }

    private int getTrackAlt()
    {
        return themeColor(0.09F);
    }

    private int getTrackSelected()
    {
        return themeColor(0.22F);
    }

    private int getGrid()
    {
        return themeColor(0.10F);
    }

    private int getGridMajor()
    {
        return themeColor(0.20F);
    }

    private int getGridSecond()
    {
        return themeColor(0.15F);
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
                getBackground()
        );

        Gui.drawRect(
                0,
                top,
                width,
                top + 1,
                EditorThemeManager.get().getAccent()
        );

        BodyPartModelData selected =
                controller.getSelectedModel();

        if (selected == null)
        {
            drawModelTracks(
                    mc,
                    top,
                    width,
                    sceneLength
            );
        }
        else
        {
            drawBoneTracks(
                    mc,
                    top,
                    width,
                    sceneLength,
                    selected
            );
        }

        drawCurrentFrameLine(
                top,
                width,
                height
        );
    }

    private void drawHeader(
            Minecraft mc,
            int top,
            int width,
            String title,
            boolean modelView)
    {
        Gui.drawRect(
                0,
                top,
                width,
                top + HEADER_HEIGHT,
                getHeader()
        );

        Gui.drawRect(
                0,
                top + HEADER_HEIGHT - 1,
                width,
                top + HEADER_HEIGHT,
                themeColor(0.05F)
        );

        mc.fontRenderer.drawString(
                title,
                10,
                top + 7,
                0xFFE2E5E7
        );

        if (modelView)
        {
            mc.fontRenderer.drawString(
                    "← MODELS",
                    width - 76,
                    top + 7,
                    EditorThemeManager.get().getAccentBright()
            );
        }
    }

    private void drawModelTracks(
            Minecraft mc,
            int top,
            int width,
            int sceneLength)
    {
        drawHeader(
                mc,
                top,
                width,
                "BODY PART MODELS",
                false
        );

        drawRulerAndGrid(
                mc,
                top,
                width,
                sceneLength
        );

        int y = top + HEADER_HEIGHT;

        Gui.drawRect(
                0,
                y,
                TIMELINE_START_X,
                y + TRACK_HEIGHT,
                getTrack()
        );

        mc.fontRenderer.drawString(
                "MODELS",
                10,
                y + 6,
                0xFF9DA4A9
        );

        int row = 1;

        for (BodyPartModelData model : controller.getModels())
        {
            int rowY =
                    y + row * TRACK_HEIGHT;

            if (rowY + TRACK_HEIGHT > top + getTimelineHeight())
            {
                break;
            }

            int trackColor =
                    row % 2 == 0
                            ? getTrackAlt()
                            : getTrack();

            Gui.drawRect(
                    0,
                    rowY,
                    width,
                    rowY + TRACK_HEIGHT,
                    trackColor
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.getAttachmentBoneName(),
                            TIMELINE_START_X - 14
                    ),
                    10,
                    rowY + 6,
                    0xFF9DA4A9
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

            int left =
                    Math.max(
                            TIMELINE_START_X,
                            Math.min(width - 2, startX)
                    );

            int right =
                    Math.max(
                            left + 2,
                            Math.min(width - 2, endX)
                    );

            boolean selected =
                    model == controller.getSelectedModel();

            Gui.drawRect(
                    left,
                    rowY + 4,
                    right,
                    rowY + TRACK_HEIGHT - 4,
                    selected
                            ? EditorThemeManager.get().getAccentBright()
                            : EditorThemeManager.get().getAccent()
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.getModelName(),
                            Math.max(20, right - left - 8)
                    ),
                    left + 4,
                    rowY + 6,
                    0xFF101010
            );

            row++;
        }
    }

    private void drawBoneTracks(
            Minecraft mc,
            int top,
            int width,
            int sceneLength,
            BodyPartModelData model)
    {
        drawHeader(
                mc,
                top,
                width,
                "MODEL: " + model.getModelName(),
                true
        );

        drawRulerAndGrid(
                mc,
                top,
                width,
                sceneLength
        );

        int y = top + HEADER_HEIGHT;
        int row = 0;

        for (AnimationBone bone : model.getBones())
        {
            if (bone == null)
            {
                continue;
            }

            int rowY =
                    y + row * TRACK_HEIGHT;

            if (rowY + TRACK_HEIGHT > top + getTimelineHeight())
            {
                break;
            }

            int trackColor =
                    row % 2 == 0
                            ? getTrack()
                            : getTrackAlt();

            AnimationBone selectedBone =
                    controller.getKeyframeController()
                            .getSelectedBone(
                                    model.getBones()
                            );

            if (bone == selectedBone)
            {
                trackColor = getTrackSelected();
            }

            Gui.drawRect(
                    0,
                    rowY,
                    width,
                    rowY + TRACK_HEIGHT,
                    trackColor
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            bone.getName(),
                            TIMELINE_START_X - 14
                    ),
                    10,
                    rowY + 6,
                    bone == selectedBone
                            ? EditorThemeManager.get().getAccentBright()
                            : 0xFF9DA4A9
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
                        rowY + TRACK_HEIGHT / 2,
                        keyframe ==
                                controller.getKeyframeController()
                                        .getSelectedKeyframe()
                );
            }

            row++;
        }
    }

    private void drawRulerAndGrid(
            Minecraft mc,
            int top,
            int width,
            int sceneLength)
    {
        int rulerY = top + 24;

        Gui.drawRect(
                TIMELINE_START_X,
                rulerY,
                width,
                top + HEADER_HEIGHT,
                getGrid()
        );

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

            boolean major = frame % 5 == 0;
            boolean second = frame % 2 == 0;

            int gridColor =
                    major
                            ? getGridMajor()
                            : second
                                    ? getGridSecond()
                                    : getGrid();

            Gui.drawRect(
                    x,
                    top + HEADER_HEIGHT,
                    x + 1,
                    top + getTimelineHeight(),
                    gridColor
            );

            int tickHeight =
                    major ? 7 : 4;

            Gui.drawRect(
                    x,
                    rulerY,
                    x + 1,
                    rulerY + tickHeight,
                    major
                            ? getGridMajor()
                            : getGridSecond()
            );

            if (major)
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

    private void drawCurrentFrameLine(
            int top,
            int width,
            int height)
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
                x + 1,
                height,
                EditorThemeManager.get().getAccent()
        );

        Gui.drawRect(
                x,
                top,
                x + 2,
                top + 5,
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

        Gui.drawRect(
                x - 3,
                y - 3,
                x + 4,
                y + 4,
                color
        );

        Gui.drawRect(
                x - 1,
                y - 1,
                x + 2,
                y + 2,
                0xFF101010
        );
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
            if (mouseX > width - 95)
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

        BodyPartModelData selected =
                controller.getSelectedModel();

        if (selected == null)
        {
            int row =
                    relativeY / TRACK_HEIGHT;

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
                relativeY / TRACK_HEIGHT;

        if (boneIndex < 0 ||
                boneIndex >= selected.getBones().size())
        {
            return true;
        }

        controller.getKeyframeController()
                .setSelectedBoneIndex(
                        boneIndex,
                        selected.getBones()
                );

        AnimationBone bone =
                selected.getBone(boneIndex);

        AnimationKeyframe key =
                controller.getKeyframeController()
                        .findKeyframeAt(
                                bone,
                                mouseX,
                                6
                        );

        if (key != null)
        {
            controller.getKeyframeController()
                    .setSelectedKeyframe(key);

            timeline.setTick(
                    key.getFrame()
            );

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
            controller.getKeyframeController()
                    .createKeyframe(
                            bone,
                            frame
                    );

            controller.getKeyframeController()
                    .setSelectedKeyframe(
                            controller.getKeyframeController()
                                    .findKeyframe(
                                            bone,
                                            frame
                                    )
                    );
        }

        return true;
    }

    private boolean mouseButtonDoubleClick(
            int x,
            int y)
    {
        long now =
                System.currentTimeMillis();

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
                mc.fontRenderer.getStringWidth(
                        value + "..."
                ) > maxWidth)
        {
            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        return value + "...";
    }
}

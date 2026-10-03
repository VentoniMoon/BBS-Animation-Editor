package com.example.examplemod;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Body Parts uses the same visual and interaction language as the Pose
 * Timeline.  The only additional level is the model-duration view.
 */
public class BodyPartsTimelineController
{
    private static final int TRACK_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 35;
    private static final int FIRST_FRAME = 0;
    private static final int TIMELINE_START_X =
            AnimationEditorScreen.LEFT_PANEL_WIDTH;

    private static final long DOUBLE_CLICK_DELAY = 300L;
    private static final int DOUBLE_CLICK_DISTANCE = 5;

    private final BodyPartsEditorController controller;
    private final EditorTimeline timeline;

    private int boneScroll;
    private long lastClickTime;
    private int lastClickX = -1;
    private int lastClickY = -1;
    private int lastClickBone = -1;

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

    public EditorTimeline getTimeline()
    {
        return this.timeline;
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

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int background()
    {
        return themeColor(0.07F);
    }

    private int header()
    {
        return themeColor(0.12F);
    }

    private int headerLight()
    {
        return themeColor(0.17F);
    }

    private int ruler()
    {
        return themeColor(0.09F);
    }

    private int track()
    {
        return themeColor(0.12F);
    }

    private int trackSelected()
    {
        return themeColor(0.22F);
    }

    private int border()
    {
        return themeColor(0.05F);
    }

    private int grid()
    {
        return themeColor(0.10F);
    }

    private int gridSecond()
    {
        return themeColor(0.15F);
    }

    private int gridMajor()
    {
        return themeColor(0.20F);
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
                background()
        );

        Gui.drawRect(
                0,
                top,
                width,
                top + 1,
                EditorThemeManager.get().getAccent()
        );

        BodyPartModelData model =
                controller.getSelectedModel();

        if (model == null)
        {
            drawModelTracks(
                    mc,
                    width,
                    height,
                    top,
                    sceneLength
            );
        }
        else
        {
            drawModelBoneTimeline(
                    mc,
                    width,
                    height,
                    top,
                    sceneLength,
                    model
            );
        }

        drawPlayhead(
                mc,
                width,
                height,
                top
        );
    }

    private void drawHeader(
            Minecraft mc,
            int width,
            int top,
            String title,
            boolean modelView)
    {
        Gui.drawRect(
                0,
                top + 1,
                width,
                top + HEADER_HEIGHT,
                header()
        );

        Gui.drawRect(
                0,
                top + HEADER_HEIGHT - 1,
                width,
                top + HEADER_HEIGHT,
                border()
        );

        Gui.drawRect(
                8,
                top + 10,
                10,
                top + 22,
                EditorThemeManager.get().getAccent()
        );

        mc.fontRenderer.drawString(
                title,
                16,
                top + 11,
                0xFFE0E3E5
        );

        if (modelView)
        {
            mc.fontRenderer.drawString(
                    "← MODELS",
                    width - 76,
                    top + 11,
                    EditorThemeManager.get().getAccentBright()
            );
        }

        String zoom =
                "ZOOM " +
                Math.round(timeline.getZoom() * 100.0F) +
                "%";

        int zoomWidth =
                mc.fontRenderer.getStringWidth(zoom);

        mc.fontRenderer.drawString(
                zoom,
                width - zoomWidth - 12,
                top + 11,
                0xFF9DA4A9
        );
    }

    private void drawModelTracks(
            Minecraft mc,
            int width,
            int height,
            int top,
            int sceneLength)
    {
        drawHeader(
                mc,
                width,
                top,
                "BODY PART MODELS",
                false
        );

        drawRuler(
                mc,
                width,
                top,
                sceneLength
        );

        int tracksTop = top + HEADER_HEIGHT;

        Gui.drawRect(
                0,
                tracksTop,
                TIMELINE_START_X,
                tracksTop + TRACK_HEIGHT,
                headerLight()
        );

        mc.fontRenderer.drawString(
                "MODEL",
                10,
                tracksTop + 6,
                0xFF9DA4A9
        );

        int row = 0;

        for (BodyPartModelData model : controller.getModels())
        {
            int y = tracksTop + row * TRACK_HEIGHT;

            if (y + TRACK_HEIGHT > height)
            {
                break;
            }

            drawTrackBackground(
                    width,
                    y,
                    false
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.getAttachmentBoneName(),
                            TIMELINE_START_X - 14
                    ),
                    10,
                    y + 6,
                    0xFF9DA4A9
            );

            int left =
                    timeline.getFrameX(
                            model.getStartFrame(),
                            TIMELINE_START_X
                    );

            int right =
                    timeline.getFrameX(
                            model.getEndFrame(),
                            TIMELINE_START_X
                    );

            left = Math.max(TIMELINE_START_X, left);
            right = Math.max(left + 4, Math.min(width, right));

            Gui.drawRect(
                    left,
                    y + 4,
                    right,
                    y + TRACK_HEIGHT - 4,
                    EditorThemeManager.get().getAccent()
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.getModelName(),
                            Math.max(20, right - left - 8)
                    ),
                    left + 4,
                    y + 6,
                    0xFF101010
            );

            row++;
        }
    }

    private void drawModelBoneTimeline(
            Minecraft mc,
            int width,
            int height,
            int top,
            int sceneLength,
            BodyPartModelData model)
    {
        drawHeader(
                mc,
                width,
                top,
                "MODEL: " + model.getModelName(),
                true
        );

        drawRuler(
                mc,
                width,
                top,
                getMaximumFrame(sceneLength, model)
        );

        int tracksTop = top + HEADER_HEIGHT;

        int visible =
                Math.max(
                        0,
                        (height - tracksTop) / TRACK_HEIGHT
                );

        int maxScroll =
                Math.max(
                        0,
                        model.getBones().size() - visible
                );

        boneScroll =
                Math.max(
                        0,
                        Math.min(
                                boneScroll,
                                maxScroll
                        )
                );

        for (int visibleIndex = 0;
                visibleIndex < visible;
                visibleIndex++)
        {
            int boneIndex =
                    boneScroll + visibleIndex;

            if (boneIndex >= model.getBones().size())
            {
                break;
            }

            AnimationBone bone =
                    model.getBones().get(boneIndex);

            if (bone == null)
            {
                continue;
            }

            int y =
                    tracksTop +
                    visibleIndex * TRACK_HEIGHT;

            AnimationBone selectedBone =
                    controller.getKeyframeController()
                            .getSelectedBone(model.getBones());

            drawTrackBackground(
                    width,
                    y,
                    bone == selectedBone
            );

            if (bone == selectedBone)
            {
                Gui.drawRect(
                        0,
                        y,
                        3,
                        y + TRACK_HEIGHT,
                        EditorThemeManager.get().getAccent()
                );

                Gui.drawRect(
                        3,
                        y,
                        TIMELINE_START_X - 1,
                        y + TRACK_HEIGHT,
                        0xFF242D31
                );
            }

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            bone.getName(),
                            TIMELINE_START_X - 14
                    ),
                    10,
                    y + 6,
                    bone == selectedBone
                            ? EditorThemeManager.get().getAccentBright()
                            : 0xFF9DA4A9
            );

            for (AnimationKeyframe keyframe :
                    bone.getKeyframes())
            {
                if (keyframe == null)
                {
                    continue;
                }

                int x =
                        timeline.getFrameX(
                                keyframe.getFrame(),
                                TIMELINE_START_X
                        );

                if (x < TIMELINE_START_X - 8 ||
                        x > width + 8)
                {
                    continue;
                }

                drawKeyframe(
                        x,
                        y + TRACK_HEIGHT / 2,
                        keyframe ==
                                controller.getKeyframeController()
                                        .getSelectedKeyframe()
                );
            }
        }
    }

    private void drawTrackBackground(
            int width,
            int y,
            boolean selected)
    {
        Gui.drawRect(
                0,
                y,
                width,
                y + TRACK_HEIGHT,
                selected
                        ? trackSelected()
                        : track()
        );

        Gui.drawRect(
                0,
                y + TRACK_HEIGHT - 1,
                width,
                y + TRACK_HEIGHT,
                border()
        );
    }

    private void drawRuler(
            Minecraft mc,
            int width,
            int top,
            int maximumFrame)
    {
        int rulerTop = top + 20;
        int rulerBottom = top + HEADER_HEIGHT;

        Gui.drawRect(
                TIMELINE_START_X,
                rulerTop,
                width,
                rulerBottom,
                ruler()
        );

        Gui.drawRect(
                0,
                rulerTop,
                TIMELINE_START_X,
                rulerBottom,
                headerLight()
        );

        Gui.drawRect(
                TIMELINE_START_X - 1,
                rulerTop,
                TIMELINE_START_X,
                rulerBottom,
                EditorThemeManager.get().getAccent()
        );

        if (maximumFrame < EditorTimeline.TICKS_PER_SECOND)
        {
            maximumFrame = EditorTimeline.TICKS_PER_SECOND;
        }

        float pixelsPerFrame =
                timeline.getPixelsPerFrame();

        if (pixelsPerFrame <= 0.0F)
        {
            return;
        }

        int first =
                Math.max(
                        FIRST_FRAME,
                        (int)Math.floor(
                                timeline.getOffset() /
                                pixelsPerFrame
                        )
                );

        int last =
                Math.min(
                        maximumFrame,
                        (int)Math.ceil(
                                (
                                        timeline.getOffset() +
                                        width -
                                        TIMELINE_START_X
                                ) /
                                pixelsPerFrame
                        )
                );

        int minorStep;

        if (timeline.getZoom() >= 2.0F)
        {
            minorStep = 1;
        }
        else if (timeline.getZoom() >= 1.0F)
        {
            minorStep = 5;
        }
        else if (timeline.getZoom() >= 0.5F)
        {
            minorStep = 10;
        }
        else
        {
            minorStep = 20;
        }

        for (int frame = first;
                frame <= last;
                frame += minorStep)
        {
            int x =
                    timeline.getFrameX(
                            frame,
                            TIMELINE_START_X
                    );

            if (x < TIMELINE_START_X || x > width)
            {
                continue;
            }

            boolean second =
                    frame % EditorTimeline.TICKS_PER_SECOND == 0;

            boolean halfSecond =
                    frame % 10 == 0;

            int color =
                    second
                            ? gridMajor()
                            : halfSecond
                                    ? gridSecond()
                                    : grid();

            Gui.drawRect(
                    x,
                    rulerBottom,
                    x + 1,
                    top + getTimelineHeight(),
                    color
            );

            if (second)
            {
                Gui.drawRect(
                        x,
                        rulerTop,
                        x + 2,
                        rulerBottom,
                        EditorThemeManager.get().getAccent()
                );

                mc.fontRenderer.drawString(
                        (frame / EditorTimeline.TICKS_PER_SECOND) + "s",
                        x + 4,
                        rulerTop + 5,
                        0xFFE0E3E5
                );
            }
            else if (halfSecond)
            {
                Gui.drawRect(
                        x,
                        rulerTop + 5,
                        x + 1,
                        rulerBottom,
                        gridMajor()
                );

                mc.fontRenderer.drawString(
                        String.valueOf(frame),
                        x + 3,
                        rulerTop + 5,
                        0xFF666D72
                );
            }
            else
            {
                Gui.drawRect(
                        x,
                        rulerTop + 8,
                        x + 1,
                        rulerBottom,
                        grid()
                );
            }
        }

        Gui.drawRect(
                TIMELINE_START_X,
                rulerBottom - 1,
                width,
                rulerBottom,
                border()
        );
    }

    private void drawKeyframe(
            int x,
            int y,
            boolean selected)
    {
        int color =
                selected
                        ? EditorThemeManager.get().getAccent()
                        : EditorThemeManager.get().getAccent();

        Gui.drawRect(
                x,
                y - 6,
                x + 1,
                y + 7,
                color
        );

        Gui.drawRect(
                x - 1,
                y - 5,
                x + 2,
                y + 6,
                color
        );

        Gui.drawRect(
                x - 2,
                y - 4,
                x + 3,
                y + 5,
                color
        );

        Gui.drawRect(
                x - 3,
                y - 3,
                x + 4,
                y + 4,
                color
        );

        Gui.drawRect(
                x - 4,
                y - 2,
                x + 5,
                y + 3,
                color
        );

        if (!selected)
        {
            int inner = themeColor(0.10F);

            Gui.drawRect(x, y - 4, x + 1, y + 5, inner);
            Gui.drawRect(x - 1, y - 3, x + 2, y + 4, inner);
            Gui.drawRect(x - 2, y - 2, x + 3, y + 3, inner);
        }
        else
        {
            Gui.drawRect(
                    x - 1,
                    y - 2,
                    x + 2,
                    y + 3,
                    EditorThemeManager.get().getAccentBright()
            );
        }
    }

    private void drawPlayhead(
            Minecraft mc,
            int width,
            int height,
            int top)
    {
        int frame = timeline.getTick();

        int x =
                timeline.getFrameX(
                        frame,
                        TIMELINE_START_X
                );

        if (x < TIMELINE_START_X || x > width)
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
                x - 2,
                top + 18,
                x + 4,
                top + 23,
                EditorThemeManager.get().getAccentBright()
        );

        String text = String.valueOf(frame);

        int labelWidth =
                mc.fontRenderer.getStringWidth(text);

        int labelX =
                x - labelWidth / 2;

        labelX =
                Math.max(
                        TIMELINE_START_X + 2,
                        Math.min(
                                width - labelWidth - 2,
                                labelX
                        )
                );

        mc.fontRenderer.drawString(
                text,
                labelX,
                top + 24,
                EditorThemeManager.get().getAccentBright()
        );
    }

    private int getMaximumFrame(
            int sceneLength,
            BodyPartModelData model)
    {
        int maximum =
                Math.max(
                        FIRST_FRAME,
                        sceneLength - 1
                );

        if (model == null)
        {
            return maximum;
        }

        maximum =
                Math.max(
                        maximum,
                        model.getEndFrame()
                );

        for (AnimationBone bone : model.getBones())
        {
            if (bone == null)
            {
                continue;
            }

            for (AnimationKeyframe keyframe :
                    bone.getKeyframes())
            {
                if (keyframe != null)
                {
                    maximum =
                            Math.max(
                                    maximum,
                                    keyframe.getFrame()
                            );
                }
            }
        }

        return maximum;
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int height,
            int sceneLength)
    {
        int top = height - getTimelineHeight();

        if (mouseY < top || mouseY > height)
        {
            return false;
        }

        BodyPartModelData model =
                controller.getSelectedModel();

        if (model != null &&
                mouseY < top + HEADER_HEIGHT &&
                mouseX > width - 95)
        {
            controller.backToModelTracks();
            boneScroll = 0;
            return true;
        }

        int tracksTop = top + HEADER_HEIGHT;

        if (mouseY < tracksTop)
        {
            int frame =
                    timeline.getFrameFromMouseX(
                            mouseX,
                            TIMELINE_START_X
                    );

            timeline.setTick(
                    Math.max(
                            FIRST_FRAME,
                            Math.min(
                                    sceneLength,
                                    frame
                            )
                    )
            );

            return true;
        }

        if (model == null)
        {
            int row =
                    (mouseY - tracksTop) / TRACK_HEIGHT;

            if (row >= 0 &&
                    row < controller.getModels().size())
            {
                controller.selectModel(
                        controller.getModels().get(row)
                );
                boneScroll = 0;
                return true;
            }

            return true;
        }

        int visibleBoneIndex =
                (mouseY - tracksTop) / TRACK_HEIGHT;

        int boneIndex =
                boneScroll + visibleBoneIndex;

        if (boneIndex < 0 ||
                boneIndex >= model.getBones().size())
        {
            return true;
        }

        AnimationBone bone =
                model.getBones().get(boneIndex);

        controller.getKeyframeController()
                .setSelectedBoneIndex(
                        boneIndex,
                        model.getBones()
                );

        int frame =
                timeline.getFrameFromMouseX(
                        mouseX,
                        TIMELINE_START_X
                );

        int maximum =
                getMaximumFrame(
                        sceneLength,
                        model
                );

        frame =
                Math.max(
                        FIRST_FRAME,
                        Math.min(
                                frame,
                                maximum
                        )
                );

        if (mouseButton == 0)
        {
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

                controller.getKeyframeController()
                        .startKeyframeDragging(
                                bone,
                                key
                        );

                timeline.setTick(key.getFrame());
                resetClickState();
                return true;
            }

            long now = System.currentTimeMillis();

            boolean doubleClick =
                    lastClickBone == boneIndex &&
                    Math.abs(lastClickX - mouseX) <=
                            DOUBLE_CLICK_DISTANCE &&
                    Math.abs(lastClickY - mouseY) <=
                            DOUBLE_CLICK_DISTANCE &&
                    now - lastClickTime <=
                            DOUBLE_CLICK_DELAY;

            timeline.setTick(frame);

            if (doubleClick)
            {
                AnimationKeyframe createdKey =
                        controller.getKeyframeController()
                                .createKeyframe(
                                        bone,
                                        frame
                                );

                controller.getKeyframeController()
                        .setSelectedKeyframe(createdKey);

                resetClickState();
                return true;
            }

            lastClickTime = now;
            lastClickX = mouseX;
            lastClickY = mouseY;
            lastClickBone = boneIndex;

            controller.getKeyframeController()
                    .setSelectedKeyframe(null);

            return true;
        }

        if (mouseButton == 1)
        {
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
                        .requestDelete(
                                bone,
                                key
                        );
            }

            return true;
        }

        return true;
    }

    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            int width,
            int height,
            int sceneLength)
    {
        if (controller.getSelectedModel() == null)
        {
            return false;
        }

        if (controller.getKeyframeController()
                .isKeyframeDragging() &&
                clickedMouseButton == 0)
        {
            boolean moved =
                    controller.getKeyframeController()
                            .moveDraggingKeyframe(
                                    mouseX,
                                    FIRST_FRAME
                            );

            if (moved)
            {
                AnimationKeyframe key =
                        controller.getKeyframeController()
                                .getDraggingKeyframe();

                if (key != null)
                {
                    timeline.setTick(
                            key.getFrame()
                    );
                }
            }

            return true;
        }

        return false;
    }

    public void mouseReleased()
    {
        controller.getKeyframeController()
                .stopKeyframeDragging();
    }

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int wheel,
            int width,
            int height,
            int sceneLength)
    {
        if (wheel == 0)
        {
            return false;
        }

        int top = height - getTimelineHeight();

        if (mouseY < top || mouseY > height)
        {
            return false;
        }

        BodyPartModelData model =
                controller.getSelectedModel();

        int tracksTop = top + HEADER_HEIGHT;

        if (model != null &&
                mouseX < TIMELINE_START_X &&
                mouseY >= tracksTop)
        {
            int visible =
                    Math.max(
                            0,
                            (height - tracksTop) / TRACK_HEIGHT
                    );

            int maxScroll =
                    Math.max(
                            0,
                            model.getBones().size() - visible
                    );

            if (maxScroll > 0)
            {
                boneScroll =
                        wheel > 0
                                ? Math.max(0, boneScroll - 1)
                                : Math.min(maxScroll, boneScroll + 1);

                return true;
            }
        }

        boolean ctrl =
                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
                Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (ctrl)
        {
            int frame =
                    timeline.getFrameFromMouseX(
                            mouseX,
                            TIMELINE_START_X
                    );

            float oldZoom = timeline.getZoom();

            timeline.changeZoom(
                    wheel > 0 ? 0.25F : -0.25F
            );

            if (oldZoom != timeline.getZoom())
            {
                timeline.setOffset(
                        TIMELINE_START_X +
                        Math.round(
                                frame *
                                timeline.getPixelsPerFrame()
                        ) -
                        mouseX
                );

                timeline.clampOffset(
                        getMaximumFrame(
                                sceneLength,
                                model
                        ),
                        width - TIMELINE_START_X
                );
            }

            return true;
        }

        timeline.addOffset(
                wheel > 0 ? -60 : 60
        );

        timeline.clampOffset(
                getMaximumFrame(
                        sceneLength,
                        model
                ),
                width - TIMELINE_START_X
        );

        return true;
    }

    private void resetClickState()
    {
        lastClickTime = 0L;
        lastClickX = -1;
        lastClickY = -1;
        lastClickBone = -1;
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
}

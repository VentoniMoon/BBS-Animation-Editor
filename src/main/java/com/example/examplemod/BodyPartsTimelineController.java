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

    private BodyPartModelData draggingModel;
    private int dragMode;
    private int dragMouseX;
    private int dragMouseY;
    private boolean dragMoved;
    private int dragStartFrame;
    private int dragEndFrame;

    private static final int DRAG_NONE = 0;
    private static final int DRAG_MOVE = 1;
    private static final int DRAG_START = 2;
    private static final int DRAG_END = 3;

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

        /*
         * Body Parts has its own view timeline. Keep its length in
         * sync with the actual scene/model range, otherwise the
         * EditorTimeline clamps every playback frame back to 0.
         */
        this.timeline.setLength(
                Math.max(
                        1,
                        getMaximumFrame(sceneLength, model) + 1
                )
        );

        if (model == null)
        {
            drawActorTimeline(
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
                    "← BODY PARTS",
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

    private void drawActorTimeline(
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
                "BODY PARTS",
                false
        );

        drawRuler(
                mc,
                width,
                top,
                getMaximumFrame(sceneLength, null)
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
                "ACTOR BONES",
                10,
                tracksTop + 6,
                0xFF9DA4A9
        );

        int visible =
                Math.max(
                        0,
                        (height - tracksTop) / TRACK_HEIGHT
                );

        int maxScroll =
                Math.max(
                        0,
                        controller.getActorBones().size() - visible
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

            if (boneIndex >= controller.getActorBones().size())
            {
                break;
            }

            AnimationBone bone =
                    controller.getActorBones().get(boneIndex);

            if (bone == null)
            {
                continue;
            }

            int y =
                    tracksTop +
                    visibleIndex * TRACK_HEIGHT;

            boolean selected =
                    boneIndex ==
                    controller.getSelectedActorBone();

            drawTrackBackground(
                    width,
                    y,
                    selected
            );

            if (selected)
            {
                Gui.drawRect(
                        0,
                        y,
                        3,
                        y + TRACK_HEIGHT,
                        EditorThemeManager.get().getAccent()
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
                    selected
                            ? EditorThemeManager.get().getAccentBright()
                            : 0xFF9DA4A9
            );

            drawModelBarsForBone(
                    mc,
                    width,
                    y,
                    bone.getName()
            );
        }
    }

    private void drawModelBarsForBone(
            Minecraft mc,
            int width,
            int y,
            String boneName)
    {
        int stack = 0;

        for (BodyPartModelData model :
                controller.getModels())
        {
            if (model == null ||
                    !boneName.equals(
                            model.getAttachmentBoneName()))
            {
                continue;
            }

            int barY =
                    y + 4 + stack * 12;

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

            if (right < TIMELINE_START_X ||
                    left > width)
            {
                stack++;
                continue;
            }

            left =
                    Math.max(
                            TIMELINE_START_X,
                            left
                    );

            right =
                    Math.min(
                            width,
                            Math.max(left + 8, right)
                    );

            boolean selected =
                    model == controller.getSelectedAttachment();

            Gui.drawRect(
                    left,
                    barY,
                    right,
                    barY + 12,
                    selected
                            ? EditorThemeManager.get().getAccentBright()
                            : EditorThemeManager.get().getAccent()
            );

            Gui.drawRect(
                    left,
                    barY,
                    Math.min(right, left + 2),
                    barY + 12,
                    EditorThemeManager.get().getAccentBright()
            );

            Gui.drawRect(
                    Math.max(left, right - 2),
                    barY,
                    right,
                    barY + 12,
                    EditorThemeManager.get().getAccentBright()
            );

            mc.fontRenderer.drawString(
                    trim(
                            mc,
                            model.hasModel()
                                    ? model.getModelName()
                                    : "SELECT MODEL",
                            Math.max(16, right - left - 8)
                    ),
                    left + 4,
                    barY + 2,
                    model.hasModel()
                            ? 0xFF101010
                            : 0xFFE0E3E5
            );

            stack++;
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

        for (BodyPartModelData item :
                controller.getModels())
        {
            if (item != null)
            {
                maximum =
                        Math.max(
                                maximum,
                                item.getEndFrame()
                        );
            }
        }

        if (model != null)
        {
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
                mouseX > width - 120)
        {
            controller.backToModelTracks();
            boneScroll = 0;
            resetClickState();
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
                                    getMaximumFrame(sceneLength, model),
                                    frame
                            )
                    )
            );

            return true;
        }

        if (model == null)
        {
            return mouseClickedActorTimeline(
                    mouseX,
                    mouseY,
                    mouseButton,
                    tracksTop,
                    sceneLength
            );
        }

        return mouseClickedModelTimeline(
                mouseX,
                mouseY,
                mouseButton,
                tracksTop,
                sceneLength,
                model
        );
    }

    private boolean mouseClickedActorTimeline(
            int mouseX,
            int mouseY,
            int mouseButton,
            int tracksTop,
            int sceneLength)
    {
        int visibleBone =
                (mouseY - tracksTop) / TRACK_HEIGHT;

        int boneIndex =
                boneScroll + visibleBone;

        if (boneIndex < 0 ||
                boneIndex >= controller.getActorBones().size())
        {
            return true;
        }

        AnimationBone bone =
                controller.getActorBones().get(boneIndex);

        if (bone == null)
        {
            return true;
        }

        if (mouseButton != 0)
        {
            return true;
        }

        int frame =
                Math.max(
                        FIRST_FRAME,
                        Math.min(
                                getMaximumFrame(
                                        sceneLength,
                                        null
                                ),
                                timeline.getFrameFromMouseX(
                                        mouseX,
                                        TIMELINE_START_X
                                )
                        )
                );

        /*
         * BODY PARTS Level 1 interaction:
         *
         *   double-click empty space -> create attachment block
         *   single-click block        -> select block
         *   double-click block        -> open block's Level 2 timeline
         *
         * Dragging a selected block remains available for moving/resizing.
         */
        BodyPartModelData hit =
                findAttachmentAt(
                        bone,
                        boneIndex,
                        mouseX,
                        mouseY,
                        tracksTop
                );

        long now = System.currentTimeMillis();

        if (hit != null)
        {
            boolean doubleClick =
                    lastClickBone == boneIndex &&
                    Math.abs(lastClickX - mouseX) <=
                            DOUBLE_CLICK_DISTANCE &&
                    Math.abs(lastClickY - mouseY) <=
                            DOUBLE_CLICK_DISTANCE &&
                    now - lastClickTime <=
                            DOUBLE_CLICK_DELAY &&
                    lastClickedAttachment == hit;

            controller.setSelectedActorBone(boneIndex);
            controller.selectAttachment(hit);
            timeline.setTick(frame);

            if (doubleClick)
            {
                resetClickState();

                if (hit.hasModel())
                {
                    controller.selectModel(hit);
                    boneScroll = 0;
                }

                return true;
            }

            startAttachmentDrag(
                    hit,
                    mouseX,
                    mouseY,
                    timeline.getFrameX(
                            hit.getStartFrame(),
                            TIMELINE_START_X
                    ),
                    timeline.getFrameX(
                            hit.getEndFrame(),
                            TIMELINE_START_X
                    )
            );

            lastClickTime = now;
            lastClickX = mouseX;
            lastClickY = mouseY;
            lastClickBone = boneIndex;
            lastClickedAttachment = hit;

            return true;
        }

        /*
         * Empty actor-bone track. A double-click creates the
         * rectangle at the clicked frame. A single click only
         * moves the playhead/selects the actor bone.
         */
        boolean doubleClick =
                lastClickBone == boneIndex &&
                Math.abs(lastClickX - mouseX) <=
                        DOUBLE_CLICK_DISTANCE &&
                Math.abs(lastClickY - mouseY) <=
                        DOUBLE_CLICK_DISTANCE &&
                now - lastClickTime <=
                        DOUBLE_CLICK_DELAY &&
                lastClickedAttachment == null;

        controller.setSelectedActorBone(boneIndex);
        timeline.setTick(frame);

        if (doubleClick)
        {
            BodyPartModelData created =
                    controller.createAttachment(
                            frame,
                            sceneLength
                    );

            resetClickState();

            if (created != null)
            {
                controller.selectAttachment(created);
            }

            return true;
        }

        lastClickTime = now;
        lastClickX = mouseX;
        lastClickY = mouseY;
        lastClickBone = boneIndex;
        lastClickedAttachment = null;

        return true;
    }

    private BodyPartModelData lastClickedAttachment;

    private BodyPartModelData findAttachmentAt(
            AnimationBone bone,
            int boneIndex,
            int mouseX,
            int mouseY,
            int tracksTop)
    {
        if (bone == null)
        {
            return null;
        }

        int visibleBone =
                boneIndex - boneScroll;

        int barTop =
                tracksTop +
                visibleBone * TRACK_HEIGHT +
                4;

        int stack = 0;

        for (BodyPartModelData item :
                controller.getModels())
        {
            if (item == null ||
                    !bone.getName().equals(
                            item.getAttachmentBoneName()))
            {
                continue;
            }

            int left =
                    timeline.getFrameX(
                            item.getStartFrame(),
                            TIMELINE_START_X
                    );

            int right =
                    timeline.getFrameX(
                            item.getEndFrame(),
                            TIMELINE_START_X
                    );

            int currentTop =
                    barTop +
                    stack * 12;

            if (mouseX >= left &&
                    mouseX <= right &&
                    mouseY >= currentTop &&
                    mouseY <= currentTop + 12)
            {
                return item;
            }

            stack++;
        }

        return null;
    }

    private boolean mouseClickedModelTimeline(
            int mouseX,
            int mouseY,
            int mouseButton,
            int tracksTop,
            int sceneLength,
            BodyPartModelData model)
    {
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

        if (bone == null)
        {
            return true;
        }

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

        frame =
                Math.max(
                        FIRST_FRAME,
                        Math.min(
                                getMaximumFrame(sceneLength, model),
                                frame
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
        if (clickedMouseButton != 0)
        {
            return false;
        }

        if (controller.getSelectedModel() == null)
        {
            return dragAttachment(
                    mouseX,
                    mouseY,
                    height
            );
        }

        if (controller.getKeyframeController()
                .isKeyframeDragging())
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
                    timeline.setTick(key.getFrame());
                }
            }

            return true;
        }

        return false;
    }

    private boolean dragAttachment(
            int mouseX,
            int mouseY,
            int height)
    {
        if (draggingModel == null)
        {
            return false;
        }

        int deltaFrame =
                timeline.getFrameFromMouseX(
                        mouseX,
                        TIMELINE_START_X
                ) -
                timeline.getFrameFromMouseX(
                        dragMouseX,
                        TIMELINE_START_X
                );

        if (deltaFrame != 0)
        {
            dragMoved = true;
        }

        if (dragMode == DRAG_START)
        {
            int newStart =
                    Math.max(
                            0,
                            Math.min(
                                    dragEndFrame - 1,
                                    dragStartFrame + deltaFrame
                            )
                    );

            draggingModel.setStartFrame(newStart);
            timeline.setTick(newStart);
            return true;
        }

        if (dragMode == DRAG_END)
        {
            int newEnd =
                    Math.max(
                            dragStartFrame + 1,
                            dragEndFrame + deltaFrame
                    );

            draggingModel.setEndFrame(newEnd);
            timeline.setTick(newEnd);
            return true;
        }

        if (dragMode == DRAG_MOVE)
        {
            int start =
                    Math.max(
                            0,
                            dragStartFrame + deltaFrame
                    );

            int end =
                    start +
                    (dragEndFrame - dragStartFrame);

            draggingModel.setStartFrame(start);
            draggingModel.setEndFrame(end);

            int top =
                    height -
                    getTimelineHeight() +
                    HEADER_HEIGHT;

            int visibleBone =
                    (mouseY - top) / TRACK_HEIGHT;

            if (mouseY != dragMouseY)
            {
                dragMoved = true;
            }

            int newBone =
                    boneScroll + visibleBone;

            if (newBone >= 0 &&
                    newBone < controller.getActorBones().size())
            {
                AnimationBone target =
                        controller.getActorBones().get(newBone);

                if (target != null)
                {
                    draggingModel.setAttachmentBoneName(
                            target.getName()
                    );

                    controller.setSelectedActorBone(newBone);
                }
            }

            timeline.setTick(start);
            return true;
        }

        return false;
    }

    private void startAttachmentDrag(
            BodyPartModelData model,
            int mouseX,
            int mouseY,
            int left,
            int right)
    {
        draggingModel = model;
        dragMouseX = mouseX;
        dragMouseY = mouseY;
        dragMoved = false;
        dragStartFrame = model.getStartFrame();
        dragEndFrame = model.getEndFrame();

        if (mouseX <= left + 5)
        {
            dragMode = DRAG_START;
        }
        else if (mouseX >= right - 5)
        {
            dragMode = DRAG_END;
        }
        else
        {
            dragMode = DRAG_MOVE;
        }
    }

    public void mouseReleased()
    {
        /*
         * A single click selects the attachment.
         * Opening Level 2 is intentionally handled only by the
         * second click in mouseClickedActorTimeline().
         *
         * This keeps click and drag semantics separate:
         *   click  -> select
         *   drag   -> move/resize
         *   double -> open Level 2
         */
        if (draggingModel != null &&
                !dragMoved)
        {
            controller.selectAttachment(draggingModel);
        }

        draggingModel = null;
        dragMode = DRAG_NONE;
        dragMoved = false;

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

        if (mouseX < TIMELINE_START_X &&
                mouseY >= tracksTop)
        {
            int visible =
                    Math.max(
                            0,
                            (height - tracksTop) / TRACK_HEIGHT
                    );

            int count =
                    model == null
                            ? controller.getActorBones().size()
                            : model.getBones().size();

            int maxScroll =
                    Math.max(
                            0,
                            count - visible
                    );

            if (maxScroll > 0)
            {
                boneScroll =
                        wheel > 0
                                ? Math.max(0, boneScroll - 1)
                                : Math.min(
                                        maxScroll,
                                        boneScroll + 1
                                );

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
                        getMaximumFrame(sceneLength, model),
                        width - TIMELINE_START_X
                );
            }

            return true;
        }

        /*
         * Plain wheel scrolling is deliberately kept conservative.
         * It must never move the timeline past its visible content.
         * Ctrl+wheel remains the zoom control above.
         */
        int maximumFrame =
                getMaximumFrame(
                        sceneLength,
                        model
                );

        timeline.addOffset(
                wheel > 0 ? -40 : 40
        );

        timeline.clampOffset(
                maximumFrame,
                Math.max(
                        1,
                        width - TIMELINE_START_X
                )
        );

        return true;
    }


    private String trim(
            Minecraft mc,
            String text,
            int width)
    {
        if (text == null)
        {
            return "";
        }

        if (width <= 0 || mc == null || mc.fontRenderer == null)
        {
            return "";
        }

        if (mc.fontRenderer.getStringWidth(text) <= width)
        {
            return text;
        }

        return mc.fontRenderer.trimStringToWidth(text, width);
    }

    private void resetClickState()
    {
        lastClickTime = 0L;
        lastClickX = -100000;
        lastClickY = -100000;
        lastClickBone = -1;
        lastClickedAttachment = null;
    }

}

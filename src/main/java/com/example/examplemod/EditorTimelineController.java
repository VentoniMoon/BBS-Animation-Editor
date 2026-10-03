package com.example.examplemod;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

import java.util.List;

/**
 * Управляет Timeline редактора.
 *
 * Отвечает за:
 *
 * - отображение Timeline;
 * - ruler;
 * - tracks костей;
 * - keyframes;
 * - выбор костей;
 * - выбор keyframe;
 * - создание keyframe;
 * - удаление keyframe;
 * - перемещение keyframe;
 * - прокрутку костей;
 * - zoom;
 * - горизонтальную прокрутку.
 *
 * Сам EditorTimeline остаётся моделью Timeline.
 */
public class EditorTimelineController
{
    private static final int FIRST_FRAME = 0;
    private static final int TRACK_HEIGHT = 20;

    private static final long DOUBLE_CLICK_DELAY = 300L;
    private static final int DOUBLE_CLICK_DISTANCE = 5;

    private static final int COLOR_TEXT = 0xFFE0E3E5;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9DA4A9;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;

    /*
     * =========================================================
     * VISUAL STYLE
     * =========================================================
     *
     * Dark colors are derived from the active editor theme.
     * This keeps the Timeline visually tied to Ocean,
     * Emerald, Ruby and Amethyst without changing geometry.
     */

    private static int themeColor(float strength)
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

    private static int themeBrightColor(float strength)
    {
        int accent = EditorThemeManager.get().getAccentBright();

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

    private static int getTimelineBackgroundColor()
    {
        return themeColor(0.07F);
    }

    private static int getTimelineHeaderColor()
    {
        return themeColor(0.12F);
    }

    private static int getTimelineHeaderLightColor()
    {
        return themeColor(0.17F);
    }

    private static int getTimelineRulerColor()
    {
        return themeColor(0.09F);
    }

    private static int getTimelineRulerTopColor()
    {
        return themeColor(0.20F);
    }

    private static int getTimelineTrackColor()
    {
        return themeColor(0.12F);
    }

    private static int getTimelineTrackAltColor()
    {
        return themeColor(0.09F);
    }

    private static int getTimelineTrackSelectedColor()
    {
        return themeColor(0.22F);
    }

    private static int getTimelineTrackBorderColor()
    {
        return themeColor(0.05F);
    }

    private static int getTimelineGridColor()
    {
        return themeColor(0.10F);
    }

    private static int getTimelineGridMajorColor()
    {
        return themeColor(0.20F);
    }

    private static int getTimelineGridSecondColor()
    {
        return themeColor(0.15F);
    }

    private static int getTimelineKeyframeColor()
    {
        return EditorThemeManager.get().getAccent();
    }

    private static int getTimelineKeyframeInnerColor()
    {
        return themeColor(0.10F);
    }

    /*
     * =========================================================
     * THEME
     * =========================================================
     */

    private static int getAccentColor()
    {
        return EditorThemeManager
                .get()
                .getAccent();
    }

    private static int getAccentBrightColor()
    {
        return EditorThemeManager
                .get()
                .getAccentBright();
    }

    /*
     * Playhead remains a semantic red color.
     *
     * It does NOT change with the editor theme.
     */

    private static int getPlayheadColor()
    {
        return EditorThemeManager.get().getAccent();
    }

    private static int getPlayheadHeadColor()
    {
        return EditorThemeManager.get().getAccentBright();
    }

    private final EditorTimeline timeline;

    private final EditorKeyframeController keyframeController;

    private final EditorPlaybackController playbackController;

    private final EditorActorPreviewController actorPreviewController;

    private int boneScroll = 0;

    private long lastTimelineClickTime = 0L;

    private int lastTimelineClickX = -1;

    private int lastTimelineClickY = -1;

    private int lastTimelineClickBone = -1;


    public EditorTimelineController(
            EditorTimeline timeline,
            EditorKeyframeController keyframeController,
            EditorPlaybackController playbackController,
            EditorActorPreviewController actorPreviewController)
    {
        this.timeline = timeline;

        this.keyframeController =
                keyframeController;

        this.playbackController =
                playbackController;

        this.actorPreviewController =
                actorPreviewController;
    }


    public EditorTimeline getTimeline()
    {
        return this.timeline;
    }


    public int getBoneScroll()
    {
        return this.boneScroll;
    }


    public void reset()
    {
        this.boneScroll = 0;

        resetClickState();
    }


    public void resetClickState()
    {
        this.lastTimelineClickTime = 0L;

        this.lastTimelineClickX = -1;

        this.lastTimelineClickY = -1;

        this.lastTimelineClickBone = -1;
    }


    public int getTimelineHeight()
    {
        return 180;
    }


    public int getMaximumFrame(
            int sceneLength)
    {
        int maximum =
                FIRST_FRAME;

        int timelineLast =
                this.timeline.getLastTick();

        if (timelineLast > maximum)
        {
            maximum =
                    timelineLast;
        }

        if (sceneLength > 0)
        {
            maximum =
                    Math.max(
                            maximum,
                            sceneLength - 1
                    );
        }

        List<AnimationBone> bones =
                this.actorPreviewController
                        .getBones();

        for (AnimationBone bone : bones)
        {
            if (bone == null)
            {
                continue;
            }

            for (AnimationKeyframe keyframe :
                    bone.getKeyframes())
            {
                if (keyframe == null)
                {
                    continue;
                }

                maximum =
                        Math.max(
                                maximum,
                                keyframe.getFrame()
                        );
            }
        }

        return maximum;
    }


    /*
     * =========================================================
     * DRAW TIMELINE
     * =========================================================
     */

    public void draw(
            int width,
            int height,
            int leftPanelWidth,
            int sceneLength,
            BlockbusterSceneActorData actor)
    {
        Minecraft mc =
                Minecraft.getMinecraft();

        int timelineTop =
                height -
                        getTimelineHeight();

        /*
         * =====================================================
         * COMPLETE BACKGROUND
         * =====================================================
         */

        Gui.drawRect(
                0,
                timelineTop,
                width,
                height,
                getTimelineBackgroundColor()
        );

        /*
         * Theme accent line separates Timeline
         * from the rest of the editor.
         */

        Gui.drawRect(
                0,
                timelineTop,
                width,
                timelineTop + 1,
                getAccentColor()
        );

        /*
         * =====================================================
         * HEADER
         * =====================================================
         */

        int headerHeight =
                35;

        Gui.drawRect(
                0,
                timelineTop + 1,
                width,
                timelineTop + headerHeight,
                getTimelineHeaderColor()
        );

        Gui.drawRect(
                0,
                timelineTop + headerHeight - 1,
                width,
                timelineTop + headerHeight,
                getTimelineTrackBorderColor()
        );

        /*
         * Left title accent.
         */

        Gui.drawRect(
                8,
                timelineTop + 10,
                10,
                timelineTop + 22,
                getAccentColor()
        );

        mc.fontRenderer.drawString(
                "TIMELINE",
                16,
                timelineTop + 11,
                COLOR_TEXT
        );

        /*
         * Scene information.
         */

        if (sceneLength > 0)
        {
            String sceneText =
                    "Scene  "
                            +
                            sceneLength
                            +
                            " frames";

            mc.fontRenderer.drawString(
                    sceneText,
                    88,
                    timelineTop + 11,
                    COLOR_TEXT_SECONDARY
            );
        }

        /*
         * Actor information.
         */

        if (actor != null)
        {
            String actorName =
                    actor.getId();

            if (
                    actorName == null ||
                            actorName.length() == 0
            )
            {
                actorName =
                        "Unknown";
            }

            if (actorName.length() > 22)
            {
                actorName =
                        actorName.substring(
                                0,
                                19
                        )
                                +
                                "...";
            }

            mc.fontRenderer.drawString(
                    "Actor  ",
                    205,
                    timelineTop + 11,
                    COLOR_TEXT_MUTED
            );

            mc.fontRenderer.drawString(
                    actorName,
                    238,
                    timelineTop + 11,
                    getAccentColor()
            );
        }

        /*
         * Playback status.
         */

        boolean playing =
                this.playbackController
                        .isPlaying();

        String playbackText =
                playing
                        ? "PLAYING"
                        : "PAUSED";

        int playbackColor =
                playing
                        ? 0xFF66DD88
                        : COLOR_TEXT_MUTED;

        int playbackWidth =
                mc.fontRenderer
                        .getStringWidth(
                                playbackText
                        );

        mc.fontRenderer.drawString(
                playbackText,
                width -
                        playbackWidth -
                        80,
                timelineTop + 11,
                playbackColor
        );

        /*
         * Zoom.
         */

        String zoomText =
                "ZOOM "
                        +
                        Math.round(
                                this.timeline.getZoom()
                                        *
                                        100.0F
                        )
                        +
                        "%";

        int zoomWidth =
                mc.fontRenderer
                        .getStringWidth(
                                zoomText
                        );

        mc.fontRenderer.drawString(
                zoomText,
                width -
                        zoomWidth -
                        12,
                timelineTop + 11,
                COLOR_TEXT_SECONDARY
        );

        /*
         * =====================================================
         * RULER
         * =====================================================
         */

        drawTimelineRuler(
                width,
                timelineTop,
                leftPanelWidth,
                sceneLength
        );

        /*
         * =====================================================
         * TRACKS
         * =====================================================
         */

        int tracksTop =
                timelineTop + 35;

        int visibleBoneCount =
                Math.max(
                        0,
                        (
                                height -
                                        25 -
                                        tracksTop
                        ) /
                                TRACK_HEIGHT
                );

        int maxBoneScroll =
                Math.max(
                        0,
                        this.actorPreviewController
                                .getBones()
                                .size() -
                                visibleBoneCount
                );

        this.boneScroll =
                Math.max(
                        0,
                        Math.min(
                                this.boneScroll,
                                maxBoneScroll
                        )
                );

        /*
         * Track background behind all bones.
         */

        Gui.drawRect(
                0,
                tracksTop,
                width,
                height,
                getTimelineBackgroundColor()
        );

        for (
                int visibleIndex = 0;
                visibleIndex < visibleBoneCount;
                visibleIndex++
        )
        {
            int boneIndex =
                    this.boneScroll +
                            visibleIndex;

            if (
                    boneIndex >=
                            this.actorPreviewController
                                    .getBones()
                                    .size()
            )
            {
                break;
            }

            AnimationBone bone =
                    this.actorPreviewController
                            .getBones()
                            .get(boneIndex);

            int trackY =
                    tracksTop +
                            visibleIndex *
                                    TRACK_HEIGHT;

            drawBoneTrack(
                    width,
                    bone,
                    trackY,
                    boneIndex ==
                            this.keyframeController
                                    .getSelectedBoneIndex(),
                    leftPanelWidth
            );
        }

        /*
         * =====================================================
         * PLAYHEAD
         * =====================================================
         */

        drawCurrentFrameLine(
                width,
                height,
                timelineTop,
                leftPanelWidth
        );

        /*
         * Bottom status.
         */

        mc.fontRenderer.drawString(
                playbackText,
                10,
                height - 25,
                playbackColor
        );
    }


    /*
     * =========================================================
     * RULER
     * =========================================================
     */

    private void drawTimelineRuler(
            int width,
            int timelineTop,
            int timelineStartX,
            int sceneLength)
    {
        Minecraft mc =
                Minecraft.getMinecraft();

        int maximumFrame =
                getMaximumFrame(
                        sceneLength
                );

        if (
                maximumFrame <
                        EditorTimeline.TICKS_PER_SECOND
        )
        {
            maximumFrame =
                    EditorTimeline.TICKS_PER_SECOND;
        }

        float pixelsPerFrame =
                this.timeline
                        .getPixelsPerFrame();

        if (pixelsPerFrame <= 0.0F)
        {
            return;
        }

        int firstVisibleFrame =
                Math.max(
                        FIRST_FRAME,
                        (int) Math.floor(
                                this.timeline.getOffset()
                                        /
                                        pixelsPerFrame
                        )
                );

        int visibleWidth =
                width -
                        timelineStartX;

        int lastVisibleFrame =
                Math.min(
                        maximumFrame,
                        (int) Math.ceil(
                                (
                                        this.timeline.getOffset()
                                                +
                                                visibleWidth
                                )
                                        /
                                        pixelsPerFrame
                        )
                );

        /*
         * Ruler body.
         */

        int rulerTop =
                timelineTop + 20;

        int rulerBottom =
                timelineTop + 35;

        Gui.drawRect(
                timelineStartX,
                rulerTop,
                width,
                rulerBottom,
                getTimelineRulerColor()
        );

        /*
         * Left track header area.
         */

        Gui.drawRect(
                0,
                rulerTop,
                timelineStartX,
                rulerBottom,
                getTimelineHeaderLightColor()
        );

        Gui.drawRect(
                timelineStartX - 1,
                rulerTop,
                timelineStartX,
                rulerBottom,
                getAccentColor()
        );

        /*
         * Determine ruler spacing.
         */

        int minorStep;

        if (this.timeline.getZoom() >= 2.0F)
        {
            minorStep = 1;
        }
        else if (this.timeline.getZoom() >= 1.0F)
        {
            minorStep = 5;
        }
        else if (this.timeline.getZoom() >= 0.5F)
        {
            minorStep = 10;
        }
        else
        {
            minorStep = 20;
        }

        /*
         * =====================================================
         * VERTICAL GRID
         * =====================================================
         */

        for (
                int frame = firstVisibleFrame;
                frame <= lastVisibleFrame;
                frame += minorStep
        )
        {
            int x =
                    this.timeline.getFrameX(
                            frame,
                            timelineStartX
                    );

            if (
                    x < timelineStartX ||
                            x > width
            )
            {
                continue;
            }

            boolean second =
                    frame %
                            EditorTimeline.TICKS_PER_SECOND
                            == 0;

            boolean halfSecond =
                    frame % 10 == 0;

            /*
             * Full-height grid line.
             */

            if (second)
            {
                Gui.drawRect(
                        x,
                        rulerBottom,
                        x + 1,
                        timelineTop +
                                getTimelineHeight(),
                        getTimelineGridMajorColor()
                );
            }
            else if (halfSecond)
            {
                Gui.drawRect(
                        x,
                        rulerBottom,
                        x + 1,
                        timelineTop +
                                getTimelineHeight(),
                        getTimelineGridSecondColor()
                );
            }
            else
            {
                Gui.drawRect(
                        x,
                        rulerBottom,
                        x + 1,
                        timelineTop +
                                getTimelineHeight(),
                        getTimelineGridColor()
                );
            }

            /*
             * =================================================
             * RULER MARKS
             * =================================================
             */

            if (second)
            {
                Gui.drawRect(
                        x,
                        rulerTop,
                        x + 2,
                        rulerBottom,
                        getAccentColor()
                );

                int seconds =
                        frame /
                                EditorTimeline.TICKS_PER_SECOND;

                String label =
                        seconds + "s";

                mc.fontRenderer.drawString(
                        label,
                        x + 4,
                        rulerTop + 5,
                        COLOR_TEXT
                );
            }
            else if (halfSecond)
            {
                Gui.drawRect(
                        x,
                        rulerTop + 5,
                        x + 1,
                        rulerBottom,
                        getTimelineGridMajorColor()
                );

                mc.fontRenderer.drawString(
                        String.valueOf(frame),
                        x + 3,
                        rulerTop + 5,
                        COLOR_TEXT_MUTED
                );
            }
            else
            {
                Gui.drawRect(
                        x,
                        rulerTop + 8,
                        x + 1,
                        rulerBottom,
                        getTimelineGridColor()
                );
            }
        }

        /*
         * Ruler bottom separator.
         */

        Gui.drawRect(
                timelineStartX,
                rulerBottom - 1,
                width,
                rulerBottom,
                getTimelineTrackBorderColor()
        );
    }


    /*
     * =========================================================
     * BONE TRACK
     * =========================================================
     */

    private void drawBoneTrack(
            int width,
            AnimationBone bone,
            int trackY,
            boolean selected,
            int timelineStartX)
    {
        Minecraft mc =
                Minecraft.getMinecraft();

        /*
         * Alternating track background.
         */

        int visibleTrackIndex =
                (
                        trackY -
                                (
                                        trackY /
                                                TRACK_HEIGHT
                                ) *
                                        TRACK_HEIGHT
                );

        int trackColor =
                selected
                        ? getTimelineTrackSelectedColor()
                        : getTimelineTrackColor();

        /*
         * Full track.
         */

        Gui.drawRect(
                0,
                trackY,
                width,
                trackY + TRACK_HEIGHT,
                trackColor
        );

        /*
         * Subtle alternating separator.
         */

        Gui.drawRect(
                0,
                trackY + TRACK_HEIGHT - 1,
                width,
                trackY + TRACK_HEIGHT,
                getTimelineTrackBorderColor()
        );

        /*
         * Selected bone indicator.
         */

        if (selected)
        {
            Gui.drawRect(
                    0,
                    trackY,
                    3,
                    trackY + TRACK_HEIGHT,
                    getAccentColor()
            );

            Gui.drawRect(
                    3,
                    trackY,
                    timelineStartX - 1,
                    trackY + TRACK_HEIGHT,
                    0xFF242D31
            );
        }

        /*
         * Bone name area.
         */

        String boneName =
                bone.getName();

        if (
                boneName == null ||
                        boneName.length() == 0
        )
        {
            boneName =
                    "Unnamed";
        }

        if (boneName.length() > 24)
        {
            boneName =
                    boneName.substring(
                            0,
                            21
                    )
                            +
                            "...";
        }

        mc.fontRenderer.drawString(
                boneName,
                selected
                        ? 13
                        : 10,
                trackY + 5,
                selected
                        ? COLOR_TEXT
                        : COLOR_TEXT_SECONDARY
        );

        /*
         * Divider between bone list and timeline.
         */

        Gui.drawRect(
                timelineStartX - 1,
                trackY,
                timelineStartX,
                trackY + TRACK_HEIGHT,
                selected
                        ? getAccentColor()
                        : getTimelineTrackBorderColor()
        );

        /*
         * Track center line.
         */

        Gui.drawRect(
                timelineStartX,
                trackY + 10,
                width,
                trackY + 11,
                0xFF292929
        );

        /*
         * =====================================================
         * KEYFRAMES
         * =====================================================
         */

        AnimationKeyframe selectedKeyframe =
                this.keyframeController
                        .getSelectedKeyframe();

        for (
                AnimationKeyframe keyframe :
                bone.getKeyframes()
        )
        {
            if (keyframe == null)
            {
                continue;
            }

            int frame =
                    keyframe.getFrame();

            int x =
                    this.timeline.getFrameX(
                            frame,
                            timelineStartX
                    );

            if (
                    x < timelineStartX - 8 ||
                            x > width + 8
            )
            {
                continue;
            }

            drawKeyframe(
                    x,
                    trackY + 10,
                    keyframe ==
                            selectedKeyframe
            );
        }
    }


    /*
     * =========================================================
     * CURRENT FRAME / PLAYHEAD
     * =========================================================
     */

    private void drawCurrentFrameLine(
            int width,
            int height,
            int timelineTop,
            int timelineStartX)
    {
        int currentFrame =
                this.playbackController
                        .getCurrentFrame();

        int currentX =
                this.timeline.getFrameX(
                        currentFrame,
                        timelineStartX
                );

        if (
                currentX < timelineStartX ||
                        currentX > width
        )
        {
            return;
        }

        /*
         * Dark shadow around playhead.
         */

        Gui.drawRect(
                currentX - 1,
                timelineTop + 20,
                currentX + 3,
                height,
                0x66300000
        );

        /*
         * Main playhead.
         */

        Gui.drawRect(
                currentX,
                timelineTop + 20,
                currentX + 2,
                height,
                getPlayheadColor()
        );

        /*
         * Playhead head.
         */

        Gui.drawRect(
                currentX - 3,
                timelineTop + 18,
                currentX + 5,
                timelineTop + 21,
                getPlayheadHeadColor()
        );

        Gui.drawRect(
                currentX - 2,
                timelineTop + 21,
                currentX + 4,
                timelineTop + 23,
                getPlayheadColor()
        );

        /*
         * Current frame label.
         */

        Minecraft mc =
                Minecraft.getMinecraft();

        String frameText =
                String.valueOf(
                        currentFrame
                );

        int labelWidth =
                mc.fontRenderer
                        .getStringWidth(
                                frameText
                        );

        int labelX =
                currentX -
                        labelWidth / 2;

        if (labelX < timelineStartX + 2)
        {
            labelX =
                    timelineStartX + 2;
        }

        if (
                labelX +
                        labelWidth >
                        width - 2
        )
        {
            labelX =
                    width -
                            labelWidth -
                            2;
        }

        mc.fontRenderer.drawString(
                frameText,
                labelX,
                timelineTop + 24,
                getPlayheadHeadColor()
        );
    }


    /*
     * =========================================================
     * KEYFRAME
     * =========================================================
     */

    private void drawKeyframe(
            int x,
            int y,
            boolean selected)
    {
        int outerColor =
                selected
                        ? getAccentColor()
                        : getTimelineKeyframeColor();

        /*
         * Diamond.
         *
         * Using four rectangles keeps this compatible
         * with the existing 1.12.2 Gui renderer.
         */

        Gui.drawRect(
                x,
                y - 6,
                x + 1,
                y + 7,
                outerColor
        );

        Gui.drawRect(
                x - 1,
                y - 5,
                x + 2,
                y + 6,
                outerColor
        );

        Gui.drawRect(
                x - 2,
                y - 4,
                x + 3,
                y + 5,
                outerColor
        );

        Gui.drawRect(
                x - 3,
                y - 3,
                x + 4,
                y + 4,
                outerColor
        );

        Gui.drawRect(
                x - 4,
                y - 2,
                x + 5,
                y + 3,
                outerColor
        );

        /*
         * Inner dark diamond.
         */

        if (!selected)
        {
            Gui.drawRect(
                    x,
                    y - 4,
                    x + 1,
                    y + 5,
                    getTimelineKeyframeInnerColor()
            );

            Gui.drawRect(
                    x - 1,
                    y - 3,
                    x + 2,
                    y + 4,
                    getTimelineKeyframeInnerColor()
            );

            Gui.drawRect(
                    x - 2,
                    y - 2,
                    x + 3,
                    y + 3,
                    getTimelineKeyframeInnerColor()
            );
        }
        else
        {
            /*
             * Selected keyframe gets a bright center.
             */

            Gui.drawRect(
                    x - 1,
                    y - 2,
                    x + 2,
                    y + 3,
                    getAccentBrightColor()
            );
        }
    }


    /*
     * =========================================================
     * MOUSE CLICK
     * =========================================================
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int height,
            int leftPanelWidth,
            int sceneLength)
    {
        int timelineTop =
                height -
                        getTimelineHeight();

        if (
                mouseY < timelineTop ||
                        mouseY > height
        )
        {
            return false;
        }

        int tracksTop =
                timelineTop + 35;

        int relativeY =
                mouseY -
                        tracksTop;

        if (relativeY < 0)
        {
            return false;
        }

        int visibleBoneIndex =
                relativeY /
                        TRACK_HEIGHT;

        int boneIndex =
                this.boneScroll +
                        visibleBoneIndex;

        if (
                boneIndex < 0 ||
                        boneIndex >=
                                this.actorPreviewController
                                        .getBones()
                                        .size()
        )
        {
            return false;
        }

        this.keyframeController
                .setSelectedBoneIndex(
                        boneIndex,
                        this.actorPreviewController
                                .getBones()
                );

        AnimationBone bone =
                this.actorPreviewController
                        .getBones()
                        .get(boneIndex);

        int frame =
                this.timeline
                        .getFrameFromMouseX(
                                mouseX,
                                leftPanelWidth
                        );

        int maximumFrame =
                getMaximumFrame(
                        sceneLength
                );

        frame =
                Math.max(
                        FIRST_FRAME,
                        Math.min(
                                frame,
                                maximumFrame
                        )
                );

        if (mouseButton == 0)
        {
            AnimationKeyframe clickedKeyframe =
                    this.keyframeController
                            .findKeyframeAt(
                                    bone,
                                    mouseX,
                                    6
                            );

            if (clickedKeyframe != null)
            {
                this.keyframeController
                        .setSelectedKeyframe(
                                clickedKeyframe
                        );

                this.keyframeController
                        .startKeyframeDragging(
                                bone,
                                clickedKeyframe
                        );

                this.playbackController
                        .setCurrentFrame(
                                clickedKeyframe
                                        .getFrame()
                        );

                resetClickState();

                return true;
            }

            long now =
                    System.currentTimeMillis();

            boolean sameClickArea =
                    this.lastTimelineClickBone ==
                            boneIndex
                            &&
                            Math.abs(
                                    this.lastTimelineClickX -
                                            mouseX
                            ) <=
                                    DOUBLE_CLICK_DISTANCE
                            &&
                            Math.abs(
                                    this.lastTimelineClickY -
                                            mouseY
                            ) <=
                                    DOUBLE_CLICK_DISTANCE;

            boolean doubleClick =
                    sameClickArea
                            &&
                            now -
                                    this.lastTimelineClickTime
                                    <=
                                    DOUBLE_CLICK_DELAY;

            this.playbackController
                    .setCurrentFrame(
                            frame
                    );

            if (doubleClick)
            {
                AnimationKeyframe keyframe =
                        this.keyframeController
                                .createKeyframe(
                                        bone,
                                        frame
                                );

                this.keyframeController
                        .setSelectedKeyframe(
                                keyframe
                        );

                resetClickState();

                return true;
            }

            this.lastTimelineClickTime =
                    now;

            this.lastTimelineClickX =
                    mouseX;

            this.lastTimelineClickY =
                    mouseY;

            this.lastTimelineClickBone =
                    boneIndex;

            this.keyframeController
                    .setSelectedKeyframe(
                            null
                    );

            return true;
        }

        if (mouseButton == 1)
        {
            AnimationKeyframe clickedKeyframe =
                    this.keyframeController
                            .findKeyframeAt(
                                    bone,
                                    mouseX,
                                    6
                            );

            if (clickedKeyframe != null)
            {
                this.keyframeController
                        .requestDelete(
                                bone,
                                clickedKeyframe
                        );

                return true;
            }
        }

        return true;
    }


    /*
     * =========================================================
     * DRAG
     * =========================================================
     */

    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton)
    {
        if (
                this.keyframeController
                        .isKeyframeDragging()
                        &&
                        clickedMouseButton == 0
        )
        {
            boolean moved =
                    this.keyframeController
                            .moveDraggingKeyframe(
                                    mouseX,
                                    FIRST_FRAME
                            );

            if (moved)
            {
                AnimationKeyframe keyframe =
                        this.keyframeController
                                .getDraggingKeyframe();

                if (keyframe != null)
                {
                    this.playbackController
                            .setCurrentFrame(
                                    keyframe.getFrame()
                            );
                }
            }

            return true;
        }

        return false;
    }


    public void mouseReleased()
    {
        this.keyframeController
                .stopKeyframeDragging();
    }


    /*
     * =========================================================
     * SCROLL
     * =========================================================
     */

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int wheel,
            int width,
            int height,
            int leftPanelWidth,
            int sceneLength)
    {
        if (wheel == 0)
        {
            return false;
        }

        int timelineTop =
                height -
                        getTimelineHeight();

        if (
                mouseY < timelineTop ||
                        mouseY > height
        )
        {
            return false;
        }

        int tracksTop =
                timelineTop + 35;

        if (
                mouseX >= 0 &&
                        mouseX < leftPanelWidth &&
                        mouseY >= tracksTop
        )
        {
            int visibleBoneCount =
                    Math.max(
                            0,
                            (
                                    height -
                                            25 -
                                            tracksTop
                            ) /
                                    TRACK_HEIGHT
                    );

            int maxBoneScroll =
                    Math.max(
                            0,
                            this.actorPreviewController
                                    .getBones()
                                    .size() -
                                    visibleBoneCount
                    );

            if (maxBoneScroll > 0)
            {
                if (wheel > 0)
                {
                    this.boneScroll =
                            Math.max(
                                    0,
                                    this.boneScroll - 1
                            );
                }
                else
                {
                    this.boneScroll =
                            Math.min(
                                    maxBoneScroll,
                                    this.boneScroll + 1
                            );
                }

                return true;
            }
        }

        boolean ctrlDown =
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                )
                        ||
                        Keyboard.isKeyDown(
                                Keyboard.KEY_RCONTROL
                        );

        if (ctrlDown)
        {
            int mouseFrame =
                    this.timeline
                            .getFrameFromMouseX(
                                    mouseX,
                                    leftPanelWidth
                            );

            float oldZoom =
                    this.timeline.getZoom();

            this.timeline.changeZoom(
                    wheel > 0
                            ? 0.25F
                            : -0.25F
            );

            if (
                    oldZoom !=
                            this.timeline.getZoom()
            )
            {
                this.timeline.setOffset(
                        leftPanelWidth
                                +
                                Math.round(
                                        mouseFrame
                                                *
                                                this.timeline
                                                        .getPixelsPerFrame()
                                )
                                -
                                mouseX
                );

                this.timeline.clampOffset(
                        getMaximumFrame(
                                sceneLength
                        ),
                        width -
                                leftPanelWidth
                );
            }

            return true;
        }

        int scrollAmount =
                60;

        if (wheel > 0)
        {
            this.timeline.addOffset(
                    -scrollAmount
            );
        }
        else
        {
            this.timeline.addOffset(
                    scrollAmount
            );
        }

        this.timeline.clampOffset(
                getMaximumFrame(
                        sceneLength
                ),
                width -
                        leftPanelWidth
        );

        return true;
    }
}
package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * UI-контроллер Character Timeline.
 *
 * Character Timeline использует тот же EditorTimeline,
 * что и основной Pose Timeline.
 *
 * ВАЖНО:
 *
 * - currentFrame является общим кадром редактора;
 * - Character Timeline не хранит собственного currentFrame;
 * - двойной ЛКМ по пустому месту трека создаёт CharacterKey;
 * - ЛКМ по ключу выбирает его и позволяет перетаскивать;
 * - ПКМ по ключу удаляет его;
 * - CharacterKey принадлежит Timeline выбранного Actor.
 */
public class CharacterTimelineEditorController
{
    private static final int FIRST_FRAME = 0;

    /*
     * ---------------------------------------------------------
     * TRACK LAYOUT
     * ---------------------------------------------------------
     */

    private static final int TRACK_HEIGHT = 20;

    private static final int HEADER_HEIGHT = 35;

    /*
     * Character Timeline keeps the same fixed height as the
     * other editor timelines. Body Part rows scroll inside
     * this fixed viewport when their count becomes large.
     */
    private static final int TIMELINE_HEIGHT = 180;

    private int trackScroll = 0;

    /*
     * Character Timeline имеет одну основную дорожку.
     * Остальные дорожки создаются динамически для Body Parts.
     */
    private static final String MAIN_TRACK_ID = "main";

    /*
     * ---------------------------------------------------------
     * DOUBLE CLICK
     * ---------------------------------------------------------
     */

    private static final long DOUBLE_CLICK_TIME = 350L;

    private static final int DOUBLE_CLICK_DISTANCE = 4;

    /*
     * ---------------------------------------------------------
     * COLORS
     * ---------------------------------------------------------
     *
     * Dark Timeline colors are derived from the active theme.
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

    private static int getPlayheadColor()
    {
        return EditorThemeManager.get().getAccent();
    }

    private static int getPlayheadHeadColor()
    {
        return EditorThemeManager.get().getAccentBright();
    }

    private static final int COLOR_TEXT_SECONDARY = 0xFF9DA4A9;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;

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
     * ---------------------------------------------------------
     * STATE
     * ---------------------------------------------------------
     */

    private BlockbusterSceneActorData selectedActor;

    private java.util.List<BodyPartModelData> bodyPartModels =
            new java.util.ArrayList<BodyPartModelData>();

    private BodyPartModelData selectedBodyPartModel;

    private int selectedTrack = -1;

    private CharacterKey selectedKey;

    private final EditorTimeline timeline;

    private final EditorPlaybackController playbackController;

    private boolean draggingKey = false;

    private int dragTrack = -1;

    private CharacterKey dragKey = null;

    private boolean draggingBodyPart = false;
    private int dragBodyPartFrame = -1;
    private BodyPartModelData dragBodyPartModel;

    /*
     * Состояние последнего ЛКМ.
     *
     * Нужно только для определения двойного клика
     * по пустому месту.
     */
    private long lastLeftClickTime = 0L;

    private int lastLeftClickTrack = -1;

    private int lastLeftClickFrame = -1;


    /*
     * =========================================================
     * CONSTRUCTORS
     * =========================================================
     */

    public CharacterTimelineEditorController()
    {
        this(
                new EditorTimeline(),
                null
        );
    }

    public CharacterTimelineEditorController(
            EditorTimeline timeline)
    {
        this(
                timeline,
                null
        );
    }

    public CharacterTimelineEditorController(
            EditorTimeline timeline,
            EditorPlaybackController playbackController)
    {
        if (timeline == null)
        {
            this.timeline = new EditorTimeline();
        }
        else
        {
            this.timeline = timeline;
        }

        this.playbackController = playbackController;
    }


    /*
     * =========================================================
     * TIMELINE
     * =========================================================
     */

    public EditorTimeline getTimeline()
    {
        return this.timeline;
    }

    public void setCurrentFrame(int frame)
    {
        frame = Math.max(
                FIRST_FRAME,
                frame
        );

        /*
         * Главный источник currentFrame —
         * EditorPlaybackController.
         */
        if (this.playbackController != null)
        {
            this.playbackController.setCurrentFrame(frame);
        }

        /*
         * Сохраняем тот же кадр и во внутреннем
         * EditorTimeline Character Timeline.
         */
        this.timeline.setTick(frame);
    }

    public void setTimeline(
            EditorTimeline timeline)
    {
        /*
         * Оставлено для совместимости API.
         *
         * EditorTimeline создаётся один раз,
         * потому что он является состоянием UI Timeline.
         */
    }


    /*
     * =========================================================
     * ACTOR
     * =========================================================
     */

    public void setSelectedActor(
            BlockbusterSceneActorData actor)
    {
        if (this.selectedActor != actor)
        {
            this.selectedTrack = -1;
            this.selectedKey = null;

            this.draggingKey = false;
            this.dragTrack = -1;
            this.dragKey = null;

            resetDoubleClickState();
            this.trackScroll = 0;
        }

        this.selectedActor = actor;

        if (this.selectedActor != null)
        {
            clampTrackScroll();

            CharacterTimelineController characterTimeline =
                    this.selectedActor.getCharacterTimeline();

            if (characterTimeline != null)
            {
                characterTimeline.ensureMainTrack();
            }
        }
    }

    public BlockbusterSceneActorData getSelectedActor()
    {
        return this.selectedActor;
    }

    public void setBodyPartModels(
            java.util.List<BodyPartModelData> models)
    {
        this.bodyPartModels = models == null
                ? new java.util.ArrayList<BodyPartModelData>()
                : models;

        if (this.selectedBodyPartModel != null &&
                !this.bodyPartModels.contains(this.selectedBodyPartModel))
        {
            this.selectedBodyPartModel = null;
        }
    }

    public BodyPartModelData getSelectedBodyPartModel()
    {
        return this.selectedBodyPartModel;
    }

    public CharacterTimelineController getCharacterTimeline()
    {
        if (this.selectedActor == null)
        {
            return null;
        }

        return this.selectedActor.getCharacterTimeline();
    }


    /*
     * =========================================================
     * DIMENSIONS
     * =========================================================
     */

    public int getTimelineHeight()
    {
        return TIMELINE_HEIGHT;
    }

    private int getVisibleTrackCount()
    {
        return Math.max(
                1,
                (TIMELINE_HEIGHT - HEADER_HEIGHT - 5) / TRACK_HEIGHT
        );
    }

    private int getMaxTrackScroll()
    {
        return Math.max(
                0,
                getTrackCount() - getVisibleTrackCount()
        );
    }

    private void clampTrackScroll()
    {
        this.trackScroll = Math.max(
                0,
                Math.min(
                        this.trackScroll,
                        getMaxTrackScroll()
                )
        );
    }

    public int getTrackHeight()
    {
        return TRACK_HEIGHT;
    }

    public int getHeaderHeight()
    {
        return HEADER_HEIGHT;
    }


    /*
     * =========================================================
     * TRACKS
     * =========================================================
     */

    public int getTrackCount()
    {
        CharacterTimelineController timeline =
                getCharacterTimeline();

        if (timeline == null)
        {
            return 0;
        }

        return timeline.getTrackCount();
    }

    public String getTrackLabel(int index)
    {
        CharacterTrack track = getTrack(index);

        if (track == null)
        {
            return "Track " + (index + 1);
        }

        String label = track.getLabel();

        if (label == null || label.length() == 0)
        {
            return track.isMainTrack()
                    ? "Main Character"
                    : "Character Track " + (index + 1);
        }

        return label;
    }


    public CharacterTrack getTrack(int index)
    {
        CharacterTimelineController timeline =
                getCharacterTimeline();

        if (timeline == null)
        {
            return null;
        }

        if (
                index < 0 ||
                        index >= timeline.getTrackCount()
        )
        {
            return null;
        }

        return timeline.getTrack(index);
    }


    /*
     * =========================================================
     * LENGTH
     * =========================================================
     */

    public int getMaximumFrame(
            int sceneLength)
    {
        int maximum =
                Math.max(
                        FIRST_FRAME,
                        sceneLength - 1
                );

        CharacterTimelineController timeline =
                getCharacterTimeline();

        if (timeline == null)
        {
            return maximum;
        }

        for (
                int i = 0;
                i < timeline.getTrackCount();
                i++
        )
        {
            CharacterTrack track =
                    timeline.getTrack(i);

            if (track == null)
            {
                continue;
            }

            for (
                    CharacterKey key :
                    track.getKeys()
            )
            {
                if (key == null)
                {
                    continue;
                }

                maximum =
                        Math.max(
                                maximum,
                                key.getFrame()
                        );
            }
        }

        return maximum;
    }


    /*
     * =========================================================
     * FRAME CONVERSION
     * =========================================================
     */

    public int getFrameFromMouseX(
            int mouseX,
            int timelineStartX)
    {
        return this.timeline.getFrameFromMouseX(
                mouseX,
                timelineStartX
        );
    }

    public int getFrameX(
            int frame,
            int timelineStartX)
    {
        return this.timeline.getFrameX(
                frame,
                timelineStartX
        );
    }


    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    public void draw(
            GuiScreen screen,
            int width,
            int height,
            int timelineStartX,
            int timelineTop,
            int currentFrame,
            int sceneLength)
    {
        if (screen == null)
        {
            return;
        }

        screen.drawRect(
                0,
                timelineTop,
                width,
                height,
                getTimelineBackgroundColor()
        );

        screen.drawRect(
                0,
                timelineTop,
                width,
                timelineTop + 1,
                getAccentColor()
        );

        int tracksTop =
                timelineTop +
                        HEADER_HEIGHT;

        drawTrackBackgrounds(
                screen,
                width,
                timelineTop,
                tracksTop
        );

        drawRulerAndGrid(
                screen,
                width,
                timelineTop,
                timelineStartX,
                sceneLength
        );

        drawTrackKeyframes(
                screen,
                width,
                tracksTop,
                timelineStartX
        );

        drawCurrentFrameLine(
                screen,
                width,
                timelineTop,
                currentFrame,
                timelineStartX
        );
    }


    /*
     * =========================================================
     * TRACK BACKGROUNDS
     * =========================================================
     */

    private void drawTrackBackgrounds(
            GuiScreen screen,
            int width,
            int timelineTop,
            int tracksTop)
    {
        int tracksBottom =
                timelineTop +
                        getTimelineHeight();

        screen.drawRect(
                0,
                tracksTop,
                width,
                tracksBottom,
                getTimelineBackgroundColor()
        );

        int trackCount =
                getTrackCount();

        int visibleTracks =
                getVisibleTrackCount();

        clampTrackScroll();

        for (
                int visibleIndex = 0;
                visibleIndex < visibleTracks;
                visibleIndex++
        )
        {
            int i =
                    this.trackScroll +
                            visibleIndex;

            if (i >= trackCount)
            {
                break;
            }

            int y =
                    tracksTop +
                            visibleIndex * TRACK_HEIGHT;

            int bottom =
                    y +
                            TRACK_HEIGHT;

            int background =
                    (i % 2 == 0)
                            ? getTimelineTrackColor()
                            : getTimelineTrackAltColor();

            if (i == this.selectedTrack)
            {
                background =
                        getTimelineTrackSelectedColor();
            }

            screen.drawRect(
                    AnimationEditorScreen.LEFT_PANEL_WIDTH,
                    y,
                    width,
                    bottom,
                    background
            );

            screen.drawRect(
                    0,
                    y,
                    AnimationEditorScreen.LEFT_PANEL_WIDTH,
                    bottom,
                    i == this.selectedTrack
                            ? getTimelineTrackSelectedColor()
                            : getTimelineTrackColor()
            );

            if (i == this.selectedTrack)
            {
                screen.drawRect(
                        0,
                        y,
                        3,
                        bottom,
                        getAccentColor()
                );
            }

            screen.drawRect(
                    0,
                    bottom - 1,
                    width,
                    bottom,
                    getTimelineTrackBorderColor()
            );

            Minecraft mc = Minecraft.getMinecraft();
            String label = getTrackLabel(i);
            int labelWidth =
                    AnimationEditorScreen.LEFT_PANEL_WIDTH - 14;

            if (mc.fontRenderer.getStringWidth(label) > labelWidth)
            {
                label =
                        mc.fontRenderer.trimStringToWidth(
                                label,
                                labelWidth
                        );
            }

            screen.drawString(
                    mc.fontRenderer,
                    label,
                    10,
                    y + 6,
                    i == this.selectedTrack
                            ? getAccentBrightColor()
                            : COLOR_TEXT_SECONDARY
            );
        }
    }


    /*
     * =========================================================
     * RULER + GRID
     * =========================================================
     */

    private void drawRulerAndGrid(
            GuiScreen screen,
            int width,
            int timelineTop,
            int timelineStartX,
            int sceneLength)
    {
        int rulerTop =
                timelineTop;

        int rulerBottom =
                timelineTop +
                        HEADER_HEIGHT;

        int gridTop =
                rulerBottom;

        int gridBottom =
                timelineTop +
                        getTimelineHeight();

        screen.drawRect(
                0,
                rulerTop,
                width,
                rulerBottom,
                getTimelineHeaderColor()
        );

        screen.drawRect(
                0,
                rulerTop,
                timelineStartX,
                rulerBottom,
                getTimelineHeaderLightColor()
        );

        Minecraft mc = Minecraft.getMinecraft();

        screen.drawString(
                mc.fontRenderer,
                "CHARACTER",
                10,
                rulerTop + 11,
                getAccentBrightColor()
        );

        screen.drawRect(
                timelineStartX,
                rulerTop,
                width,
                rulerTop + 20,
                getTimelineHeaderLightColor()
        );

        screen.drawRect(
                timelineStartX,
                rulerTop + 20,
                width,
                rulerBottom,
                getTimelineRulerColor()
        );

        screen.drawRect(
                timelineStartX,
                rulerBottom - 1,
                width,
                rulerBottom,
                getTimelineRulerTopColor()
        );

        float pixelsPerFrame =
                this.timeline.getPixelsPerFrame();

        float offset =
                this.timeline.getOffset();

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

        int firstFrame =
                (int) Math.floor(
                        -offset /
                                pixelsPerFrame
                ) - 2;

        if (firstFrame < FIRST_FRAME)
        {
            firstFrame = FIRST_FRAME;
        }

        int lastFrame =
                (int) Math.ceil(
                        (
                                width -
                                        timelineStartX -
                                        offset
                        ) /
                                pixelsPerFrame
                ) + 2;

        int maximumFrame =
                Math.max(
                        sceneLength - 1,
                        getMaximumFrame(sceneLength)
                );

        lastFrame =
                Math.max(
                        lastFrame,
                        Math.min(
                                maximumFrame + 1,
                                lastFrame
                        )
                );

        for (
                int frame = firstFrame;
                frame <= lastFrame;
                frame++
        )
        {
            int x =
                    getFrameX(
                            frame,
                            timelineStartX
                    );

            if (
                    x < timelineStartX ||
                            x >= width
            )
            {
                continue;
            }

            boolean major =
                    frame % minorStep == 0;

            boolean secondary =
                    !major &&
                            frame % 5 == 0;

            int color;

            if (major)
            {
                color = getTimelineGridMajorColor();
            }
            else if (secondary)
            {
                color = getTimelineGridSecondColor();
            }
            else
            {
                color = getTimelineGridColor();
            }

            screen.drawRect(
                    x,
                    gridTop,
                    x + 1,
                    gridBottom,
                    color
            );

            int tickHeight =
                    major
                            ? 8
                            : secondary
                            ? 5
                            : 3;

            screen.drawRect(
                    x,
                    rulerBottom - tickHeight,
                    x + 1,
                    rulerBottom,
                    major
                            ? COLOR_TEXT_SECONDARY
                            : COLOR_TEXT_MUTED
            );

            if (major)
            {
                String label =
                        String.valueOf(frame);

                int labelWidth =
                        Minecraft.getMinecraft()
                                .fontRenderer
                                .getStringWidth(label);

                screen.drawString(
                        Minecraft.getMinecraft()
                                .fontRenderer,
                        label,
                        x - labelWidth / 2,
                        rulerTop + 23,
                        COLOR_TEXT_SECONDARY
                );
            }
        }
    }


    /*
     * =========================================================
     * KEYFRAMES
     * =========================================================
     */

    private void drawTrackKeyframes(
            GuiScreen screen,
            int width,
            int tracksTop,
            int timelineStartX)
    {
        CharacterTimelineController timeline =
                getCharacterTimeline();

        if (timeline == null)
        {
            return;
        }

        Minecraft mc =
                Minecraft.getMinecraft();

        int visibleTracks =
                getVisibleTrackCount();

        clampTrackScroll();

        for (
                int visibleIndex = 0;
                visibleIndex < visibleTracks;
                visibleIndex++
        )
        {
            int trackIndex =
                    this.trackScroll +
                            visibleIndex;

            if (trackIndex >= timeline.getTrackCount())
            {
                break;
            }

            CharacterTrack track =
                    timeline.getTrack(trackIndex);

            if (track == null)
            {
                continue;
            }

            int centerY =
                    tracksTop +
                            visibleIndex * TRACK_HEIGHT +
                            TRACK_HEIGHT / 2;

            BodyPartModelData bodyPart =
                    getBodyPartModel(track);

            if (bodyPart != null)
            {
                drawBodyPartKeyframes(
                        screen,
                        width,
                        centerY,
                        timelineStartX,
                        bodyPart
                );

                continue;
            }

            for (
                    CharacterKey key :
                    track.getKeys()
            )
            {
                if (key == null)
                {
                    continue;
                }

                int frameX =
                        getFrameX(
                                key.getFrame(),
                                timelineStartX
                        );

                if (
                        frameX < timelineStartX - 10 ||
                                frameX > width + 10
                )
                {
                    continue;
                }

                boolean selected =
                        key == this.selectedKey;

                drawKeyframe(
                        screen,
                        frameX,
                        centerY,
                        selected
                );

                String marker =
                        getKeyMarker(key);

                if (
                        marker != null &&
                                marker.length() > 0
                )
                {
                    int markerWidth =
                            mc.fontRenderer
                                    .getStringWidth(marker);

                    screen.drawString(
                            mc.fontRenderer,
                            marker,
                            frameX - markerWidth / 2,
                            centerY - 4,
                            getTimelineKeyframeInnerColor()
                    );
                }
            }
        }
    }

    private BodyPartModelData getBodyPartModel(
            CharacterTrack track)
    {
        if (track == null)
        {
            return null;
        }

        String id = track.getTrackId();

        if (id == null || !id.startsWith("bodypart:"))
        {
            return null;
        }

        for (BodyPartModelData model : this.bodyPartModels)
        {
            if (model != null && id.equals(model.getTimelineId()))
            {
                return model;
            }
        }

        return null;
    }

    private int findBodyPartKeyNearFrame(BodyPartModelData model, int frame)
    {
        if (model == null) return -1;
        int nearest = -1;
        int distance = Integer.MAX_VALUE;
        for (Integer value : model.getModelKeyFrames())
        {
            if (value == null) continue;
            int d = Math.abs(value.intValue() - frame);
            if (d <= 1 && d < distance)
            {
                nearest = value.intValue();
                distance = d;
            }
        }
        return nearest;
    }

    private boolean createBodyPartKey(BodyPartModelData model, int frame)
    {
        if (model == null || !model.hasModel()) return false;
        if (model.hasModelKeyAt(frame)) return true;
        model.setModelKey(frame, model.getModelNameAt(frame));
        return true;
    }

    private void removeBodyPartKey(
            BodyPartModelData model,
            int frame)
    {
        if (model == null || model.getBones() == null)
        {
            return;
        }

        for (AnimationBone bone : model.getBones())
        {
            if (bone != null)
            {
                bone.removeKeyframe(frame);
            }
        }
    }

    private boolean moveBodyPartKey(
            BodyPartModelData model,
            int oldFrame,
            int newFrame)
    {
        if (model == null ||
                oldFrame == newFrame ||
                model.getBones() == null)
        {
            return true;
        }

        if (findBodyPartKeyNearFrame(model, newFrame) == newFrame)
        {
            return false;
        }

        for (AnimationBone bone : model.getBones())
        {
            if (bone == null)
            {
                continue;
            }

            AnimationKeyframe keyframe =
                    bone.getKeyframeAt(oldFrame);

            if (keyframe != null &&
                    !bone.moveKeyframe(keyframe, newFrame))
            {
                return false;
            }
        }

        return true;
    }

    private void drawBodyPartKeyframes(
            GuiScreen screen,
            int width,
            int centerY,
            int timelineStartX,
            BodyPartModelData model)
    {
        if (model == null || model.getBones() == null)
        {
            return;
        }

        java.util.HashSet<Integer> frames =
                new java.util.HashSet<Integer>();

        for (AnimationBone bone : model.getBones())
        {
            if (bone == null || bone.getKeyframes() == null)
            {
                continue;
            }

            for (AnimationKeyframe keyframe : bone.getKeyframes())
            {
                if (keyframe != null)
                {
                    frames.add(keyframe.getFrame());
                }
            }
        }

        for (Integer value : frames)
        {
            if (value == null)
            {
                continue;
            }

            int frame = value.intValue();
            int frameX = getFrameX(frame, timelineStartX);

            if (frameX < timelineStartX - 10 ||
                    frameX > width + 10)
            {
                continue;
            }

            drawKeyframe(
                    screen,
                    frameX,
                    centerY,
                    this.selectedBodyPartModel == model &&
                            this.dragBodyPartFrame == frame
            );

            screen.drawString(
                    Minecraft.getMinecraft().fontRenderer,
                    "B",
                    frameX - 2,
                    centerY - 4,
                    getTimelineKeyframeInnerColor()
            );
        }
    }

    private String getKeyMarker(
            CharacterKey key)
    {
        if (
                key == null ||
                        key.getType() == null
        )
        {
            return "";
        }

        switch (key.getType())
        {
            case SKIN:
                return "S";

            case MORPH:
                return "M";

            case ANIMATION:
                return "A";

            case ACTION:
                return "C";

            case BODY_PART_OVERRIDE:
                return "B";

            case CUSTOM:
                return "?";

            default:
                return "";
        }
    }

    private void drawKeyframe(
            GuiScreen screen,
            int x,
            int y,
            boolean selected)
    {
        int color =
                selected
                        ? getAccentColor()
                        : getTimelineKeyframeColor();

        screen.drawRect(
                x - 1,
                y - 6,
                x + 2,
                y + 7,
                color
        );

        screen.drawRect(
                x - 3,
                y - 4,
                x + 4,
                y + 5,
                color
        );

        screen.drawRect(
                x - 5,
                y - 2,
                x + 6,
                y + 3,
                color
        );

        screen.drawRect(
                x - 1,
                y - 4,
                x + 2,
                y + 5,
                getTimelineKeyframeInnerColor()
        );

        screen.drawRect(
                x - 3,
                y - 2,
                x + 4,
                y + 3,
                getTimelineKeyframeInnerColor()
        );
    }


    /*
     * =========================================================
     * PLAYHEAD
     * =========================================================
     */

    private void drawCurrentFrameLine(
            GuiScreen screen,
            int width,
            int timelineTop,
            int currentFrame,
            int timelineStartX)
    {
        int x =
                getFrameX(
                        currentFrame,
                        timelineStartX
                );

        if (
                x < timelineStartX ||
                        x >= width
        )
        {
            return;
        }

        int bottom =
                timelineTop +
                        getTimelineHeight();

        screen.drawRect(
                x - 1,
                timelineTop,
                x + 2,
                bottom,
                0x66000000
        );

        screen.drawRect(
                x,
                timelineTop,
                x + 1,
                bottom,
                getPlayheadColor()
        );

        screen.drawRect(
                x - 4,
                timelineTop,
                x + 5,
                timelineTop + 4,
                getPlayheadHeadColor()
        );

        screen.drawRect(
                x - 2,
                timelineTop + 4,
                x + 3,
                timelineTop + 6,
                getPlayheadHeadColor()
        );

        String text =
                String.valueOf(currentFrame);

        Minecraft mc =
                Minecraft.getMinecraft();

        int textWidth =
                mc.fontRenderer
                        .getStringWidth(text);

        int labelX =
                x -
                        textWidth / 2;

        if (labelX < timelineStartX + 2)
        {
            labelX =
                    timelineStartX + 2;
        }

        if (
                labelX + textWidth >
                        width - 2
        )
        {
            labelX =
                    width -
                            textWidth -
                            2;
        }

        screen.drawString(
                mc.fontRenderer,
                text,
                labelX,
                timelineTop + 7,
                getPlayheadHeadColor()
        );
    }


    /*
     * =========================================================
     * SELECTION
     * =========================================================
     */

    public int getSelectedTrack()
    {
        return this.selectedTrack;
    }

    public CharacterKey getSelectedKey()
    {
        return this.selectedKey;
    }

    public void clearSelection()
    {
        this.selectedTrack = -1;
        this.selectedKey = null;

        this.draggingKey = false;
        this.dragTrack = -1;
        this.dragKey = null;
        this.draggingBodyPart = false;
        this.dragBodyPartFrame = -1;
        this.dragBodyPartModel = null;

        resetDoubleClickState();
    }


    /*
     * =========================================================
     * CREATE KEY
     * =========================================================
     */

    /**
     * Создаёт новый CharacterKey на указанном треке и кадре.
     *
     * Пока используется CUSTOM.
     *
     * Позже Character Mode сможет передавать сюда
     * конкретный тип:
     *
     * SKIN
     * MORPH
     * ANIMATION
     * ACTION
     * BODY_PART_OVERRIDE
     */
    private CharacterKey createKey(
            CharacterTrack track,
            int frame)
    {
        if (track == null)
        {
            return null;
        }

        CharacterKey existing =
                track.getKeyAtFrame(frame);

        if (existing != null)
        {
            return existing;
        }

        return track.createKey(
                frame,
                CharacterKey.Type.CUSTOM
        );
    }


    /*
     * =========================================================
     * DOUBLE CLICK
     * =========================================================
     */

    private boolean isDoubleClick(
            int track,
            int frame)
    {
        long now =
                System.currentTimeMillis();

        boolean result =
                this.lastLeftClickTrack == track
                        &&
                        Math.abs(
                                this.lastLeftClickFrame -
                                        frame
                        ) <= DOUBLE_CLICK_DISTANCE
                        &&
                        now -
                                this.lastLeftClickTime
                                <= DOUBLE_CLICK_TIME;

        this.lastLeftClickTime = now;
        this.lastLeftClickTrack = track;
        this.lastLeftClickFrame = frame;

        return result;
    }

    private void resetDoubleClickState()
    {
        this.lastLeftClickTime = 0L;
        this.lastLeftClickTrack = -1;
        this.lastLeftClickFrame = -1;
    }


    /*
     * =========================================================
     * MOUSE CLICKED
     * =========================================================
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int height,
            int timelineStartX,
            int sceneLength,
            int timelineTop)
    {
        if (this.selectedActor == null)
        {
            return false;
        }

        int tracksTop =
                timelineTop +
                        HEADER_HEIGHT;

        if (
                mouseX < timelineStartX ||
                        mouseX >= width ||
                        mouseY < timelineTop ||
                        mouseY >=
                                timelineTop +
                                        getTimelineHeight()
        )
        {
            return false;
        }

        /*
         * =====================================================
         * RULER
         * =====================================================
         */

        if (mouseY < tracksTop)
        {
            int frame =
                    getFrameFromMouseX(
                            mouseX,
                            timelineStartX
                    );

            this.timeline.setTick(
                    Math.max(
                            FIRST_FRAME,
                            frame
                    )
            );

            resetDoubleClickState();

            return true;
        }

        /*
         * =====================================================
         * TRACK
         * =====================================================
         */

        int relativeY =
                mouseY -
                        tracksTop;

        int visibleIndex =
                relativeY /
                        TRACK_HEIGHT;

        int trackIndex =
                this.trackScroll +
                        visibleIndex;

        if (
                visibleIndex < 0 ||
                        visibleIndex >= getVisibleTrackCount() ||
                        trackIndex < 0 ||
                        trackIndex >= getTrackCount()
        )
        {
            return false;
        }

        this.selectedTrack =
                trackIndex;

        CharacterTrack track =
                getTrack(trackIndex);

        if (track == null)
        {
            return false;
        }

        int frame =
                getFrameFromMouseX(
                        mouseX,
                        timelineStartX
                );

        if (frame < FIRST_FRAME)
        {
            frame = FIRST_FRAME;
        }

        /*
         * =====================================================
         * EXISTING KEY
         * =====================================================
         */

        BodyPartModelData bodyPart =
                getBodyPartModel(track);

        if (bodyPart != null)
        {
            int existingFrame =
                    findBodyPartKeyNearFrame(
                            bodyPart,
                            frame
                    );

            if (existingFrame >= 0)
            {
                this.selectedBodyPartModel = bodyPart;
                this.selectedKey = null;

                if (mouseButton == 1)
                {
                    removeBodyPartKey(
                            bodyPart,
                            existingFrame
                    );

                    this.draggingBodyPart = false;
                    this.dragBodyPartFrame = -1;
                    this.dragBodyPartModel = null;

                    resetDoubleClickState();
                    return true;
                }

                if (mouseButton == 0)
                {
                    this.draggingBodyPart = true;
                    this.dragBodyPartFrame = existingFrame;
                    this.dragBodyPartModel = bodyPart;

                    resetDoubleClickState();
                    return true;
                }

                return true;
            }
        }

        CharacterKey clickedKey =
                findKeyNearFrame(
                        track,
                        frame
                );

        if (clickedKey != null)
        {
            this.selectedKey =
                    clickedKey;

            if (mouseButton == 1)
            {
                track.removeKey(
                        clickedKey
                );

                this.selectedKey = null;
                this.draggingKey = false;
                this.dragTrack = -1;
                this.dragKey = null;

                resetDoubleClickState();

                return true;
            }

            if (mouseButton == 0)
            {
                this.draggingKey = true;
                this.dragTrack = trackIndex;
                this.dragKey = clickedKey;

                resetDoubleClickState();

                return true;
            }

            return true;
        }

        /*
         * =====================================================
         * EMPTY TRACK
         * =====================================================
         */

        if (mouseButton == 0)
        {
            /*
             * Первый ЛКМ по пустому месту
             * только выбирает дорожку/позицию.
             *
             * Второй быстрый ЛКМ по той же позиции
             * создаёт CharacterKey.
             */
            if (isDoubleClick(trackIndex, frame))
            {
                BodyPartModelData bodyPartForCreate =
                        getBodyPartModel(track);

                if (bodyPartForCreate != null)
                {
                    if (createBodyPartKey(
                            bodyPartForCreate,
                            frame))
                    {
                        this.selectedBodyPartModel = bodyPartForCreate;
                        this.selectedKey = null;
                        this.draggingKey = false;
                        this.dragTrack = -1;
                        this.dragKey = null;
                    }

                    return true;
                }

                CharacterKey key =
                        createKey(
                                track,
                                frame
                        );

                if (key != null)
                {
                    this.selectedKey =
                            key;

                    this.draggingKey = false;
                    this.dragTrack = -1;
                    this.dragKey = null;

                    return true;
                }
            }

            this.selectedKey = null;

            this.timeline.setTick(
                    frame
            );

            return true;
        }

        /*
         * Остальные кнопки мыши
         * не создают ключи.
         */
        resetDoubleClickState();

        return true;
    }


    /*
     * Текущий AnimationEditorScreen.
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int timelineTop,
            int timelineStartX)
    {
        return mouseClicked(
                mouseX,
                mouseY,
                mouseButton,
                width,
                timelineTop +
                        getTimelineHeight(),
                timelineStartX,
                0,
                timelineTop
        );
    }


    /*
     * Дополнительная совместимость.
     */

    public boolean mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int height,
            int timelineStartX,
            int sceneLength)
    {
        int timelineTop =
                height -
                        getTimelineHeight();

        return mouseClicked(
                mouseX,
                mouseY,
                mouseButton,
                width,
                height,
                timelineStartX,
                sceneLength,
                timelineTop
        );
    }


    /*
     * =========================================================
     * FIND KEY
     * =========================================================
     */

    private CharacterKey findKeyNearFrame(
            CharacterTrack track,
            int frame)
    {
        if (track == null)
        {
            return null;
        }

        CharacterKey nearest = null;

        int nearestDistance =
                Integer.MAX_VALUE;

        for (
                CharacterKey key :
                track.getKeys()
        )
        {
            if (key == null)
            {
                continue;
            }

            int distance =
                    Math.abs(
                            key.getFrame() -
                                    frame
                    );

            if (
                    distance <= 1 &&
                            distance < nearestDistance
            )
            {
                nearest = key;
                nearestDistance = distance;
            }
        }

        return nearest;
    }


    /*
     * =========================================================
     * MOUSE DRAG
     * =========================================================
     */

    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            int width,
            int timelineTop,
            int timelineStartX)
    {
        if (clickedMouseButton != 0)
        {
            return false;
        }

        if (this.selectedActor == null)
        {
            return false;
        }

        if (this.draggingBodyPart &&
                this.dragBodyPartModel != null)
        {
            int frame =
                    getFrameFromMouseX(
                            mouseX,
                            timelineStartX
                    );

            if (frame < FIRST_FRAME)
            {
                frame = FIRST_FRAME;
            }

            if (moveBodyPartKey(
                    this.dragBodyPartModel,
                    this.dragBodyPartFrame,
                    frame))
            {
                this.dragBodyPartFrame = frame;
            }

            return true;
        }

        if (!this.draggingKey ||
                this.dragKey == null)
        {
            return false;
        }

        int frame =
                getFrameFromMouseX(
                        mouseX,
                        timelineStartX
                );

        if (frame < FIRST_FRAME)
        {
            frame = FIRST_FRAME;
        }

        CharacterTrack track =
                getTrack(this.dragTrack);

        if (track == null)
        {
            return false;
        }

        CharacterKey existing =
                track.getKeyAtFrame(frame);

        if (
                existing != null &&
                        existing != this.dragKey
        )
        {
            return true;
        }

        this.dragKey.setFrame(frame);

        track.sortKeys();

        return true;
    }

    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton)
    {
        return false;
    }


    /*
     * =========================================================
     * MOUSE RELEASE
     * =========================================================
     */

    public void mouseReleased()
    {
        this.draggingKey = false;
        this.dragTrack = -1;
        this.dragKey = null;
    }


    /*
     * =========================================================
     * MOUSE SCROLL
     * =========================================================
     */

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int wheel,
            int width,
            int height,
            int timelineStartX,
            int sceneLength)
    {
        int timelineTop =
                height -
                        getTimelineHeight();

        if (
                mouseY < timelineTop ||
                        mouseY >= height ||
                        mouseX < timelineStartX ||
                        mouseX >= width
        )
        {
            return false;
        }

        /*
         * Ctrl + колесо = Zoom.
         */

        if (
                Keyboard.isKeyDown(
                        Keyboard.KEY_LCONTROL
                )
                        ||
                        Keyboard.isKeyDown(
                                Keyboard.KEY_RCONTROL
                        )
        )
        {
            int mouseFrame =
                    getFrameFromMouseX(
                            mouseX,
                            timelineStartX
                    );

            float oldFrameX =
                    getFrameX(
                            mouseFrame,
                            timelineStartX
                    );

            this.timeline.changeZoom(
                    wheel > 0
                            ? 0.25F
                            : -0.25F
            );

            float newFrameX =
                    getFrameX(
                            mouseFrame,
                            timelineStartX
                    );

            this.timeline.addOffset(
                    Math.round(
                            newFrameX -
                                    oldFrameX
                    )
            );

            this.timeline.clampOffset(
                    getMaximumFrame(
                            sceneLength
                    ),
                    Math.max(
                            1,
                            width -
                                    timelineStartX
                    )
            );

            return true;
        }

        /*
         * Обычная прокрутка Character Timeline =
         * вертикальная прокрутка дорожек.
         *
         * Горизонтальный Timeline остаётся неподвижным:
         * при большом количестве Body Parts пользователь
         * должен видеть следующие строки, не меняя высоту
         * самого Timeline.
         */
        if (getMaxTrackScroll() > 0)
        {
            this.trackScroll += wheel > 0 ? -1 : 1;
            clampTrackScroll();
        }

        return true;
    }


    /*
     * Текущий AnimationEditorScreen.
     */

    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int wheel,
            int width,
            int height,
            int timelineStartX)
    {
        return mouseScrolled(
                mouseX,
                mouseY,
                wheel,
                width,
                height,
                timelineStartX,
                0
        );
    }


    /*
     * =========================================================
     * DELETE
     * =========================================================
     */

    public void deleteSelectedKey()
    {
        if (
                this.selectedActor == null ||
                        this.selectedKey == null ||
                        this.selectedTrack < 0
        )
        {
            return;
        }

        CharacterTrack track =
                getTrack(this.selectedTrack);

        if (track == null)
        {
            return;
        }

        track.removeKey(
                this.selectedKey
        );

        this.selectedKey = null;
        this.draggingKey = false;
        this.dragTrack = -1;
        this.dragKey = null;

        resetDoubleClickState();
    }


    /*
     * =========================================================
     * UPDATE
     * =========================================================
     */

    public void update()
    {
        /*
         * Пока отдельной логики обновления
         * Character Timeline нет.
         */
    }
}
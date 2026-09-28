package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * UI-контроллер Character Timeline.
 *
 * Character Timeline использует тот же EditorTimeline,
 * что и основной Pose Timeline.
 */
public class CharacterTimelineEditorController
{
    private static final int FIRST_FRAME = 0;
    private static final int TRACK_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 35;

    private static final int COLOR_BACKGROUND = 0xFF17191B;
    private static final int COLOR_HEADER = 0xFF202225;
    private static final int COLOR_HEADER_LIGHT = 0xFF292C2F;
    private static final int COLOR_RULER = 0xFF1D2023;
    private static final int COLOR_RULER_TOP = 0xFF303438;
    private static final int COLOR_TRACK = 0xFF202225;
    private static final int COLOR_TRACK_ALT = 0xFF1C1F21;
    private static final int COLOR_TRACK_SELECTED = 0xFF2B3438;
    private static final int COLOR_TRACK_SELECTED_EDGE = 0xFF66CCFF;
    private static final int COLOR_TRACK_BORDER = 0xFF111315;
    private static final int COLOR_GRID = 0xFF292C2F;
    private static final int COLOR_GRID_MAJOR = 0xFF34383C;
    private static final int COLOR_GRID_SECOND = 0xFF2E3235;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9DA4A9;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;
    private static final int COLOR_CYAN = 0xFF66CCFF;
    private static final int COLOR_KEYFRAME = 0xFFE5E8EA;
    private static final int COLOR_KEYFRAME_INNER = 0xFF25282B;
    private static final int COLOR_KEYFRAME_SELECTED = 0xFF66CCFF;
    private static final int COLOR_PLAYHEAD = 0xFFFF6B6B;
    private static final int COLOR_PLAYHEAD_HEAD = 0xFFFF8A8A;

    private BlockbusterSceneActorData selectedActor;

    private int selectedTrack = -1;

    private CharacterKey selectedKey;

    private final EditorTimeline timeline;

    private boolean draggingKey = false;

    private int dragTrack = -1;

    private CharacterKey dragKey = null;


    /*
     * =========================================================
     * CONSTRUCTORS
     * =========================================================
     */

    public CharacterTimelineEditorController()
    {
        this(new EditorTimeline());
    }

    public CharacterTimelineEditorController(
            EditorTimeline timeline)
    {
        if (timeline == null)
        {
            this.timeline = new EditorTimeline();
        }
        else
        {
            this.timeline = timeline;
        }
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

    public void setTimeline(
            EditorTimeline timeline)
    {
        /*
         * Оставлено для совместимости API.
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
        }

        this.selectedActor = actor;
    }

    public BlockbusterSceneActorData getSelectedActor()
    {
        return this.selectedActor;
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
        return 180;
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
                COLOR_BACKGROUND
        );

        screen.drawRect(
                0,
                timelineTop,
                width,
                timelineTop + 1,
                COLOR_CYAN
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
                COLOR_BACKGROUND
        );

        int trackCount =
                getTrackCount();

        for (
                int i = 0;
                i < trackCount;
                i++
        )
        {
            int y =
                    tracksTop +
                            i * TRACK_HEIGHT;

            int bottom =
                    y +
                            TRACK_HEIGHT;

            int background =
                    (i % 2 == 0)
                            ? COLOR_TRACK
                            : COLOR_TRACK_ALT;

            if (i == this.selectedTrack)
            {
                background =
                        COLOR_TRACK_SELECTED;
            }

            screen.drawRect(
                    0,
                    y,
                    width,
                    bottom,
                    background
            );

            if (i == this.selectedTrack)
            {
                screen.drawRect(
                        0,
                        y,
                        2,
                        bottom,
                        COLOR_TRACK_SELECTED_EDGE
                );
            }

            screen.drawRect(
                    0,
                    bottom - 1,
                    width,
                    bottom,
                    COLOR_TRACK_BORDER
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
                timelineStartX,
                rulerTop,
                width,
                rulerBottom,
                COLOR_HEADER
        );

        screen.drawRect(
                timelineStartX,
                rulerTop,
                width,
                rulerTop + 20,
                COLOR_HEADER_LIGHT
        );

        screen.drawRect(
                timelineStartX,
                rulerTop + 20,
                width,
                rulerBottom,
                COLOR_RULER
        );

        screen.drawRect(
                timelineStartX,
                rulerBottom - 1,
                width,
                rulerBottom,
                COLOR_RULER_TOP
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
                color = COLOR_GRID_MAJOR;
            }
            else if (secondary)
            {
                color = COLOR_GRID_SECOND;
            }
            else
            {
                color = COLOR_GRID;
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

        for (
                int trackIndex = 0;
                trackIndex < timeline.getTrackCount();
                trackIndex++
        )
        {
            CharacterTrack track =
                    timeline.getTrack(trackIndex);

            if (track == null)
            {
                continue;
            }

            int centerY =
                    tracksTop +
                            trackIndex * TRACK_HEIGHT +
                            TRACK_HEIGHT / 2;

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
                            COLOR_KEYFRAME_INNER
                    );
                }
            }
        }
    }

    private String getKeyMarker(
            CharacterKey key)
    {
        if (key == null ||
                key.getType() == null)
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
                        ? COLOR_KEYFRAME_SELECTED
                        : COLOR_KEYFRAME;

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
                COLOR_KEYFRAME_INNER
        );

        screen.drawRect(
                x - 3,
                y - 2,
                x + 4,
                y + 3,
                COLOR_KEYFRAME_INNER
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
                COLOR_PLAYHEAD
        );

        screen.drawRect(
                x - 4,
                timelineTop,
                x + 5,
                timelineTop + 4,
                COLOR_PLAYHEAD_HEAD
        );

        screen.drawRect(
                x - 2,
                timelineTop + 4,
                x + 3,
                timelineTop + 6,
                COLOR_PLAYHEAD_HEAD
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
                COLOR_PLAYHEAD_HEAD
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
         * Ruler.
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

            return true;
        }

        int relativeY =
                mouseY -
                        tracksTop;

        int trackIndex =
                relativeY /
                        TRACK_HEIGHT;

        if (
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

        int frame =
                getFrameFromMouseX(
                        mouseX,
                        timelineStartX
                );

        if (frame < FIRST_FRAME)
        {
            frame = FIRST_FRAME;
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

            if (mouseButton == 0)
            {
                this.draggingKey = true;
                this.dragTrack = trackIndex;
                this.dragKey = clickedKey;
            }

            return true;
        }

        /*
         * Ctrl + ЛКМ создаёт новый keyframe.
         */

        if (
                mouseButton == 0 &&
                        GuiScreen.isCtrlKeyDown()
        )
        {
            CharacterKey key =
                    track.createKey(
                            frame,
                            CharacterKey.Type.CUSTOM
                    );

            this.selectedKey =
                    key;

            this.draggingKey = false;
            this.dragTrack = -1;
            this.dragKey = null;

            return true;
        }

        if (mouseButton == 0)
        {
            this.selectedKey = null;
            this.draggingKey = false;
            this.dragTrack = -1;
            this.dragKey = null;

            return true;
        }

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

            if (distance <= 1 &&
                    distance < nearestDistance)
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
        if (
                !this.draggingKey ||
                        this.dragKey == null ||
                        clickedMouseButton != 0
        )
        {
            return false;
        }

        if (this.selectedActor == null)
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
         * Обычная прокрутка =
         * горизонтальное движение Timeline.
         */

        this.timeline.addOffset(
                wheel > 0
                        ? -20
                        : 20
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
package com.example.examplemod;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnimationEditorScreen extends GuiScreen
{
    /*
     * =========================================================
     * EDITOR MODE
     * =========================================================
     */

    private final EditorModeController editorModeController =
            new EditorModeController();

    private static final int TOP_BAR_HEIGHT = 25;
    private static final int LEFT_PANEL_WIDTH = 180;
    private static final int ACTOR_PANEL_HEIGHT = 150;
    private static final int INTERPOLATION_PANEL_HEIGHT = 100;

    /*
     * =========================================================
     * PREVIEW CONTROLS
     * =========================================================
     */

    private static final int PREVIEW_BUTTON_WIDTH = 30;
    private static final int PREVIEW_BUTTON_HEIGHT = 20;
    private static final int PREVIEW_BUTTON_GAP = 5;

    /*
     * =========================================================
     * DELETE DIALOG
     * =========================================================
     */

    private static final int DELETE_DIALOG_WIDTH = 280;
    private static final int DELETE_DIALOG_HEIGHT = 105;

    private static final int DELETE_BUTTON_WIDTH = 90;
    private static final int DELETE_BUTTON_HEIGHT = 20;

    /*
     * =========================================================
     * VISUAL CONSTANTS
     * =========================================================
     */

    private static final int COLOR_PANEL =
            0xFF17191B;

    private static final int COLOR_PANEL_DARK =
            0xFF111315;

    private static final int COLOR_PANEL_LIGHT =
            0xFF1D2023;

    private static final int COLOR_PANEL_HOVER =
            0xFF25292D;

    private static final int COLOR_SELECTED =
            0xFF28343A;

    private static final int COLOR_SELECTED_EDGE =
            0xFF5CC9E8;

    private static final int COLOR_BORDER =
            0xFF303438;

    private static final int COLOR_BORDER_DARK =
            0xFF0D0F10;

    private static final int COLOR_TEXT =
            0xFFE2E5E7;

    private static final int COLOR_TEXT_SECONDARY =
            0xFF9AA1A6;

    private static final int COLOR_TEXT_MUTED =
            0xFF666D72;

    private static final int COLOR_CYAN =
            0xFF66CCFF;

    private static final int COLOR_CYAN_BRIGHT =
            0xFF8BE1FF;

    /*
     * =========================================================
     * SERVICES
     * =========================================================
     */

    private EditorSceneState sceneState;

    private EditorSceneViewport sceneViewport;

    private final EditorActorPreviewController actorPreviewController =
            new EditorActorPreviewController();

    /*
     * =========================================================
     * TIMELINE / PLAYBACK
     * =========================================================
     */

    private final EditorTimeline timeline =
            new EditorTimeline();

    private final EditorPlaybackController playbackController =
            new EditorPlaybackController(
                    this.timeline
            );

    /*
     * =========================================================
     * CHARACTER MODE
     * =========================================================
     */

    private final CharacterEditorController characterController =
            new CharacterEditorController(
                    this.playbackController
            );

    private final CharacterEditorPanel characterEditorPanel =
            new CharacterEditorPanel(
                    this.characterController
            );

    /*
     * Отдельный UI-контроллер Character Timeline.
     *
     * Он специально вынесен из AnimationEditorScreen,
     * чтобы этот класс не разрастался логикой Character Mode.
     */
    private final CharacterTimelineEditorController
            characterTimelineEditorController =
            new CharacterTimelineEditorController();

    /*
     * =========================================================
     * KEYFRAME CONTROLLER
     * =========================================================
     */

    private final EditorKeyframeController keyframeController =
            new EditorKeyframeController(
                    this.timeline,
                    LEFT_PANEL_WIDTH
            );

    /*
     * =========================================================
     * TIMELINE CONTROLLER
     * =========================================================
     */

    private final EditorTimelineController timelineController =
            new EditorTimelineController(
                    this.timeline,
                    this.keyframeController,
                    this.playbackController,
                    this.actorPreviewController
            );

    /*
     * =========================================================
     * RECORD CONTROLLER
     * =========================================================
     */

    private final EditorRecordController recordController =
            new EditorRecordController(
                    this.playbackController
            );

    /*
     * =========================================================
     * SCENE
     * =========================================================
     */

    private boolean sceneDropdownOpen = false;

    /*
     * =========================================================
     * TRANSFORM
     * =========================================================
     */

    private boolean transformDragging = false;

    /*
     * =========================================================
     * INTERPOLATION
     * =========================================================
     */

    private final InterpolationPanel interpolationPanel =
            new InterpolationPanel();


    @Override
    public void initGui()
    {
        super.initGui();

        BlockbusterPreviewAnimationState.clear();

        EmoticonsPreviewAnimationState.clear();

        this.sceneState =
                new EditorSceneState();

        this.sceneState.refreshScenes();

        this.sceneDropdownOpen = false;

        this.sceneViewport =
                new EditorSceneViewport();

        this.actorPreviewController
                .initializeAdapters();

        this.interpolationPanel
                .setInterpolationChanged(
                        new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                /*
                                 * Interpolation относится только
                                 * к Pose Mode.
                                 */
                                if (
                                        editorModeController.getMode()
                                                != EditorModeController.EditorMode.POSE
                                )
                                {
                                    return;
                                }

                                AnimationKeyframe selectedKeyframe =
                                        keyframeController
                                                .getSelectedKeyframe();

                                if (
                                        selectedKeyframe != null
                                )
                                {
                                    selectedKeyframe
                                            .setInterpolation(
                                                    interpolationPanel
                                                            .getSelectedInterpolation()
                                            );

                                    selectedKeyframe
                                            .setEasing(
                                                    interpolationPanel
                                                            .getSelectedEasing()
                                            );
                                }
                            }
                        }
                );

        /*
         * Если SceneState уже имеет выбранного Actor,
         * сразу синхронизируем его с Character Timeline.
         */
        syncCharacterTimelineActor();

        resetTimeline(1);
    }


    /*
     * =========================================================
     * CHARACTER TIMELINE SYNC
     * =========================================================
     */

    /**
     * Передаёт текущего Actor в Character Timeline Editor.
     *
     * Character Timeline хранится внутри
     * BlockbusterSceneActorData, поэтому здесь мы только
     * передаём ссылку на выбранного Actor.
     */
    private void syncCharacterTimelineActor()
    {
        this.characterTimelineEditorController
                .setSelectedActor(
                        getSelectedActor()
                );
    }


    /*
     * =========================================================
     * RECORD / PREVIEW
     * =========================================================
     */

    private void applyRecordFrame()
    {
        this.recordController.applyRecordFrame(
                getSelectedActorRecord()
        );

        this.recordController.applyAnimationPose();
    }


    private void applyAdapters()
    {
        this.actorPreviewController.applyAdapters(
                this.playbackController
                        .getCurrentFrame()
        );
    }


    private void applyAdapters(
            float animationFrame)
    {
        this.actorPreviewController.applyAdapters(
                animationFrame
        );
    }


    /*
     * =========================================================
     * RESET TIMELINE
     * =========================================================
     */

    private void resetTimeline(
            int length)
    {
        this.playbackController
                .setCurrentFrame(0);

        this.playbackController
                .pause();

        this.keyframeController
                .reset();

        this.timelineController
                .reset();

        this.transformDragging =
                false;

        this.timeline.setLength(
                Math.max(
                        1,
                        length
                )
        );

        this.timeline.rewind();

        this.timeline.pause();

        this.timeline.resetOffset();

        this.timeline.setZoom(
                1.0F
        );

        BlockbusterPreviewAnimationState.clear();

        EmoticonsPreviewAnimationState.clear();

        BlockbusterPreviewAnimationState.setPlaying(
                false
        );

        syncCharacterTimelineActor();

        applyRecordFrame();

        applyAdapters();
    }


    /*
     * =========================================================
     * LOAD SCENE
     * =========================================================
     */

    private void loadScene(
            int index)
    {
        try
        {
            if (!this.sceneState.loadScene(index))
            {
                return;
            }

            this.sceneDropdownOpen =
                    false;

            this.actorPreviewController
                    .clearBones();

            this.keyframeController
                    .reset();

            this.timelineController
                    .reset();

            this.transformDragging =
                    false;

            BlockbusterPreviewAnimationState.clear();

            EmoticonsPreviewAnimationState.clear();

            this.actorPreviewController
                    .getAnimationPreview()
                    .setReferencePosition(
                            0.0D,
                            0.0D,
                            0.0D
                    );

            this.updateSceneViewportPosition();

            syncCharacterTimelineActor();

            resetTimeline(
                    getSceneLength()
            );
        }
        catch (IOException exception)
        {
            exception.printStackTrace();
        }
    }


    /*
     * =========================================================
     * SCENE HELPERS
     * =========================================================
     */

    private int getSceneLength()
    {
        if (this.sceneState == null)
        {
            return 0;
        }

        return this.sceneState
                .getSceneLength();
    }


    private List<BlockbusterSceneActorData>
    getSceneActors()
    {
        if (this.sceneState == null)
        {
            return new ArrayList<BlockbusterSceneActorData>();
        }

        return this.sceneState
                .getActors();
    }


    private BlockbusterSceneActorData
    getSelectedActor()
    {
        if (this.sceneState == null)
        {
            return null;
        }

        return this.sceneState
                .getSelectedActorData();
    }


    public BlockbusterSceneActorData
    getSelectedActorForTimeline()
    {
        return getSelectedActor();
    }


    private BlockbusterRecord
    getSelectedActorRecord()
    {
        if (this.sceneState == null)
        {
            return null;
        }

        return this.sceneState
                .getSelectedActorRecord();
    }


    private BlockbusterRecordFrame
    getCurrentRecordFrame()
    {
        return this.recordController
                .getCurrentRecordFrame(
                        getSelectedActorRecord()
                );
    }


    /*
     * =========================================================
     * SELECT ACTOR
     * =========================================================
     */

    private void selectActor(
            int index)
    {
        List<BlockbusterSceneActorData> actors =
                getSceneActors();

        if (
                index < 0 ||
                        index >= actors.size()
        )
        {
            return;
        }

        BlockbusterSceneActorData data =
                actors.get(index);

        if (data == null)
        {
            return;
        }

        this.sceneState
                .setSelectedActor(index);

        /*
         * Character Timeline получает нового Actor.
         */
        this.characterTimelineEditorController
                .setSelectedActor(
                        data
                );

        this.actorPreviewController
                .selectActorAnimation(
                        data.getId(),
                        this.sceneState,
                        this.keyframeController
                );

        this.playbackController
                .setCurrentFrame(0);

        this.playbackController
                .pause();

        this.timeline.resetOffset();

        this.timelineController
                .reset();

        this.keyframeController
                .resetSelectionOnly();

        this.timeline.setTick(0);

        this.timeline.pause();

        BlockbusterPreviewAnimationState.clear();

        EmoticonsPreviewAnimationState.clear();

        BlockbusterPreviewAnimationState.setPlaying(
                false
        );

        this.actorPreviewController
                .resetPreviewReference(
                        getSelectedActorRecord()
                );

        applyRecordFrame();

        applyAdapters();
    }


    /*
     * =========================================================
     * MAIN DRAW
     * =========================================================
     */

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks)
    {
        this.drawRect(
                0,
                0,
                this.width,
                this.height,
                EditorVisualStyle.BACKGROUND
        );

        drawTopBar();

        drawActorPanel();

        drawPreview(
                mouseX,
                mouseY
        );

        drawTimeline();

        drawInterpolationPanel(
                mouseX,
                mouseY
        );

        if (this.sceneDropdownOpen)
        {
            drawSceneDropdown();
        }

        if (
                this.editorModeController
                        .isDropdownOpen()
        )
        {
            drawEditorModeDropdown();
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        if (isDeleteDialogOpen())
        {
            drawDeleteDialog(
                    mouseX,
                    mouseY
            );
        }
    }


    /*
     * =========================================================
     * TOP BAR
     * =========================================================
     */

    private void drawTopBar()
    {
        this.drawRect(
                0,
                0,
                this.width,
                TOP_BAR_HEIGHT,
                COLOR_PANEL_DARK
        );

        this.drawRect(
                0,
                TOP_BAR_HEIGHT - 1,
                this.width,
                TOP_BAR_HEIGHT,
                COLOR_BORDER
        );

        this.drawRect(
                8,
                TOP_BAR_HEIGHT - 2,
                145,
                TOP_BAR_HEIGHT - 1,
                COLOR_CYAN
        );

        EditorVisualStyle.title(
                this,
                this.fontRenderer,
                "BBS Animation Editor",
                10,
                8
        );

        /*
         * SCENE
         */

        int sceneButtonX =
                175;

        int sceneButtonWidth =
                180;

        boolean sceneHovered =
                isMouseInside(
                        sceneButtonX,
                        3,
                        sceneButtonWidth,
                        TOP_BAR_HEIGHT - 6
                );

        drawToolbarButton(
                sceneButtonX,
                3,
                sceneButtonWidth,
                TOP_BAR_HEIGHT - 6,
                sceneHovered || sceneDropdownOpen
        );

        String sceneName =
                "Select Scene";

        if (this.sceneState != null)
        {
            int selectedScene =
                    this.sceneState
                            .getSelectedScene();

            List<File> sceneFiles =
                    this.sceneState
                            .getSceneFiles();

            if (
                    selectedScene >= 0 &&
                            selectedScene < sceneFiles.size()
            )
            {
                sceneName =
                        sceneFiles
                                .get(selectedScene)
                                .getName();
            }
        }

        if (sceneName.length() > 22)
        {
            sceneName =
                    sceneName.substring(
                            0,
                            19
                    ) + "...";
        }

        String sceneText =
                "SCENE: " + sceneName;

        this.drawString(
                this.fontRenderer,
                sceneText,
                sceneButtonX + 8,
                8,
                sceneDropdownOpen
                        ? COLOR_CYAN_BRIGHT
                        : COLOR_TEXT
        );

        this.drawString(
                this.fontRenderer,
                "▼",
                sceneButtonX +
                        sceneButtonWidth -
                        13,
                8,
                COLOR_TEXT_SECONDARY
        );

        /*
         * ACTOR
         */

        BlockbusterSceneActorData actor =
                getSelectedActor();

        int actorX =
                sceneButtonX +
                        sceneButtonWidth +
                        15;

        String actorText =
                "ACTOR: —";

        if (actor != null)
        {
            String actorName =
                    actor.getId();

            if (
                    actorName == null ||
                            actorName.length() == 0
            )
            {
                actorName = "Unnamed";
            }

            if (actorName.length() > 20)
            {
                actorName =
                        actorName.substring(
                                0,
                                17
                        ) + "...";
            }

            actorText =
                    "ACTOR: " + actorName;
        }

        this.drawString(
                this.fontRenderer,
                actorText,
                actorX,
                8,
                actor != null
                        ? COLOR_CYAN
                        : COLOR_TEXT_MUTED
        );

        /*
         * MODE
         */

        int modeX =
                this.editorModeController
                        .getButtonX(
                                this.width
                        );

        int mouseX =
                Mouse.getX()
                        *
                        this.width
                        /
                        this.mc.displayWidth;

        int mouseY =
                this.height
                        -
                        Mouse.getY()
                                *
                                this.height
                                /
                                this.mc.displayHeight
                        -
                        1;

        boolean modeHovered =
                this.editorModeController
                        .isMouseOverButton(
                                this.width,
                                mouseX,
                                mouseY
                        );

        drawToolbarButton(
                modeX,
                3,
                EditorModeController.MODE_BUTTON_WIDTH,
                TOP_BAR_HEIGHT - 6,
                modeHovered ||
                        this.editorModeController
                                .isDropdownOpen()
        );

        String modeText =
                "MODE: " +
                        this.editorModeController
                                .getModeName();

        this.drawString(
                this.fontRenderer,
                modeText,
                modeX + 8,
                8,
                this.editorModeController
                        .isDropdownOpen()
                        ? COLOR_CYAN_BRIGHT
                        : COLOR_TEXT
        );

        this.drawString(
                this.fontRenderer,
                "▼",
                modeX +
                        EditorModeController.MODE_BUTTON_WIDTH -
                        13,
                8,
                COLOR_TEXT_SECONDARY
        );

        /*
         * FRAME
         */

        int frame =
                this.playbackController
                        .getCurrentFrame();

        String frameText =
                "FRAME  " + frame;

        int frameWidth =
                this.fontRenderer
                        .getStringWidth(
                                frameText
                        );

        this.drawString(
                this.fontRenderer,
                frameText,
                this.width -
                        frameWidth -
                        12,
                8,
                COLOR_TEXT_SECONDARY
        );
    }


    private void drawToolbarButton(
            int x,
            int y,
            int width,
            int height,
            boolean hovered)
    {
        this.drawRect(
                x,
                y,
                x + width,
                y + height,
                hovered
                        ? COLOR_PANEL_HOVER
                        : COLOR_PANEL_LIGHT
        );

        this.drawRect(
                x,
                y,
                x + width,
                y + 1,
                hovered
                        ? COLOR_CYAN
                        : COLOR_BORDER
        );

        this.drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                COLOR_BORDER_DARK
        );
    }


    /*
     * =========================================================
     * MODE DROPDOWN
     * =========================================================
     */

    private void drawEditorModeDropdown()
    {
        int x =
                this.editorModeController
                        .getDropdownX(
                                this.width
                        );

        int y =
                this.editorModeController
                        .getDropdownY();

        int width =
                this.editorModeController
                        .getDropdownWidth();

        int rowHeight =
                this.editorModeController
                        .getDropdownRowHeight();

        int height =
                this.editorModeController
                        .getDropdownHeight();

        drawDropdownFrame(
                x,
                y,
                width,
                height
        );

        EditorModeController.EditorMode[] modes =
                new EditorModeController.EditorMode[]
                        {
                                EditorModeController.EditorMode.CHARACTER,
                                EditorModeController.EditorMode.POSE,
                                EditorModeController.EditorMode.BODY_PARTS
                        };

        for (
                int i = 0;
                i < modes.length;
                i++
        )
        {
            EditorModeController.EditorMode mode =
                    modes[i];

            int rowY =
                    y +
                            i *
                                    rowHeight;

            int mouseX =
                    Mouse.getX()
                            *
                            this.width
                            /
                            this.mc.displayWidth;

            int mouseY =
                    this.height
                            -
                            Mouse.getY()
                                    *
                                    this.height
                                    /
                                    this.mc.displayHeight
                            -
                            1;

            boolean hovered =
                    mouseX >= x
                            &&
                            mouseX < x + width
                            &&
                            mouseY >= rowY
                            &&
                            mouseY < rowY + rowHeight;

            boolean selected =
                    this.editorModeController
                            .isModeSelected(
                                    mode
                            );

            if (selected)
            {
                this.drawRect(
                        x,
                        rowY,
                        x + 2,
                        rowY + rowHeight,
                        COLOR_CYAN
                );

                this.drawRect(
                        x + 2,
                        rowY,
                        x + width,
                        rowY + rowHeight,
                        hovered
                                ? COLOR_PANEL_HOVER
                                : COLOR_SELECTED
                );
            }
            else if (hovered)
            {
                this.drawRect(
                        x,
                        rowY,
                        x + width,
                        rowY + rowHeight,
                        COLOR_PANEL_HOVER
                );
            }

            String name =
                    this.editorModeController
                            .getModeName(
                                    mode
                            );

            this.drawString(
                    this.fontRenderer,
                    name,
                    x + 9,
                    rowY + 5,
                    selected
                            ? COLOR_CYAN_BRIGHT
                            : COLOR_TEXT
            );
        }
    }


    /*
     * =========================================================
     * SCENE DROPDOWN
     * =========================================================
     */

    private void drawSceneDropdown()
    {
        int x = 175;

        int y =
                TOP_BAR_HEIGHT + 2;

        int width = 180;

        int rowHeight = 19;

        int maxVisible = 8;

        List<File> sceneFiles =
                this.sceneState
                        .getSceneFiles();

        int selectedScene =
                this.sceneState
                        .getSelectedScene();

        int count =
                Math.min(
                        maxVisible,
                        sceneFiles.size()
                );

        int height =
                Math.max(
                        rowHeight,
                        count * rowHeight
                );

        drawDropdownFrame(
                x,
                y,
                width,
                height
        );

        if (sceneFiles.isEmpty())
        {
            this.drawString(
                    this.fontRenderer,
                    "No scenes found",
                    x + 9,
                    y + 6,
                    COLOR_TEXT_MUTED
            );

            return;
        }

        for (
                int i = 0;
                i < count;
                i++
        )
        {
            File file =
                    sceneFiles.get(i);

            int rowY =
                    y +
                            i *
                                    rowHeight;

            boolean selected =
                    i == selectedScene;

            int mouseX =
                    Mouse.getX()
                            *
                            this.width
                            /
                            this.mc.displayWidth;

            int mouseY =
                    this.height
                            -
                            Mouse.getY()
                                    *
                                    this.height
                                    /
                                    this.mc.displayHeight
                            -
                            1;

            boolean hovered =
                    mouseX >= x &&
                            mouseX < x + width &&
                            mouseY >= rowY &&
                            mouseY < rowY + rowHeight;

            if (selected)
            {
                this.drawRect(
                        x,
                        rowY,
                        x + 2,
                        rowY + rowHeight,
                        COLOR_CYAN
                );

                this.drawRect(
                        x + 2,
                        rowY,
                        x + width,
                        rowY + rowHeight,
                        hovered
                                ? COLOR_PANEL_HOVER
                                : COLOR_SELECTED
                );
            }
            else if (hovered)
            {
                this.drawRect(
                        x,
                        rowY,
                        x + width,
                        rowY + rowHeight,
                        COLOR_PANEL_HOVER
                );
            }

            String name =
                    file.getName();

            if (name.length() > 24)
            {
                name =
                        name.substring(
                                0,
                                21
                        ) + "...";
            }

            this.drawString(
                    this.fontRenderer,
                    name,
                    x + 9,
                    rowY + 6,
                    selected
                            ? COLOR_CYAN_BRIGHT
                            : COLOR_TEXT
            );
        }
    }


    private void drawDropdownFrame(
            int x,
            int y,
            int width,
            int height)
    {
        this.drawRect(
                x - 3,
                y - 2,
                x + width + 3,
                y + height + 3,
                0xCC080909
        );

        this.drawRect(
                x,
                y,
                x + width,
                y + height,
                COLOR_PANEL
        );

        this.drawRect(
                x,
                y,
                x + width,
                y + 1,
                COLOR_CYAN
        );

        this.drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                COLOR_BORDER
        );

        this.drawRect(
                x,
                y,
                x + 1,
                y + height,
                COLOR_BORDER
        );

        this.drawRect(
                x + width - 1,
                y,
                x + width,
                y + height,
                COLOR_BORDER
        );
    }


    /*
     * =========================================================
     * ACTOR PANEL
     * =========================================================
     */

    private void drawActorPanel()
    {
        int top =
                TOP_BAR_HEIGHT;

        int bottom =
                top +
                        ACTOR_PANEL_HEIGHT;

        this.drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                COLOR_PANEL
        );

        this.drawRect(
                LEFT_PANEL_WIDTH - 1,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                COLOR_BORDER
        );

        drawPanelHeader(
                "ACTORS",
                0,
                top,
                LEFT_PANEL_WIDTH
        );

        List<BlockbusterSceneActorData> actors =
                getSceneActors();

        if (actors.isEmpty())
        {
            this.drawString(
                    this.fontRenderer,
                    "No actors",
                    12,
                    top + 38,
                    COLOR_TEXT_MUTED
            );

            return;
        }

        int actorY =
                top + 30;

        final int actorRowHeight =
                31;

        for (
                int i = 0;
                i < actors.size();
                i++
        )
        {
            BlockbusterSceneActorData actor =
                    actors.get(i);

            if (actor == null)
            {
                continue;
            }

            if (
                    actorY + actorRowHeight >
                            bottom - 3
            )
            {
                break;
            }

            boolean selected =
                    i ==
                            this.sceneState
                                    .getSelectedActor();

            int mouseX =
                    Mouse.getX()
                            *
                            this.width
                            /
                            this.mc.displayWidth;

            int mouseY =
                    this.height
                            -
                            Mouse.getY()
                                    *
                                    this.height
                                    /
                                    this.mc.displayHeight
                            -
                            1;

            boolean hovered =
                    mouseX >= 5 &&
                            mouseX < LEFT_PANEL_WIDTH - 5 &&
                            mouseY >= actorY - 2 &&
                            mouseY < actorY +
                                    actorRowHeight - 2;

            if (selected)
            {
                this.drawRect(
                        5,
                        actorY - 2,
                        LEFT_PANEL_WIDTH - 5,
                        actorY +
                                actorRowHeight - 2,
                        COLOR_SELECTED
                );

                this.drawRect(
                        5,
                        actorY - 2,
                        7,
                        actorY +
                                actorRowHeight - 2,
                        COLOR_CYAN
                );
            }
            else if (hovered)
            {
                this.drawRect(
                        5,
                        actorY - 2,
                        LEFT_PANEL_WIDTH - 5,
                        actorY +
                                actorRowHeight - 2,
                        COLOR_PANEL_HOVER
                );
            }

            String id =
                    actor.getId();

            String name =
                    actor.getName();

            if (
                    name == null ||
                            name.length() == 0
            )
            {
                name =
                        actor.getMorphName();
            }

            if (
                    name == null ||
                            name.length() == 0
            )
            {
                name =
                        "Unnamed Actor";
            }

            if (id.length() > 22)
            {
                id =
                        id.substring(
                                0,
                                19
                        ) + "...";
            }

            if (name.length() > 22)
            {
                name =
                        name.substring(
                                0,
                                19
                        ) + "...";
            }

            this.drawString(
                    this.fontRenderer,
                    id,
                    13,
                    actorY + 1,
                    selected
                            ? COLOR_TEXT
                            : COLOR_TEXT_SECONDARY
            );

            this.drawString(
                    this.fontRenderer,
                    name,
                    13,
                    actorY + 13,
                    selected
                            ? COLOR_CYAN
                            : COLOR_TEXT_MUTED
            );

            actorY +=
                    actorRowHeight;
        }
    }


    private void drawPanelHeader(
            String text,
            int x,
            int y,
            int width)
    {
        this.drawRect(
                x,
                y,
                x + width,
                y + 25,
                COLOR_PANEL_DARK
        );

        this.drawRect(
                x,
                y + 24,
                x + width,
                y + 25,
                COLOR_BORDER
        );

        this.drawRect(
                x + 9,
                y + 7,
                x + 11,
                y + 18,
                COLOR_CYAN
        );

        this.drawString(
                this.fontRenderer,
                text,
                x + 16,
                y + 8,
                COLOR_TEXT
        );
    }


    /*
     * =========================================================
     * INTERPOLATION
     * =========================================================
     */

    private void drawInterpolationPanel(
            int mouseX,
            int mouseY)
    {
        int top =
                TOP_BAR_HEIGHT +
                        ACTOR_PANEL_HEIGHT;

        this.interpolationPanel
                .setBounds(
                        0,
                        top,
                        LEFT_PANEL_WIDTH,
                        INTERPOLATION_PANEL_HEIGHT
                );

        this.drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                top +
                        INTERPOLATION_PANEL_HEIGHT,
                COLOR_PANEL
        );

        this.drawRect(
                LEFT_PANEL_WIDTH - 1,
                top,
                LEFT_PANEL_WIDTH,
                top +
                        INTERPOLATION_PANEL_HEIGHT,
                COLOR_BORDER
        );

        /*
         * Interpolation используется только Pose,
         * но сам существующий визуальный блок пока
         * оставляем без изменений.
         */
        AnimationKeyframe selectedKeyframe =
                this.keyframeController
                        .getSelectedKeyframe();

        if (
                selectedKeyframe != null &&
                        this.editorModeController.getMode()
                                == EditorModeController.EditorMode.POSE
        )
        {
            this.interpolationPanel
                    .setSelectedInterpolation(
                            selectedKeyframe
                                    .getInterpolation()
                    );

            this.interpolationPanel
                    .setSelectedEasing(
                            selectedKeyframe
                                    .getEasing()
                    );
        }

        this.interpolationPanel.draw(
                mouseX,
                mouseY
        );
    }


    /*
     * =========================================================
     * PREVIEW
     * =========================================================
     */

    private void drawPreview(
            int mouseX,
            int mouseY)
    {
        int left =
                LEFT_PANEL_WIDTH;

        int top =
                TOP_BAR_HEIGHT;

        int rightPanelWidth =
                185;

        int previewRight =
                this.width -
                        rightPanelWidth;

        int bottom =
                this.height -
                        getTimelineHeight();

        int previewWidth =
                Math.max(
                        150,
                        previewRight - left
                );

        int previewHeight =
                Math.max(
                        1,
                        bottom - top
                );

        /*
         * Preview background.
         */
        this.drawRect(
                left,
                top,
                previewRight,
                bottom,
                0xFF0E1011
        );

        /*
         * Preview top border.
         */
        this.drawRect(
                left,
                top,
                previewRight,
                top + 1,
                COLOR_BORDER
        );

        /*
         * Preview right separator.
         */
        this.drawRect(
                previewRight - 1,
                top,
                previewRight,
                bottom,
                COLOR_BORDER
        );

        /*
         * Preview viewport.
         */
        this.sceneViewport.setBounds(
                left,
                top,
                previewWidth,
                previewHeight
        );

        /*
         * Smooth playback.
         *
         * Не изменяем существующую логику паузы:
         * во время воспроизведения используются
         * дробные animation frames.
         */
        if (
                this.playbackController
                        .isPlaying()
        )
        {
            applyAdapters(
                    this.playbackController
                            .getCurrentAnimationFrame()
            );
        }

        this.sceneViewport.draw(
                this.mc,
                this.recordController
                        .getCurrentActorPose(),
                this.actorPreviewController
                        .getBones(),
                this.playbackController
                        .getCurrentFrame()
        );

        this.sceneViewport.drawActor(
                this.mc,
                this.getSelectedActor(),
                this.getCurrentRecordFrame()
        );

        this.sceneViewport
                .finishPreviewRender();

        this.sceneViewport
                .renderPreviewToScreen();

        drawPreviewFrame(
                left,
                top,
                previewRight,
                bottom
        );

        drawPreviewControls(
                left,
                previewRight,
                top,
                bottom
        );

        /*
         * =====================================================
         * RIGHT EDITOR PANEL
         * =====================================================
         *
         * Character Mode:
         *     CharacterEditorPanel
         *
         * Pose Mode:
         *     TransformPanel
         *
         * Body Parts:
         *     Пока отдельная панель ещё не подключена.
         *
         * Preview при этом НЕ меняется.
         */

        int panelX =
                this.width -
                        rightPanelWidth;

        int panelY =
                top;

        this.drawRect(
                panelX,
                top,
                this.width,
                bottom,
                COLOR_PANEL
        );

        this.drawRect(
                panelX,
                top,
                panelX + 1,
                bottom,
                COLOR_BORDER
        );

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            this.characterEditorPanel
                    .setBounds(
                            panelX,
                            panelY,
                            rightPanelWidth,
                            bottom - panelY
                    );

            this.characterEditorPanel.draw(
                    this.mc,
                    mouseX,
                    mouseY
            );
        }
        else if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            /*
             * Transform остаётся исключительно
             * частью Pose Mode.
             */
            this.actorPreviewController
                    .getTransformPanel()
                    .setPosition(
                            panelX,
                            panelY + 10
                    );

            this.actorPreviewController
                    .getTransformPanel()
                    .draw(
                            this.mc,
                            this.keyframeController
                                    .getSelectedKeyframe(),
                            getCurrentTransform(),
                            this.playbackController
                                    .getCurrentFrame()
                    );
        }
    }


    private void drawPreviewFrame(
            int left,
            int top,
            int right,
            int bottom)
    {
        this.drawRect(
                left,
                top,
                right,
                top + 1,
                COLOR_BORDER
        );

        this.drawRect(
                left,
                bottom - 1,
                right,
                bottom,
                COLOR_BORDER
        );

        this.drawRect(
                left + 7,
                top + 7,
                left + 32,
                top + 8,
                COLOR_CYAN
        );

        this.drawString(
                this.fontRenderer,
                "PREVIEW",
                left + 7,
                top + 10,
                COLOR_TEXT_MUTED
        );
    }


    private void drawPreviewControls(
            int left,
            int right,
            int top,
            int bottom)
    {
        int totalWidth =
                PREVIEW_BUTTON_WIDTH * 3
                        +
                        PREVIEW_BUTTON_GAP * 2;

        int centerX =
                left +
                        (
                                right - left
                        ) / 2;

        int startX =
                centerX -
                        totalWidth / 2;

        int buttonY =
                bottom -
                        PREVIEW_BUTTON_HEIGHT -
                        12;

        int mouseX =
                Mouse.getX()
                        *
                        this.width
                        /
                        this.mc.displayWidth;

        int mouseY =
                this.height -
                        Mouse.getY()
                                *
                                this.height
                                /
                                this.mc.displayHeight
                        -
                        1;

        int previousX =
                startX;

        boolean previousHovered =
                isMouseInside(
                        mouseX,
                        mouseY,
                        previousX,
                        buttonY,
                        PREVIEW_BUTTON_WIDTH,
                        PREVIEW_BUTTON_HEIGHT
                );

        drawPreviewButton(
                previousX,
                buttonY,
                "|<",
                previousHovered
        );

        int playX =
                previousX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        boolean playHovered =
                isMouseInside(
                        mouseX,
                        mouseY,
                        playX,
                        buttonY,
                        PREVIEW_BUTTON_WIDTH,
                        PREVIEW_BUTTON_HEIGHT
                );

        drawPreviewButton(
                playX,
                buttonY,
                this.playbackController
                        .isPlaying()
                        ? "||"
                        : ">",
                playHovered
        );

        int nextX =
                playX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        boolean nextHovered =
                isMouseInside(
                        mouseX,
                        mouseY,
                        nextX,
                        buttonY,
                        PREVIEW_BUTTON_WIDTH,
                        PREVIEW_BUTTON_HEIGHT
                );

        drawPreviewButton(
                nextX,
                buttonY,
                ">|",
                nextHovered
        );
    }


    private void drawPreviewButton(
            int x,
            int y,
            String text,
            boolean hovered)
    {
        int background =
                hovered
                        ? COLOR_SELECTED
                        : COLOR_PANEL_DARK;

        this.drawRect(
                x,
                y,
                x + PREVIEW_BUTTON_WIDTH,
                y + PREVIEW_BUTTON_HEIGHT,
                background
        );

        this.drawRect(
                x,
                y,
                x + PREVIEW_BUTTON_WIDTH,
                y + 1,
                hovered
                        ? COLOR_CYAN
                        : COLOR_BORDER
        );

        this.drawRect(
                x,
                y +
                        PREVIEW_BUTTON_HEIGHT -
                        1,
                x + PREVIEW_BUTTON_WIDTH,
                y +
                        PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER_DARK
        );

        this.drawRect(
                x,
                y,
                x + 1,
                y + PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER
        );

        this.drawRect(
                x +
                        PREVIEW_BUTTON_WIDTH -
                        1,
                y,
                x +
                        PREVIEW_BUTTON_WIDTH,
                y +
                        PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER
        );

        int textWidth =
                this.fontRenderer
                        .getStringWidth(
                                text
                        );

        this.drawString(
                this.fontRenderer,
                text,
                x +
                        (
                                PREVIEW_BUTTON_WIDTH -
                                        textWidth
                        ) / 2,
                y + 6,
                hovered
                        ? COLOR_CYAN_BRIGHT
                        : COLOR_TEXT
        );
    }


    /*
     * =========================================================
     * TIMELINE
     * =========================================================
     */

    private void drawTimeline()
    {
        /*
         * =====================================================
         * CHARACTER MODE
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            syncCharacterTimelineActor();

            int timelineHeight =
                    this.characterTimelineEditorController
                            .getTimelineHeight();

            int top =
                    this.height -
                            timelineHeight;

            this.characterTimelineEditorController
                    .draw(
                            this,
                            this.width,
                            this.height,
                            0,
                            top,
                            this.playbackController
                                    .getCurrentFrame(),
                            getSceneLength()
                    );

            return;
        }

        /*
         * =====================================================
         * POSE / EXISTING TIMELINE
         * =====================================================
         */

        int timelineHeight =
                getTimelineHeight();

        int top =
                this.height -
                        timelineHeight;

        this.drawRect(
                0,
                top,
                this.width,
                this.height,
                COLOR_PANEL_DARK
        );

        this.drawRect(
                0,
                top,
                this.width,
                top + 1,
                COLOR_CYAN
        );

        this.timelineController.draw(
                this.width,
                this.height,
                LEFT_PANEL_WIDTH,
                getSceneLength(),
                getSelectedActor()
        );
    }


    private int getTimelineHeight()
    {
        /*
         * Character Timeline имеет собственную высоту.
         *
         * Это также гарантирует, что Preview и его нижняя
         * граница используют ту же высоту, что и Timeline.
         */
        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            return this.characterTimelineEditorController
                    .getTimelineHeight();
        }

        return this.timelineController
                .getTimelineHeight();
    }


    /*
     * =========================================================
     * INPUT
     * =========================================================
     */

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws IOException
    {
        if (isDeleteDialogOpen())
        {
            handleDeleteDialogClick(
                    mouseX,
                    mouseY,
                    mouseButton
            );

            return;
        }

        this.transformDragging =
                false;

        /*
         * =====================================================
         * MODE BUTTON
         * =====================================================
         */

        if (
                this.editorModeController
                        .isMouseOverButton(
                                this.width,
                                mouseX,
                                mouseY
                        )
        )
        {
            if (mouseButton == 0)
            {
                this.editorModeController
                        .toggleDropdown();

                this.sceneDropdownOpen =
                        false;
            }

            return;
        }

        /*
         * =====================================================
         * MODE DROPDOWN
         * =====================================================
         */

        if (
                this.editorModeController
                        .isDropdownOpen()
        )
        {
            EditorModeController.EditorMode selectedMode =
                    this.editorModeController
                            .getModeAtMouse(
                                    this.width,
                                    mouseX,
                                    mouseY
                            );

            if (selectedMode != null)
            {
                this.editorModeController
                        .setMode(
                                selectedMode
                        );

                /*
                 * Pose selection сбрасывается только
                 * при смене режима.
                 */
                this.keyframeController
                        .resetSelectionOnly();

                this.transformDragging =
                        false;

                /*
                 * Character Timeline получает текущего Actor.
                 */
                syncCharacterTimelineActor();

                return;
            }

            this.editorModeController
                    .closeDropdown();
        }

        /*
         * =====================================================
         * SCENE BUTTON
         * =====================================================
         */

        int sceneButtonX = 175;

        int sceneButtonWidth = 180;

        if (
                mouseX >= sceneButtonX &&
                        mouseX <=
                                sceneButtonX +
                                        sceneButtonWidth &&
                        mouseY >= 3 &&
                        mouseY <=
                                TOP_BAR_HEIGHT - 3
        )
        {
            this.sceneDropdownOpen =
                    !this.sceneDropdownOpen;

            this.editorModeController
                    .closeDropdown();

            return;
        }

        /*
         * =====================================================
         * SCENE DROPDOWN
         * =====================================================
         */

        if (this.sceneDropdownOpen)
        {
            int x = 175;

            int y =
                    TOP_BAR_HEIGHT + 2;

            int width = 180;

            int rowHeight = 19;

            int count =
                    Math.min(
                            8,
                            this.sceneState
                                    .getSceneFiles()
                                    .size()
                    );

            if (
                    mouseX >= x &&
                            mouseX <= x + width &&
                            mouseY >= y &&
                            mouseY <
                                    y +
                                            count *
                                                    rowHeight
            )
            {
                int sceneIndex =
                        (
                                mouseY - y
                        ) /
                                rowHeight;

                if (
                        sceneIndex >= 0 &&
                                sceneIndex <
                                        this.sceneState
                                                .getSceneFiles()
                                                .size()
                )
                {
                    loadScene(
                            sceneIndex
                    );

                    return;
                }
            }

            this.sceneDropdownOpen =
                    false;

            this.editorModeController
                    .closeDropdown();
        }

        /*
         * =====================================================
         * CHARACTER PANEL
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            int rightPanelWidth = 185;

            int panelX =
                    this.width -
                            rightPanelWidth;

            int panelY =
                    TOP_BAR_HEIGHT;

            int panelBottom =
                    this.height -
                            getTimelineHeight();

            if (
                    mouseX >= panelX &&
                            mouseX < this.width &&
                            mouseY >= panelY &&
                            mouseY < panelBottom
            )
            {
                if (
                        this.characterEditorPanel
                                .mouseClicked(
                                        mouseX,
                                        mouseY,
                                        mouseButton
                                )
                )
                {
                    return;
                }
            }
        }

        /*
         * =====================================================
         * INTERPOLATION
         * =====================================================
         *
         * Только Pose Mode.
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
                        &&
                        this.interpolationPanel != null
        )
        {
            if (
                    this.interpolationPanel
                            .mouseClicked(
                                    mouseX,
                                    mouseY,
                                    mouseButton
                            )
            )
            {
                return;
            }
        }

        /*
         * =====================================================
         * ACTOR LIST
         * =====================================================
         */

        int actorTop =
                TOP_BAR_HEIGHT;

        int actorBottom =
                actorTop +
                        ACTOR_PANEL_HEIGHT;

        final int actorRowHeight =
                31;

        if (
                mouseX >= 0 &&
                        mouseX <= LEFT_PANEL_WIDTH &&
                        mouseY >= actorTop + 30 &&
                        mouseY < actorBottom
        )
        {
            int relativeY =
                    mouseY -
                            (
                                    actorTop + 30
                            );

            int actorIndex =
                    relativeY /
                            actorRowHeight;

            if (
                    actorIndex >= 0 &&
                            actorIndex <
                                    getSceneActors()
                                            .size()
            )
            {
                selectActor(
                        actorIndex
                );

                return;
            }
        }

        /*
         * =====================================================
         * PREVIEW BUTTONS
         * =====================================================
         */

        int previewBottom =
                this.height -
                        getTimelineHeight();

        int previewRight =
                this.width - 185;

        int totalWidth =
                PREVIEW_BUTTON_WIDTH * 3
                        +
                        PREVIEW_BUTTON_GAP * 2;

        int centerX =
                LEFT_PANEL_WIDTH +
                        (
                                previewRight -
                                        LEFT_PANEL_WIDTH
                        ) / 2;

        int startX =
                centerX -
                        totalWidth / 2;

        int buttonY =
                previewBottom -
                        PREVIEW_BUTTON_HEIGHT -
                        12;

        int previousX =
                startX;

        if (
                mouseX >= previousX &&
                        mouseX <
                                previousX +
                                        PREVIEW_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        PREVIEW_BUTTON_HEIGHT
        )
        {
            stepTimeline(-1);

            return;
        }

        int playX =
                previousX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        if (
                mouseX >= playX &&
                        mouseX <
                                playX +
                                        PREVIEW_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        PREVIEW_BUTTON_HEIGHT
        )
        {
            toggleTimelinePlayback();

            return;
        }

        int nextX =
                playX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        if (
                mouseX >= nextX &&
                        mouseX <
                                nextX +
                                        PREVIEW_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        PREVIEW_BUTTON_HEIGHT
        )
        {
            stepTimeline(1);

            return;
        }

        /*
         * =====================================================
         * TRANSFORM PANEL
         * =====================================================
         *
         * Только Pose Mode.
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            AnimationKeyframe selectedKeyframe =
                    this.keyframeController
                            .getSelectedKeyframe();

            if (selectedKeyframe != null)
            {
                int transformPanelX =
                        this.width - 185;

                int transformPanelY =
                        TOP_BAR_HEIGHT + 10;

                int transformPanelWidth =
                        175;

                int transformPanelHeight =
                        245;

                boolean insideTransformPanel =
                        mouseX >= transformPanelX &&
                                mouseX <=
                                        transformPanelX +
                                                transformPanelWidth &&
                                mouseY >= transformPanelY &&
                                mouseY <=
                                        transformPanelY +
                                                transformPanelHeight;

                if (insideTransformPanel)
                {
                    if (
                            this.actorPreviewController
                                    .getTransformPanel()
                                    .mouseClicked(
                                            mouseX,
                                            mouseY,
                                            mouseButton,
                                            selectedKeyframe
                                    )
                    )
                    {
                        this.transformDragging =
                                true;

                        return;
                    }
                }
            }
        }

        /*
         * =====================================================
         * SCENE VIEWPORT
         * =====================================================
         */

        if (
                this.sceneViewport != null &&
                        this.sceneViewport.mousePressed(
                                mouseX,
                                mouseY,
                                mouseButton
                        )
        )
        {
            return;
        }

        /*
         * =====================================================
         * CHARACTER TIMELINE
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            syncCharacterTimelineActor();

            if (
                    this.characterTimelineEditorController
                            .mouseClicked(
                                    mouseX,
                                    mouseY,
                                    mouseButton,
                                    this.width,
                                    this.height -
                                            getTimelineHeight(),
                                    0
                            )
            )
            {
                return;
            }
        }

        /*
         * =====================================================
         * EXISTING POSE TIMELINE
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            if (
                    this.timelineController
                            .mouseClicked(
                                    mouseX,
                                    mouseY,
                                    mouseButton,
                                    this.width,
                                    this.height,
                                    LEFT_PANEL_WIDTH,
                                    getSceneLength()
                            )
            )
            {
                applyRecordFrame();

                applyAdapters();

                return;
            }
        }

        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }


    /*
     * =========================================================
     * PLAYBACK
     * =========================================================
     */

    private void toggleTimelinePlayback()
    {
        this.playbackController
                .togglePlayback();

        BlockbusterPreviewAnimationState
                .setPlaying(
                        this.playbackController
                                .isPlaying()
                );

        /*
         * ВАЖНО:
         * при паузе остаёмся на целочисленном tick.
         */
        if (
                !this.playbackController
                        .isPlaying()
        )
        {
            applyRecordFrame();

            applyAdapters();
        }
    }


    private void stepTimeline(
            int direction)
    {
        this.playbackController
                .step(
                        direction
                );

        BlockbusterPreviewAnimationState
                .setPlaying(
                        false
                );

        applyRecordFrame();

        applyAdapters();
    }


    /*
     * =========================================================
     * MOUSE DRAG
     * =========================================================
     */

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        /*
         * =====================================================
         * CHARACTER TIMELINE DRAG
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            if (
                    this.characterTimelineEditorController
                            .mouseClickMove(
                                    mouseX,
                                    mouseY,
                                    clickedMouseButton,
                                    this.width,
                                    this.height -
                                            getTimelineHeight(),
                                    0
                            )
            )
            {
                return;
            }
        }

        /*
         * =====================================================
         * EXISTING POSE TIMELINE KEYFRAME DRAG
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            if (
                    this.timelineController
                            .mouseClickMove(
                                    mouseX,
                                    mouseY,
                                    clickedMouseButton
                            )
            )
            {
                applyRecordFrame();

                applyAdapters();

                return;
            }
        }

        /*
         * =====================================================
         * TRANSFORM PANEL DRAG
         * =====================================================
         *
         * Только Pose Mode.
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
                        &&
                        this.transformDragging
                        &&
                        this.keyframeController
                                .getSelectedKeyframe() != null
                        &&
                        clickedMouseButton == 0
        )
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .mouseDragged(
                            mouseX,
                            mouseY,
                            this.keyframeController
                                    .getSelectedKeyframe()
                    );

            applyAdapters();

            super.mouseClickMove(
                    mouseX,
                    mouseY,
                    clickedMouseButton,
                    timeSinceLastClick
            );

            return;
        }

        /*
         * =====================================================
         * SCENE VIEWPORT DRAG
         * =====================================================
         */

        if (
                this.sceneViewport != null
                        &&
                        this.sceneViewport.mouseDragged(
                                mouseX,
                                mouseY
                        )
        )
        {
            return;
        }

        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }


    /*
     * =========================================================
     * MOUSE RELEASE
     * =========================================================
     */

    @Override
    protected void mouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        /*
         * Character Timeline.
         */
        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            this.characterTimelineEditorController
                    .mouseReleased();
        }

        /*
         * TransformPanel относится только к Pose Mode.
         */
        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .mouseReleased(
                            state
                    );
        }

        if (this.sceneViewport != null)
        {
            this.sceneViewport.mouseReleased(
                    mouseX,
                    mouseY,
                    state
            );
        }

        /*
         * Старый Timeline.
         */
        this.timelineController
                .mouseReleased();

        this.transformDragging =
                false;

        super.mouseReleased(
                mouseX,
                mouseY,
                state
        );
    }


    /*
     * =========================================================
     * MOUSE WHEEL
     * =========================================================
     */

    @Override
    public void handleMouseInput()
            throws IOException
    {
        super.handleMouseInput();

        if (isDeleteDialogOpen())
        {
            return;
        }

        int wheel =
                Mouse.getEventDWheel();

        if (wheel == 0)
        {
            return;
        }

        int mouseX =
                Mouse.getEventX()
                        *
                        this.width
                        /
                        this.mc.displayWidth;

        int mouseY =
                this.height
                        -
                        Mouse.getEventY()
                                *
                                this.height
                                /
                                this.mc.displayHeight
                        -
                        1;

        /*
         * =====================================================
         * CHARACTER TIMELINE
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
        )
        {
            if (
                    this.characterTimelineEditorController
                            .mouseScrolled(
                                    mouseX,
                                    mouseY,
                                    wheel > 0
                                            ? 1
                                            : -1,
                                    this.width,
                                    this.height -
                                            getTimelineHeight(),
                                    0
                            )
            )
            {
                return;
            }
        }

        /*
         * =====================================================
         * INTERPOLATION
         * =====================================================
         *
         * Только Pose.
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
                        &&
                        this.interpolationPanel != null
        )
        {
            if (
                    this.interpolationPanel
                            .mouseScrolled(
                                    mouseX,
                                    mouseY,
                                    wheel > 0
                                            ? 1
                                            : -1
                            )
            )
            {
                return;
            }
        }

        /*
         * =====================================================
         * SCENE VIEWPORT
         * =====================================================
         */

        if (
                this.sceneViewport != null
                        &&
                        this.sceneViewport
                                .mouseScrolled(
                                        mouseX,
                                        mouseY,
                                        wheel > 0
                                                ? 1
                                                : -1
                                )
        )
        {
            return;
        }

        /*
         * =====================================================
         * EXISTING POSE TIMELINE
         * =====================================================
         */

        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            if (
                    this.timelineController
                            .mouseScrolled(
                                    mouseX,
                                    mouseY,
                                    wheel > 0
                                            ? 1
                                            : -1,
                                    this.width,
                                    this.height,
                                    LEFT_PANEL_WIDTH,
                                    getSceneLength()
                            )
            )
            {
                return;
            }
        }
    }


    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws IOException
    {
        if (isDeleteDialogOpen())
        {
            if (
                    keyCode ==
                            Keyboard.KEY_ESCAPE
            )
            {
                clearPendingDelete();
            }

            return;
        }

        if (
                keyCode ==
                        Keyboard.KEY_ESCAPE
        )
        {
            BlockbusterPreviewAnimationState.clear();

            EmoticonsPreviewAnimationState.clear();

            clearPendingDelete();

            this.mc.displayGuiScreen(
                    null
            );

            return;
        }

        /*
         * Character Timeline:
         *
         * DELETE удаляет выбранный Character Key.
         *
         * Это пока прямое удаление без старого
         * Pose Delete Dialog, потому что CharacterKey
         * является отдельной системой.
         */
        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.CHARACTER
                        &&
                        (
                                keyCode ==
                                        Keyboard.KEY_DELETE
                                        ||
                                        keyCode ==
                                                Keyboard.KEY_BACK
                        )
        )
        {
            this.characterTimelineEditorController
                    .deleteSelectedKey();

            return;
        }

        /*
         * TransformPanel получает клавиатуру
         * только в Pose Mode.
         */
        if (
                this.editorModeController.getMode()
                        == EditorModeController.EditorMode.POSE
        )
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .keyTyped(
                            typedChar,
                            keyCode,
                            this.keyframeController
                                    .getSelectedKeyframe()
                    );
        }

        if (keyCode == Keyboard.KEY_LEFT)
        {
            stepTimeline(-1);

            return;
        }

        if (keyCode == Keyboard.KEY_RIGHT)
        {
            stepTimeline(1);

            return;
        }

        /*
         * SPACE намеренно ничего не делает.
         */
    }


    /*
     * =========================================================
     * UPDATE
     * =========================================================
     */

    @Override
    public void updateScreen()
    {
        if (this.sceneViewport != null)
        {
            this.sceneViewport
                    .updateCameraMovement(
                            this.mc
                    );
        }

        super.updateScreen();

        this.timeline.update();

        this.playbackController.update();

        BlockbusterPreviewAnimationState
                .setPlaying(
                        this.playbackController
                                .isPlaying()
                );

        /*
         * Record всегда вычисляется
         * по текущему целому кадру.
         */
        applyRecordFrame();

        /*
         * На паузе применяем только integer frame.
         *
         * Во время воспроизведения
         * drawPreview() отдельно использует
         * currentAnimationFrame для плавной
         * анимации.
         */
        if (
                !this.playbackController
                        .isPlaying()
        )
        {
            applyAdapters();
        }
    }


    /*
     * =========================================================
     * DELETE DIALOG
     * =========================================================
     */

    private boolean isDeleteDialogOpen()
    {
        return this.keyframeController
                .isDeletePending();
    }


    private int getDeleteDialogX()
    {
        return (
                this.width -
                        DELETE_DIALOG_WIDTH
        ) / 2;
    }


    private int getDeleteDialogY()
    {
        return (
                this.height -
                        DELETE_DIALOG_HEIGHT
        ) / 2;
    }


    private int getDeleteCancelX()
    {
        int dialogX =
                getDeleteDialogX();

        return dialogX +
                DELETE_DIALOG_WIDTH -
                DELETE_BUTTON_WIDTH * 2 -
                20;
    }


    private int getDeleteConfirmX()
    {
        return getDeleteCancelX() +
                DELETE_BUTTON_WIDTH +
                8;
    }


    private void drawDeleteDialog(
            int mouseX,
            int mouseY)
    {
        this.drawRect(
                0,
                0,
                this.width,
                this.height,
                0x99000000
        );

        int x =
                getDeleteDialogX();

        int y =
                getDeleteDialogY();

        this.drawRect(
                x - 4,
                y - 4,
                x +
                        DELETE_DIALOG_WIDTH +
                        4,
                y +
                        DELETE_DIALOG_HEIGHT +
                        4,
                0xDD070808
        );

        this.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + DELETE_DIALOG_HEIGHT,
                COLOR_PANEL
        );

        this.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + 26,
                COLOR_PANEL_DARK
        );

        this.drawRect(
                x,
                y,
                x + DELETE_DIALOG_WIDTH,
                y + 1,
                COLOR_CYAN
        );

        this.drawRect(
                x,
                y + 25,
                x + DELETE_DIALOG_WIDTH,
                y + 26,
                COLOR_BORDER
        );

        this.drawString(
                this.fontRenderer,
                "DELETE KEYFRAME",
                x + 10,
                y + 8,
                COLOR_TEXT
        );

        String message =
                "Delete keyframe at frame "
                        +
                        this.keyframeController
                                .getPendingDeleteFrame()
                        +
                        "?";

        this.drawString(
                this.fontRenderer,
                message,
                x + 10,
                y + 39,
                COLOR_TEXT_SECONDARY
        );

        int cancelX =
                getDeleteCancelX();

        int confirmX =
                getDeleteConfirmX();

        int buttonY =
                y +
                        DELETE_DIALOG_HEIGHT -
                        DELETE_BUTTON_HEIGHT -
                        10;

        boolean cancelHovered =
                mouseX >= cancelX &&
                        mouseX <
                                cancelX +
                                        DELETE_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        DELETE_BUTTON_HEIGHT;

        boolean confirmHovered =
                mouseX >= confirmX &&
                        mouseX <
                                confirmX +
                                        DELETE_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        DELETE_BUTTON_HEIGHT;

        drawDialogButton(
                cancelX,
                buttonY,
                "Cancel",
                cancelHovered,
                false
        );

        drawDialogButton(
                confirmX,
                buttonY,
                "Delete",
                confirmHovered,
                true
        );
    }


    private void drawDialogButton(
            int x,
            int y,
            String text,
            boolean hovered,
            boolean destructive)
    {
        int background;

        if (destructive)
        {
            background =
                    hovered
                            ? 0xFF8A3A3A
                            : 0xFF5C3030;
        }
        else
        {
            background =
                    hovered
                            ? COLOR_PANEL_HOVER
                            : COLOR_PANEL_LIGHT;
        }

        this.drawRect(
                x,
                y,
                x + DELETE_BUTTON_WIDTH,
                y + DELETE_BUTTON_HEIGHT,
                background
        );

        this.drawRect(
                x,
                y,
                x + DELETE_BUTTON_WIDTH,
                y + 1,
                destructive
                        ? 0xFFB75A5A
                        : hovered
                        ? COLOR_CYAN
                        : COLOR_BORDER
        );

        this.drawRect(
                x,
                y +
                        DELETE_BUTTON_HEIGHT -
                        1,
                x + DELETE_BUTTON_WIDTH,
                y +
                        DELETE_BUTTON_HEIGHT,
                COLOR_BORDER_DARK
        );

        int textWidth =
                this.fontRenderer
                        .getStringWidth(
                                text
                        );

        this.drawString(
                this.fontRenderer,
                text,
                x +
                        (
                                DELETE_BUTTON_WIDTH -
                                        textWidth
                        ) / 2,
                y + 6,
                destructive && hovered
                        ? 0xFFFFFFFF
                        : COLOR_TEXT
        );
    }


    private void handleDeleteDialogClick(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        int dialogX =
                getDeleteDialogX();

        int dialogY =
                getDeleteDialogY();

        int dialogRight =
                dialogX +
                        DELETE_DIALOG_WIDTH;

        int dialogBottom =
                dialogY +
                        DELETE_DIALOG_HEIGHT;

        int buttonY =
                dialogY +
                        DELETE_DIALOG_HEIGHT -
                        DELETE_BUTTON_HEIGHT -
                        10;

        int cancelX =
                getDeleteCancelX();

        int confirmX =
                getDeleteConfirmX();

        if (mouseButton != 0)
        {
            if (
                    mouseX < dialogX ||
                            mouseX > dialogRight ||
                            mouseY < dialogY ||
                            mouseY > dialogBottom
            )
            {
                clearPendingDelete();
            }

            return;
        }

        if (
                mouseX >= confirmX &&
                        mouseX <
                                confirmX +
                                        DELETE_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        DELETE_BUTTON_HEIGHT
        )
        {
            confirmDeleteKeyframe();

            return;
        }

        if (
                mouseX >= cancelX &&
                        mouseX <
                                cancelX +
                                        DELETE_BUTTON_WIDTH &&
                        mouseY >= buttonY &&
                        mouseY <
                                buttonY +
                                        DELETE_BUTTON_HEIGHT
        )
        {
            clearPendingDelete();

            return;
        }

        if (
                mouseX < dialogX ||
                        mouseX > dialogRight ||
                        mouseY < dialogY ||
                        mouseY > dialogBottom
        )
        {
            clearPendingDelete();
        }
    }


    private void confirmDeleteKeyframe()
    {
        if (
                !this.keyframeController
                        .isDeletePending()
        )
        {
            clearPendingDelete();

            return;
        }

        if (
                this.keyframeController
                        .confirmDelete()
        )
        {
            applyAdapters();
        }
    }


    private void clearPendingDelete()
    {
        this.keyframeController
                .clearPendingDelete();
    }


    /*
     * =========================================================
     * PUBLIC ACCESSORS
     * =========================================================
     */

    public AnimationKeyframe
    getSelectedKeyframe()
    {
        return this.keyframeController
                .getSelectedKeyframe();
    }


    public AnimationBone
    getSelectedBone()
    {
        return this.actorPreviewController
                .getSelectedBone(
                        this.keyframeController
                );
    }


    public AnimationTransform
    getCurrentTransform()
    {
        return this.actorPreviewController
                .getCurrentTransform(
                        this.keyframeController,
                        this.playbackController
                                .getCurrentFrame()
                );
    }


    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */

    private boolean isMouseInside(
            int x,
            int y,
            int left,
            int top,
            int width,
            int height)
    {
        return x >= left &&
                x < left + width &&
                y >= top &&
                y < top + height;
    }


    private boolean isMouseInside(
            int left,
            int top,
            int width,
            int height)
    {
        int mouseX =
                Mouse.getX()
                        *
                        this.width
                        /
                        this.mc.displayWidth;

        int mouseY =
                this.height
                        -
                        Mouse.getY()
                                *
                                this.height
                                /
                                this.mc.displayHeight
                        -
                        1;

        return isMouseInside(
                mouseX,
                mouseY,
                left,
                top,
                width,
                height
        );
    }


    private void updateSceneViewportPosition()
    {
        if (
                this.sceneState == null ||
                        this.sceneViewport == null
        )
        {
            return;
        }

        this.sceneViewport.setScenePosition(
                this.sceneState.getSceneX(),
                this.sceneState.getSceneY(),
                this.sceneState.getSceneZ()
        );
    }


    @Override
    public void onGuiClosed()
    {
        clearPendingDelete();

        this.editorModeController
                .closeDropdown();

        this.characterTimelineEditorController
                .mouseReleased();

        this.timelineController
                .mouseReleased();

        this.playbackController
                .pause();

        BlockbusterPreviewAnimationState.clear();

        EmoticonsPreviewAnimationState.clear();

        super.onGuiClosed();
    }
}
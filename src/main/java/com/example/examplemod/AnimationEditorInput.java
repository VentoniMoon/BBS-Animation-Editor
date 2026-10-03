package com.example.examplemod;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Обработка ввода Animation Editor.
 *
 * Здесь находятся:
 * - mouseClicked
 * - mouseClickMove
 * - mouseReleased
 * - handleMouseInput
 * - keyTyped
 * - управление playback через кнопки/клавиши
 * - удаление keyframe
 * - сохранение редактора
 *
 * Отрисовка и состояние редактора остаются в AnimationEditorScreen.
 */
public class AnimationEditorInput
{
    private final AnimationEditorScreen screen;

    private final EditorModeController editorModeController;
    private final EditorThemeController editorThemeController;

    private final EditorSceneState sceneState;
    private final EditorSceneViewport sceneViewport;

    private final BlockbusterCharacterGuiBridge characterGuiBridge;
    private final AnimationDeleteDialog deleteDialog;

    private final EditorTimeline timeline;
    private final EditorPlaybackController playbackController;

    private final CharacterEditorPanel characterEditorPanel;
    private final CharacterTimelineEditorController characterTimelineEditorController;

    private final EditorKeyframeController keyframeController;
    private final EditorTimelineController timelineController;
    private final EditorActorPreviewController actorPreviewController;

    private final EditorRecordController recordController;
    private final InterpolationPanel interpolationPanel;

    private final BodyPartsEditorController bodyPartsController;
    private final BodyPartsEditorPanel bodyPartsEditorPanel;
    private final BodyPartsTimelineController bodyPartsTimelineController;

    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    private final EditorSaveInputController saveInputController;

    /*
     * =========================================================
     * INPUT STATE
     * =========================================================
     */

    private boolean sceneDropdownOpen;
    private boolean transformDragging;

    public AnimationEditorInput(
            AnimationEditorScreen screen,
            EditorModeController editorModeController,
            EditorThemeController editorThemeController,
            EditorSceneState sceneState,
            EditorSceneViewport sceneViewport,
            BlockbusterCharacterGuiBridge characterGuiBridge,
            AnimationDeleteDialog deleteDialog,
            EditorTimeline timeline,
            EditorPlaybackController playbackController,
            CharacterEditorPanel characterEditorPanel,
            CharacterTimelineEditorController characterTimelineEditorController,
            EditorKeyframeController keyframeController,
            EditorTimelineController timelineController,
            EditorActorPreviewController actorPreviewController,
            EditorRecordController recordController,
            InterpolationPanel interpolationPanel,
            BodyPartsEditorController bodyPartsController,
            BodyPartsEditorPanel bodyPartsEditorPanel,
            BodyPartsTimelineController bodyPartsTimelineController)
    {
        this.screen = screen;
        this.editorModeController = editorModeController;
        this.editorThemeController = editorThemeController;
        this.sceneState = sceneState;
        this.sceneViewport = sceneViewport;
        this.characterGuiBridge = characterGuiBridge;
        this.deleteDialog = deleteDialog;
        this.timeline = timeline;
        this.playbackController = playbackController;
        this.characterEditorPanel = characterEditorPanel;
        this.characterTimelineEditorController =
                characterTimelineEditorController;
        this.keyframeController = keyframeController;
        this.timelineController = timelineController;
        this.actorPreviewController = actorPreviewController;
        this.recordController = recordController;
        this.interpolationPanel = interpolationPanel;
        this.bodyPartsController = bodyPartsController;
        this.bodyPartsEditorPanel = bodyPartsEditorPanel;
        this.bodyPartsTimelineController = bodyPartsTimelineController;

        /*
         * Save input deliberately remains outside
         * AnimationEditorScreen.
         */
        this.saveInputController =
                new EditorSaveInputController(
                        sceneState
                );
    }

    /*
     * =========================================================
     * SAVE ACCESS
     * =========================================================
     */

    public EditorSaveInputController getSaveInputController()
    {
        return this.saveInputController;
    }

    public boolean isDirty()
    {
        return this.saveInputController != null
                && this.saveInputController.isDirty();
    }

    public void markDirty()
    {
        if (this.saveInputController != null)
        {
            this.saveInputController.markDirty();
        }
    }

    public void markClean()
    {
        if (this.saveInputController != null)
        {
            this.saveInputController.markClean();
        }
    }

    /*
     * =========================================================
     * SCENE DROPDOWN
     * =========================================================
     */

    public boolean isSceneDropdownOpen()
    {
        return this.sceneDropdownOpen;
    }

    public void setSceneDropdownOpen(boolean value)
    {
        this.sceneDropdownOpen = value;
    }

    /*
     * =========================================================
     * TRANSFORM DRAGGING
     * =========================================================
     */

    public boolean isTransformDragging()
    {
        return this.transformDragging;
    }

    public void setTransformDragging(boolean value)
    {
        this.transformDragging = value;
    }

    /*
     * =========================================================
     * RESET INPUT
     * =========================================================
     */

    public void resetInputState()
    {
        this.sceneDropdownOpen = false;
        this.transformDragging = false;
    }

    /*
     * =========================================================
     * MOUSE CLICKED
     * =========================================================
     */

    public void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws IOException
    {
        /*
         * =========================================================
         * DELETE DIALOG
         * =========================================================
         */

        if (isDeleteDialogOpen())
        {
            int result =
                    this.deleteDialog.mouseClicked(
                            this.screen.width,
                            this.screen.height,
                            mouseX,
                            mouseY,
                            mouseButton
                    );

            if (result == 2)
            {
                confirmDeleteKeyframe();
            }
            else if (result == 1 || result == 3)
            {
                clearPendingDelete();
            }

            return;
        }

        this.transformDragging = false;

        /*
         * =========================================================
         * THEME DROPDOWN
         * =========================================================
         */

        if (this.editorThemeController.isMouseOverButton(
                this.screen.width,
                mouseX,
                mouseY))
        {
            if (mouseButton == 0)
            {
                this.editorThemeController.toggleDropdown();
                this.editorModeController.closeDropdown();
                this.sceneDropdownOpen = false;
            }

            return;
        }

        if (this.editorThemeController.isDropdownOpen())
        {
            EditorTheme selectedTheme =
                    this.editorThemeController.getThemeAtMouse(
                            this.screen.width,
                            mouseX,
                            mouseY
                    );

            if (selectedTheme != null)
            {
                this.editorThemeController.setTheme(selectedTheme);
                return;
            }

            this.editorThemeController.closeDropdown();
        }

        /*
         * =========================================================
         * MODE DROPDOWN
         * =========================================================
         */

        if (this.editorModeController.isMouseOverButton(
                this.screen.width,
                mouseX,
                mouseY))
        {
            if (mouseButton == 0)
            {
                this.editorModeController.toggleDropdown();
                this.editorThemeController.closeDropdown();
                this.sceneDropdownOpen = false;
            }

            return;
        }

        if (this.editorModeController.isDropdownOpen())
        {
            EditorModeController.EditorMode selectedMode =
                    this.editorModeController.getModeAtMouse(
                            this.screen.width,
                            mouseX,
                            mouseY
                    );

            if (selectedMode != null)
            {
                this.editorModeController.setMode(selectedMode);

                this.keyframeController.resetSelectionOnly();

                this.transformDragging = false;

                this.screen.syncCharacterTimelineActor();
                this.screen.syncCharacterEditor();

                return;
            }

            this.editorModeController.closeDropdown();
        }

        /*
         * =========================================================
         * SCENE BUTTON
         * =========================================================
         */

        int sceneButtonX = 175;
        int sceneButtonWidth = 180;

        if (mouseX >= sceneButtonX
                && mouseX <= sceneButtonX + sceneButtonWidth
                && mouseY >= 3
                && mouseY <=
                AnimationEditorScreen.TOP_BAR_HEIGHT - 3)
        {
            this.sceneDropdownOpen =
                    !this.sceneDropdownOpen;

            this.editorModeController.closeDropdown();
            this.editorThemeController.closeDropdown();

            return;
        }

        /*
         * =========================================================
         * SCENE DROPDOWN
         * =========================================================
         */

        if (this.sceneDropdownOpen)
        {
            int x = 175;
            int y =
                    AnimationEditorScreen.TOP_BAR_HEIGHT + 2;
            int dropdownWidth = 180;
            int rowHeight = 19;

            int count =
                    Math.min(
                            8,
                            this.sceneState
                                    .getSceneFiles()
                                    .size()
                    );

            if (mouseX >= x
                    && mouseX <= x + dropdownWidth
                    && mouseY >= y
                    && mouseY < y + count * rowHeight)
            {
                int sceneIndex =
                        (mouseY - y) / rowHeight;

                if (sceneIndex >= 0
                        && sceneIndex <
                        this.sceneState
                                .getSceneFiles()
                                .size())
                {
                    /*
                     * Loading another scene changes the editor
                     * context completely.
                     */
                    this.screen.loadScene(sceneIndex);

                    this.markClean();
                    this.resetInputState();

                    return;
                }
            }

            this.sceneDropdownOpen = false;
            this.editorModeController.closeDropdown();
            this.editorThemeController.closeDropdown();
        }

        /*
         * =========================================================
         * CHARACTER PANEL
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            int rightPanelWidth = 185;

            int panelX =
                    this.screen.width -
                            rightPanelWidth;

            int panelY =
                    AnimationEditorScreen.TOP_BAR_HEIGHT;

            int panelBottom =
                    this.screen.height -
                            this.screen.getTimelineHeight();

            if (mouseX >= panelX
                    && mouseX < this.screen.width
                    && mouseY >= panelY
                    && mouseY < panelBottom)
            {
                if (this.characterEditorPanel.mouseClicked(
                        mouseX,
                        mouseY,
                        mouseButton))
                {
                    this.markDirty();
                    return;
                }
            }
        }

        /*
         * =========================================================
         * INTERPOLATION PANEL
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE
                && this.interpolationPanel.mouseClicked(
                mouseX,
                mouseY,
                mouseButton))
        {
            this.markDirty();
            return;
        }

        /*
         * =========================================================
         * BODY PART BONE LIST
         * =========================================================
         */
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            int actorTop = AnimationEditorScreen.TOP_BAR_HEIGHT;
            int actorBottom = actorTop + AnimationEditorScreen.ACTOR_PANEL_HEIGHT;

            if (mouseX >= 0 &&
                    mouseX <= AnimationEditorScreen.LEFT_PANEL_WIDTH &&
                    mouseY >= actorTop + 30 &&
                    mouseY < actorBottom)
            {
                int relativeY = mouseY - actorTop - 30;
                int boneIndex = relativeY / 20;

                if (boneIndex >= 0 &&
                        boneIndex < this.bodyPartsController.getActorBones().size())
                {
                    this.bodyPartsController.setSelectedActorBone(boneIndex);
                    return;
                }
            }

            int panelX = this.screen.width - BodyPartsEditorPanel.WIDTH;
            int panelY = AnimationEditorScreen.TOP_BAR_HEIGHT;
            int panelBottom = this.screen.height - this.bodyPartsTimelineController.getTimelineHeight();

            if (mouseX >= panelX && mouseX < this.screen.width &&
                    mouseY >= panelY && mouseY < panelBottom)
            {
                if (this.bodyPartsEditorPanel.mouseClicked(
                        mouseX, mouseY, mouseButton, this.screen.getSceneLength()))
                {
                    this.markDirty();
                    return;
                }
            }
        }

        /*
         * =========================================================
         * ACTOR LIST
         * =========================================================
         */

        int actorTop =
                AnimationEditorScreen.TOP_BAR_HEIGHT;

        int actorBottom =
                actorTop +
                        AnimationEditorScreen.ACTOR_PANEL_HEIGHT;

        final int actorRowHeight = 31;

        if (mouseX >= 0
                && mouseX <=
                AnimationEditorScreen.LEFT_PANEL_WIDTH
                && mouseY >= actorTop + 30
                && mouseY < actorBottom)
        {
            int relativeY =
                    mouseY -
                            actorTop -
                            30;

            int actorIndex =
                    relativeY /
                            actorRowHeight;

            if (actorIndex >= 0
                    && actorIndex <
                    this.screen
                            .getSceneActors()
                            .size())
            {
                this.screen.selectActor(actorIndex);
                return;
            }
        }

        /*
         * =========================================================
         * PREVIEW CONTROLS
         * =========================================================
         */

        int previewBottom =
                this.screen.height -
                        this.screen.getTimelineHeight();

        int previewRight =
                this.screen.width - 185;

        int totalWidth =
                AnimationEditorScreen.PREVIEW_BUTTON_WIDTH * 3
                        + AnimationEditorScreen.PREVIEW_BUTTON_GAP * 2;

        int centerX =
                AnimationEditorScreen.LEFT_PANEL_WIDTH
                        + (previewRight -
                        AnimationEditorScreen.LEFT_PANEL_WIDTH) / 2;

        int startX =
                centerX -
                        totalWidth / 2;

        int buttonY =
                previewBottom -
                        AnimationEditorScreen.PREVIEW_BUTTON_HEIGHT -
                        12;

        if (isMouseInside(
                mouseX,
                mouseY,
                startX,
                buttonY,
                AnimationEditorScreen.PREVIEW_BUTTON_WIDTH,
                AnimationEditorScreen.PREVIEW_BUTTON_HEIGHT))
        {
            stepTimeline(-1);
            return;
        }

        int playX =
                startX +
                        AnimationEditorScreen.PREVIEW_BUTTON_WIDTH +
                        AnimationEditorScreen.PREVIEW_BUTTON_GAP;

        if (isMouseInside(
                mouseX,
                mouseY,
                playX,
                buttonY,
                AnimationEditorScreen.PREVIEW_BUTTON_WIDTH,
                AnimationEditorScreen.PREVIEW_BUTTON_HEIGHT))
        {
            toggleTimelinePlayback();
            return;
        }

        int nextX =
                playX +
                        AnimationEditorScreen.PREVIEW_BUTTON_WIDTH +
                        AnimationEditorScreen.PREVIEW_BUTTON_GAP;

        if (isMouseInside(
                mouseX,
                mouseY,
                nextX,
                buttonY,
                AnimationEditorScreen.PREVIEW_BUTTON_WIDTH,
                AnimationEditorScreen.PREVIEW_BUTTON_HEIGHT))
        {
            stepTimeline(1);
            return;
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            AnimationKeyframe selectedKeyframe =
                    this.keyframeController
                            .getSelectedKeyframe();

            if (selectedKeyframe != null)
            {
                int transformPanelX =
                        this.screen.width - 185;

                int transformPanelY =
                        AnimationEditorScreen.TOP_BAR_HEIGHT + 10;

                int transformPanelWidth = 175;
                int transformPanelHeight = 245;

                if (mouseX >= transformPanelX
                        && mouseX <=
                        transformPanelX +
                                transformPanelWidth
                        && mouseY >= transformPanelY
                        && mouseY <=
                        transformPanelY +
                                transformPanelHeight)
                {
                    if (this.actorPreviewController
                            .getTransformPanel()
                            .mouseClicked(
                                    mouseX,
                                    mouseY,
                                    mouseButton,
                                    selectedKeyframe))
                    {
                        this.transformDragging = true;
                        this.markDirty();
                        return;
                    }
                }
            }
        }

        /*
         * =========================================================
         * BODY PART TRANSFORM PANEL
         * =========================================================
         */
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            AnimationKeyframe selectedKeyframe =
                    this.bodyPartsController.getKeyframeController().getSelectedKeyframe();

            if (selectedKeyframe != null &&
                    this.bodyPartsEditorPanel.mouseClickedTransform(
                            mouseX, mouseY, mouseButton))
            {
                this.transformDragging = true;
                this.markDirty();
                return;
            }
        }

        /*
         * =========================================================
         * VIEWPORT
         * =========================================================
         */

        if (this.sceneViewport != null
                && this.sceneViewport.mousePressed(
                mouseX,
                mouseY,
                mouseButton))
        {
            return;
        }

        /*
         * =========================================================
         * CHARACTER TIMELINE
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            this.screen.syncCharacterTimelineActor();

            if (this.characterTimelineEditorController.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton,
                    this.screen.width,
                    this.screen.height -
                            this.screen.getTimelineHeight(),
                    0))
            {
                this.markDirty();
                return;
            }
        }

        /*
         * =========================================================
         * BODY PARTS TIMELINE
         * =========================================================
         */
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            if (this.bodyPartsTimelineController.mouseClicked(
                    mouseX, mouseY, this.screen.width, this.screen.height,
                    this.screen.getSceneLength()))
            {
                this.playbackController.setCurrentFrame(
                        this.bodyPartsController.getTimeline().getTick()
                );

                this.markDirty();
                return;
            }
        }

        /*
         * =========================================================
         * BODY PARTS TIMELINE DRAG
         * =========================================================
         */
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            if (this.bodyPartsTimelineController.mouseClickMove(
                    mouseX, mouseY, clickedMouseButton,
                    this.screen.width, this.screen.height,
                    this.screen.getSceneLength()))
            {
                this.playbackController.setCurrentFrame(
                        this.bodyPartsController.getTimeline().getTick()
                );
                this.markDirty();
                return;
            }
        }

        /*
         * =========================================================
         * POSE TIMELINE
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            if (this.timelineController.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton,
                    this.screen.width,
                    this.screen.height,
                    AnimationEditorScreen.LEFT_PANEL_WIDTH,
                    this.screen.getSceneLength()))
            {
                this.markDirty();

                this.screen.applyRecordFrame();
                this.screen.applyAdapters();

                return;
            }
        }

        this.screen.callSuperMouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    /*
     * =========================================================
     * MOUSE DRAG
     * =========================================================
     */

    public void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        /*
         * =========================================================
         * CHARACTER TIMELINE
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            if (this.characterTimelineEditorController.mouseClickMove(
                    mouseX,
                    mouseY,
                    clickedMouseButton,
                    this.screen.width,
                    this.screen.height -
                            this.screen.getTimelineHeight(),
                    0))
            {
                this.markDirty();
                return;
            }
        }

        /*
         * =========================================================
         * POSE TIMELINE
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            if (this.timelineController.mouseClickMove(
                    mouseX,
                    mouseY,
                    clickedMouseButton))
            {
                this.markDirty();

                this.screen.applyRecordFrame();
                this.screen.applyAdapters();

                return;
            }
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS
                && this.transformDragging
                && this.bodyPartsController.getKeyframeController().getSelectedKeyframe() != null
                && clickedMouseButton == 0)
        {
            this.bodyPartsEditorPanel.mouseDraggedTransform(mouseX, mouseY);
            this.markDirty();
            return;
        }

        /*
         * =========================================================
         * TRANSFORM DRAG
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE
                && this.transformDragging
                && this.keyframeController
                .getSelectedKeyframe() != null
                && clickedMouseButton == 0)
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .mouseDragged(
                            mouseX,
                            mouseY,
                            this.keyframeController
                                    .getSelectedKeyframe()
                    );

            this.markDirty();

            this.screen.applyAdapters();

            this.screen.callSuperMouseClickMove(
                    mouseX,
                    mouseY,
                    clickedMouseButton,
                    timeSinceLastClick
            );

            return;
        }

        /*
         * =========================================================
         * VIEWPORT
         * =========================================================
         */

        if (this.sceneViewport != null
                && this.sceneViewport.mouseDragged(
                mouseX,
                mouseY))
        {
            return;
        }

        this.screen.callSuperMouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }

    /*
     * =========================================================
     * MOUSE RELEASED
     * =========================================================
     */

    public void mouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            this.characterTimelineEditorController.mouseReleased();
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .mouseReleased(state);
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            this.bodyPartsTimelineController.mouseReleased();
            this.bodyPartsEditorPanel.mouseReleased(state);
        }

        if (this.sceneViewport != null)
        {
            this.sceneViewport.mouseReleased(
                    mouseX,
                    mouseY,
                    state
            );
        }

        this.timelineController.mouseReleased();

        this.transformDragging = false;

        this.screen.callSuperMouseReleased(
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

    public void handleMouseInput()
            throws IOException
    {
        /*
         * In Forge/Minecraft 1.12.2 GuiScreen.handleMouseInput()
         * consumes the current LWJGL event. For the editor we handle
         * wheel events explicitly BEFORE calling vanilla.
         *
         * IMPORTANT:
         * Mouse.getDWheel() consumes the accumulated wheel value.
         * Using it here made the result depend on the order in which
         * vanilla processed the event. Mouse.getEventDWheel() is the
         * value belonging to the event currently being dispatched.
         */
        int wheel =
                Mouse.getEventDWheel();

        if (wheel != 0)
        {
            if (isDeleteDialogOpen())
            {
                return;
            }

            int mouseX =
                    Mouse.getEventX()
                            * this.screen.width
                            / this.screen.mc.displayWidth;

            int mouseY =
                    this.screen.height
                            - Mouse.getEventY()
                            * this.screen.height
                            / this.screen.mc.displayHeight
                            - 1;

            int direction =
                    wheel > 0
                            ? 1
                            : -1;

            /*
             * =====================================================
             * CHARACTER MODE
             * =====================================================
             *
             * Handle the Character timeline first, then the
             * Character inspector. Do NOT call vanilla for a wheel
             * event: there is nothing in the vanilla GUI that the
             * editor needs from this wheel event.
             */
            if (this.editorModeController.getMode()
                    == EditorModeController.EditorMode.CHARACTER)
            {
                if (this.characterTimelineEditorController
                        .mouseScrolled(
                                mouseX,
                                mouseY,
                                direction,
                                this.screen.width,
                                this.screen.height -
                                        this.screen.getTimelineHeight(),
                                0))
                {
                    return;
                }

                /*
                 * Character inspector wheel is handled from
                 * CharacterEditorPanel.draw(), where Mouse.getDWheel()
                 * is still available for the current frame.
                 */
            }

            /*
             * =====================================================
             * INTERPOLATION / VIEWPORT / POSE TIMELINE
             * =====================================================
             */
            if (this.editorModeController.getMode()
                    == EditorModeController.EditorMode.POSE
                    && this.interpolationPanel.mouseScrolled(
                    mouseX,
                    mouseY,
                    direction))
            {
                return;
            }

            if (this.sceneViewport != null
                    && this.sceneViewport.mouseScrolled(
                    mouseX,
                    mouseY,
                    direction))
            {
                return;
            }

            if (this.editorModeController.getMode()
                    == EditorModeController.EditorMode.POSE)
            {
                this.timelineController.mouseScrolled(
                        mouseX,
                        mouseY,
                        direction,
                        this.screen.width,
                        this.screen.height,
                        AnimationEditorScreen.LEFT_PANEL_WIDTH,
                        this.screen.getSceneLength()
                );
            }

            return;
        }

        /*
         * Non-wheel mouse events still go through vanilla so that
         * GuiScreen can dispatch the normal mouse button events.
         */
        this.screen.callSuperHandleMouseInput();
    }

    /*
     * =========================================================
     * KEYBOARD
     * =========================================================
     */

    public void keyTyped(
            char typedChar,
            int keyCode)
            throws IOException
    {
        /*
         * =========================================================
         * SAVE
         * =========================================================
         *
         * Ctrl + S must be handled before the other keyboard
         * shortcuts.
         */

        if (this.saveInputController != null
                && this.saveInputController.handleKeyTyped(
                typedChar,
                keyCode))
        {
            return;
        }

        /*
         * =========================================================
         * CHARACTER ANIMATION SETUP INPUT
         * =========================================================
         *
         * Handle the picker/search before global Escape.
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER
                && this.characterEditorPanel.keyTyped(
                typedChar,
                keyCode))
        {
            this.markDirty();
            return;
        }

                /*
         * =========================================================
         * DELETE DIALOG
         * =========================================================
         */

        if (isDeleteDialogOpen())
        {
            if (keyCode == Keyboard.KEY_ESCAPE)
            {
                clearPendingDelete();
            }

            return;
        }

        /*
         * =========================================================
         * ESCAPE
         * =========================================================
         */

        if (keyCode == Keyboard.KEY_ESCAPE)
        {
            BlockbusterPreviewAnimationState.clear();
            EmoticonsPreviewAnimationState.clear();

            clearPendingDelete();

            this.screen.closeEditor();

            return;
        }

        /*
         * =========================================================
         * CHARACTER MODE DELETE
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER
                && (keyCode == Keyboard.KEY_DELETE
                || keyCode == Keyboard.KEY_BACK))
        {
            this.characterTimelineEditorController
                    .deleteSelectedKey();

            this.markDirty();

            return;
        }

        /*
         * =========================================================
         * POSE TRANSFORM INPUT
         * =========================================================
         */

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            AnimationKeyframe selectedKeyframe =
                    this.keyframeController
                            .getSelectedKeyframe();

            if (selectedKeyframe != null)
            {
                /*
                 * Store the transform before the TransformPanel
                 * receives the keyboard event.
                 */
                float beforePX =
                        selectedKeyframe.getTransform()
                                .getPositionX();

                float beforePY =
                        selectedKeyframe.getTransform()
                                .getPositionY();

                float beforePZ =
                        selectedKeyframe.getTransform()
                                .getPositionZ();

                float beforeRX =
                        selectedKeyframe.getTransform()
                                .getRotationX();

                float beforeRY =
                        selectedKeyframe.getTransform()
                                .getRotationY();

                float beforeRZ =
                        selectedKeyframe.getTransform()
                                .getRotationZ();

                float beforeSX =
                        selectedKeyframe.getTransform()
                                .getScaleX();

                float beforeSY =
                        selectedKeyframe.getTransform()
                                .getScaleY();

                float beforeSZ =
                        selectedKeyframe.getTransform()
                                .getScaleZ();

                this.actorPreviewController
                        .getTransformPanel()
                        .keyTyped(
                                typedChar,
                                keyCode,
                                selectedKeyframe
                        );

                /*
                 * Only mark the document dirty when the actual
                 * animation transform changed.
                 */
                boolean changed =
                        beforePX != selectedKeyframe
                                .getTransform()
                                .getPositionX()
                                || beforePY != selectedKeyframe
                                .getTransform()
                                .getPositionY()
                                || beforePZ != selectedKeyframe
                                .getTransform()
                                .getPositionZ()
                                || beforeRX != selectedKeyframe
                                .getTransform()
                                .getRotationX()
                                || beforeRY != selectedKeyframe
                                .getTransform()
                                .getRotationY()
                                || beforeRZ != selectedKeyframe
                                .getTransform()
                                .getRotationZ()
                                || beforeSX != selectedKeyframe
                                .getTransform()
                                .getScaleX()
                                || beforeSY != selectedKeyframe
                                .getTransform()
                                .getScaleY()
                                || beforeSZ != selectedKeyframe
                                .getTransform()
                                .getScaleZ();

                if (changed)
                {
                    this.markDirty();
                    this.screen.applyAdapters();
                }
            }
        }

        /*
         * =========================================================
         * TIMELINE STEP
         * =========================================================
         */

        if (keyCode == Keyboard.KEY_LEFT)
        {
            stepTimeline(-1);
            return;
        }

        if (keyCode == Keyboard.KEY_RIGHT)
        {
            stepTimeline(1);
        }
    }

    /*
     * =========================================================
     * PLAYBACK
     * =========================================================
     */

    private void toggleTimelinePlayback()
    {
        this.playbackController.togglePlayback();

        BlockbusterPreviewAnimationState.setPlaying(
                this.playbackController.isPlaying()
        );

        if (!this.playbackController.isPlaying())
        {
            /*
             * Preserve the existing pause behaviour:
             * return to the exact integer frame and then apply
             * the adapters.
             */
            this.screen.applyRecordFrame();
            this.screen.applyAdapters();
        }
    }

    private void stepTimeline(int direction)
    {
        this.playbackController.step(direction);

        BlockbusterPreviewAnimationState.setPlaying(false);

        /*
         * Stepping is navigation, not editing.
         */
        this.screen.applyRecordFrame();
        this.screen.applyAdapters();
    }

    /*
     * =========================================================
     * DELETE
     * =========================================================
     */

    private boolean isDeleteDialogOpen()
    {
        return this.keyframeController
                .isDeletePending();
    }

    private void confirmDeleteKeyframe()
    {
        if (!this.keyframeController.isDeletePending())
        {
            clearPendingDelete();
            return;
        }

        if (this.keyframeController.confirmDelete())
        {
            this.markDirty();

            this.screen.applyAdapters();
        }

        this.deleteDialog.close();
    }

    private void clearPendingDelete()
    {
        this.keyframeController
                .clearPendingDelete();

        this.deleteDialog.close();
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
        return x >= left
                && x < left + width
                && y >= top
                && y < top + height;
    }
}
package com.example.examplemod;

import net.minecraft.client.Minecraft;

import mchorse.blockbuster.common.entity.EntityActor;

/**
 * Полный контроллер Character Mode.
 *
 * Отвечает за:
 *
 * CharacterEditorController
 * CharacterEditorPanel
 * Character Timeline
 * Blockbuster Character GUI Bridge
 *
 * Runtime EntityActor сюда не добавляется как часть Scene Actor.
 * Он только временно передаётся панели для открытия оригинальных GUI.
 */
public class CharacterModeController
{
    private final CharacterEditorController characterController;

    private final CharacterEditorPanel characterEditorPanel;

    private final CharacterTimelineEditorController
            characterTimelineController;

    private final BlockbusterCharacterGuiBridge
            guiBridge;

    private BlockbusterSceneActorData selectedActor;

    private EntityActor runtimeActor;


    public CharacterModeController(
            EditorPlaybackController playbackController)
    {
        this.characterController =
                new CharacterEditorController(
                        playbackController
                );

        this.characterEditorPanel =
                new CharacterEditorPanel(
                        this.characterController
                );

        this.characterTimelineController =
                new CharacterTimelineEditorController();

        this.guiBridge =
                new BlockbusterCharacterGuiBridge();

        this.selectedActor = null;
        this.runtimeActor = null;

        /*
         * Bridge принадлежит Character Mode.
         */
        this.characterEditorPanel
                .setGuiBridge(
                        this.guiBridge
                );
    }


    public CharacterEditorController
    getCharacterController()
    {
        return this.characterController;
    }


    public CharacterEditorPanel
    getCharacterEditorPanel()
    {
        return this.characterEditorPanel;
    }


    public CharacterTimelineEditorController
    getCharacterTimelineController()
    {
        return this.characterTimelineController;
    }


    public BlockbusterCharacterGuiBridge
    getGuiBridge()
    {
        return this.guiBridge;
    }


    public BlockbusterSceneActorData
    getSelectedActor()
    {
        return this.selectedActor;
    }


    public EntityActor
    getRuntimeActor()
    {
        return this.runtimeActor;
    }


    /**
     * Устанавливает выбранного Scene Actor.
     */
    public void setSelectedActor(
            BlockbusterSceneActorData actor)
    {
        this.selectedActor = actor;

        this.characterTimelineController
                .setSelectedActor(
                        actor
                );

        this.characterEditorPanel
                .setSelectedActor(
                        actor
                );

        this.characterEditorPanel
                .setRuntimeActor(
                        this.runtimeActor
                );
    }


    public void syncSelectedActor(
            BlockbusterSceneActorData actor)
    {
        this.setSelectedActor(actor);
    }


    /**
     * Передаёт Character Mode тот же EntityActor,
     * который используется Preview.
     */
    public void setRuntimeActor(
            EntityActor actor)
    {
        this.runtimeActor = actor;

        this.characterEditorPanel
                .setRuntimeActor(
                        actor
                );
    }


    public void clearSelectedActor()
    {
        this.selectedActor = null;

        this.runtimeActor = null;

        this.characterTimelineController
                .setSelectedActor(
                        null
                );

        this.characterEditorPanel
                .setSelectedActor(
                        null
                );

        this.characterEditorPanel
                .setRuntimeActor(
                        null
                );
    }


    public int getCurrentFrame()
    {
        return this.characterController
                .getCurrentFrame();
    }


    public void setCurrentFrame(
            int frame)
    {
        this.characterEditorPanel
                .setCurrentFrame(
                        frame
                );
    }


    public void drawPanel(
            Minecraft mc,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int width,
            int height)
    {
        this.characterEditorPanel
                .setBounds(
                        x,
                        y,
                        width,
                        height
                );

        this.characterEditorPanel
                .draw(
                        mc,
                        mouseX,
                        mouseY
                );
    }


    public int getTimelineHeight()
    {
        return this.characterTimelineController
                .getTimelineHeight();
    }


    public void drawTimeline(
            AnimationEditorScreen screen,
            int width,
            int height,
            int x,
            int top,
            int currentFrame,
            int sceneLength)
    {
        this.characterTimelineController
                .draw(
                        screen,
                        width,
                        height,
                        x,
                        top,
                        currentFrame,
                        sceneLength
                );
    }


    public boolean mouseClickedPanel(
            int mouseX,
            int mouseY,
            int mouseButton)
    {
        return this.characterEditorPanel
                .mouseClicked(
                        mouseX,
                        mouseY,
                        mouseButton
                );
    }


    public boolean mouseClickedTimeline(
            int mouseX,
            int mouseY,
            int mouseButton,
            int width,
            int timelineTop,
            int left)
    {
        return this.characterTimelineController
                .mouseClicked(
                        mouseX,
                        mouseY,
                        mouseButton,
                        width,
                        timelineTop,
                        left
                );
    }


    public boolean mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            int width,
            int timelineTop,
            int left)
    {
        return this.characterTimelineController
                .mouseClickMove(
                        mouseX,
                        mouseY,
                        clickedMouseButton,
                        width,
                        timelineTop,
                        left
                );
    }


    public boolean mouseScrolled(
            int mouseX,
            int mouseY,
            int direction,
            int width,
            int timelineTop,
            int left)
    {
        return this.characterTimelineController
                .mouseScrolled(
                        mouseX,
                        mouseY,
                        direction,
                        width,
                        timelineTop,
                        left
                );
    }


    public void mouseReleased()
    {
        this.characterTimelineController
                .mouseReleased();
    }


    public void deleteSelectedKey()
    {
        this.characterTimelineController
                .deleteSelectedKey();
    }


    public void reset()
    {
        this.characterTimelineController
                .mouseReleased();

        this.selectedActor = null;
        this.runtimeActor = null;

        this.characterEditorPanel
                .setSelectedActor(
                        null
                );

        this.characterEditorPanel
                .setRuntimeActor(
                        null
                );
    }
}
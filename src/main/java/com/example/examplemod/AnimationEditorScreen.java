package com.example.examplemod;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnimationEditorScreen extends GuiScreen
{
    private static final int TOP_BAR_HEIGHT = 25;

    private static final int LEFT_PANEL_WIDTH = 180;

    private static final int TIMELINE_HEIGHT = 180;

    /*
     * Левая колонка.
     *
     * Actors        — выбор персонажа.
     * Interpolation — настройки выбранного keyframe.
     * Bones         — выбор кости.
     */

    private static final int ACTOR_PANEL_HEIGHT = 125;

    private static final int INTERPOLATION_PANEL_HEIGHT = 105;

    private static final float BASE_FRAME_WIDTH = 6.0F;

    private static final int FIRST_FRAME = 0;

    private float timelineZoom = 1.0F;

    private static final float MIN_TIMELINE_ZOOM = 0.25F;

    private static final float MAX_TIMELINE_ZOOM = 4.0F;

    private static final float TIMELINE_ZOOM_STEP = 0.25F;

    private static final int TRACK_HEIGHT = 20;

    /*
     * Animation system.
     */

    private AnimationAdapterManager adapterManager;

    private BlockbusterAnimationAdapter blockbusterAdapter;

    /*
     * Scene system.
     */

    private BlockbusterSceneManager sceneManager;

    private List<File> sceneFiles =
            new ArrayList<File>();

    private int selectedScene = -1;

    private String sceneStatus =
            "No scene selected";

    private boolean sceneDropdownOpen = false;

    /*
     * Actor system.
     */

    private int selectedActor = -1;

    private String actorStatus =
            "No actor selected";

    /*
     * Old recording system.
     *
     * Пока оставляем для совместимости.
     */

    private BlockbusterRecordManager recordManager;

    private List<File> recordingFiles =
            new ArrayList<File>();

    private int selectedRecording = -1;

    private String recordingStatus =
            "No recording selected";

    /*
     * Temporary record manager.
     */

    private BlockbusterRecordManager
            blockbusterRecordManager;

    private List<File> blockbusterRecordFiles =
            new ArrayList<File>();

    private int selectedRecord = -1;

    /*
     * Timeline.
     */

    private int currentFrame = 0;

    private int timelineOffset = 0;

    private boolean playing = false;

    private final EditorTimeline timeline =
            new EditorTimeline();

    /*
     * Temporary bones.
     *
     * Позже полностью заменим их
     * реальной иерархией костей модели.
     */

    private final List<AnimationBone> bones =
            new ArrayList<AnimationBone>();

    private int selectedBone = 0;

    private AnimationKeyframe selectedKeyframe =
            null;

    /*
     * Editor panels.
     */

    private final TransformPanel transformPanel =
            new TransformPanel();

    private final AnimationPreview animationPreview =
            new AnimationPreview();

    @Override
    public void initGui()
    {
        super.initGui();

        /*
         * Scene manager.
         */

        this.sceneManager =
                new BlockbusterSceneManager();

        this.sceneFiles =
                this.sceneManager.getSceneFiles();

        this.selectedScene = -1;

        this.selectedActor = -1;

        this.sceneDropdownOpen = false;

        if (this.sceneFiles.isEmpty())
        {
            this.sceneStatus =
                    "No Blockbuster scenes found";
        }
        else
        {
            this.sceneStatus =
                    this.sceneFiles.size()
                            + " scene(s) found";
        }

        System.out.println(
                "[BBS Animation Editor] "
                        + "Editor scene list size: "
                        + this.sceneFiles.size()
        );

        for (int i = 0;
             i < this.sceneFiles.size();
             i++)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Editor scene ["
                            + i
                            + "]: "
                            + this.sceneFiles
                            .get(i)
                            .getAbsolutePath()
            );
        }

        /*
         * Old record manager.
         */

        this.recordManager =
                new BlockbusterRecordManager();

        this.recordingFiles =
                this.recordManager
                        .getRecordingFiles();

        this.selectedRecording = -1;

        /*
         * Animation adapters.
         */

        this.adapterManager =
                new AnimationAdapterManager();

        this.blockbusterAdapter =
                new BlockbusterAnimationAdapter();

        this.adapterManager.register(
                this.blockbusterAdapter
        );

        if (this.blockbusterAdapter.supports())
        {
            this.blockbusterAdapter
                    .loadModel("steve");
        }

        /*
         * Blockbuster records.
         */

        this.blockbusterRecordManager =
                new BlockbusterRecordManager();

        this.refreshBlockbusterRecordList();

        /*
         * Temporary test bones.
         */

        createTemporaryTestBones();

        /*
         * Timeline.
         */

        this.timelineZoom = 1.0F;

        this.timelineOffset = 0;

        this.currentFrame = 0;

        this.timeline.setLength(
                getMaximumFrame() + 1
        );

        this.timeline.rewind();

        this.timeline.pause();

        this.playing = false;
    }

    private void createTemporaryTestBones()
    {
        this.bones.clear();

        AnimationBone anchor =
                new AnimationBone("Anchor");

        AnimationBone body =
                new AnimationBone("Body");

        AnimationBone head =
                new AnimationBone("Head");

        AnimationBone armLeft =
                new AnimationBone("Arm.L");

        AnimationBone armRight =
                new AnimationBone("Arm.R");

        AnimationBone legLeft =
                new AnimationBone("Leg.L");

        AnimationBone legRight =
                new AnimationBone("Leg.R");

        anchor.addChild(body);

        body.addChild(head);

        body.addChild(armLeft);

        body.addChild(armRight);

        body.addChild(legLeft);

        body.addChild(legRight);

        body.setLocalPosition(
                0.0F,
                0.0F,
                0.0F
        );

        head.setLocalPosition(
                0.0F,
                -55.0F,
                0.0F
        );

        armLeft.setLocalPosition(
                -30.0F,
                0.0F,
                0.0F
        );

        armRight.setLocalPosition(
                30.0F,
                0.0F,
                0.0F
        );

        legLeft.setLocalPosition(
                -12.0F,
                65.0F,
                0.0F
        );

        legRight.setLocalPosition(
                12.0F,
                65.0F,
                0.0F
        );

        body.addKeyframe(0);

        body.addKeyframe(20);

        body.addKeyframe(40);

        body.getKeyframes()
                .get(0)
                .getTransform()
                .setPosition(
                        0.0F,
                        0.0F,
                        0.0F
                );

        body.getKeyframes()
                .get(1)
                .getTransform()
                .setPosition(
                        20.0F,
                        0.0F,
                        0.0F
                );

        body.getKeyframes()
                .get(2)
                .getTransform()
                .setPosition(
                        40.0F,
                        0.0F,
                        0.0F
                );

        head.addKeyframe(0);

        head.addKeyframe(30);

        armLeft.addKeyframe(0);

        armRight.addKeyframe(0);

        legLeft.addKeyframe(0);

        legRight.addKeyframe(0);

        this.bones.add(anchor);

        this.bones.add(body);

        this.bones.add(head);

        this.bones.add(armLeft);

        this.bones.add(armRight);

        this.bones.add(legLeft);

        this.bones.add(legRight);

        this.selectedBone = 0;

        this.selectedKeyframe = null;
    }

    /*
     * ------------------------------------------------------------
     * Scene
     * ------------------------------------------------------------
     */

    private void refreshSceneList()
    {
        if (this.sceneManager == null)
        {
            return;
        }

        this.sceneFiles =
                this.sceneManager.getSceneFiles();

        if (this.sceneFiles == null)
        {
            this.sceneFiles =
                    new ArrayList<File>();
        }

        if (
                this.selectedScene >=
                        this.sceneFiles.size()
        )
        {
            this.selectedScene = -1;
        }
    }

    private void loadScene(int index)
    {
        if (this.sceneManager == null)
        {
            return;
        }

        if (
                index < 0 ||
                        index >=
                                this.sceneFiles.size()
        )
        {
            return;
        }

        File file =
                this.sceneFiles.get(index);

        try
        {
            BlockbusterScene scene =
                    this.sceneManager.load(file);

            this.selectedScene = index;

            this.selectedActor = -1;

            this.actorStatus =
                    "No actor selected";

            this.sceneDropdownOpen = false;

            this.currentFrame = 0;

            this.timelineOffset = 0;

            this.playing = false;

            int sceneLength =
                    getSceneLength();

            this.timeline.setLength(
                    Math.max(
                            1,
                            sceneLength
                    )
            );

            this.timeline.rewind();

            this.timeline.pause();

            this.selectedKeyframe = null;

            this.sceneStatus =
                    "Scene loaded: "
                            + file.getName();

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Loaded scene into editor: "
                            + file.getName()
            );

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Scene actors: "
                            + scene.getActors().size()
            );

            for (
                    BlockbusterSceneActorData data :
                    this.sceneManager
                            .getActorData()
            )
            {
                if (data == null)
                {
                    continue;
                }

                System.out.println(
                        "[BBS Animation Editor] "
                                + "Scene actor: "
                                + data.getId()
                                + " | name="
                                + data.getName()
                                + " | morph="
                                + data.getMorphName()
                                + " | record="
                                + data.hasRecord()
                );
            }
        }
        catch (IOException exception)
        {
            this.sceneStatus =
                    "Failed to load scene";

            System.err.println(
                    "[BBS Animation Editor] "
                            + "Could not load scene: "
                            + file.getAbsolutePath()
            );

            exception.printStackTrace();
        }
    }

    private int getSceneLength()
    {
        if (this.sceneManager == null)
        {
            return 0;
        }

        int maximumLength = 0;

        for (
                BlockbusterSceneActorData data :
                this.sceneManager
                        .getActorData()
        )
        {
            if (
                    data == null ||
                            !data.hasRecord()
            )
            {
                continue;
            }

            maximumLength =
                    Math.max(
                            maximumLength,
                            data.getLength()
                    );
        }

        return maximumLength;
    }

    private List<BlockbusterSceneActorData>
    getSceneActors()
    {
        if (this.sceneManager == null)
        {
            return new ArrayList<
                    BlockbusterSceneActorData>();
        }

        return this.sceneManager
                .getActorData();
    }

    private BlockbusterSceneActorData
    getSelectedActor()
    {
        List<BlockbusterSceneActorData> actors =
                getSceneActors();

        if (
                this.selectedActor < 0 ||
                        this.selectedActor >=
                                actors.size()
        )
        {
            return null;
        }

        return actors.get(
                this.selectedActor
        );
    }

    private void selectActor(int index)
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

        this.selectedActor = index;

        this.actorStatus =
                "Actor selected: "
                        + data.getId();

        this.currentFrame = 0;

        this.timelineOffset = 0;

        this.playing = false;

        this.timeline.setTick(0);

        this.timeline.pause();

        System.out.println(
                "[BBS Animation Editor] "
                        + "Selected scene actor: "
                        + data.getId()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Actor name: "
                        + data.getName()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Actor morph: "
                        + data.getMorphName()
        );

        System.out.println(
                "[BBS Animation Editor] "
                        + "Actor record: "
                        + data.hasRecord()
        );
    }

    /*
     * ------------------------------------------------------------
     * Records
     * ------------------------------------------------------------
     */

    private void refreshBlockbusterRecordList()
    {
        if (
                this.blockbusterRecordManager ==
                        null
        )
        {
            return;
        }

        this.blockbusterRecordFiles =
                this.blockbusterRecordManager
                        .getRecordingFiles();

        if (
                this.blockbusterRecordFiles ==
                        null
        )
        {
            this.blockbusterRecordFiles =
                    new ArrayList<File>();
        }

        if (
                this.selectedRecord >=
                        this.blockbusterRecordFiles
                                .size()
        )
        {
            this.selectedRecord = -1;
        }
    }

    private void loadBlockbusterRecord(
            int index)
    {
        if (
                this.blockbusterRecordManager ==
                        null
        )
        {
            return;
        }

        if (
                index < 0 ||
                        index >=
                                this.blockbusterRecordFiles
                                        .size()
        )
        {
            return;
        }

        File file =
                this.blockbusterRecordFiles
                        .get(index);

        try
        {
            this.blockbusterRecordManager
                    .load(file);

            this.selectedRecord = index;

            this.currentFrame = 0;

            this.timelineOffset = 0;

            this.playing = false;

            this.timeline.setLength(
                    this.blockbusterRecordManager
                            .getCurrentRecord()
                            .getFullLength()
            );

            this.timeline.rewind();

            this.timeline.pause();

            this.selectedKeyframe = null;

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Loaded recording into editor: "
                            + file.getName()
            );
        }
        catch (IOException e)
        {
            System.err.println(
                    "[BBS Animation Editor] "
                            + "Could not load recording: "
                            + file.getAbsolutePath()
            );

            e.printStackTrace();
        }
    }

    private BlockbusterRecord
    getCurrentBlockbusterRecord()
    {
        BlockbusterSceneActorData actor =
                getSelectedActor();

        if (
                actor != null &&
                        actor.hasRecord()
        )
        {
            return actor.getRecord();
        }

        if (
                this.blockbusterRecordManager ==
                        null
        )
        {
            return null;
        }

        return this.blockbusterRecordManager
                .getCurrentRecord();
    }

    private int getBlockbusterRecordLength()
    {
        BlockbusterRecord record =
                getCurrentBlockbusterRecord();

        if (record == null)
        {
            return 0;
        }

        return record.getLength();
    }

    /*
     * ------------------------------------------------------------
     * GUI
     * ------------------------------------------------------------
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
                0xFF202124
        );

        drawTopBar();

        drawActorPanel();

        drawInterpolationPanel();

        drawBonePanel();

        drawPreview();

        drawTimeline(
                mouseX,
                mouseY
        );

        /*
         * Dropdown рисуется последним,
         * чтобы он находился поверх остальных
         * элементов GUI.
         */

        if (this.sceneDropdownOpen)
        {
            drawSceneDropdown();
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );
    }

    /*
     * ------------------------------------------------------------
     * Top bar
     * ------------------------------------------------------------
     */

    private void drawTopBar()
    {
        this.drawRect(
                0,
                0,
                this.width,
                TOP_BAR_HEIGHT,
                0xFF303134
        );

        this.drawString(
                this.fontRenderer,
                "BBS Animation Editor",
                10,
                8,
                0xFFFFFF
        );

        /*
         * Scene selector.
         */

        int sceneButtonX = 175;

        int sceneButtonWidth = 180;

        this.drawRect(
                sceneButtonX,
                3,
                sceneButtonX +
                        sceneButtonWidth,
                TOP_BAR_HEIGHT - 3,
                0xFF45474A
        );

        String sceneName =
                "Select Scene";

        if (
                this.selectedScene >= 0 &&
                        this.selectedScene <
                                this.sceneFiles.size()
        )
        {
            sceneName =
                    this.sceneFiles
                            .get(this.selectedScene)
                            .getName();
        }

        if (sceneName.length() > 22)
        {
            sceneName =
                    sceneName.substring(
                            0,
                            19
                    )
                            + "...";
        }

        this.drawString(
                this.fontRenderer,
                "Scene: "
                        + sceneName
                        + " ▼",
                sceneButtonX + 8,
                8,
                0xFFFFFF
        );

        /*
         * Selected actor.
         */

        BlockbusterSceneActorData actor =
                getSelectedActor();

        if (actor != null)
        {
            this.drawString(
                    this.fontRenderer,
                    "Actor: "
                            + actor.getId(),
                    375,
                    8,
                    0x66CCFF
            );
        }

        /*
         * Current frame.
         */

        this.drawString(
                this.fontRenderer,
                "Frame: "
                        + this.currentFrame,
                this.width - 100,
                8,
                0xCCCCCC
        );
    }

    /*
     * ------------------------------------------------------------
     * Scene dropdown
     * ------------------------------------------------------------
     */

    private void drawSceneDropdown()
    {
        int x = 175;

        int y =
                TOP_BAR_HEIGHT + 2;

        int width = 180;

        int rowHeight = 18;

        int maxVisible = 8;

        int count =
                Math.min(
                        maxVisible,
                        this.sceneFiles.size()
                );

        int height =
                Math.max(
                        rowHeight,
                        count * rowHeight
                );

        this.drawRect(
                x - 2,
                y - 2,
                x + width + 2,
                y + height + 2,
                0xFF111111
        );

        this.drawRect(
                x,
                y,
                x + width,
                y + height,
                0xFF292A2D
        );

        if (this.sceneFiles.isEmpty())
        {
            this.drawString(
                    this.fontRenderer,
                    "No scenes found",
                    x + 8,
                    y + 5,
                    0x777777
            );

            return;
        }

        for (int i = 0;
             i < count;
             i++)
        {
            File file =
                    this.sceneFiles.get(i);

            int rowY =
                    y +
                            i * rowHeight;

            boolean selected =
                    i == this.selectedScene;

            if (selected)
            {
                this.drawRect(
                        x,
                        rowY,
                        x + width,
                        rowY + rowHeight,
                        0xFF45474A
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
                        )
                                + "...";
            }

            this.drawString(
                    this.fontRenderer,
                    name,
                    x + 8,
                    rowY + 5,
                    selected
                            ? 0xFFFFFF
                            : 0xAAAAAA
            );
        }
    }

    /*
     * ------------------------------------------------------------
     * Actors
     * ------------------------------------------------------------
     */

    private void drawActorPanel()
    {
        int top = TOP_BAR_HEIGHT;

        int bottom =
                top +
                        ACTOR_PANEL_HEIGHT;

        this.drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                0xFF292A2D
        );

        this.drawString(
                this.fontRenderer,
                "ACTORS",
                10,
                top + 10,
                0x66CCFF
        );

        List<BlockbusterSceneActorData> actors =
                getSceneActors();

        if (actors.isEmpty())
        {
            this.drawString(
                    this.fontRenderer,
                    "No actors",
                    10,
                    top + 32,
                    0x777777
            );

            return;
        }

        int actorY =
                top + 30;

        final int rowHeight = 31;

        for (int i = 0;
             i < actors.size();
             i++)
        {
            BlockbusterSceneActorData actor =
                    actors.get(i);

            if (actor == null)
            {
                continue;
            }

            if (
                    actorY + rowHeight >
                            bottom - 3
            )
            {
                break;
            }

            boolean selected =
                    i == this.selectedActor;

            if (selected)
            {
                this.drawRect(
                        5,
                        actorY - 2,
                        LEFT_PANEL_WIDTH - 5,
                        actorY + rowHeight - 2,
                        0xFF45474A
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
                        )
                                + "...";
            }

            if (name.length() > 22)
            {
                name =
                        name.substring(
                                0,
                                19
                        )
                                + "...";
            }

            this.drawString(
                    this.fontRenderer,
                    id,
                    12,
                    actorY + 1,
                    selected
                            ? 0xFFFFFF
                            : 0xBBBBBB
            );

            this.drawString(
                    this.fontRenderer,
                    name,
                    12,
                    actorY + 13,
                    selected
                            ? 0x66CCFF
                            : 0x888888
            );

            actorY += rowHeight;
        }
    }

    /*
     * ------------------------------------------------------------
     * Interpolation
     * ------------------------------------------------------------
     */

    private void drawInterpolationPanel()
    {
        int top =
                TOP_BAR_HEIGHT +
                        ACTOR_PANEL_HEIGHT;

        int bottom =
                top +
                        INTERPOLATION_PANEL_HEIGHT;

        this.drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                0xFF252629
        );

        this.drawString(
                this.fontRenderer,
                "INTERPOLATION",
                10,
                top + 10,
                0xFFFFFF
        );

        if (this.selectedKeyframe == null)
        {
            this.drawString(
                    this.fontRenderer,
                    "No keyframe selected",
                    10,
                    top + 32,
                    0x777777
            );

            this.drawString(
                    this.fontRenderer,
                    "Select a keyframe",
                    10,
                    top + 47,
                    0x666666
            );

            return;
        }

        this.drawString(
                this.fontRenderer,
                "Keyframe: "
                        + this.selectedKeyframe
                        .getFrame(),
                10,
                top + 30,
                0x66CCFF
        );

        int buttonY =
                top + 50;

        drawInterpolationButton(
                "Linear",
                10,
                buttonY,
                false
        );

        drawInterpolationButton(
                "Smooth",
                92,
                buttonY,
                false
        );

        drawInterpolationButton(
                "Bezier",
                10,
                buttonY + 24,
                false
        );

        drawInterpolationButton(
                "Step",
                92,
                buttonY + 24,
                false
        );
    }

    private void drawInterpolationButton(
            String text,
            int x,
            int y,
            boolean selected)
    {
        int width = 76;
        int height = 18;

        this.drawRect(
                x,
                y,
                x + width,
                y + height,
                selected
                        ? 0xFF55585C
                        : 0xFF353639
        );

        this.drawString(
                this.fontRenderer,
                text,
                x + 8,
                y + 5,
                selected
                        ? 0xFFFFFF
                        : 0xAAAAAA
        );
    }

    /*
     * ------------------------------------------------------------
     * Bones
     * ------------------------------------------------------------
     */

    private void drawBonePanel()
    {
        int top =
                TOP_BAR_HEIGHT +
                        ACTOR_PANEL_HEIGHT +
                        INTERPOLATION_PANEL_HEIGHT;

        int bottom =
                this.height -
                        TIMELINE_HEIGHT;

        this.drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                0xFF252629
        );

        this.drawString(
                this.fontRenderer,
                "BONES",
                10,
                top + 8,
                0xFFFFFF
        );

        int y =
                top + 28;

        for (int i = 0;
             i < this.bones.size();
             i++)
        {
            AnimationBone bone =
                    this.bones.get(i);

            boolean selected =
                    i == this.selectedBone;

            if (selected)
            {
                this.drawRect(
                        5,
                        y - 3,
                        LEFT_PANEL_WIDTH - 5,
                        y + 13,
                        0xFF45474A
                );
            }

            this.drawString(
                    this.fontRenderer,
                    bone.getName(),
                    15,
                    y,
                    selected
                            ? 0xFFFFFF
                            : 0xAAAAAA
            );

            y += TRACK_HEIGHT;

            if (
                    y >
                            bottom - 10
            )
            {
                break;
            }
        }
    }

    /*
     * ------------------------------------------------------------
     * Preview
     * ------------------------------------------------------------
     */

    private void drawPreview()
    {
        int left =
                LEFT_PANEL_WIDTH;

        int top =
                TOP_BAR_HEIGHT;

        int right =
                this.width;

        int bottom =
                this.height -
                        TIMELINE_HEIGHT;

        this.drawRect(
                left,
                top,
                right,
                bottom,
                0xFF1E1F21
        );

        int previewWidth =
                Math.max(
                        150,
                        (right - left) - 195
                );

        int previewHeight =
                bottom - top;

        animationPreview.setBounds(
                left,
                top,
                previewWidth,
                previewHeight
        );

        if (this.bones.size() >= 7)
        {
            animationPreview.draw(
                    this.mc,
                    this.bones.get(1),
                    this.bones.get(2),
                    this.bones.get(3),
                    this.bones.get(4),
                    this.bones.get(5),
                    this.bones.get(6),
                    this.currentFrame
            );
        }

        int panelX =
                right - 185;

        int panelY =
                top + 10;

        transformPanel.setPosition(
                panelX,
                panelY
        );

        transformPanel.draw(
                this.mc,
                this.selectedKeyframe,
                getCurrentTransform(),
                this.currentFrame
        );
    }

    /*
     * ------------------------------------------------------------
     * Timeline
     * ------------------------------------------------------------
     */

    private void drawTimeline(
            int mouseX,
            int mouseY)
    {
        int timelineTop =
                this.height -
                        TIMELINE_HEIGHT;

        this.drawRect(
                0,
                timelineTop,
                this.width,
                this.height,
                0xFF292A2D
        );

        this.drawString(
                this.fontRenderer,
                "TIMELINE",
                10,
                timelineTop + 8,
                0xFFFFFF
        );

        int sceneLength =
                getSceneLength();

        if (sceneLength > 0)
        {
            this.drawString(
                    this.fontRenderer,
                    "Scene: "
                            + sceneLength
                            + " frames",
                    80,
                    timelineTop + 8,
                    0x55FFAA
            );
        }

        BlockbusterSceneActorData actor =
                getSelectedActor();

        if (actor != null)
        {
            this.drawString(
                    this.fontRenderer,
                    "Actor: "
                            + actor.getId(),
                    210,
                    timelineTop + 8,
                    0x66CCFF
            );
        }

        int timelineStartX =
                LEFT_PANEL_WIDTH;

        drawTimelineRuler(
                timelineTop,
                timelineStartX
        );

        int tracksTop =
                timelineTop + 35;

        for (int i = 0;
             i < this.bones.size();
             i++)
        {
            AnimationBone bone =
                    this.bones.get(i);

            int trackY =
                    tracksTop +
                            i * TRACK_HEIGHT;

            drawBoneTrack(
                    bone,
                    trackY,
                    i == this.selectedBone,
                    timelineStartX
            );
        }

        drawCurrentFrameLine(
                timelineTop,
                timelineStartX
        );

        String playbackText =
                this.playing
                        ? "Playing"
                        : "Paused";

        this.drawString(
                this.fontRenderer,
                playbackText,
                10,
                this.height - 25,
                this.playing
                        ? 0x55FF55
                        : 0xAAAAAA
        );

        String zoomText =
                "Zoom: "
                        + Math.round(
                        this.timelineZoom *
                                100.0F
                )
                        + "%";

        this.drawString(
                this.fontRenderer,
                zoomText,
                this.width - 80,
                timelineTop + 8,
                0xAAAAAA
        );
    }

    private void drawTimelineRuler(
            int timelineTop,
            int timelineStartX)
    {
        int maximumFrame =
                getMaximumFrame();

        if (
                maximumFrame <
                        EditorTimeline
                                .TICKS_PER_SECOND
        )
        {
            maximumFrame =
                    EditorTimeline
                            .TICKS_PER_SECOND;
        }

        float pixelsPerFrame =
                getPixelsPerFrame();

        int firstVisibleFrame =
                Math.max(
                        FIRST_FRAME,
                        (int) Math.floor(
                                this.timelineOffset /
                                        pixelsPerFrame
                        )
                );

        int visibleWidth =
                this.width -
                        timelineStartX;

        int lastVisibleFrame =
                Math.min(
                        maximumFrame,
                        (int) Math.ceil(
                                (
                                        this.timelineOffset +
                                                visibleWidth
                                ) /
                                        pixelsPerFrame
                        )
                );

        int minorStep;

        if (this.timelineZoom >= 2.0F)
        {
            minorStep = 1;
        }
        else if (this.timelineZoom >= 1.0F)
        {
            minorStep = 5;
        }
        else if (this.timelineZoom >= 0.5F)
        {
            minorStep = 10;
        }
        else
        {
            minorStep = 20;
        }

        for (
                int frame = firstVisibleFrame;
                frame <= lastVisibleFrame;
                frame += minorStep
        )
        {
            int x =
                    getFrameX(frame);

            if (
                    x < timelineStartX ||
                            x > this.width
            )
            {
                continue;
            }

            boolean second =
                    frame %
                            EditorTimeline
                                    .TICKS_PER_SECOND
                            == 0;

            boolean halfSecond =
                    frame % 10 == 0;

            if (second)
            {
                this.drawRect(
                        x,
                        timelineTop + 20,
                        x + 1,
                        timelineTop + 42,
                        0xFF777777
                );

                int seconds =
                        frame /
                                EditorTimeline
                                        .TICKS_PER_SECOND;

                this.drawString(
                        this.fontRenderer,
                        seconds + "s",
                        x + 3,
                        timelineTop + 27,
                        0xFFFFFF
                );
            }
            else if (halfSecond)
            {
                this.drawRect(
                        x,
                        timelineTop + 25,
                        x + 1,
                        timelineTop + 40,
                        0xFF555555
                );

                this.drawString(
                        this.fontRenderer,
                        String.valueOf(frame),
                        x + 3,
                        timelineTop + 27,
                        0x999999
                );
            }
            else
            {
                this.drawRect(
                        x,
                        timelineTop + 30,
                        x + 1,
                        timelineTop + 38,
                        0xFF444444
                );
            }
        }
    }

    private void drawBoneTrack(
            AnimationBone bone,
            int trackY,
            boolean selected,
            int timelineStartX)
    {
        int trackColor =
                selected
                        ? 0xFF3A3B3E
                        : 0xFF303134;

        this.drawRect(
                0,
                trackY,
                this.width,
                trackY + TRACK_HEIGHT,
                trackColor
        );

        /*
         * Здесь намеренно НЕТ названия кости.
         *
         * Список Bones слева является
         * единственным местом выбора кости.
         */

        this.drawRect(
                timelineStartX,
                trackY + 10,
                this.width,
                trackY + 11,
                0xFF555555
        );

        for (
                AnimationKeyframe keyframe :
                bone.getKeyframes()
        )
        {
            int frame =
                    keyframe.getFrame();

            int x =
                    getFrameX(frame);

            if (
                    x < timelineStartX ||
                            x > this.width
            )
            {
                continue;
            }

            boolean keyframeSelected =
                    keyframe ==
                            this.selectedKeyframe;

            drawKeyframe(
                    x,
                    trackY + 10,
                    keyframeSelected
            );
        }
    }

    private void drawCurrentFrameLine(
            int timelineTop,
            int timelineStartX)
    {
        int currentX =
                getFrameX(
                        this.currentFrame
                );

        if (
                currentX >= timelineStartX &&
                        currentX <= this.width
        )
        {
            this.drawRect(
                    currentX,
                    timelineTop + 20,
                    currentX + 2,
                    this.height,
                    0xFFFF5555
            );
        }
    }

    private void drawKeyframe(
            int x,
            int y,
            boolean selected)
    {
        int color =
                selected
                        ? 0xFFFFAA00
                        : 0xFFFFFFFF;

        this.drawRect(
                x - 5,
                y - 5,
                x + 5,
                y + 5,
                color
        );

        this.drawRect(
                x - 3,
                y - 3,
                x + 3,
                y + 3,
                0xFF292A2D
        );
    }

    /*
     * ------------------------------------------------------------
     * Timeline helpers
     * ------------------------------------------------------------
     */

    private float getPixelsPerFrame()
    {
        return BASE_FRAME_WIDTH *
                this.timelineZoom;
    }

    private int getFrameX(int frame)
    {
        return LEFT_PANEL_WIDTH
                + Math.round(
                frame *
                        this.getPixelsPerFrame()
        )
                - this.timelineOffset;
    }

    private int getFrameFromMouseX(
            int mouseX)
    {
        int relativeX =
                mouseX
                        - LEFT_PANEL_WIDTH
                        + this.timelineOffset;

        float pixelsPerFrame =
                this.getPixelsPerFrame();

        if (pixelsPerFrame <= 0.0F)
        {
            return FIRST_FRAME;
        }

        return Math.round(
                (float) relativeX /
                        pixelsPerFrame
        );
    }

    private AnimationKeyframe findKeyframe(
            AnimationBone bone,
            int frame)
    {
        for (
                AnimationKeyframe keyframe :
                bone.getKeyframes()
        )
        {
            if (
                    keyframe.getFrame() ==
                            frame
            )
            {
                return keyframe;
            }
        }

        return null;
    }

    private AnimationKeyframe findKeyframeAt(
            AnimationBone bone,
            int mouseX,
            int frame,
            int radius)
    {
        for (
                AnimationKeyframe keyframe :
                bone.getKeyframes()
        )
        {
            int keyframeX =
                    getFrameX(
                            keyframe.getFrame()
                    );

            if (
                    Math.abs(
                            keyframeX -
                                    mouseX
                    ) <= radius
            )
            {
                return keyframe;
            }
        }

        return null;
    }

    private int getMaximumTimelineOffset()
    {
        int maximumFrame =
                getMaximumFrame();

        int timelineWidth =
                this.width -
                        LEFT_PANEL_WIDTH;

        int contentWidth =
                Math.round(
                        (maximumFrame + 1) *
                                getPixelsPerFrame()
                );

        return Math.max(
                0,
                contentWidth -
                        timelineWidth
        );
    }

    private int getMaximumFrame()
    {
        int maximum = 2000;

        int timelineLast =
                this.timeline.getLastTick();

        if (timelineLast > maximum)
        {
            maximum =
                    timelineLast;
        }

        int sceneLength =
                getSceneLength();

        if (sceneLength > 0)
        {
            maximum =
                    Math.max(
                            maximum,
                            sceneLength - 1
                    );
        }

        for (
                AnimationBone bone :
                this.bones
        )
        {
            for (
                    AnimationKeyframe keyframe :
                    bone.getKeyframes()
            )
            {
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
     * ------------------------------------------------------------
     * Mouse
     * ------------------------------------------------------------
     */

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws IOException
    {
        /*
         * Scene dropdown button.
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

            return;
        }

        /*
         * Scene dropdown list.
         */

        if (this.sceneDropdownOpen)
        {
            int x = 175;

            int y =
                    TOP_BAR_HEIGHT + 2;

            int width = 180;

            int rowHeight = 18;

            int count =
                    Math.min(
                            8,
                            this.sceneFiles.size()
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
                        ) / rowHeight;

                if (
                        sceneIndex >= 0 &&
                                sceneIndex <
                                        this.sceneFiles
                                                .size()
                )
                {
                    loadScene(
                            sceneIndex
                    );

                    return;
                }
            }

            this.sceneDropdownOpen = false;
        }

        /*
         * Actor selection.
         */

        int actorTop =
                TOP_BAR_HEIGHT;

        int actorBottom =
                actorTop +
                        ACTOR_PANEL_HEIGHT;

        final int actorRowHeight = 31;

        if (
                mouseX >= 0 &&
                        mouseX <= LEFT_PANEL_WIDTH &&
                        mouseY >= actorTop + 30 &&
                        mouseY < actorBottom
        )
        {
            int relativeY =
                    mouseY -
                            (actorTop + 30);

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
         * Bone selection.
         */

        int bonePanelTop =
                TOP_BAR_HEIGHT +
                        ACTOR_PANEL_HEIGHT +
                        INTERPOLATION_PANEL_HEIGHT;

        int timelineTop =
                this.height -
                        TIMELINE_HEIGHT;

        if (
                mouseX >= 0 &&
                        mouseX <= LEFT_PANEL_WIDTH &&
                        mouseY >=
                                bonePanelTop + 28 &&
                        mouseY <
                                timelineTop
        )
        {
            int relativeY =
                    mouseY -
                            (bonePanelTop + 28);

            int boneIndex =
                    relativeY /
                            TRACK_HEIGHT;

            if (
                    boneIndex >= 0 &&
                            boneIndex <
                                    this.bones.size()
            )
            {
                this.selectedBone =
                        boneIndex;

                this.selectedKeyframe =
                        null;

                return;
            }
        }

        /*
         * Timeline.
         */

        if (
                mouseY >= timelineTop &&
                        mouseY <= this.height
        )
        {
            int tracksTop =
                    timelineTop + 35;

            int relativeY =
                    mouseY - tracksTop;

            int boneIndex =
                    relativeY /
                            TRACK_HEIGHT;

            if (
                    boneIndex >= 0 &&
                            boneIndex <
                                    this.bones.size()
            )
            {
                this.selectedBone =
                        boneIndex;

                AnimationBone bone =
                        this.bones.get(
                                this.selectedBone
                        );

                int frame =
                        getFrameFromMouseX(
                                mouseX
                        );

                int maximumFrame =
                        getMaximumFrame();

                if (frame < FIRST_FRAME)
                {
                    frame =
                            FIRST_FRAME;
                }

                if (frame > maximumFrame)
                {
                    frame =
                            maximumFrame;
                }

                if (mouseButton == 0)
                {
                    AnimationKeyframe
                            clickedKeyframe =
                            findKeyframeAt(
                                    bone,
                                    mouseX,
                                    frame,
                                    6
                            );

                    if (
                            clickedKeyframe != null
                    )
                    {
                        this.selectedKeyframe =
                                clickedKeyframe;

                        this.timeline.setTick(
                                clickedKeyframe
                                        .getFrame()
                        );

                        this.currentFrame =
                                this.timeline
                                        .getTick();
                    }
                    else
                    {
                        this.timeline.setTick(
                                frame
                        );

                        this.currentFrame =
                                this.timeline
                                        .getTick();

                        if (
                                findKeyframe(
                                        bone,
                                        frame
                                ) == null
                        )
                        {
                            bone.addKeyframe(
                                    frame
                            );
                        }

                        this.selectedKeyframe =
                                findKeyframe(
                                        bone,
                                        frame
                                );
                    }
                }

                if (mouseButton == 1)
                {
                    AnimationKeyframe
                            clickedKeyframe =
                            findKeyframeAt(
                                    bone,
                                    mouseX,
                                    frame,
                                    6
                            );

                    if (
                            clickedKeyframe != null
                    )
                    {
                        if (
                                clickedKeyframe ==
                                        this.selectedKeyframe
                        )
                        {
                            this.selectedKeyframe =
                                    null;
                        }

                        bone.removeKeyframe(
                                clickedKeyframe
                                        .getFrame()
                        );
                    }
                }
            }
        }

        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    /*
     * ------------------------------------------------------------
     * Mouse drag
     * ------------------------------------------------------------
     */

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        if (
                this.selectedKeyframe != null
        )
        {
            this.transformPanel.mouseDragged(
                    mouseX,
                    mouseY,
                    this.selectedKeyframe
            );
        }

        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }

    @Override
    protected void mouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        this.transformPanel.mouseReleased(
                state
        );

        super.mouseReleased(
                mouseX,
                mouseY,
                state
        );
    }

    /*
     * ------------------------------------------------------------
     * Mouse wheel
     * ------------------------------------------------------------
     */

    @Override
    public void handleMouseInput()
            throws IOException
    {
        super.handleMouseInput();

        int wheel =
                org.lwjgl.input.Mouse
                        .getEventDWheel();

        if (wheel == 0)
        {
            return;
        }

        int mouseX =
                org.lwjgl.input.Mouse
                        .getEventX()
                        * this.width
                        / this.mc.displayWidth;

        int mouseY =
                this.height
                        - org.lwjgl.input.Mouse
                        .getEventY()
                        * this.height
                        / this.mc.displayHeight
                        - 1;

        int timelineTop =
                this.height -
                        TIMELINE_HEIGHT;

        if (mouseY < timelineTop)
        {
            return;
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
                    getFrameFromMouseX(
                            mouseX
                    );

            float oldZoom =
                    this.timelineZoom;

            if (wheel > 0)
            {
                this.timelineZoom +=
                        TIMELINE_ZOOM_STEP;
            }
            else
            {
                this.timelineZoom -=
                        TIMELINE_ZOOM_STEP;
            }

            if (
                    this.timelineZoom <
                            MIN_TIMELINE_ZOOM
            )
            {
                this.timelineZoom =
                        MIN_TIMELINE_ZOOM;
            }

            if (
                    this.timelineZoom >
                            MAX_TIMELINE_ZOOM
            )
            {
                this.timelineZoom =
                        MAX_TIMELINE_ZOOM;
            }

            if (
                    oldZoom !=
                            this.timelineZoom
            )
            {
                this.timelineOffset =
                        LEFT_PANEL_WIDTH
                                + Math.round(
                                mouseFrame *
                                        getPixelsPerFrame()
                        )
                                - mouseX;

                clampTimelineOffset();
            }

            return;
        }

        int scrollAmount = 60;

        if (wheel > 0)
        {
            this.timelineOffset -=
                    scrollAmount;
        }
        else
        {
            this.timelineOffset +=
                    scrollAmount;
        }

        clampTimelineOffset();
    }

    private void clampTimelineOffset()
    {
        int maxOffset =
                getMaximumTimelineOffset();

        if (this.timelineOffset < 0)
        {
            this.timelineOffset = 0;
        }

        if (
                this.timelineOffset >
                        maxOffset
        )
        {
            this.timelineOffset =
                    maxOffset;
        }
    }

    /*
     * ------------------------------------------------------------
     * Keyboard
     * ------------------------------------------------------------
     */

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws IOException
    {
        this.transformPanel.keyTyped(
                typedChar,
                keyCode,
                this.selectedKeyframe
        );

        if (keyCode == 1)
        {
            this.mc.displayGuiScreen(
                    null
            );

            return;
        }

        if (keyCode == 203)
        {
            this.timeline.previousTick();

            this.currentFrame =
                    this.timeline.getTick();
        }

        if (keyCode == 205)
        {
            this.timeline.nextTick();

            this.currentFrame =
                    this.timeline.getTick();
        }

        if (keyCode == 57)
        {
            this.timeline.togglePlaying();

            this.playing =
                    this.timeline.isPlaying();
        }
    }

    /*
     * ------------------------------------------------------------
     * Update
     * ------------------------------------------------------------
     */

    @Override
    public void updateScreen()
    {
        super.updateScreen();

        BlockbusterSceneLoaderDebug.update();

        BlockbusterRecordDebug.update();

        BlockbusterRecordRoundTripDebug.update();

        BlockbusterActionsDebug.update();

        BlockbusterActionStructureDebug.update();

        BlockbusterActionViewDebug.update();

        this.timeline.update();

        this.currentFrame =
                this.timeline.getTick();

        this.playing =
                this.timeline.isPlaying();

        applyAdapters();
    }

    /*
     * ------------------------------------------------------------
     * Selected objects
     * ------------------------------------------------------------
     */

    public AnimationKeyframe
    getSelectedKeyframe()
    {
        return this.selectedKeyframe;
    }

    public AnimationBone
    getSelectedBone()
    {
        if (
                this.selectedBone >= 0 &&
                        this.selectedBone <
                                this.bones.size()
        )
        {
            return this.bones.get(
                    this.selectedBone
            );
        }

        return null;
    }

    public AnimationTransform
    getCurrentTransform()
    {
        AnimationBone bone =
                getSelectedBone();

        if (bone == null)
        {
            return new AnimationTransform();
        }

        return bone.getTransformAt(
                this.currentFrame
        );
    }

    /*
     * ------------------------------------------------------------
     * Adapter
     * ------------------------------------------------------------
     */

    private void applyAdapters()
    {
        if (this.adapterManager == null)
        {
            return;
        }

        AnimationAdapter adapter =
                this.adapterManager
                        .findSupportedAdapter();

        if (adapter == null)
        {
            return;
        }

        AnimationAdapterData data =
                AnimationAdapterDataBuilder.build(
                        this.bones,
                        this.currentFrame
                );

        adapter.apply(
                data.getBones(),
                this.currentFrame
        );
    }
}
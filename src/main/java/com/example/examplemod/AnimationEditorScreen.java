package com.example.examplemod;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnimationEditorScreen extends GuiScreen
{
    private static final int TOP_BAR_HEIGHT = 25;
    private static final int LEFT_PANEL_WIDTH = 180;
    private static final int ACTOR_PANEL_HEIGHT = 120;
    private static final int INTERPOLATION_PANEL_HEIGHT = 100;

    private static final int BASE_FRAME_WIDTH = 6;
    private static final int FIRST_FRAME = 0;
    private static final int TRACK_HEIGHT = 20;

    private static final float MIN_TIMELINE_ZOOM = 0.25F;
    private static final float MAX_TIMELINE_ZOOM = 4.0F;
    private static final float TIMELINE_ZOOM_STEP = 0.25F;

    private float timelineZoom = 1.0F;

    private AnimationAdapterManager adapterManager;

    private BlockbusterModelAccess blockbusterModelAccess;

    private boolean sceneDropdownOpen = false;

    private int currentFrame = 0;

    private int timelineOffset = 0;

    private boolean playing = false;

    private final EditorTimeline timeline =
            new EditorTimeline();

    private EditorSceneState sceneState;

    private List<AnimationBone> bones =
            new ArrayList<AnimationBone>();

    private int selectedBone = 0;

    private AnimationKeyframe selectedKeyframe =
            null;

    /*
     * TRUE только тогда, когда пользователь
     * действительно начал drag внутри TransformPanel.
     *
     * Это запрещает случайное изменение keyframe
     * при перетаскивании мыши по Timeline или Preview.
     */
    private boolean transformDragging = false;

    /*
     * Base actor state from Blockbuster Record.
     *
     * These values are not animation keyframes.
     */

    private final BlockbusterRecordPose currentRecordPose =
            new BlockbusterRecordPose();

    private final AnimationFrameResolver frameResolver =
            new AnimationFrameResolver();

    private final ActorPose currentActorPose =
            new ActorPose();

    private final AnimationPose currentAnimationPose =
            new AnimationPose();

    private final TransformPanel transformPanel =
            new TransformPanel();

    private final AnimationPreview animationPreview =
            new AnimationPreview();

    @Override
    public void initGui()
    {
        super.initGui();

        this.sceneState =
                new EditorSceneState();

        this.sceneState.refreshScenes();

        this.sceneDropdownOpen = false;

        this.adapterManager =
                new AnimationAdapterManager();

        this.blockbusterModelAccess =
                new BlockbusterModelAccess(
                        null
                );

        BlockbusterAnimationAdapter blockbusterAdapter =
                new BlockbusterAnimationAdapter(
                        this.blockbusterModelAccess
                );

        this.adapterManager.register(
                blockbusterAdapter
        );

        if (blockbusterAdapter.supports())
        {
            this.blockbusterModelAccess.loadModelByName(
                    "steve"
            );
        }

        resetTimeline(1);
    }

    private ActorAnimationData getOrCreateActorAnimation(
            String actorId)
    {
        if (actorId == null || actorId.isEmpty())
        {
            return null;
        }

        ActorAnimationData existing =
                this.sceneState
                        .getAnimationData()
                        .getActor(actorId);

        if (existing != null)
        {
            return existing;
        }

        List<BlockbusterLimbData> limbs =
                this.blockbusterModelAccess.getLimbData();

        ActorAnimationData created =
                new ActorAnimationData(
                        actorId,
                        limbs
                );

        this.sceneState
                .getAnimationData()
                .putActor(
                        actorId,
                        created
                );

        return created;
    }

    private void selectActorAnimation(
            String actorId)
    {
        ActorAnimationData data =
                getOrCreateActorAnimation(
                        actorId
                );

        if (data == null)
        {
            this.bones =
                    new ArrayList<AnimationBone>();

            this.selectedBone = 0;
            this.selectedKeyframe = null;
            this.transformDragging = false;

            return;
        }

        this.bones =
                data.getBones();

        if (
                this.selectedBone >=
                        this.bones.size()
        )
        {
            this.selectedBone = 0;
        }

        this.selectedKeyframe = null;
        this.transformDragging = false;

        debugAnimationPose();
    }

    /**
     * Establishes the first Record frame as the
     * reference position of the Preview.
     *
     * The absolute Blockbuster world coordinates
     * are therefore never used directly as GUI
     * coordinates.
     */
    private void resetPreviewReference()
    {
        BlockbusterRecord record =
                getSelectedActorRecord();

        if (record == null)
        {
            this.animationPreview.setReferencePosition(
                    0.0D,
                    0.0D,
                    0.0D
            );

            return;
        }

        BlockbusterRecordFrame firstFrame =
                record.getFrame(0);

        if (firstFrame == null)
        {
            this.animationPreview.setReferencePosition(
                    0.0D,
                    0.0D,
                    0.0D
            );

            return;
        }

        this.animationPreview.setReferencePosition(
                firstFrame.getX(),
                firstFrame.getY(),
                firstFrame.getZ()
        );
    }

    private void applyRecordFrame()
    {
        BlockbusterRecord record =
                getSelectedActorRecord();

        if (record == null)
        {
            return;
        }

        BlockbusterRecordPose pose =
                this.frameResolver.resolveRecordPose(
                        record,
                        this.currentFrame
                );

        this.currentRecordPose.setPosition(
                pose.getX(),
                pose.getY(),
                pose.getZ()
        );

        this.currentRecordPose.setRotation(
                pose.getYaw(),
                pose.getPitch()
        );

        AnimationActorTransform actorTransform =
                this.frameResolver.resolveActorTransform(
                        record,
                        this.currentFrame
                );

        this.currentActorPose.setPosition(
                actorTransform.getX(),
                actorTransform.getY(),
                actorTransform.getZ()
        );

        this.currentActorPose.setRotation(
                actorTransform.getYaw(),
                actorTransform.getPitch()
        );
    }

    private void buildAnimationPose()
    {
        this.currentAnimationPose.clear();

        if (this.bones == null)
        {
            return;
        }

        AnimationPose pose =
                AnimationPoseBuilder.build(
                        this.bones,
                        this.currentFrame
                );

        for (
                java.util.Map.Entry<String, AnimationTransform> entry :
                pose.getTransforms().entrySet()
        )
        {
            this.currentAnimationPose.setTransform(
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private void debugAnimationPose()
    {
        if (
                this.bones == null
                        || this.bones.isEmpty()
        )
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Cannot debug animation pose: "
                            + "no bones"
            );

            return;
        }

        AnimationFrameState state =
                AnimationFrameStateBuilder.build(
                        this.bones,
                        this.currentFrame
                );

        AnimationPose pose =
                AnimationPoseBuilder.build(
                        this.bones,
                        state
                );

        System.out.println("");
        System.out.println(
                "=============================================="
        );
        System.out.println(
                "[BBS Animation Editor] ANIMATION POSE DEBUG"
        );
        System.out.println(
                "=============================================="
        );

        System.out.println(
                "[BBS Animation Editor] Frame: "
                        + this.currentFrame
        );

        System.out.println(
                "[BBS Animation Editor] Bones: "
                        + this.bones.size()
        );

        for (
                AnimationBone bone :
                this.bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            /*
             * Дополнительная диагностика.
             *
             * Показываем:
             *
             * LOCAL =
             * статическая точка привязки Blockbuster.
             *
             * KEYFRAMES =
             * количество пользовательских keyframe
             * именно у этой кости.
             *
             * Это позволяет определить,
             * не повреждается ли иерархия
             * при работе с Timeline.
             */
            System.out.println(
                    "[BBS Animation Editor] "
                            + bone.getName()
                            + " LOCAL=("
                            + bone.getLocalX()
                            + ", "
                            + bone.getLocalY()
                            + ", "
                            + bone.getLocalZ()
                            + ")"
                            + " KEYFRAMES="
                            + bone.getKeyframes().size()
            );

            AnimationTransform transform =
                    pose.getTransform(
                            bone.getName()
                    );

            if (transform == null)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + bone.getName()
                                + " -> NO TRANSFORM"
                );

                continue;
            }

            String parentName =
                    bone.getParent() == null
                            ? "ROOT"
                            : bone.getParent().getName();

            System.out.println(
                    "[BBS Animation Editor] "
                            + bone.getName()
                            + " parent="
                            + parentName
                            + " WORLD=("
                            + transform.getPositionX()
                            + ", "
                            + transform.getPositionY()
                            + ", "
                            + transform.getPositionZ()
                            + ") ROT=("
                            + transform.getRotationX()
                            + ", "
                            + transform.getRotationY()
                            + ", "
                            + transform.getRotationZ()
                            + ") SCALE=("
                            + transform.getScaleX()
                            + ", "
                            + transform.getScaleY()
                            + ", "
                            + transform.getScaleZ()
                            + ")"
            );
        }

        System.out.println(
                "=============================================="
        );
        System.out.println("");
    }

    private void applyAnimationPose()
    {
        /*
         * Положение и вращение самого актёра
         * полностью определяется Blockbuster Record.
         *
         * AnimationBone не изменяет мировое положение
         * ActorPose.
         */
        this.currentRecordPose.applyTo(
                this.currentActorPose
        );
    }

    private void resetTimeline(int length)
    {
        this.currentFrame = 0;
        this.timelineOffset = 0;
        this.playing = false;
        this.selectedKeyframe = null;
        this.transformDragging = false;

        this.timeline.setLength(
                Math.max(
                        1,
                        length
                )
        );

        this.timeline.rewind();
        this.timeline.pause();

        applyRecordFrame();
        applyAnimationPose();
    }

    private void loadScene(int index)
    {
        try
        {
            if (!this.sceneState.loadScene(index))
            {
                return;
            }

            this.sceneDropdownOpen = false;

            this.bones =
                    new ArrayList<AnimationBone>();

            this.selectedBone = 0;
            this.selectedKeyframe = null;
            this.transformDragging = false;

            this.animationPreview.setReferencePosition(
                    0.0D,
                    0.0D,
                    0.0D
            );

            resetTimeline(
                    getSceneLength()
            );
        }
        catch (IOException exception)
        {
            exception.printStackTrace();
        }
    }

    private int getSceneLength()
    {
        if (this.sceneState == null)
        {
            return 0;
        }

        return this.sceneState.getSceneLength();
    }

    private List<BlockbusterSceneActorData>
    getSceneActors()
    {
        if (this.sceneState == null)
        {
            return new ArrayList<BlockbusterSceneActorData>();
        }

        return this.sceneState.getActors();
    }

    private BlockbusterSceneActorData
    getSelectedActor()
    {
        if (this.sceneState == null)
        {
            return null;
        }

        return this.sceneState.getSelectedActorData();
    }

    private BlockbusterRecord
    getSelectedActorRecord()
    {
        if (this.sceneState == null)
        {
            return null;
        }

        return this.sceneState.getSelectedActorRecord();
    }

    private BlockbusterRecordFrame getCurrentRecordFrame()
    {
        BlockbusterRecord record =
                getSelectedActorRecord();

        if (record == null)
        {
            return null;
        }

        return record.getFrame(
                this.currentFrame
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

        this.sceneState.setSelectedActor(index);

        selectActorAnimation(
                data.getId()
        );

        this.currentFrame = 0;
        this.timelineOffset = 0;
        this.playing = false;
        this.selectedKeyframe = null;
        this.transformDragging = false;

        this.timeline.setTick(0);
        this.timeline.pause();

        /*
         * The first Record frame becomes the
         * Preview reference point.
         */
        resetPreviewReference();

        applyRecordFrame();
        applyAnimationPose();
    }

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
        drawPreview();
        drawTimeline();

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

        if (this.sceneState != null)
        {
            int selectedScene =
                    this.sceneState.getSelectedScene();

            List<File> sceneFiles =
                    this.sceneState.getSceneFiles();

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

        this.drawString(
                this.fontRenderer,
                "Scene: "
                        + sceneName
                        + " ▼",
                sceneButtonX + 8,
                8,
                0xFFFFFF
        );

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

        this.drawString(
                this.fontRenderer,
                "Frame: "
                        + this.currentFrame,
                this.width - 100,
                8,
                0xCCCCCC
        );
    }

    private void drawSceneDropdown()
    {
        int x = 175;
        int y = TOP_BAR_HEIGHT + 2;
        int width = 180;
        int rowHeight = 18;
        int maxVisible = 8;

        List<File> sceneFiles =
                this.sceneState.getSceneFiles();

        int selectedScene =
                this.sceneState.getSelectedScene();

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

        if (sceneFiles.isEmpty())
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
                            i * rowHeight;

            boolean selected =
                    i == selectedScene;

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
                        ) + "...";
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

        final int actorRowHeight = 31;

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
                    i == this.sceneState.getSelectedActor();

            if (selected)
            {
                this.drawRect(
                        5,
                        actorY - 2,
                        LEFT_PANEL_WIDTH - 5,
                        actorY +
                                actorRowHeight -
                                2,
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

            actorY += actorRowHeight;
        }
    }

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
                        this.getTimelineHeight();

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
                Math.max(
                        1,
                        bottom - top
                );

        this.animationPreview.setBounds(
                left,
                top,
                previewWidth,
                previewHeight
        );

        this.animationPreview.draw(
                this.mc,
                this.currentActorPose,
                this.bones,
                this.currentFrame
        );

        int panelX =
                right - 185;

        int panelY =
                top + 10;

        this.transformPanel.setPosition(
                panelX,
                panelY
        );

        this.transformPanel.draw(
                this.mc,
                this.selectedKeyframe,
                getCurrentTransform(),
                this.currentFrame
        );
    }

    private void drawTimeline()
    {
        int timelineTop =
                this.height -
                        this.getTimelineHeight();

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

        for (
                int i = 0;
                i < this.bones.size();
                i++
        )
        {
            AnimationBone bone =
                    this.bones.get(i);

            int trackY =
                    tracksTop +
                            i * TRACK_HEIGHT;

            if (
                    trackY >
                            this.height - 25
            )
            {
                break;
            }

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

    private int getTimelineHeight()
    {
        int requiredHeight =
                35 +
                        this.bones.size() *
                                TRACK_HEIGHT +
                        25;

        return Math.min(
                220,
                Math.max(
                        180,
                        requiredHeight
                )
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
                        EditorTimeline.TICKS_PER_SECOND
        )
        {
            maximumFrame =
                    EditorTimeline.TICKS_PER_SECOND;
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
                            EditorTimeline.TICKS_PER_SECOND
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
                                EditorTimeline.TICKS_PER_SECOND;

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

        this.drawString(
                this.fontRenderer,
                bone.getName(),
                10,
                trackY + 5,
                selected
                        ? 0xFFFFFF
                        : 0xAAAAAA
        );

        this.drawRect(
                timelineStartX - 1,
                trackY,
                timelineStartX,
                trackY + TRACK_HEIGHT,
                0xFF555555
        );

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

            drawKeyframe(
                    x,
                    trackY + 10,
                    keyframe ==
                            this.selectedKeyframe
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

    private float getPixelsPerFrame()
    {
        return BASE_FRAME_WIDTH *
                this.timelineZoom;
    }

    private int getFrameX(
            int frame)
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
        if (bone == null)
        {
            return null;
        }

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
            int radius)
    {
        if (bone == null)
        {
            return null;
        }

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
        int maximum =
                FIRST_FRAME;

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
            if (bone == null)
            {
                continue;
            }

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

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws IOException
    {
        /*
         * Любой новый mouse click сначала
         * прекращает предыдущий Transform drag.
         */
        this.transformDragging = false;

        int sceneButtonX = 175;
        int sceneButtonWidth = 180;

        /*
         * =========================
         * SCENE DROPDOWN
         * =========================
         */

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

        if (this.sceneDropdownOpen)
        {
            int x = 175;
            int y = TOP_BAR_HEIGHT + 2;
            int width = 180;
            int rowHeight = 18;

            int count =
                    Math.min(
                            8,
                            this.sceneState
                                    .getSceneFiles()
                                    .size()
                    );

            if (
                    mouseX >= x &&
                            mouseX <=
                                    x + width &&
                            mouseY >= y &&
                            mouseY <
                                    y +
                                            count *
                                                    rowHeight
            )
            {
                int sceneIndex =
                        (mouseY - y) /
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

            this.sceneDropdownOpen = false;
        }

        /*
         * =========================
         * ACTOR PANEL
         * =========================
         */

        int actorTop =
                TOP_BAR_HEIGHT;

        int actorBottom =
                actorTop +
                        ACTOR_PANEL_HEIGHT;

        final int actorRowHeight = 31;

        if (
                mouseX >= 0 &&
                        mouseX <=
                                LEFT_PANEL_WIDTH &&
                        mouseY >=
                                actorTop + 30 &&
                        mouseY <
                                actorBottom
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
         * =========================
         * TRANSFORM PANEL
         * =========================
         *
         * TransformPanel находится справа
         * поверх Preview, поэтому его нужно
         * обработать ДО Timeline.
         */

        if (this.selectedKeyframe != null)
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
                        this.transformPanel.mouseClicked(
                                mouseX,
                                mouseY,
                                mouseButton,
                                this.selectedKeyframe
                        )
                )
                {
                    /*
                     * Только здесь начинается настоящий
                     * drag TransformPanel.
                     */
                    this.transformDragging = true;

                    return;
                }
            }
        }

        /*
         * =========================
         * TIMELINE
         * =========================
         */

        int timelineTop =
                this.height -
                        this.getTimelineHeight();

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
                /*
                 * Timeline selection всегда
                 * прекращает Transform drag.
                 */
                this.transformDragging = false;

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
                            findKeyframeAt(
                                    bone,
                                    mouseX,
                                    6
                            );

                    if (clickedKeyframe != null)
                    {
                        this.selectedKeyframe =
                                clickedKeyframe;

                        this.timeline.setTick(
                                clickedKeyframe
                                        .getFrame()
                        );

                        this.currentFrame =
                                this.timeline.getTick();

                        applyRecordFrame();
                        applyAnimationPose();
                    }
                    else
                    {
                        this.timeline.setTick(
                                frame
                        );

                        this.currentFrame =
                                this.timeline.getTick();

                        applyRecordFrame();
                        applyAnimationPose();

                        AnimationKeyframe keyframe =
                                findKeyframe(
                                        bone,
                                        frame
                                );

                        if (keyframe == null)
                        {
                            bone.addKeyframe(
                                    frame
                            );

                            keyframe =
                                    findKeyframe(
                                            bone,
                                            frame
                                    );
                        }

                        this.selectedKeyframe =
                                keyframe;
                    }
                }
                else if (mouseButton == 1)
                {
                    AnimationKeyframe clickedKeyframe =
                            findKeyframeAt(
                                    bone,
                                    mouseX,
                                    6
                            );

                    if (clickedKeyframe != null)
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

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        /*
         * ВАЖНО:
         *
         * Раньше TransformPanel получал mouseDragged()
         * при любом движении мыши, если существовал
         * selectedKeyframe.
         *
         * Теперь изменение keyframe разрешено
         * только если drag действительно начался
         * внутри TransformPanel.
         */
        if (
                this.transformDragging
                        && this.selectedKeyframe != null
                        && clickedMouseButton == 0
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

        /*
         * После отпускания мыши drag больше
         * не должен продолжаться.
         */
        this.transformDragging = false;

        super.mouseReleased(
                mouseX,
                mouseY,
                state
        );
    }

    @Override
    public void handleMouseInput()
            throws IOException
    {
        super.handleMouseInput();

        int wheel =
                Mouse.getEventDWheel();

        if (wheel == 0)
        {
            return;
        }

        int mouseX =
                Mouse.getEventX()
                        * this.width
                        / this.mc.displayWidth;

        int mouseY =
                this.height
                        - Mouse.getEventY()
                        * this.height
                        / this.mc.displayHeight
                        - 1;

        int timelineTop =
                this.height -
                        this.getTimelineHeight();

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

            this.timelineZoom =
                    Math.max(
                            MIN_TIMELINE_ZOOM,
                            Math.min(
                                    MAX_TIMELINE_ZOOM,
                                    this.timelineZoom
                            )
                    );

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

        this.timelineOffset =
                Math.max(
                        0,
                        Math.min(
                                this.timelineOffset,
                                maxOffset
                        )
                );
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws IOException
    {
        if (keyCode == 1)
        {
            this.mc.displayGuiScreen(
                    null
            );

            return;
        }

        this.transformPanel.keyTyped(
                typedChar,
                keyCode,
                this.selectedKeyframe
        );

        if (keyCode == 203)
        {
            this.timeline.previousTick();
            this.currentFrame =
                    this.timeline.getTick();

            applyRecordFrame();
            applyAnimationPose();
        }

        if (keyCode == 205)
        {
            this.timeline.nextTick();
            this.currentFrame =
                    this.timeline.getTick();

            applyRecordFrame();
            applyAnimationPose();
        }

        if (keyCode == 57)
        {
            this.timeline.togglePlaying();

            this.playing =
                    this.timeline.isPlaying();
        }
    }

    @Override
    public void updateScreen()
    {
        super.updateScreen();

        this.timeline.update();

        this.currentFrame =
                this.timeline.getTick();

        this.playing =
                this.timeline.isPlaying();

        applyRecordFrame();
        applyAnimationPose();
        buildAnimationPose();
        applyAdapters();
    }

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

        /*
         * Если выбранный keyframe действительно
         * принадлежит этой кости — используем его
         * transform.
         *
         * Если keyframe выбран от другой кости,
         * полностью игнорируем его.
         *
         * Это защищает редактор от ситуации,
         * когда selectedKeyframe остаётся после
         * переключения строки Timeline.
         */
        if (this.selectedKeyframe != null)
        {
            boolean belongsToBone =
                    false;

            for (
                    AnimationKeyframe keyframe :
                    bone.getKeyframes()
            )
            {
                if (keyframe == this.selectedKeyframe)
                {
                    belongsToBone = true;
                    break;
                }
            }

            if (!belongsToBone)
            {
                return new AnimationTransform();
            }
        }

        return bone.getTransformAt(
                this.currentFrame
        );
    }

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

        if (
                this.bones == null
                        || this.bones.isEmpty()
        )
        {
            return;
        }

        java.util.List<AnimationBoneSnapshot>
                snapshots =
                new java.util.ArrayList<AnimationBoneSnapshot>();

        for (
                AnimationBone bone :
                this.bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            /*
             * Получаем именно пользовательскую
             * локальную трансформацию этой кости.
             *
             * Не getWorldTransformAt().
             *
             * Blockbuster самостоятельно применит
             * parent hierarchy.
             */
            AnimationTransform transform =
                    bone.getTransformAt(
                            this.currentFrame
                    );

            if (transform == null)
            {
                continue;
            }

            String parentName =
                    null;

            if (bone.getParent() != null)
            {
                parentName =
                        bone.getParent()
                                .getName();
            }

            AnimationBoneSnapshot snapshot =
                    new AnimationBoneSnapshot(
                            bone.getName(),
                            parentName,
                            transform
                    );

            snapshots.add(
                    snapshot
            );
        }

        adapter.apply(
                snapshots,
                this.currentFrame
        );
    }
}
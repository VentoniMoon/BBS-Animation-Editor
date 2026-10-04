package com.example.examplemod;

import net.minecraft.client.gui.GuiScreen;

import mchorse.blockbuster.common.entity.EntityActor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

public class AnimationEditorScreen extends GuiScreen
{
    /*
     * =========================================================
     * LAYOUT
     * =========================================================
     */

    public static final int TOP_BAR_HEIGHT = 25;
    public static final int LEFT_PANEL_WIDTH = 180;
    public static final int ACTOR_PANEL_HEIGHT = 150;
    public static final int INTERPOLATION_PANEL_HEIGHT = 100;

    public static final int PREVIEW_BUTTON_WIDTH = 30;
    public static final int PREVIEW_BUTTON_HEIGHT = 20;
    public static final int PREVIEW_BUTTON_GAP = 5;

    /*
     * =========================================================
     * COLORS
     * =========================================================
     */

    private static final int COLOR_PANEL = 0xFF181818;
    private static final int COLOR_PANEL_DARK = 0xFF111111;
    private static final int COLOR_PANEL_LIGHT = 0xFF1D1D1D;
    private static final int COLOR_PANEL_HOVER = 0xFF252525;
    private static final int COLOR_SELECTED = 0xFF282828;
    private static final int COLOR_BORDER = 0xFF303030;
    private static final int COLOR_BORDER_DARK = 0xFF0D0F10;
    private static final int COLOR_TEXT = 0xFFE2E5E7;
    private static final int COLOR_TEXT_SECONDARY = 0xFF9AA1A6;
    private static final int COLOR_TEXT_MUTED = 0xFF666D72;

    /*
     * =========================================================
     * CONTROLLERS
     * =========================================================
     */

    private final EditorModeController editorModeController =
            new EditorModeController();

    private final EditorThemeController editorThemeController =
            new EditorThemeController();

    private final EditorActorPreviewController actorPreviewController =
            new EditorActorPreviewController();

    private final CharacterStateResolver characterStateResolver =
            new CharacterStateResolver();

    private final CharacterAnimationSetupRuntimeController
            characterAnimationSetupRuntimeController =
            new CharacterAnimationSetupRuntimeController();

    private final BlockbusterCharacterGuiBridge characterGuiBridge =
            new BlockbusterCharacterGuiBridge(
                    Minecraft.getMinecraft(),
                    this
            );

    private final AnimationDeleteDialog deleteDialog =
            new AnimationDeleteDialog();

    private final EditorTimeline timeline =
            new EditorTimeline();

    private final EditorPlaybackController playbackController =
            new EditorPlaybackController(timeline);

    private final CharacterEditorController characterController =
            new CharacterEditorController(playbackController);

    private final CharacterEditorPanel characterEditorPanel =
            new CharacterEditorPanel(characterController);

    private final CharacterTimelineEditorController
            characterTimelineEditorController =
            new CharacterTimelineEditorController(
                    timeline,
                    playbackController
            );

    private final EditorKeyframeController keyframeController =
            new EditorKeyframeController(
                    timeline,
                    LEFT_PANEL_WIDTH
            );

    private final EditorTimelineController timelineController =
            new EditorTimelineController(
                    timeline,
                    keyframeController,
                    playbackController,
                    actorPreviewController
            );

    private final EditorRecordController recordController =
            new EditorRecordController(playbackController);

    private final InterpolationPanel interpolationPanel =
            new InterpolationPanel();

    private final BodyPartsEditorController bodyPartsController =
            new BodyPartsEditorController();

    private final BodyPartsEditorPanel bodyPartsEditorPanel =
            new BodyPartsEditorPanel(bodyPartsController);

    private final BodyPartsTimelineController bodyPartsTimelineController =
            new BodyPartsTimelineController(bodyPartsController);

    private final EditorActorListPanel actorListPanel =
            new EditorActorListPanel();


    /*
     * =========================================================
     * EDITOR STATE
     * =========================================================
     */

    private EditorSceneState sceneState;
    private EditorSceneViewport sceneViewport;

    /*
     * Stage 5 - 3D transform gizmo.
     *
     * The gizmo is an editor overlay and does not own animation data.
     */
    private final EditorGizmoController gizmoController =
            new EditorGizmoController();

    private AnimationEditorInput editorInput;

    /*
     * =========================================================
     * THEME
     * =========================================================
     */

    private static int getAccentColor()
    {
        return EditorThemeManager.get().getAccent();
    }

    private static int getAccentBrightColor()
    {
        return EditorThemeManager.get().getAccentBright();
    }

    /*
     * =========================================================
     * INIT
     * =========================================================
     */

    @Override
    public void initGui()
    {
        super.initGui();

        /*
         * =========================================================
         * RETURN FROM CHILD GUI
         * =========================================================
         *
         * GuiScreen Minecraft повторно вызывает initGui() при
         * возврате из дочернего GUI.
         *
         * ВАЖНО:
         *
         * Если EditorSceneState уже существует, это НЕ новое
         * открытие редактора.
         *
         * В этом случае нельзя:
         *
         * - создавать новую сцену;
         * - сбрасывать выбранного актёра;
         * - сбрасывать timeline;
         * - создавать новый viewport;
         * - сбрасывать камеру;
         * - очищать animation data.
         *
         * Мы возвращаемся именно в тот редактор, который был открыт.
         */

        boolean returningToExistingEditor =
                this.sceneState != null;

        /*
         * =========================================================
         * FIRST EDITOR OPEN
         * =========================================================
         */

        if (!returningToExistingEditor)
        {
            BlockbusterPreviewAnimationState.clear();
            EmoticonsPreviewAnimationState.clear();

            this.sceneState =
                    new EditorSceneState();

            this.sceneState.refreshScenes();

            this.characterEditorPanel.setGuiBridge(
                    this.characterGuiBridge
            );

            this.bodyPartsEditorPanel.setScreen(this);

            this.sceneViewport =
                    new EditorSceneViewport();

            this.sceneViewport.setBodyPartsController(
                    this.bodyPartsController
            );

            this.actorPreviewController.initializeAdapters();

            /*
             * ---------------------------------------------------------
             * INPUT
             * ---------------------------------------------------------
             */

            this.editorInput =
                    new AnimationEditorInput(
                            this,
                            this.editorModeController,
                            this.editorThemeController,
                            this.sceneState,
                            this.sceneViewport,
                            this.characterGuiBridge,
                            this.deleteDialog,
                            this.timeline,
                            this.playbackController,
                            this.characterEditorPanel,
                            this.characterTimelineEditorController,
                            this.keyframeController,
                            this.timelineController,
                            this.actorPreviewController,
                            this.recordController,
                            this.interpolationPanel,
                            this.bodyPartsController,
                            this.bodyPartsEditorPanel,
                            this.bodyPartsTimelineController,
                            this.gizmoController
                    );

            this.editorInput.resetInputState();

            /*
             * ---------------------------------------------------------
             * INTERPOLATION
             * ---------------------------------------------------------
             */

            this.interpolationPanel.setInterpolationChanged(
                    new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            if (editorModeController.getMode()
                                    != EditorModeController.EditorMode.POSE)
                            {
                                return;
                            }

                            AnimationKeyframe keyframe =
                                    keyframeController
                                            .getSelectedKeyframe();

                            if (keyframe != null)
                            {
                                keyframe.setInterpolation(
                                        interpolationPanel
                                                .getSelectedInterpolation()
                                );

                                keyframe.setEasing(
                                        interpolationPanel
                                                .getSelectedEasing()
                                );

                                markEditorDirty();
                            }
                        }
                    }
            );

            /*
             * ---------------------------------------------------------
             * INITIAL EDITOR STATE
             * ---------------------------------------------------------
             */

            this.editorModeController.closeDropdown();
            this.editorThemeController.closeDropdown();

            syncCharacterTimelineActor();
            syncCharacterEditor();

            resetTimeline(1);

            return;
        }

        /*
         * =========================================================
         * RETURN TO EXISTING EDITOR
         * =========================================================
         */

        if (this.editorInput != null)
        {
            this.editorInput.resetInputState();
        }

        this.editorModeController.closeDropdown();
        this.editorThemeController.closeDropdown();

        /*
         * =========================================================
         * RESTORE ACTIVE ACTOR
         * =========================================================
         */

        syncCharacterTimelineActor();
        syncCharacterEditor();

        /*
         * =========================================================
         * RESTORE CURRENT FRAME
         * =========================================================
         */

        applyRecordFrame();

        if (!this.playbackController.isPlaying())
        {
            applyAdapters();
        }

        applyCharacterState();

        /*
         * =========================================================
         * RESTORE VIEWPORT POSITION
         * =========================================================
         */

        updateSceneViewportPosition();
    }

    /*
     * =========================================================
     * GIZMO
     * =========================================================
     */

    public boolean isGizmoEnabled()
    {
        EditorModeController.EditorMode mode =
                this.editorModeController.getMode();

        return mode ==
                EditorModeController.EditorMode.POSE
                ||
                mode ==
                EditorModeController.EditorMode.BODY_PARTS;
    }

    public AnimationBone getGizmoBone()
    {
        if (!isGizmoEnabled())
        {
            return null;
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            AnimationKeyframe selected =
                    this.keyframeController
                            .getSelectedKeyframe();

            AnimationBone owner =
                    this.keyframeController
                            .getSelectedKeyframeBone();

            if (owner != null && selected != null)
            {
                return owner;
            }

            if (selected != null)
            {
                List<AnimationBone> bones =
                        this.actorPreviewController
                                .getBones();

                if (bones != null)
                {
                    for (AnimationBone bone : bones)
                    {
                        if (bone == null
                                || bone.getKeyframes() == null)
                        {
                            continue;
                        }

                        for (AnimationKeyframe keyframe :
                                bone.getKeyframes())
                        {
                            if (keyframe == selected)
                            {
                                return bone;
                            }
                        }
                    }
                }
            }

            return this.actorPreviewController
                    .getSelectedBone(
                            this.keyframeController
                    );
        }

        BodyPartModelData model =
                this.bodyPartsController
                        .getSelectedModel();

        if (model != null)
        {
            AnimationKeyframe selected =
                    this.bodyPartsController
                            .getKeyframeController()
                            .getSelectedKeyframe();

            if (selected != null)
            {
                AnimationBone selectedBone =
                        this.bodyPartsController
                                .getKeyframeController()
                                .getSelectedBone(
                                        model.getBones()
                                );

                if (selectedBone != null)
                {
                    return selectedBone;
                }
            }

            /*
             * Level 1 / Global Transform operates on the attachment
             * bone itself.
             */
            return findBodyPartAttachmentBone(model);
        }

        BodyPartModelData attachmentModel =
                this.bodyPartsController
                        .getSelectedAttachment();

        if (attachmentModel == null)
        {
            return null;
        }

        return findBodyPartAttachmentBone(attachmentModel);
    }

    private AnimationBone findBodyPartAttachmentBone(
            BodyPartModelData model)
    {
        if (model == null ||
                model.getAttachmentBoneName() == null)
        {
            return null;
        }

        for (AnimationBone bone :
                this.bodyPartsController.getActorBones())
        {
            if (bone != null &&
                    model.getAttachmentBoneName().equals(
                            bone.getName()))
            {
                return bone;
            }
        }

        return null;
    }

    public BodyPartModelData getGizmoBodyPartModel()
    {
        if (this.editorModeController.getMode()
                != EditorModeController.EditorMode.BODY_PARTS)
        {
            return null;
        }

        return this.bodyPartsController.getSelectedModel();
    }

    public AnimationBone getGizmoBodyPartAttachmentBone()
    {
        if (this.editorModeController.getMode()
                != EditorModeController.EditorMode.BODY_PARTS)
        {
            return null;
        }

        BodyPartModelData model =
                this.bodyPartsController.getSelectedModel();

        if (model == null)
        {
            model = this.bodyPartsController.getSelectedAttachment();
        }

        return findBodyPartAttachmentBone(model);
    }

    public AnimationTransform getGizmoGlobalTransform()
    {
        if (this.editorModeController.getMode()
                != EditorModeController.EditorMode.BODY_PARTS
                || this.bodyPartsController.getSelectedModel() != null)
        {
            return null;
        }

        BodyPartModelData model =
                this.bodyPartsController.getSelectedAttachment();

        return model == null
                ? null
                : model.getGlobalTransform();
    }

    public void prepareGizmoTarget()
    {
        boolean chameleonGizmoTarget =
                this.actorPreviewController.isCurrentActorChameleon();

        BodyPartModelData gizmoBodyPart =
                this.bodyPartsController.getSelectedModel();

        if (gizmoBodyPart == null)
        {
            gizmoBodyPart =
                    this.bodyPartsController.getSelectedAttachment();
        }

        if (gizmoBodyPart != null &&
                gizmoBodyPart.getBaseMorph() != null)
        {
            chameleonGizmoTarget =
                    gizmoBodyPart.getBaseMorph()
                            .getClass()
                            .getName()
                            .endsWith(".ChameleonMorph");
        }

        this.gizmoController.setChameleonCoordinateSpace(
                chameleonGizmoTarget
        );

        this.gizmoController.setBodyPartTarget(
                getGizmoBodyPartModel(),
                getGizmoBodyPartAttachmentBone()
        );

        this.gizmoController.setGlobalTransformTarget(
                getGizmoGlobalTransform()
        );

        this.gizmoController.setGizmoFrame(
                this.playbackController.getCurrentFrame()
        );
    }

    public AnimationKeyframe getGizmoKeyframe()
    {
        if (!isGizmoEnabled())
        {
            return null;
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            AnimationKeyframe selected =
                    this.keyframeController
                            .getSelectedKeyframe();

            if (selected != null)
            {
                return selected;
            }

            /*
             * A key created directly on the timeline is the gizmo
             * target even if another timeline operation cleared the
             * transient selectedKeyframe reference afterwards.
             *
             * Resolve the key from the currently selected bone and
             * the actual editor frame.  This keeps the gizmo tied to
             * the animation data, not to a fragile UI selection flag.
             */
            AnimationBone bone =
                    this.actorPreviewController
                            .getSelectedBone(
                                    this.keyframeController
                            );

            if (bone != null)
            {
                return this.keyframeController
                        .findKeyframe(
                                bone,
                                this.playbackController
                                        .getCurrentFrame()
                        );
            }

            return null;
        }

        AnimationKeyframe selected =
                this.bodyPartsController
                        .getKeyframeController()
                        .getSelectedKeyframe();

        if (selected != null)
        {
            return selected;
        }

        return null;
    }

    /*
     * =========================================================
     * SAVE
     * =========================================================
     */

    public EditorSaveController getSaveController()
    {
        if (this.sceneState == null)
        {
            return null;
        }

        return this.sceneState.getSaveController();
    }

    public void markEditorDirty()
    {
        EditorSaveController saveController =
                getSaveController();

        if (saveController != null)
        {
            saveController.markDirty();
        }
    }

    public boolean isEditorDirty()
    {
        EditorSaveController saveController =
                getSaveController();

        return saveController != null
                && saveController.isDirty();
    }

    public boolean saveEditor()
    {
        EditorSaveController saveController =
                getSaveController();

        if (saveController == null)
        {
            return false;
        }

        try
        {
            saveController.save();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * =========================================================
     * CHARACTER SYNC
     * =========================================================
     */

    public void syncCharacterTimelineActor()
    {
        this.characterTimelineEditorController.setSelectedActor(
                getSelectedActor()
        );

        this.characterTimelineEditorController.setBodyPartModels(
                this.bodyPartsController.getModels()
        );

        BlockbusterSceneActorData actor =
                getSelectedActor();

        if (actor != null)
        {
            actor.getCharacterTimeline()
                    .syncBodyPartTracks(
                            this.bodyPartsController.getModels()
                    );
        }
    }

    public void syncCharacterEditor()
    {
        BlockbusterSceneActorData selectedActor =
                getSelectedActor();

        this.characterEditorPanel.setSelectedActor(
                selectedActor
        );

        this.characterEditorPanel.setRuntimeActor(
                findRuntimeActor()
        );

        /*
         * =========================================================
         * CHARACTER KEY
         * =========================================================
         *
         * Character Timeline уже хранит настоящий выбранный
         * CharacterKey.
         *
         * currentFrame НЕ является выбранным ключом.
         *
         * Например:
         *
         * selectedKey -> frame 40
         * currentFrame -> 80
         *
         * Ключ всё равно остаётся на frame 40.
         */
        this.characterEditorPanel.setCurrentFrame(
                this.playbackController.getCurrentFrame()
        );

        this.characterEditorPanel.setSelectedKey(
                this.characterTimelineEditorController
                        .getSelectedKey()
        );

        this.characterEditorPanel.setSelectedBodyPartModel(
                this.characterTimelineEditorController
                        .getSelectedBodyPartModel()
        );
    }

    private void applyCharacterState()
    {
        BlockbusterSceneActorData data =
                getSelectedActor();

        EntityActor actor =
                findRuntimeActor();


        if (data == null || actor == null)
        {
            return;
        }


        this.characterStateResolver.apply(
                data,
                actor,
                this.playbackController.getCurrentFrame()
        );

        this.characterAnimationSetupRuntimeController.apply(
                data,
                actor,
                this.playbackController.getCurrentFrame()
        );
    }

    public void refreshCharacterState()
    {
        /*
         * Перечитываем выбранный CharacterKey
         */
        syncCharacterTimelineActor();

        syncCharacterEditor();


        /*
         * Принудительно применяем новый Morph
         */
        this.characterStateResolver.reset();

        applyCharacterState();


        /*
         * Обновляем реального Actor в Preview
         */
        EntityActor actor =
                findRuntimeActor();

        if (actor != null)
        {
            BlockbusterSceneActorData data =
                    getSelectedActor();

            if (data != null)
            {
                this.characterStateResolver.apply(
                        data,
                        actor,
                        this.playbackController.getCurrentFrame()
                );
            }
        }
    }

    private EntityActor findRuntimeActor()
    {
        /*
         * =========================================================
         * PRIMARY SOURCE
         * =========================================================
         *
         * Character Timeline должен работать с тем же EntityActor,
         * которого реально рисует BlockbusterActorPreviewRenderer.
         *
         * Раньше здесь выполнялся общий поиск любого EntityActor
         * внутри sceneViewport. Это мог вернуть другой EntityActor
         * из Minecraft world.
         *
         * Поэтому сначала ищем именно BlockbusterActorPreviewRenderer.
         */

        BlockbusterActorPreviewRenderer renderer =
                findBlockbusterActorPreviewRenderer(
                        this.sceneViewport,
                        new HashSet<Object>(),
                        0
                );

        if (renderer == null)
        {
            renderer =
                    findBlockbusterActorPreviewRenderer(
                            this.actorPreviewController,
                            new HashSet<Object>(),
                            0
                    );
        }

        if (renderer != null)
        {
            EntityActor actor =
                    renderer.getActor();

            if (actor != null)
            {
                return actor;
            }
        }

        /*
         * =========================================================
         * FALLBACK
         * =========================================================
         *
         * Renderer может ещё не успеть создать EntityActor.
         *
         * В таком случае оставляем старый поиск как запасной путь.
         */

        EntityActor actor =
                findEntityActorFromObject(
                        this.sceneViewport,
                        new HashSet<Object>(),
                        0
                );

        if (actor != null)
        {
            return actor;
        }

        return findEntityActorFromObject(
                this.actorPreviewController,
                new HashSet<Object>(),
                0
        );
    }


    private BlockbusterActorPreviewRenderer
    findBlockbusterActorPreviewRenderer(
            Object object,
            Set<Object> visited,
            int depth)
    {
        if (object == null || depth > 8)
        {
            return null;
        }

        /*
         * ---------------------------------------------------------
         * DIRECT INSTANCE
         * ---------------------------------------------------------
         */

        if (object instanceof BlockbusterActorPreviewRenderer)
        {
            return (BlockbusterActorPreviewRenderer) object;
        }

        /*
         * ---------------------------------------------------------
         * VISITED
         * ---------------------------------------------------------
         */

        if (!visited.add(object))
        {
            return null;
        }

        /*
         * ---------------------------------------------------------
         * GETTERS
         * ---------------------------------------------------------
         */

        for (Method method : object.getClass().getMethods())
        {
            String name =
                    method.getName();

            if (!"getActorPreviewRenderer".equals(name)
                    && !"getBlockbusterActorPreviewRenderer".equals(name)
                    && !"getPreviewRenderer".equals(name))
            {
                continue;
            }

            if (method.getParameterTypes().length != 0)
            {
                continue;
            }

            try
            {
                Object result =
                        method.invoke(object);

                if (result instanceof BlockbusterActorPreviewRenderer)
                {
                    return (BlockbusterActorPreviewRenderer) result;
                }

                if (result != null)
                {
                    BlockbusterActorPreviewRenderer renderer =
                            findBlockbusterActorPreviewRenderer(
                                    result,
                                    visited,
                                    depth + 1
                            );

                    if (renderer != null)
                    {
                        return renderer;
                    }
                }
            }
            catch (Exception ignored)
            {
            }
        }

        /*
         * ---------------------------------------------------------
         * FIELDS
         * ---------------------------------------------------------
         */

        Class<?> current =
                object.getClass();

        while (current != null
                && current != Object.class)
        {
            for (Field field :
                    current.getDeclaredFields())
            {
                try
                {
                    field.setAccessible(true);

                    Object value =
                            field.get(object);

                    if (value instanceof
                            BlockbusterActorPreviewRenderer)
                    {
                        return
                                (BlockbusterActorPreviewRenderer)
                                        value;
                    }

                    if (value == null)
                    {
                        continue;
                    }

                    String className =
                            value.getClass().getName();

                    /*
                     * Не уходим в системные классы.
                     */

                    if (className.startsWith("java.")
                            || className.startsWith("javax.")
                            || className.startsWith("sun.")
                            || className.startsWith("com.sun."))
                    {
                        continue;
                    }

                    BlockbusterActorPreviewRenderer renderer =
                            findBlockbusterActorPreviewRenderer(
                                    value,
                                    visited,
                                    depth + 1
                            );

                    if (renderer != null)
                    {
                        return renderer;
                    }
                }
                catch (Exception ignored)
                {
                }
            }

            current =
                    current.getSuperclass();
        }

        return null;
    }


    private EntityActor findEntityActorFromObject(
            Object object,
            Set<Object> visited,
            int depth)
    {
        if (object == null || depth > 6)
        {
            return null;
        }

        if (object instanceof EntityActor)
        {
            return (EntityActor) object;
        }

        if (!visited.add(object))
        {
            return null;
        }

        /*
         * Сначала проверяем публичные getter'ы.
         */

        for (Method method : object.getClass().getMethods())
        {
            String name =
                    method.getName();

            if (!"getActor".equals(name)
                    && !"getRuntimeActor".equals(name)
                    && !"getEntityActor".equals(name))
            {
                continue;
            }

            if (method.getParameterTypes().length != 0)
            {
                continue;
            }

            try
            {
                Object result =
                        method.invoke(object);

                if (result instanceof EntityActor)
                {
                    return (EntityActor) result;
                }

                if (result != null)
                {
                    EntityActor actor =
                            findEntityActorFromObject(
                                    result,
                                    visited,
                                    depth + 1
                            );

                    if (actor != null)
                    {
                        return actor;
                    }
                }
            }
            catch (Exception ignored)
            {
            }
        }

        /*
         * Затем ищем EntityActor в полях.
         */

        Class<?> current =
                object.getClass();

        while (current != null
                && current != Object.class)
        {
            for (Field field :
                    current.getDeclaredFields())
            {
                try
                {
                    field.setAccessible(true);

                    Object value =
                            field.get(object);

                    if (value instanceof EntityActor)
                    {
                        return (EntityActor) value;
                    }

                    if (value == null)
                    {
                        continue;
                    }

                    String className =
                            value.getClass().getName();

                    /*
                     * Не уходим в JDK-классы.
                     */

                    if (className.startsWith("java.")
                            || className.startsWith("javax.")
                            || className.startsWith("sun.")
                            || className.startsWith("com.sun."))
                    {
                        continue;
                    }

                    EntityActor actor =
                            findEntityActorFromObject(
                                    value,
                                    visited,
                                    depth + 1
                            );

                    if (actor != null)
                    {
                        return actor;
                    }
                }
                catch (Exception ignored)
                {
                }
            }

            current =
                    current.getSuperclass();
        }

        return null;
    }

    /*
     * =========================================================
     * PUBLIC EDITOR ACCESS
     * =========================================================
     *
     * Эти методы используются внешними GUI/Bridge-классами.
     *
     * Текущий кадр хранится внутри EditorPlaybackController,
     * а не в отдельном поле currentFrame этого класса.
     *
     * Поэтому внешние системы получают кадр через этот метод,
     * без reflection.
     */

    public int getCurrentFrame()
    {
        return this.playbackController.getCurrentFrame();
    }

    public EditorSceneState getSceneState()
    {
        return this.sceneState;
    }

    /*
     * =========================================================
     * RECORD / ANIMATION
     * =========================================================
     */

    public void applyRecordFrame()
    {
        this.recordController.applyRecordFrame(
                getSelectedActorRecord()
        );

        this.recordController.applyAnimationPose();
    }

    public void applyAdapters()
    {
        this.actorPreviewController.applyAdapters(
                this.playbackController.getCurrentFrame()
        );
    }

    public void applyAdapters(float animationFrame)
    {
        this.actorPreviewController.applyAdapters(
                animationFrame
        );
    }

    private void resetTimeline(int length)
    {
        this.playbackController.setCurrentFrame(0);
        this.playbackController.pause();

        this.keyframeController.reset();
        this.timelineController.reset();

        if (this.editorInput != null)
        {
            this.editorInput.setTransformDragging(false);
        }

        this.timeline.setLength(
                Math.max(1, length)
        );

        this.timeline.rewind();
        this.timeline.pause();
        this.timeline.resetOffset();
        this.timeline.setZoom(1.0F);

        BlockbusterPreviewAnimationState.clear();
        EmoticonsPreviewAnimationState.clear();

        BlockbusterPreviewAnimationState.setPlaying(false);

        syncCharacterTimelineActor();
        syncCharacterEditor();

        applyRecordFrame();
        applyAdapters();
    }

    /*
     * =========================================================
     * SCENE
     * =========================================================
     */

    public void loadScene(int index)
    {
        try
        {
            if (!this.sceneState.loadScene(index))
            {
                return;
            }

            if (this.editorInput != null)
            {
                this.editorInput.resetInputState();
            }

            this.editorThemeController.closeDropdown();
            this.editorModeController.closeDropdown();

            this.actorPreviewController.clearBones();

            this.keyframeController.reset();
            this.timelineController.reset();

            BlockbusterPreviewAnimationState.clear();
            EmoticonsPreviewAnimationState.clear();

            /*
             * EditorSceneState.loadScene() уже загрузил
             * editor data.
             */

            this.actorPreviewController
                    .getAnimationPreview()
                    .setReferencePosition(
                            0.0D,
                            0.0D,
                            0.0D
                    );

            updateSceneViewportPosition();

            syncCharacterTimelineActor();
            syncCharacterEditor();

            resetTimeline(getSceneLength());
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }
    }

    public int getSceneLength()
    {
        return this.sceneState == null
                ? 0
                : this.sceneState.getSceneLength();
    }

    public List<BlockbusterSceneActorData> getSceneActors()
    {
        return this.sceneState == null
                ? new ArrayList<BlockbusterSceneActorData>()
                : this.sceneState.getActors();
    }

    private BlockbusterSceneActorData getSelectedActor()
    {
        return this.sceneState == null
                ? null
                : this.sceneState.getSelectedActorData();
    }

    public BlockbusterSceneActorData getSelectedActorForTimeline()
    {
        return getSelectedActor();
    }

    private BlockbusterRecord getSelectedActorRecord()
    {
        return this.sceneState == null
                ? null
                : this.sceneState.getSelectedActorRecord();
    }

    public BlockbusterRecordFrame getCurrentRecordFrame()
    {
        return this.recordController.getCurrentRecordFrame(
                getSelectedActorRecord()
        );
    }

    public EditorActorListPanel getActorListPanel()
    {
        return this.actorListPanel;
    }

    public void scrollActorList(int direction)
    {
        this.actorListPanel.scroll(
                direction,
                getSceneActors()
        );
    }

    public void selectActor(int index)
    {
        List<BlockbusterSceneActorData> actors =
                getSceneActors();

        if (index < 0 || index >= actors.size())
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
        this.actorListPanel.clamp(actors);

        this.characterTimelineEditorController
                .setSelectedActor(data);

        this.characterEditorPanel
                .setSelectedActor(data);

        this.actorPreviewController
                .selectActorAnimation(
                        data.getId(),
                        this.sceneState,
                        this.keyframeController
                );

        this.bodyPartsController.setActorBones(
                this.actorPreviewController.getBones()
        );

        this.playbackController.setCurrentFrame(0);
        this.playbackController.pause();

        this.timeline.resetOffset();
        this.timelineController.reset();
        this.keyframeController.resetSelectionOnly();

        this.timeline.setTick(0);
        this.timeline.pause();

        BlockbusterPreviewAnimationState.clear();
        EmoticonsPreviewAnimationState.clear();

        BlockbusterPreviewAnimationState.setPlaying(false);

        this.actorPreviewController
                .resetPreviewReference(
                        getSelectedActorRecord()
                );

        applyRecordFrame();
        applyAdapters();

        this.characterStateResolver.reset();
        applyCharacterState();

        syncCharacterEditor();
    }

    /*
     * =========================================================
     * DRAW
     * =========================================================
     */

    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks)
    {
        drawRect(
                0,
                0,
                width,
                height,
                EditorVisualStyle.BACKGROUND
        );

        drawTopBar();
        drawActorPanel();

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            drawBodyPartsBonePanel();
        }

        drawPreview(mouseX, mouseY);
        drawTimeline();
        drawInterpolationPanel(mouseX, mouseY);

        if (this.editorInput != null
                && this.editorInput.isSceneDropdownOpen())
        {
            drawSceneDropdown(mouseX, mouseY);
        }

        if (this.editorThemeController.isDropdownOpen())
        {
            drawEditorThemeDropdown(
                    mouseX,
                    mouseY
            );
        }

        if (this.editorModeController.isDropdownOpen())
        {
            drawEditorModeDropdown(
                    mouseX,
                    mouseY
            );
        }

        super.drawScreen(
                mouseX,
                mouseY,
                partialTicks
        );

        if (isDeleteDialogOpen())
        {
            this.deleteDialog.open(
                    this.keyframeController
                            .getPendingDeleteFrame()
            );

            this.deleteDialog.draw(
                    this,
                    this.fontRenderer,
                    this.width,
                    this.height,
                    mouseX,
                    mouseY,
                    getAccentColor()
            );
        }
    }

    private void drawTopBar()
    {
        drawRect(
                0,
                0,
                width,
                TOP_BAR_HEIGHT,
                COLOR_PANEL_DARK
        );

        drawRect(
                0,
                TOP_BAR_HEIGHT - 1,
                width,
                TOP_BAR_HEIGHT,
                COLOR_BORDER
        );

        drawRect(
                8,
                TOP_BAR_HEIGHT - 2,
                145,
                TOP_BAR_HEIGHT - 1,
                getAccentColor()
        );

        EditorVisualStyle.title(
                this,
                fontRenderer,
                "BBS Animation Editor",
                10,
                8
        );

        /*
         * =========================================================
         * SCENE
         * =========================================================
         */

        int sceneButtonX = 175;
        int sceneButtonWidth = 180;

        boolean sceneHovered =
                this.editorInput != null
                        && this.editorInput.isSceneDropdownOpen();

        boolean sceneDropdownOpen =
                this.editorInput != null
                        && this.editorInput.isSceneDropdownOpen();

        drawToolbarButton(
                sceneButtonX,
                3,
                sceneButtonWidth,
                TOP_BAR_HEIGHT - 6,
                sceneHovered || sceneDropdownOpen
        );

        String sceneName = "Select Scene";

        if (sceneState != null)
        {
            int selectedScene =
                    sceneState.getSelectedScene();

            List<File> sceneFiles =
                    sceneState.getSceneFiles();

            if (selectedScene >= 0
                    && selectedScene < sceneFiles.size())
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
                    sceneName.substring(0, 19)
                            + "...";
        }

        drawString(
                fontRenderer,
                "SCENE: " + sceneName,
                sceneButtonX + 8,
                8,
                sceneDropdownOpen
                        ? getAccentBrightColor()
                        : COLOR_TEXT
        );

        drawString(
                fontRenderer,
                "▼",
                sceneButtonX +
                        sceneButtonWidth -
                        13,
                8,
                COLOR_TEXT_SECONDARY
        );

        /*
         * =========================================================
         * ACTOR
         * =========================================================
         */

        BlockbusterSceneActorData actor =
                getSelectedActor();

        int actorX =
                sceneButtonX +
                        sceneButtonWidth +
                        15;

        String actorText = "ACTOR: —";

        if (actor != null)
        {
            String actorName = actor.getId();

            if (actorName == null
                    || actorName.length() == 0)
            {
                actorName = "Unnamed";
            }

            if (actorName.length() > 20)
            {
                actorName =
                        actorName.substring(0, 17)
                                + "...";
            }

            actorText =
                    "ACTOR: " + actorName;
        }

        drawString(
                fontRenderer,
                actorText,
                actorX,
                8,
                actor != null
                        ? getAccentColor()
                        : COLOR_TEXT_MUTED
        );

        /*
         * =========================================================
         * THEME
         * =========================================================
         */

        int themeX =
                this.editorThemeController
                        .getButtonX(width);

        boolean themeHovered =
                this.editorThemeController
                        .isDropdownOpen();

        drawToolbarButton(
                themeX,
                3,
                EditorThemeController.BUTTON_WIDTH,
                TOP_BAR_HEIGHT - 6,
                themeHovered
        );

        drawString(
                fontRenderer,
                "THEME: " +
                        this.editorThemeController
                                .getThemeName(),
                themeX + 8,
                8,
                this.editorThemeController
                        .isDropdownOpen()
                        ? getAccentBrightColor()
                        : COLOR_TEXT
        );

        drawString(
                fontRenderer,
                "▼",
                themeX +
                        EditorThemeController.BUTTON_WIDTH -
                        13,
                8,
                COLOR_TEXT_SECONDARY
        );

        /*
         * =========================================================
         * MODE
         * =========================================================
         */

        int modeX =
                this.editorModeController
                        .getButtonX(width);

        boolean modeHovered =
                this.editorModeController
                        .isDropdownOpen();

        drawToolbarButton(
                modeX,
                3,
                EditorModeController.MODE_BUTTON_WIDTH,
                TOP_BAR_HEIGHT - 6,
                modeHovered
        );

        drawString(
                fontRenderer,
                "MODE: " +
                        this.editorModeController
                                .getModeName(),
                modeX + 8,
                8,
                this.editorModeController
                        .isDropdownOpen()
                        ? getAccentBrightColor()
                        : COLOR_TEXT
        );

        drawString(
                fontRenderer,
                "▼",
                modeX +
                        EditorModeController.MODE_BUTTON_WIDTH -
                        13,
                8,
                COLOR_TEXT_SECONDARY
        );

        /*
         * =========================================================
         * CURRENT FRAME
         * =========================================================
         */

        String frameText =
                "FRAME  " +
                        this.playbackController
                                .getCurrentFrame();

        int frameWidth =
                fontRenderer
                        .getStringWidth(frameText);

        drawString(
                fontRenderer,
                frameText,
                width - frameWidth - 12,
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
        drawRect(
                x,
                y,
                x + width,
                y + height,
                hovered
                        ? COLOR_PANEL_HOVER
                        : COLOR_PANEL_LIGHT
        );

        drawRect(
                x,
                y,
                x + width,
                y + 1,
                hovered
                        ? getAccentColor()
                        : COLOR_BORDER
        );

        drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                COLOR_BORDER_DARK
        );
    }

    private void drawEditorThemeDropdown(
            int mouseX,
            int mouseY)
    {
        int x =
                this.editorThemeController
                        .getDropdownX(width);

        int y =
                this.editorThemeController
                        .getDropdownY();

        int dropdownWidth =
                this.editorThemeController
                        .getDropdownWidth();

        int rowHeight =
                this.editorThemeController
                        .getDropdownRowHeight();

        int dropdownHeight =
                this.editorThemeController
                        .getDropdownHeight();

        drawDropdownFrame(
                x,
                y,
                dropdownWidth,
                dropdownHeight
        );

        EditorTheme[] themes =
                {
                        EditorThemeManager.getOcean(),
                        EditorThemeManager.getEmerald(),
                        EditorThemeManager.getRuby(),
                        EditorThemeManager.getAmethyst()
                };

        for (int i = 0; i < themes.length; i++)
        {
            EditorTheme theme = themes[i];

            int rowY =
                    y + i * rowHeight;

            boolean hovered =
                    mouseX >= x
                            && mouseX < x + dropdownWidth
                            && mouseY >= rowY
                            && mouseY < rowY + rowHeight;

            boolean selected =
                    this.editorThemeController
                            .isThemeSelected(theme);

            if (selected)
            {
                drawRect(
                        x,
                        rowY,
                        x + 2,
                        rowY + rowHeight,
                        getAccentColor()
                );

                drawRect(
                        x + 2,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        hovered
                                ? COLOR_PANEL_HOVER
                                : COLOR_SELECTED
                );
            }
            else if (hovered)
            {
                drawRect(
                        x,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        COLOR_PANEL_HOVER
                );
            }

            drawString(
                    fontRenderer,
                    theme.getName(),
                    x + 9,
                    rowY + 5,
                    selected
                            ? getAccentBrightColor()
                            : COLOR_TEXT
            );
        }
    }

    private void drawEditorModeDropdown(
            int mouseX,
            int mouseY)
    {
        int x =
                this.editorModeController
                        .getDropdownX(width);

        int y =
                this.editorModeController
                        .getDropdownY();

        int dropdownWidth =
                this.editorModeController
                        .getDropdownWidth();

        int rowHeight =
                this.editorModeController
                        .getDropdownRowHeight();

        int dropdownHeight =
                this.editorModeController
                        .getDropdownHeight();

        drawDropdownFrame(
                x,
                y,
                dropdownWidth,
                dropdownHeight
        );

        EditorModeController.EditorMode[] modes =
                {
                        EditorModeController.EditorMode.CHARACTER,
                        EditorModeController.EditorMode.POSE,
                        EditorModeController.EditorMode.BODY_PARTS
                };

        for (int i = 0; i < modes.length; i++)
        {
            EditorModeController.EditorMode mode =
                    modes[i];

            int rowY =
                    y + i * rowHeight;

            boolean hovered =
                    mouseX >= x
                            && mouseX < x + dropdownWidth
                            && mouseY >= rowY
                            && mouseY < rowY + rowHeight;

            boolean selected =
                    this.editorModeController
                            .isModeSelected(mode);

            if (selected)
            {
                drawRect(
                        x,
                        rowY,
                        x + 2,
                        rowY + rowHeight,
                        getAccentColor()
                );

                drawRect(
                        x + 2,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        hovered
                                ? COLOR_PANEL_HOVER
                                : COLOR_SELECTED
                );
            }
            else if (hovered)
            {
                drawRect(
                        x,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        COLOR_PANEL_HOVER
                );
            }

            drawString(
                    fontRenderer,
                    this.editorModeController
                            .getModeName(mode),
                    x + 9,
                    rowY + 5,
                    selected
                            ? getAccentBrightColor()
                            : COLOR_TEXT
            );
        }
    }

    private void drawSceneDropdown(
            int mouseX,
            int mouseY)
    {
        int x = 175;
        int y = TOP_BAR_HEIGHT + 2;
        int dropdownWidth = 180;
        int rowHeight = 19;
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

        int dropdownHeight =
                Math.max(
                        rowHeight,
                        count * rowHeight
                );

        drawDropdownFrame(
                x,
                y,
                dropdownWidth,
                dropdownHeight
        );

        if (sceneFiles.isEmpty())
        {
            drawString(
                    fontRenderer,
                    "No scenes found",
                    x + 9,
                    y + 6,
                    COLOR_TEXT_MUTED
            );

            return;
        }

        for (int i = 0; i < count; i++)
        {
            File file = sceneFiles.get(i);

            int rowY =
                    y + i * rowHeight;

            boolean selected =
                    i == selectedScene;

            boolean hovered =
                    mouseX >= x
                            && mouseX < x + dropdownWidth
                            && mouseY >= rowY
                            && mouseY < rowY + rowHeight;

            if (selected)
            {
                drawRect(
                        x,
                        rowY,
                        x + 2,
                        rowY + rowHeight,
                        getAccentColor()
                );

                drawRect(
                        x + 2,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        hovered
                                ? COLOR_PANEL_HOVER
                                : COLOR_SELECTED
                );
            }
            else if (hovered)
            {
                drawRect(
                        x,
                        rowY,
                        x + dropdownWidth,
                        rowY + rowHeight,
                        COLOR_PANEL_HOVER
                );
            }

            String name = file.getName();

            if (name.length() > 24)
            {
                name =
                        name.substring(0, 21)
                                + "...";
            }

            drawString(
                    fontRenderer,
                    name,
                    x + 9,
                    rowY + 6,
                    selected
                            ? getAccentBrightColor()
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
        drawRect(
                x - 3,
                y - 2,
                x + width + 3,
                y + height + 3,
                0xCC080909
        );

        drawRect(
                x,
                y,
                x + width,
                y + height,
                COLOR_PANEL
        );

        drawRect(
                x,
                y,
                x + width,
                y + 1,
                getAccentColor()
        );

        drawRect(
                x,
                y + height - 1,
                x + width,
                y + height,
                COLOR_BORDER
        );

        drawRect(
                x,
                y,
                x + 1,
                y + height,
                COLOR_BORDER
        );

        drawRect(
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

    private void drawBodyPartsBonePanel()
    {
        int top = TOP_BAR_HEIGHT + ACTOR_PANEL_HEIGHT;
        int bottom = height - getTimelineHeight();

        drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                COLOR_PANEL
        );

        drawRect(
                LEFT_PANEL_WIDTH - 1,
                top,
                LEFT_PANEL_WIDTH,
                bottom,
                COLOR_BORDER
        );

        drawPanelHeader(
                "BODY PART BONES",
                0,
                top,
                LEFT_PANEL_WIDTH
        );

        List<AnimationBone> bones =
                this.actorPreviewController.getBones();

        if (bones == null || bones.isEmpty())
        {
            drawString(
                    fontRenderer,
                    "No bones",
                    12,
                    top + 38,
                    COLOR_TEXT_MUTED
            );
            return;
        }

        int y = top + 31;

        for (int i = 0; i < bones.size(); i++)
        {
            AnimationBone bone = bones.get(i);

            if (bone == null)
            {
                continue;
            }

            if (y + 20 > bottom)
            {
                break;
            }

            boolean selected =
                    i == this.bodyPartsController
                            .getSelectedActorBone();

            if (selected)
            {
                drawRect(
                        5,
                        y - 2,
                        LEFT_PANEL_WIDTH - 5,
                        y + 17,
                        COLOR_SELECTED
                );

                drawRect(
                        5,
                        y - 2,
                        7,
                        y + 17,
                        getAccentColor()
                );
            }

            drawString(
                    fontRenderer,
                    bone.getName(),
                    selected ? 13 : 10,
                    y + 3,
                    selected
                            ? COLOR_TEXT
                            : COLOR_TEXT_SECONDARY
            );

            y += 20;
        }
    }

    private void drawActorPanel()
    {
        int top = TOP_BAR_HEIGHT;

        this.actorListPanel.draw(
                this.mc,
                0,
                top,
                LEFT_PANEL_WIDTH,
                ACTOR_PANEL_HEIGHT,
                getSceneActors(),
                this.sceneState == null
                        ? -1
                        : this.sceneState.getSelectedActor(),
                COLOR_PANEL,
                COLOR_PANEL_DARK,
                COLOR_SELECTED,
                COLOR_BORDER,
                COLOR_TEXT,
                COLOR_TEXT_SECONDARY,
                COLOR_TEXT_MUTED,
                getAccentColor()
        );
    }

    private void drawPanelHeader(
            String text,
            int x,
            int y,
            int width)
    {
        drawRect(
                x,
                y,
                x + width,
                y + 25,
                COLOR_PANEL_DARK
        );

        drawRect(
                x,
                y + 24,
                x + width,
                y + 25,
                COLOR_BORDER
        );

        drawRect(
                x + 9,
                y + 7,
                x + 11,
                y + 18,
                getAccentColor()
        );

        drawString(
                fontRenderer,
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

        this.interpolationPanel.setBounds(
                0,
                top,
                LEFT_PANEL_WIDTH,
                INTERPOLATION_PANEL_HEIGHT
        );

        drawRect(
                0,
                top,
                LEFT_PANEL_WIDTH,
                top + INTERPOLATION_PANEL_HEIGHT,
                COLOR_PANEL
        );

        drawRect(
                LEFT_PANEL_WIDTH - 1,
                top,
                LEFT_PANEL_WIDTH,
                top + INTERPOLATION_PANEL_HEIGHT,
                COLOR_BORDER
        );

        AnimationKeyframe selectedKeyframe =
                this.keyframeController
                        .getSelectedKeyframe();

        if (selectedKeyframe != null
                && this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
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
        int left = LEFT_PANEL_WIDTH;
        int top = TOP_BAR_HEIGHT;
        int rightPanelWidth = 185;
        int previewRight =
                width - rightPanelWidth;

        int bottom =
                height - getTimelineHeight();

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

        drawRect(
                left,
                top,
                previewRight,
                bottom,
                0xFF0E1011
        );

        drawRect(
                left,
                top,
                previewRight,
                top + 1,
                COLOR_BORDER
        );

        drawRect(
                previewRight - 1,
                top,
                previewRight,
                bottom,
                COLOR_BORDER
        );

        this.sceneViewport.setBounds(
                left,
                top,
                previewWidth,
                previewHeight
        );

        if (this.playbackController.isPlaying())
        {
            applyAdapters(
                    this.playbackController
                            .getCurrentAnimationFrame()
            );
        }

        this.sceneViewport.draw(
                mc,
                this.recordController
                        .getCurrentActorPose(),
                this.actorPreviewController
                        .getBones(),
                this.playbackController
                        .getCurrentFrame()
        );

        this.sceneViewport.drawActor(
                mc,
                getSelectedActor(),
                getCurrentRecordFrame()
        );

        syncCharacterEditor();

        /*
         * Stage 5:
         * The actual gizmo belongs to the 3D Preview pass. It must be
         * drawn while the Preview framebuffer is still active, before
         * finishPreviewRender() copies the finished scene back to the GUI.
         *
         * The tool buttons are drawn later in GUI space by draw().
         */
        prepareGizmoTarget();

        this.gizmoController.draw3D(
                mc,
                left,
                top,
                previewWidth,
                previewHeight,
                getGizmoBone(),
                getGizmoKeyframe(),
                getCurrentRecordFrame(),
                this.sceneViewport.getCamera(),
                isGizmoEnabled()
        );

        this.sceneViewport.finishPreviewRender();
        this.sceneViewport.renderPreviewToScreen();

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
         * ---------------------------------------------------------
         * STAGE 5 - GIZMO
         * ---------------------------------------------------------
         *
         * Drawn on top of the rendered Preview, but before the
         * inspector on the right. The three tool buttons therefore
         * belong visually to the Preview itself.
         */
        prepareGizmoTarget();

        this.gizmoController.draw(
                mc,
                left,
                top,
                previewWidth,
                previewHeight,
                getGizmoBone(),
                getGizmoKeyframe(),
                getCurrentRecordFrame(),
                this.sceneViewport.getCamera(),
                isGizmoEnabled()
        );

        int panelX =
                width - rightPanelWidth;

        drawRect(
                panelX,
                top,
                width,
                bottom,
                COLOR_PANEL
        );

        drawRect(
                panelX,
                top,
                panelX + 1,
                bottom,
                COLOR_BORDER
        );

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            this.characterEditorPanel.setBounds(
                    panelX,
                    top,
                    rightPanelWidth,
                    bottom - top
            );

            this.characterEditorPanel.draw(
                    mc,
                    mouseX,
                    mouseY
            );
        }
        else if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            this.actorPreviewController
                    .getTransformPanel()
                    .setPosition(
                            panelX,
                            top + 10
                    );

            this.actorPreviewController
                    .getTransformPanel()
                    .draw(
                            mc,
                            this.keyframeController
                                    .getSelectedKeyframe(),
                            getCurrentTransform(),
                            this.playbackController
                                    .getCurrentFrame()
                    );
        }
        else if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            this.bodyPartsEditorPanel.setBounds(
                    panelX,
                    top,
                    bottom - top
            );

            this.bodyPartsEditorPanel.draw(
                    mc,
                    mouseX,
                    mouseY,
                    getSceneLength()
            );
        }
    }

    private void drawPreviewFrame(
            int left,
            int top,
            int right,
            int bottom)
    {
        drawRect(
                left,
                top,
                right,
                top + 1,
                COLOR_BORDER
        );

        drawRect(
                left,
                bottom - 1,
                right,
                bottom,
                COLOR_BORDER
        );

        drawRect(
                left + 7,
                top + 7,
                left + 32,
                top + 8,
                getAccentColor()
        );

        drawString(
                fontRenderer,
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
                        + PREVIEW_BUTTON_GAP * 2;

        int centerX =
                left +
                        (right - left) / 2;

        int startX =
                centerX -
                        totalWidth / 2;

        int buttonY =
                bottom -
                        PREVIEW_BUTTON_HEIGHT -
                        12;

        drawPreviewButton(
                startX,
                buttonY,
                "|<",
                false
        );

        int playX =
                startX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        drawPreviewButton(
                playX,
                buttonY,
                this.playbackController.isPlaying()
                        ? "||"
                        : ">",
                false
        );

        int nextX =
                playX +
                        PREVIEW_BUTTON_WIDTH +
                        PREVIEW_BUTTON_GAP;

        drawPreviewButton(
                nextX,
                buttonY,
                ">|",
                false
        );
    }

    private void drawPreviewButton(
            int x,
            int y,
            String text,
            boolean hovered)
    {
        drawRect(
                x,
                y,
                x + PREVIEW_BUTTON_WIDTH,
                y + PREVIEW_BUTTON_HEIGHT,
                hovered
                        ? COLOR_SELECTED
                        : COLOR_PANEL_DARK
        );

        drawRect(
                x,
                y,
                x + PREVIEW_BUTTON_WIDTH,
                y + 1,
                hovered
                        ? getAccentColor()
                        : COLOR_BORDER
        );

        drawRect(
                x,
                y + PREVIEW_BUTTON_HEIGHT - 1,
                x + PREVIEW_BUTTON_WIDTH,
                y + PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER_DARK
        );

        drawRect(
                x,
                y,
                x + 1,
                y + PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER
        );

        drawRect(
                x + PREVIEW_BUTTON_WIDTH - 1,
                y,
                x + PREVIEW_BUTTON_WIDTH,
                y + PREVIEW_BUTTON_HEIGHT,
                COLOR_BORDER
        );

        int textWidth =
                fontRenderer.getStringWidth(text);

        drawString(
                fontRenderer,
                text,
                x +
                        (PREVIEW_BUTTON_WIDTH - textWidth) / 2,
                y + 6,
                hovered
                        ? getAccentBrightColor()
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
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            this.bodyPartsController.getTimeline().setTick(
                    this.playbackController.getCurrentFrame()
            );

            this.bodyPartsTimelineController.draw(
                    this.mc,
                    width,
                    height,
                    getSceneLength()
            );
            return;
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            syncCharacterTimelineActor();

            int timelineHeight =
                    this.characterTimelineEditorController
                            .getTimelineHeight();

            int top =
                    height - timelineHeight;

            this.characterTimelineEditorController.draw(
                    this,
                    width,
                    height,
                    LEFT_PANEL_WIDTH,
                    top,
                    this.playbackController
                            .getCurrentFrame(),
                    getSceneLength()
            );

            return;
        }

        int timelineHeight =
                getTimelineHeight();

        int top =
                height - timelineHeight;

        drawRect(
                0,
                top,
                width,
                height,
                COLOR_PANEL_DARK
        );

        drawRect(
                0,
                top,
                width,
                top + 1,
                getAccentColor()
        );

        this.timelineController.draw(
                width,
                height,
                LEFT_PANEL_WIDTH,
                getSceneLength(),
                getSelectedActor()
        );
    }

    public int getTimelineHeight()
    {
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            return this.bodyPartsTimelineController
                    .getTimelineHeight();
        }

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            return this.characterTimelineEditorController
                    .getTimelineHeight();
        }

        return this.timelineController
                .getTimelineHeight();
    }

    /*
     * =========================================================
     * INPUT DELEGATION
     * =========================================================
     */

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws java.io.IOException
    {
        if (this.editorInput != null)
        {
            this.editorInput.mouseClicked(
                    mouseX,
                    mouseY,
                    mouseButton
            );
        }
    }

    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        if (this.editorInput != null)
        {
            this.editorInput.mouseClickMove(
                    mouseX,
                    mouseY,
                    clickedMouseButton,
                    timeSinceLastClick
            );
        }
    }

    @Override
    protected void mouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        if (this.editorInput != null)
        {
            this.editorInput.mouseReleased(
                    mouseX,
                    mouseY,
                    state
            );
        }
    }

    @Override
    public void handleMouseInput()
            throws java.io.IOException
    {
        if (this.editorInput != null)
        {
            this.editorInput.handleMouseInput();
        }
        else
        {
            super.handleMouseInput();
        }
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws java.io.IOException
    {
        if (this.editorInput != null)
        {
            this.editorInput.keyTyped(
                    typedChar,
                    keyCode
            );
        }
    }

    /*
     * =========================================================
     * SUPER ACCESS FOR INPUT
     * =========================================================
     */

    public void callSuperMouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws java.io.IOException
    {
        super.mouseClicked(
                mouseX,
                mouseY,
                mouseButton
        );
    }

    public void callSuperMouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick)
    {
        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }

    public void callSuperMouseReleased(
            int mouseX,
            int mouseY,
            int state)
    {
        super.mouseReleased(
                mouseX,
                mouseY,
                state
        );
    }

    public void callSuperHandleMouseInput()
            throws java.io.IOException
    {
        super.handleMouseInput();
    }

    /*
     * =========================================================
     * KEYFRAME / TRANSFORM ACCESS
     * =========================================================
     */

    public AnimationKeyframe getSelectedKeyframe()
    {
        return this.keyframeController
                .getSelectedKeyframe();
    }

    public AnimationBone getSelectedBone()
    {
        return this.actorPreviewController
                .getSelectedBone(
                        this.keyframeController
                );
    }

    public AnimationTransform getCurrentTransform()
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
     * DELETE DIALOG
     * =========================================================
     */

    private boolean isDeleteDialogOpen()
    {
        return this.keyframeController
                .isDeletePending();
    }

    /*
     * =========================================================
     * CLOSE
     * =========================================================
     */

    public void closeEditor()
    {
        /*
         * Перед закрытием не сохраняем автоматически.
         */

        BlockbusterPreviewAnimationState.clear();
        EmoticonsPreviewAnimationState.clear();

        this.playbackController.pause();

        this.mc.displayGuiScreen(null);
    }

    /*
     * =========================================================
     * MOUSE HELPERS
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

    /*
     * =========================================================
     * VIEWPORT
     * =========================================================
     */

    private void updateSceneViewportPosition()
    {
        if (this.sceneState == null
                || this.sceneViewport == null)
        {
            return;
        }

        this.sceneViewport.setScenePosition(
                this.sceneState.getSceneX(),
                this.sceneState.getSceneY(),
                this.sceneState.getSceneZ()
        );
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
            this.sceneViewport.updateCameraMovement(mc);
        }

        super.updateScreen();

        this.timeline.update();
        this.playbackController.update();

        BlockbusterPreviewAnimationState.setPlaying(
                this.playbackController.isPlaying()
        );

        applyRecordFrame();

        if (!this.playbackController.isPlaying())
        {
            applyAdapters();
        }

        this.characterEditorPanel.updateCursorCounter();

        applyCharacterState();

        syncCharacterEditor();
    }

    /*
     * =========================================================
     * GUI CLOSED
     * =========================================================
     */

    @Override
    public void onGuiClosed()
    {
        if (this.editorInput != null)
        {
            this.editorInput.resetInputState();
        }

        this.editorModeController.closeDropdown();
        this.editorThemeController.closeDropdown();

        this.characterTimelineEditorController
                .mouseReleased();

        this.timelineController.mouseReleased();

        this.playbackController.pause();

        BlockbusterPreviewAnimationState.clear();
        EmoticonsPreviewAnimationState.clear();

        super.onGuiClosed();
    }
}
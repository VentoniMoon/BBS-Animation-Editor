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

    private final EditorMultiActorPreviewController multiActorPreview =
            new EditorMultiActorPreviewController();

    /** Owns one camera/world/actor/overlay pass and its GL cleanup. */
    private final PreviewSceneRenderCoordinator preview3 =
            new PreviewSceneRenderCoordinator();

    private final EditorPreviewAutoFocusController previewAutoFocus =
            new EditorPreviewAutoFocusController();

    private final EditorModeController editorModeController =
            new EditorModeController();

    /** Dedicated scene-wide CAMERA workspace (never tied to actor selection). */
    private final CameraWorkspace cameraWorkspace = new CameraWorkspace();

    /** Popup is accessible from one icon beside the playback controls. */
    private final CameraOutputPopup cameraOutputPopup=new CameraOutputPopup();

    /** Explicit, experimental CAMERA video renderer; normal preview is untouched. */
    private final CameraVideoRenderController cameraVideoExport=
            new CameraVideoRenderController();

    /** Modal CAMERA export settings/progress, separate from viewport logic. */
    private final CameraExportDialog cameraExportDialog=
            new CameraExportDialog();

    public CameraVideoRenderController getCameraVideoExport()
    {
        return cameraVideoExport;
    }

    public boolean isCameraMode()
    {
        return editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA;
    }
    /** Match the native stage's selected-vs-background actor visibility. */
    public boolean showAllActorsInPreview()
    {
        return multiActorPreview.isShowAllActors();
    }

    public boolean isCameraPlaying()
    {
        return playbackController.isPlaying();
    }

    /** Render-thread ownership of CAMERA's 20 TPS timeline during MP4 export. */
    public void pauseForCameraVideoExport()
    {
        cameraWorkspace.stopPreviewRecording();
        playbackController.pause();
        cameraWorkspace.pauseAudio();
        wavAudio.update(playbackController.getCurrentFrame(),false);
    }

    /**
     * Called before native world rendering, not from GuiScreen's world
     * framebuffer compositing callback. Camera and actors use the same tick.
     */
    public void setCameraWorkspaceExportFrame(int sceneTick)
    {
        playbackController.setCurrentFrame(sceneTick);
        applyRecordFrame();
        applyAdapters();
        applyCharacterState();
    }

    public void setCameraWorkspaceExportFrame(CameraExportPlan.Sample sample)
    {
        if(sample==null)throw new IllegalArgumentException("Missing film sample");
        playbackController.setCurrentFrame(sample.sceneTick);
        applyRecordFrame();
        applyAdapters((float)sample.sceneTime());
        applyCharacterState(); // Discrete Character actions stay at 20 TPS.
    }

    public void restoreAfterCameraVideoExport(int frame,boolean wasPlaying)
    {
        playbackController.pause();
        playbackController.setCurrentFrame(frame);
        applyRecordFrame();
        applyAdapters();
        applyCharacterState();
        if(wasPlaying)playbackController.togglePlayback();
    }

    public CameraOutputSettings getCameraOutputSettings()
    {
        return this.sceneState.getCameraOutput();
    }

    private void saveCameraOutputSettings()
    {
        if(sceneState==null)return;
        cameraWorkspace.syncScene(sceneState);
        cameraWorkspace.saveScene();
    }

    /** Keep the Export dialog's target aspect persistent in the CAMERA scene. */
    public void applyCameraExportOutputSize(int width,int height)
    {
        if(sceneState!=null&&sceneState.getCameraOutput().set(width,height))
            saveCameraOutputSettings();
    }

    public PreviewCameraPose getCameraWorkspacePose()
    {
        return this.sceneViewport.getCamera().sample();
    }

    /** Start authoring a camera key from exactly its saved world-space pose. */
    public void setCameraWorkspaceEditorPose(CameraClipDocument.Key key)
    {
        if(key==null||this.sceneViewport==null)return;
        PreviewCameraPose free=this.sceneViewport.getCamera().sample();
        this.sceneViewport.getCamera().apply(new PreviewCameraPose(
                key.x,key.y,key.z,key.yaw,key.pitch,key.roll,key.fov,
                free.nearPlane,free.farPlane));
        this.sceneViewport.setCameraMode(EditorCamera.Mode.FREE);
    }

    /** Shared camera for gizmo draw and picking while viewing film from a clip. */
    public EditorCamera getPreviewInteractionCamera()
    {
        if(this.sceneViewport==null)return null;
        return this.cameraWorkspace.crossModeShot()
                &&this.editorModeController.getMode()
                    !=EditorModeController.EditorMode.CAMERA
                ?this.sceneViewport.getCameraForPreviewInteractions()
                :this.sceneViewport.getCamera();
    }
    public boolean isCrossModeCameraView()
    {
        return this.cameraWorkspace.crossModeShot()
                &&this.editorModeController.getMode()
                    !=EditorModeController.EditorMode.CAMERA;
    }


    /**
     * Called at RenderTick START, before the native Minecraft world draw.
     * SHOT camera only takes ownership of the rendered pose, not the
     * editor's FREE/ORBIT position. Switching back restores free navigation.
     */
    public void setCameraWorkspaceFrame(int frame)
    {
        this.playbackController.setCurrentFrame(Math.max(0,Math.min(
                frame,Math.max(0,getSceneLength()-1))));
    }

    public PreviewCameraPose getCameraWorkspaceNativePose()
    {
        PreviewCameraPose free=this.sceneViewport.getCamera().sample();
        boolean inCamera=this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA;
        if(!inCamera&&!this.cameraWorkspace.crossModeShot())return free;
        this.cameraWorkspace.syncScene(this.sceneState);
        CameraClipDocument.TargetResolver resolver=
                new CameraClipDocument.TargetResolver()
                {
                    @Override
                    public CameraClipDocument.Target resolve(String id,int frame)
                    {
                        if(id==null||id.isEmpty())return null;
                        for(BlockbusterSceneActorData actor:sceneState.getActors())
                        {
                            if(actor==null||!id.equals(actor.getId())
                                    ||!actor.hasRecord())continue;
                            BlockbusterRecord record=actor.getRecord();
                            BlockbusterRecordFrame state=record.getFrame(frame);
                            if(state==null)return null;
                            return new CameraClipDocument.Target(
                                state.getX(),state.getY(),state.getZ(),
                                state.getYaw(),state.getPitch());
                        }
                        return null;
                    }
                };
        if(cameraVideoExport.isExporting())
        {
            CameraExportPlan.Sample sample=cameraVideoExport.currentSample();
            CameraExportPlan plan=cameraVideoExport.currentPlan();
            if(sample!=null&&plan!=null)
                return CameraExportCameraSampler.sample(
                        cameraWorkspace.document,plan,sample,free,resolver);
            return cameraWorkspace.sampleForExport(free,
                    playbackController.getCurrentFrame(),resolver);
        }
        int previewTick=playbackController.getCurrentFrame();
        if((inCamera?cameraWorkspace.shot():cameraWorkspace.crossModeShot())
                &&playbackController.isPlaying()&&
                CameraVisualFrameClock.hasSample(previewTick))
            return CameraExportCameraSampler.sampleAt(
                    cameraWorkspace.document,getSceneLength(),previewTick,
                    CameraVisualFrameClock.fraction(previewTick),free,resolver);
        return cameraWorkspace.sample(free,previewTick,resolver,inCamera);
    }

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

    private final EditorWavAudioController wavAudio =
            new EditorWavAudioController();

    private final EditorWavWaveform wavWaveform =
            new EditorWavWaveform();
    private boolean keepAudioOnNextGuiClose;

    public void preserveAudioOnGuiTransition()
    {
        this.keepAudioOnNextGuiClose = true;
    }


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

    /** Only selected BetterLightsMorph actors use this POSE inspector. */
    private final BetterLightsActorInspector betterLightsInspector =
            new BetterLightsActorInspector();

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
        this.wavAudio.restoreAudible();
        this.wavAudio.restoreLastLoaded();

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
            this.sceneState.setBodyPartsController(this.bodyPartsController);

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
            EditorSessionSelection.restore(this, this.sceneState);

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
            /*
             * In the model-editing view, target the selected bone of the
             * Body Part model itself. The parent actor bone is only the
             * attachment point for the whole model and must not replace
             * an internal bone just because no keyframe is selected yet.
             */
            EditorKeyframeController bodyPartKeys =
                    this.bodyPartsController.getKeyframeController();

            /*
             * Mirror Pose's primary resolution rule: when a keyframe is
             * selected, trust its owning bone if that bone belongs to this
             * child model. Some model adapters (notably Emoticons) can leave
             * the selected-bone index stale while the keyframe owner remains
             * correct.
             */
            AnimationKeyframe selectedKey =
                    bodyPartKeys.getSelectedKeyframe();
            AnimationBone keyOwner =
                    bodyPartKeys.getSelectedKeyframeBone();

            if (selectedKey != null && keyOwner != null &&
                    model.getBones() != null)
            {
                for (AnimationBone childBone : model.getBones())
                {
                    if (childBone == keyOwner)
                    {
                        return keyOwner;
                    }
                }
            }

            AnimationBone selectedBone =
                    bodyPartKeys.getSelectedBone(model.getBones());

            if (selectedBone != null)
            {
                return selectedBone;
            }

            /*
             * A selected child model with internal bones must never silently
             * fall back to the actor attachment bone. That attachment is the
             * pivot of the whole added model, not one of its internal bones.
             *
             * Some model adapters can temporarily lose their selected-bone
             * index while rebuilding the child skeleton. Keep the gizmo on
             * a real child bone in that case (prefer a root, then first valid
             * bone) instead of making it appear stuck to the actor.
             */
            List<AnimationBone> childBones = model.getBones();

            if (childBones != null && !childBones.isEmpty())
            {
                for (AnimationBone childBone : childBones)
                {
                    if (childBone != null && childBone.getParent() == null)
                    {
                        return childBone;
                    }
                }

                for (AnimationBone childBone : childBones)
                {
                    if (childBone != null)
                    {
                        return childBone;
                    }
                }
            }

            /*
             * Only a model without an internal skeleton may use its actor
             * attachment as a fallback target.
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
                != EditorModeController.EditorMode.BODY_PARTS)
        {
            return null;
        }

        BodyPartModelData model =
                this.bodyPartsController.getSelectedModel();

        if (model != null)
        {
            /*
             * Selecting a Body Part model clears the bone/keyframe
             * selection. In that state the gizmo edits the transform of
             * the whole attached model, not the actor attachment bone.
             *
             * Once an internal child bone is selected, the gizmo must
             * return to that bone's keyframe and leave the model transform
             * untouched.
             */
            AnimationBone selectedChildBone =
                    this.bodyPartsController.getKeyframeController()
                            .getSelectedBone(model.getBones());

            return selectedChildBone == null
                    ? model.getGlobalTransform()
                    : null;
        }

        model = this.bodyPartsController.getSelectedAttachment();

        return model == null
                ? null
                : model.getGlobalTransform();
    }

    public void prepareGizmoTarget()
    {
        this.gizmoController.setGizmoFrame(
                this.playbackController.getCurrentFrame()
        );
        // Light rig tracks store WORLD blocks rather than model pixels.
        this.gizmoController.setLightRigTarget(
                editorModeController.getMode()==EditorModeController.EditorMode.POSE
                && BetterLightsActorInspector.isLightActor(getSelectedActor())
                    ?BetterLightsRig.forActor(getSelectedActor()):null);

        boolean chameleonGizmoTarget =
                this.actorPreviewController.isCurrentActorChameleon();

        BodyPartModelData gizmoBodyPart =
                this.bodyPartsController.getSelectedModel();

        if (gizmoBodyPart == null)
        {
            gizmoBodyPart =
                    this.bodyPartsController.getSelectedAttachment();
        }

        this.gizmoController.setBodyPartRuntimeMorph(
                this.sceneViewport.getPreparedBodyPartMorph(gizmoBodyPart)
        );

        if (gizmoBodyPart != null)
        {
            /*
             * Resolve the active child morph at the current timeline frame,
             * not just the model's original morph. Character Timeline can
             * replace an attached model, and the gizmo adapter must follow
             * that same active model type.
             */
            mchorse.metamorph.api.morphs.AbstractMorph activeMorph =
                    gizmoBodyPart.getMorphAt(
                            this.playbackController.getCurrentFrame()
                    );

            if (activeMorph != null)
            {
                chameleonGizmoTarget =
                        activeMorph.getClass()
                                .getName()
                                .endsWith(".ChameleonMorph");
            }
        }

        this.gizmoController.setChameleonCoordinateSpace(
                chameleonGizmoTarget
        );

        AnimationBone gizmoAttachmentBone =
                getGizmoBodyPartAttachmentBone();

        /*
         * Do not substitute the selected internal child bone (or an actor
         * bone returned by a fallback selection) for a missing attachment.
         * For Body Parts, the attachment bone is a separate coordinate
         * anchor; the selected gizmo bone belongs to the child model.
         * This applies to Blockbuster, Chameleon and Emoticons child models.
         */
        if (gizmoAttachmentBone == null &&
                getGizmoBodyPartModel() == null)
        {
            gizmoAttachmentBone = getGizmoBone();
        }

        this.gizmoController.setGizmoAttachmentBone(
                gizmoAttachmentBone
        );

        this.gizmoController.setBodyPartTarget(
                getGizmoBodyPartModel(),
                getGizmoBodyPartAttachmentBone()
        );

        this.gizmoController.setGlobalTransformTarget(
                getGizmoGlobalTransform()
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

        EditorKeyframeController bodyPartKeys =
                this.bodyPartsController.getKeyframeController();

        AnimationKeyframe selected =
                bodyPartKeys.getSelectedKeyframe();

        if (selected != null)
        {
            return selected;
        }

        /*
         * Timeline selection can survive while the transient selected-key
         * reference is cleared by another UI action. Resolve the key from
         * the selected Body Part bone and current frame, just as the Pose
         * editor does. This keeps the gizmo connected to the actual track.
         */
        BodyPartModelData selectedModel =
                this.bodyPartsController.getSelectedModel();

        if (selectedModel != null)
        {
            AnimationBone selectedBone =
                    bodyPartKeys.getSelectedBone(selectedModel.getBones());

            if (selectedBone != null)
            {
                return bodyPartKeys.findKeyframe(
                        selectedBone,
                        this.playbackController.getCurrentFrame()
                );
            }
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
                this.characterTimelineEditorController.getSelectedBodyPartModel()
        );
        this.characterEditorPanel.setWavAudio(this.wavAudio);
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

        /*
         * Apply Character Bones overrides to native Blockbuster morphs.
         * The controller supports CustomMorph and ignores other model types.
         */
        if (actor.morph != null && actor.morph.get() != null)
        {
            CharacterBodyPartOverrideController.apply(
                    actor.morph.get(),
                    data.getCharacterTimeline(),
                    this.playbackController.getCurrentFrame()
            );
        }

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

    public CameraWorkspace getCameraWorkspace()
    {
        return this.cameraWorkspace;
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
        cameraOutputPopup.close();
        this.multiActorPreview.reset();
        this.previewAutoFocus.reset();
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

    /** CAMERA gets first chance to consume wheel events over its panels. */
    public boolean scrollCameraWorkspace(int mouseX,int mouseY,int direction)
    {
        if(editorModeController.getMode()!=EditorModeController.EditorMode.CAMERA)
            return false;
        if(cameraOutputPopup.isOpen())return cameraOutputPopup.scroll(direction);
        return cameraWorkspace.mouseScrolled(mouseX,mouseY,direction);
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
        /*
         * BBS FS native capture MUST happen before drawing any GUI pixel.
         * Otherwise the world texture would contain our dark editor panels.
         */
        BetterLightsRig.bind(this.sceneState);
        BbsFsWorldFrameCapture.beginFrame(this.mc);
        // During CAMERA output a Minecraft world pass uses the *film* FBO
        // dimensions (e.g. 3840x2160, or 1080x1920). Vanilla may configure
        // its GUI orthographic matrix while those temporary dimensions are
        // still active. Merely restoring displayWidth/FBO is insufficient:
        // it creates a second, scaled editor GUI with a different layout.
        boolean restoredNativeFilm=CameraNativeFrameProbe.beforeGui(this.mc);
        // BetterLights' temporary player shader origin must end after the
        // completed native world frame, before any UI/record editing. The
        // RenderTick.END callback is a failsafe if this GUI pass is skipped.
        CameraBetterLightsCameraOrigin.end();
        if(restoredNativeFilm && this.mc!=null &&
                this.mc.entityRenderer!=null)
        {
            if(this.mc.getFramebuffer()!=null)
                this.mc.getFramebuffer().bindFramebuffer(true);
            this.mc.entityRenderer.setupOverlayRendering();
            CameraNativeFrameProbe.resyncDefaultTextureForGui();
        }

        if(cameraVideoExport.isExporting())
        {
            // The native world, actors, media and encoder STILL advance via
            // RenderTick START / beforeGui above. Never paint the live GUI
            // a second time over an output-sized target; it can inherit
            // residual viewport/scissor state and creates resolution-
            // dependent duplicate toolbars, timelines and inspector panels.
            org.lwjgl.opengl.GL11.glDisable(
                    org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
            net.minecraft.client.renderer.GlStateManager.colorMask(
                    true,true,true,true);
            net.minecraft.client.renderer.GlStateManager.color(1F,1F,1F,1F);
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(
                    org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,1,0);
            drawRect(0,0,width,height,0xFF080E15);
            cameraExportDialog.draw(this,mouseX,mouseY);
            return;
        }

        drawRect(
                0,
                0,
                width,
                height,
                EditorVisualStyle.BACKGROUND
        );

        drawTopBar();
        if(this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA)
        {
            String exportStatus=cameraVideoExport.status();
            if(exportStatus!=null&&!exportStatus.isEmpty())
            {
                int bannerWidth=Math.min(width-16,Math.max(120,
                        fontRenderer.getStringWidth(exportStatus)+15));
                drawRect(8,32,8+bannerWidth,49,0xEB192732);
                fontRenderer.drawString(
                        fontRenderer.trimStringToWidth(exportStatus,
                                bannerWidth-12),
                        14,37,0xFF8CD9FB);
            }
        }
        if (this.editorModeController.getMode()
                != EditorModeController.EditorMode.CAMERA)
            drawActorPanel();

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            drawBodyPartsBonePanel();
        }

        drawPreview(mouseX, mouseY);
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA)
        {
            this.cameraWorkspace.syncScene(this.sceneState);
            this.cameraWorkspace.draw(this,
                    this.playbackController.getCurrentFrame(),getSceneLength());
            this.cameraWorkspace.syncAudio(
                    this.playbackController.getCurrentFrame(),
                    this.playbackController.isPlaying());
        }
        else
        {
            // Film audio follows the same frame when CAM view is enabled
            // outside CAMERA; otherwise normal actor editing remains silent.
            if(this.cameraWorkspace.crossModeShot())
            {
                this.cameraWorkspace.syncScene(this.sceneState);
                this.cameraWorkspace.syncAudio(
                        this.playbackController.getCurrentFrame(),
                        this.playbackController.isPlaying());
            }
            else this.cameraWorkspace.pauseAudio();
            drawTimeline();
            drawWavWaveform();
            drawInterpolationPanel(mouseX, mouseY);
        }

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
        if(this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA)
            cameraExportDialog.draw(this,mouseX,mouseY);
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

        if (this.editorModeController.getMode()
                != EditorModeController.EditorMode.CAMERA)
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
         * CAMERA has no actor selector in the top bar. Use the now-free
         * actor slot for a labelled Export Video control when space permits;
         * the small EXPORT icon in the preview remains usable on narrow UI.
         */
        if(isCameraMode()&&editorThemeController.getButtonX(width)-actorX>=120)
        {
            drawToolbarButton(actorX,3,111,TOP_BAR_HEIGHT-6,
                    cameraExportDialog.isOpen());
            drawString(fontRenderer,"EXPORT VIDEO",actorX+10,8,
                    cameraExportDialog.isOpen()?getAccentBrightColor():
                            COLOR_TEXT);
        }

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
         * RECORD EXPORT: CLAPPERBOARD
         * =========================================================
         */
        int exportX = modeX + EditorModeController.MODE_BUTTON_WIDTH + 6;
        int exportY = 3;
        boolean exportHovered = isRecordExportButtonHovered(
                org.lwjgl.input.Mouse.getX() * width / mc.displayWidth,
                height - org.lwjgl.input.Mouse.getY() * height / mc.displayHeight - 1);
        drawToolbarButton(exportX, exportY, 25, TOP_BAR_HEIGHT - 6, exportHovered);

        int ink = exportHovered ? getAccentBrightColor() : COLOR_TEXT;
        /* Clapperboard body, top hinge and diagonal striped clapper. */
        drawRect(exportX + 6, exportY + 9, exportX + 20, exportY + 16, ink);
        drawRect(exportX + 8, exportY + 11, exportX + 18, exportY + 15, COLOR_PANEL_DARK);
        drawRect(exportX + 6, exportY + 5, exportX + 20, exportY + 8, ink);
        drawRect(exportX + 9, exportY + 5, exportX + 11, exportY + 8, COLOR_PANEL_DARK);
        drawRect(exportX + 15, exportY + 5, exportX + 17, exportY + 8, COLOR_PANEL_DARK);

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

    public boolean isRecordExportButtonHovered(int mouseX, int mouseY)
    {
        int x = this.editorModeController.getButtonX(width)
                + EditorModeController.MODE_BUTTON_WIDTH + 6;
        return mouseX >= x && mouseX < x + 25
                && mouseY >= 3 && mouseY < TOP_BAR_HEIGHT - 3;
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
                        EditorModeController.EditorMode.BODY_PARTS,
                        EditorModeController.EditorMode.CAMERA
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

    /**
     * Optional BBS FS-style actor stage, invoked only from Minecraft's
     * RenderWorldLastEvent. The world camera/projection are already active.
     *
     * No second renderWorld(), no second snapshot and no editor FBO begin:
     * this only evaluates the same actor/Body Parts/physics code that the
     * GUI coordinator would otherwise draw in its later compositing pass.
     */
    public boolean renderActorsInNativeWorld()
    {
        if (this.sceneViewport == null || this.sceneState == null ||
                mc == null || mc.world == null ||
                this.sceneViewport.getWidth() <= 0 ||
                this.sceneViewport.getHeight() <= 0)
            return false;

        CameraExportPlan.Sample film=cameraVideoExport.currentSample();
        final int previewTick=playbackController.getCurrentFrame();
        if(CameraVisualFrameClock.hasSample(previewTick))
            applyAdapters(CameraVisualFrameClock.animationFrame(previewTick));
        else if(this.playbackController.isPlaying())
            applyAdapters(this.playbackController.getCurrentAnimationFrame());

        final int frame = this.playbackController.getCurrentFrame();
        final BlockbusterSceneActorData selected = getSelectedActor();
        this.sceneViewport.beginNativeActorFrame();

        /* Background renderer saves/restores the selected actor animation
         * state. This ordering is the same as PreviewSceneRenderCoordinator. */
        this.multiActorPreview.drawBackgroundActors(
                mc, this.sceneViewport, this.sceneState,
                selected,frame,this.bodyPartsController,
                CameraVisualFrameClock.sceneTime(frame));

        BlockbusterRecordFrame selectedFrame=
                CameraVisualFrameClock.hasSample(frame)
                    ?CameraExportActorFrameSampler.sample(
                            getSelectedActorRecord(),frame,
                            CameraVisualFrameClock.fraction(frame))
                    :getCurrentRecordFrame();
        this.sceneViewport.drawActor(
                mc, selected, selectedFrame);
        // Light casters (POSE and CAMERA) were prepared at RenderTick.START
        // before OptiFine rendered the WORLD. Do not create light entities
        // or update light uniforms from RenderWorldLast a second time.
        return true;
    }

    private void drawPreview(
            int mouseX,
            int mouseY)
    {
        // CAMERA needs only short Layer names and preset labels.
        // Do not alter Character/Pose/Body Parts sidebars.
        int left = this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA
                ? CameraWorkspace.CAMERA_LEFT_WIDTH : LEFT_PANEL_WIDTH;
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

        // Output frame belongs to the scene, independent of GUI pixels.
        if(this.editorModeController.getMode()
                !=EditorModeController.EditorMode.CAMERA
                &&cameraOutputPopup.isOpen())cameraOutputPopup.close();
        boolean outputView=this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA
                ||this.cameraWorkspace.crossModeShot();
        int[] film=outputView&&sceneState!=null
                ?sceneState.getCameraOutput().fit(
                        left,top,previewWidth,previewHeight)
                :new int[]{left,top,previewWidth,previewHeight};
        final int filmX=film[0],filmY=film[1];
        final int filmW=film[2],filmH=film[3];
        this.sceneViewport.setBounds(filmX,filmY,filmW,filmH);

        if (this.playbackController.isPlaying() &&
                !BbsFsNativeActorPass.didRenderThisFrame())
        {
            applyAdapters(CameraVisualFrameClock.hasSample(
                    this.playbackController.getCurrentFrame())
                ?CameraVisualFrameClock.animationFrame(
                        this.playbackController.getCurrentFrame())
                :this.playbackController.getCurrentAnimationFrame());
        }

        updatePreviewAutoFocus();

        BlockbusterRecordFrame displayedRecord=CameraVisualFrameClock.hasSample(
                playbackController.getCurrentFrame())
                ?CameraExportActorFrameSampler.sample(
                        getSelectedActorRecord(),playbackController.getCurrentFrame(),
                        CameraVisualFrameClock.fraction(playbackController.getCurrentFrame()))
                :getCurrentRecordFrame();
        this.preview3.draw(
                mc,
                this.sceneViewport,
                this.multiActorPreview,
                this.sceneState,
                getSelectedActor(),
                displayedRecord,
                this.bodyPartsController,
                this.recordController.getCurrentActorPose(),
                this.actorPreviewController.getBones(),
                this.playbackController.getCurrentFrame(),
                () -> {
                    syncCharacterEditor();
                    prepareGizmoTarget();
                    this.gizmoController.setPreviewActor(findRuntimeActor());
                },
                () -> this.gizmoController.draw3D(
                        mc, filmX, filmY, filmW, filmH,
                        getGizmoBone(), getGizmoKeyframe(),
                        getCurrentRecordFrame(),
                        getPreviewInteractionCamera(), isGizmoEnabled())
        );
        if(this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA
                ||this.cameraWorkspace.crossModeShot())
        {
            this.cameraWorkspace.syncScene(this.sceneState);
            CameraFilmCompositor.composite(mc,
                    this.sceneViewport.getRenderBackend()
                            .getNativeTarget().getFramebuffer(),
                    filmX,filmY,filmW,filmH,
                    this.cameraWorkspace.document.clips(),
                    this.playbackController.getCurrentFrame());
        }
        this.sceneViewport.renderPreviewToScreen();
        if(editorModeController.getMode()==EditorModeController.EditorMode.CAMERA)
        {
            cameraWorkspace.drawMediaSelection(
                    playbackController.getCurrentFrame(),
                    filmX,filmY,filmW,filmH);
            cameraWorkspace.drawLightGizmo(this,
                    filmX,filmY,filmW,filmH);
        }
        // Actor lights share the same world-space cone guide as CAMERA
        // statics. The guide is drawn after film compositing, so it cannot
        // leak into screenshots or the exported video.
        if(editorModeController.getMode()==EditorModeController.EditorMode.POSE
                &&BetterLightsActorInspector.isLightActor(getSelectedActor())
                &&displayedRecord!=null)
        {
            BlockbusterSceneActorData lightActor=getSelectedActor();
            ActorAnimationData rig=BetterLightsRig.forActor(lightActor);
            double tick=CameraVisualFrameClock.sceneTime(
                    playbackController.getCurrentFrame());
            BetterLightsWorldState light=BetterLightsWorldState.actor(
                    displayedRecord.getX(),displayedRecord.getY(),
                    displayedRecord.getZ(),
                    BetterLightsRig.offset(rig,tick),
                    BetterLightsRig.targetOffset(rig,tick));
            net.minecraft.nbt.NBTTagCompound lightSettings=
                    BetterLightsRig.settingsAt(rig,tick,
                            lightActor.getActor().getMorph());
            if(light!=null)
                BetterLightsWireOverlay.draw(getCameraWorkspaceNativePose(),
                        lightSettings,light.x,light.y,light.z,
                        light.targetX,light.targetY,light.targetZ,
                        filmX,filmY,filmW,filmH);
        }

        /*
         * The GUI pass only draws the three gizmo mode buttons.
         * Transform handles are rendered exclusively by draw3D(), before
         * the Preview framebuffer is copied to the GUI.
         */
        this.gizmoController.draw(
                mc,
                filmX,
                filmY,
                filmW,
                filmH,
                null,
                null,
                null,
                getPreviewInteractionCamera(),
                isGizmoEnabled()
        );

        drawPreviewFrame(left,top,previewRight,bottom);
        if(outputView)
        {
            int accent=getAccentColor();
            drawRect(filmX,filmY,filmX+filmW,filmY+1,accent);
            drawRect(filmX,filmY+filmH-1,filmX+filmW,filmY+filmH,accent);
            drawRect(filmX,filmY,filmX+1,filmY+filmH,accent);
            drawRect(filmX+filmW-1,filmY,filmX+filmW,filmY+filmH,accent);
        }

        drawPreviewControls(
                left, previewRight, top, bottom, mouseX, mouseY
        );
        if(editorModeController.getMode()==EditorModeController.EditorMode.CAMERA)
            cameraOutputPopup.draw(mc,sceneState.getCameraOutput(),
                    left,top,previewRight,
                    previewExtraY(left,previewRight,bottom),mouseX,mouseY);

        /*
         * ---------------------------------------------------------
         * STAGE 5 - GIZMO
         * ---------------------------------------------------------
         *
         * The original BBS architecture renders the gizmo as real
         * 3D geometry while the Preview framebuffer is still bound.
         * Keep the gizmo inside that same camera/render pass.
         */

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
            boolean lightActor=BetterLightsActorInspector.isLightActor(
                    getSelectedActor());
            if(lightActor)
            {
                AnimationBone bone=actorPreviewController.getSelectedBone(
                        keyframeController);
                betterLightsInspector.setOriginKey(
                        bone!=null&&BetterLightsRig.ORIGIN.equals(
                                bone.getName())
                        ?keyframeController.getSelectedKeyframe():null);
            }
            else betterLightsInspector.setOriginKey(null);
            if(lightActor&&!betterLightsInspector.rigTab())
            {
                this.betterLightsInspector.draw(mc,panelX,top,bottom,
                        getSelectedActor(),
                        this.playbackController.getCurrentFrame());
            }
            else
            {
            this.actorPreviewController
                    .getTransformPanel()
                    .setLightRigMode(lightActor);
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
                if(lightActor&&keyframeController.getSelectedKeyframe()==null)
                    fontRenderer.drawString("Select a key on Origin / Target",
                            panelX+9,top+42,0xFF98BFCE);
            }
            if(lightActor)
                betterLightsInspector.drawTabs(mc,panelX,bottom);
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

    /**
     * Playback keeps its familiar three controls. Mini picture buttons live
     * alongside it, never over the preview image.
     * The same geometry is used for drawing, tooltips and hit detection.
     */
    private static final int PREVIEW_ICON_SIZE=18;
    private static final int PREVIEW_ICON_GAP=3;

    private int previewExtraCount()
    {
        return 4; // CAMERA now has SHOT / REC / FORMAT / EXPORT.
    }

    private int previewPlaybackX(int left,int right)
    {
        int total=PREVIEW_BUTTON_WIDTH*3+PREVIEW_BUTTON_GAP*2;
        return left+(right-left-total)/2;
    }

    private int previewExtraX(int left,int right)
    {
        int extra=previewExtraCount()*PREVIEW_ICON_SIZE+
                (previewExtraCount()-1)*PREVIEW_ICON_GAP;
        int playbackTotal=PREVIEW_BUTTON_WIDTH*3+PREVIEW_BUTTON_GAP*2;
        int start=previewPlaybackX(left,right);
        if(right-left>=playbackTotal+extra+17)
        {
            int after=start+playbackTotal+8;
            if(after+extra<=right-5)return after;
            return start-extra-8;
        }
        // Narrow window: a second short toolbar row above playback.
        return left+(right-left-extra)/2;
    }

    private int previewExtraY(int left,int right,int bottom)
    {
        int extra=previewExtraCount()*PREVIEW_ICON_SIZE+
                (previewExtraCount()-1)*PREVIEW_ICON_GAP;
        int playbackTotal=PREVIEW_BUTTON_WIDTH*3+PREVIEW_BUTTON_GAP*2;
        int playY=bottom-PREVIEW_BUTTON_HEIGHT-12;
        return right-left>=playbackTotal+extra+17
                ?playY+1:playY-PREVIEW_ICON_SIZE-4;
    }

    private void drawCompactPreviewTools(int left,int right,int bottom,
            int mouseX,int mouseY)
    {
        int start=previewExtraX(left,right);
        int y=previewExtraY(left,right,bottom);
        boolean cameraMode=this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA;
        EditorCamera.Mode navigation=this.sceneViewport==null
                ?EditorCamera.Mode.FREE
                :this.sceneViewport.getCamera().getMode();
        boolean[] checked=cameraMode
                ?new boolean[]{cameraWorkspace.shot(),
                        cameraWorkspace.recordingPreview(),
                        cameraOutputPopup.isOpen(),
                        cameraExportDialog.isOpen()}
                :new boolean[]{multiActorPreview.isShowAllActors(),
                        previewAutoFocus.isEnabled(),
                        navigation==EditorCamera.Mode.ORBIT,
                        cameraWorkspace.crossModeShot()};
        int[] icons=cameraMode
                ?new int[]{PreviewToolbarIcons.SHOT,
                        PreviewToolbarIcons.RECORD,
                        PreviewToolbarIcons.OUTPUT_FORMAT,
                        PreviewToolbarIcons.EXPORT_VIDEO}
                :new int[]{PreviewToolbarIcons.ALL_ACTORS,
                        PreviewToolbarIcons.AUTOFOCUS,
                        PreviewToolbarIcons.NAVIGATION,
                        PreviewToolbarIcons.FILM_CAMERA};
        String[] hints=cameraMode
                ?new String[]{
                        cameraWorkspace.shot()?"Shot camera (switch to free view)"
                                :"Free camera (switch to shot view)",
                        cameraWorkspace.recordingPreview()
                                ?"Stop recording selected camera key"
                                :"Record camera movement to selected clip key",
                        "Output format ("+
                                sceneState.getCameraOutput().getWidth()+" x "+
                                sceneState.getCameraOutput().getHeight()+")",
                        "Export video (MP4 / FFmpeg)"}
                :new String[]{"Show all actors","Auto-focus selected key",
                        navigation==EditorCamera.Mode.ORBIT
                                ?"Orbit camera (switch to free)"
                                :"Free camera (switch to orbit)",
                        cameraWorkspace.crossModeShot()
                                ?"Disable film camera view"
                                :"View through CAMERA clips"};
        for(int i=0;i<icons.length;i++)
        {
            int x=start+i*(PREVIEW_ICON_SIZE+PREVIEW_ICON_GAP);
            boolean hovered=mouseX>=x&&mouseX<x+PREVIEW_ICON_SIZE
                    &&mouseY>=y&&mouseY<y+PREVIEW_ICON_SIZE;
            drawToolbarButton(x,y,PREVIEW_ICON_SIZE,PREVIEW_ICON_SIZE,
                    hovered||checked[i]);
            if(checked[i])drawRect(x,y+PREVIEW_ICON_SIZE-2,
                    x+PREVIEW_ICON_SIZE,y+PREVIEW_ICON_SIZE,
                    icons[i]==PreviewToolbarIcons.RECORD?0xFFFF6868:getAccentColor());
            int ink=icons[i]==PreviewToolbarIcons.RECORD&&checked[i]
                    ?0xFFFF9999:checked[i]||hovered
                    ?getAccentBrightColor():COLOR_TEXT_SECONDARY;
            PreviewToolbarIcons.draw(icons[i],x,y,ink,checked[i]);
            if(hovered)
            {
                // Tooltip above buttons stays within the cinematic frame.
                int tw=fontRenderer.getStringWidth(hints[i])+12;
                int tipX=Math.max(left+2,Math.min(right-tw-2,x+9-tw/2));
                int tipY=y-19;
                drawRect(tipX,tipY,tipX+tw,tipY+16,0xF010171D);
                drawRect(tipX,tipY,tipX+tw,tipY+1,getAccentColor());
                drawString(fontRenderer,hints[i],tipX+6,tipY+4,COLOR_TEXT);
            }
        }
    }

    public boolean clickStage8PreviewButton(int mouseX,int mouseY,int button)
    {
        if(this.sceneViewport==null||button!=0)return false;
        // Toolbar occupies the outer editor canvas even in portrait mode.
        int left=this.editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA
                ?CameraWorkspace.CAMERA_LEFT_WIDTH:LEFT_PANEL_WIDTH;
        int right=this.width-185;
        int bottom=this.height-getTimelineHeight();
        int x=previewExtraX(left,right);
        int y=previewExtraY(left,right,bottom);
        int count=previewExtraCount();
        if(mouseY<y||mouseY>=y+PREVIEW_ICON_SIZE)return false;
        int offset=mouseX-x;
        if(offset<0)return false;
        int step=PREVIEW_ICON_SIZE+PREVIEW_ICON_GAP;
        int which=offset/step;
        if(which>=count||offset%step>=PREVIEW_ICON_SIZE)return false;
        if(this.editorModeController.getMode()==EditorModeController.EditorMode.CAMERA)
        {
            if(which==0)cameraWorkspace.toggleShotView();
            else if(which==1)
                cameraWorkspace.togglePreviewRecordingFromToolbar(
                        this,this.playbackController.getCurrentFrame());
            else if(which==2)cameraOutputPopup.toggle();
            else
            {
                cameraOutputPopup.close();
                cameraExportDialog.open(this);
            }
            return true;
        }
        if(which==0)multiActorPreview.toggleShowAllActors();
        else if(which==1)previewAutoFocus.toggle();
        else if(which==2)
        {
            EditorCamera.Mode current=this.sceneViewport.getCamera().getMode();
            this.sceneViewport.setCameraMode(current==EditorCamera.Mode.ORBIT
                    ?EditorCamera.Mode.FREE:EditorCamera.Mode.ORBIT);
        }
        else
        {
            cameraWorkspace.syncScene(sceneState);
            cameraWorkspace.toggleCrossModeShot();
        }
        return true;
    }

    private void updatePreviewAutoFocus()
    {
        if(this.cameraWorkspace.crossModeShot()
                &&this.editorModeController.getMode()
                    !=EditorModeController.EditorMode.CAMERA)return;
        if (this.sceneState == null || this.sceneViewport == null)
        {
            return;
        }

        Object key = null;
        int frame = this.playbackController.getCurrentFrame();

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.POSE)
        {
            AnimationKeyframe selected = this.keyframeController.getSelectedKeyframe();
            if (selected != null)
            {
                key = selected;
                frame = selected.getFrame();
            }
        }
        else if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.BODY_PARTS)
        {
            AnimationKeyframe selected = this.bodyPartsController
                    .getKeyframeController().getSelectedKeyframe();
            if (selected != null)
            {
                key = selected;
                frame = selected.getFrame();
            }
        }
        else if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CHARACTER)
        {
            CharacterKey selected =
                    this.characterTimelineEditorController.getSelectedKey();
            if (selected != null)
            {
                key = selected;
                frame = selected.getFrame();
            }
        }

        this.previewAutoFocus.update(this.sceneViewport, getSelectedActor(),
                key, frame, this.playbackController.isPlaying());
    }

    private void drawPreviewControls(
            int left,
            int right,
            int top,
            int bottom,
            int mouseX,
            int mouseY)
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
        drawCompactPreviewTools(left,right,bottom,mouseX,mouseY);
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

    private void drawWavWaveform()
    {
        if (!this.wavAudio.isLoaded()) return;
        int timelineTop = height - getBaseTimelineHeight();
        this.wavWaveform.draw(width, timelineTop + 1,
                LEFT_PANEL_WIDTH, this.timeline,
                this.playbackController.getCurrentFrame());
        // Always accessible above the waveform in CHARACTER, POSE and BODY
        // PARTS. Do not replace or mute CAMERA clips or the film export.
        int right=width-7;
        int left=Math.max(LEFT_PANEL_WIDTH+4,right-83);
        int top=timelineTop+2;
        drawRect(left-2,top-1,right+1,top+16,0xEB122630);
        drawRect(left,top,right,top+14,
                this.wavAudio.isAudible()?0xFF315567:0xFF564245);
        fontRenderer.drawString(this.wavAudio.isAudible()
                        ?"WAV SOUND ON":"WAV MUTED",
                left+5,top+3,0xFFE6EFF5);
    }

    /** Only intercept the tiny WAV monitor button above the sound waveform. */
    private boolean clickWavMonitor(int mouseX,int mouseY,int button)
    {
        if(button!=0||!wavAudio.isLoaded()||isCameraMode())return false;
        int timelineTop=height-getBaseTimelineHeight();
        int right=width-7;
        int left=Math.max(LEFT_PANEL_WIDTH+4,right-83);
        if(mouseX<left-2||mouseX>right+1||
                mouseY<timelineTop+1||mouseY>timelineTop+18)
            return false;
        wavAudio.toggleAudible();
        return true;
    }

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
                getBaseTimelineHeight();

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
        return getBaseTimelineHeight();
    }

    private int getBaseTimelineHeight()
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

        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA)
        {
            // CAMERA shares Character's compact, fixed-height film timeline.
            return this.characterTimelineEditorController.getTimelineHeight();
        }

        return this.timelineController
                .getTimelineHeight();
    }

    /*
     * =========================================================
     * INPUT DELEGATION
     * =========================================================
     */

    /**
     * Playback buttons sit OVER the lower part of the film. Clicking Play
     * must never start a drag on an image/subtitle behind that button.
     */
    private boolean isPlaybackButtonHit(int mouseX,int mouseY)
    {
        int left=CameraWorkspace.CAMERA_LEFT_WIDTH;
        int right=width-185;
        int total=PREVIEW_BUTTON_WIDTH*3+PREVIEW_BUTTON_GAP*2;
        int x=left+(right-left-total)/2;
        int y=height-getTimelineHeight()-PREVIEW_BUTTON_HEIGHT-12;
        return mouseY>=y&&mouseY<y+PREVIEW_BUTTON_HEIGHT&&
                mouseX>=x&&mouseX<x+total;
    }

    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton)
            throws java.io.IOException
    {
        // Modal Export must intercept clicks before the timeline, preview or
        // normal editor toolbars; cancellation stays available in this panel.
        if(cameraExportDialog.isOpen())
        {
            cameraExportDialog.mouseClicked(this,mouseX,mouseY,mouseButton);
            return;
        }
        if(isCameraMode()&&mouseButton==0)
        {
            int cameraExportX=175+180+15;
            if(editorThemeController.getButtonX(width)-cameraExportX>=120&&
                    mouseX>=cameraExportX&&mouseX<cameraExportX+111&&
                    mouseY>=3&&mouseY<TOP_BAR_HEIGHT-3)
            {
                cameraOutputPopup.close();
                cameraExportDialog.open(this);
                return;
            }
        }
        if(cameraVideoExport.isExporting())return;
        if(clickWavMonitor(mouseX,mouseY,mouseButton))return;
        // Output popup consumes clicks before the camera timeline and gizmos.
        if(cameraOutputPopup.isOpen()
                &&cameraOutputPopup.mouseClicked(mouseX,mouseY,mouseButton,
                    sceneState.getCameraOutput(),
                    () -> saveCameraOutputSettings()))return;
        // CAMERA's toolbar uses the same hitboxes as all other modes.
        if(this.editorModeController.getMode()==EditorModeController.EditorMode.CAMERA
                &&clickStage8PreviewButton(mouseX,mouseY,mouseButton))return;
        // The CAMERA light gizmo gets first chance at its three handles.
        // It must not accidentally scrub the scene or drag a VIDEO layer.
        if(editorModeController.getMode()==EditorModeController.EditorMode.CAMERA
                &&sceneViewport!=null&&
                cameraWorkspace.beginLightGizmo(this,mouseX,mouseY,
                        mouseButton,sceneViewport.getX(),
                        sceneViewport.getY(),sceneViewport.getWidth(),
                        sceneViewport.getHeight()))return;
        // Topmost visible Image/Video/Subtitle can be dragged inside the
        // current output-format frame, even when portrait (9:16).
        if(editorModeController.getMode()==EditorModeController.EditorMode.CAMERA
                &&sceneViewport!=null&&!isPlaybackButtonHit(mouseX,mouseY)
                &&cameraWorkspace.beginMediaDrag(mouseX,mouseY,mouseButton,
                        playbackController.getCurrentFrame(),
                        sceneViewport.getX(),sceneViewport.getY(),
                        sceneViewport.getWidth(),sceneViewport.getHeight()))
            return;
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA
                && this.cameraWorkspace.click(this,mouseX,mouseY,mouseButton,
                    this.playbackController.getCurrentFrame(),getSceneLength()))
            return;
        if(this.editorModeController.getMode()
                ==EditorModeController.EditorMode.POSE &&
                BetterLightsActorInspector.isLightActor(getSelectedActor()))
        {
            if(mouseButton==0&&betterLightsInspector.selectTab(
                    mouseX,mouseY,width-185,
                    height-getTimelineHeight()))return;
            if(!betterLightsInspector.rigTab()&&
                    betterLightsInspector.mouseClicked(this,
                        mouseX,mouseY,mouseButton))return;
        }
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
        if(cameraExportDialog.isOpen()||cameraVideoExport.isExporting())return;
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA
                && clickedMouseButton == 0)
        {
            if(cameraWorkspace.dragLightGizmo(this,mouseX,mouseY))return;
            this.cameraWorkspace.move(mouseX,mouseY,getSceneLength());
            return;
        }
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
        if(cameraExportDialog.isOpen()||cameraVideoExport.isExporting())return;
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA)
        {
            this.cameraWorkspace.releaseLightGizmo(this);
            this.cameraWorkspace.release();
        }
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
        if(cameraExportDialog.isOpen()||cameraVideoExport.isExporting())
        {
            // Keep the authored scene immutable while native frames are
            // being encoded; especially ignore timeline/zoom wheel input.
            super.handleMouseInput();
            return;
        }
        if(this.editorModeController.getMode()
                ==EditorModeController.EditorMode.POSE &&
                BetterLightsActorInspector.isLightActor(getSelectedActor()) &&
                betterLightsInspector.scroll(
                    org.lwjgl.input.Mouse.getEventX()*width/
                        Math.max(1,mc.displayWidth),
                    height-org.lwjgl.input.Mouse.getEventY()*height/
                        Math.max(1,mc.displayHeight)-1,
                    org.lwjgl.input.Mouse.getEventDWheel()))
            return;
        if (this.editorInput != null)
        {
            this.editorInput.handleMouseInput();
        }
        else
        {
            super.handleMouseInput();
        }
    }

    /** Access only the selected actor's preview physics session. */
    public BodyPartPhysicsBakeController getPhysicsBakeController()
    {
        BlockbusterActorPreviewRenderer renderer=
                findBlockbusterActorPreviewRenderer(
                        this.sceneViewport,new HashSet<Object>(),0);
        if(renderer==null)
            renderer=findBlockbusterActorPreviewRenderer(
                    this.actorPreviewController,new HashSet<Object>(),0);
        return renderer==null?null:renderer.getPhysicsBakeController();
    }

    @Override
    protected void keyTyped(
            char typedChar,
            int keyCode)
            throws java.io.IOException
    {
        // Keep the legacy shortcut, but open an actual settings/progress
        // window instead of silently starting a hidden 20 FPS job.
        if(editorModeController.getMode()
                ==EditorModeController.EditorMode.CAMERA
                &&keyCode==org.lwjgl.input.Keyboard.KEY_F8
                &&isCtrlKeyDown()&&isShiftKeyDown())
        {
            if(!cameraExportDialog.isOpen())
                cameraExportDialog.open(this);
            return;
        }
        if(cameraExportDialog.isOpen())
        {
            cameraExportDialog.keyTyped(this,typedChar,keyCode);
            return;
        }
        if(cameraVideoExport.isExporting())
        {
            if(keyCode==org.lwjgl.input.Keyboard.KEY_ESCAPE)
                cameraVideoExport.cancel("MP4 export canceled");
            return;
        }
        // Hidden opt-in diagnostic only; never intercept normal F9/editor
        // input unless the developer explicitly enabled this world probe.
        if(CameraNativeFrameProbe.isEnabled()
                &&editorModeController.getMode()
                    ==EditorModeController.EditorMode.CAMERA
                &&keyCode==org.lwjgl.input.Keyboard.KEY_F9
                &&isCtrlKeyDown()&&isShiftKeyDown())
        {
            CameraOutputSettings out=sceneState.getCameraOutput();
            CameraNativeFrameProbe.request(out.getWidth(),out.getHeight());
            return;
        }
        if(cameraOutputPopup.keyTyped(typedChar,keyCode,
                    sceneState.getCameraOutput(),
                    () -> saveCameraOutputSettings()))return;
        if (this.editorModeController.getMode()
                == EditorModeController.EditorMode.CAMERA
                && this.cameraWorkspace.keyTyped(typedChar,keyCode))
            return;
        if(this.editorModeController.getMode()
                ==EditorModeController.EditorMode.POSE &&
                BetterLightsActorInspector.isLightActor(getSelectedActor())&&
                !betterLightsInspector.rigTab()&&
                betterLightsInspector.keyTyped(this,typedChar,keyCode))
            return;
        if (this.editorInput != null)
            this.editorInput.keyTyped(typedChar,keyCode);
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
            if(!this.cameraWorkspace.crossModeShot()
                    ||this.editorModeController.getMode()
                        ==EditorModeController.EditorMode.CAMERA)
                this.sceneViewport.updateCameraMovement(mc);
            if(this.editorModeController.getMode()
                    == EditorModeController.EditorMode.CAMERA)
                this.cameraWorkspace.captureNavigationPose(
                        this.sceneViewport.getCamera().sample());
            else
                this.cameraWorkspace.stopPreviewRecording();
        }

        super.updateScreen();

        /*
         * Normal timeline playback follows Minecraft's 20 TPS. During a
         * Jolt key bake, however, the native pose is captured by Preview
         * rendering (which may run slower than 20 FPS). Never skip an
         * uncaptured physics tick. Wait until the selected Body Part has
         * supplied that tick's completed Jolt pose, then advance exactly
         * one tick. This also prevents a bake silently wrapping to frame 0.
         *
         * Non-bake playback is completely unchanged.
         */
        boolean advanceTimeline=true;
        if(this.timeline.isPlaying())
        {
            BodyPartPhysicsBakeController bake=getPhysicsBakeController();
            if(bake!=null && bake.isRecording())
            {
                int frame=this.timeline.getTick();
                if(!bake.hasCapturedFrame(frame))
                {
                    advanceTimeline=false;
                    if(bake.hasTimedOutWaitingFor(frame))
                        this.timeline.pause();
                }
                else if(frame>=Math.min(this.timeline.getLastTick(),
                        bake.getLastRecordableFrame()))
                {
                    this.timeline.pause();
                    advanceTimeline=false;
                }
            }
        }
        if(advanceTimeline)
            this.timeline.update();
        this.playbackController.update();
        this.wavWaveform.sync(this.wavAudio.getLoadedFile());
        this.wavAudio.update(
                this.playbackController.getCurrentFrame(),
                this.playbackController.isPlaying() &&
                        !this.cameraVideoExport.isExporting()
        );

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
        // Preview-only lights are not Minecraft entities: release them
        // explicitly even if no further native world pass is rendered.
        BetterLightsWorldEngine.reset();
        cameraVideoExport.close();
        cameraExportDialog.close();
        /* Never leave the detached preview camera as Minecraft's view. */
        if (this.sceneViewport != null)
        {
            BbsFsWorldCameraDriver.detach(this.sceneViewport);
            this.sceneViewport.disposePreviewResources();
        }
        CameraNativeFrameProbe.close(this.mc);
        BbsFsWorldFrameCapture.release();
        this.cameraWorkspace.dispose();

        EditorSessionSelection.remember(this.sceneState);

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
        if (!this.keepAudioOnNextGuiClose)
            this.wavAudio.unload();
        this.keepAudioOnNextGuiClose = false;

        BlockbusterPreviewAnimationState.clear();
        EmoticonsPreviewAnimationState.clear();

        super.onGuiClosed();
    }
}
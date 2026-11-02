package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;

/**
 * Stage 8 scene renderer. Each background actor receives an independent
 * EntityActor / morph renderer; the selected actor keeps the existing
 * EditorSceneViewport renderer, gizmo and BodyParts editing pipeline.
 *
 * Visibility is local to Preview and never mutates scene/Record data.
 */
public final class EditorMultiActorPreviewController
{
    private final Map<String, BlockbusterActorPreviewRenderer> renderers =
            new HashMap<String, BlockbusterActorPreviewRenderer>();
    /** Saved read-only render context from the NORMAL actor preparation. */
    private final Map<String,List<AnimationBoneSnapshot>> mirrorPoseContexts =
            new HashMap<String,List<AnimationBoneSnapshot>>();
    private int mirrorFrame;

    private boolean showAllActors = true;

    /** Do not reuse native EntityActor render state across scene loads. */
    private EditorSceneState previousScene;
    private int previousSceneIndex=-1;

    public boolean isShowAllActors()
    {
        return this.showAllActors;
    }

    public void toggleShowAllActors()
    {
        this.showAllActors = !this.showAllActors;
    }

    public void drawBackgroundActors(
            Minecraft mc,
            EditorSceneViewport viewport,
            EditorSceneState scene,
            BlockbusterSceneActorData selected,
            int frame,
            BodyPartsEditorController bodyPartsController)
    {
        drawBackgroundActors(mc,viewport,scene,selected,frame,
                bodyPartsController,frame);
    }

    public void drawBackgroundActors(
            Minecraft mc,
            EditorSceneViewport viewport,
            EditorSceneState scene,
            BlockbusterSceneActorData selected,
            int frame,
            BodyPartsEditorController bodyPartsController,double sceneTime)
    {
        if(scene!=previousScene ||
                (scene!=null && scene.getSelectedScene()!=previousSceneIndex))
        {
            reset();
            previousScene=scene;
            previousSceneIndex=scene==null?-1:scene.getSelectedScene();
        }

        mirrorPoseContexts.clear();
        mirrorFrame=frame;
        if (!this.showAllActors || scene == null || viewport == null)
        {
            return;
        }

        List<BlockbusterSceneActorData> actors = scene.getActors();
        if (actors == null) return;

        Set<String> active = new HashSet<String>();

        /* Preserve the selected actor's editor pose throughout the scene pass. */
        List<AnimationBoneSnapshot> selectedBlockbuster =
                BlockbusterPreviewAnimationState.getSnapshots();
        List<AnimationBoneSnapshot> selectedEmoticons =
                EmoticonsPreviewAnimationState.getSnapshots();
        int selectedBlockbusterFrame = BlockbusterPreviewAnimationState.getFrame();
        int selectedEmoticonsFrame = EmoticonsPreviewAnimationState.getFrame();

        for (BlockbusterSceneActorData data : actors)
        {
            if (data == null || data.getId() == null) continue;
            active.add(data.getId());
            if (data == selected)
            {
                // Selected actors belong to EditorSceneViewport's renderer.
                // A previously background-rendered light may otherwise keep
                // an independent LightCaster alive at its stale position.
                BlockbusterActorPreviewRenderer obsolete=
                        renderers.remove(data.getId());
                if(obsolete!=null)obsolete.reset();
                continue;
            }
            if(!data.hasRecord())continue;

            BlockbusterRecord record = data.getRecord();
            if (record == null || record.getFrames() == null
                    || record.getFrames().isEmpty()) continue;

            /* Do not draw a missing frame before an actor's recording begins. */
            BlockbusterRecordFrame current = record.getFrame(
                    Math.max(0, Math.min(frame, record.getFrames().size() - 1)));
            if (current == null) continue;
            if(CameraVisualFrameClock.hasSample(frame))
                current=CameraExportActorFrameSampler.sample(
                        record,frame,CameraVisualFrameClock.fraction(frame));

            BlockbusterActorPreviewRenderer renderer = renderers.get(data.getId());
            if (renderer == null)
            {
                renderer = new BlockbusterActorPreviewRenderer();
                renderers.put(data.getId(), renderer);
            }

            /*
             * Preview animation adapters use global per-render state.
             * Supply snapshots belonging to THIS actor for the duration of
             * its draw call; never leak them to another actor or the gizmo.
             */
            List<AnimationBoneSnapshot> own = new ArrayList<AnimationBoneSnapshot>();
            SceneAnimationData animation = scene.getAnimationData();
            ActorAnimationData actorAnimation =
                    animation == null ? null : animation.getActor(data.getId());
            if (actorAnimation != null)
            {
                for (AnimationBone bone : actorAnimation.getBones())
                {
                    if (bone == null) continue;
                    AnimationTransform value = bone.getTransformAt((float)sceneTime);
                    if (value == null) continue;
                    String parent = bone.getParent() == null ? null
                            : bone.getParent().getName();
                    own.add(new AnimationBoneSnapshot(bone.getName(), parent, value));
                }
            }

            /*
             * Each actor owns its Body Parts timeline. Do not switch
             * the selection in the editor's shared BodyParts controller.
             */
            renderer.setPassiveBodyPartsModels(bodyPartsController == null
                    ? null : bodyPartsController.getModelsForActor(data.getId()));
            // Morph/Emoticons draw layers can read the per-actor global
            // snapshot context even when the EntityActor is already posed.
            // Keep that context for the *read-only* mirror draw.
            mirrorPoseContexts.put(data.getId(),
                    new ArrayList<AnimationBoneSnapshot>(own));

            // A secondary actor's CustomMorph/Emoticons render can change
            // GL state AND Minecraft's cached flags/bound textures. The
            // original glPushAttrib/glPopAttrib restores the driver only;
            // without reconciling GlStateManager, the next actor's second
            // skin layer/mesh can inherit stale texture or alpha state.
            // Limit the expensive resync to BACKGROUND actor boundaries,
            // not every ordinary single-actor preview render.
            BbsFsGlStateResync.Snapshot beforeActor=
                    BbsFsGlStateResync.capture();
            try
            {
                BlockbusterPreviewAnimationState.set(own, frame);
                EmoticonsPreviewAnimationState.set(own, frame);
                viewport.drawAdditionalActor(mc, data, current, renderer);
            }
            finally
            {
                try
                {
                    BbsFsGlStateResync.restore(beforeActor);
                }
                finally
                {
                    BlockbusterPreviewAnimationState.set(
                            selectedBlockbuster, selectedBlockbusterFrame);
                    EmoticonsPreviewAnimationState.set(
                            selectedEmoticons, selectedEmoticonsFrame);
                }
            }
        }

        /* Discard stale actor state when a scene changes. */
        java.util.Iterator<Map.Entry<String, BlockbusterActorPreviewRenderer>> it =
                renderers.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String, BlockbusterActorPreviewRenderer> entry = it.next();
            if (!active.contains(entry.getKey()))
            {
                entry.getValue().reset();
                it.remove();
            }
        }
    }

    /** Read-only visual replay of background actors at the current tick. */
    public void renderPreparedMirrorActors(Minecraft mc,PreviewCameraPose view,
            int width,int height)
    {
        renderPreparedCameraActors(mc,view,width,height,true);
    }

    public void renderPreparedNativeActors(Minecraft mc,PreviewCameraPose view,
            int width,int height)
    {
        renderPreparedCameraActors(mc,view,width,height,false);
    }

    private void renderPreparedCameraActors(Minecraft mc,PreviewCameraPose view,
            int width,int height,boolean mirror)
    {
        if(!showAllActors||mc==null||view==null)return;
        List<AnimationBoneSnapshot> selectedBlockbuster =
                BlockbusterPreviewAnimationState.getSnapshots();
        List<AnimationBoneSnapshot> selectedEmoticons =
                EmoticonsPreviewAnimationState.getSnapshots();
        int selectedBlockbusterFrame=BlockbusterPreviewAnimationState.getFrame();
        int selectedEmoticonsFrame=EmoticonsPreviewAnimationState.getFrame();
        try
        {
            for(Map.Entry<String,BlockbusterActorPreviewRenderer> entry:
                    renderers.entrySet())
            {
                List<AnimationBoneSnapshot> own=
                        mirrorPoseContexts.get(entry.getKey());
                if(own==null||entry.getValue()==null)continue;
                BlockbusterPreviewAnimationState.set(own,mirrorFrame);
                EmoticonsPreviewAnimationState.set(own,mirrorFrame);
                if(mirror)
                    entry.getValue().renderPreparedMirror(mc,view,width,height);
                else
                    entry.getValue().renderPreparedNative(mc,view,width,height);
            }
        }
        finally
        {
            BlockbusterPreviewAnimationState.set(selectedBlockbuster,
                    selectedBlockbusterFrame);
            EmoticonsPreviewAnimationState.set(selectedEmoticons,
                    selectedEmoticonsFrame);
        }
    }

    /**
     * Replays only cached poses into the ACTIVE shadow target. In
     * particular, restore the per-actor Emoticons/Blockbuster snapshot
     * context before each render, matching the mirror's normal replay.
     */
    public int renderPreparedShadowActors(Minecraft mc)
    {
        if(!showAllActors||mc==null)return 0;
        List<AnimationBoneSnapshot> selectedBlockbuster=
                BlockbusterPreviewAnimationState.getSnapshots();
        List<AnimationBoneSnapshot> selectedEmoticons=
                EmoticonsPreviewAnimationState.getSnapshots();
        int selectedBlockbusterFrame=BlockbusterPreviewAnimationState.getFrame();
        int selectedEmoticonsFrame=EmoticonsPreviewAnimationState.getFrame();
        int rendered=0;
        try
        {
            for(Map.Entry<String,BlockbusterActorPreviewRenderer> entry:
                    renderers.entrySet())
            {
                List<AnimationBoneSnapshot> own=
                        mirrorPoseContexts.get(entry.getKey());
                if(own==null||entry.getValue()==null)continue;
                BlockbusterPreviewAnimationState.set(own,mirrorFrame);
                EmoticonsPreviewAnimationState.set(own,mirrorFrame);
                if(entry.getValue().renderPreparedShadow(mc))rendered++;
            }
        }
        finally
        {
            BlockbusterPreviewAnimationState.set(selectedBlockbuster,
                    selectedBlockbusterFrame);
            EmoticonsPreviewAnimationState.set(selectedEmoticons,
                    selectedEmoticonsFrame);
        }
        return rendered;
    }

    public void reset()
    {
        for (BlockbusterActorPreviewRenderer renderer : renderers.values())
        {
            renderer.reset();
        }
        renderers.clear();
        mirrorPoseContexts.clear();
        previousScene=null;
        previousSceneIndex=-1;
    }
}

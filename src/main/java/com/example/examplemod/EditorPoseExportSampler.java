package com.example.examplemod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 7: samples LOCAL bone transforms for a selected actor at each Record
 * tick, using the editor's existing interpolation logic.
 *
 * This is intentionally independent of the target morph type: writing these
 * values directly into player Frames would NOT animate model bones.
 */
public final class EditorPoseExportSampler
{
    private EditorPoseExportSampler() {}

    public static final class SampledFrame
    {
        private final int frame;
        private final Map<String, AnimationTransform> bones;

        private SampledFrame(int frame, Map<String, AnimationTransform> bones)
        {
            this.frame = frame;
            this.bones = Collections.unmodifiableMap(bones);
        }

        public int getFrame() { return this.frame; }

        public Map<String, AnimationTransform> getBones()
        {
            Map<String, AnimationTransform> copy =
                    new LinkedHashMap<String, AnimationTransform>();
            for (Map.Entry<String, AnimationTransform> entry : this.bones.entrySet())
            {
                copy.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }
    }

    public static List<SampledFrame> sampleSelected(EditorSceneState state)
            throws IOException
    {
        return sampleSelected(state, false);
    }

    /**
     * BodyParts-only exports need a neutral parent Pose sample per tick,
     * without inventing editor Pose keys.
     */
    public static List<SampledFrame> sampleSelected(
            EditorSceneState state, boolean allowEmpty)
            throws IOException
    {
        if (state == null || state.getSelectedActorData() == null)
        {
            throw new IOException("No actor selected for Pose export");
        }

        BlockbusterSceneActorData actor = state.getSelectedActorData();
        if (!actor.hasRecord())
        {
            throw new IOException("Selected actor has no Blockbuster Record");
        }

        SceneAnimationData all = state.getAnimationData();
        ActorAnimationData animation = all == null ? null : all.getActor(actor.getId());
        if (animation == null && !allowEmpty)
        {
            throw new IOException("Selected actor has no Pose animation");
        }

        int length = actor.getRecord().getLength();
        if (length <= 0)
        {
            throw new IOException("Record has no frames");
        }

        List<AnimationBone> animated = new ArrayList<AnimationBone>();
        for (AnimationBone bone : animation == null
                ? java.util.Collections.<AnimationBone>emptyList()
                : animation.getBones())
        {
            if (bone == null || bone.getKeyframes() == null
                    || bone.getKeyframes().isEmpty())
            {
                continue;
            }
            for (AnimationKeyframe key : bone.getKeyframes())
            {
                if (key != null && (key.getFrame() < 0 || key.getFrame() >= length))
                {
                    throw new IOException("Pose key outside Record: bone="
                            + bone.getName() + ", frame=" + key.getFrame()
                            + ", length=" + length);
                }
            }
            animated.add(bone);
        }
        if (animated.isEmpty() && !allowEmpty)
        {
            throw new IOException("Selected actor has no Pose keyframes");
        }

        List<SampledFrame> result = new ArrayList<SampledFrame>(length);
        for (int tick = 0; tick < length; tick++)
        {
            Map<String, AnimationTransform> local =
                    new LinkedHashMap<String, AnimationTransform>();
            for (AnimationBone bone : animated)
            {
                AnimationTransform transform = bone.getTransformAt(tick);
                if (transform != null)
                {
                    local.put(bone.getName(), transform.copy());
                }
            }
            result.add(new SampledFrame(tick, local));
        }
        return result;
    }
}

package com.example.examplemod;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Preview-only bridge between a timeline physics sample and any number of
 * render passes. Physics is stepped elsewhere, once per sequential frame.
 * A render pass may read a sample but must never advance the simulation.
 *
 * This class owns no editor keyframes and does not mutate a morph.
 */
public final class BodyPartPhysicsFrameState
{
    private static final class Sample
    {
        int frame;
        Map<String, float[]> swings = new HashMap<String, float[]>();
        java.util.Set<String> joltBones = new java.util.HashSet<String>();

        Sample(int frame)
        {
            this.frame = frame;
        }
    }

    /* Solver writes arrive after rendering. Visible poses are frozen at
     * the beginning of a frame, so repeated draws can never alternate
     * between pre-step and post-step rotations. */
    private final Map<BodyPartModelData, Sample> samples =
            new IdentityHashMap<BodyPartModelData, Sample>();
    private final Map<BodyPartModelData, Sample> visible =
            new IdentityHashMap<BodyPartModelData, Sample>();
    private int lastTimelineFrame = Integer.MIN_VALUE;

    public void beginFrame(int frame)
    {
        if (frame == lastTimelineFrame) return;

        boolean contiguous = lastTimelineFrame != Integer.MIN_VALUE &&
                frame == lastTimelineFrame + 1;
        visible.clear();
        if (contiguous)
        {
            for (Map.Entry<BodyPartModelData, Sample> entry : samples.entrySet())
            {
                Sample previous = entry.getValue();
                if (previous.frame == lastTimelineFrame)
                    visible.put(entry.getKey(), previous);
            }
        }
        else
        {
            samples.clear();
        }
        lastTimelineFrame = frame;
    }

    /** Legacy solver output (keeps its historical angle limit). */
    public void accept(BodyPartModelData part, int frame, String bone, float[] localSwing)
    {
        accept(part, frame, bone, localSwing, false);
    }

    /**
     * A Jolt swing is solved as a full quaternion and MUST NOT be clipped to
     * the legacy Verlet correction angle at render time.
     */
    public void accept(BodyPartModelData part, int frame, String bone,
            float[] localSwing, boolean fromJolt)
    {
        if (part == null || bone == null || localSwing == null || localSwing.length != 4)
            return;
        for (float v : localSwing)
            if (!Float.isFinite(v)) return;
        Sample existing = samples.get(part);
        if (existing != null && frame < existing.frame) return;
        if (existing == null || existing.frame != frame)
        {
            existing = new Sample(frame);
            samples.put(part, existing);
        }
        existing.swings.put(bone, localSwing.clone());
        if (fromJolt) existing.joltBones.add(bone);
        else existing.joltBones.remove(bone);
    }

    /**
     * Reads only the snapshot frozen when this timeline frame began.
     * New physics samples are never exposed halfway through a render frame.
     */
    public Map<String, float[]> forRender(BodyPartModelData part, int frame)
    {
        if (frame != lastTimelineFrame) return null;
        Sample sample = visible.get(part);
        if (sample == null) return null;
        Map<String, float[]> result = new HashMap<String, float[]>();
        for (Map.Entry<String, float[]> entry : sample.swings.entrySet())
            result.put(entry.getKey(), entry.getValue().clone());
        return result;
    }

    /** Matches the visible frozen sample, never the newly-computed frame. */
    public boolean isJoltForRender(BodyPartModelData part, int frame, String bone)
    {
        if (frame != lastTimelineFrame || bone == null) return false;
        Sample sample = visible.get(part);
        return sample != null && sample.joltBones.contains(bone);
    }

    /**
     * A paused editor may change its source bone pose without advancing time.
     * Publish the completed physics sample for the NEXT render pass of this
     * same frame. Call only after the solver has finished all chain bones.
     * This is never used during ordinary sequential playback.
     */
    public void publishEditedFrame(BodyPartModelData part, int frame)
    {
        if(part==null || frame!=lastTimelineFrame)return;
        Sample sample=samples.get(part);
        if(sample==null || sample.frame!=frame)return;
        Sample snapshot=new Sample(frame);
        for(Map.Entry<String,float[]> entry:sample.swings.entrySet())
            snapshot.swings.put(entry.getKey(),entry.getValue().clone());
        snapshot.joltBones.addAll(sample.joltBones);
        visible.put(part,snapshot);
    }

    public void clear()
    {
        samples.clear();
        visible.clear();
        lastTimelineFrame = Integer.MIN_VALUE;
    }
}

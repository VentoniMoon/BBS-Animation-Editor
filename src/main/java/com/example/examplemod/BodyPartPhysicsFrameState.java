package com.example.examplemod;

import java.util.IdentityHashMap;
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
        float[] swing;

        Sample(int frame, float[] swing)
        {
            this.frame = frame;
            this.swing = swing.clone();
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

    /** Called after the ordinary render and the solver's sequential step. */
    public void accept(BodyPartModelData part, int frame, float[] localSwing)
    {
        if (part == null || localSwing == null || localSwing.length != 4)
            return;
        for (float v : localSwing)
            if (!Float.isFinite(v)) return;
        Sample existing = samples.get(part);
        if (existing != null && frame < existing.frame) return;
        samples.put(part, new Sample(frame, localSwing));
    }

    /**
     * Reads only the snapshot frozen when this timeline frame began.
     * New physics samples are never exposed halfway through a render frame.
     */
    public float[] forRender(BodyPartModelData part, int frame)
    {
        if (frame != lastTimelineFrame) return null;
        Sample sample = visible.get(part);
        return sample == null ? null : sample.swing.clone();
    }

    public void clear()
    {
        samples.clear();
        visible.clear();
        lastTimelineFrame = Integer.MIN_VALUE;
    }
}

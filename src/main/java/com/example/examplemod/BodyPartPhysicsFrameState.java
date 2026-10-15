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

    private final Map<BodyPartModelData, Sample> samples =
            new IdentityHashMap<BodyPartModelData, Sample>();
    private int lastTimelineFrame = Integer.MIN_VALUE;

    /**
     * Called before preparing the morph. Discard physics from a different
     * timeline traversal, but keep the last result for repeated renders
     * of the same frame.
     */
    public void beginFrame(int frame)
    {
        if (lastTimelineFrame != Integer.MIN_VALUE &&
                (frame < lastTimelineFrame || frame > lastTimelineFrame + 1))
            samples.clear();
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
     * Re-use the same physics pose on repeated renders. A sample calculated
     * after frame N's render can first affect N+1. Skipped samples are not
     * held for multiple frames (which previously created periodic snaps).
     */
    public float[] forRender(BodyPartModelData part, int frame)
    {
        Sample sample = samples.get(part);
        if (sample == null || frame < sample.frame ||
                frame > sample.frame + 1)
            return null;
        return sample.swing.clone();
    }

    public void clear()
    {
        samples.clear();
        lastTimelineFrame = Integer.MIN_VALUE;
    }
}

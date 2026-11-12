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
        Map<String, SkirtAdaptiveWidth.Width> widths =
                new HashMap<String, SkirtAdaptiveWidth.Width>();
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
    /** N-2, used ONLY to predict the N frame skirt from frozen N-1. */
    private final Map<BodyPartModelData, Sample> older =
            new IdentityHashMap<BodyPartModelData, Sample>();
    private int lastTimelineFrame = Integer.MIN_VALUE;

    public void beginFrame(int frame)
    {
        if (frame == lastTimelineFrame) return;

        boolean contiguous = lastTimelineFrame != Integer.MIN_VALUE &&
                frame == lastTimelineFrame + 1;
        Map<BodyPartModelData,Sample> beforeVisible =
                new IdentityHashMap<BodyPartModelData,Sample>(visible);
        older.clear();
        visible.clear();
        if (contiguous)
        {
            for (Map.Entry<BodyPartModelData, Sample> entry : samples.entrySet())
            {
                Sample previous = entry.getValue();
                if (previous.frame == lastTimelineFrame)
                {
                    visible.put(entry.getKey(), previous);
                    Sample prior=beforeVisible.get(entry.getKey());
                    if(prior!=null && prior.frame==frame-2)
                        older.put(entry.getKey(),prior);
                }
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
     * Publish a bounded skirt panel WIDTH (not length). Smooth only across
     * truly consecutive frames; a seek/rewind starts with fresh pose.
     * The same snapshot clock as native Jolt swings keeps FBO passes stable.
     */
    public void acceptWidth(BodyPartModelData part,int frame,String bone,
            SkirtAdaptiveWidth.Width width)
    {
        if(part==null||bone==null||width==null||
                (width.axis!=0&&width.axis!=2)||
                !Float.isFinite(width.multiplier)||
                width.multiplier<1F||width.multiplier>1.28F)return;
        Sample sample=samples.get(part);
        if(sample==null||sample.frame!=frame)return;
        float target=width.multiplier;
        Sample previous=visible.get(part);
        if(previous!=null&&previous.frame==frame-1)
        {
            SkirtAdaptiveWidth.Width prior=previous.widths.get(bone);
            if(prior!=null&&prior.axis==width.axis)
            {
                float delta=(target-prior.multiplier)*.55F;
                target=prior.multiplier+
                        Math.max(-.07F,Math.min(.07F,delta));
            }
        }
        sample.widths.put(bone,new SkirtAdaptiveWidth.Width(width.axis,target));
    }

    public Map<String,SkirtAdaptiveWidth.Width> forRenderWidths(
            BodyPartModelData part,int frame)
    {
        if(frame!=lastTimelineFrame)return java.util.Collections.emptyMap();
        Sample sample=visible.get(part);
        if(sample==null)return java.util.Collections.emptyMap();
        return new HashMap<String,SkirtAdaptiveWidth.Width>(sample.widths);
    }

    /** Optional conservative one-tick phase lead for the exact 40-bone
     * skirt. Reads ONLY the two completed/frozen Jolt samples; it cannot
     * advance physics or expose a mid-render draw from the current tick. */
    public Map<String,float[]> forRenderSkirtLead(BodyPartModelData part,int frame)
    {
        Map<String,float[]> fallback=forRender(part,frame);
        if(fallback==null)return null;
        Sample recent=visible.get(part),prior=older.get(part);
        if(recent==null||prior==null||recent.frame!=frame-1 ||
                prior.frame!=frame-2 ||
                recent.joltBones.size()!=40 ||
                !recent.joltBones.equals(prior.joltBones))return fallback;
        Map<String,float[]> predicted=SkirtPoseVisualLead.swings(
                prior.swings,recent.swings);
        return predicted==null?fallback:predicted;
    }

    public Map<String,SkirtAdaptiveWidth.Width> forRenderSkirtWidthLead(
            BodyPartModelData part,int frame)
    {
        Map<String,SkirtAdaptiveWidth.Width> fallback=
                forRenderWidths(part,frame);
        Sample recent=visible.get(part),prior=older.get(part);
        if(recent==null||prior==null||recent.frame!=frame-1 ||
                prior.frame!=frame-2 ||
                recent.joltBones.size()!=40 ||
                !recent.joltBones.equals(prior.joltBones))return fallback;
        Map<String,SkirtAdaptiveWidth.Width> predicted=
                SkirtPoseVisualLead.widths(prior.widths,recent.widths);
        return predicted==null?fallback:predicted;
    }

    /** Frame shown in the visible snapshot, for Jolt/skirt debugging. */
    public int visibleSourceFrame(BodyPartModelData part,int frame)
    {
        Sample sample=frame==lastTimelineFrame?visible.get(part):null;
        return sample==null?Integer.MIN_VALUE:sample.frame;
    }

    /** Exactly matches the regular Jolt swing subframe interpolation window. */
    public Map<String,SkirtAdaptiveWidth.Width> forRenderWidthsInterpolated(
            BodyPartModelData part,int frame,float fraction)
    {
        Map<String,SkirtAdaptiveWidth.Width> fallback=forRenderWidths(part,frame);
        Sample previous=visible.get(part),current=samples.get(part);
        if(previous==null||current==null||previous.frame!=frame-1||
                current.frame!=frame||!Float.isFinite(fraction)||
                !previous.widths.keySet().equals(current.widths.keySet()))
            return fallback;
        float t=Math.max(0F,Math.min(1F,fraction));
        Map<String,SkirtAdaptiveWidth.Width> result=new HashMap<
                String,SkirtAdaptiveWidth.Width>();
        for(Map.Entry<String,SkirtAdaptiveWidth.Width> e:previous.widths.entrySet())
        {
            SkirtAdaptiveWidth.Width a=e.getValue(),b=current.widths.get(e.getKey());
            if(b==null||b.axis!=a.axis)return fallback;
            result.put(e.getKey(),new SkirtAdaptiveWidth.Width(a.axis,
                    a.multiplier+(b.multiplier-a.multiplier)*t));
        }
        return result;
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

    /**
     * Baking must use the completed Jolt sample of the EXACT current tick.
     * The regular Preview intentionally displays a frozen previous tick;
     * using that delayed pose for bake creates off-by-one keys and gaps.
     * The solver publishes the completed current sample after rendering;
     * lockstep playback holds the tick long enough for the next draw.
     */
    public Map<String,float[]> forBakeRender(BodyPartModelData part,int frame)
    {
        if(frame!=lastTimelineFrame)return null;
        Sample visibleSample=visible.get(part);
        if(visibleSample==null || visibleSample.frame!=frame ||
                visibleSample.joltBones.isEmpty())return null;
        return forRender(part,frame);
    }

    /**
     * Render-only sub-tick interpolation. This NEVER advances physics.
     * The previous Jolt frame is frozen at beginFrame(); the current frame
     * is published after the first normal actor draw. Only when both
     * complete and have identical driven-bone topology do we interpolate
     * local axis-angle swings via quaternion slerp. Legacy solver remains
     * unchanged. Paused/seeked frames never blend a stale previous pose.
     */
    public Map<String,float[]> forRenderInterpolated(
            BodyPartModelData part,int frame,float fraction)
    {
        Map<String,float[]> fallback=forRender(part,frame);
        if(fallback==null || !Float.isFinite(fraction))return fallback;
        Sample previous=visible.get(part);
        Sample current=samples.get(part);
        if(previous==null || current==null ||
                previous.frame!=frame-1 || current.frame!=frame ||
                previous.joltBones.isEmpty() ||
                !previous.joltBones.equals(current.joltBones) ||
                !previous.swings.keySet().equals(current.swings.keySet()))
            return fallback;
        try
        {
            return JoltPhysicsPoseInterpolator.swings(
                    previous.swings,current.swings,
                    Math.max(0F,Math.min(1F,fraction)));
        }
        catch(IllegalArgumentException invalid)
        {
            return fallback;
        }
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
        snapshot.widths.putAll(sample.widths);
        visible.put(part,snapshot);
    }

    public void clear()
    {
        samples.clear();
        visible.clear();
        older.clear();
        lastTimelineFrame = Integer.MIN_VALUE;
    }
}

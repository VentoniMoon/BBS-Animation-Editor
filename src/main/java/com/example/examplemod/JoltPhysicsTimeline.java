package com.example.examplemod;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Deterministic, fixed-tick timeline for Jolt (or a test solver).
 *
 * Unlike the live Preview adapter, this class never assumes that a pose at
 * frame N is enough to simulate frames 0..N. The caller MUST supply recorded
 * source transforms / collision shapes for EACH intervening tick. This
 * enables a future offline Jolt bake without guessing missing locomotion.
 *
 * The solver remains at the highest computed frame; reading a cached older
 * frame never rolls its native state backward. A subsequent future frame
 * advances from that highest state, not from the last cursor position.
 *
 * Physics is kept at exact 20 Hz: visual sub-tick interpolation is a
 * separate, side-effect-free concern. No Minecraft or JNI dependencies.
 */
public final class JoltPhysicsTimeline<Input, Output> implements AutoCloseable
{
    public interface FrameSource<T>
    {
        /** Evaluated CLEAN source animation and collider poses at this tick. */
        T inputAt(int frame);
    }

    public interface Solver<T, R>
    {
        /** Create/reseed a solver at the first tick and return its pose. */
        R initialize(int frame, T input);

        /** Integrate exactly one adjacent timeline tick. */
        R advance(int frame, T input);

        /** Release native Jolt scene on reset, invalidation or close. */
        void reset();
    }

    public interface Copier<T>
    {
        /** Return a deep copy; never hand mutable JNI/animation buffers out. */
        T copy(T source);
    }

    private final int firstFrame;
    private final int maxFrames;
    private final FrameSource<Input> source;
    private final Solver<Input, Output> solver;
    private final Copier<Output> copier;
    private final NavigableMap<Integer, Output> cache =
            new TreeMap<Integer, Output>();
    private long currentRevision=Long.MIN_VALUE;
    private int latestFrame=-1;
    private boolean closed;

    public JoltPhysicsTimeline(int firstFrame, int maxFrames,
            FrameSource<Input> source,Solver<Input,Output> solver,
            Copier<Output> copier)
    {
        if(firstFrame<0 || maxFrames<1 || source==null ||
                solver==null || copier==null)
            throw new IllegalArgumentException("Invalid physics timeline setup");
        this.firstFrame=firstFrame;
        this.maxFrames=maxFrames;
        this.source=source;
        this.solver=solver;
        this.copier=copier;
    }

    public synchronized Output evaluate(int frame,long sourceRevision)
    {
        requireOpen();
        if(frame<firstFrame)
            throw new IllegalArgumentException("Frame precedes physics origin");
        if((long)frame-firstFrame>=maxFrames)
            throw new IllegalArgumentException(
                    "Physics range exceeds configured replay budget");
        if(currentRevision!=sourceRevision)
        {
            invalidate();
            currentRevision=sourceRevision;
        }
        Output cached=cache.get(Integer.valueOf(frame));
        if(cached!=null)return copier.copy(cached);

        // No approximation based on the CURRENT frame's pose: fetch every
        // missing historical tick, including body motion and all contacts.
        try
        {
            for(int next=latestFrame<firstFrame?firstFrame:latestFrame+1;
                    next<=frame;next++)
            {
                Input input=source.inputAt(next);
                if(input==null)
                    throw new IllegalStateException(
                            "Missing clean animation/collider input at "+next);
                Output solved=next==firstFrame ?
                        solver.initialize(next,input) : solver.advance(next,input);
                if(solved==null)
                    throw new IllegalStateException(
                            "Missing solved Jolt pose at "+next);
                cache.put(Integer.valueOf(next),copier.copy(solved));
                latestFrame=next;
            }
        }
        catch(RuntimeException error)
        {
            invalidate(); // A partially advanced Jolt world cannot be reused.
            throw error;
        }
        catch(Error error)
        {
            invalidate();
            throw error;
        }
        return copier.copy(cache.get(Integer.valueOf(frame)));
    }

    public synchronized int latestFrame()
    {
        return latestFrame;
    }

    public synchronized int cachedFrames()
    {
        return cache.size();
    }

    public synchronized NavigableMap<Integer,Output> snapshot()
    {
        NavigableMap<Integer,Output> result=new TreeMap<Integer,Output>();
        for(java.util.Map.Entry<Integer,Output> entry:cache.entrySet())
            result.put(entry.getKey(),copier.copy(entry.getValue()));
        return Collections.unmodifiableNavigableMap(result);
    }

    /** Call for an edited key, parent transform, collision config or morph. */
    public synchronized void invalidate()
    {
        solver.reset();
        cache.clear();
        latestFrame=-1;
        currentRevision=Long.MIN_VALUE;
    }

    @Override public synchronized void close()
    {
        if(!closed)
        {
            invalidate();
            closed=true;
        }
    }

    private void requireOpen()
    {
        if(closed)throw new IllegalStateException("Physics timeline closed");
    }
}

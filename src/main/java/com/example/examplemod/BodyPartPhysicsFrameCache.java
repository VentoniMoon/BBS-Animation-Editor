package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic tick cache for a single simulated chain.
 * Never writes AnimationBone keys. Call invalidate() when the source pose,
 * attachment transform, collision geometry or settings changes.
 */
public final class BodyPartPhysicsFrameCache
{
    public interface PoseSource
    {
        /**
         * Return all animated joint positions in a common WORLD coordinate
         * space, starting with the attached root at index 0.
         * An implementation must include parent anchor and Body Part transforms.
         */
        BodyPartChainSolver.Vec3[] getWorldJoints(int frame);
    }

    private final Map<Integer, BodyPartChainSolver.Vec3[]> frames =
            new TreeMap<Integer, BodyPartChainSolver.Vec3[]>();
    private final PoseSource source;
    private final int firstFrame;
    private BodyPartChainSolver solver;
    private int lastFrame = -1;

    public BodyPartPhysicsFrameCache(PoseSource source, int firstFrame)
    {
        if (source == null) throw new IllegalArgumentException("Pose source required");
        this.source = source;
        this.firstFrame = Math.max(0, firstFrame);
    }

    public void invalidate()
    {
        frames.clear();
        solver = null;
        lastFrame = -1;
    }

    /**
     * Evaluates all missing frames in chronological order, rather than
     * stepping once at the requested cursor location.
     */
    public BodyPartChainSolver.Vec3[] evaluate(int frame, BodyPartPhysicsSettings settings)
    {
        if (frame < firstFrame || settings == null || !settings.isEnabled()) return null;
        if (frames.containsKey(frame)) return copy(frames.get(frame));

        // Seeking to a frame older than the most recently calculated one can
        // use its snapshot, or rebuild sequentially if not previously cached.
        if (lastFrame > frame) invalidate();

        if (solver == null)
        {
            BodyPartChainSolver.Vec3[] initial = source.getWorldJoints(firstFrame);
            solver = new BodyPartChainSolver(initial);
            frames.put(firstFrame, copy(initial));
            lastFrame = firstFrame;
        }

        while (lastFrame < frame)
        {
            int next = lastFrame + 1;
            BodyPartChainSolver.Vec3[] pose = source.getWorldJoints(next);
            solver.step(pose, 1F / 20F, settings.getGravity(),
                    settings.getStiffness(), settings.getDamping());
            BodyPartChainSolver.Vec3[] result = new BodyPartChainSolver.Vec3[solver.size()];
            for (int i = 0; i < result.length; i++)
                result[i] = solver.getPoint(i);
            frames.put(next, copy(result));
            lastFrame = next;
        }

        return copy(frames.get(frame));
    }

    private static BodyPartChainSolver.Vec3[] copy(BodyPartChainSolver.Vec3[] array)
    {
        if (array == null) throw new IllegalArgumentException("Missing pose");
        BodyPartChainSolver.Vec3[] result = new BodyPartChainSolver.Vec3[array.length];
        System.arraycopy(array, 0, result, 0, array.length);
        return result;
    }
}

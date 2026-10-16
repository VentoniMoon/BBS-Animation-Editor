package com.example.examplemod;

/**
 * Per-sample diagnostic wrapper around a mesh surface constraint.
 * Tracks actual corrections, not merely collider availability.
 * No allocation inside project(); keeps the delegate's output unchanged.
 */
public final class BodyPartPhysicsSurfaceProbe
        implements BodyPartChainSolver.SurfaceConstraint
{
    private final BodyPartChainSolver.SurfaceConstraint delegate;
    private int calls;
    private int corrections;
    private float largestShift;

    public BodyPartPhysicsSurfaceProbe(BodyPartChainSolver.SurfaceConstraint delegate)
    {
        if (delegate == null)
            throw new IllegalArgumentException("Missing surface constraint");
        this.delegate = delegate;
    }

    @Override
    public BodyPartChainSolver.Vec3 project(BodyPartChainSolver.Vec3 point,
                                           int particleIndex)
    {
        calls++;
        BodyPartChainSolver.Vec3 result = delegate.project(point, particleIndex);
        if (point != null && result != null)
        {
            float shift = result.sub(point).length();
            if (Float.isFinite(shift) && shift > 0.000001F)
            {
                corrections++;
                largestShift = Math.max(largestShift, shift);
            }
        }
        return result;
    }

    public int getCalls() { return calls; }
    public int getCorrections() { return corrections; }
    public float getLargestShift() { return largestShift; }
}

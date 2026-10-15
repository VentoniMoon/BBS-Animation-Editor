package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/**
 * World-space collision primitives for strands. All coordinates are blocks.
 * Actor/head/chest/back colliders must be rebuilt with their animated world
 * transforms each simulation tick. Nothing here assumes upright actors.
 */
public final class BodyPartPhysicsCollisions
{
    private BodyPartPhysicsCollisions() {}

    public static final class Capsule
    {
        public final BodyPartChainSolver.Vec3 a, b;
        public final float radius;

        public Capsule(BodyPartChainSolver.Vec3 a, BodyPartChainSolver.Vec3 b,
                       float radius)
        {
            if (a == null || b == null || !(radius > 0F))
                throw new IllegalArgumentException("Invalid body capsule");
            this.a = a;
            this.b = b;
            this.radius = radius;
        }

        public BodyPartChainSolver.Vec3 resolve(
                BodyPartChainSolver.Vec3 point, float strandRadius)
        {
            BodyPartChainSolver.Vec3 axis = b.sub(a);
            float lengthSquared = dot(axis, axis);
            float t = lengthSquared < 1e-10F ? 0F :
                    clamp(dot(point.sub(a), axis) / lengthSquared, 0F, 1F);
            BodyPartChainSolver.Vec3 closest = a.add(axis.scale(t));
            BodyPartChainSolver.Vec3 delta = point.sub(closest);
            float distance = delta.length();
            float target = radius + Math.max(0F, strandRadius);
            if (distance >= target) return point;

            if (distance < 1e-7F)
            {
                // Pick a deterministic perpendicular rather than NaN when a
                // particle exactly coincides with the capsule centre.
                BodyPartChainSolver.Vec3 unit = lengthSquared > 1e-10F ?
                        axis.scale(1F / (float)Math.sqrt(lengthSquared)) :
                        new BodyPartChainSolver.Vec3(0F,1F,0F);
                BodyPartChainSolver.Vec3 helper =
                        Math.abs(unit.y) < 0.9F ?
                        new BodyPartChainSolver.Vec3(0F,1F,0F) :
                        new BodyPartChainSolver.Vec3(1F,0F,0F);
                delta = cross(unit, helper);
                distance = delta.length();
            }
            return closest.add(delta.scale(target / distance));
        }
    }

    public static BodyPartChainSolver.Vec3 resolve(
            BodyPartChainSolver.Vec3 point, float strandRadius,
            List<Capsule> colliders)
    {
        if (point == null) throw new IllegalArgumentException("Missing particle");
        if (colliders == null || colliders.isEmpty()) return point;
        BodyPartChainSolver.Vec3 result = point;
        // A pass through the colliders may push a particle into an earlier
        // collider. Iterating provides a stable approximate separation.
        for (int iteration = 0; iteration < 3; iteration++)
            for (Capsule capsule : colliders)
                if (capsule != null) result = capsule.resolve(result, strandRadius);
        return result;
    }

    /**
     * Resolve the interior of a strand segment as well as its endpoints.
     * The midpoint is projected against the body capsule and its correction
     * is distributed to both free joints. The root remains pinned.
     * This is an approximate capsule/segment contact, not continuous CCD.
     */
    public static BodyPartChainSolver.Vec3[] resolveSegment(
            BodyPartChainSolver.Vec3 start, BodyPartChainSolver.Vec3 end,
            float strandRadius, List<Capsule> colliders, boolean pinStart)
    {
        if (start == null || end == null)
            throw new IllegalArgumentException("Missing segment endpoint");
        if (colliders == null || colliders.isEmpty())
            return new BodyPartChainSolver.Vec3[] {start, end};
        BodyPartChainSolver.Vec3 midpoint = start.add(end).scale(0.5F);
        BodyPartChainSolver.Vec3 corrected = resolve(midpoint, strandRadius, colliders);
        BodyPartChainSolver.Vec3 correction = corrected.sub(midpoint);
        if (correction.length() < 0.000001F)
            return new BodyPartChainSolver.Vec3[] {start, end};
        if (pinStart)
            return new BodyPartChainSolver.Vec3[] {start, end.add(correction.scale(2F))};
        return new BodyPartChainSolver.Vec3[] {
                start.add(correction), end.add(correction)
        };
    }

    private static float clamp(float n,float min,float max)
    {
        return Math.max(min,Math.min(max,n));
    }

    private static float dot(BodyPartChainSolver.Vec3 a,
                             BodyPartChainSolver.Vec3 b)
    {
        return a.x*b.x+a.y*b.y+a.z*b.z;
    }

    private static BodyPartChainSolver.Vec3 cross(BodyPartChainSolver.Vec3 a,
                                                   BodyPartChainSolver.Vec3 b)
    {
        return new BodyPartChainSolver.Vec3(
                a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);
    }
}

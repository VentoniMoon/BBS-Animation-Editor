package com.example.examplemod;

/**
 * Opt-in preview alternative to BodyPartPhysicsHierarchySwing.
 *
 * Contract: next() returns the LOCAL axis-angle to apply to the Chameleon
 * bone, without a root-name dependent sign or a second inversion in caller.
 * The source matrix is the bone's CLEAN original world transform.
 *
 * Enabled by the editor's USE_CANONICAL_PHYSICS_SWING switch for in-game validation.
 */
public final class BodyPartPhysicsHierarchySwingCanonical
{
    private final float[] accumulated = new float[] {1,0,0,0,1,0,0,0,1};

    public float[] next(float[] originalBoneWorld,
                        BodyPartChainSolver.Vec3 originalWorldDirection,
                        BodyPartChainSolver.Vec3 solvedWorldDirection)
    {
        BodyPartChainSolver.Vec3 correctedGoal = inverseRotate(accumulated, solvedWorldDirection);
        BodyPartChainSolver.Vec3 rest =
                BodyPartPhysicsPoseAdapter.toLocalDirection(originalBoneWorld, originalWorldDirection);
        BodyPartChainSolver.Vec3 goal =
                BodyPartPhysicsPoseAdapter.toLocalDirection(originalBoneWorld, correctedGoal);
        float[] swing = BodyPartPhysicsPoseAdapter.localSwing(rest, goal);
        swing[3] = Math.max(-1.20F, Math.min(1.20F, swing[3]));

        float x = originalBoneWorld[0] * swing[0] + originalBoneWorld[4] * swing[1] + originalBoneWorld[8] * swing[2];
        float y = originalBoneWorld[1] * swing[0] + originalBoneWorld[5] * swing[1] + originalBoneWorld[9] * swing[2];
        float z = originalBoneWorld[2] * swing[0] + originalBoneWorld[6] * swing[1] + originalBoneWorld[10] * swing[2];
        float length = (float) Math.sqrt(x*x + y*y + z*z);

        if (length > 1E-7F && Math.abs(swing[3]) > 1E-7F)
        {
            float[] delta = BodyPartPhysicsRotation.axisAngle(
                    new float[] {x/length, y/length, z/length, swing[3]});
            // Delta is in the CLEAN bone basis, before ancestor corrections.
            float[] combined = BodyPartPhysicsRotation.multiply3(accumulated, delta);
            System.arraycopy(combined, 0, accumulated, 0, 9);
        }
        return swing;
    }

    private static BodyPartChainSolver.Vec3 inverseRotate(float[] m,
                                                           BodyPartChainSolver.Vec3 v)
    {
        return new BodyPartChainSolver.Vec3(
                m[0]*v.x + m[1]*v.y + m[2]*v.z,
                m[3]*v.x + m[4]*v.y + m[5]*v.z,
                m[6]*v.x + m[7]*v.y + m[8]*v.z);
    }
}

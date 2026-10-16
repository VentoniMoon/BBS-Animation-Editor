package com.example.examplemod;

/**
 * Converts a world-space solved strand into hierarchical local bone swings.
 *
 * Each child is evaluated in the pose created by corrections to its ancestors,
 * not against the original uncorrected bone basis. This avoids applying an
 * independent full world-space target rotation at every level of a chain.
 * Preview-only math; no Chameleon or GL dependencies.
 */
public final class BodyPartPhysicsHierarchySwing
{
    private final float[] ancestorRotation = identity();
    private final boolean physicalSign;

    public BodyPartPhysicsHierarchySwing(boolean physicalSign)
    {
        this.physicalSign=physicalSign;
    }

    public BodyPartPhysicsHierarchySwing()
    {
        this(false);
    }

    private static float[] identity()
    {
        return new float[]{1F,0F,0F,0F,1F,0F,0F,0F,1F};
    }

    private static BodyPartChainSolver.Vec3 rotate(float[] m, BodyPartChainSolver.Vec3 v)
    {
        return new BodyPartChainSolver.Vec3(
                m[0]*v.x+m[3]*v.y+m[6]*v.z,
                m[1]*v.x+m[4]*v.y+m[7]*v.z,
                m[2]*v.x+m[5]*v.y+m[8]*v.z);
    }

    private static BodyPartChainSolver.Vec3 inverseRotate(
            float[] m, BodyPartChainSolver.Vec3 v)
    {
        return new BodyPartChainSolver.Vec3(
                m[0]*v.x+m[1]*v.y+m[2]*v.z,
                m[3]*v.x+m[4]*v.y+m[5]*v.z,
                m[6]*v.x+m[7]*v.y+m[8]*v.z);
    }

    /**
     * Returns the actual local swing required at this bone. The caller is
     * responsible for adapting the swing to the Chameleon pose convention.
     * Bone matrices include the complete original actor/attachment pose.
     */
    public float[] next(float[] originalBoneWorld,
            BodyPartChainSolver.Vec3 originalWorldDirection,
            BodyPartChainSolver.Vec3 solvedWorldDirection)
    {
        BodyPartChainSolver.Vec3 correctedGoal =
                inverseRotate(ancestorRotation,solvedWorldDirection);
        BodyPartChainSolver.Vec3 rest =
                BodyPartPhysicsPoseAdapter.toLocalDirection(
                        originalBoneWorld,originalWorldDirection);
        BodyPartChainSolver.Vec3 goal =
                BodyPartPhysicsPoseAdapter.toLocalDirection(
                        originalBoneWorld,correctedGoal);
        float[] swing=BodyPartPhysicsPoseAdapter.localSwing(rest,goal);
        // Match BodyPartChameleonPhysicsPose's per-bone visual limit.
        // Otherwise descendants are solved as though their parent received
        // a rotation which the renderer silently clamps away.
        swing[3]=Math.max(-0.45F,Math.min(0.45F,swing[3]));

        // For back hair use the desired rendered swing in world space;
        // other strands retain their established preview convention.
        // Accumulate it for descendants. The inverse adapter angle
        // returned below is negated back by the preview writer.
        // Convert the local axis into the original world basis first.
        BodyPartChainSolver.Vec3 localAxis=new BodyPartChainSolver.Vec3(
                swing[0],swing[1],swing[2]);
        BodyPartChainSolver.Vec3 worldAxis=new BodyPartChainSolver.Vec3(
                originalBoneWorld[0]*localAxis.x+
                        originalBoneWorld[4]*localAxis.y+
                        originalBoneWorld[8]*localAxis.z,
                originalBoneWorld[1]*localAxis.x+
                        originalBoneWorld[5]*localAxis.y+
                        originalBoneWorld[9]*localAxis.z,
                originalBoneWorld[2]*localAxis.x+
                        originalBoneWorld[6]*localAxis.y+
                        originalBoneWorld[10]*localAxis.z);
        float axisLength=worldAxis.length();
        if(axisLength>1E-7F && Math.abs(swing[3])>1E-7F)
        {
            float[] delta=BodyPartPhysicsRotation.axisAngle(new float[]{
                    worldAxis.x/axisLength,worldAxis.y/axisLength,
                    worldAxis.z/axisLength,physicalSign?swing[3]:-swing[3]});
            float[] accumulated=BodyPartPhysicsRotation.multiply3(
                    ancestorRotation,delta);
            System.arraycopy(accumulated,0,ancestorRotation,0,9);
        }
        // The preview writer converts the adapter swing by negating its
        // angle before applying it to Chameleon. Return the inverse adapter
        // angle, so the ACTUALLY RENDERED rotation matches the physical
        // localSwing(rest, goal) calculated above. The ancestor matrix
        // already follows the rendered positive angle (see below).
        return new float[]{swing[0],swing[1],swing[2],
                physicalSign?-swing[3]:swing[3]};
    }
}

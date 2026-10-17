package com.example.examplemod;

import java.util.List;

/**
 * Collects actual world-space joints from a renderer-owned matrix provider.
 * No guesswork about Chameleon axis signs or the parent's anchor rotation.
 *
 * The provider must evaluate matrices in the same pose and coordinate system
 * for all joints, *including* parent attachment transforms.
 */
public final class BodyPartPhysicsWorldPose
{
    public interface MatrixSource
    {
        /** OpenGL column-major bone world matrix, or null if unavailable. */
        float[] getBoneWorldMatrix(String boneName);
    }

    private BodyPartPhysicsWorldPose() {}

    /**
     * Native Chameleon matrices are relative to the attached morph.
     * Wrap them in the real, animated Metamorph attachment matrix before
     * passing them to the solver. The attachment matrix must be captured
     * from the rendering pass, not reconstructed using editor pivot values.
     */
    public static MatrixSource worldSource(final float[] worldFromAttachment,
                                           final MatrixSource localSource)
    {
        if (localSource == null)
            throw new IllegalArgumentException("Missing native local matrix provider");
        final float[] attachment = BodyPartPhysicsMatrixSpace.snapshot(worldFromAttachment);
        return new MatrixSource()
        {
            @Override
            public float[] getBoneWorldMatrix(String boneName)
            {
                float[] local = localSource.getBoneWorldMatrix(boneName);
                return local == null ? null :
                        BodyPartPhysicsMatrixSpace.childWorld(attachment, local);
            }
        };
    }

    /**
     * A physical strand with N bones has N+1 particles: bone pivots plus
     * the tip of the final bone. Omitting the tip would leave the last
     * segment with no simulated direction.
     *
     * @param finalTipLocalPoint tip of the final bone in its local units
     */
    public static BodyPartChainSolver.Vec3[] collect(
            BodyPartPhysicsChains.Chain chain,
            MatrixSource source,
            BodyPartChainSolver.Vec3 finalTipLocalPoint)
    {
        if (chain == null || source == null || finalTipLocalPoint == null)
            throw new IllegalArgumentException("Missing strand pose input");
        List<AnimationBone> bones = chain.bones;
        if (bones.isEmpty())
            throw new IllegalArgumentException("Strand needs at least one bone");

        BodyPartChainSolver.Vec3[] joints =
                new BodyPartChainSolver.Vec3[bones.size() + 1];
        float[] lastMatrix = null;
        for (int i = 0; i < bones.size(); i++)
        {
            AnimationBone bone = bones.get(i);
            if (bone == null || bone.getName() == null)
                throw new IllegalArgumentException("Unnamed physics bone");
            float[] matrix = source.getBoneWorldMatrix(bone.getName());
            if (matrix == null || matrix.length != 16)
                throw new IllegalStateException(
                        "World bone matrix unavailable: " + bone.getName());
            joints[i] = BodyPartPhysicsPoseAdapter.position(matrix);
            lastMatrix = matrix;
        }
        joints[bones.size()] =
                BodyPartPhysicsPoseAdapter.transformPoint(lastMatrix, finalTipLocalPoint);
        for (int i = 1; i < joints.length; i++)
        {
            if (joints[i].sub(joints[i - 1]).length() < 0.00001F)
                throw new IllegalStateException(
                        "Zero-length strand segment near " +
                        bones.get(Math.min(i, bones.size() - 1)).getName());
        }
        return joints;
    }
}

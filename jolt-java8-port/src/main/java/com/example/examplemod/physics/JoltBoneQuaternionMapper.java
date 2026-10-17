package com.example.examplemod.physics;

/**
 * Coordinate-space bridge between Jolt's world-space rigid-body orientation
 * and the target Chameleon bone's local quaternion.
 *
 * All quaternions use x,y,z,w order and the same right-handed world frame.
 * Render-specific handedness and axis conversion must happen OUTSIDE this
 * module, at a single verified importer boundary.
 */
public final class JoltBoneQuaternionMapper {
    private JoltBoneQuaternionMapper() { }

    /** Fixed correction from the initial rigid-body basis to its bone basis. */
    public static float[] restCorrection(float[] bodyRestWorld, float[] boneRestWorld) {
        return multiply(inverse(bodyRestWorld), boneRestWorld);
    }

    /**
     * Solve the target local bone rotation exactly in quaternion space:
     * parentWorld * result = bodyWorld * bodyToBoneCorrection.
     */
    public static float[] localBoneRotation(float[] parentWorld,
            float[] bodyWorld, float[] bodyToBoneCorrection) {
        return multiply(inverse(parentWorld),
                multiply(bodyWorld, bodyToBoneCorrection));
    }

    /** Choose the same quaternion hemisphere as the preceding animation tick. */
    public static float[] continuous(float[] previous, float[] current) {
        float[] p = normalized(previous);
        float[] q = normalized(current);
        float dot = p[0]*q[0] + p[1]*q[1] + p[2]*q[2] + p[3]*q[3];
        if (dot < 0F) {
            for (int i = 0; i < 4; i++) q[i] = -q[i];
        }
        return q;
    }

    public static float[] multiply(float[] first, float[] second) {
        float[] a=normalized(first), b=normalized(second);
        float x=a[3]*b[0] + a[0]*b[3] + a[1]*b[2] - a[2]*b[1];
        float y=a[3]*b[1] - a[0]*b[2] + a[1]*b[3] + a[2]*b[0];
        float z=a[3]*b[2] + a[0]*b[1] - a[1]*b[0] + a[2]*b[3];
        float w=a[3]*b[3] - a[0]*b[0] - a[1]*b[1] - a[2]*b[2];
        return normalized(new float[] {x,y,z,w});
    }

    public static float[] inverse(float[] q) {
        float[] n=normalized(q);
        return new float[] {-n[0],-n[1],-n[2],n[3]};
    }

    public static float[] normalized(float[] q) {
        if (q == null || q.length != 4)
            throw new IllegalArgumentException("Quaternion must be x,y,z,w");
        double sum=0D;
        for(float c:q) {
            if(!Float.isFinite(c))throw new IllegalArgumentException("Non-finite quaternion");
            sum += c*c;
        }
        if (!(sum > 1E-16D))
            throw new IllegalArgumentException("Zero-length quaternion");
        float inv=(float)(1D/Math.sqrt(sum));
        return new float[] {q[0]*inv,q[1]*inv,q[2]*inv,q[3]*inv};
    }
}

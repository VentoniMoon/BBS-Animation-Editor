package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only Jolt -> Chameleon pose diagnostic.
 *
 * Predict the segment directions IF Chameleon applied each Jolt axis-angle
 * exactly in its bone-local frame, including corrected parent rotations.
 * Compare these directions with the real post-render native bone pivots.
 *
 * Does not touch physics, keyframes, or the renderer; Java 8 pure math.
 */
public final class BodyPartJoltPoseDiagnostic
{
    private BodyPartJoltPoseDiagnostic() {}

    /**
     * One expected world-space direction per bone in the chain.
     * cleanWorld includes the real actor/BodyPart attachment and native
     * Chameleon transforms BEFORE applying this physics pose.
     */
    public static Map<String, float[]> expectedDirections(
            String[] names, float[][] cleanWorld, float[] terminalTip,
            Map<String, float[]> localSwings)
    {
        if (names == null || cleanWorld == null ||
                names.length == 0 || names.length != cleanWorld.length ||
                terminalTip == null || terminalTip.length != 3 ||
                localSwings == null)
            throw new IllegalArgumentException("Invalid diagnostic chain");

        Map<String, float[]> directions = new LinkedHashMap<String, float[]>();
        float[] accumulated = identity3();

        for (int i = 0; i < names.length; i++)
        {
            float[] bone = cleanWorld[i];
            float[] next = i + 1 < names.length
                    ? point(cleanWorld[i + 1]) : terminalTip;
            float[] origin = point(bone);
            float[] initialDirection = normalize(new float[] {
                    next[0] - origin[0],
                    next[1] - origin[1],
                    next[2] - origin[2]
            });

            float[] swing = localSwings.get(names[i]);
            if (swing == null || swing.length != 4)
                throw new IllegalArgumentException("Missing Jolt swing for " + names[i]);

            // RworldSwing = RcleanWorld * RlocalSwing * inverse(RcleanWorld).
            // First bring it into the already-corrected ancestor frame.
            float[] originalBasis = orthonormalBasis(bone);
            float[] worldSwing = BodyPartPhysicsRotation.multiply3(
                    BodyPartPhysicsRotation.multiply3(
                            originalBasis, BodyPartPhysicsRotation.axisAngle(swing)),
                    transpose3(originalBasis));

            accumulated = BodyPartPhysicsRotation.multiply3(accumulated, worldSwing);
            directions.put(names[i], normalize(transform3(accumulated, initialDirection)));
        }
        return directions;
    }

    public static float[] direction(float[] parent, float[] child)
    {
        float[] a = point(parent), b = point(child);
        return normalize(new float[] {b[0]-a[0], b[1]-a[1], b[2]-a[2]});
    }

    public static float[] directionToTip(float[] bone, float[] tip)
    {
        if (tip == null || tip.length != 3)
            throw new IllegalArgumentException("Invalid tip");
        float[] p = point(bone);
        return normalize(new float[] {tip[0]-p[0], tip[1]-p[1], tip[2]-p[2]});
    }

    /**
     * Angle between two CLEAN native attachment frames. A sudden change
     * here means a frozen Jolt sample was captured under a different
     * parent's orientation. Classify that event as SOURCE_CHANGED rather
     * than incorrectly blaming the Chameleon bone-Euler adapter.
     */
    public static float parentRotationChangeDegrees(float[] first,
            float[] second)
    {
        float[] a=orthonormalBasis(first);
        float[] b=orthonormalBasis(second);
        // Trace(Ra^-1 * Rb) is the sum of the three corresponding
        // unit-axis dot products, independent of scale or translation.
        double trace=0D;
        for(int i=0;i<9;i++)
            trace+=a[i]*b[i];
        double cosine=Math.max(-1D,Math.min(1D,(trace-1D)*0.5D));
        return (float)Math.toDegrees(Math.acos(cosine));
    }

    public static float errorDegrees(float[] expected, float[] actual)
    {
        float[] a = normalize(expected), b = normalize(actual);
        double cosine = Math.max(-1D, Math.min(1D,
                a[0]*b[0] + a[1]*b[1] + a[2]*b[2]));
        return (float)Math.toDegrees(Math.acos(cosine));
    }

    private static float[] point(float[] matrix)
    {
        if (matrix == null || matrix.length != 16)
            throw new IllegalArgumentException("Expected column-major 4x4");
        return new float[] {matrix[12], matrix[13], matrix[14]};
    }

    /**
     * Remove positive scale from an affine native Chameleon matrix.
     * Reflections/shear are rejected: diagnostic must not guess handedness.
     */
    private static float[] orthonormalBasis(float[] m)
    {
        if (m == null || m.length != 16)
            throw new IllegalArgumentException("Expected native 4x4");
        float[] basis = new float[9];
        for (int c = 0; c < 3; c++)
        {
            float[] axis = normalize(new float[] {
                    m[c*4], m[c*4+1], m[c*4+2]
            });
            for (int r = 0; r < 3; r++)
                basis[c*3+r] = axis[r];
        }
        float dot01 = basis[0]*basis[3] + basis[1]*basis[4] + basis[2]*basis[5];
        float dot02 = basis[0]*basis[6] + basis[1]*basis[7] + basis[2]*basis[8];
        float dot12 = basis[3]*basis[6] + basis[4]*basis[7] + basis[5]*basis[8];
        float determinant = basis[0]*(basis[4]*basis[8]-basis[5]*basis[7])
                -basis[3]*(basis[1]*basis[8]-basis[2]*basis[7])
                +basis[6]*(basis[1]*basis[5]-basis[2]*basis[4]);
        if (Math.abs(dot01) > 0.02F || Math.abs(dot02) > 0.02F ||
                Math.abs(dot12) > 0.02F || determinant < 0.95F)
            throw new IllegalArgumentException("Sheared or mirrored native bone basis");
        return basis;
    }

    private static float[] transform3(float[] matrix, float[] v)
    {
        return new float[] {
                matrix[0]*v[0] + matrix[3]*v[1] + matrix[6]*v[2],
                matrix[1]*v[0] + matrix[4]*v[1] + matrix[7]*v[2],
                matrix[2]*v[0] + matrix[5]*v[1] + matrix[8]*v[2]
        };
    }

    private static float[] transpose3(float[] m)
    {
        return new float[] {
                m[0], m[3], m[6],
                m[1], m[4], m[7],
                m[2], m[5], m[8]
        };
    }

    private static float[] identity3()
    {
        return new float[] {1F,0F,0F, 0F,1F,0F, 0F,0F,1F};
    }

    private static float[] normalize(float[] v)
    {
        if (v == null || v.length != 3)
            throw new IllegalArgumentException("Expected 3D vector");
        double lengthSq = 0D;
        for (float f : v)
        {
            if (!Float.isFinite(f))
                throw new IllegalArgumentException("Non-finite direction");
            lengthSq += f*f;
        }
        if (!(lengthSq > 1E-12D))
            throw new IllegalArgumentException("Zero-length strand direction");
        float inverse = (float)(1D / Math.sqrt(lengthSq));
        return new float[] {v[0]*inverse, v[1]*inverse, v[2]*inverse};
    }
}

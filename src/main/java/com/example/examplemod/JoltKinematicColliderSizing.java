package com.example.examplemod;

/**
 * Keeps Jolt kinematic collision shapes rigid while their animated world
 * transforms move. The fitted model may deform each tick, but an existing
 * native capsule or box cannot change size without recreating its owner rig.
 * Lock dimensions only for consecutive samples of the SAME scene.
 */
public final class JoltKinematicColliderSizing
{
    private JoltKinematicColliderSizing() {}

    public static final class Capsule
    {
        public final float[] a, b;
        public final float radius;

        private Capsule(float[] a, float[] b, float radius)
        {
            this.a = a;
            this.b = b;
            this.radius = radius;
        }
    }

    /** Keep the previous capsule's dimensions, following its new center/axis. */
    public static Capsule holdCapsule(float[] a, float[] b, float radius,
            float[] oldA, float[] oldB, float oldRadius)
    {
        if (!valid3(a) || !valid3(b) || !valid3(oldA) || !valid3(oldB))
            throw new IllegalArgumentException("Invalid animated capsule endpoints");

        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ox = oldB[0] - oldA[0], oy = oldB[1] - oldA[1],
                oz = oldB[2] - oldA[2];
        double referenceLength = Math.sqrt(ox * ox + oy * oy + oz * oz);
        if (!(length > 1E-6D) || !(referenceLength > 1E-6D) ||
                !Double.isFinite(length) || !Double.isFinite(referenceLength) ||
                !Float.isFinite(oldRadius) || !(oldRadius > 0F))
            throw new IllegalArgumentException("Invalid animated capsule dimensions");

        double scale = referenceLength / (2D * length);
        float[] start = new float[3];
        float[] end = new float[3];
        double[] center = {(a[0] + (double)b[0]) * 0.5D,
                (a[1] + (double)b[1]) * 0.5D,
                (a[2] + (double)b[2]) * 0.5D};
        double[] direction = {dx, dy, dz};
        for (int i = 0; i < 3; i++)
        {
            start[i] = (float)(center[i] - direction[i] * scale);
            end[i] = (float)(center[i] + direction[i] * scale);
        }
        return new Capsule(start, end, oldRadius);
    }

    /** Preserve the dimensions of each zone in a multi-capsule torso. */
    public static void holdCompound(float[][] a, float[][] b, float[] radii,
            float[][] oldA, float[][] oldB, float[] oldRadii)
    {
        if (a == null || b == null || radii == null || oldA == null ||
                oldB == null || oldRadii == null || a.length != b.length ||
                a.length != radii.length || a.length != oldA.length ||
                a.length != oldB.length || a.length != oldRadii.length)
            throw new IllegalArgumentException("Compound collider topology mismatch");
        for (int i = 0; i < radii.length; i++)
        {
            Capsule stable = holdCapsule(a[i], b[i], radii[i],
                    oldA[i], oldB[i], oldRadii[i]);
            a[i] = stable.a;
            b[i] = stable.b;
            radii[i] = stable.radius;
        }
    }

    /**
     * Keep authored primitive dimensions while following animated transforms.
     * False indicates a real count/type change requiring normal scene rebuild.
     */
    public static boolean holdPrimitiveSizes(float[][] current,
            float[][] previous, int rowSize)
    {
        if (current == null || previous == null ||
                current.length != previous.length ||
                (rowSize != 10 && rowSize != 11))
            return false;
        for (int i = 0; i < current.length; i++)
        {
            if (current[i] == null || previous[i] == null ||
                    current[i].length != rowSize || previous[i].length != rowSize ||
                    (rowSize == 11 && current[i][10] != previous[i][10]))
                return false;
            for (int axis = 7; axis < 10; axis++)
                if (!Float.isFinite(current[i][axis]) ||
                        !Float.isFinite(previous[i][axis]))
                    return false;
        }
        for (int i = 0; i < current.length; i++)
            System.arraycopy(previous[i], 7, current[i], 7, 3);
        return true;
    }

    private static boolean valid3(float[] xyz)
    {
        if (xyz == null || xyz.length != 3) return false;
        for (float x : xyz) if (!Float.isFinite(x)) return false;
        return true;
    }
}

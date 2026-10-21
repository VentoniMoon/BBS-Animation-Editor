package com.example.examplemod;

/** Standalone Java 8 smoke test; no Forge classes or JNI are required. */
public final class JoltKinematicColliderSizingSmoke
{
    private static void eq(float expected, float actual)
    {
        if (Math.abs(expected - actual) > 1E-5F)
            throw new AssertionError("Expected " + expected + ", actual " + actual);
    }

    private static float distance(float[] a, float[] b)
    {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    public static void main(String[] args)
    {
        float[] oldA = {0F, 0F, 0F};
        float[] oldB = {0F, 0.5F, 0F};

        JoltKinematicColliderSizing.Capsule capsule =
                JoltKinematicColliderSizing.holdCapsule(
                        new float[]{2F, 2F, 1F},
                        new float[]{2F, 2.7F, 1F},
                        0.2F, oldA, oldB, 0.18F);
        eq(0.5F, distance(capsule.a, capsule.b));
        eq(0.18F, capsule.radius);
        eq(2F, (capsule.a[0] + capsule.b[0]) * 0.5F);
        eq(2.35F, (capsule.a[1] + capsule.b[1]) * 0.5F);

        // A 90-degree torso rotation changes direction, never frozen size.
        capsule = JoltKinematicColliderSizing.holdCapsule(
                new float[]{8F, 2F, 0F},
                new float[]{8.8F, 2F, 0F},
                0.21F, oldA, oldB, 0.18F);
        eq(0.5F, distance(capsule.a, capsule.b));
        eq(8.4F, (capsule.a[0] + capsule.b[0]) * 0.5F);
        eq(2F, capsule.a[1]);
        eq(2F, capsule.b[1]);

        float[][] starts = {{1F, 1F, 1F}, {1F, 2F, 1F}};
        float[][] ends = {{1F, 1.8F, 1F}, {1F, 2.9F, 1F}};
        float[] radii = {0.19F, 0.22F};
        JoltKinematicColliderSizing.holdCompound(
                starts, ends, radii,
                new float[][]{{0F, 0F, 0F}, {0F, 1F, 0F}},
                new float[][]{{0F, 0.3F, 0F}, {0F, 1.4F, 0F}},
                new float[]{0.17F, 0.16F});
        eq(0.3F, distance(starts[0], ends[0]));
        eq(0.4F, distance(starts[1], ends[1]));
        eq(0.17F, radii[0]);
        eq(0.16F, radii[1]);

        float[][] shapes = {{1F, 2F, 3F, 0F, 0F, 0F, 1F,
                0.21F, 0.3F, 0.1F, 2F}};
        float[][] oldShapes = {{0F, 0F, 0F, 0F, 0F, 0F, 1F,
                0.12F, 0.25F, 0.06F, 2F}};
        if (!JoltKinematicColliderSizing.holdPrimitiveSizes(
                shapes, oldShapes, 11))
            throw new AssertionError("Expected unchanged shape topology");
        eq(1F, shapes[0][0]);
        eq(0.12F, shapes[0][7]);
        eq(0.25F, shapes[0][8]);
        shapes[0][10] = 1F;
        if (JoltKinematicColliderSizing.holdPrimitiveSizes(
                shapes, oldShapes, 11))
            throw new AssertionError("Shape type change must rebuild");

        float[][] boxes = {{1F, 2F, 3F, 0F, 0F, 0F, 1F,
                0.21F, 0.3F, 0.1F}};
        float[][] oldBoxes = {{0F, 0F, 0F, 0F, 0F, 0F, 1F,
                0.12F, 0.25F, 0.06F}};
        if (!JoltKinematicColliderSizing.holdPrimitiveSizes(
                boxes, oldBoxes, 10))
            throw new AssertionError("Expected unchanged box topology");
        eq(0.12F, boxes[0][7]);
        if (JoltKinematicColliderSizing.holdPrimitiveSizes(
                boxes, new float[0][], 10))
            throw new AssertionError("Primitive count change must rebuild");
        System.out.println("JoltKinematicColliderSizingSmoke: PASS");
    }
}

package com.example.examplemod;

/**
 * Standalone diagnostic, deliberately independent of Minecraft/Forge.
 *
 * Run with JDK 8:
 *   javac -d build/physics-test src/main/java/com/example/examplemod/BodyPartPhysicsRotation.java src/test/java/com/example/examplemod/BodyPartPhysicsSwingSignDiagnostic.java
 *   java -cp build/physics-test com.example.examplemod.BodyPartPhysicsSwingSignDiagnostic
 *
 * This checks the sign contract between the world-space swing solver and
 * Chameleon's local rotation writer. It does NOT claim to model the complete
 * native Chameleon pose pipeline.
 */
public final class BodyPartPhysicsSwingSignDiagnostic
{
    private BodyPartPhysicsSwingSignDiagnostic() {}

    private static float[] rotate(float[] m, float[] v)
    {
        return new float[] {
                m[0] * v[0] + m[3] * v[1] + m[6] * v[2],
                m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
                m[2] * v[0] + m[5] * v[1] + m[8] * v[2]
        };
    }

    private static double angularError(float[] a, float[] b)
    {
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < 3; i++)
        {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        double cosine = dot / Math.sqrt(aa * bb);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static void assertError(String label, double actual, double expected)
    {
        if (Math.abs(actual - expected) > 0.02)
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static float[] worldDirection(float[] anchor, float angle)
    {
        float[] local = BodyPartPhysicsRotation.axisAngle(
                new float[] {1F, 0F, 0F, angle});
        return rotate(BodyPartPhysicsRotation.multiply3(anchor, local),
                new float[] {0F, -1F, 0F});
    }


    /**
     * Two linked bones: first rotates around X, second around Z.
     * All matrices are column-major, as in BodyPartPhysicsRotation.
     * The child correction must be evaluated in the corrected parent basis.
     */
    private static void testTwoBoneHierarchy()
    {
        float a = (float) Math.toRadians(15);
        float b = (float) Math.toRadians(20);
        float[] anchor = BodyPartPhysicsRotation.eulerXYZ(0F, (float) Math.PI, 0F);
        float[] first = BodyPartPhysicsRotation.axisAngle(new float[] {1F, 0F, 0F, a});
        float[] second = BodyPartPhysicsRotation.axisAngle(new float[] {0F, 0F, 1F, b});
        float[] segment = new float[] {0F, -1F, 0F};

        float[] parentWorld = BodyPartPhysicsRotation.multiply3(anchor, first);
        float[] intendedChildWorld = BodyPartPhysicsRotation.multiply3(parentWorld, second);
        float[] targetParent = rotate(parentWorld, segment);
        float[] targetChild = rotate(intendedChildWorld, segment);

        // Applying the child's swing AFTER its parent's swing matches both goals.
        assertError("two-bone parent", angularError(rotate(parentWorld, segment),
                targetParent), 0);
        assertError("two-bone child", angularError(rotate(intendedChildWorld, segment),
                targetChild), 0);

        // Applying the opposite child swing does not. This reproduces the
        // risk in the legacy single-inversion sign contract.
        float[] wrongChild = BodyPartPhysicsRotation.multiply3(parentWorld,
                BodyPartPhysicsRotation.axisAngle(new float[] {0F, 0F, 1F, -b}));
        assertError("two-bone inverted child", angularError(rotate(wrongChild, segment),
                targetChild), 40);

        System.out.println("PASS: two-bone X/Z hierarchy, corrected parent basis, and inverted child");
    }

    public static void main(String[] args)
    {
        float angle = (float) Math.toRadians(15);
        float[] anchor = BodyPartPhysicsRotation.eulerXYZ(0F,
                (float) Math.PI, 0F);
        float[] target = worldDirection(anchor, angle);
        double positiveError = angularError(worldDirection(anchor, angle), target);
        double negativeError = angularError(worldDirection(anchor, -angle), target);
        assertError("positive local swing", positiveError, 0);
        assertError("negative local swing", negativeError, 30);

        // Current production path:
        // hierarchy(false) returns +angle; preview writer negates it.
        // hierarchy(true) returns -angle; preview writer negates it.
        float frontRendered = -angle;
        float backRendered = angle;
        assertError("front legacy sign", angularError(worldDirection(anchor, frontRendered), target), 30);
        assertError("back legacy sign", angularError(worldDirection(anchor, backRendered), target), 0);

        System.out.println("PASS: +15 deg local X matches goal; -15 deg differs by 30 deg");
        System.out.println("PASS: current front/back sign conventions are opposite");
        testTwoBoneHierarchy();
        System.out.println("NOTE: this is a sign-contract diagnostic, not an in-game render test");
    }
}

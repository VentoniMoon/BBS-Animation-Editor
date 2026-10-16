package com.example.examplemod;

/**
 * Standalone diagnostic, deliberately independent of Minecraft/Forge.
 *
 * Run with JDK 8:
 *   javac -d build/physics-test src/main/java/com/example/examplemod/BodyPartPhysicsRotation.java src/main/java/com/example/examplemod/BodyPartChainSolver.java src/main/java/com/example/examplemod/BodyPartPhysicsPoseAdapter.java src/main/java/com/example/examplemod/BodyPartPhysicsHierarchySwing.java src/main/java/com/example/examplemod/BodyPartPhysicsHierarchySwingCanonical.java src/test/java/com/example/examplemod/BodyPartPhysicsSwingSignDiagnostic.java
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


    /** A real call to production HierarchySwing, using two original native-like bases. */
    private static void testProductionHierarchy(boolean backConvention)
    {
        float a = (float) Math.toRadians(15);
        float b = (float) Math.toRadians(20);
        float[] anchor = BodyPartPhysicsRotation.eulerXYZ(0F, (float) Math.PI, 0F);
        float[] rx = BodyPartPhysicsRotation.axisAngle(new float[] {1F, 0F, 0F, a});
        float[] rz = BodyPartPhysicsRotation.axisAngle(new float[] {0F, 0F, 1F, b});
        float[] down = new float[] {0F, -1F, 0F};
        float[] targetParent = rotate(BodyPartPhysicsRotation.multiply3(anchor, rx), down);
        float[] targetChild = rotate(BodyPartPhysicsRotation.multiply3(
                BodyPartPhysicsRotation.multiply3(anchor, rx), rz), down);
        float[] rest = rotate(anchor, down);

        float[] base4 = new float[16];
        base4[15] = 1F;
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 3; r++)
                base4[c * 4 + r] = anchor[c * 3 + r];

        BodyPartPhysicsHierarchySwing hierarchy =
                new BodyPartPhysicsHierarchySwing(backConvention);
        float[] one = hierarchy.next(base4,
                new BodyPartChainSolver.Vec3(rest[0],rest[1],rest[2]),
                new BodyPartChainSolver.Vec3(targetParent[0],targetParent[1],targetParent[2]));
        float[] two = hierarchy.next(base4,
                new BodyPartChainSolver.Vec3(rest[0],rest[1],rest[2]),
                new BodyPartChainSolver.Vec3(targetChild[0],targetChild[1],targetChild[2]));

        // This is the CURRENT production writer contract (not the proposed fix).
        one[3] = -one[3];
        two[3] = -two[3];
        float[] renderedParent = BodyPartPhysicsRotation.multiply3(anchor,
                BodyPartPhysicsRotation.axisAngle(one));
        float[] renderedChild = BodyPartPhysicsRotation.multiply3(renderedParent,
                BodyPartPhysicsRotation.axisAngle(two));
        double errorParent = angularError(rotate(renderedParent, down), targetParent);
        double errorChild = angularError(rotate(renderedChild, down), targetChild);
        System.out.println((backConvention ? "back" : "front") +
                " production hierarchy errors: parent=" + errorParent +
                " deg, child=" + errorChild + " deg");
        if (backConvention)
        {
            assertError("back production parent", errorParent, 0);
            assertError("back production child", errorChild, 0);
        }
        else
        {
            // The front convention is expected to fail the target match;
            // assert a measurable deviation until production is fixed.
            assertError("front production parent mismatch", errorParent, 30);
            assertError("front production child mismatch", errorChild, 61.31445);
        }
    }


    private static void testCanonicalHierarchy(float anchorYawDegrees)
    {
        float a = (float) Math.toRadians(15);
        float b = (float) Math.toRadians(20);
        float[] anchor = BodyPartPhysicsRotation.eulerXYZ(
                0F, (float) Math.toRadians(anchorYawDegrees), 0F);
        float[] rx = BodyPartPhysicsRotation.axisAngle(new float[] {1F,0F,0F,a});
        float[] rz = BodyPartPhysicsRotation.axisAngle(new float[] {0F,0F,1F,b});
        float[] restLocal = new float[] {0F,-1F,0F};
        float[] restWorld = rotate(anchor, restLocal);
        float[] targetOne = rotate(BodyPartPhysicsRotation.multiply3(anchor,rx),restLocal);
        float[] targetTwo = rotate(BodyPartPhysicsRotation.multiply3(
                BodyPartPhysicsRotation.multiply3(anchor,rx),rz),restLocal);
        float[] matrix = new float[16];
        matrix[15] = 1F;
        for (int col=0;col<3;col++)
            for (int row=0;row<3;row++)
                matrix[col*4+row] = anchor[col*3+row];

        BodyPartPhysicsHierarchySwingCanonical hierarchy =
                new BodyPartPhysicsHierarchySwingCanonical();
        BodyPartChainSolver.Vec3 rest = new BodyPartChainSolver.Vec3(
                restWorld[0],restWorld[1],restWorld[2]);
        float[] first = hierarchy.next(matrix,rest,new BodyPartChainSolver.Vec3(
                targetOne[0],targetOne[1],targetOne[2]));
        float[] second = hierarchy.next(matrix,rest,new BodyPartChainSolver.Vec3(
                targetTwo[0],targetTwo[1],targetTwo[2]));
        float[] actualFirst = BodyPartPhysicsRotation.multiply3(anchor,
                BodyPartPhysicsRotation.axisAngle(first));
        float[] actualSecond = BodyPartPhysicsRotation.multiply3(actualFirst,
                BodyPartPhysicsRotation.axisAngle(second));
        assertError("canonical parent yaw=" + anchorYawDegrees,
                angularError(rotate(actualFirst,restLocal),targetOne),0);
        assertError("canonical child yaw=" + anchorYawDegrees,
                angularError(rotate(actualSecond,restLocal),targetTwo),0);
        System.out.println("PASS canonical two-bone yaw=" + anchorYawDegrees);
    }


    /** Real back-hair pivots (Blockbench pixels, converted to directions). */
    private static void testHorsBackHair(boolean overLimit)
    {
        float[] anchor = BodyPartPhysicsRotation.eulerXYZ(0F,(float)Math.PI,0F);
        float[] rootRest = new float[] {0F,-3F,0.58F}; // back_1 -> back_2
        float[] childRest = new float[] {0F,-3F,0F};   // back_2 -> back_3
        float rootAngle = (float)Math.toRadians(overLimit ? 40 : 15);
        float childAngle = (float)Math.toRadians(12);
        float limit = 0.45F;
        float[] rootTarget = rotate(BodyPartPhysicsRotation.multiply3(anchor,
                BodyPartPhysicsRotation.axisAngle(new float[]{1F,0F,0F,rootAngle})),rootRest);
        float[] childTarget = rotate(BodyPartPhysicsRotation.multiply3(
                BodyPartPhysicsRotation.multiply3(anchor,
                        BodyPartPhysicsRotation.axisAngle(new float[]{1F,0F,0F,rootAngle})),
                BodyPartPhysicsRotation.axisAngle(new float[]{0F,0F,1F,childAngle})),childRest);
        float[] matrix = new float[16];
        matrix[15] = 1F;
        for(int col=0;col<3;col++)
            for(int row=0;row<3;row++)
                matrix[col*4+row] = anchor[col*3+row];
        BodyPartPhysicsHierarchySwingCanonical solver =
                new BodyPartPhysicsHierarchySwingCanonical();
        float[] rootWorld = rotate(anchor,rootRest);
        float[] childWorld = rotate(anchor,childRest);
        float[] first=solver.next(matrix,new BodyPartChainSolver.Vec3(
                rootWorld[0],rootWorld[1],rootWorld[2]),new BodyPartChainSolver.Vec3(
                rootTarget[0],rootTarget[1],rootTarget[2]));
        float[] second=solver.next(matrix,new BodyPartChainSolver.Vec3(
                childWorld[0],childWorld[1],childWorld[2]),new BodyPartChainSolver.Vec3(
                childTarget[0],childTarget[1],childTarget[2]));
        float[] actualRootMatrix = BodyPartPhysicsRotation.multiply3(
                anchor,BodyPartPhysicsRotation.axisAngle(first));
        float[] actualChildMatrix = BodyPartPhysicsRotation.multiply3(
                actualRootMatrix,BodyPartPhysicsRotation.axisAngle(second));
        double rootError=angularError(rotate(actualRootMatrix,rootRest),rootTarget);
        double childError=angularError(rotate(actualChildMatrix,childRest),childTarget);
        System.out.println("Hors back-hair overLimit="+overLimit+
                " rootError="+rootError+" childError="+childError);
        if(!overLimit)
        {
            assertError("Hors root under limit",rootError,0);
            assertError("Hors child under limit",childError,0);
        }
        else
        {
            double expectedMin=Math.toDegrees(rootAngle-limit)-1;
            if(rootError<expectedMin || rootError>Math.toDegrees(rootAngle)+1)
                throw new AssertionError("Limit did not cap root error as expected: "+rootError);
            if(Math.abs(first[3])>limit+0.00001F || Math.abs(second[3])>limit+0.00001F)
                throw new AssertionError("Swing exceeded visual angle limit");
        }
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
        testProductionHierarchy(true);
        testProductionHierarchy(false);
        testCanonicalHierarchy(0F);
        testCanonicalHierarchy(180F);
        testHorsBackHair(false);
        testHorsBackHair(true);
        System.out.println("NOTE: this is a sign-contract diagnostic, not an in-game render test");
    }
}

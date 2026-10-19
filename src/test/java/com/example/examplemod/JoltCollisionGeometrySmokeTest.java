package com.example.examplemod;

import java.util.List;

/**
 * Dependency-free Java 8 regression checks for Jolt collision descriptors.
 *
 * Run without launching Minecraft:
 *   javac -d build/collision-test \
 *     src/main/java/com/example/examplemod/JoltColliderPrimitives.java \
 *     src/main/java/com/example/examplemod/EmoticonsSkinnedBodyHull.java \
 *     src/test/java/com/example/examplemod/JoltCollisionGeometrySmokeTest.java
 *   java -cp build/collision-test com.example.examplemod.JoltCollisionGeometrySmokeTest
 */
public final class JoltCollisionGeometrySmokeTest
{
    private static void near(float actual,float expected,String label)
    {
        if(!Float.isFinite(actual) || Math.abs(actual-expected)>0.0002F)
            throw new AssertionError(label+": "+actual+" expected "+expected);
    }
    private static void check(boolean ok,String label)
    {
        if(!ok)throw new AssertionError(label);
    }
    private static float[] identity()
    {
        float[] m=new float[16];
        m[0]=m[5]=m[10]=m[15]=1F;
        return m;
    }
    private static int[] cubeTriangles()
    {
        return new int[]{0,1,2,1,3,2,4,6,5,5,6,7,
                0,4,1,1,4,5,2,3,6,3,7,6,
                0,2,4,2,6,4,1,5,3,3,5,7};
    }
    private static float[] cubeVertices()
    {
        float[] points=new float[24];
        int at=0;
        for(int i=0;i<8;i++)
        {
            points[at++]=(i&1)==0?-0.12F:0.12F;
            points[at++]=(i&2)==0?-0.35F:0.35F;
            points[at++]=(i&4)==0?-0.09F:0.09F;
        }
        return points;
    }
    public static void main(String[] args)
    {
        float[] obb={1F,2F,3F,0F,0F,0F,1F,0.12F,0.40F,0.10F};
        float[] original=obb.clone();
        float[] scaled=JoltColliderPrimitives.encode(
                obb,JoltColliderPrimitives.BOX,0.93F);
        near(scaled[7],0.1116F,"box clearance X");
        near(scaled[8],0.372F,"box clearance Y");
        near(scaled[9],0.093F,"box clearance Z");
        check(obb[7]==original[7],"source box remains immutable");
        float[] capsule=JoltColliderPrimitives.encode(
                obb,JoltColliderPrimitives.CAPSULE,0.93F);
        check(capsule[10]==JoltColliderPrimitives.CAPSULE,"capsule mode");
        near(capsule[7],0.093F,"capsule radial clearance");
        near(capsule[8],0.372F,"capsule axis");
        float[][] poles=JoltColliderPrimitives.capsulePoles(capsule);
        near(poles[1][1]-poles[0][1],0.744F,"capsule poles Y");
        float[] xLong={0F,0F,0F,0F,0F,0F,1F,0.40F,0.10F,0.12F};
        float[] xCapsule=JoltColliderPrimitives.encode(
                xLong,JoltColliderPrimitives.CAPSULE,0.93F);
        float[][] xPoles=JoltColliderPrimitives.capsulePoles(xCapsule);
        near(Math.abs(xPoles[1][0]-xPoles[0][0]),0.744F,
                "X-aligned capsule poles");
        check(JoltColliderPrimitives.sameTopology(
                new float[][]{capsule},new float[][]{capsule.clone()}),
                "same primitive topology");

        List<float[]> local=EmoticonsSkinnedBodyHull.boneLocalBoxes(
                cubeVertices(),cubeTriangles(),identity(),3,0.018F);
        check(local.size()==1,"eight-vertex cube has one closed collider");
        float[] m=identity();
        m[12]=10F;m[13]=20F;m[14]=30F;
        float[] world=EmoticonsSkinnedBodyHull.transformRigid(m,local.get(0));
        near(world[0],10F,"world collider center X");
        near(world[1],20F,"world collider center Y");
        near(world[2],30F,"world collider center Z");
        System.out.println("Jolt collision geometry smoke tests passed");
    }
}

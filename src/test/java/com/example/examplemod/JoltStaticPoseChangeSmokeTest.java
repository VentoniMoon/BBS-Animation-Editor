package com.example.examplemod;

/**
 * Dependency-free regression checks. No Forge or JNI runtime required.
 *
 * javac -d build/collision-test
 *   src/main/java/com/example/examplemod/JoltPhysicsDiscontinuity.java
 *   src/main/java/com/example/examplemod/JoltStaticPoseChange.java
 *   src/test/java/com/example/examplemod/JoltStaticPoseChangeSmokeTest.java
 * java -cp build/collision-test
 *   com.example.examplemod.JoltStaticPoseChangeSmokeTest
 */
public final class JoltStaticPoseChangeSmokeTest
{
    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }
    private static float[] identity()
    {
        float[] m=new float[16];
        m[0]=m[5]=m[10]=m[15]=1F;
        return m;
    }
    private static float[] box()
    {
        return new float[]{1F,2F,3F,0F,0F,0F,1F,
                .2F,.3F,.2F};
    }
    public static void main(String[] args)
    {
        float[] parent=identity();
        float[] original=box();
        check(!JoltStaticPoseChange.significant(parent,parent,
                new float[][]{original},new float[][]{original.clone()}),
                "unchanged paused pose");
        float[] jitter=original.clone();
        jitter[0]+=0.02F;
        check(!JoltStaticPoseChange.significant(parent,parent,
                new float[][]{original},new float[][]{jitter}),
                "sub-pixel pose jitter must not reseed");
        float[] moved=original.clone();
        moved[1]+=0.30F;
        check(JoltStaticPoseChange.significant(parent,parent,
                new float[][]{original},new float[][]{moved}),
                "large head center key edit must reseed");
        float[] rotated=original.clone();
        rotated[5]=(float)Math.sin(Math.toRadians(45D));
        rotated[6]=(float)Math.cos(Math.toRadians(45D));
        check(JoltStaticPoseChange.significant(parent,parent,
                new float[][]{original},new float[][]{rotated}),
                "large head rotation key edit must reseed");
        float[] negativeQuaternion=original.clone();
        negativeQuaternion[6]=-1F;
        check(!JoltStaticPoseChange.significant(parent,parent,
                new float[][]{original},new float[][]{negativeQuaternion}),
                "quaternion sign equivalence");
        float[] tipped=identity();
        tipped[0]=0F;tipped[1]=1F;
        tipped[4]=-1F;tipped[5]=0F;
        check(JoltStaticPoseChange.significant(parent,tipped,
                new float[][]{original},new float[][]{original.clone()}),
                "tilted parent bone must reseed");
        System.out.println("Jolt static pose edit smoke tests passed");
    }
}

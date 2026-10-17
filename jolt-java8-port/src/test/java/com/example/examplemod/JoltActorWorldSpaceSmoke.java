package com.example.examplemod;

/**
 * Pure Java 8: same actor-world transform for Jolt bones, strand pivots,
 * torso proxies, and rotated Chameleon collision boxes.
 */
public final class JoltActorWorldSpaceSmoke
{
    private JoltActorWorldSpaceSmoke() {}

    private static void near(float got,float expected,String label)
    {
        if(Math.abs(got-expected)>0.00025F)
            throw new AssertionError(label+" got "+got+", expected "+expected);
    }

    private static void nearVec(float[] actual,float[] expected,String label)
    {
        for(int i=0;i<3;i++)near(actual[i],expected[i],label+"["+i+"]");
    }

    public static void main(String[] args)
    {
        // Emoticons rotates the model by (360-renderYawOffset).
        float[] rotated=BodyPartJoltActorSpace.worldFromActor(10,64,-5,90);
        nearVec(BodyPartJoltActorSpace.point(rotated,new float[]{1,2,3}),
                new float[]{7,66,-4},"yaw -90/translation");
        nearVec(BodyPartJoltActorSpace.point(rotated,new float[]{-1,2,3}),
                new float[]{7,66,-6},"mirrored strand roots");

        float[] pivot=BodyPartPhysicsMatrixSpace.identity();
        pivot[12]=1F;pivot[13]=2F;pivot[14]=3F;
        float[] worldBone=BodyPartJoltActorSpace.matrix(rotated,pivot);
        nearVec(new float[]{worldBone[12],worldBone[13],worldBone[14]},
                BodyPartJoltActorSpace.point(rotated,new float[]{1,2,3}),
                "pose agrees with attachment");

        float[] localBox={0F,1F,2F, 0F,0F,0F,1F, 0.2F,0.3F,0.4F};
        float[] worldBox=BodyPartJoltActorSpace.box(rotated,localBox);
        nearVec(worldBox,new float[]{8F,65F,-5F},"collision cube center");
        float[] localHalfX={0.2F,0F,0F};
        // q=(0,-sin 45,0,cos45): its local X rotates to world +Z.
        float qy=worldBox[4],qw=worldBox[6];
        float projectedX=(1F-2F*qy*qy)*localHalfX[0];
        float projectedZ=-2F*qy*qw*localHalfX[0];
        near(projectedX,0F,"box local X world X");
        near(projectedZ,0.2F,"box local X world Z");
        near(worldBox[7],0.2F,"box geometry preserved X");
        near(worldBox[8],0.3F,"box geometry preserved Y");
        near(worldBox[9],0.4F,"box geometry preserved Z");

        // Movement of the entire character is now visible to Jolt even
        // if the Chameleon parent and all three bone-local poses are static.
        float[] later=BodyPartJoltActorSpace.worldFromActor(10.45,64,-5.2,90);
        float[] before=BodyPartJoltActorSpace.point(rotated,
                new float[]{0.2F,1.4F,-0.1F});
        float[] after=BodyPartJoltActorSpace.point(later,
                new float[]{0.2F,1.4F,-0.1F});
        near(after[0]-before[0],0.45F,"actor translation is physical x");
        near(after[2]-before[2],-0.2F,"actor translation is physical z");

        float[] laterYaw=BodyPartJoltActorSpace.worldFromActor(10,64,-5,0);
        float[] rotatedTip=BodyPartJoltActorSpace.point(rotated,
                new float[]{0.2F,1.4F,-0.1F});
        float[] staticTip=BodyPartJoltActorSpace.point(laterYaw,
                new float[]{0.2F,1.4F,-0.1F});
        if(Math.abs(staticTip[0]-rotatedTip[0])<0.05F)
            throw new AssertionError("Physical Jolt root ignores actor yaw");

        System.out.println("Jolt actor-world bones, boxes, translated and rotating pin: PASS");
    }
}

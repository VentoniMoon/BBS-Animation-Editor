package com.example.examplemod;

/** Math-only Java 8 regression checks, independent of Minecraft/Forge. */
public final class EditorWorldBoneRetargetMathSmokeTest
{
    private static void close(float actual,float expected,float tolerance)
    {
        if(!Float.isFinite(actual)||Math.abs(actual-expected)>tolerance)
            throw new AssertionError(actual+" != "+expected);
    }

    private static void sameDirection(float[] actual,float[] expected)
    {
        float aa=(float)Math.sqrt(actual[0]*actual[0]+
                actual[1]*actual[1]+actual[2]*actual[2]);
        float bb=(float)Math.sqrt(expected[0]*expected[0]+
                expected[1]*expected[1]+expected[2]*expected[2]);
        close(aa,bb,.002F);
        for(int i=0;i<3;i++)close(actual[i],expected[i],.002F);
    }

    private static float[] vec(float x,float y,float z)
    {
        return new float[]{x,y,z};
    }

    private static void simpleWorldRotation()
    {
        float[] e=EditorWorldBoneRetargetMath.alignLocalEuler(
                vec(0,0,0),vec(0,0,0),
                vec(1,0,0),vec(0,1,0));
        sameDirection(EditorWorldBoneRetargetMath.rotate(e,vec(1,0,0)),
                vec(0,1,0));
    }

    private static void rotatedAncestor()
    {
        float[] parent=vec(0,0,90);
        float[] e=EditorWorldBoneRetargetMath.alignLocalEuler(
                parent,vec(0,0,0),
                vec(0,2,0),vec(-2,0,0));
        // World parent rotated the original child X into world +Y.
        float[] localDir=EditorWorldBoneRetargetMath.rotate(
                e,vec(2,0,0));
        sameDirection(EditorWorldBoneRetargetMath.rotate(
                parent,localDir),vec(-2,0,0));
    }

    private static void antiparallel()
    {
        float[] e=EditorWorldBoneRetargetMath.alignLocalEuler(
                vec(0,0,0),vec(0,0,0),
                vec(0,0,3),vec(0,0,-3));
        sameDirection(EditorWorldBoneRetargetMath.rotate(e,
                vec(0,0,3)),vec(0,0,-3));
    }

    private static void noStretchAndAngleWrap()
    {
        close(EditorWorldBoneRetargetMath.nearestAngle(-179F,179F),
                181F,.001F);
        float[] original=vec(.2F,.7F,.1F);
        float length=(float)Math.sqrt(.2F*.2F+.7F*.7F+.1F*.1F);
        float[] rotated=EditorWorldBoneRetargetMath.rotate(
                vec(17,37,-21),original);
        float actual=(float)Math.sqrt(rotated[0]*rotated[0]+
                rotated[1]*rotated[1]+rotated[2]*rotated[2]);
        close(actual,length,.0001F);
    }

    private static void rejectZeroSegment()
    {
        boolean rejected=false;
        try
        {
            EditorWorldBoneRetargetMath.alignLocalEuler(
                    vec(0,0,0),vec(0,0,0),
                    vec(0,0,0),vec(1,0,0));
        }
        catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Zero bone segment accepted");
    }

    public static void main(String[] args)
    {
        simpleWorldRotation();
        rotatedAncestor();
        antiparallel();
        noStretchAndAngleWrap();
        rejectZeroSegment();
        System.out.println("EditorWorldBoneRetargetMath smoke passed");
    }
}

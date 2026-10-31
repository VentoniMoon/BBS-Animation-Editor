package com.example.examplemod;

public final class CameraMirrorLightSpaceSmokeTest
{
    private static void near(float a,float b,String what)
    {
        if(Math.abs(a-b)>0.002F)
            throw new AssertionError(what+": "+a+" != "+b);
    }

    public static void main(String[] args)
    {
        double[] light={648.25,3.75,-961.5};
        double[] main={648.63,2.93,-958.29};
        double[] mirror={648.63,2.93,-975.70};
        float[] mainMatrix={
                0F,0F,1F,0F, 0F,1F,0F,0F,
                -1F,0F,0F,0F, 0F,0F,0F,1F
        };
        float[] mirrorMatrix={
                0F,0F,-1F,0F, 0F,1F,0F,0F,
                1F,0F,0F,0F, 0F,0F,0F,1F
        };

        float[] oldRel=CameraMirrorLightSpace.relative(light,main);
        if(CameraMirrorLightSpace.match(oldRel,light,main,mainMatrix)!=
                CameraMirrorLightSpace.RELATIVE)
            throw new AssertionError("Main relative camera was not matched");
        float[] newRel=CameraMirrorLightSpace.reflected(
                CameraMirrorLightSpace.RELATIVE,light,mirror,mirrorMatrix);
        near(newRel[2],14.2F,"Reflected relative light stays world-anchored");

        float[] oldEye=CameraMirrorLightSpace.transform(mainMatrix,
                oldRel[0],oldRel[1],oldRel[2]);
        if(CameraMirrorLightSpace.match(oldEye,light,main,mainMatrix)!=
                CameraMirrorLightSpace.EYE)
            throw new AssertionError("Main view-space light was not matched");
        float[] newEye=CameraMirrorLightSpace.reflected(
                CameraMirrorLightSpace.EYE,light,mirror,mirrorMatrix);
        float[] expected=CameraMirrorLightSpace.transform(mirrorMatrix,
                newRel[0],newRel[1],newRel[2]);
        for(int i=0;i<3;i++)near(newEye[i],expected[i],
                "Reflected view-space caster axis "+i);

        float[] absolute={(float)light[0],(float)light[1],
                (float)light[2]};
        if(CameraMirrorLightSpace.match(absolute,light,main,mainMatrix)!=
                CameraMirrorLightSpace.WORLD)
            throw new AssertionError("World-space source misidentified");
        float[] retained=CameraMirrorLightSpace.reflected(
                CameraMirrorLightSpace.WORLD,light,mirror,mirrorMatrix);
        for(int i=0;i<3;i++)near(retained[i],absolute[i],
                "World coordinates cannot be shifted");
        if(CameraMirrorLightSpace.match(
                new float[]{10000F,0F,-200F},light,main,mainMatrix)!=
                CameraMirrorLightSpace.UNKNOWN)
            throw new AssertionError("Unrelated uniform was accepted");
        System.out.println("PASS: BetterLights uniforms matched and rebased "+
                "only for genuine registered light positions");
    }
}

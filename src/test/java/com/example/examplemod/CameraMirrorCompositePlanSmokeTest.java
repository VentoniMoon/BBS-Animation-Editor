package com.example.examplemod;

import java.util.Arrays;

public final class CameraMirrorCompositePlanSmokeTest
{
    private static void equal(int[] actual,int... expected)
    {
        if(!Arrays.equals(actual,expected))
            throw new AssertionError(
                    Arrays.toString(actual)+" != "+Arrays.toString(expected));
    }
    private static void check(boolean okay)
    {
        if(!okay)throw new AssertionError("Composite plan incorrect");
    }
    public static void main(String[] args)
    {
        CameraMirrorCompositePlan a=CameraMirrorCompositePlan.create(
                "deferred0",new int[]{36064,36066,36071},null,8);
        equal(a.targets,0,2,7);
        check(a.flip[0]&&a.flip[1]&&a.flip[2]);

        Boolean[] flips=new Boolean[8];
        flips[2]=Boolean.FALSE;
        CameraMirrorCompositePlan b=CameraMirrorCompositePlan.create(
                "composite2",new int[]{36066,0,36068},flips,8);
        equal(b.targets,2,-1,4);
        check(!b.flip[0]&&!b.flip[1]&&b.flip[2]);

        CameraMirrorCompositePlan all=CameraMirrorCompositePlan.create(
                "composite0",null,null,8);
        equal(all.targets,0,1,2,3,4,5,6,7);
        expectFailure(new Runnable()
        {
            public void run()
            {
                CameraMirrorCompositePlan.create(
                        "bad",new int[]{36064,36064},null,8);
            }
        });
        expectFailure(new Runnable()
        {
            public void run()
            {
                CameraMirrorCompositePlan.create(
                        "bad",new int[]{36072},null,8);
            }
        });
        expectFailure(new Runnable()
        {
            public void run()
            {
                CameraMirrorCompositePlan.create(
                        "no output",new int[]{0,0},null,8);
            }
        });
        System.out.println("Mirror BSL composite draw-buffer plan OK");
    }
    private static void expectFailure(Runnable r)
    {
        try{r.run();}
        catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Invalid DRAWBUFFERS was accepted");
    }
}

package com.example.examplemod;

import java.util.Arrays;

/**
 * Deterministic, pure Java 8 validation of local 3-panel contact selection.
 * The solver itself must still check real hem vertices for penetration.
 */
public final class SkirtAdaptiveHemLiftSmokeTest
{
    private SkirtAdaptiveHemLiftSmokeTest(){}

    public static void main(String[] args)
    {
        localCornerAndTwoAdjacentPanels();
        twoIndependentLegsCannotOpenWholeRing();
        shuffledBoneOrderGivesIdenticalWorldPanels();
        noActualSelectedCollisionCannotLift();
        boundsAndMalformedDataFailClosed();
        System.out.println("SkirtAdaptiveHemLiftSmokeTest OK");
    }

    private static void localCornerAndTwoAdjacentPanels()
    {
        Fixture f=new Fixture();
        f.selected[4]=true;
        f.selected[3]=true;
        f.selected[5]=true;
        f.selected[6]=true;
        for(int i:new int[]{3,4,5,6})
        {
            f.demand[SkirtHemContactIslands.LEFT][i]=.17F;
            f.depth[SkirtHemContactIslands.LEFT][i]=.11F;
        }
        f.demand[SkirtHemContactIslands.LEFT][4]=.32F;
        float[] w=f.weights();
        near(w[4],1F,"center must have full permission");
        near(w[3],SkirtAdaptiveHemLift.SECONDARY_WEIGHT,
                "one angular neighbour");
        near(w[5],SkirtAdaptiveHemLift.SECONDARY_WEIGHT,
                "other angular neighbour");
        near(w[6],0F,"two-away panel must not lift");
        need(count(w)==3,"exactly three local lower bones");
    }

    private static void twoIndependentLegsCannotOpenWholeRing()
    {
        Fixture f=new Fixture();
        for(int i=0;i<20;i++)
        {
            f.selected[i]=true;
            int g=i<10?SkirtHemContactIslands.LEFT:
                    SkirtHemContactIslands.RIGHT;
            f.demand[g][i]=.18F;
            f.depth[g][i]=.15F;
        }
        f.demand[SkirtHemContactIslands.LEFT][4]=.33F;
        f.demand[SkirtHemContactIslands.RIGHT][14]=.33F;
        float[] w=f.weights();
        need(count(w)==6,"two legs must influence at most six panels");
        near(w[4],1F,"left leg strongest");
        near(w[14],1F,"right leg strongest");
        near(w[0],0F,"other skirt panels remain natural");
    }

    private static void shuffledBoneOrderGivesIdenticalWorldPanels()
    {
        Fixture f=new Fixture();
        for(int i=0;i<20;i++)
        {
            f.selected[i]=true;
            int g=i<10?SkirtHemContactIslands.LEFT:
                    SkirtHemContactIslands.RIGHT;
            f.demand[g][i]=.20F;
            f.depth[g][i]=.10F;
        }
        // Test equal-pressure tie handling by stable name, not list order.
        float[] original=f.weights();
        Fixture shuffled=new Fixture();
        for(int i=0;i<20;i++)
        {
            int old=(i*7)%20;
            shuffled.names[i]=f.names[old];
            shuffled.root[i]=f.root[old].clone();
            shuffled.selected[i]=f.selected[old];
            for(int g=0;g<3;g++)
            {
                shuffled.demand[g][i]=f.demand[g][old];
                shuffled.depth[g][i]=f.depth[g][old];
            }
        }
        float[] other=shuffled.weights();
        for(int i=0;i<20;i++)
        {
            boolean found=false;
            for(int j=0;j<20;j++)
                if(f.names[i].equals(shuffled.names[j]))
                {
                    near(original[i],other[j],"bone order independence");
                    found=true;
                    break;
                }
            need(found,"missing ring bone after permutation");
        }
        // Same inputs / same result; there is no hidden animation memory.
        need(Arrays.equals(original,f.weights()),
                "rerender must be deterministic");
    }

    private static void noActualSelectedCollisionCannotLift()
    {
        Fixture f=new Fixture();
        f.selected[4]=true;
        f.demand[SkirtHemContactIslands.LEFT][4]=.31F;
        // No direct thigh depth means no contact.
        need(count(f.weights())==0,
                "no real intersection must never lift the skirt");
        f.depth[SkirtHemContactIslands.LEFT][4]=.1F;
        f.selected[4]=false;
        need(count(f.weights())==0,
                "existing safety island rejection must be retained");
    }

    private static void boundsAndMalformedDataFailClosed()
    {
        near(SkirtAdaptiveHemLift.collisionCap(.675F,1F),
                .955F,"center dynamic cap");
        near(SkirtAdaptiveHemLift.collisionCap(.675F,.38F),
                .7814F,"neighbor dynamic cap");
        near(SkirtAdaptiveHemLift.extraBudget(0F),0F,
                "no contact budget");
        need(SkirtAdaptiveHemLift.collisionCap(.8F,7F)<=1.02F,
                "hard maximum must not yield umbrella flare");
        Fixture f=new Fixture();
        f.selected[4]=true;
        f.depth[0][4]=Float.NaN;
        need(count(f.weights())==0,"invalid collision must fail closed");
    }

    private static final class Fixture
    {
        final String[] names=new String[20];
        final float[][] root=new float[20][3];
        final boolean[] selected=new boolean[20];
        final float[][] demand=new float[3][20];
        final float[][] depth=new float[3][20];
        Fixture()
        {
            for(int i=0;i<20;i++)
            {
                names[i]="group"+i;
                double a=2D*Math.PI*i/20;
                root[i][0]=.35F*(float)Math.cos(a);
                root[i][2]=.35F*(float)Math.sin(a);
            }
        }
        float[] weights()
        {
            return SkirtAdaptiveHemLift.weights(
                    names,root,selected,demand,depth);
        }
    }

    private static int count(float[] weights)
    {
        int n=0;
        for(float w:weights)if(w>.001F)n++;
        return n;
    }
    private static void near(float actual,float expected,String message)
    {
        need(Math.abs(actual-expected)<.0002F,
                message+" expected="+expected+" actual="+actual);
    }
    private static void need(boolean ok,String message)
    {if(!ok)throw new AssertionError(message);}
}

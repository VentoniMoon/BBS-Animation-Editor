package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone Java 8 regression of the connected two-link skirt grid. */
public final class SkirtConnectedClothSolverSmokeTest
{
    private static final int N=20;
    private static final String[][] BONES={
        {"group2","group22"},{"group6","group24"},
        {"group3","group25"},{"group4","group23"},
        {"group5","group26"},{"group18","group29"},
        {"group17","group27"},{"group7","group38"},
        {"group16","group39"},{"group8","group40"},
        {"group9","group37"},{"group10","group41"},
        {"group20","group35"},{"group19","group36"},
        {"group14","group31"},{"group15","group32"},
        {"group21","group30"},{"group13","group34"},
        {"group12","group28"},{"group11","group33"}
    };

    public static void main(String[] args)
    {
        neutralSkirtStaysNeutral();
        raisedLegMovesOnlyNearbyFabric();
        unrelatedBodyGeometryDoesNotMakeFakeContact();
        brokenGiantHullIsIgnoredInsteadOfInflatingRing();
        extremeIndependentStripGetsNeighborConstraints();
        mirroredRearBoneBasisRemainsValid();
        System.out.println("SkirtConnectedClothSolverSmokeTest OK");
    }

    private static void neutralSkirtStaysNeutral()
    {
        Fixture f=new Fixture(false);
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(f.strips,f.raw,null);
        check(r.swings.size()==40,"missing two-link source bones");
        check(r.adjustedPanels==0,"neutral cloth solver displaced panels");
        for(Map.Entry<String,float[]> e:r.swings.entrySet())
            near(e.getValue()[3],0F,.0001F,"neutral bone "+e.getKey());
        check(r.largestSeamStretch<.0002F,"rest ring gap changed");
    }

    private static void raisedLegMovesOnlyNearbyFabric()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.47F,-.31F,0F,.11F,.16F,.105F),
                primitive(.47F,-.31F,0F),"emoticons.left_leg");
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(
                        f.strips,f.raw,budget.select(true));
        check(r.colliderCount==1,"real thigh OBB not used");
        check(r.maxContactBefore>.005F,"thigh overlap not found");
        check(r.adjustedPanels>0,"front skirt did not react");
        check(r.adjustedPanels<N,"contact inflated entire ring");
        check(r.largestSeamStretch<.16F,"connected hem seam diverged");
        check(r.swings.get(BONES[0][0])[3]>.01F||
                r.swings.get(BONES[0][1])[3]>.01F,
                "front panel must move to accommodate raised leg");
        check(r.swings.get(BONES[10][0])[3]<.12F,
                "opposite rear should not receive raised thigh force");
        for(float[] swing:f.raw.values())
            near(swing[3],0F,.00001F,"native Jolt input must stay immutable");
    }

    private static void unrelatedBodyGeometryDoesNotMakeFakeContact()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.47F,-.31F,0F,.11F,.16F,.105F),
                primitive(.47F,-.31F,0F),"chameleon.some_attachment");
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(
                        f.strips,f.raw,budget.select(true));
        check(r.colliderCount==0 && r.adjustedPanels==0,
                "unrelated collision geometry changed skirt pose");
    }

    private static void brokenGiantHullIsIgnoredInsteadOfInflatingRing()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(0F,-.27F,0F,.47F,.35F,.47F),
                primitive(0F,-.27F,0F),"emoticons.left_leg");
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(
                        f.strips,f.raw,budget.select(true));
        check(r.colliderCount==0 && r.ignoredColliders>0,
                "giant false thigh hull must be rejected");
        check(r.adjustedPanels==0,
                "inaccurate full-ring collider caused umbrella flare");
    }

    private static void extremeIndependentStripGetsNeighborConstraints()
    {
        Fixture f=new Fixture(false);
        f.raw.put(BONES[0][0],new float[]{0F,0F,1F,.75F});
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(f.strips,f.raw,null);
        check(r.adjustedPanels>0,"detached independent Jolt strip unchanged");
        check(r.largestSeamStretch<.25F,
                "cross-strip grid did not preserve the ring");
    }

    private static void mirroredRearBoneBasisRemainsValid()
    {
        Fixture f=new Fixture(true);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(-.47F,-.31F,0F,.11F,.16F,.105F),
                primitive(-.47F,-.31F,0F),"emoticons.right_leg");
        SkirtConnectedClothSolver.Result r=
                SkirtConnectedClothSolver.solve(
                        f.strips,f.raw,budget.select(true));
        check(r.adjustedPanels>0,"mirrored rear leg has no contact");
        for(Map.Entry<String,float[]> e:r.swings.entrySet())
        {
            float[] aa=e.getValue();
            check(aa.length==4,"bad axis-angle output");
            for(float v:aa)check(Float.isFinite(v),"NaN after rear Y=180");
            check(aa[3]<=2F,"rear swing exploded after matrix inversion");
        }
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=new ArrayList<
                SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> raw=new LinkedHashMap<String,float[]>();

        Fixture(boolean rearMirror)
        {
            for(int i=0;i<N;i++)
            {
                float a=(float)(2*Math.PI*i/N);
                float x=(float)Math.cos(a)*.44F,
                        z=(float)Math.sin(a)*.44F;
                boolean mirror=rearMirror && i>=7&&i<=13;
                String upper=BONES[i][0],lower=BONES[i][1];
                strips.add(new SkirtClothPoseCoupler.Strip(upper,lower,
                        matrix(x,0F,z,mirror),
                        matrix(x,-.18F,z,mirror),
                        new float[]{x,-.40F,z}));
                raw.put(upper,new float[]{1F,0F,0F,0F});
                raw.put(lower,new float[]{1F,0F,0F,0F});
            }
        }
    }

    private static float[] matrix(float x,float y,float z,boolean mirrored)
    {
        float[] m=new float[]{
                mirrored?-1F:1F,0,0,0,0,1,0,0,
                0,0,mirrored?-1F:1F,0,x,y,z,1};
        return m;
    }
    private static float[] box(float x,float y,float z,float hx,float hy,float hz)
    {return new float[]{x,y,z,0,0,0,1,hx,hy,hz};}
    private static float[] primitive(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.1F,.16F,.1F,2F};}
    private static void check(boolean yes,String error)
    {if(!yes)throw new AssertionError(error);}
    private static void near(float actual,float expected,float eps,String error)
    {check(Math.abs(actual-expected)<=eps,error+": "+actual);}
}

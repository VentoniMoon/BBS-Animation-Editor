package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone Java 8 regression of the opt-in connected skirt redesign. */
public final class SkirtClothV2SolverSmokeTest
{
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
    private SkirtClothV2SolverSmokeTest(){}

    public static void main(String[] args)
    {
        noColliderPreservesSoftJoltMotionExactly();
        thighClearancePreservesSeamsAndBelt();
        adjustablePenetrationActuallyChangesCollisionThreshold();
        giantInvalidHullAndFarBodyDoNotCauseUmbrella();
        mirroredBoneBasisAndSameFrameDeterminism();
        System.out.println("SkirtClothV2SolverSmokeTest OK");
    }

    private static void noColliderPreservesSoftJoltMotionExactly()
    {
        Fixture f=new Fixture(false);
        f.pose.put(BONES[0][0],new float[]{0,0,1,.21F});
        f.pose.put(BONES[5][1],new float[]{1,0,0,.24F});
        SkirtClothV2Solver.Result result=run(f,null,.14F);
        check(result.movedPanels==0 && !result.accepted,
                "free-flowing Jolt pose must not become a rigid tube");
        for(String name:f.pose.keySet())
            for(int k=0;k<4;k++)
                check(Float.floatToIntBits(f.pose.get(name)[k])==
                        Float.floatToIntBits(result.swings.get(name)[k]),
                        "neutral cloth changed Jolt pose "+name);
    }

    private static void thighClearancePreservesSeamsAndBelt()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget b=budget(.50F,-.30F,0F,.13F,.16F,.13F);
        SkirtClothV2Solver.Result result=run(f,b,.10F);
        System.out.println("V2 thigh fixture boxes="+result.validLegBoxes+
                " ignored="+result.ignoredLegBoxes+
                " active="+result.activePanels+
                " before="+result.maxPenetrationBefore+
                " after="+result.maxPenetrationAfter+
                " moved="+result.movedPanels);
        check(result.validLegBoxes==1 && result.activePanels>0,
                "real upper thigh contact not recognized");
        check(result.maxPenetrationBefore>.003F,
                "test must create leg collision");
        check(result.maxPenetrationAfter<=
                        result.maxPenetrationBefore+.0021F,
                "leg penetration must never increase");
        check(result.seamErrorAfter<=
                        Math.max(result.seamErrorBefore+.0751F,.1201F),
                "neighbor seams must be bounded before mapping");
        check(result.movedPanels<20,
                "nearby thigh must not spread a rigid umbrella to rear");
        for(String[] p:BONES)
        {
            check(result.swings.containsKey(p[0]) &&
                    result.swings.containsKey(p[1]),"incomplete rig");
            float[] original=f.pose.get(p[0]);
            check(original[3]==0F,"pinned waist Jolt input modified");
            SkirtAdaptiveWidth.Width top=result.widths.get(p[0]);
            SkirtAdaptiveWidth.Width hem=result.widths.get(p[1]);
            if(top!=null)check(top.multiplier<=1.0701F,
                    "upper width expanded beyond weld safety");
            if(hem!=null)check(hem.multiplier<=1.2801F,
                    "hem width expanded beyond weld safety");
        }
    }

    private static void adjustablePenetrationActuallyChangesCollisionThreshold()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget b=budget(.52F,-.30F,0F,.11F,.16F,.10F);
        SkirtClothV2Solver.Result strict=run(f,b,0F);
        SkirtClothV2Solver.Result permissive=run(f,b,.35F);
        check(strict.allowedPenetration==0F &&
                permissive.allowedPenetration==.35F,
                "ratio must be externally adjustable");
        check(strict.maxPenetrationBefore>
                        permissive.maxPenetrationBefore+.012F,
                "body ingress ratio should change accepted leg depth");
        check(strict.maxPenetrationAfter<=
                        strict.maxPenetrationBefore+.0021F,
                "strict setting must not worsen contact");
    }

    private static void giantInvalidHullAndFarBodyDoNotCauseUmbrella()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget giant=budget(0F,-.27F,0F,
                .47F,.34F,.47F);
        SkirtClothV2Solver.Result bad=run(f,giant,.14F);
        check(bad.movedPanels==0 && bad.ignoredLegBoxes>0,
                "broad 20-panel false contact must be rejected");
        SkirtClothV2Solver.Result far=run(f,
                budget(5F,-.30F,5F,.1F,.16F,.1F),.14F);
        check(far.movedPanels==0&&far.maxPenetrationBefore==0F,
                "far-leg noise must not influence cloth");
    }

    private static void mirroredBoneBasisAndSameFrameDeterminism()
    {
        Fixture f=new Fixture(true);
        BodyPartJoltContactBudget b=budget(-.50F,-.30F,0F,
                .13F,.16F,.13F);
        SkirtClothV2Solver.Result a=run(f,b,.10F);
        SkirtClothV2Solver.Result again=run(f,b,.10F);
        for(String id:f.pose.keySet())
        {
            float[] x=a.swings.get(id),y=again.swings.get(id);
            check(x!=null&&y!=null&&x.length==4,
                    "mirrored Chameleon swing disappeared");
            for(int k=0;k<4;k++)
            {
                check(Float.isFinite(x[k]),
                        "NaN/Inf after mirrored rear swing");
                close(x[k],y[k],.000001F,
                        "one frame different from replay "+id);
            }
        }
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=
                new ArrayList<SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> pose=
                new LinkedHashMap<String,float[]>();
        Fixture(boolean mirror)
        {
            for(int i=0;i<20;i++)
            {
                float a=(float)(2D*Math.PI*i/20D);
                float x=.44F*(float)Math.cos(a),
                        z=.44F*(float)Math.sin(a);
                boolean back=mirror&&i>=7&&i<=13;
                String top=BONES[i][0],lower=BONES[i][1];
                float[] p0=matrix(x,0F,z,back),
                        p1=matrix(x,-.18F,z,back);
                float[][] upper={{x+.01F,-.12F,z-.03F},
                        {x+.01F,-.16F,z+.03F}};
                float[][] hem={{x+.01F,-.30F,z-.04F},
                        {x+.01F,-.30F,z+.04F},
                        {x+.01F,-.39F,z-.04F},
                        {x+.01F,-.39F,z+.04F}};
                strips.add(new SkirtClothPoseCoupler.Strip(top,lower,
                        p0,p1,new float[]{x,-.41F,z},upper,hem));
                pose.put(top,new float[]{1F,0F,0F,0F});
                pose.put(lower,new float[]{1F,0F,0F,0F});
            }
        }
    }

    private static SkirtClothV2Solver.Result run(Fixture f,
            BodyPartJoltContactBudget b,float ratio)
    {
        return SkirtClothV2Solver.solve(f.strips,f.pose,
                b==null?null:b.select(true),
                new SkirtClothV2Solver.Parameters(ratio,.82F,.24F));
    }
    private static BodyPartJoltContactBudget budget(
            float x,float y,float z,float hx,float hy,float hz)
    {
        BodyPartJoltContactBudget b=new BodyPartJoltContactBudget();
        b.add(new float[]{x,y,z,0F,0F,0F,1F,hx,hy,hz},
                new float[]{x,y,z,0F,0F,0F,1F,hx,hy,hz,2F},
                "emoticons.left_leg");
        return b;
    }
    private static float[] matrix(float x,float y,float z,boolean mirror)
    {
        return new float[]{mirror?-1F:1F,0,0,0,0,1,0,0,
                0,0,mirror?-1F:1F,0,x,y,z,1};
    }
    private static void check(boolean value,String why)
    {if(!value)throw new AssertionError(why);}
    private static void close(float actual,float expected,float eps,
            String why)
    {check(Math.abs(actual-expected)<eps,
            why+" expected="+expected+" found="+actual);}
}

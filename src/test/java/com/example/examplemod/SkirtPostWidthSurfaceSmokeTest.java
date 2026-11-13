package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java 8 test for decoded quad vertices AFTER Chameleon width scale. */
public final class SkirtPostWidthSurfaceSmokeTest
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

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=
                new ArrayList<SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> swings=
                new LinkedHashMap<String,float[]>();
        final Map<String,SkirtAdaptiveWidth.Width> widths=
                new LinkedHashMap<String,SkirtAdaptiveWidth.Width>();
        final Map<String,Float> evidence=new LinkedHashMap<String,Float>();
        final BodyPartJoltContactBudget.Selection legs;

        Fixture()
        {
            for(int i=0;i<20;i++)
            {
                double angle=2D*Math.PI*i/20D;
                float x=.42F*(float)Math.cos(angle);
                float z=.42F*(float)Math.sin(angle);
                String upper=BONES[i][0],lower=BONES[i][1];
                float[][] quad=i==0?new float[][] {
                        {.395F,-.29F,-.04F},
                        {.445F,-.29F,-.04F},
                        {.445F,-.34F,.04F},
                        {.395F,-.34F,.04F},
                        {.420F,-.315F,0F}
                }:null;
                strips.add(new SkirtClothPoseCoupler.Strip(
                        upper,lower,mat(x,0F,z),
                        mat(x,-.16F,z),new float[]{x,-.40F,z},
                        null,quad));
                swings.put(upper,new float[]{1F,0F,0F,0F});
                swings.put(lower,new float[]{1F,0F,0F,0F});
                widths.put(upper,new SkirtAdaptiveWidth.Width(
                        SkirtAdaptiveWidth.widthAxis(upper),1F));
                widths.put(lower,new SkirtAdaptiveWidth.Width(
                        SkirtAdaptiveWidth.widthAxis(lower),1.18F));
            }
            evidence.put("group2",Float.valueOf(.16F));
            BodyPartJoltContactBudget b=new BodyPartJoltContactBudget();
            float[] box={.45F,-.30F,0F,0F,0F,0F,1F,
                    .13F,.17F,.12F};
            float[] typed={.45F,-.30F,0F,0F,0F,0F,1F,
                    .13F,.17F,.12F,2F};
            b.add(box,typed,"emoticons.left_leg");
            legs=b.select(true);
        }
    }

    public static void main(String[] args)
    {
        widthAdjustedSurfaceIsNotTheBoneOnlyAudit();
        limitedRefinementDoesNotFlareUncontactedPanels();
        noEvidenceOrNoColliderNeverChangesPose();
        System.out.println("SkirtPostWidthSurfaceSmokeTest OK");
    }

    private static void widthAdjustedSurfaceIsNotTheBoneOnlyAudit()
    {
        Fixture f=new Fixture();
        SkirtVisibleThighContacts.PostWidthResult before=
                SkirtVisibleThighContacts.correctPostWidth(
                        f.strips,f.swings,f.widths,f.legs,f.evidence,
                        .85F,false);
        need(before.sourcePoints==5,
                "decoded quad corners and face center must be measured");
        need(before.beforeHits>0&&before.overlapBefore>0F,
                "synthetic leg must overlap width-adjusted cloth surface");
        need(before.changedPanels==0 &&
                before.beforeHits==before.afterHits,
                "read-only audit cannot change a pose");
        SkirtVisibleThighContacts.PostWidthResult after=
                SkirtVisibleThighContacts.correctPostWidth(
                        f.strips,f.swings,f.widths,f.legs,f.evidence,
                        .85F,true);
        need(after.changedPanels>=1&&after.changedPanels<=1,
                "only the measured leg-contact panel may change");
        need(after.overlapAfter<after.overlapBefore-.002F,
                "post-width contact refinement must reduce actual overlap");
        need(after.maxDepthAfter<=after.maxDepthBefore+.01201F,
                "do not replace many mild collisions with one deep spike");
        need(after.sourcePoints==before.sourcePoints,
                "refinement cannot change Chameleon quad topology");
        need(after.widths.get("group22").multiplier>=1.18F &&
                after.widths.get("group22").multiplier<=1.28F,
                "never shrink active fabric or exceed maximum stretch");
        near(f.widths.get("group22").multiplier,1.18F,
                "input width must remain unchanged");
        near(f.swings.get("group22")[3],0F,
                "original Jolt pose must remain unchanged");
        near(after.swings.get("group24")[3],0F,
                "opposite untouched panel must not respond");
        near(after.swings.get("group2")[3],0F,
                "pinned upper belt link may not rotate in post-width stage");
    }

    private static void limitedRefinementDoesNotFlareUncontactedPanels()
    {
        Fixture f=new Fixture();
        SkirtVisibleThighContacts.PostWidthResult result=
                SkirtVisibleThighContacts.correctPostWidth(
                        f.strips,f.swings,f.widths,f.legs,f.evidence,
                        .85F,true);
        need(result.selectedPanels==1 && result.changedPanels<=1,
                "only confirmed direct contact should move");
        for(int i=1;i<BONES.length;i++)
        {
            near(result.swings.get(BONES[i][0])[3],0F,
                    "other upper bone must remain unchanged");
            near(result.swings.get(BONES[i][1])[3],0F,
                    "other lower bone must remain unchanged");
        }
    }

    private static void noEvidenceOrNoColliderNeverChangesPose()
    {
        Fixture f=new Fixture();
        SkirtVisibleThighContacts.PostWidthResult noContact=
                SkirtVisibleThighContacts.correctPostWidth(
                        f.strips,f.swings,f.widths,f.legs,
                        new LinkedHashMap<String,Float>(),.85F,true);
        need(noContact.changedPanels==0,
                "empty contact evidence cannot invent a collision correction");
        SkirtVisibleThighContacts.PostWidthResult normalScale=
                SkirtVisibleThighContacts.correctPostWidth(
                        f.strips,f.swings,f.widths,f.legs,f.evidence,
                        1F,true);
        need(normalScale.changedPanels==0,
                "legacy full-scale skirt must be untouched");
    }

    private static float[] mat(float x,float y,float z)
    {
        float[] m=new float[16];
        m[0]=m[5]=m[10]=m[15]=1F;
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }
    private static void near(float value,float expected,String message)
    {
        need(Math.abs(value-expected)<.0001F,
                message+" got "+value+" expected "+expected);
    }
    private static void need(boolean valid,String message)
    {
        if(!valid)throw new AssertionError(message);
    }
}

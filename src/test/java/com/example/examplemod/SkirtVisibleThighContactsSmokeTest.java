package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java 8 geometric regression: the visible hem must move outward from
 * a real fitted thigh box, not merely receive an existing Jolt bone capsule.
 */
public final class SkirtVisibleThighContactsSmokeTest
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

    public static void main(String[] args)
    {
        wideStridePushesClothOutward();
        standingThighsMustNotInflateHem();
        raisedThighEdgeCrossesWithCenterUnderWaist();
        broadVerticalNeutralThighDoesNotFlare();
        bothLegsOnlyDeflectLocalPanels();
        absentLegDoesNotInventContact();
        mirroredRearKeepsCorrectWorldPush();
        skirtUsesFullThighBoxWhileHairKeepsCapsule();
        System.out.println("SkirtVisibleThighContactsSmokeTest OK");
    }

    private static void wideStridePushesClothOutward()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,budget.select(true));
        require(result.touchedPanels>0,"leg must deflect skirt surfaces");
        require(result.peakOverlap>.02F,"penetration must be detected");
        float angle=result.swings.get(BONES[0][0])[3];
        float hem=result.swings.get(BONES[0][1])[3];
        require(angle>.02F && angle<=.1001F,
                "upper skirt root over-deflected "+angle);
        require(hem>.10F && hem<=.281F,
                "lower skirt panel did not flex locally "+hem);
        require(result.touchedPanels<=7,
                "a single thigh flared too many panels: "+result.touchedPanels);
        near(f.swings.get(BONES[0][0])[3],0F,.00001F,
                "physics input must not be mutated");
        near(result.swings.get(BONES[10][0])[3],0F,.00001F,
                "opposite skirt panel must not flare");
    }

    private static void standingThighsMustNotInflateHem()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        // Neutral hips: real thigh hulls near the middle of the waist.
        // Their broad fitted boxes must not form an inflated umbrella.
        budget.add(box(.12F,-.23F,0F),typed(.12F,-.23F,0F),
                "emoticons.left_leg");
        budget.add(box(-.12F,-.23F,0F),typed(-.12F,-.23F,0F),
                "emoticons.right_leg");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,budget.select(true));
        require(result.touchedPanels==0,
                "resting hip colliders should not inflate entire skirt");
        for(String[] names:BONES)
            for(String name:names)
                near(result.swings.get(name)[3],0F,.00001F,
                        "neutral frame changed "+name);
    }

    /**
     * Regression: the knee/thigh moves forward far enough that its CORNER
     * penetrates the hem, although the BONE CENTER remains at 0.18 blocks.
     * The former center-distance gate rejected this valid contact entirely.
     */
    private static void raisedThighEdgeCrossesWithCenterUnderWaist()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(wideBox(.18F,-.27F,0F),wideTyped(.18F,-.27F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result out=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,budget.select(true));
        require(out.touchedPanels>0,
                "raised thigh corner penetrated, but center-only gate missed it");
        require(out.peakOverlap>.01F,"thigh outer face did not trigger contact");
        require(out.touchedPanels<=7,"single thigh spread around skirt");
        require(out.swings.get(BONES[0][0])[3]<=.1001F,
                "waist root must not open into umbrella");
        require(out.swings.get(BONES[0][1])[3]<=.281F,
                "lower flap must remain bounded");
    }

    /** A wide but still vertical neutral leg is not a raised thigh.
     * It must not react like a horizontal protruding knee at the waist. */
    private static void broadVerticalNeutralThighDoesNotFlare()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        float[] obb={.18F,-.27F,0F,0F,0F,0F,1F,
                .23F,.32F,.14F};
        float[] capsule={.18F,-.27F,0F,0F,0F,0F,1F,
                .12F,.32F,.12F,2F};
        budget.add(obb,capsule,"emoticons.left_leg");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,budget.select(true));
        require(result.touchedPanels==0,
                "vertical resting thigh must not re-inflate the skirt");
    }

    private static void bothLegsOnlyDeflectLocalPanels()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        budget.add(box(-.42F,-.27F,0F),typed(-.42F,-.27F,0F),
                "emoticons.right_leg");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,budget.select(true));
        require(result.touchedPanels>0 && result.touchedPanels<15,
                "two leg contacts must not cause whole-ring flare: "+
                result.touchedPanels);
        for(String[] names:BONES)
        {
            require(result.swings.get(names[0])[3]<=.1001F,
                    "the waist-side upper panel flared");
            require(result.swings.get(names[1])[3]<=.281F,
                    "the hem exceeded the bounded outward deflection");
        }
    }

    private static void absentLegDoesNotInventContact()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(5F,-.27F,0F),typed(5F,-.27F,0F),"emoticons.left_leg");
        SkirtVisibleThighContacts.Result far=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,budget.select(true));
        require(far.touchedPanels==0,"remote leg must not move cloth");
        BodyPartJoltContactBudget empty=new BodyPartJoltContactBudget();
        SkirtVisibleThighContacts.Result missing=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,empty.select(true));
        require(missing.touchedPanels==0,"cannot synthesize missing thigh");
    }

    private static void mirroredRearKeepsCorrectWorldPush()
    {
        Fixture f=new Fixture(true);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(-.42F,-.27F,0F),typed(-.42F,-.27F,0F),
                "emoticons.right_leg");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,budget.select(true));
        require(result.touchedPanels>0,"rear thigh contact missing");
        float[] local=result.swings.get(BONES[10][0]);
        require(local[3]>.02F && local[3]<=.1001F,
                "rear panel root flared beyond safe limit");
        require(result.swings.get(BONES[10][1])[3]>.10F,
                "rear hem must flex at the lower joint");
        // Model's local Y=180 reverses Z. The world-space correction must
        // still point the rear panel OUTWARD (negative world X).
        float worldZ=-local[2];
        require(worldZ<-.8F,"rear contact correction was mirrored");
    }

    private static void skirtUsesFullThighBoxWhileHairKeepsCapsule()
    {
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection cloth=budget.select(true);
        BodyPartJoltContactBudget.Selection hair=budget.select(false);
        require(cloth.shapes[0][10]==JoltColliderPrimitives.BOX,
                "cloth requires complete physical thigh cuboid");
        require(hair.shapes[0][10]==JoltColliderPrimitives.CAPSULE,
                "hair prototype should retain its native limb capsule");
        for(int i=7;i<10;i++)
            near(cloth.shapes[0][i],cloth.boxes[0][i],.00001F,
                    "physical thigh width must match fitted skin box");
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=
                new ArrayList<SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> swings=new HashMap<String,float[]>();

        Fixture(boolean mirroredRear)
        {
            for(int i=0;i<BONES.length;i++)
            {
                double a=2D*Math.PI*i/BONES.length;
                float x=.42F*(float)Math.cos(a);
                float z=.42F*(float)Math.sin(a);
                boolean reverse=mirroredRear && i>=10;
                String top=BONES[i][0],bottom=BONES[i][1];
                strips.add(new SkirtClothPoseCoupler.Strip(top,bottom,
                        matrix(x,0F,z,reverse),
                        matrix(x,-.17F,z,reverse),
                        new float[]{x,-.38F,z}));
                swings.put(top,new float[]{1,0,0,0});
                swings.put(bottom,new float[]{1,0,0,0});
            }
        }
    }

    private static float[] matrix(float x,float y,float z,boolean mirror)
    {
        float[] m=new float[16];
        m[0]=mirror?-1F:1F;
        m[5]=1F;
        m[10]=mirror?-1F:1F;
        m[15]=1F;
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }
    private static float[] box(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.11F,.17F,.13F};}
    private static float[] wideBox(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.28F,.17F,.13F};}
    private static float[] wideTyped(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.12F,.17F,.12F,2F};}
    private static float[] typed(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.085F,.17F,.085F,2F};}
    private static void require(boolean b,String reason)
    {if(!b)throw new AssertionError(reason);}
    private static void near(float value,float expected,float e,String why)
    {require(Math.abs(value-expected)<e,why+"="+value);}
}

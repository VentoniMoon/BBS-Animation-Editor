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
        require(angle>.10F && angle<=1.301F,"unexpected native correction "+angle);
        near(f.swings.get(BONES[0][0])[3],0F,.00001F,
                "physics input must not be mutated");
        near(result.swings.get(BONES[10][0])[3],0F,.00001F,
                "opposite skirt panel must not flare");
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
        require(local[3]>.10F,"rear panel does not rotate");
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
    private static float[] typed(float x,float y,float z)
    {return new float[]{x,y,z,0,0,0,1,.085F,.17F,.085F,2F};}
    private static void require(boolean b,String reason)
    {if(!b)throw new AssertionError(reason);}
    private static void near(float value,float expected,float e,String why)
    {require(Math.abs(value-expected)<e,why+"="+value);}
}

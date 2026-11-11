package com.example.examplemod;

import java.util.Arrays;

/**
 * Standalone Java 8 test of native Jolt parent contact budgets.
 * Mirrors a walking Emoticons actor whose left/right leg regions
 * are listed after 30 unrelated geometry slices.
 */
public final class BodyPartJoltContactBudgetSmokeTest
{
    public static void main(String[] args)
    {
        overflowRetainsBothThighs();
        legacyHairKeepsOriginalOrder();
        noSyntheticLegsWhenMeshMissing();
        withinBudgetKeepsStableOrdering();
        System.out.println("BodyPartJoltContactBudgetSmokeTest OK");
    }

    private static void overflowRetainsBothThighs()
    {
        BodyPartJoltContactBudget budget=make(39,true);
        BodyPartJoltContactBudget.Selection s=budget.select(true);
        expect(s.boxes.length==BodyPartJoltContactBudget.LIMIT,
                "Jolt 32-shape budget exceeded");
        expect(s.shapes.length==s.boxes.length &&
                s.owners.size()==s.boxes.length,"native shape arrays diverged");
        expect(s.leftUpperLeg && s.rightUpperLeg,
                "thighs must outrank optional skin/attachment shapes");
        expect(s.skipped==9 && s.total==41,"overflow metadata incorrect");
        for(int i=0;i<s.boxes.length;i++)
        {
            expect(s.shapes[i][0]==s.boxes[i][0],
                    "fallback Box and native typed shape must remain paired");
            if(s.owners.get(i).equals("emoticons.left_leg"))
                expect(s.boxes[i][0]==101F,"left leg identity changed");
            if(s.owners.get(i).equals("emoticons.right_leg"))
                expect(s.boxes[i][0]==102F,"right leg identity changed");
        }
    }

    private static void legacyHairKeepsOriginalOrder()
    {
        BodyPartJoltContactBudget budget=make(39,true);
        BodyPartJoltContactBudget.Selection s=budget.select(false);
        expect(s.owners.size()==32,"historical box cap");
        expect(s.owners.get(0).equals("chameleon.hair"),
                "non-skirt original order must be preserved");
        expect(!s.leftUpperLeg&&!s.rightUpperLeg,
                "test setup invalid: legs must be after cap");
    }

    private static void withinBudgetKeepsStableOrdering()
    {
        BodyPartJoltContactBudget budget=make(10,true);
        BodyPartJoltContactBudget.Selection s=budget.select(true);
        expect(s.total==12 && s.skipped==0,
                "safe collider set should not be truncated");
        expect(s.owners.get(0).equals("chameleon.hair"),
                "reorder only when actual native budget is exceeded");
        expect(s.leftUpperLeg && s.rightUpperLeg,
                "existing full-body colliders not retained");
    }

    private static void noSyntheticLegsWhenMeshMissing()
    {
        BodyPartJoltContactBudget budget=make(40,false);
        BodyPartJoltContactBudget.Selection s=budget.select(true);
        expect(!s.leftUpperLeg&&!s.rightUpperLeg,
                "do not invent colliders without real Emoticons mesh");
    }

    private static BodyPartJoltContactBudget make(int count,boolean legs)
    {
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        for(int i=0;i<count;i++)
            budget.add(box(i),primitive(i),"chameleon.hair");
        if(legs)
        {
            budget.add(box(101),primitive(101),"emoticons.left_leg");
            budget.add(box(102),primitive(102),"emoticons.right_leg");
        }
        return budget;
    }

    private static float[] box(float id)
    {return new float[]{id,0,0, 0,0,0,1, .1F,.2F,.1F};}
    private static float[] primitive(float id)
    {return new float[]{id,0,0, 0,0,0,1, .1F,.2F,.1F, 2};}
    private static void expect(boolean ok,String why)
    {if(!ok)throw new AssertionError(why);}
}

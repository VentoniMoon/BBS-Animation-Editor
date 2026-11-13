package com.example.examplemod;

/** Standalone Java 8 regression for skirt-only three-zone thigh proxies. */
public final class SkirtLegCollisionProxySmokeTest
{
    private SkirtLegCollisionProxySmokeTest(){}

    public static void main(String[] args)
    {
        threeZonesAreProximalToDistal();
        tiltFollowsBoneInsteadOfWorldAxis();
        onlySkirtSelectionChanges();
        duplicateFittedBoxesDoNotTriplePressure();
        shapeBudgetPreservesBothThighs();
        invalidAndLowerLegStayOriginal();
        System.out.println("SkirtLegCollisionProxySmokeTest OK");
    }

    private static void threeZonesAreProximalToDistal()
    {
        float[] original=box(0F,-.25F,0F,.19F,.22F,.18F);
        float[][] parts=SkirtLegCollisionProxy.split(original);
        require(parts!=null&&parts.length==3,"must produce three zones");
        require(parts[0][1]>parts[1][1]&&parts[1][1]>parts[2][1],
                "upper zone must remain closest to belt");
        near(parts[0][7],original[7]*.70F,"upper X radius");
        near(parts[1][7],original[7]*.85F,"middle X radius");
        near(parts[2][7],original[7],"lower X full radius");
        near(parts[0][9],original[9]*.70F,"upper Z radius");
        near(parts[2][9],original[9],"lower Z full radius");
        require(parts[0][8]>.22F/3F,
                "zone should overlap its neighbor axially");
        for(int i=0;i<3;i++)
            near(parts[i][3],0F,"keep original bone rotation");
        near(original[1],-.25F,"source box must be unchanged");
        near(original[7],.19F,"source width must be unchanged");
    }

    private static void tiltFollowsBoneInsteadOfWorldAxis()
    {
        float[] rotated=box(2F,5F,7F,.19F,.24F,.19F);
        rotated[5]=.70710677F;rotated[6]=.70710677F;
        float[][] p=SkirtLegCollisionProxy.split(rotated);
        require(p!=null,"rotated leg must produce zones");
        near(p[0][0],2F-.16F,"proximal follows rotated local Y");
        near(p[1][0],2F,"middle remains centered");
        near(p[2][0],2F+.16F,"distal follows rotated local -Y");
        near(p[0][1],5F,"raised thigh must not use world Y slices");
        near(p[2][1],5F,"raised thigh must not flip contact profile");
    }

    private static void onlySkirtSelectionChanges()
    {
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        float[] thigh=box(.1F,-.2F,.13F,.19F,.22F,.19F);
        budget.add(thigh,typed(thigh),"emoticons.right_leg");
        float[] shin=box(.1F,-.6F,.13F,.16F,.22F,.16F);
        budget.add(shin,typed(shin),"emoticons.low_leg_right");
        BodyPartJoltContactBudget.Selection original=budget.select(true);
        BodyPartJoltContactBudget.Selection tapered=budget.select(true,true);
        BodyPartJoltContactBudget.Selection hair=budget.select(false,true);
        require(original.boxes.length==2&&hair.boxes.length==2,
                "old skirt/non-skirt callers must not be expanded");
        require(tapered.boxes.length==4,
                "one upper thigh becomes 3 zones; shin stays one box");
        require(tapered.leftUpperLeg==false&&tapered.rightUpperLeg,
                "right thigh metadata lost");
        require(tapered.owners.get(0).endsWith("@skirt-upper")&&
                tapered.owners.get(1).endsWith("@skirt-middle")&&
                tapered.owners.get(2).endsWith("@skirt-lower"),
                "zone identities must stay in documented order");
        for(int i=0;i<3;i++)
        {
            for(int k=0;k<10;k++)
                near(tapered.shapes[i][k],tapered.boxes[i][k],
                        "Jolt and visual boxes must match");
            near(tapered.shapes[i][10],JoltColliderPrimitives.BOX,
                    "native skirt zones must be oriented boxes");
        }
        near(tapered.boxes[3][7],shin[7],"lower leg unchanged");
        near(hair.shapes[0][10],JoltColliderPrimitives.CAPSULE,
                "hair retains original primitive type");
        near(thigh[7],.19F,"source thigh not mutated");
    }

    private static void duplicateFittedBoxesDoNotTriplePressure()
    {
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        float[] larger=box(.10F,-.20F,.13F,.19F,.22F,.19F);
        float[] smaller=box(.11F,-.215F,.13F,.16F,.206F,.16F);
        budget.add(smaller,typed(smaller),"emoticons.left_leg");
        budget.add(larger,typed(larger),"emoticons.left_leg");
        BodyPartJoltContactBudget.Selection s=budget.select(true,true);
        require(s.boxes.length==3,"same fitted thigh must have 3, not 6 zones");
        require(s.skipped==1&&s.total==2,"dedup must be auditable");
        near(s.boxes[2][7],.19F,"largest fit should define lower volume");

        // A separate segment, even under the same bone owner, survives.
        float[] far=box(.1F,-.5F,.13F,.16F,.18F,.16F);
        budget.add(far,typed(far),"emoticons.left_leg");
        require(budget.select(true,true).boxes.length==6,
                "distant valid box must not be merged into hip");
    }

    private static void shapeBudgetPreservesBothThighs()
    {
        BodyPartJoltContactBudget b=new BodyPartJoltContactBudget();
        for(int i=0;i<40;i++)
        {
            float[] other=box(i,0F,0F,.1F,.15F,.1F);
            b.add(other,typed(other),"chameleon.hair");
        }
        float[] left=box(-.15F,-.2F,0F,.19F,.22F,.19F);
        float[] right=box(.15F,-.2F,0F,.19F,.22F,.19F);
        b.add(left,typed(left),"emoticons.left_leg");
        b.add(right,typed(right),"emoticons.right_leg");
        BodyPartJoltContactBudget.Selection s=b.select(true,true);
        require(s.boxes.length==BodyPartJoltContactBudget.LIMIT,
                "no native over-budget shape expansion");
        require(s.leftUpperLeg&&s.rightUpperLeg,
                "must prioritize both thigh proxies before hair");
        int zones=0;
        for(String owner:s.owners)if(owner.contains("@skirt-"))zones++;
        require(zones==6,"all six thigh zones must survive prioritization");
    }

    private static void invalidAndLowerLegStayOriginal()
    {
        float[] tooBroad=box(0F,0F,0F,.6F,.3F,.6F);
        require(!SkirtLegCollisionProxy.eligible(
                "emoticons.left_leg",tooBroad),
                "large invalid skin fit must not be disguised by taper");
        require(!SkirtLegCollisionProxy.eligible(
                "emoticons.low_left_leg",
                box(0F,0F,0F,.18F,.22F,.18F)),
                "lower left leg must never be treated as thigh");
        require(!SkirtLegCollisionProxy.eligible(
                "emoticons.low_leg_right",
                box(0F,0F,0F,.18F,.22F,.18F)),
                "lower right leg must never be treated as thigh");
        require(SkirtLegCollisionProxy.split(new float[10])==null,
                "zero-sized boxes must fail closed");
    }

    private static float[] box(float x,float y,float z,
            float hx,float hy,float hz)
    {
        return new float[]{x,y,z,0F,0F,0F,1F,hx,hy,hz};
    }

    private static float[] typed(float[] box)
    {
        float[] out=new float[11];
        System.arraycopy(box,0,out,0,10);
        out[10]=JoltColliderPrimitives.CAPSULE;
        return out;
    }

    private static void near(float got,float expected,String why)
    {
        require(Math.abs(got-expected)<.0001F,
                why+" got="+got+" expected="+expected);
    }
    private static void require(boolean condition,String why)
    {
        if(!condition)throw new AssertionError(why);
    }
}

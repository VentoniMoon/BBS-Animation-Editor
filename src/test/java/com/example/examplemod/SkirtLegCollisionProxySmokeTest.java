package com.example.examplemod;

/** Standalone Java 8 regression for skirt-only three-zone thigh proxies. */
public final class SkirtLegCollisionProxySmokeTest
{
    private SkirtLegCollisionProxySmokeTest(){}

    public static void main(String[] args)
    {
        threeZonesAreProximalToDistal();
        tiltFollowsBoneInsteadOfWorldAxis();
        raisedThighShrinksOnlyDistalZone();
        raisedThighReleasesUpperPelvisClearance();
        pelvisAnchorIdentifiesUpperEvenWhenLocalYIsInverted();
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
        near(p[0][0],2F-.16F+
                (SkirtLegCollisionProxy.PROXIMAL_SHRINK?.24F*.12F:0F),
                "proximal follows rotated local Y and frees the belt");
        near(p[1][0],2F,"middle remains centered");
        near(p[2][0],2F+.16F,"distal follows rotated local -Y");
        near(p[0][1],5F,"raised thigh must not use world Y slices");
        near(p[2][1],5F,"raised thigh must not flip contact profile");
    }

    /**
     * User's frame 26: skirt cloth already rests acceptably on the lap,
     * but the LAST third of each upper-thigh OBB is still full width
     * near the knee. Test a per-thigh progressive taper with no change
     * to the upper/middle zones, standing gait or other collision types.
     */
    private static void raisedThighShrinksOnlyDistalZone()
    {
        final float[] base=box(.13F,-.3F,.04F,.19F,.24F,.18F);
        float[][] upright=SkirtLegCollisionProxy.split(base);
        float[] walking=base.clone();
        setThighAngle(walking,45F);
        float[][] gait=SkirtLegCollisionProxy.split(walking);
        near(gait[2][7],upright[2][7],
                "normal 45-degree stride must keep distal thigh X radius");
        near(gait[2][8],upright[2][8],
                "normal 45-degree stride must keep zone axial overlap");
        near(gait[2][9],upright[2][9],
                "normal 45-degree stride must keep distal thigh Z radius");

        float[] sixtyFive=base.clone(),eighty=base.clone();
        setThighAngle(sixtyFive,65F);
        setThighAngle(eighty,80F);
        float[][] transition=SkirtLegCollisionProxy.split(sixtyFive);
        float[][] seated=SkirtLegCollisionProxy.split(eighty);
        // PR #253 allows intentional extra relief at the upper and
        // middle thigh on sitting; their bone-axis length is unchanged.
        for(int zone=0;zone<2;zone++)
        {
            float target=zone==0?.82F:.88F;
            if(!SkirtLegCollisionProxy.PROXIMAL_SHRINK)target=1F;
            near(seated[zone][7],upright[zone][7]*target,
                    "top/middle radius should follow independent A/B");
            near(seated[zone][9],upright[zone][9]*target,
                    "top/middle depth should follow independent A/B");
            near(seated[zone][8],upright[zone][8],
                    "top/middle axis length must keep adjacent overlap");
            if(SkirtLegCollisionProxy.PROXIMAL_SHRINK)
                require(transition[zone][7]<upright[zone][7]&&
                        transition[zone][7]>seated[zone][7],
                        "hip-zone shrink must activate smoothly");
            else
                near(transition[zone][7],upright[zone][7],
                        "hip-zone A/B disabled must match old dimensions");
        }
        if(SkirtLegCollisionProxy.DISTAL_SHRINK)
        {
            require(transition[2][7]<upright[2][7]&&
                    transition[2][7]>seated[2][7],
                    "knee-facing zone must taper smoothly from 50 to 80");
            near(seated[2][7],base[7]*.85F,
                    "deep seated distal X shrink must be bounded to 15%");
            near(seated[2][9],base[9]*.85F,
                    "deep seated distal Z shrink must be bounded to 15%");
            near(seated[2][8],upright[2][8]*.95F,
                    "deep seated axial shrink must be at most five percent");
            require(seated[2][8]+seated[1][8]>
                    2F*base[8]/3F,
                    "distal taper broke the overlap with middle third");
        }
        else
            for(int dimension=7;dimension<10;dimension++)
                near(seated[2][dimension],upright[2][dimension],
                        "distal shrink A/B disabled must preserve old OBB");
        for(int dimension=0;dimension<10;dimension++)
            near(base[dimension],box(.13F,-.3F,.04F,
                    .19F,.24F,.18F)[dimension],
                    "source thigh collider must stay unchanged");

        BodyPartJoltContactBudget selected=
                new BodyPartJoltContactBudget();
        selected.add(eighty,typed(eighty),"emoticons.left_leg");
        BodyPartJoltContactBudget.Selection result=
                selected.select(true,true);
        require(result.boxes.length==3,
                "one bent upper thigh should produce three OBB zones");
        near(result.boxes[2][7],seated[2][7],
                "Jolt and XPBD should receive the same reduced OBB");
        near(result.shapes[2][7],result.boxes[2][7],
                "native and skirt renderer must share reduced dimensions");
    }

    /** The enlarged rotated upper thigh used to enter the pinned
     * waistband. Shift the TOP box away from the pelvis along the
     * genuine bone axis, not the camera- or world-Y axis, and keep
     * contact overlap with the middle/last boxes. */
    private static void raisedThighReleasesUpperPelvisClearance()
    {
        float[] thigh=box(.14F,.36F,.27F,.20F,.24F,.19F);
        setThighAngle(thigh,80F);
        float sin80=(float)Math.sin(Math.toRadians(80D));
        float cos80=(float)Math.cos(Math.toRadians(80D));
        float[] pelvis={
                thigh[0],thigh[1]+cos80*.32F,
                thigh[2]+sin80*.32F};
        float[][] zones=SkirtLegCollisionProxy.split(thigh,pelvis);
        require(zones!=null&&zones.length==3,
                "two thigh joints should still have exactly 3 OBB zones");
        float fullHalf=.24F;
        float expectedTopOffset=fullHalf*(2F/3F-
                (SkirtLegCollisionProxy.PROXIMAL_SHRINK?.12F:0F));
        float oldTopOffset=fullHalf*2F/3F;
        float dotTop=(zones[0][1]-thigh[1])*cos80+
                (zones[0][2]-thigh[2])*sin80;
        near(dotTop,expectedTopOffset,
                "upper OBB must withdraw toward the knee on raised leg");
        if(SkirtLegCollisionProxy.PROXIMAL_SHRINK)
            require(dotTop<oldTopOffset-.02F,
                    "deep hip bend should clear material near the belt");
        near(zones[1][1],thigh[1],
                "middle box must stay at the original thigh center");
        float topMiddleDistance=Math.abs(dotTop);
        require(zones[0][8]+zones[1][8]>topMiddleDistance+.001F,
                "waist-side clearance created a new collider gap");
        float middleDistalDistance=Math.abs(
                (zones[2][1]-thigh[1])*cos80+
                (zones[2][2]-thigh[2])*sin80);
        require(zones[1][8]+zones[2][8]>
                middleDistalDistance+.001F,
                "lower overlap must remain even with distal taper");
        require(zones[0][7]<zones[1][7]&&
                zones[1][7]<zones[2][7],
                "hip-to-knee taper should never form an outward ridge");

        // The same selected OBB is passed to the native/Jolt shape,
        // skirt preview overlay and XPBD collision responses.
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(thigh,typed(thigh),"emoticons.left_leg");
        float[] pelvisBox=box(
                pelvis[0],pelvis[1],pelvis[2],.12F,.09F,.12F);
        budget.add(pelvisBox,typed(pelvisBox),"emoticons.low_body");
        BodyPartJoltContactBudget.Selection selected=
                budget.select(true,true);
        require(selected.boxes.length==4,
                "fitted thigh + pelvis selection must stay in budget");
        for(int zone=0;zone<3;zone++)
            for(int axis=0;axis<10;axis++)
            {
                near(selected.boxes[zone][axis],
                        zones[zone][axis],
                        "physical skirt OBB must match preview selection");
                near(selected.shapes[zone][axis],
                        selected.boxes[zone][axis],
                        "Jolt collider must match XPBD and overlay OBB");
            }
    }

    private static void setThighAngle(float[] box,float degrees)
    {
        double half=Math.toRadians(degrees)*.5D;
        box[3]=(float)Math.sin(half);
        box[4]=0F;box[5]=0F;box[6]=(float)Math.cos(half);
    }

    private static void pelvisAnchorIdentifiesUpperEvenWhenLocalYIsInverted()
    {
        float[] inverted=box(0F,-.2F,0F,.19F,.22F,.19F);
        inverted[3]=1F;inverted[6]=0F; // 180 deg around X: local +Y points down
        float[] pelvis=new float[]{0F,0F,0F};
        float[][] corrected=SkirtLegCollisionProxy.split(inverted,pelvis);
        require(corrected[0][1]>corrected[2][1],
                "upper narrow zone must be closer to pelvis, not local +Y");

        BodyPartJoltContactBudget b=new BodyPartJoltContactBudget();
        b.add(inverted,typed(inverted),"emoticons.left_leg");
        float[] hip=box(0F,0F,0F,.18F,.12F,.18F);
        b.add(hip,typed(hip),"emoticons.low_body");
        BodyPartJoltContactBudget.Selection selection=b.select(true,true);
        require(selection.boxes.length==4,"pelvis must remain its own shape");
        require(selection.boxes[0][1]>selection.boxes[2][1],
                "skirt selection must use the captured pelvis center");
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

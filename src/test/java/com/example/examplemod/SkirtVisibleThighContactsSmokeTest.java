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
        auditMeasuresResidualEvenWhenNoVisualCorrectionIsAvailable();
        wholeRingGuardLeavesAuditableResidual();
        badBroadShapeDoesNotSuppressValidLocalThigh();
        waistTierOverlapMustNotBlockHemCollisionResponse();
        compact085RespondsLocallyWithoutChangingNormalScale();
        adaptiveLiftMustOnlyImproveRealCompactThighContact();
        jointMidCorrectionImprovesFrontCornerWithoutFlaredWaist();
        jointMidCorrectionRejectsRestingThighAndFullScale();
        shuffledBoneEnumerationKeepsSamePhysicalNeighbors();
        standingThighsMustNotInflateHem();
        raisedThighEdgeCrossesWithCenterUnderWaist();
        actualDiagonalCubeSurfaceDetectsMissedLeg();
        decodedQuadRotationIsPreserved();
        broadVerticalNeutralThighDoesNotFlare();
        bothLegsOnlyDeflectLocalPanels();
        twoPlausibleThighsCannotSilenceAllVisibleHemCorrection();
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

    /** A Java 8 proof that audit counts distinct geometry intersections,
     * and that the diagnostic does not edit any input or Jolt key. */
    private static void auditMeasuresResidualEvenWhenNoVisualCorrectionIsAvailable()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection leg=budget.select(true);
        SkirtVisibleThighContacts.Audit prior=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,f.swings,leg,.85F);
        require(prior.legBoxes==1,"must count actual fitted thigh OBB");
        require(prior.sourcePoints>0&&prior.uniqueHits>0,
                "visible source skirt must overlap modeled thigh");
        require(prior.uniqueHits<=prior.sourcePoints,
                "raw collider hits are not unique mesh points");
        require(prior.hitPanels>0&&prior.hitPanels<=20,
                "audit incorrectly reports panel count");
        require(prior.dominantOwner.contains("emoticons.left_leg"),
                "collider identity was lost");
        SkirtVisibleThighContacts.Result corrected=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,leg,.85F);
        SkirtVisibleThighContacts.Audit after=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,corrected.swings,leg,.85F);
        require(after.sourcePoints==prior.sourcePoints,
                "contact correction changed source cube topology");
        require(after.legBoxes==prior.legBoxes,
                "contact correction changed physics collider topology");
        require(Float.isFinite(after.maxOverlap) &&
                after.uniqueHits<=after.sourcePoints,
                "invalid post-correction overlap diagnostic");
        SkirtVisibleThighContacts.Audit repeated=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,corrected.swings,leg,.85F);
        require(after.uniqueHits==repeated.uniqueHits &&
                after.rawHits==repeated.rawHits &&
                after.hitPanels==repeated.hitPanels,
                "repeat draw changes diagnostic results");
        require(f.swings.get(BONES[0][0])[3]==0F,
                "read-back mutated original Jolt pose");
    }

    /**
     * A historically observed failure: one oversized/offset contact proxy
     * marks every panel as penetrating. The protective >12/20 guard MUST
     * still avoid the umbrella flare, but the new audit MUST disclose the
     * untouched residual rather than implying the collision was fixed.
     */
    private static void wholeRingGuardLeavesAuditableResidual()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(new float[]{0F,-.2F,0F,0F,0F,0F,1F,
                    .60F,.40F,.60F},
                new float[]{0F,-.2F,0F,0F,0F,0F,1F,
                    .20F,.40F,.20F,2F},
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection selected=budget.select(true);
        SkirtVisibleThighContacts.Audit pre=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,f.swings,selected,.85F);
        require(pre.hitPanels==20,
                "synthetic oversized leg should overlap entire skirt ring");
        SkirtVisibleThighContacts.Result correction=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,selected,.85F);
        require(correction.touchedPanels==0,
                "20-panel guard must avoid expanding the whole ring");
        SkirtVisibleThighContacts.Audit post=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,correction.swings,selected,.85F);
        require(post.uniqueHits==pre.uniqueHits && post.hitPanels==20,
                "audit must expose unchanged penetration after safety abort");
    }

    /** A real observed pattern: aggregate 20-panel contact aborts the
     * whole skirt because ONE incorrect giant leg shape overlaps every
     * strip. It must not suppress a second correctly fitted local thigh
     * box. Do not disable the global umbrella guard for the bad box. */
    private static void badBroadShapeDoesNotSuppressValidLocalThigh()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(new float[]{0F,-.2F,0F,0F,0F,0F,1F,
                    .60F,.40F,.60F},
                new float[]{0F,-.2F,0F,0F,0F,0F,1F,
                    .20F,.40F,.20F,2F},
                "emoticons.right_leg");
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection selected=budget.select(true);
        SkirtVisibleThighContacts.Audit pre=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,f.swings,selected,.85F);
        require(pre.hitPanels==20&&pre.perLegPanels.length==2,
                "synthetic oversized OBB must create full-ring false contact");
        require(pre.perLegPanels[0]==20&&pre.perLegPanels[1]<=12,
                "only giant collider must exceed spatial coverage cap");
        require(pre.perLegCoverage.contains("panels=20"),
                "diagnostic must identify oversized collider panel coverage");
        SkirtVisibleThighContacts.Result result=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,selected,.85F);
        require(result.touchedPanels>0&&result.touchedPanels<=7,
                "valid local box was incorrectly silenced by giant collider");
        require(result.contactDepths.containsKey(BONES[0][0]),
                "local front thigh should still produce adaptive response");
        near(result.swings.get(BONES[10][0])[3],0F,.00001F,
                "giant false collider must not create rear umbrella flare");
        near(f.swings.get(BONES[0][0])[3],0F,.00001F,
                "source Jolt pose must remain untouched");
    }

    /**
     * The real compact 0.85 garment's UPPER tier naturally runs close
     * to the actor's thigh. In the old solver an upper-tier overlap was
     * counted as a hem collision and could trigger the 20/20 abort.
     * Its inner upper quad must NOT move the lower cloth, whereas a real
     * overlap at the lower hem must still produce a local correction.
     */
    private static void waistTierOverlapMustNotBlockHemCollisionResponse()
    {
        Fixture f=new Fixture(false);
        SkirtClothPoseCoupler.Strip source=f.strips.get(0);
        f.strips.set(0,new SkirtClothPoseCoupler.Strip(
                source.upper,source.lower,source.upperWorld,
                source.lowerWorld,source.tipWorld,
                new float[][]{{.42F,-.12F,0F}},null));
        BodyPartJoltContactBudget high=new BodyPartJoltContactBudget();
        high.add(box(.42F,-.12F,0F),typed(.42F,-.12F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection selected=high.select(true);
        SkirtVisibleThighContacts.Audit full=
                SkirtVisibleThighContacts.auditBonePose(
                        f.strips,f.swings,selected,.85F);
        SkirtVisibleThighContacts.Audit hem=
                SkirtVisibleThighContacts.auditHemBonePose(
                        f.strips,f.swings,selected,.85F);
        require(full.uniqueHits>0,
                "whole-garment audit must still see upper thigh overlap");
        require(hem.uniqueHits==0 && hem.hitPanels==0,
                "upper waist proximity is not a lower-hem collision");
        SkirtVisibleThighContacts.Result response=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,selected,.85F);
        require(response.touchedPanels==0 &&
                response.contactDepths.isEmpty(),
                "upper garment tier alone must not flare the hem");

        BodyPartJoltContactBudget low=new BodyPartJoltContactBudget();
        low.add(box(.42F,-.30F,0F),typed(.42F,-.30F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result real=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,low.select(true),.85F);
        require(real.touchedPanels>0 && real.touchedPanels<=7,
                "real lower hem contact should still deflect locally");
    }

    private static void compact085RespondsLocallyWithoutChangingNormalScale()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection geometry=budget.select(true);
        SkirtVisibleThighContacts.Result standard=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,geometry,1F);
        SkirtVisibleThighContacts.Result compact=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,geometry,.85F,true,false);
        require(standard.touchedPanels>0&&compact.touchedPanels>0,
                "real thigh must affect both sizes");
        require(compact.peakOverlap>=standard.peakOverlap,
                "0.85 should receive larger local clearance");
        require(!compact.contactDepths.isEmpty() &&
                compact.contactDepths.containsKey(BONES[0][0]),
                "must expose measured direct contact for adaptive width");
        require(compact.contactDepths.size()<=7,
                "compact skirt contact must remain local");
        require(compact.swings.get(BONES[0][0])[3]<=.1001F,
                "compact waistband must not flare");
        require(compact.swings.get(BONES[0][1])[3]<=.956F,
                "compact dynamic hem angular safety cap");
        SkirtVisibleThighContacts.Result noBody=
                SkirtVisibleThighContacts.correct(f.strips,f.swings,
                        new BodyPartJoltContactBudget().select(true),.85F);
        require(noBody.contactDepths.isEmpty()&&noBody.touchedPanels==0,
                "small visual scale alone must NOT expand the skirt");
    }

    /** The geo file's bone order is not a reliable angular ring order.
     * A different enumeration of the SAME 20 physical strips must not
     * change which neighboring panels receive the local contact response. */
    /**
     * Local iterative lift must reduce actual lower-cube/leg intersections,
     * not just increase an angular number. Compare the same frame against
     * the old cap, with an explicit A/B argument to avoid JVM order effects.
     */
    private static void adaptiveLiftMustOnlyImproveRealCompactThighContact()
    {
        boolean found=false;
        float[] xPositions={.42F,.46F,.50F,.54F,.58F};
        float[] heights={-.24F,-.28F,-.32F};
        float[] poses={0F,.18F,.32F,.50F,.62F};
        for(float x:xPositions)
        {
            if(found)break;
            for(float y:heights)
            {
                if(found)break;
                for(float pose:poses)
                {
                    Fixture f=new Fixture(false);
                    f.swings.put(BONES[0][1],new float[]{0F,0F,1F,pose});
                    BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
                    budget.add(box(x,y,0F),typed(x,y,0F),
                            "emoticons.left_leg");
                    BodyPartJoltContactBudget.Selection leg=budget.select(true);
                    SkirtVisibleThighContacts.Result normal=
                            SkirtVisibleThighContacts.correct(
                                    f.strips,f.swings,leg,.85F,false);
                    SkirtVisibleThighContacts.Result lifted=
                            SkirtVisibleThighContacts.correct(
                                    f.strips,f.swings,leg,.85F,true);
                    if(lifted.liftedHemPanels>0)
                    {
                        SkirtVisibleThighContacts.Audit baseline=
                                SkirtVisibleThighContacts.auditHemBonePose(
                                        f.strips,normal.swings,leg,.85F);
                        SkirtVisibleThighContacts.Audit improved=
                                SkirtVisibleThighContacts.auditHemBonePose(
                                        f.strips,lifted.swings,leg,.85F);
                        if(improved.uniqueHits<baseline.uniqueHits)
                        {
                            found=true;
                            require(lifted.liftedHemPanels<=3,
                                    "one thigh must move at most three extra lower bones");
                            require(lifted.liftOverlapReduction>0F,
                                    "trial lift must reduce geometric penetration");
                            require(lifted.swings.get(BONES[0][1])[3]<=.956F,
                                    "collision-only swing passed dynamic safety cap");
                            for(int i=0;i<BONES.length;i++)
                            {
                                // No additional upper-bone/waist motion is
                                // permitted by the adaptive lift stage.
                                float[] a=normal.swings.get(BONES[i][0]);
                                float[] b=lifted.swings.get(BONES[i][0]);
                                for(int axis=0;axis<4;axis++)
                                    near(a[axis],b[axis],.00001F,
                                            "upper waistband altered by lift");
                            }
                            float[] original=f.swings.get(BONES[0][1]);
                            near(original[3],pose,.00001F,
                                    "adaptive lift changed original Jolt sample");
                            SkirtVisibleThighContacts.Result replay=
                                    SkirtVisibleThighContacts.correct(
                                            f.strips,f.swings,leg,.85F,true);
                            for(String[] names:BONES)
                            {
                                float[] a=lifted.swings.get(names[1]);
                                float[] b=replay.swings.get(names[1]);
                                for(int component=0;component<4;component++)
                                    near(a[component],b[component],.00001F,
                                            "lift accumulated between render passes");
                            }
                        }
                    }
                }
            }
        }
        require(found,
                "no legitimate raised thigh geometry produced lower hem "+
                "collision reduction beyond old hard cap");

        Fixture noContact=new Fixture(false);
        BodyPartJoltContactBudget none=new BodyPartJoltContactBudget();
        SkirtVisibleThighContacts.Result idle=
                SkirtVisibleThighContacts.correct(
                        noContact.strips,noContact.swings,
                        none.select(true),.85F,true);
        require(idle.liftedHemPanels==0 && idle.touchedPanels==0,
                "standing with no leg OBB must never raise the skirt");
        BodyPartJoltContactBudget leg=new BodyPartJoltContactBudget();
        leg.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result fullScaleA=
                SkirtVisibleThighContacts.correct(
                        noContact.strips,noContact.swings,
                        leg.select(true),1F,false);
        SkirtVisibleThighContacts.Result fullScaleB=
                SkirtVisibleThighContacts.correct(
                        noContact.strips,noContact.swings,
                        leg.select(true),1F,true);
        require(fullScaleB.liftedHemPanels==0,
                "the full-sized skirt must retain old swing limits");
        for(String[] names:BONES)
            for(String name:names)
                for(int component=0;component<4;component++)
                    near(fullScaleA.swings.get(name)[component],
                            fullScaleB.swings.get(name)[component],
                            .00001F,"scale one should be exact legacy behavior");
    }

    /**
     * A front corner's middle surface can remain IN the raised thigh even
     * after the lower hem has already swung up. Detect and correct those
     * real hinge-area cube vertices through the two-bone joint, without
     * touching the original Jolt pose or far / upper resting skirt.
     */
    private static void jointMidCorrectionImprovesFrontCornerWithoutFlaredWaist()
    {
        boolean found=false;
        for(float x:new float[]{.40F,.42F,.45F,.48F})
        {
            if(found)break;
            for(float y:new float[]{-.25F,-.28F,-.31F})
            {
                if(found)break;
                for(float upperY:new float[]{-.16F,-.19F,-.22F})
                {
                    Fixture f=new Fixture(false);
                    SkirtClothPoseCoupler.Strip panel=f.strips.get(0);
                    f.strips.set(0,new SkirtClothPoseCoupler.Strip(
                            panel.upper,panel.lower,panel.upperWorld,
                            panel.lowerWorld,panel.tipWorld,
                            new float[][]{{.42F,upperY,0F}},
                            new float[][]{{.42F,-.29F,0F},
                                    {.42F,-.35F,0F}}));
                    BodyPartJoltContactBudget budget=
                            new BodyPartJoltContactBudget();
                    budget.add(box(x,y,0F),typed(x,y,0F),
                            "emoticons.left_leg");
                    BodyPartJoltContactBudget.Selection leg=budget.select(true);
                    SkirtVisibleThighContacts.Result prior=
                            SkirtVisibleThighContacts.correct(
                                    f.strips,f.swings,leg,.85F,true,false);
                    SkirtVisibleThighContacts.Result improved=
                            SkirtVisibleThighContacts.correct(
                                    f.strips,f.swings,leg,.85F,true,true);
                    if(improved.jointLiftPanels>0 &&
                            improved.midOverlapReduction>.004F)
                    {
                        found=true;
                        require(improved.jointLiftPanels<=3,
                                "one moving thigh opened too many middle panels");
                        require(improved.midCandidatePanels>0,
                                "joint trial used a contact it never detected");
                        float root=improved.swings.get(BONES[0][0])[3];
                        require(root<.651F,
                                "new root joint exceeded safety bound: "+root);
                        // The belt is still pinned in the same world
                        // position. Only swing keys are modified.
                        for(int index=8;index<=12;index++)
                            near(prior.swings.get(BONES[index][0])[3],
                                    improved.swings.get(BONES[index][0])[3],
                                    .00001F,"opposite belt panel altered");
                        SkirtVisibleThighContacts.Audit oldHem=
                                SkirtVisibleThighContacts.auditHemBonePose(
                                        f.strips,prior.swings,leg,.85F);
                        SkirtVisibleThighContacts.Audit newHem=
                                SkirtVisibleThighContacts.auditHemBonePose(
                                        f.strips,improved.swings,leg,.85F);
                        require(newHem.uniqueHits<=oldHem.uniqueHits+3,
                                "middle correction created severe hem clipping");
                        require(f.swings.get(BONES[0][0])[3]==0F,
                                "new joint modified Jolt starting pose");
                        SkirtVisibleThighContacts.Result replay=
                                SkirtVisibleThighContacts.correct(
                                        f.strips,f.swings,leg,.85F,true,true);
                        for(String[] names:BONES)
                            for(String name:names)
                                for(int k=0;k<4;k++)
                                    near(improved.swings.get(name)[k],
                                            replay.swings.get(name)[k],
                                            .00001F,
                                            "middle collision accumulated across renders");
                        break;
                    }
                }
            }
        }
        require(found,
                "no synthetic raised thigh caused a beneficial two-bone "+
                "middle joint correction");
    }

    private static void jointMidCorrectionRejectsRestingThighAndFullScale()
    {
        Fixture f=new Fixture(false);
        // Waist-zone surface (31% of the belt-to-hem length) must
        // NEVER become a middle contact even when the leg OBB contains
        // the top of the skirt.
        SkirtClothPoseCoupler.Strip panel=f.strips.get(0);
        f.strips.set(0,new SkirtClothPoseCoupler.Strip(
                panel.upper,panel.lower,panel.upperWorld,
                panel.lowerWorld,panel.tipWorld,
                new float[][]{{.42F,-.12F,0F}},null));
        BodyPartJoltContactBudget waist=new BodyPartJoltContactBudget();
        waist.add(box(.42F,-.12F,0F),typed(.42F,-.12F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result idle=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,waist.select(true),.85F,true,true);
        require(idle.jointLiftPanels==0,
                "upper resting waist contact raised skirt middle");
        BodyPartJoltContactBudget empty=new BodyPartJoltContactBudget();
        SkirtVisibleThighContacts.Result absent=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,empty.select(true),.85F,true,true);
        require(absent.jointLiftPanels==0,
                "missing leg collider activated joint solver");
        BodyPartJoltContactBudget moving=new BodyPartJoltContactBudget();
        moving.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        SkirtVisibleThighContacts.Result fullA=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,moving.select(true),1F,true,false);
        SkirtVisibleThighContacts.Result fullB=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,moving.select(true),1F,true,true);
        require(fullB.jointLiftPanels==0,
                "scale 1 must retain legacy 2-bone pose exactly");
        for(String[] names:BONES)
            for(String name:names)
                for(int k=0;k<4;k++)
                    near(fullA.swings.get(name)[k],
                            fullB.swings.get(name)[k],
                            .00001F,"full-size skirt changed by compact solver");
    }

    private static void shuffledBoneEnumerationKeepsSamePhysicalNeighbors()
    {
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        budget.add(box(.42F,-.27F,0F),typed(.42F,-.27F,0F),
                "emoticons.left_leg");
        BodyPartJoltContactBudget.Selection legs=budget.select(true);
        SkirtVisibleThighContacts.Result original=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,legs,.85F);
        java.util.List<SkirtClothPoseCoupler.Strip> reordered=
                new java.util.ArrayList<SkirtClothPoseCoupler.Strip>(f.strips);
        java.util.Collections.rotate(reordered,7);
        java.util.Collections.swap(reordered,0,11);
        java.util.Collections.swap(reordered,3,16);
        SkirtVisibleThighContacts.Result shuffled=
                SkirtVisibleThighContacts.correct(
                        reordered,f.swings,legs,.85F);
        for(String[] names:BONES)
            for(String bone:names)
            {
                float[] a=original.swings.get(bone);
                float[] b=shuffled.swings.get(bone);
                require(a!=null&&b!=null,"missing changed panel "+bone);
                for(int axis=0;axis<4;axis++)
                    near(a[axis],b[axis],.00002F,
                            "contact neighbor order changed "+bone);
            }
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

    private static void actualDiagonalCubeSurfaceDetectsMissedLeg()
    {
        // A diagonal side cube extends ~3px beyond its central Jolt
        // strip. The old fixed +/-1px contact probes MISS a narrow leg
        // intersecting the *actual* rendered panel corner.
        Fixture f=new Fixture(false);
        BodyPartJoltContactBudget budget=new BodyPartJoltContactBudget();
        float[] actualBox={.42F,-.30F,.18F,0F,0F,0F,1F,
                .055F,.07F,.025F};
        float[] capsule={.42F,-.30F,.18F,0F,0F,0F,1F,
                .04F,.07F,.04F,2F};
        budget.add(actualBox,capsule,"emoticons.left_leg");
        SkirtVisibleThighContacts.Result old=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,budget.select(true));
        require(old.swings.get(BONES[0][0])[3]<.001F,
                "old bone-centered strip should miss the protruding cube corner");
        SkirtClothPoseCoupler.Strip oldStrip=f.strips.get(0);
        f.strips.set(0,new SkirtClothPoseCoupler.Strip(
                oldStrip.upper,oldStrip.lower,oldStrip.upperWorld,
                oldStrip.lowerWorld,oldStrip.tipWorld,null,
                new float[][] {{.42F,-.30F,.18F}}));
        SkirtVisibleThighContacts.Result corrected=
                SkirtVisibleThighContacts.correct(
                        f.strips,f.swings,budget.select(true));
        require(corrected.exactSurfaceSamples>0 &&
                corrected.exactSurfaceHits>0,
                "real rendered cube surface must find the contact");
        require(corrected.swings.get(BONES[0][0])[3]>.01F &&
                corrected.touchedPanels<7,
                "real geometry collision must stay localized");
        require(corrected.swings.get(BONES[0][0])[3]<=.1001F,
                "cube collision must not balloon waist");
    }

    private static void decodedQuadRotationIsPreserved()
    {
        float[] m=matrix(0F,0F,0F,false);
        float[][] quads={{1F,0F,0F},{0F,0F,0F},
                {0F,1F,0F},{1F,1F,0F}};
        float[][] surface=BodyPartJoltCubeGeometry
                .surfaceSamplesRenderedVertices(m,new float[]{0,0,0},
                        new float[]{0,0,0},new float[]{0,0,90F},quads);
        require(surface.length==5,"missing quad center sample");
        near(surface[0][0],0F,.0001F,
                "rotated cube world X incorrect");
        near(surface[0][1],1F,.0001F,
                "rotated cube world Y incorrect");
        near(surface[4][0],-.5F,.0001F,
                "rotated quad center wrong X");
        near(surface[4][1],.5F,.0001F,
                "rotated quad center wrong Y");
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

    /**
     * The supplied in-game 0.85 walk log showed 14-19 hem panels
     * affected by two separately fitted thigh sets, but many frames
     * had correctedPanels=0. Find an equivalent synthetic geometry
     * with two individually local OBBs whose UNION crosses the old
     * hard >12 limit, then verify the solver no longer cancels both.
     */
    private static void twoPlausibleThighsCannotSilenceAllVisibleHemCorrection()
    {
        Fixture f=new Fixture(false);
        boolean found=false;
        float[] distances={.28F,.32F,.36F,.40F};
        float[] halfSizes={.22F,.25F,.28F,.31F};
        for(float center:distances)
        {
            if(found)break;
            for(float half:halfSizes)
            {
                if(found)break;
                BodyPartJoltContactBudget left=new BodyPartJoltContactBudget();
                BodyPartJoltContactBudget right=new BodyPartJoltContactBudget();
                float[] front={center,-.29F,0F,0F,0F,0F,1F,
                        half,.20F,half};
                float[] rear={-center,-.29F,0F,0F,0F,0F,1F,
                        half,.20F,half};
                left.add(front,typed(center,-.29F,0F),
                        "emoticons.left_leg");
                right.add(rear,typed(-center,-.29F,0F),
                        "emoticons.right_leg");
                SkirtVisibleThighContacts.Audit a=
                        SkirtVisibleThighContacts.auditHemBonePose(
                                f.strips,f.swings,left.select(true),.85F);
                SkirtVisibleThighContacts.Audit b=
                        SkirtVisibleThighContacts.auditHemBonePose(
                                f.strips,f.swings,right.select(true),.85F);
                BodyPartJoltContactBudget union=
                        new BodyPartJoltContactBudget();
                union.add(front,typed(center,-.29F,0F),
                        "emoticons.left_leg");
                union.add(rear,typed(-center,-.29F,0F),
                        "emoticons.right_leg");
                SkirtVisibleThighContacts.Audit both=
                        SkirtVisibleThighContacts.auditHemBonePose(
                                f.strips,f.swings,union.select(true),.85F);
                if(a.hitPanels>=5&&a.hitPanels<=12 &&
                        b.hitPanels>=5&&b.hitPanels<=12 &&
                        both.hitPanels>=14)
                {
                    found=true;
                    SkirtVisibleThighContacts.Result solved=
                            SkirtVisibleThighContacts.correct(
                                    f.strips,f.swings,
                                    union.select(true),.85F);
                    require(solved.locallyLimited,
                            "two local thighs must invoke bounded islands");
                    require(solved.candidatePanels>12 &&
                            solved.selectedPanels<=12 &&
                            solved.selectedPanels>0,
                            "still aborted all contacts for two thighs: "+
                            solved.candidatePanels+"->"+solved.selectedPanels);
                    require(solved.touchedPanels>0 &&
                            solved.touchedPanels<18,
                            "limited correction must preserve a natural skirt "+
                            solved.touchedPanels);
                    require(f.swings.get(BONES[0][0])[3]==0F,
                            "source Jolt pose unexpectedly mutated");
                }
            }
        }
        require(found,
                "test fixture lacks two plausible thigh OBBs spanning "+
                "14+ distinct hem panels");
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

package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Preview-only clearance for actual skirt panel SURFACES against actual
 * Emoticons leg geometry. Jolt solves the central 2-bone chains; broad flat
 * panels reach farther than those tiny central native capsules. In particular,
 * a 3D leg collider can be installed yet pass through the visible hem.
 *
 * Uses the same captured world-space fitted OBBs passed into native Jolt.
 * Rotates ONLY skirt panel bones around their animated waist anchors.
 * Does not move the actor, change animation keys, touch the native Jolt world,
 * invent missing colliders, or alter hair/ragdoll physics.
 */
public final class SkirtVisibleThighContacts
{
    private static final int PANELS=20;
    private static final float WIDTH=.0625F; // actual 2px wide panels / 16
    /*
     * Previous correction repeatedly rotated ROOTS by up to 0.95 rad
     * and propagated to neighbors. Both legs near the standing waist
     * could open all 20 panels into an inverted umbrella.
     *
     * Keep the waist nearly fixed; allow the hem to give locally.
     */
    private static final float MAX_EXTRA_ROOT=.095F;
    private static final float MAX_EXTRA_HEM=.28F;
    private static final float MAX_FINAL_ROOT=.52F;
    private static final float MAX_FINAL_HEM=.60F;
    /** Experimental lift for compact (.85) skirts: trial rotations
     * only at genuine selected thigh-to-hem contacts. Off restores
     * the exact pre-existing visual correction. */
    private static final boolean ADAPTIVE_HEM_LIFT=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.adaptiveHemLift","true"));
    /** Contact-only secondary solve of the mid-section of a compact
     * two-bone skirt. It operates after Adaptive Hem Lift and commits a
     * joint upper/lower trial ONLY when real geometry penetrates less. */
    private static final boolean ADAPTIVE_MID_COLLISION=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.adaptiveMidCollision","true"));
    private static final float MID_CONTACT_START=.40F;
    private static final float MID_CONTACT_END=.70F;
    private static final float MAX_EXTRA_MID_ROOT=.13F;
    private static final float MAX_MID_ROOT_CAP=.65F;
    /** Experimental contact-weighted envelope relative to native Jolt.
     * Set false to reproduce the exact pre-silhouette #188 behaviour. */
    private static final boolean SILHOUETTE_PRESERVATION=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.silhouettePreservation","true"));
    /** Only rescue an otherwise rejected thigh when its oriented hull
     * actually reaches BEYOND the rest waist radius, not just into the
     * naturally tight inner fabric at scale 0.85. */
    private static final float RESCUE_OUTER_SURPLUS=.027F;
    private static final float NEIGHBOR_RESPONSE=.16F;
    /* Skip neutral hips only if the entire oriented leg volume sits
     * well inside the waist, never based on the bone CENTER alone. */
    private static final float LEG_OUTWARD_THRESHOLD=.82F;
    /** A center/tilt gate can miss raised thighs while they are crossing
     * the compact skirt near the pelvis. Rescue ONLY a thigh with clear
     * penetration of its actual, exposed middle/hem Chameleon vertices.
     * This does not reinterpret mere inner-waist contact as a collision.
     * A/B flag disables only this fallback, not existing protection. */
    private static final boolean SURFACE_EVIDENCE_LEG_GATE=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.surfaceEvidenceLegGate","true"));
    private static final float SURFACE_EVIDENCE_DEPTH=.038F;
    private static final float OUTER_SURFACE_BAND=.030F;
    /** Experimental broad-hull rejection, preserving the previous global
     * guard as an absolute fallback when smaller local boxes cannot isolate
     * a useful response. Does not remove the collider from native Jolt. */
    private static final boolean REJECT_BROAD_LEG_BOXES=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.rejectBroadLegBoxes","true"));
    private static final int MAX_SINGLE_LEG_PANELS=12;
    /** Restrict only genuine two-leg aggregate >12/20 contacts to
     * two short angular contact islands, instead of cancelling ALL
     * correction for the user's wide step. Local and neutral input
     * remains exactly as before. Reversible A/B test flag. */
    private static final boolean LOCAL_CONTACT_ISLANDS=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.localContactIslands","true"));
    /** The upper tier sits naturally alongside the upper thighs inside
     * the waist. Treating its INNER cube faces as hem contacts caused
     * 20/20-panel false alarms and repeatedly aborted all correction.
     * Only use the lower 32% of the authored skirt length as actual
     * hem/leg contact for this conservative test. */
    private static final float HEM_CONTACT_FROM=.68F;
    private static final boolean HEM_ONLY_CONTACT=
            Boolean.parseBoolean(System.getProperty(
                    "bbs.editor.physics.skirt.hemOnlyContact","true"));

    public static final class Result
    {
        public final Map<String,float[]> swings;
        public final int touchedPanels;
        public final float peakOverlap;
        /** Actual decoded Chameleon cube surface samples vs contact hits. */
        public final int exactSurfaceSamples,exactSurfaceHits;
        /** Real direct surface-to-thigh penetration, keyed by upper bone.
         * A neighbor's visual movement is NOT a new collision contact. */
        public final Map<String,Float> contactDepths;
        public final int candidatePanels,selectedPanels;
        public final boolean locallyLimited;
        /** Number of lower bones granted additional collision-only swing. */
        public final int liftedHemPanels;
        /** Sum of local uncapped overlap depths removed by trial rotations. */
        public final float liftOverlapReduction;
        /** Corrected middle-tier contact, independently of the hem lift. */
        public final int midCandidatePanels,jointLiftPanels;
        public final float midOverlapReduction;
        /** The old tilt/center gate rejected these boxes; strong
         * outer-surface penetration rescued only some of them. */
        public final int rescuedLegBoxes,rejectedLegBoxes;
        /** Contact-weighted outward shape regularization after the three
         * existing correction passes: clipped or retained on collision. */
        public final int silhouetteLimitedPanels,silhouetteBlockedPanels;
        public final float silhouetteRemovedOutward;
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap)
        {
            this(swings,touchedPanels,peakOverlap,0,0,
                    Collections.<String,Float>emptyMap());
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    contactDepths,0,0,false);
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths,
                int candidatePanels,int selectedPanels,boolean locallyLimited)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    contactDepths,candidatePanels,selectedPanels,
                    locallyLimited,0,0F);
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths,
                int candidatePanels,int selectedPanels,boolean locallyLimited,
                int liftedHemPanels,float liftOverlapReduction)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    contactDepths,candidatePanels,selectedPanels,
                    locallyLimited,liftedHemPanels,liftOverlapReduction,
                    0,0,0F);
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths,
                int candidatePanels,int selectedPanels,boolean locallyLimited,
                int liftedHemPanels,float liftOverlapReduction,
                int midCandidatePanels,int jointLiftPanels,
                float midOverlapReduction)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    contactDepths,candidatePanels,selectedPanels,
                    locallyLimited,liftedHemPanels,liftOverlapReduction,
                    midCandidatePanels,jointLiftPanels,midOverlapReduction,
                    0,0);
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths,
                int candidatePanels,int selectedPanels,boolean locallyLimited,
                int liftedHemPanels,float liftOverlapReduction,
                int midCandidatePanels,int jointLiftPanels,
                float midOverlapReduction,
                int rescuedLegBoxes,int rejectedLegBoxes)
        {
            this(swings,touchedPanels,peakOverlap,exactSamples,exactHits,
                    contactDepths,candidatePanels,selectedPanels,
                    locallyLimited,liftedHemPanels,liftOverlapReduction,
                    midCandidatePanels,jointLiftPanels,midOverlapReduction,
                    rescuedLegBoxes,rejectedLegBoxes,0,0,0F);
        }
        Result(Map<String,float[]> swings,int touchedPanels,float peakOverlap,
                int exactSamples,int exactHits,
                Map<String,Float> contactDepths,
                int candidatePanels,int selectedPanels,boolean locallyLimited,
                int liftedHemPanels,float liftOverlapReduction,
                int midCandidatePanels,int jointLiftPanels,
                float midOverlapReduction,int rescuedLegBoxes,
                int rejectedLegBoxes,int silhouetteLimitedPanels,
                int silhouetteBlockedPanels,float silhouetteRemovedOutward)
        {
            this.silhouetteLimitedPanels=silhouetteLimitedPanels;
            this.silhouetteBlockedPanels=silhouetteBlockedPanels;
            this.silhouetteRemovedOutward=silhouetteRemovedOutward;
            this.rescuedLegBoxes=rescuedLegBoxes;
            this.rejectedLegBoxes=rejectedLegBoxes;
            this.midCandidatePanels=midCandidatePanels;
            this.jointLiftPanels=jointLiftPanels;
            this.midOverlapReduction=midOverlapReduction;
            this.liftedHemPanels=liftedHemPanels;
            this.liftOverlapReduction=liftOverlapReduction;
            this.candidatePanels=candidatePanels;
            this.selectedPanels=selectedPanels;
            this.locallyLimited=locallyLimited;
            this.swings=swings;
            this.touchedPanels=touchedPanels;
            this.peakOverlap=peakOverlap;
            exactSurfaceSamples=exactSamples;
            exactSurfaceHits=exactHits;
            this.contactDepths=Collections.unmodifiableMap(
                    new LinkedHashMap<String,Float>(contactDepths));
        }
    }

    private static final class Leg
    {
        final float[] p;
        final int sourceIndex;
        final String owner;
        final float[][] basis=new float[3][];
        Leg(float[] p){this(p,-1,"unknown");}
        Leg(float[] p,int sourceIndex,String owner)
        {
            this.p=p;
            this.sourceIndex=sourceIndex;
            this.owner=owner;
            float[] q={p[3],p[4],p[5],p[6]};
            basis[0]=rotateQuaternion(new float[]{1,0,0},q);
            basis[1]=rotateQuaternion(new float[]{0,1,0},q);
            basis[2]=rotateQuaternion(new float[]{0,0,1},q);
        }
    }

    private static final class Point
    {
        final float[] p,axis;
        Point(float[] p,float[] axis){this.p=p;this.axis=axis;}
    }

    private SkirtVisibleThighContacts(){}

    public static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected)
    {
        return correct(strips,starting,selected,1F);
    }

    /**
     * The input matrices already include Global Transform scale. Do not
     * rescale leg boxes or skirt geometry: a compact garment instead needs
     * a slightly earlier LOCAL response against the unchanged body.
     */
    public static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected,float visualScale)
    {
        return correct(strips,starting,selected,visualScale,
                ADAPTIVE_HEM_LIFT,ADAPTIVE_MID_COLLISION,
                SURFACE_EVIDENCE_LEG_GATE,SILHOUETTE_PRESERVATION);
    }

    /** Package-private deterministic A/B seam test, without mutating
     * system properties or changing the class-loading order. */
    static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected,float visualScale,
            boolean adaptiveLift)
    {
        // Preserve the exact PR #186 isolated hem-lift A/B test.
        return correct(strips,starting,selected,visualScale,
                adaptiveLift,false);
    }

    /** Explicit pure Java A/B test without class-load system flags. */
    static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected,float visualScale,
            boolean adaptiveLift,boolean adaptiveMid)
    {
        return correct(strips,starting,selected,visualScale,
                adaptiveLift,adaptiveMid,SURFACE_EVIDENCE_LEG_GATE);
    }

    /** Pure Java regression switch; does not mutate process-wide flags. */
    static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected,float visualScale,
            boolean adaptiveLift,boolean adaptiveMid,boolean surfaceGate)
    {
        // Keep existing 7-argument collision regression mode unchanged.
        return correct(strips,starting,selected,visualScale,adaptiveLift,
                adaptiveMid,surfaceGate,false);
    }

    /** Pure A/B mode comparing the same Jolt sample to the silhouette
     * envelope without changing process-wide Java system properties. */
    static Result correct(List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> starting,
            BodyPartJoltContactBudget.Selection selected,float visualScale,
            boolean adaptiveLift,boolean adaptiveMid,boolean surfaceGate,
            boolean silhouette)
    {
        Map<String,float[]> copy=cloneSwings(starting);
        if(strips==null||strips.size()!=PANELS||selected==null||
                selected.boxes==null||selected.owners==null)
            return new Result(copy,0,0F);
        List<Leg> legs=new ArrayList<Leg>();
        for(int i=0;i<Math.min(selected.boxes.length,selected.owners.size());i++)
        {
            if(!BodyPartJoltContactBudget.isLeg(selected.owners.get(i)))continue;
            float[] box=selected.boxes[i];
            if(valid(box,10) && box[7]>.01F && box[8]>.01F && box[9]>.01F)
                legs.add(new Leg(box,i,selected.owners.get(i)));
        }
        if(legs.isEmpty())return new Result(copy,0,0F);
        if(REJECT_BROAD_LEG_BOXES)
        {
            // Filter ONLY shapes which individually cover most of the
            // skirt. Unlike the previous all-or-nothing guard, a local
            // genuine thigh box may still correct the front hem when
            // another incorrect/oversized OBB overlaps the entire ring.
            Audit coverage=HEM_ONLY_CONTACT?
                    auditHemBonePose(strips,copy,selected,visualScale):
                    auditBonePose(strips,copy,selected,visualScale);
            if(coverage.legBoxes==legs.size() &&
                    coverage.perLegPanels.length==legs.size())
            {
                List<Leg> local=new ArrayList<Leg>();
                for(int index=0;index<legs.size();index++)
                    if(coverage.perLegPanels[index]<=MAX_SINGLE_LEG_PANELS)
                        local.add(legs.get(index));
                if(!local.isEmpty())legs=local;
                else
                {
                    // Every actual collider is implausibly broad. We
                    // cannot choose a meaningful LOCAL leg contact: retain
                    // the original umbrella safety and unchanged pose.
                    return new Result(copy,0,0F);
                }
            }
        }
        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip s:strips)
        {
            if(s==null||s.tipWorld==null||!valid(s.tipWorld,3)||
                    !valid(s.upperWorld,16)||!valid(s.lowerWorld,16)||
                    !valid(copy.get(s.upper),4)||!valid(copy.get(s.lower),4)||
                    SkirtAdaptiveWidth.widthAxis(s.lower)<0)
                return new Result(copy,0,0F);
            cx+=s.upperWorld[12];
            cz+=s.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;

        // A stationary thigh lives UNDER the waist. Its approximate hull
        // can intersect the inner cloth surface even in a neutral stance.
        // A lifted thigh can extend THROUGH the skirt while its BONE CENTER
        // remains near the pelvis: use the entire fitted collider envelope
        // rather than the center-only test. Only then run actual OBB overlap
        // against the lower hem sample points.
        float waistRadius=0F;
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            float[] root=origin(strip.upperWorld);
            float x=root[0]-cx,z=root[2]-cz;
            waistRadius+=(float)Math.sqrt(x*x+z*z);
        }
        waistRadius/=PANELS;
        List<Leg> active=new ArrayList<Leg>();
        int rescuedLegBoxes=0,rejectedLegBoxes=0;
        // Several OBBs are fitted to the SAME Emoticons thigh (three
        // near-identical proxies per leg in the user's gait log). Only
        // one previously rejected representative per leg side can be
        // rescued. Existing old-gate pass-throughs remain unchanged.
        Leg[] fallbackBest=new Leg[SkirtHemContactIslands.GROUPS];
        float[] fallbackReach=new float[SkirtHemContactIslands.GROUPS];
        java.util.Arrays.fill(fallbackReach,-Float.MAX_VALUE);
        float evidenceMargin=SkirtScaleContactResponse.surfaceMargin(visualScale);
        for(Leg leg:legs)
        {
            float x=leg.p[0]-cx,z=leg.p[2]-cz;
            float distance=(float)Math.sqrt(x*x+z*z);
            // Project all three oriented half-extents onto the outward
            // horizontal direction, not only the collider center. This
            // handles rotated thighs whose broad corner reaches the skirt.
            float[] radial=distance>.0001F?
                    new float[]{x/distance,0F,z/distance}:
                    new float[]{1F,0F,0F};
            float support=0F;
            for(int axis=0;axis<3;axis++)
                support+=Math.abs(dot(radial,leg.basis[axis]))*
                        leg.p[7+axis];
            // A broad but VERTICAL neutral thigh can graze the short
            // skirt edge. Do not apply a resting outward force all around
            // the waist; require either outward-moving center or a thigh
            // whose longest fitted axis visibly tilts off vertical.
            int longest=0;
            if(leg.p[8]>leg.p[7])longest=1;
            if(leg.p[9]>leg.p[7+longest])longest=2;
            float tilted=(float)Math.sqrt(
                    leg.basis[longest][0]*leg.basis[longest][0]+
                    leg.basis[longest][2]*leg.basis[longest][2]);
            boolean raised=tilted>=.30F;
            boolean movedOut=distance>=waistRadius*.68F;
            boolean oldGate=distance+support>=
                    Math.max(.09F,waistRadius*LEG_OUTWARD_THRESHOLD) &&
                    (raised||movedOut);
            if(oldGate)
                active.add(leg);
            else if(surfaceGate && visualScale<.995F &&
                    // Merely sitting under the tight .85 skirt is not
                    // evidence of visible breakthrough. The fitted
                    // oriented leg must cross the natural waist envelope.
                    distance+support>=waistRadius+RESCUE_OUTER_SURPLUS &&
                    deepOuterFabricContact(strips,copy,leg,cx,cz,
                            evidenceMargin))
            {
                int side=SkirtHemContactIslands.sideOf(leg.owner);
                float reach=distance+support;
                if(fallbackBest[side]==null ||
                        reach>fallbackReach[side]+.0001F ||
                        (Math.abs(reach-fallbackReach[side])<=.0001F &&
                         leg.owner.compareTo(fallbackBest[side].owner)<0))
                {
                    fallbackBest[side]=leg;
                    fallbackReach[side]=reach;
                }
                else rejectedLegBoxes++;
            }
            else rejectedLegBoxes++;
        }
        for(Leg best:fallbackBest)
            if(best!=null)
            {
                active.add(best);
                rescuedLegBoxes++;
            }
        if(active.isEmpty())
            return new Result(copy,0,0F,0,0,
                    Collections.<String,Float>emptyMap(),0,0,false,
                    0,0F,0,0,0F,0,rejectedLegBoxes);

        boolean[] touched=new boolean[PANELS];
        float peak=0F;
        int exactSampleCount=0,exactHitCount=0;
        float[] need=new float[PANELS];
        float[] directDepth=new float[PANELS];
        float[][] sideNeed=new float[SkirtHemContactIslands.GROUPS][PANELS];
        float[][] sideDepth=new float[SkirtHemContactIslands.GROUPS][PANELS];
        float surfaceMargin=SkirtScaleContactResponse.surfaceMargin(visualScale);
        float angleGain=SkirtScaleContactResponse.angleGain(visualScale);
        // Evaluate ONLY lower-fabric contact. Upper samples near the waist
        // used to treat the resting hips as a penetration and push outward
        // the entire skirt. A single pass prevents repeated amplifying
        // corrections from the same stationary overlapping collider.
        for(int i=0;i<PANELS;i++)
        {
            SkirtClothPoseCoupler.Strip strip=strips.get(i);
            float[] root=origin(strip.upperWorld);
            float rx=root[0]-cx,rz=root[2]-cz;
            float rr=(float)Math.sqrt(rx*rx+rz*rz);
            if(rr<.02F)continue;
            float[] outward={rx/rr,0F,rz/rr};
            float[] hinge=origin(strip.lowerWorld);
            float[] upperSwing=worldSwing(strip.upperWorld,
                    copy.get(strip.upper));
            float[] lowerSwing=worldSwing(strip.lowerWorld,
                    copy.get(strip.lower));
            float[] upper=rotate(sub(hinge,root),upperSwing);
            float[] lower=rotate(rotate(sub(strip.tipWorld,hinge),
                    lowerSwing),upperSwing);
            float[] pivot=add(root,upper);
            float[] hemAxis=rotate(rotate(
                    widthAxisWorld(strip.lowerWorld,strip.lower),
                    lowerSwing),upperSwing);
            // Contact the actual rotated front/side/diagonal cube surfaces.
            // A hardcoded 2px bone-centered strip misses 45-degree corner
            // cuboids and their face centers (including under Weld). Ignore
            // the top 28% around the fitted waist, which must stay pinned.
            List<Point> samples=new ArrayList<Point>();
            if(!HEM_ONLY_CONTACT)
                exactSampleCount+=addExactSurface(samples,
                        strip.upperSurfaceWorld,root,hinge,
                        strip.tipWorld,upperSwing,lowerSwing,false);
            exactSampleCount+=addExactSurface(samples,
                    strip.lowerSurfaceWorld,root,hinge,
                    strip.tipWorld,upperSwing,lowerSwing,true,
                    HEM_ONLY_CONTACT?HEM_CONTACT_FROM:.28F);
            // Keep the original thin centerline fallback for cubes without
            // a readable Chameleon surface or missing/hidden face quads.
            if(!HEM_ONLY_CONTACT)
                samples.add(new Point(add(pivot,mul(lower,.55F)),hemAxis));
            samples.add(new Point(add(pivot,mul(lower,.80F)),hemAxis));
            samples.add(new Point(add(pivot,lower),hemAxis));
            float desired=0F;
            for(Point sample:samples)
            {
                boolean exact=sample.axis==null;
                float[] side=exact?new float[]{0F,0F,0F}:
                        mul(normalize(sample.axis),WIDTH);
                int edgeLimit=exact?0:1;
                for(int edge=-edgeLimit;edge<=edgeLimit;edge++)
                {
                    float[] point=add(sample.p,mul(side,edge));
                    for(Leg leg:active)
                    {
                        float overlap=outwardPenetration(
                                point,leg,outward,surfaceMargin);
                        if(overlap>0F)
                        {
                            if(exact)exactHitCount++;
                            int legSide=SkirtHemContactIslands.sideOf(leg.owner);
                            directDepth[i]=Math.max(directDepth[i],overlap);
                            sideDepth[legSide][i]=Math.max(
                                    sideDepth[legSide][i],overlap);
                            peak=Math.max(peak,overlap);
                            float distance=Math.max(.14F,
                                    length(sub(sample.p,pivot)));
                            float demand=(float)Math.atan(overlap/distance);
                            desired=Math.max(desired,demand);
                            sideNeed[legSide][i]=Math.max(
                                    sideNeed[legSide][i],Math.min(.34F,demand));
                        }
                    }
                }
            }
            need[i]=Math.min(.34F,desired);
        }

        int candidatePanels=0;
        for(float depth:directDepth)if(depth>.001F)candidatePanels++;
        boolean limited=false;
        if(candidatePanels>12)
        {
            if(!LOCAL_CONTACT_ISLANDS)
                return new Result(copy,0,peak,exactSampleCount,exactHitCount,
                        Collections.<String,Float>emptyMap(),
                        candidatePanels,0,false);
            // The two legitimate thighs can each overlap 8-10 panels:
            // together they cross the old 12/20 guard. Keep up to five
            // adjacent REAL contact strips per thigh, instead of
            // treating the union as one malformed 20-panel collider.
            String[] ids=new String[PANELS];
            float[][] roots=new float[PANELS][];
            for(int i=0;i<PANELS;i++)
            {
                ids[i]=strips.get(i).upper;
                roots[i]=origin(strips.get(i).upperWorld);
            }
            SkirtHemContactIslands.Decision islands=
                    SkirtHemContactIslands.choose(ids,roots,
                            sideNeed,sideDepth,12);
            if(islands==null||islands.keptPanels==0 ||
                    islands.keptPanels>12)
                return new Result(copy,0,peak,exactSampleCount,exactHitCount,
                        Collections.<String,Float>emptyMap(),
                        candidatePanels,0,false);
            for(int i=0;i<PANELS;i++)
                if(!islands.keep[i])
                {
                    need[i]=0F;
                    directDepth[i]=0F;
                }
            limited=true;
        }
        Map<String,Float> directContacts=
                new LinkedHashMap<String,Float>();
        for(int i=0;i<PANELS;i++)
            if(directDepth[i]>.001F)
                directContacts.put(strips.get(i).upper,
                        Float.valueOf(directDepth[i]));

        // The geometry profile's bone enumeration is NOT guaranteed to
        // run in angular order. Spread pressure to true physical neighbors
        // around the waist rather than the next entries in the bone list.
        java.util.List<Integer> angular=new java.util.ArrayList<Integer>();
        for(int i=0;i<PANELS;i++)angular.add(Integer.valueOf(i));
        final float centerX=cx,centerZ=cz;
        java.util.Collections.sort(angular,new java.util.Comparator<Integer>()
        {
            @Override public int compare(Integer ai,Integer bi)
            {
                SkirtClothPoseCoupler.Strip a=strips.get(ai.intValue());
                SkirtClothPoseCoupler.Strip b=strips.get(bi.intValue());
                double angleA=Math.atan2(a.upperWorld[14]-centerZ,
                        a.upperWorld[12]-centerX);
                double angleB=Math.atan2(b.upperWorld[14]-centerZ,
                        b.upperWorld[12]-centerX);
                int cmp=Double.compare(angleA,angleB);
                return cmp!=0?cmp:a.upper.compareTo(b.upper);
            }
        });
        float[] neighborhood=new float[PANELS];
        for(int ringIndex=0;ringIndex<PANELS;ringIndex++)
        {
            int panel=angular.get(ringIndex).intValue();
            int prev=angular.get((ringIndex+PANELS-1)%PANELS).intValue();
            int next=angular.get((ringIndex+1)%PANELS).intValue();
            neighborhood[panel]=Math.max(need[panel],Math.max(
                    need[prev]*NEIGHBOR_RESPONSE,
                    need[next]*NEIGHBOR_RESPONSE));
        }
        for(int i=0;i<PANELS;i++)
        {
            float contact=neighborhood[i];
            if(contact<.012F)continue;
            SkirtClothPoseCoupler.Strip strip=strips.get(i);
            float[] root=origin(strip.upperWorld);
            float rx=root[0]-cx,rz=root[2]-cz;
            float rr=(float)Math.sqrt(rx*rx+rz*rz);
            if(rr<.02F)continue;
            float[] tangent={-rz/rr,0F,rx/rr};

            // Preserve the natural waist silhouette: <6 degrees extra at
            // the root; the lower link bends by up to 16 degrees instead.
            float upperStep=Math.min(MAX_EXTRA_ROOT,
                    contact*.28F*angleGain);
            float lowerStep=Math.min(MAX_EXTRA_HEM,
                    contact*.85F*angleGain);
            boolean changed=false;
            changed|=bend(copy,strip.upper,strip.upperWorld,tangent,
                    upperStep,MAX_FINAL_ROOT);
            changed|=bend(copy,strip.lower,strip.lowerWorld,tangent,
                    lowerStep,SkirtScaleContactResponse.hemAngleCap(visualScale));
            touched[i]=changed;
        }
        int additionalLifts=0;
        float overlapReduction=0F;
        // Local post-Jolt relief: only on a compact skirt, after the
        // normal two-leg safety selection AND the normal bounded bend.
        // Never raise the whole ring merely because a thigh is nearby.
        if(adaptiveLift && visualScale<.995F &&
                !directContacts.isEmpty())
        {
            String[] ids=new String[PANELS];
            float[][] roots=new float[PANELS][];
            boolean[] selectedPanel=new boolean[PANELS];
            for(int i=0;i<PANELS;i++)
            {
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                ids[i]=strip.upper;
                roots[i]=origin(strip.upperWorld);
                selectedPanel[i]=directDepth[i]>.001F;
            }
            float[] extra=SkirtAdaptiveHemLift.weights(
                    ids,roots,selectedPanel,sideNeed,sideDepth);
            for(int i=0;i<PANELS;i++)
            {
                if(extra[i]<=.01F)continue;
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                float[] root=roots[i];
                float rx=root[0]-cx,rz=root[2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                float[] tangent={-rz/rr,0F,rx/rr};
                float[] outward={rx/rr,0F,rz/rr};
                float baseline=hemResidualScore(strip,copy,active,
                        outward,surfaceMargin);
                if(baseline<=.001F)continue;

                float[] original=copy.get(strip.lower);
                float[] best=null;
                float bestScore=baseline;
                float cap=SkirtAdaptiveHemLift.collisionCap(
                        SkirtScaleContactResponse.hemAngleCap(visualScale),
                        extra[i]);
                float budget=SkirtAdaptiveHemLift.extraBudget(extra[i]);
                // Search for the LEAST aggressive trial that actually
                // reduces remaining geometric penetration. A flat/deep
                // overlap may not improve at the first few increments,
                // so inspect the full safe budget before deciding.
                for(float step=SkirtAdaptiveHemLift.STEP;
                        step<=budget+.0001F;
                        step+=SkirtAdaptiveHemLift.STEP)
                {
                    Map<String,float[]> trial=new LinkedHashMap<String,float[]>();
                    trial.put(strip.upper,copy.get(strip.upper));
                    trial.put(strip.lower,original.clone());
                    if(!bend(trial,strip.lower,strip.lowerWorld,tangent,
                            Math.min(step,budget),cap))continue;
                    float score=hemResidualScore(strip,trial,active,
                            outward,surfaceMargin);
                    if(score<bestScore-.0002F)
                    {
                        bestScore=score;
                        best=trial.get(strip.lower);
                        if(score<.002F)break;
                    }
                }
                // The cap's final fractional step must also be tested,
                // even when the 0.04 rad grid stops just below it.
                if(budget>0F)
                {
                    Map<String,float[]> trial=new LinkedHashMap<String,float[]>();
                    trial.put(strip.upper,copy.get(strip.upper));
                    trial.put(strip.lower,original.clone());
                    if(bend(trial,strip.lower,strip.lowerWorld,tangent,
                            budget,cap))
                    {
                        float score=hemResidualScore(strip,trial,active,
                                outward,surfaceMargin);
                        if(score<bestScore-.0002F)
                        {
                            bestScore=score;
                            best=trial.get(strip.lower);
                        }
                    }
                }
                if(best!=null)
                {
                    copy.put(strip.lower,best);
                    touched[i]=true;
                    additionalLifts++;
                    overlapReduction+=baseline-bestScore;
                }
            }
        }
        int midCandidates=0,jointPanels=0;
        float midReduction=0F;
        if(adaptiveMid && visualScale<.995F &&
                !directContacts.isEmpty())
        {
            // Stage 2 addresses the real hinge-area surface that was
            // deliberately excluded from the hem-only contact solver.
            // Keep it strictly adjacent to an already accepted hem
            // contact: one stray waist/upper-thigh OBB cannot invent
            // a new full-ring selection.
            boolean[] nearDirect=new boolean[PANELS];
            for(int ring=0;ring<PANELS;ring++)
            {
                int panel=angular.get(ring).intValue();
                if(directDepth[panel]<=.001F)continue;
                for(int step=-2;step<=2;step++)
                    nearDirect[angular.get(
                            (ring+PANELS+step)%PANELS).intValue()]=true;
            }
            String[] ids=new String[PANELS];
            float[][] roots=new float[PANELS][];
            float[][] midPressure=
                    new float[SkirtHemContactIslands.GROUPS][PANELS];
            float[][] midDepth=
                    new float[SkirtHemContactIslands.GROUPS][PANELS];
            boolean[] midContact=new boolean[PANELS];
            for(int i=0;i<PANELS;i++)
            {
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                ids[i]=strip.upper;
                roots[i]=origin(strip.upperWorld);
                if(!nearDirect[i])continue;
                float rx=roots[i][0]-cx,rz=roots[i][2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                float[] outward={rx/rr,0F,rz/rr};
                for(int g=0;g<SkirtHemContactIslands.GROUPS;g++)
                {
                    float pressure=middleResidualScore(strip,copy,active,
                            outward,surfaceMargin,g);
                    if(pressure>.015F)
                    {
                        midContact[i]=true;
                        midPressure[g][i]=Math.min(.35F,pressure);
                        midDepth[g][i]=Math.min(.25F,pressure);
                    }
                }
                if(midContact[i])midCandidates++;
            }
            // Both legs may contact the same seam. Preserve one
            // three-panel angular corner per leg, not 20 unrelated
            // root rotations. This selection uses the SAME accepted
            // active colliders as the subsequent trial/validation.
            float[] jointWeights=SkirtAdaptiveHemLift.weights(
                    ids,roots,midContact,midPressure,midDepth);
            for(int i=0;i<PANELS;i++)
            {
                float weight=jointWeights[i];
                if(weight<.01F)continue;
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                float rx=roots[i][0]-cx,rz=roots[i][2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                float[] tangent={-rz/rr,0F,rx/rr};
                float[] outward={rx/rr,0F,rz/rr};
                float startMiddle=middleResidualScore(strip,copy,active,
                        outward,surfaceMargin,-1);
                if(startMiddle<=.015F)continue;
                float startHem=hemResidualScore(strip,copy,active,
                        outward,surfaceMargin);
                float[] upperOriginal=copy.get(strip.upper);
                float[] lowerOriginal=copy.get(strip.lower);
                float upperCap=Math.min(MAX_MID_ROOT_CAP,
                        MAX_FINAL_ROOT+MAX_EXTRA_MID_ROOT*weight);
                float lowerCap=SkirtAdaptiveHemLift.collisionCap(
                        SkirtScaleContactResponse.hemAngleCap(visualScale),
                        weight);
                float bestMiddle=startMiddle;
                float bestCombined=startMiddle+startHem;
                float[] bestUpper=null,bestLower=null;
                // A joint rather than a lower-only search. Preserve the
                // waist anchor: both rotations are around existing bone
                // pivots, never a translation or morph scale change.
                for(int up=1;up<=4;up++)
                {
                    float upperStep=MAX_EXTRA_MID_ROOT*weight*up/4F;
                    for(int down=0;down<=2;down++)
                    {
                        Map<String,float[]> trial=
                                new LinkedHashMap<String,float[]>();
                        trial.put(strip.upper,upperOriginal.clone());
                        trial.put(strip.lower,lowerOriginal.clone());
                        if(!bend(trial,strip.upper,strip.upperWorld,tangent,
                                upperStep,upperCap))continue;
                        if(down>0)
                        {
                            float lowerStep=.05F*down*weight;
                            if(!bend(trial,strip.lower,strip.lowerWorld,tangent,
                                    lowerStep,lowerCap))continue;
                        }
                        float mid=middleResidualScore(strip,trial,active,
                                outward,surfaceMargin,-1);
                        if(mid>=bestMiddle-.002F)continue;
                        float hem=hemResidualScore(strip,trial,active,
                                outward,surfaceMargin);
                        // Correcting the hinge is not allowed to move
                        // a thigh THROUGH the lower hem: at most a
                        // tiny numerical increase in the hem residual.
                        if(hem>startHem+Math.max(.01F,startHem*.025F))
                            continue;
                        float combined=mid+hem;
                        if(combined>=bestCombined-.003F)continue;
                        bestCombined=combined;
                        bestMiddle=mid;
                        bestUpper=trial.get(strip.upper);
                        bestLower=trial.get(strip.lower);
                    }
                }
                if(bestUpper!=null)
                {
                    copy.put(strip.upper,bestUpper);
                    copy.put(strip.lower,bestLower);
                    touched[i]=true;
                    jointPanels++;
                    midReduction+=startMiddle-bestMiddle;
                }
            }
        }
        int silhouetteLimited=0,silhouetteBlocked=0;
        float silhouetteRemoved=0F;
        if(silhouette && visualScale<.995F &&
                !directContacts.isEmpty())
        {
            // Unlike merely capping all bone angles, this measures how
            // far the CORRECTED rendered skirt hem actually opens relative
            // to the natural, Jolt-coupled pose of this exact frame.
            // Native motion may already flare for physical reasons and
            // must not be "fixed" by a visual-only contact stage.
            String[] ids=new String[PANELS];
            float[][] roots=new float[PANELS][];
            boolean[] selectedPanel=new boolean[PANELS];
            for(int i=0;i<PANELS;i++)
            {
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                ids[i]=strip.upper;
                roots[i]=origin(strip.upperWorld);
                selectedPanel[i]=directDepth[i]>.001F;
            }
            float[] importance=SkirtAdaptiveHemLift.weights(
                    ids,roots,selectedPanel,sideNeed,sideDepth);
            for(int i=0;i<PANELS;i++)
            {
                SkirtClothPoseCoupler.Strip strip=strips.get(i);
                float rx=roots[i][0]-cx,rz=roots[i][2]-cz;
                float rr=(float)Math.sqrt(rx*rx+rz*rz);
                if(rr<.02F)continue;
                float[] outward={rx/rr,0F,rz/rr};
                float displacement=skirtShapeExcursion(
                        strip,starting,copy,outward);
                float maxAllowed=
                        SkirtSilhouettePreservation.maxAdditionalOutward(
                                importance[i]);
                if(displacement<=maxAllowed+.0005F)continue;
                float low=0F,high=1F;
                Map<String,float[]> trial=
                        new LinkedHashMap<String,float[]>();
                for(int step=0;step<11;step++)
                {
                    float alpha=(low+high)*.5F;
                    trial.put(strip.upper,blendSwing(
                            starting.get(strip.upper),
                            copy.get(strip.upper),alpha));
                    trial.put(strip.lower,blendSwing(
                            starting.get(strip.lower),
                            copy.get(strip.lower),alpha));
                    float candidate=skirtShapeExcursion(
                            strip,starting,trial,outward);
                    if(candidate<=maxAllowed)low=alpha;
                    else high=alpha;
                }
                trial.put(strip.upper,blendSwing(
                        starting.get(strip.upper),
                        copy.get(strip.upper),low));
                trial.put(strip.lower,blendSwing(
                        starting.get(strip.lower),
                        copy.get(strip.lower),low));
                float naturalCollision=panelCollisionScore(
                        strip,starting,active,outward,surfaceMargin);
                float finalCollision=panelCollisionScore(
                        strip,copy,active,outward,surfaceMargin);
                float trialCollision=panelCollisionScore(
                        strip,trial,active,outward,surfaceMargin);
                // Retain at least 68% of the clearance already achieved
                // by the hem+middle collision correction on this panel.
                // If no feasible beautiful pose can do so, keep the
                // collision-safe pose and explicitly count the overrun.
                if(!SkirtSilhouettePreservation.keepsClearance(
                        naturalCollision,finalCollision,trialCollision))
                {
                    silhouetteBlocked++;
                    continue;
                }
                float afterDistance=skirtShapeExcursion(
                        strip,starting,trial,outward);
                if(afterDistance>=displacement-.0005F)continue;
                copy.put(strip.upper,trial.get(strip.upper));
                copy.put(strip.lower,trial.get(strip.lower));
                silhouetteLimited++;
                silhouetteRemoved+=displacement-afterDistance;
            }
        }
        int total=0;
        for(boolean value:touched)if(value)total++;
        return new Result(copy,total,peak,exactSampleCount,exactHitCount,
                directContacts,candidatePanels,directContacts.size(),limited,
                additionalLifts,overlapReduction,
                midCandidates,jointPanels,midReduction,
                rescuedLegBoxes,rejectedLegBoxes,
                silhouetteLimited,silhouetteBlocked,silhouetteRemoved);
    }

    /**
     * Diagnostic read-back of the POST-correction bone pose against the same
     * captured Emoticons leg OBBs. This deliberately does NOT use the
     * raised-leg prefilter or 12-panel rejection guard. Comparing a
     * pre-correction and post-correction Audit exposes false OBBs, clipped
     * angular response, and the old all-panels safety abort.
     *
     * Samples use clean decoded Chameleon surface geometry transformed
     * by the requested 40-bone pose. They are NOT the final rasterized
     * Weld/width-modified GPU vertices; the printed metric is labeled
     * 'bonePose' to avoid confusing it with a solved cloth mesh.
     */
    public static final class Audit
    {
        public final int sourcePoints,uniqueHits,rawHits,hitPanels,legBoxes;
        public final float maxOverlap;
        public final String dominantOwner;
        /** Number of distinct panels overlapped by each captured leg OBB,
         * in the same order as the accepted selected leg boxes. */
        public final int[] perLegPanels;
        /** Source fit of each leg OBB, sorted by the number of touched
         * panels. Outputs are diagnostics only; never alter Jolt contacts. */
        public final String perLegCoverage;
        Audit(int sourcePoints,int uniqueHits,int rawHits,int hitPanels,
                int legBoxes,float maxOverlap,String dominantOwner)
        {
            this(sourcePoints,uniqueHits,rawHits,hitPanels,legBoxes,
                    maxOverlap,dominantOwner,new int[0],"");
        }
        Audit(int sourcePoints,int uniqueHits,int rawHits,int hitPanels,
                int legBoxes,float maxOverlap,String dominantOwner,
                int[] perLegPanels,String perLegCoverage)
        {
            this.sourcePoints=sourcePoints;
            this.uniqueHits=uniqueHits;
            this.rawHits=rawHits;
            this.hitPanels=hitPanels;
            this.legBoxes=legBoxes;
            this.maxOverlap=maxOverlap;
            this.dominantOwner=dominantOwner;
            this.perLegPanels=perLegPanels.clone();
            this.perLegCoverage=perLegCoverage;
        }
    }

    public static Audit auditBonePose(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> swings,
            BodyPartJoltContactBudget.Selection selected,
            float visualScale)
    {
        return auditPose(strips,swings,selected,visualScale,false);
    }

    /** Contact-only readback excludes the waist/upper skirt surfaces,
     * while auditBonePose retains its original full 860-point semantics
     * for backwards-compatible diagnostics. */
    public static Audit auditHemBonePose(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> swings,
            BodyPartJoltContactBudget.Selection selected,
            float visualScale)
    {
        return auditPose(strips,swings,selected,visualScale,true);
    }

    private static Audit auditPose(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> swings,
            BodyPartJoltContactBudget.Selection selected,
            float visualScale,boolean hemOnly)
    {
        Audit invalid=new Audit(0,0,0,0,0,0F,"none");
        if(strips==null||strips.size()!=PANELS||swings==null ||
                selected==null||selected.boxes==null ||
                selected.owners==null)return invalid;
        List<Leg> legs=new ArrayList<Leg>();
        List<String> ownerNames=new ArrayList<String>();
        for(int index=0;index<Math.min(selected.boxes.length,
                selected.owners.size());index++)
        {
            String owner=selected.owners.get(index);
            float[] box=selected.boxes[index];
            if(BodyPartJoltContactBudget.isLeg(owner)&&valid(box,10)&&
                    box[7]>.01F&&box[8]>.01F&&box[9]>.01F)
            {
                legs.add(new Leg(box,index,owner));
                ownerNames.add(owner+"#"+index);
            }
        }
        if(legs.isEmpty())
            return new Audit(0,0,0,0,0,0F,"none");
        float cx=0F,cz=0F;
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            if(strip==null||!valid(strip.upperWorld,16)||
                    !valid(strip.lowerWorld,16)||
                    !valid(strip.tipWorld,3)||
                    !valid(swings.get(strip.upper),4)||
                    !valid(swings.get(strip.lower),4))return invalid;
            cx+=strip.upperWorld[12];
            cz+=strip.upperWorld[14];
        }
        cx/=PANELS;cz/=PANELS;
        int sources=0,unique=0,raw=0,affected=0;
        int[] hitsByLeg=new int[legs.size()];
        int[] panelsByLeg=new int[legs.size()];
        float maxOverlap=0F;
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            float[] root=origin(strip.upperWorld);
            float[] hinge=origin(strip.lowerWorld);
            float[] upper=worldSwing(strip.upperWorld,
                    swings.get(strip.upper));
            float[] lower=worldSwing(strip.lowerWorld,
                    swings.get(strip.lower));
            float dx=root[0]-cx,dz=root[2]-cz;
            float rr=(float)Math.sqrt(dx*dx+dz*dz);
            if(rr<.02F)continue;
            float[] outward={dx/rr,0F,dz/rr};
            List<Point> points=new ArrayList<Point>();
            int exact=0;
            if(!hemOnly)
                exact+=addExactSurface(points,strip.upperSurfaceWorld,
                        root,hinge,strip.tipWorld,upper,lower,false);
            exact+=addExactSurface(points,strip.lowerSurfaceWorld,
                    root,hinge,strip.tipWorld,upper,lower,true,
                    hemOnly?HEM_CONTACT_FROM:.28F);
            // Diagnostic remains meaningful for missing decoded cuboids.
            if(exact==0)
            {
                float[] first=rotate(sub(hinge,root),upper);
                float[] second=rotate(rotate(sub(strip.tipWorld,hinge),
                        lower),upper);
                float[] joint=add(root,first);
                if(!hemOnly)
                    points.add(new Point(add(joint,mul(second,.5F)),null));
                points.add(new Point(add(joint,mul(second,.8F)),null));
                points.add(new Point(add(joint,second),null));
            }
            boolean panelContact=false;
            boolean[] shapeTouchesPanel=new boolean[legs.size()];
            for(Point p:points)
            {
                sources++;
                boolean sampleContact=false;
                for(int i=0;i<legs.size();i++)
                {
                    float overlap=outwardPenetration(p.p,legs.get(i),
                            outward,SkirtScaleContactResponse.surfaceMargin(
                                    visualScale));
                    if(overlap>0F)
                    {
                        raw++;
                        hitsByLeg[i]++;
                        shapeTouchesPanel[i]=true;
                        sampleContact=true;
                        panelContact=true;
                        maxOverlap=Math.max(maxOverlap,overlap);
                    }
                }
                if(sampleContact)unique++;
            }
            if(panelContact)affected++;
            for(int boxIndex=0;boxIndex<legs.size();boxIndex++)
                if(shapeTouchesPanel[boxIndex])panelsByLeg[boxIndex]++;
        }
        int primary=-1;
        for(int i=0;i<hitsByLeg.length;i++)
            if(primary<0 || hitsByLeg[i]>hitsByLeg[primary])primary=i;
        String source=primary<0?"none":
                ownerNames.get(primary)+"("+hitsByLeg[primary]+")";
        return new Audit(sources,unique,raw,affected,legs.size(),
                maxOverlap,source,panelsByLeg,
                describeLegCoverage(legs,hitsByLeg,panelsByLeg,cx,cz,strips));
    }

    /**
     * Compact shape-level coverage to identify malformed or misaligned
     * Emoticons parent hulls. One badly fitted leg OBB can otherwise mark
     * the whole 20-panel skirt as inside the body.
     */
    private static String describeLegCoverage(List<Leg> legs,int[] hits,
            int[] panels,float cx,float cz,
            List<SkirtClothPoseCoupler.Strip> strips)
    {
        float cy=0F;
        for(SkirtClothPoseCoupler.Strip strip:strips)
            cy+=strip.upperWorld[13];
        cy/=PANELS;
        List<Integer> order=new ArrayList<Integer>();
        for(int i=0;i<legs.size();i++)order.add(Integer.valueOf(i));
        Collections.sort(order,new java.util.Comparator<Integer>()
        {
            @Override public int compare(Integer a,Integer b)
            {
                int panelsDiff=panels[b.intValue()]-panels[a.intValue()];
                return panelsDiff!=0?panelsDiff:hits[b.intValue()]-hits[a.intValue()];
            }
        });
        StringBuilder summary=new StringBuilder();
        for(int k=0;k<order.size();k++)
        {
            int i=order.get(k).intValue();
            Leg leg=legs.get(i);
            if(k>0)summary.append(';');
            summary.append(leg.owner).append('#').append(leg.sourceIndex)
                    .append("(panels=").append(panels[i])
                    .append(",hits=").append(hits[i])
                    .append(",dxyz=").append(round(leg.p[0]-cx))
                    .append('/').append(round(leg.p[1]-cy))
                    .append('/').append(round(leg.p[2]-cz))
                    .append(",half=").append(round(leg.p[7]))
                    .append('/').append(round(leg.p[8]))
                    .append('/').append(round(leg.p[9]))
                    .append(')');
        }
        return summary.toString();
    }

    private static float round(float value)
    {return Math.round(value*1000F)/1000F;}

    /**
     * Transform decoded CLEAN quad surface vertices by the same two
     * world-space native bone swings as the Chameleon panel. Upper bone
     * surfaces follow its root swing; lower bone surfaces additionally
     * follow the lower swing around the animated hinge. This avoids
     * treating cube geometry as if it were aligned to the bone axis.
     */
    private static int addExactSurface(List<Point> out,float[][] source,
            float[] root,float[] hinge,float[] tip,
            float[] upperSwing,float[] lowerSwing,boolean lower)
    {
        return addExactSurface(out,source,root,hinge,tip,
                upperSwing,lowerSwing,lower,.28F);
    }

    private static int addExactSurface(List<Point> out,float[][] source,
            float[] root,float[] hinge,float[] tip,
            float[] upperSwing,float[] lowerSwing,boolean lower,
            float minFraction)
    {
        return addExactSurface(out,source,root,hinge,tip,upperSwing,
                lowerSwing,lower,minFraction,1.45F);
    }

    private static int addExactSurface(List<Point> out,float[][] source,
            float[] root,float[] hinge,float[] tip,
            float[] upperSwing,float[] lowerSwing,boolean lower,
            float minFraction,float maxFraction)
    {
        if(source==null||source.length==0||source.length>128)return 0;
        float[] down=sub(tip,root);
        float length=length(down);
        if(length<.05F)return 0;
        float[] direction=mul(down,1F/length);
        int n=0;
        for(float[] p:source)
        {
            if(!valid(p,3))continue;
            float fraction=dot(sub(p,root),direction)/length;
            if(fraction<minFraction || fraction>maxFraction)continue;
            float[] world;
            if(lower)
            {
                float[] inner=rotate(sub(p,hinge),lowerSwing);
                world=add(root,rotate(add(sub(hinge,root),inner),
                        upperSwing));
            }
            else world=add(root,rotate(sub(p,root),upperSwing));
            if(!valid(world,3))continue;
            out.add(new Point(world,null));
            n++;
        }
        return n;
    }

    /**
     * Conservative proof that an old center/tilt-rejected leg truly
     * penetrates the OUTWARD-facing visible skirt, not just inner
     * surfaces beside the pelvis. Only transformed, decoded Chameleon
     * cuboid points from the middle (40-70%) or hem (68%+) count;
     * fallback centerline probes and upper waist samples DO NOT count.
     *
     * Require two sufficiently deep surface points in one skirt panel.
     * The outer 0.03-block radial band is computed PER region and
     * PER panel so back-facing inner cuboid faces are not evidence.
     * All coordinates and OBBs already share the captured world space.
     */
    private static boolean deepOuterFabricContact(
            List<SkirtClothPoseCoupler.Strip> strips,
            Map<String,float[]> swings,Leg leg,float cx,float cz,
            float margin)
    {
        for(SkirtClothPoseCoupler.Strip strip:strips)
        {
            float[] root=origin(strip.upperWorld);
            float rx=root[0]-cx,rz=root[2]-cz;
            float radius=(float)Math.sqrt(rx*rx+rz*rz);
            if(radius<.02F)continue;
            float[] outward={rx/radius,0F,rz/radius};
            float[] hinge=origin(strip.lowerWorld);
            float[] upper=worldSwing(strip.upperWorld,
                    swings.get(strip.upper));
            float[] lower=worldSwing(strip.lowerWorld,
                    swings.get(strip.lower));
            List<Point> middle=new ArrayList<Point>();
            addExactSurface(middle,strip.upperSurfaceWorld,
                    root,hinge,strip.tipWorld,upper,lower,
                    false,MID_CONTACT_START,MID_CONTACT_END);
            addExactSurface(middle,strip.lowerSurfaceWorld,
                    root,hinge,strip.tipWorld,upper,lower,
                    true,MID_CONTACT_START,MID_CONTACT_END);
            if(hasDeepOuterSurfacePoint(middle,leg,outward,margin))
                return true;
            List<Point> hem=new ArrayList<Point>();
            addExactSurface(hem,strip.lowerSurfaceWorld,
                    root,hinge,strip.tipWorld,upper,lower,
                    true,HEM_CONTACT_FROM);
            if(hasDeepOuterSurfacePoint(hem,leg,outward,margin))
                return true;
        }
        return false;
    }

    private static boolean hasDeepOuterSurfacePoint(
            List<Point> points,Leg leg,float[] outward,float margin)
    {
        if(points.size()<2)return false;
        float largest=-Float.MAX_VALUE;
        for(Point point:points)
            largest=Math.max(largest,dot(point.p,outward));
        float[] firstDeep=null;
        for(Point point:points)
        {
            if(dot(point.p,outward)<largest-OUTER_SURFACE_BAND)
                continue;
            float depth=unclampedPenetration(
                    point.p,leg,outward,margin);
            if(depth<SURFACE_EVIDENCE_DEPTH)continue;
            if(firstDeep==null)
                firstDeep=point.p;
            else if(length(sub(point.p,firstDeep))>.012F)
                return true;
            // Chameleon stores vertices shared by multiple cuboid faces.
            // Two copies of the SAME vertex must not masquerade as two
            // distinct contact witnesses and reopen the umbrella failure.
        }
        return false;
    }

    /**
     * Collision between the thigh and the middle of a two-link panel,
     * 40–70% of the full belt-to-hem length. The zone deliberately
     * excludes the upper resting waist: broad standing thigh boxes
     * naturally sit behind the top of the garment.
     *
     * Reads only the same accepted native-fitted Emoticons leg OBBs
     * used by the regular corrective pass. A given sampled surface point
     * contributes its deepest overlap just once, not three times for the
     * three near-identical fitted OBBs from one Emoticons thigh.
     * No results from the unfiltered, diagnostic-only audit are fed back
     * into the solver, so discovery and validation share one geometry set.
     */
    private static float middleResidualScore(
            SkirtClothPoseCoupler.Strip strip,
            Map<String,float[]> swings,List<Leg> legs,
            float[] outward,float margin,int side)
    {
        float[] root=origin(strip.upperWorld);
        float[] hinge=origin(strip.lowerWorld);
        float[] upper=worldSwing(strip.upperWorld,
                swings.get(strip.upper));
        float[] lower=worldSwing(strip.lowerWorld,
                swings.get(strip.lower));
        List<Point> samples=new ArrayList<Point>();
        int exact=addExactSurface(samples,strip.upperSurfaceWorld,
                root,hinge,strip.tipWorld,upper,lower,false,
                MID_CONTACT_START,MID_CONTACT_END);
        exact+=addExactSurface(samples,strip.lowerSurfaceWorld,
                root,hinge,strip.tipWorld,upper,lower,true,
                MID_CONTACT_START,MID_CONTACT_END);
        // In a normal Chameleon model these exact points are present.
        // Only a wholly missing source gets the centerline fallback.
        // If real source geometry exists but is ABOVE the middle zone,
        // do not replace it with imaginary collider points.
        boolean hasUpper=strip.upperSurfaceWorld!=null &&
                strip.upperSurfaceWorld.length>0;
        boolean hasLower=strip.lowerSurfaceWorld!=null &&
                strip.lowerSurfaceWorld.length>0;
        if(exact==0&&!hasUpper&&!hasLower)
        {
            float[] upperLink=rotate(sub(hinge,root),upper);
            float[] lowerLink=rotate(rotate(sub(strip.tipWorld,hinge),
                    lower),upper);
            samples.add(new Point(add(root,mul(upperLink,.95F)),null));
            samples.add(new Point(add(add(root,upperLink),
                    mul(lowerLink,.27F)),null));
        }
        float score=0F;
        for(Point sample:samples)
        {
            float deepest=0F;
            for(Leg leg:legs)
            {
                if(side>=0 &&
                        SkirtHemContactIslands.sideOf(leg.owner)!=side)
                    continue;
                deepest=Math.max(deepest,unclampedPenetration(
                        sample.p,leg,outward,margin));
            }
            if(deepest>.0001F)
                score+=deepest+.006F;
        }
        return score;
    }

    /**
     * Read-back of the actual lower Chameleon cube after current bone
     * swings (not the Jolt central capsule). The objective includes an
     * uncapped radial clearance depth; the regular solver's 0.25-block
     * capped measurement can be flat during deep thigh penetration.
     * Only the previously accepted active leg OBBs contribute.
     */
    private static float hemResidualScore(SkirtClothPoseCoupler.Strip strip,
            Map<String,float[]> swings,List<Leg> legs,
            float[] outward,float surfaceMargin)
    {
        float[] root=origin(strip.upperWorld);
        float[] hinge=origin(strip.lowerWorld);
        float[] upper=worldSwing(strip.upperWorld,
                swings.get(strip.upper));
        float[] lower=worldSwing(strip.lowerWorld,
                swings.get(strip.lower));
        float[] first=rotate(sub(hinge,root),upper);
        float[] second=rotate(rotate(sub(strip.tipWorld,hinge),
                lower),upper);
        float[] pivot=add(root,first);
        float[] hemAxis=rotate(rotate(
                widthAxisWorld(strip.lowerWorld,strip.lower),
                lower),upper);
        List<Point> samples=new ArrayList<Point>();
        int exact=addExactSurface(samples,strip.lowerSurfaceWorld,
                root,hinge,strip.tipWorld,upper,lower,true,
                HEM_ONLY_CONTACT?HEM_CONTACT_FROM:.28F);
        if(exact==0)
        {
            samples.add(new Point(add(pivot,mul(second,.80F)),hemAxis));
            samples.add(new Point(add(pivot,second),hemAxis));
        }
        float score=0F;
        for(Point point:samples)
        {
            boolean exactPoint=point.axis==null;
            float[] side=exactPoint?new float[]{0F,0F,0F}:
                    mul(normalize(point.axis),WIDTH);
            int limit=exactPoint?0:1;
            for(int edge=-limit;edge<=limit;edge++)
            {
                float[] at=add(point.p,mul(side,edge));
                for(Leg leg:legs)
                {
                    float depth=unclampedPenetration(
                            at,leg,outward,surfaceMargin);
                    if(depth>.0001F)
                        score+=depth+.006F;
                }
            }
        }
        return score;
    }

    private static float unclampedPenetration(float[] point,Leg leg,
            float[] radial,float margin)
    {
        float[] d=sub(point,new float[]{leg.p[0],leg.p[1],leg.p[2]});
        for(int axis=0;axis<3;axis++)
            if(Math.abs(dot(d,leg.basis[axis]))>leg.p[7+axis]+margin)
                return 0F;
        float support=margin;
        for(int axis=0;axis<3;axis++)
            support+=Math.abs(dot(radial,leg.basis[axis]))*
                    leg.p[7+axis];
        return Math.max(0F,support-dot(d,radial));
    }

    /** Penetration scored against the SAME accepted fitted body boxes
     * used by the two contact solvers; never use global unfiltered audit
     * hits as an objective for a silhouette change. */
    private static float panelCollisionScore(
            SkirtClothPoseCoupler.Strip strip,Map<String,float[]> pose,
            List<Leg> active,float[] outward,float margin)
    {
        return hemResidualScore(strip,pose,active,outward,margin)+
                middleResidualScore(strip,pose,active,outward,margin,-1);
    }

    /** Visible displacement of the lower panel, relative to this
     * frame's naturally coupled Jolt pose. A radial push and an upward
     * lift can both produce the undesirable umbrella shape. */
    private static float skirtShapeExcursion(
            SkirtClothPoseCoupler.Strip strip,
            Map<String,float[]> natural,Map<String,float[]> corrected,
            float[] outward)
    {
        float[] naturalTip=transformedSkirtTip(strip,natural);
        float[] correctedTip=transformedSkirtTip(strip,corrected);
        float[] naturalHinge=transformedSkirtHinge(strip,natural);
        float[] correctedHinge=transformedSkirtHinge(strip,corrected);
        float hemOut=Math.max(0F,dot(
                sub(correctedTip,naturalTip),outward));
        float hingeOut=Math.max(0F,dot(
                sub(correctedHinge,naturalHinge),outward));
        float hemRise=Math.max(0F,correctedTip[1]-naturalTip[1]);
        return hemOut+.32F*hingeOut+.6F*hemRise;
    }

    private static float[] transformedSkirtHinge(
            SkirtClothPoseCoupler.Strip strip,Map<String,float[]> pose)
    {
        float[] root=origin(strip.upperWorld);
        float[] hinge=origin(strip.lowerWorld);
        float[] upper=worldSwing(strip.upperWorld,pose.get(strip.upper));
        return add(root,rotate(sub(hinge,root),upper));
    }

    private static float[] transformedSkirtTip(
            SkirtClothPoseCoupler.Strip strip,Map<String,float[]> pose)
    {
        float[] root=origin(strip.upperWorld);
        float[] hinge=origin(strip.lowerWorld);
        float[] upper=worldSwing(strip.upperWorld,pose.get(strip.upper));
        float[] lower=worldSwing(strip.lowerWorld,pose.get(strip.lower));
        float[] up=rotate(sub(hinge,root),upper);
        float[] down=rotate(rotate(sub(strip.tipWorld,hinge),
                lower),upper);
        return add(root,add(up,down));
    }

    /** Quaternion nlerp on LOCAL axis-angle swings avoids interpolating
     * unrelated rotation axes and remains deterministic on timeline
     * scrubbing. Exact endpoints preserve authored pose data. */
    private static float[] blendSwing(float[] natural,float[] corrected,
            float alpha)
    {
        if(alpha<=0F)return natural.clone();
        if(alpha>=1F)return corrected.clone();
        float[] a=quaternion(natural);
        float[] b=quaternion(corrected);
        float similarity=0F;
        for(int i=0;i<4;i++)similarity+=a[i]*b[i];
        if(similarity<0F)
            for(int i=0;i<4;i++)b[i]=-b[i];
        float[] q=new float[4];
        float norm=0F;
        for(int i=0;i<4;i++)
        {
            q[i]=a[i]*(1F-alpha)+b[i]*alpha;
            norm+=q[i]*q[i];
        }
        if(norm<1E-12F)return natural.clone();
        norm=(float)Math.sqrt(norm);
        for(int i=0;i<4;i++)q[i]/=norm;
        if(q[3]<0F)
            for(int i=0;i<4;i++)q[i]=-q[i];
        float axisLength=(float)Math.sqrt(q[0]*q[0]+
                q[1]*q[1]+q[2]*q[2]);
        if(axisLength<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{q[0]/axisLength,q[1]/axisLength,
                q[2]/axisLength,
                2F*(float)Math.atan2(axisLength,q[3])};
    }

    private static boolean bend(Map<String,float[]> swings,String id,
            float[] matrix,float[] tangent,float amount,float maximum)
    {
        if(amount<.003F)return false;
        float[] current=worldSwing(matrix,swings.get(id));
        float[] after=compose(new float[]{
                tangent[0],tangent[1],tangent[2],amount},current);
        if(after[3]>maximum)
        {
            // Use the remaining safe outward bend, not a full skip that
            // would leave an apparently random isolated unresponsive panel.
            float available=Math.max(0F,maximum-current[3]);
            if(available<.003F)return false;
            after=compose(new float[]{
                    tangent[0],tangent[1],tangent[2],
                    Math.min(available,amount)},current);
            if(after[3]>maximum+.0001F)return false;
        }
        float[] local=localSwing(matrix,after);
        if(!valid(local,4))return false;
        swings.put(id,local);
        return true;
    }

    /** A point must actually overlap the leg's FULL box. If it does,
     * request just enough *outward* motion to clear the far radial face. */
    private static float outwardPenetration(float[] point,Leg leg,
            float[] radial,float surfaceMargin)
    {
        float[] d=sub(point,new float[]{leg.p[0],leg.p[1],leg.p[2]});
        for(int k=0;k<3;k++)
            if(Math.abs(dot(d,leg.basis[k]))>leg.p[7+k]+surfaceMargin)
                return 0F;
        float support=surfaceMargin;
        for(int k=0;k<3;k++)
            support+=Math.abs(dot(radial,leg.basis[k]))*leg.p[7+k];
        return Math.max(0F,Math.min(.25F,support-dot(d,radial)));
    }

    private static float[] worldSwing(float[] m,float[] aa)
    {
        float x=m[0]*aa[0]+m[4]*aa[1]+m[8]*aa[2];
        float y=m[1]*aa[0]+m[5]*aa[1]+m[9]*aa[2];
        float z=m[2]*aa[0]+m[6]*aa[1]+m[10]*aa[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,aa[3]};
    }

    private static float[] localSwing(float[] m,float[] world)
    {
        float a=m[0],b=m[4],c=m[8],d=m[1],e=m[5],f=m[9],g=m[2],h=m[6],i=m[10];
        float det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Float.isFinite(det)||Math.abs(det)<1E-7F)return null;
        float inv=1F/det;
        float x=((e*i-f*h)*world[0]+(c*h-b*i)*world[1]+(b*f-c*e)*world[2])*inv;
        float y=((f*g-d*i)*world[0]+(a*i-c*g)*world[1]+(c*d-a*f)*world[2])*inv;
        float z=((d*h-e*g)*world[0]+(b*g-a*h)*world[1]+(a*e-b*d)*world[2])*inv;
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return null;
        return new float[]{x/n,y/n,z/n,world[3]};
    }

    private static float[] widthAxisWorld(float[] m,String bone)
    {
        int axis=SkirtAdaptiveWidth.widthAxis(bone);
        return axis==0?new float[]{m[0],m[1],m[2]}:
                new float[]{m[8],m[9],m[10]};
    }

    private static float[] compose(float[] extra,float[] original)
    {
        float[] a=quaternion(extra),b=quaternion(original);
        float x=a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1];
        float y=a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0];
        float z=a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3];
        float w=a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2];
        float n=(float)Math.sqrt(x*x+y*y+z*z);
        if(n<1E-7F)return new float[]{1F,0F,0F,0F};
        return new float[]{x/n,y/n,z/n,2F*(float)Math.atan2(n,Math.abs(w))};
    }

    private static float[] quaternion(float[] aa)
    {
        float h=aa[3]*.5F;
        float sin=(float)Math.sin(h),cos=(float)Math.cos(h);
        return new float[]{aa[0]*sin,aa[1]*sin,aa[2]*sin,cos};
    }

    private static float[] rotate(float[] vec,float[] aa)
    {
        float[] q=quaternion(aa);
        return rotateQuaternion(vec,q);
    }

    private static float[] rotateQuaternion(float[] v,float[] q)
    {
        float x=q[0],y=q[1],z=q[2],w=q[3];
        float tx=2F*(y*v[2]-z*v[1]);
        float ty=2F*(z*v[0]-x*v[2]);
        float tz=2F*(x*v[1]-y*v[0]);
        return new float[]{v[0]+w*tx+y*tz-z*ty,
                v[1]+w*ty+z*tx-x*tz,
                v[2]+w*tz+x*ty-y*tx};
    }

    private static float[] origin(float[] m)
    {return new float[]{m[12],m[13],m[14]};}
    private static float[] sub(float[] a,float[] b)
    {return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static float[] add(float[] a,float[] b)
    {return new float[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static float[] mul(float[] a,float s)
    {return new float[]{a[0]*s,a[1]*s,a[2]*s};}
    private static float[] lerp(float[] a,float[] b,float t)
    {return add(a,mul(sub(b,a),t));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float length(float[] a)
    {return (float)Math.sqrt(dot(a,a));}
    private static float[] normalize(float[] a)
    {float n=length(a);return n<1E-7F?new float[]{0,0,0}:mul(a,1F/n);}
    private static boolean valid(float[] a,int size)
    {
        if(a==null||a.length!=size)return false;
        for(float x:a)if(!Float.isFinite(x))return false;
        return true;
    }
    private static Map<String,float[]> cloneSwings(Map<String,float[]> source)
    {
        Map<String,float[]> output=new LinkedHashMap<String,float[]>();
        if(source!=null)
            for(Map.Entry<String,float[]> e:source.entrySet())
                if(e.getKey()!=null&&e.getValue()!=null)
                    output.put(e.getKey(),e.getValue().clone());
        return output;
    }
}

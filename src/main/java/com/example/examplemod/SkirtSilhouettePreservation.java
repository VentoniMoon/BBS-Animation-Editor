package com.example.examplemod;

/**
 * A deterministic collision-versus-silhouette envelope for the 20 linked
 * Chameleon skirt panels. Distances are in rendered world blocks, after
 * the model's Global Transform scale has already been applied.
 *
 * The natural input is the Jolt result AFTER skirt seam coupling, BEFORE
 * visible OBB correction. Native physics, pose keys, Weld and the rest
 * shape are never modified.
 */
public final class SkirtSilhouettePreservation
{
    /** A front contact panel can yield enough to let a real thigh pass. */
    public static final float CENTER_LIMIT=.105F;
    /** Its immediate angular neighbours are deliberately much quieter. */
    public static final float NEIGHBOR_LIMIT=.052F;
    /** A broad back or incidental contact cannot open an entire skirt. */
    public static final float OUTSIDE_LIMIT=.025F;
    /** Sacrifice no more than 32% of the measured clearance that the
     * legacy correction achieved on this particular panel. */
    public static final float MAX_CLEARANCE_LOSS=.32F;

    private SkirtSilhouettePreservation(){}

    /**
     * These weights are computed by the original contact-island selector,
     * exactly one primary center and up to two physical angular neighbors
     * per named thigh. 0 = neither; 1 = direct center; 0.38 = neighbor.
     */
    public static float maxAdditionalOutward(float contactWeight)
    {
        if(!Float.isFinite(contactWeight)||contactWeight<=0F)
            return OUTSIDE_LIMIT;
        if(contactWeight>=.999F)return CENTER_LIMIT;
        float t=Math.min(1F,contactWeight/.38F);
        return OUTSIDE_LIMIT+(NEIGHBOR_LIMIT-OUTSIDE_LIMIT)*t;
    }

    /**
     * Accept a silhouette-preserving trial if it keeps most of the
     * already achieved collision reduction relative to the native pose.
     * No prior collision improvement -> don't permit new worsening.
     */
    public static boolean keepsClearance(float naturalOverlap,
            float fullCorrectionOverlap,float trialOverlap)
    {
        if(!Float.isFinite(naturalOverlap)||
                !Float.isFinite(fullCorrectionOverlap)||
                !Float.isFinite(trialOverlap))
            return false;
        if(naturalOverlap<0F||fullCorrectionOverlap<0F||
                trialOverlap<0F)return false;
        float gain=Math.max(0F,naturalOverlap-fullCorrectionOverlap);
        float tolerance=Math.min(.018F,Math.max(.002F,gain*.035F));
        return trialOverlap<=fullCorrectionOverlap+
                MAX_CLEARANCE_LOSS*gain+tolerance;
    }

    /**
     * Collision-first mode: smoothing may not give away already achieved
     * overlap reduction. Tiny score noise is accepted, but not the old
     * gain-relative 32% loss. No temporal state or hidden per-frame cache.
     */
    public static boolean keepsClearanceStrict(float before,float trial)
    {
        return Float.isFinite(before)&&Float.isFinite(trial) &&
                before>=0F&&trial>=0F &&
                trial<=before+.0005F;
    }

    /** Used for stable 20-panel diagnostics, not a runtime cache. */
    public static boolean outsideEnvelope(float outwardDistance,float weight)
    {
        return Float.isFinite(outwardDistance) &&
                outwardDistance>maxAdditionalOutward(weight)+.0005F;
    }
}

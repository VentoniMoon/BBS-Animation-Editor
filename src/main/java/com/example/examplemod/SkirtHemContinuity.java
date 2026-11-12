package com.example.examplemod;

/**
 * Small, stateless geometry preference for the circumference of a
 * 20-panel Chameleon skirt. Operates on correction-induced outward
 * displacements, NOT raw skirt world coordinates or a linked cloth mesh.
 *
 * A contact center retains a sharper bend over the thigh; its actual
 * angular neighbors taper toward native physics. The correction solver
 * separately verifies every proposed change against the same animated
 * leg OBBs before committing anything.
 */
public final class SkirtHemContinuity
{
    public static final int PANELS=20;
    private static final float CENTER_TRANSITION=.073F;
    private static final float NEIGHBOR_TRANSITION=.032F;
    private static final float OUTSIDE_TRANSITION=.017F;

    private SkirtHemContinuity(){}

    public static float transitionAllowance(float contactWeight)
    {
        if(!Float.isFinite(contactWeight)||contactWeight<=0F)
            return OUTSIDE_TRANSITION;
        if(contactWeight>=.999F)return CENTER_TRANSITION;
        float fraction=Math.min(1F,contactWeight/.38F);
        return OUTSIDE_TRANSITION+
                fraction*(NEIGHBOR_TRANSITION-OUTSIDE_TRANSITION);
    }

    /**
     * Target radial/vertical excursion above the native same-frame skirt
     * after considering immediate *physical* left/right neighbors.
     * Never increases any section beyond what contact correction gave.
     */
    public static float target(float current,float prev,float next,
            float contactWeight)
    {
        if(!Float.isFinite(current)||!Float.isFinite(prev)||
                !Float.isFinite(next)||current<0F||prev<0F||next<0F)
            return current;
        float neighbors=.5F*(prev+next);
        float profile=neighbors+transitionAllowance(contactWeight);
        float envelope=
                SkirtSilhouettePreservation.maxAdditionalOutward(contactWeight);
        return Math.max(0F,Math.min(current,Math.min(profile,envelope)));
    }

    /** Local seam variation measured over the current cyclic ring. */
    public static float roughness(float left,float center,float right)
    {
        if(!Float.isFinite(left)||!Float.isFinite(center)||
                !Float.isFinite(right))return Float.POSITIVE_INFINITY;
        return Math.abs(center-left)+Math.abs(center-right);
    }

    /** Only a genuine reduction of ring roughness is accepted. */
    public static boolean improvesNeighbors(float left,float before,
            float after,float right)
    {
        return after<=before+.0001F &&
                roughness(left,after,right)<=roughness(left,before,right)+
                .0005F;
    }
}

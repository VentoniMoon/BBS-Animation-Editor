package com.example.examplemod;

/** Java 8 control-plane regression for contact-weighted silhouette limits. */
public final class SkirtSilhouettePreservationSmokeTest
{
    private SkirtSilhouettePreservationSmokeTest(){}

    public static void main(String[] args)
    {
        silhouetteBudgetIsContactWeighted();
        collisionBenefitIsNeverDiscarded();
        invalidValuesFailClosed();
        System.out.println("SkirtSilhouettePreservationSmokeTest OK");
    }

    private static void silhouetteBudgetIsContactWeighted()
    {
        float primary=SkirtSilhouettePreservation.maxAdditionalOutward(1F);
        float adjacent=SkirtSilhouettePreservation.maxAdditionalOutward(.38F);
        float unrelated=SkirtSilhouettePreservation.maxAdditionalOutward(0F);
        require(primary>adjacent && adjacent>unrelated,
                "central contact must have greater silhouette allowance");
        near(primary,.105F,"central contact limit");
        near(adjacent,.052F,"physical angular neighbour limit");
        near(unrelated,.025F,"remaining 14+ skirt panels must stay natural");
        require(SkirtSilhouettePreservation.outsideEnvelope(.09F,0F),
                "unrelated section cannot flare 0.09 blocks");
        require(!SkirtSilhouettePreservation.outsideEnvelope(.09F,1F),
                "one real thigh contact can receive a large local yield");
    }

    private static void collisionBenefitIsNeverDiscarded()
    {
        // Legacy correction clears 10 depth units from a severe contact.
        // Limiting its outward flare may cost 1 or 2 units, but not 5.
        require(SkirtSilhouettePreservation.keepsClearance(12F,2F,4F),
                "should retain most of the collision relief");
        require(!SkirtSilhouettePreservation.keepsClearance(12F,2F,7F),
                "do not hide the thigh by reversing the solved contact");
        require(SkirtSilhouettePreservation.keepsClearance(0F,0F,0F),
                "an idle skirt is always safe");
        require(!SkirtSilhouettePreservation.keepsClearance(0F,0F,1F),
                "no collision before correction means no new collision");
    }

    private static void invalidValuesFailClosed()
    {
        near(SkirtSilhouettePreservation.maxAdditionalOutward(
                Float.NaN),.025F,"nonfinite silhouette weight");
        require(!SkirtSilhouettePreservation.keepsClearance(
                Float.NaN,0F,0F),"bad collision measurement must fail closed");
        require(!SkirtSilhouettePreservation.outsideEnvelope(
                Float.NaN,1F),"nonfinite displacement is not an overshoot");
    }

    private static void near(float actual,float expected,String label)
    {
        require(Math.abs(actual-expected)<.0001F,
                label+": "+actual+" expected "+expected);
    }
    private static void require(boolean valid,String reason)
    {
        if(!valid)throw new AssertionError(reason);
    }
}

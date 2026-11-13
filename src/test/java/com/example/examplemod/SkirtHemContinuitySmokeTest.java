package com.example.examplemod;

/** Java 8 smoke for a cyclic, contact-weighted hem transition. */
public final class SkirtHemContinuitySmokeTest
{
    private SkirtHemContinuitySmokeTest(){}

    public static void main(String[] args)
    {
        strongContactKeepsLocalRise();
        neighborsTaperIntoRest();
        noInventedOutwardMovement();
        clearanceFirstHasNoIndependentEnvelope();
        seamWrapAndQualityCheck();
        invalidValuesFailClosed();
        System.out.println("SkirtHemContinuitySmokeTest OK");
    }

    private static void strongContactKeepsLocalRise()
    {
        float center=SkirtHemContinuity.target(.17F,.045F,.045F,1F);
        float neighbor=SkirtHemContinuity.target(.17F,.045F,.045F,.38F);
        float outside=SkirtHemContinuity.target(.17F,.045F,.045F,0F);
        require(center>neighbor && neighbor>outside,
                "center must be allowed to rise more than its neighbors");
        require(center<=.1051F,"central hem exceeded collision envelope");
        require(neighbor<=.0521F,"adjacent section was not tapered");
        require(outside<=.0251F,"noncontact section opened with the leg");
    }

    private static void neighborsTaperIntoRest()
    {
        // A tall isolated outward spike must have a much lower shape
        // target than a genuinely supported center with raised neighbors.
        float spike=SkirtHemContinuity.target(.18F,.01F,.01F,.38F);
        float hill=SkirtHemContinuity.target(.18F,.09F,.09F,1F);
        require(spike<hill,"angular isolation must matter");
        require(hill>.08F,"do not flatten the real contacting hem peak");
    }

    private static void noInventedOutwardMovement()
    {
        require(SkirtHemContinuity.target(0F,.3F,.3F,1F)==0F,
                "contact smoothing must never create new movement");
        require(SkirtHemContinuity.target(.02F,.3F,.3F,1F)<=.02F,
                "small panel must not be pulled outward");
    }

    private static void clearanceFirstHasNoIndependentEnvelope()
    {
        float old=SkirtHemContinuity.target(.20F,.12F,.12F,1F);
        float safe=SkirtHemContinuity.targetClearanceFirst(
                .20F,.12F,.12F,1F);
        require(safe>old+.03F,
                "new mode must not force a leg contact under the old cap");
        require(safe<=.20F,
                "seam target may not expand the natural outline");
        near(SkirtHemContinuity.targetClearanceFirst(
                .05F,.30F,.30F,1F),.05F,
                "a low panel must never be pushed outward");
        require(SkirtHemContinuity.targetClearanceFirst(
                Float.NaN,.03F,.03F,1F)!=
                SkirtHemContinuity.targetClearanceFirst(
                Float.NaN,.03F,.03F,1F),
                "invalid inputs must stay invalid");
    }

    private static void seamWrapAndQualityCheck()
    {
        // As a circular metric, local quality does not distinguish the
        // physical first and last panel in the authored bone list.
        near(SkirtHemContinuity.roughness(.01F,.16F,.03F),
                .28F,"local seam quality");
        require(SkirtHemContinuity.improvesNeighbors(
                .01F,.16F,.08F,.03F),
                "lower spike must reduce adjacent discontinuity");
        require(!SkirtHemContinuity.improvesNeighbors(
                .01F,.08F,.16F,.03F),
                "a larger tip protrusion must be rejected");
    }

    private static void invalidValuesFailClosed()
    {
        require(Float.isNaN(SkirtHemContinuity.target(
                Float.NaN,.01F,.01F,1F)),
                "bad shape must never be invented as a valid target");
        require(!SkirtHemContinuity.improvesNeighbors(
                Float.NaN,.1F,.02F,.01F),
                "invalid ring cannot be marked as improved");
    }

    private static void near(float actual,float expected,String why)
    {
        require(Math.abs(actual-expected)<.0001F,
                why+" actual="+actual+" expected="+expected);
    }

    private static void require(boolean ok,String why)
    {
        if(!ok)throw new AssertionError(why);
    }
}

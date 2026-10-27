package com.example.examplemod;

/** Pure Java 8 regressions for both light editors' shared world geometry. */
public final class BetterLightsWorldStateSmokeTest
{
    private static void eq(double a,double b)
    {
        if(Math.abs(a-b)>0.00001D)
            throw new AssertionError(a+" != "+b);
    }

    public static void main(String[] args)
    {
        BetterLightsWorldState staticSource=BetterLightsWorldState.points(
                668.286,28.947,-949.654,
                668.286,25.947,-945.654);
        if(staticSource==null)throw new AssertionError("static source missing");
        eq(staticSource.x,668.286);
        eq(staticSource.targetY,25.947);
        eq(staticSource.directionX,0);
        eq(staticSource.directionY,-0.6);
        eq(staticSource.directionZ,0.8);

        // An actor with no keys aims four world blocks along +Z.
        BetterLightsWorldState actor=BetterLightsWorldState.actor(
                657D,9.5D,-958.4D,new float[]{0,0,0},
                new float[]{0,0,4});
        eq(actor.x,657D);eq(actor.y,9.5D);eq(actor.z,-958.4D);
        eq(actor.targetZ,-954.4D);
        eq(actor.directionZ,1D);

        // Origin moves target along with source; Target keys are deltas from
        // the source rather than from the camera, skeleton model or chunk.
        BetterLightsWorldState moved=BetterLightsWorldState.actor(
                657D,9.5D,-958.4D,new float[]{3F,2F,-1F},
                new float[]{-6F,0F,8F});
        eq(moved.x,660D);eq(moved.y,11.5D);eq(moved.z,-959.4D);
        eq(moved.targetX,654D);eq(moved.targetZ,-951.4D);
        eq(moved.directionX,-0.6D);
        eq(moved.directionZ,0.8D);

        // A coincident Target is invalid; it must not inject NaNs into the
        // native shader. A visible +Z default is stable and deterministic.
        BetterLightsWorldState collapsed=BetterLightsWorldState.points(
                4D,5D,6D,4D,5D,6D);
        eq(collapsed.directionZ,1D);
        eq(collapsed.targetZ,10D);

        if(BetterLightsWorldState.points(Double.NaN,0,0,1,0,0)!=null)
            throw new AssertionError("invalid source should be ignored");
        if(BetterLightsWorldState.points(0,0,0,Double.POSITIVE_INFINITY,0,0)
                !=null)throw new AssertionError("invalid aim should be ignored");
        System.out.println("BetterLights unified WORLD coordinate regression PASS");
    }
}

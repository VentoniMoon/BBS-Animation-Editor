package com.example.examplemod;

/** Signed oriented-box probe used to compare front strands against a
 * common animated Chameleon chest collider, regardless of actor yaw.
 */
public final class JoltContactClearanceSmoke
{
    private JoltContactClearanceSmoke() {}

    private static void near(float got,float expected,String label)
    {
        if(Math.abs(got-expected)>0.00025F)
            throw new AssertionError(label+" "+got+" != "+expected);
    }

    public static void main(String[] args)
    {
        float[] chest={0F,1.5F,0F, 0F,0F,0F,1F, 0.5F,0.6F,0.3F};
        near(BodyPartJoltContactProbe.signedDistance(
                new float[]{0F,1.5F,0F},chest),-0.3F,"inside");
        near(BodyPartJoltContactProbe.signedDistance(
                new float[]{1.5F,1.5F,0F},chest),1F,"outside");
        near(BodyPartJoltContactProbe.signedDistance(
                new float[]{0.5F,1.5F,0F},chest),0F,"surface");

        float[] left={-0.4F,1.5F,0.15F};
        float[] right={0.4F,1.5F,0.15F};
        float baseL=BodyPartJoltContactProbe.signedDistance(left,chest);
        float baseR=BodyPartJoltContactProbe.signedDistance(right,chest);
        near(baseL,baseR,"mirror clearance");
        for(int yaw=0;yaw<=270;yaw+=90)
        {
            float[] actor=BodyPartJoltActorSpace.worldFromActor(10,64,-5,yaw);
            float[] wc=BodyPartJoltActorSpace.box(actor,chest);
            float[] wl=BodyPartJoltActorSpace.point(actor,left);
            float[] wr=BodyPartJoltActorSpace.point(actor,right);
            near(BodyPartJoltContactProbe.signedDistance(wl,wc),
                    baseL,"left rotated "+yaw);
            near(BodyPartJoltContactProbe.signedDistance(wr,wc),
                    baseR,"right rotated "+yaw);
            float[] other={2F,1.5F,0F,0F,0F,0F,1F,0.2F,0.2F,0.2F};
            float[] wo=BodyPartJoltActorSpace.box(actor,other);
            BodyPartJoltContactProbe.Nearest nearest=
                    BodyPartJoltContactProbe.nearest(wl,
                            new float[][]{wo,wc});
            if(nearest.index!=1)
                throw new AssertionError("Nearest chest lost after actor yaw");
        }
        // A native physics capsule can overlap a box even when its
        // animated source pivot is outside. Test the ACTUAL segment path
        // and subtract native capsule radius from sampled signed clearance.
        float[] solvedLink={-.8F,1.5F,0F, .8F,1.5F,0F, .04F};
        BodyPartJoltContactProbe.Nearest solved=
                BodyPartJoltContactProbe.nearestSolvedLink(
                        solvedLink,new float[][]{chest});
        if(solved.index!=0)
            throw new AssertionError("Missed crossed chest collision");
        near(solved.clearance,-.34F,"native through-box clearance");
        float[] far={2F,1.5F,0F, 2.4F,1.5F,0F,.04F};
        solved=BodyPartJoltContactProbe.nearestSolvedLink(
                far,new float[][]{chest});
        if(solved.index!=0 || !(solved.clearance>1F))
            throw new AssertionError("False contact for distant physical link");
        System.out.println("Mirror hair/chest clearances invariant under world yaw: PASS");
        System.out.println("Native physical capsule versus installed box diagnostic: PASS");
    }
}

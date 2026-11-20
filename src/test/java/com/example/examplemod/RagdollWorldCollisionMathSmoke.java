package com.example.examplemod;

/**
 * Pure Java 8 regression tests for the actor/world coordinate mapping.
 *
 * javac --release 8 -d /tmp/ragdoll-test
 *    src/main/java/com/example/examplemod/RagdollWorldCollisionMath.java
 *    src/test/java/com/example/examplemod/RagdollWorldCollisionMathSmoke.java
 * java -cp /tmp/ragdoll-test com.example.examplemod.RagdollWorldCollisionMathSmoke
 */
public final class RagdollWorldCollisionMathSmoke
{
    private static void near(double got,double want,String label)
    {
        if(!Double.isFinite(got)||Math.abs(got-want)>1E-4D)
            throw new AssertionError(label+" got="+got+" want="+want);
    }

    public static void main(String[] args)
    {
        float[] local={1F,2F,0F};
        double[] world=RagdollWorldCollisionMath.worldPoint(
                local,10D,64D,20D,90F);
        near(world[0],10D,"world X at 90 yaw");
        near(world[1],66D,"world Y");
        near(world[2],21D,"world Z at 90 yaw");

        float[] box=RagdollWorldCollisionMath.localBox(
                9.5D,65.875D,20.5D,10.5D,66.125D,21.5D,
                10D,64D,20D,90F);
        near(box[0],1D,"local center X");
        near(box[1],2D,"local center Y");
        near(box[2],0D,"local center Z");
        near(box[3],.5D,"half X");
        near(box[4],.125D,"half Y");
        near(box[5],.5D,"half Z");
        near(box[7],Math.sqrt(.5D),"local orientation");
        near(box[9],Math.sqrt(.5D),"local orientation W");

        // A 1/16-bit slab stays 1/16 thick. Never turn it into a whole
        // block or a synthetic collision plane extending beyond the bit.
        float[] bit=RagdollWorldCollisionMath.localBox(
                1D,5D,1D,1.0625D,5.015625D,1.0625D,
                0D,0D,0D,0F);
        near(bit[3],.03125D,"Chisels & Bits half width");
        near(bit[4],.0078125D,"LittleTiles half height");

        // The world center must round-trip for arbitrary actor yaw/position.
        float[] random=RagdollWorldCollisionMath.localBox(
                -4.3125D,17.25D,48.125D,-3.875D,18D,48.25D,
                -6.5D,15D,45.75D,-137F);
        double[] worldAgain=RagdollWorldCollisionMath.worldPoint(random,
                -6.5D,15D,45.75D,-137F);
        near(worldAgain[0],(-4.3125D-3.875D)*.5D,"roundtrip X");
        near(worldAgain[1],17.625D,"roundtrip Y");
        near(worldAgain[2],(48.125D+48.25D)*.5D,"roundtrip Z");
        System.out.println("Ragdoll world collision math Java 8 smoke OK");
    }
}

package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Actual Jolt JNI smoke: independent pelvis/chest/head/arms/legs, native
 * hinge and swing limits, world-floor contact, one-shot body impulse.
 */
public final class JoltHumanoidRagdollSmoke
{
    private static void check(boolean yes,String reason)
    {
        if(!yes)throw new AssertionError(reason);
    }

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new IllegalStateException("No Jolt factory");
        Jolt.registerTypes();

        float[][] heads={
                {0F,.75F,0F}, {0F,1.125F,0F}, {0F,1.5F,0F},
                {.375F,1.375F,0F},{-.375F,1.375F,0F},
                {.375F,1.125F,0F},{-.375F,1.125F,0F},
                {.125F,.75F,0F},{-.125F,.75F,0F},
                {.125F,.375F,0F},{-.125F,.375F,0F}
        };
        float[][] tails={
                {0F,1.125F,0F},{0F,1.5F,0F},{0F,2F,0F},
                {.375F,1.125F,0F},{-.375F,1.125F,0F},
                {.375F,.875F,0F},{-.375F,.875F,0F},
                {.125F,.375F,0F},{-.125F,.375F,0F},
                {.125F,0F,0F},{-.125F,0F,0F}
        };
        int[] parents={-1,0,1,1,1,3,4,0,0,7,8};
        int[] kind={1,1,1,1,1,2,2,1,1,2,2};
        float[] radii={.19F,.2F,.22F,.105F,.105F,.09F,.09F,
                .15F,.15F,.115F,.115F};
        float[][] limits=new float[11][5];
        for(int i=0;i<limits.length;i++)
        {
            limits[i][0]=65F;
            limits[i][1]=-40F;
            limits[i][2]=40F;
            limits[i][3]=0F;
            limits[i][4]=145F;
        }
        boolean rejected=false;
        try
        {
            JoltHumanoidRagdoll.create(heads,tails,radii,
                    new int[]{-1,1,1,1,1,3,4,0,0,7,8},
                    kind,limits,-.1F);
        }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Self-parent topology accepted");

        try(JoltHumanoidRagdoll ragdoll=
                JoltHumanoidRagdoll.create(heads,tails,radii,
                        parents,kind,limits,-.1F))
        {
            float[][] before=ragdoll.poses();
            check(before.length==11,"Missing Emoticons rigid body");
            ragdoll.impulse(2,5F,0F,0F,0F,1.75F,.12F);
            for(int i=0;i<14;i++)ragdoll.step();
            float[][] after=ragdoll.poses();
            check(after.length==11,"World body count changed");
            float moved=0F;
            for(int i=0;i<after.length;i++)
                for(int k=0;k<7;k++)
                {
                    check(Float.isFinite(after[i][k]),
                            "Nonfinite Jolt pose: "+i+"/"+k);
                    check(Math.abs(after[i][k])<10F,
                            "Exploded Jolt segment: "+i+"/"+k);
                }
            for(int k=0;k<3;k++)
                moved+=Math.abs(before[2][k]-after[2][k]);
            check(moved>.01F,"Head did not react to applied impulse");
        }
        // Fitted native hinge frames: keep a stationary balanced biped
        // upright, then tip it and verify a continuous, stable fall.
        float[][] frames=new float[11][6];
        for(int i=0;i<frames.length;i++)
            frames[i]=new float[]{1F,0F,0F,0F,1F,0F};
        try(JoltHumanoidRagdoll rig=
                JoltHumanoidRagdoll.createFitted(heads,tails,radii,
                        parents,kind,limits,frames,-.03F))
        {
            for(int tick=0;tick<40;tick++)rig.step();
            float[][] rest=rig.poses();
            check(rest[2][1]>1.4F,
                    "Still upright pose fell without an impact");
        }
        try(JoltHumanoidRagdoll rig=
                JoltHumanoidRagdoll.createFitted(heads,tails,radii,
                        parents,kind,limits,frames,-.03F))
        {
            rig.startFall(0F,1F);
            for(int tick=0;tick<70;tick++)
            {
                rig.step();
                float[][] sample=rig.poses();
                for(int i=0;i<sample.length;i++)
                    for(float value:sample[i])
                        check(Float.isFinite(value)&&Math.abs(value)<10F,
                                "Unstable impact pose at tick "+tick);
            }
            float[][] end=rig.poses();
            check(end[2][1]<0.9F && end[2][2]>.15F,
                    "Body did not tip before landing");
        }
        System.out.println("Native Jolt Emoticons 11-body ragdoll OK");
    }
}

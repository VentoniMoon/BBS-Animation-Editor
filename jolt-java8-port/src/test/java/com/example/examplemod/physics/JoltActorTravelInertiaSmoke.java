package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Native proof that translating an entire actor-world pin produces dynamic
 * hair inertia, even without animating ANY local Chameleon bone.
 *
 * This complements the pure matrix tests: the old actor-local Preview
 * passed an unchanged root pin while an entity was walking, so no
 * physical translation impulse could be generated.
 */
public final class JoltActorTravelInertiaSmoke
{
    private JoltActorTravelInertiaSmoke() {}

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("No native Jolt factory");
        Jolt.registerTypes();

        float[][] pivots={
                {0F,1.8F,0F},{0F,1.6F,0F},
                {0F,1.4F,0F},{0F,1.2F,0F}
        };
        JoltHairChain rig=new JoltHairChain(pivots,0.55F,0.25F,0.6F);
        try
        {
            for(int i=0;i<30;i++)rig.step();
            double first=rig.position(0).xx();
            // A deliberate actor-world movement: root translates 0.20
            // blocks in one animation tick. The first dynamic body must
            // respond, but is not instantaneously teleported to the new
            // root-centered rest segment.
            rig.moveAnchor(0.20D,1.8D,0D,0F,0F,0F,1F);
            rig.step();
            double after=rig.position(0).xx();
            double response=after-first;
            if(!Double.isFinite(response) || Math.abs(response)<0.0005D)
                throw new AssertionError("Translated actor root did not move hair");
            for(int i=0;i<35;i++)
            {
                rig.moveAnchor(0.20D,1.8D,0D,0F,0F,0F,1F);
                rig.step();
            }
            double settled=rig.position(0).xx();
            double delayed=settled-after;
            if(!Double.isFinite(delayed) ||
                    Math.abs(response)<0.0005D ||
                    Math.abs(delayed)<0.00005D)
                throw new AssertionError("Native strand moved only once; no time-dependent following: "
                        +response+" / "+delayed);
            System.out.println("Native hair follows moving actor-world anchor over time: PASS "+
                    "first="+first+" after="+after+" settled="+settled);
        }
        finally
        {
            rig.close();
        }
    }
}

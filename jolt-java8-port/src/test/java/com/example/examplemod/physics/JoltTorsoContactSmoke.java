package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.RVec3;

/** Native Jolt 6.0.0 Java 8: kinematic torso MUST collide with hair. */
public final class JoltTorsoContactSmoke
{
    private JoltTorsoContactSmoke() {}

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new IllegalStateException("Jolt native factory");
        Jolt.registerTypes();

        float[][] pivots={
                {0.22F,1.85F,0F},
                {0.22F,1.65F,0F},
                {0.22F,1.45F,0F},
                {0.22F,1.25F,0F}
        };
        JoltHairChain free=new JoltHairChain(pivots,0F,.30F,.5F);
        JoltHairChain contacted=new JoltHairChain(pivots,0F,.30F,.5F);
        try
        {
            if(free.hasTorsoCollider())throw new AssertionError("Default contacts must remain OFF");
            float[] a={0F,1.20F,0F},b={0F,1.80F,0F};
            contacted.setTorsoCollider(a,b,.20F);
            if(!contacted.hasTorsoCollider())
                throw new AssertionError("Native torso body was not created");
            for(int t=0;t<160;t++)
            {
                free.step();contacted.step();
            }
            double freeX=free.position(1).xx();
            double collidedX=contacted.position(1).xx();
            if(!Double.isFinite(collidedX)||!Double.isFinite(freeX))
                throw new AssertionError("Nonfinite native contact simulation");
            if(!(collidedX>freeX+0.001D))
                throw new AssertionError("Torso didn't push strand outward: "+
                        "free="+freeX+" contact="+collidedX);

            // Animated torso must move with the bones, not stay at rest.
            a[0]+=0.05F;b[0]+=0.05F;
            contacted.setTorsoCollider(a,b,.20F);
            for(int t=0;t<10;t++)contacted.step();
            RVec3 p=contacted.position(1);
            if(!Double.isFinite(p.xx())||!Double.isFinite(p.yy()))
                throw new AssertionError("Moving contact caused invalid Jolt body pose");
            try
            {
                contacted.setTorsoCollider(a,b,1.0F);
                throw new AssertionError("Absurd torso collider radius accepted");
            }
            catch(IllegalArgumentException expected) { }
            System.out.println("Native Java 8 hair vs fitted kinematic torso: PASS; freeX="+
                    freeX+" collisionX="+collidedX);
        }
        finally {free.close();contacted.close();}
    }
}

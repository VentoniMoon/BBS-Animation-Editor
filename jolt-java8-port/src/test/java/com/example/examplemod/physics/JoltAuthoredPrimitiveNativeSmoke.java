package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Real JNI regression: authored passive Sphere and Capsule colliders
 * participate in Jolt hair contacts; topology/size changes must fail
 * explicitly rather than deform a previously created native body.
 */
public final class JoltAuthoredPrimitiveNativeSmoke
{
    private JoltAuthoredPrimitiveNativeSmoke() {}

    private static float[][] pivots()
    {
        return new float[][] {
                {0.18F,1.84F,0F},{0.18F,1.63F,0F},
                {0.18F,1.42F,0F},{0.18F,1.21F,0F}
        };
    }

    private static void check(int primitive)
    {
        JoltHairChain baseline=new JoltHairChain(pivots(),.55F,.25F,.6F);
        JoltHairChain pushed=new JoltHairChain(pivots(),.55F,.25F,.6F);
        try
        {
            float[] form=primitive==1?
                    new float[]{0F,1.4F,0F,0F,0F,0F,1F,
                            .18F,.18F,.18F,1F}:
                    new float[]{0F,1.4F,0F,0F,0F,0F,1F,
                            .18F,.38F,.18F,2F};
            pushed.setAdditionalShapes(new float[][]{form});
            for(int tick=0;tick<100;tick++)
            {
                baseline.step();
                pushed.step();
            }
            double free=baseline.position(1).xx();
            double collided=pushed.position(1).xx();
            if(!Double.isFinite(free) || !Double.isFinite(collided) ||
                    Math.abs(collided-free)<0.001D)
                throw new AssertionError("No native contact for primitive "+
                        primitive+" free="+free+" collided="+collided);
            float[] moved=form.clone();
            moved[0]+=0.01F;
            pushed.setAdditionalShapes(new float[][]{moved});
            pushed.step();
            float[] typeChanged=moved.clone();
            typeChanged[10]=primitive==1?2F:1F;
            try
            {
                pushed.setAdditionalShapes(new float[][]{typeChanged});
                throw new AssertionError("Type change accepted without rig reset");
            }
            catch(IllegalArgumentException expected) {}
            float[] scaleChanged=moved.clone();
            scaleChanged[8]+=0.05F;
            try
            {
                pushed.setAdditionalShapes(new float[][]{scaleChanged});
                throw new AssertionError("Size change accepted without rig reset");
            }
            catch(IllegalArgumentException expected) {}
            System.out.println("Jolt authored primitive "+primitive+
                    " dynamic chest/hair contact PASS: "+free+" -> "+collided);
        }
        finally {baseline.close();pushed.close();}
    }

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt unavailable");
        Jolt.registerTypes();
        check(1);
        check(2);
    }
}

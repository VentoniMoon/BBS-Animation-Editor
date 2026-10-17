package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Two *independent* native Jolt worlds: symmetric left and right front hair
 * against the exact same two overlapping X-rotated chest boxes.
 * Any strong asymmetry here would implicate the native physics rather than
 * rendered Chameleon parent matrices or the character's pose.
 */
public final class JoltMirroredFrontHairNativeSmoke
{
    private JoltMirroredFrontHairNativeSmoke() {}

    private static float[][] strand(float x)
    {
        return new float[][]{
                {x,1.82F,0.05F},{x,1.61F,0.05F},
                {x,1.40F,0.05F},{x,1.19F,0.05F}
        };
    }

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt factory");
        Jolt.registerTypes();
        JoltHairChain left=new JoltHairChain(strand(-0.18F),0.55F,0.25F,0.6F);
        JoltHairChain right=new JoltHairChain(strand(0.18F),0.55F,0.25F,0.6F);
        try
        {
            float s=(float)Math.sin(Math.PI/8D);
            float c=(float)Math.cos(Math.PI/8D);
            float[][] chest={
                    {0F,1.48F,0.05F,s,0F,0F,c,.18F,.21F,.18F},
                    {0F,1.48F,0.05F,s,0F,0F,c,.17F,.19F,.16F}
            };
            left.setAdditionalBoxes(chest);
            right.setAdditionalBoxes(chest);
            for(int tick=0;tick<65;tick++)
            {
                left.step();
                right.step();
            }
            double leftX=left.position(1).xx();
            double rightX=right.position(1).xx();
            if(!Double.isFinite(leftX)||!Double.isFinite(rightX))
                throw new AssertionError("Invalid native Jolt hair collision");
            if(Math.abs(leftX+rightX)>0.035D)
                throw new AssertionError(
                    "Same mirrored front hair + symmetric boxes disagreed: "+
                    leftX+" vs "+rightX);
            System.out.println("Mirrored front strands vs overlapping rotated chest: PASS "+
                    leftX+" / "+rightX);
        }
        finally {left.close();right.close();}
    }
}

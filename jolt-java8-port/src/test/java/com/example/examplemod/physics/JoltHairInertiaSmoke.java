package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.Quat;

/**
 * Check that a sharp animated root turn produces a time-dependent secondary
 * movement of a Jolt chain, rather than an instantaneous pose snap.
 *
 * This measures native rigid bodies; it does not bake or infer Euler angles.
 */
public final class JoltHairInertiaSmoke
{
    private JoltHairInertiaSmoke() {}

    private static float[] read(Quat q)
    {
        return new float[]{q.getX(),q.getY(),q.getZ(),q.getW()};
    }

    private static float angularDifference(float[] a,float[] b)
    {
        float dot=Math.abs(a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]);
        return (float)(2D*Math.acos(Math.max(0D,Math.min(1D,dot))));
    }

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("No Jolt factory");
        Jolt.registerTypes();

        float[][] pivots={
                {0F,1.80F,0F},{0F,1.60F,0F},
                {0F,1.40F,0F},{0F,1.20F,0F}
        };
        JoltHairChain hair=new JoltHairChain(pivots,0.55F,0.25F,0.6F);
        try
        {
            for(int i=0;i<80;i++)hair.step();
            float[] start=read(hair.rotation(0));
            float angle=(float)Math.toRadians(75D);
            float s=(float)Math.sin(angle*0.5F);
            float c=(float)Math.cos(angle*0.5F);
            // One animation tick: rotate the anchor rapidly, not the hair.
            hair.moveAnchor(0D,1.80D,0D,s,0F,0F,c);
            hair.step();
            float[] first=read(hair.rotation(0));
            for(int i=0;i<35;i++)
            {
                hair.moveAnchor(0D,1.80D,0D,s,0F,0F,c);
                hair.step();
            }
            float[] driven=read(hair.rotation(0));
            hair.moveAnchor(0D,1.80D,0D,0F,0F,0F,1F);
            hair.step();
            float[] afterReturn=read(hair.rotation(0));
            for(int i=0;i<100;i++)
            {
                hair.moveAnchor(0D,1.80D,0D,0F,0F,0F,1F);
                hair.step();
            }
            float[] settled=read(hair.rotation(0));
            float moving=angularDifference(first,driven);
            float recovery=angularDifference(afterReturn,settled);
            if(!(moving>0.001F && recovery>0.001F))
                throw new AssertionError("Jolt hair not reacting over time: moving="
                        +moving+" recovery="+recovery);
            System.out.println("Jolt native hair lag and recovery: PASS; "+
                    "initialShift="+angularDifference(start,first)+
                    " following="+moving+" recovery="+recovery);
        }
        finally { hair.close(); }
    }
}

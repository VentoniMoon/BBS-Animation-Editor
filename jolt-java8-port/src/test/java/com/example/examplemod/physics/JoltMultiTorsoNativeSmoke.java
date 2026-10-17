package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Real Windows/Linux Jolt JNI contact and moving three-zone test on Java 8. */
public final class JoltMultiTorsoNativeSmoke
{
    private JoltMultiTorsoNativeSmoke() {}

    public static void main(String[] args)
    {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("No native factory");
        Jolt.registerTypes();

        float[][] pivots={
                {0.22F,1.85F,0F},
                {0.22F,1.65F,0F},
                {0.22F,1.45F,0F},
                {0.22F,1.25F,0F}
        };
        float[][] a={{0F,1.20F,0F},{0F,1.34F,0F},{0F,1.47F,0F}};
        float[][] b={{0F,1.36F,0F},{0F,1.50F,0F},{0F,1.60F,0F}};
        float[] radius={0.14F,0.20F,0.12F};

        JoltHairChain baseline=new JoltHairChain(pivots,0F,.30F,.5F);
        JoltHairChain chain=new JoltHairChain(pivots,0F,.30F,.5F);
        try
        {
            chain.setTorsoSegments(a,b,radius);
            if(!chain.hasTorsoSegments() || !chain.hasTorsoCollider())
                throw new AssertionError("Three native bodies were not installed");
            for(int frame=0;frame<140;frame++)
            {
                baseline.step();
                chain.step();
            }
            double x0=baseline.position(1).xx();
            double x1=chain.position(1).xx();
            if(!(x1>x0+0.001D))
                throw new AssertionError("Three native torso colliders had no contact effect: "+
                        x0+" vs "+x1);

            float[][] nativeHair=chain.nativeHairSegments();
            if(nativeHair.length!=pivots.length-1 || nativeHair[0].length!=7)
                throw new AssertionError("Raw hair rigid body segment count");
            for(float[] segment:nativeHair)
            {
                float length=0F;
                for(float component:segment)
                    if(!Float.isFinite(component))
                        throw new AssertionError("Nonfinite native body endpoint");
                for(int c=0;c<3;c++)
                    length+=(segment[c]-segment[c+3])*
                            (segment[c]-segment[c+3]);
                if(length<0.0025F)
                    throw new AssertionError("Degenerate native link capsule");
            }
            // These 3 shapes must be moved, not removed/re-added each frame.
            for(int k=0;k<8;k++)
            {
                for(int i=0;i<3;i++) {
                    a[i][0]=0.02F+0.002F*k;
                    b[i][0]=0.02F+0.002F*k;
                }
                chain.setTorsoSegments(a,b,radius);
                chain.step();
            }
            try {
                chain.setTorsoSegments(a,b,new float[]{.14F,2F,.12F});
                throw new AssertionError("Invalid torso segment accepted");
            } catch(IllegalArgumentException expected) { }
            System.out.println("Java8 native 3-zone torso contacts and moving body: PASS; " +
                    "baselineX="+x0+" compoundX="+x1);
        }
        finally
        {
            chain.close();
            baseline.close();
        }
    }
}

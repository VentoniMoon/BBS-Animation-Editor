package com.example.examplemod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone Java 8 regression: visual skirt cohesion without Jolt JNI. */
public final class SkirtClothPoseCouplerSmokeTest
{
    private static final int N=20;

    public static void main(String[] args)
    {
        testMirroredRearWorldAxes();
        testOutlierAndNeighborLimits();
        testIncompleteDoesNotModifyPhysics();
        System.out.println("SkirtClothPoseCouplerSmokeTest OK");
    }

    private static void testMirroredRearWorldAxes()
    {
        Fixture f=new Fixture();
        for(int i=0;i<N;i++)
        {
            boolean rear=i>=N/2;
            f.put(i,rear?-1F:1F,.30F,.16F);
        }
        Map<String,float[]> result=SkirtClothPoseCoupler.couple(f.strips,f.raw);
        for(int i=0;i<N;i++)
        {
            float[] swing=result.get("upper"+i);
            // Rear author transforms have Y=180. A common world swing must
            // map back to the OPPOSITE local axis (not cancel the front).
            require(swing[0]*(i>=10?-1F:1F)>.99F,
                    "World axis lost across mirrored rear "+i);
            near(swing[3],.30F,.02F,"Mirrored angle "+i);
        }
    }

    private static void testOutlierAndNeighborLimits()
    {
        Fixture f=new Fixture();
        for(int i=0;i<N;i++)
            f.put(i,false,i==0?1F:1F,i==0?2.5F:0F,
                    i==0?2.2F:0F);
        Map<String,float[]> result=SkirtClothPoseCoupler.couple(f.strips,f.raw);
        for(int i=0;i<N;i++)
        {
            float a=result.get("upper"+i)[3],b=result.get("lower"+i)[3];
            require(a<=.401F,"Root excessive bend "+i+": "+a);
            require(b<=.301F,"Hem excessive bend "+i+": "+b);
            float next=result.get("upper"+((i+1)%N))[3];
            require(Math.abs(a-next)<=.125F,
                    "Neighbor root seam detached "+i);
            float nextHem=result.get("lower"+((i+1)%N))[3];
            require(Math.abs(b-nextHem)<=.165F,
                    "Neighbor hem seam detached "+i);
        }
    }

    private static void testIncompleteDoesNotModifyPhysics()
    {
        Fixture f=new Fixture();
        for(int i=0;i<N;i++)f.put(i,false,1F,.75F,.55F);
        f.strips.remove(N-1);
        Map<String,float[]> result=SkirtClothPoseCoupler.couple(f.strips,f.raw);
        near(result.get("upper0")[3],.75F,.00001F,
                "Incomplete skirt must leave existing physics alone");
    }

    private static final class Fixture
    {
        final List<SkirtClothPoseCoupler.Strip> strips=new ArrayList<
                SkirtClothPoseCoupler.Strip>();
        final Map<String,float[]> raw=new LinkedHashMap<String,float[]>();

        void put(int index,boolean mirrored,float axis,float root,float leaf)
        {
            double theta=2*Math.PI*index/N;
            float[] upper=matrix((float)Math.cos(theta),
                    (float)Math.sin(theta),mirrored);
            float[] lower=matrix((float)Math.cos(theta),
                    (float)Math.sin(theta),mirrored);
            lower[13]=-.25F;
            String rootName="upper"+index,leafName="lower"+index;
            strips.add(new SkirtClothPoseCoupler.Strip(
                    rootName,leafName,upper,lower));
            raw.put(rootName,new float[] {axis,0F,0F,root});
            raw.put(leafName,new float[] {axis,0F,0F,leaf});
        }
    }

    private static float[] matrix(float x,float z,boolean rear)
    {
        float[] m=new float[16];
        m[0]=rear?-1F:1F;
        m[5]=1F;
        m[10]=rear?-1F:1F;
        m[15]=1F;
        m[12]=x;
        m[14]=z;
        return m;
    }

    private static void near(float actual,float expected,float epsilon,
            String label)
    {
        require(Math.abs(actual-expected)<=epsilon,
                label+": expected "+expected+" got "+actual);
    }

    private static void require(boolean result,String message)
    {
        if(!result)throw new AssertionError(message);
    }
}

package com.example.examplemod.physics;

import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.examplemod.JoltPhysicsOfflineRunner;

/** Real Java8 Jolt JNI: random seek to tick 40 MUST compute all prior
 * animated parents and yield the same result as sequential 0..40 playback.
 */
public final class JoltOfflineReplayNativeSmoke
{
    private JoltOfflineReplayNativeSmoke() {}

    private static float[] matrix(float x,float y,float z)
    {
        return new float[]{-1F,0F,0F,0F,0F,1F,0F,0F,
                0F,0F,-1F,0F,x,y,z,1F};
    }

    private static void near(Map<String,float[]> a,Map<String,float[]> b)
    {
        if(a==null || b==null || !a.keySet().equals(b.keySet()))
            throw new AssertionError("Replay lost source bones");
        for(String name:a.keySet())
        {
            float[] x=a.get(name),y=b.get(name);
            for(int i=0;i<x.length;i++)
                if(Math.abs(x[i]-y[i])>0.0005F)
                    throw new AssertionError("Non-deterministic physical replay "
                            +name+"["+i+"]="+x[i]+"/"+y[i]);
        }
    }

    private static JoltPhysicsOfflineRunner create(
            final AtomicInteger supplied)
    {
        return new JoltPhysicsOfflineRunner(new File("."),"hair/offline",
                0,121,new JoltPhysicsOfflineRunner.InputSource()
                {
                    @Override public JoltPhysicsOfflineRunner.FrameInput at(int tick)
                    {
                        supplied.incrementAndGet();
                        float x=3F+tick*.013F;
                        String[] names={"back_strand_1","back_strand_2",
                                "back_strand_3"};
                        float[][] bones={
                                matrix(x,5F,-2F),
                                matrix(x,4.82F,-1.91F),
                                matrix(x,4.64F,-1.82F)
                        };
                        float[] tip={x,4.46F,-1.73F};
                        return new JoltPhysicsOfflineRunner.FrameInput(
                                names,bones,tip,matrix(x,5F,-2F),
                                .45F,.28F,1F,null,null,0F,null,null);
                    }
                });
    }

    public static void main(String[] args)
    {
        AtomicInteger sourceReads=new AtomicInteger();
        JoltPhysicsOfflineRunner direct=create(sourceReads);
        JoltPhysicsOfflineRunner ordered=create(new AtomicInteger());
        try
        {
            Map<String,float[]> at40=direct.evaluate(40,7L);
            if(sourceReads.get()!=41 || direct.cachedFrames()!=41)
                throw new AssertionError("Seek fabricated missing animated frames");
            Map<String,float[]> history12=direct.evaluate(12,7L);
            if(sourceReads.get()!=41)
                throw new AssertionError("Backward cached seek reset physics");
            for(int tick=0;tick<=40;tick++)ordered.evaluate(tick,7L);
            near(at40,ordered.evaluate(40,7L));
            near(history12,ordered.evaluate(12,7L));
            at40.get("back_strand_1")[3]=Float.NaN;
            if(!Float.isFinite(direct.evaluate(40,7L).get("back_strand_1")[3]))
                throw new AssertionError("Mutable JNI frame buffer leaked");
            direct.evaluate(40,8L); // an edited Record revision
            if(sourceReads.get()!=82)
                throw new AssertionError("Edited actor did not reconstruct all Jolt ticks");
            System.out.println("Native Jolt offline frame history/direct seek parity: PASS");
        }
        finally {direct.close();ordered.close();}
    }
}

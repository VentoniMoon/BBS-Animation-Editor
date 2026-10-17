package com.example.examplemod;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Pure Java8 contract tests. Simulates actor movement + changing contact
 * impulses with a deterministic reference solver, without running Minecraft.
 */
public final class JoltPhysicsTimelineSmoke
{
    private JoltPhysicsTimelineSmoke() {}

    private static final class Input
    {
        final float actorX, boneY, chestZ;
        Input(float a,float b,float c) {actorX=a;boneY=b;chestZ=c;}
    }

    private static final class ReferenceSolver
            implements JoltPhysicsTimeline.Solver<Input,float[]>
    {
        int frame=-1,resetCount;
        float velocity, position;

        @Override public float[] initialize(int tick,Input input)
        {
            if(frame!=-1)throw new AssertionError("Initialized over live scene");
            frame=tick;
            position=input.actorX+input.boneY+input.chestZ;
            velocity=0F;
            return new float[]{position,velocity};
        }

        @Override public float[] advance(int tick,Input input)
        {
            if(tick!=frame+1)throw new AssertionError("Skipped native tick");
            frame=tick;
            // Test uses previous velocity and time-varying external poses
            velocity=velocity*0.91F+0.13F*
                    (input.actorX+input.boneY-input.chestZ-position);
            position+=velocity;
            return new float[]{position,velocity};
        }
        @Override public void reset()
        {
            resetCount++;
            frame=-1;velocity=0F;position=0F;
        }
    }

    private static void near(float[] lhs,float[] rhs)
    {
        if(lhs.length!=rhs.length)throw new AssertionError("Wrong snapshot size");
        for(int i=0;i<lhs.length;i++)
            if(Math.abs(lhs[i]-rhs[i])>0.00001F)
                throw new AssertionError("History changed: "+lhs[i]+" vs "+rhs[i]);
    }

    private static JoltPhysicsTimeline<Input,float[]> make(
            final AtomicInteger sourceReads,ReferenceSolver solver)
    {
        return new JoltPhysicsTimeline<Input,float[]>(0,201,
                new JoltPhysicsTimeline.FrameSource<Input>() {
                    @Override public Input inputAt(int frame)
                    {
                        sourceReads.incrementAndGet();
                        return new Input(frame*0.025F,
                                (float)Math.sin(frame*0.13D),
                                (float)Math.cos(frame*0.19D)*0.15F);
                    }
                }, solver,
                new JoltPhysicsTimeline.Copier<float[]>() {
                    @Override public float[] copy(float[] source)
                    {
                        return source.clone();
                    }
                });
    }

    public static void main(String[] args)
    {
        AtomicInteger inputs=new AtomicInteger();
        ReferenceSolver mainSolver=new ReferenceSolver();
        JoltPhysicsTimeline<Input,float[]> timeline=make(inputs,mainSolver);
        float[] at100=timeline.evaluate(100,11L);
        if(inputs.get()!=101 || timeline.cachedFrames()!=101 ||
                timeline.latestFrame()!=100)
            throw new AssertionError("Seek to 100 skipped recorded world poses");
        float[] old20=timeline.evaluate(20,11L);
        if(inputs.get()!=101 || mainSolver.frame!=100)
            throw new AssertionError("Backward look-up mutated live native scene");
        old20[0]=Float.NaN;
        Map<Integer,float[]> frozen=timeline.snapshot();
        frozen.get(20)[1]=Float.POSITIVE_INFINITY;
        near(timeline.evaluate(100,11L),at100);
        if(!Float.isFinite(timeline.evaluate(20,11L)[0]) ||
                !Float.isFinite(timeline.evaluate(20,11L)[1]))
            throw new AssertionError("Returned mutable internal frame buffers");
        timeline.evaluate(150,11L);
        if(mainSolver.frame!=150 || inputs.get()!=151)
            throw new AssertionError("Future seek replayed unnecessary past steps");

        AtomicInteger directInput=new AtomicInteger();
        JoltPhysicsTimeline<Input,float[]> direct=make(directInput,new ReferenceSolver());
        float[] straight150=direct.evaluate(150,11L);
        near(straight150,timeline.evaluate(150,11L));
        near(at100,direct.evaluate(100,11L));

        timeline.evaluate(42,12L);
        if(timeline.latestFrame()!=42 || timeline.cachedFrames()!=43 ||
                inputs.get()!=194 || mainSolver.resetCount<2)
            throw new AssertionError("Source revision did not restart replay");
        try
        {
            timeline.evaluate(201,12L);
            throw new AssertionError("Too-long replay accepted silently");
        }
        catch(IllegalArgumentException expected) {}
        timeline.close();
        direct.close();
        try
        {
            timeline.evaluate(0,12L);
            throw new AssertionError("Closed native replay was restarted");
        }
        catch(IllegalStateException expected) {}
        System.out.println("Deterministic Jolt timeline replay/seek/edits/immutability: PASS");
    }
}

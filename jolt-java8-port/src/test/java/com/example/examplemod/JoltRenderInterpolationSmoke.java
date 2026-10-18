package com.example.examplemod;

import java.util.Map;

/** Verify that the render bridge samples fractional Jolt swings WITHOUT
 * ticking or modifying its deterministic 20Hz physics snapshots.
 */
public final class JoltRenderInterpolationSmoke
{
    private JoltRenderInterpolationSmoke() {}
    private static void near(float actual,float want)
    {
        if(Math.abs(actual-want)>0.0005F)
            throw new AssertionError(actual+" != "+want);
    }
    public static void main(String[] args)
    {
        BodyPartModelData part=new BodyPartModelData();
        BodyPartPhysicsFrameState frame=new BodyPartPhysicsFrameState();
        frame.beginFrame(0);
        frame.accept(part,0,"hair",new float[]{0,1,0,0},true);
        frame.publishEditedFrame(part,0);
        frame.beginFrame(1);
        frame.accept(part,1,"hair",
                new float[]{0,1,0,(float)Math.PI/2F},true);
        Map<String,float[]> previous=frame.forRender(part,1);
        near(previous.get("hair")[3],0F);
        Map<String,float[]> midpoint=frame.forRenderInterpolated(part,1,0.5F);
        near(midpoint.get("hair")[3],(float)Math.PI/4F);
        midpoint.get("hair")[3]=Float.NaN;
        near(frame.forRenderInterpolated(part,1,0.5F)
                .get("hair")[3],(float)Math.PI/4F);
        near(frame.forRenderInterpolated(part,1,0F)
                .get("hair")[3],0F);
        near(frame.forRenderInterpolated(part,1,1F)
                .get("hair")[3],(float)Math.PI/2F);

        frame.beginFrame(4); // unknown intervening frame, no stale blend
        frame.accept(part,4,"hair",new float[]{0,1,0,1F},true);
        if(frame.forRenderInterpolated(part,4,0.5F)!=null)
            throw new AssertionError("Scrub applied previous frame physics");
        frame.publishEditedFrame(part,4);
        near(frame.forRenderInterpolated(part,4,0.5F)
                .get("hair")[3],1F);
        frame.beginFrame(5);
        frame.accept(part,5,"hair",new float[]{0,1,0,2F},false);
        near(frame.forRenderInterpolated(part,5,0.5F)
                .get("hair")[3],1F);
        System.out.println("Jolt fractional frame interpolation/no render state mutation: PASS");
    }
}

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
        // Preview sees the previous tick; bake MUST wait for the
        // same-frame sample while ordinary interpolation remains unchanged.
        if(frame.forBakeRender(part,1)!=null)
            throw new AssertionError("Bake accepted delayed frame 0 as frame 1");
        BodyPartPhysicsFrameState bakeFrame=new BodyPartPhysicsFrameState();
        bakeFrame.beginFrame(0);
        bakeFrame.accept(part,0,"hair",new float[]{0,1,0,0F},true);
        bakeFrame.publishEditedFrame(part,0);
        bakeFrame.beginFrame(1);
        bakeFrame.accept(part,1,"hair",
                new float[]{0,1,0,(float)Math.PI/2F},true);
        if(bakeFrame.forBakeRender(part,1)!=null)
            throw new AssertionError("Bake captured prior frame before publish");
        bakeFrame.publishEditedFrame(part,1);
        near(bakeFrame.forBakeRender(part,1).get("hair")[3],(float)Math.PI/2F);
        bakeFrame.beginFrame(2);
        if(bakeFrame.forBakeRender(part,2)!=null)
            throw new AssertionError("Bake captured previous frame after tick change");

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

package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;

/**
 * Reproduces legacy 68.8-degree clamp vs full Jolt swing, with render-frame
 * delay and per-bone source metadata. No Minecraft libraries are required.
 */
public final class NativePreviewAngleSmoke {
    public static final class FakeTransform {
        public float rotateX, rotateY, rotateZ;
    }
    public static final class FakePose {
        public final Map<Object,Object> bones = new HashMap<Object,Object>();
    }
    public static final class FakeMorph {
        public final FakePose pose = new FakePose();
        FakeMorph() { pose.bones.put("back", new FakeTransform()); }
    }

    private static void near(float actual, float expected) {
        if(Math.abs(actual-expected)>0.001F)
            throw new AssertionError("Wanted "+expected+" but got "+actual);
    }

    public static void main(String[] args) {
        BodyPartModelData hair = new BodyPartModelData();
        BodyPartPhysicsFrameState state = new BodyPartPhysicsFrameState();
        float[] jolt = new float[] {1F,0F,0F,2.2F};
        state.beginFrame(20);
        state.accept(hair,20,"back",jolt,true);

        if(state.forRender(hair,20)!=null)
            throw new AssertionError("New solver output leaked mid-render");
        state.beginFrame(21);
        if(!state.isJoltForRender(hair,21,"back"))
            throw new AssertionError("Jolt provenance lost across frame boundary");
        float[] visible=state.forRender(hair,21).get("back");
        FakeMorph nativeMorph=new FakeMorph();
        float limit=state.isJoltForRender(hair,21,"back")?(float)Math.PI:1.20F;
        if(!BodyPartChameleonPhysicsPose.addLocalSwing(nativeMorph,"back",visible,limit))
            throw new AssertionError("Unable to apply Jolt pose");
        near(((FakeTransform)nativeMorph.pose.bones.get("back")).rotateX,2.2F);

        FakeMorph oldMorph=new FakeMorph();
        if(!BodyPartChameleonPhysicsPose.addLocalSwing(oldMorph,"back",visible))
            throw new AssertionError("Legacy pose failed");
        near(((FakeTransform)oldMorph.pose.bones.get("back")).rotateX,1.20F);

        state.accept(hair,21,"back",new float[] {1F,0F,0F,0.4F},false);
        state.publishEditedFrame(hair,21);
        if(state.isJoltForRender(hair,21,"back"))
            throw new AssertionError("Legacy output retained stale Jolt flag");

        state.beginFrame(35);
        if(state.forRender(hair,35)!=null || state.isJoltForRender(hair,35,"back"))
            throw new AssertionError("Stale Jolt swing survived a timeline seek");
        System.out.println("Native and legacy preview angle isolation: PASS");
    }
}

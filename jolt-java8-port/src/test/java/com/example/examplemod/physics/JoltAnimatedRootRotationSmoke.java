package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Exercise the actual native Jolt connector with a stationary actor.
 * Rotating an authored hair-root bone must move the free physical tip even
 * when the root pivot position never moves in world coordinates.
 * Moving the actor alone is covered by JoltActorTravelInertiaSmoke.
 */
public final class JoltAnimatedRootRotationSmoke {
    private JoltAnimatedRootRotationSmoke() {}

    private static float[] rotationX(float degrees) {
        float a=(float)Math.toRadians(degrees);
        float c=(float)Math.cos(a),s=(float)Math.sin(a);
        return new float[]{
                1F,0F,0F,0F, 0F,c,s,0F,
                0F,-s,c,0F, 0F,0F,0F,1F
        };
    }

    private static float[] rootBone(float degrees) {
        float[] m=rotationX(degrees);
        m[13]=1.8F;
        return m;
    }

    private static float[][] bones(float degrees) {
        float[][] b={rootBone(degrees),rootBone(degrees),rootBone(degrees)};
        b[1][13]=1.6F;b[2][13]=1.4F;
        return b;
    }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt factory");
        Jolt.registerTypes();
        float[] parent=rotationX(0);
        float[] end={0F,1.2F,0F};
        JoltChameleonPreviewRig reference=JoltChameleonPreviewRig.create(
                0,bones(0),end,parent,.65F,.55F,.6F);
        JoltChameleonPreviewRig turned=JoltChameleonPreviewRig.create(
                0,bones(0),end,parent,.65F,.55F,.6F);
        try {
            reference.setGravity(.5F);turned.setGravity(.5F);
            reference.settle(12);turned.settle(12);
            float maxResponse=0F;
            for(int tick=1;tick<=35;tick++) {
                float angle=Math.min(60F,tick*5F);
                reference.advance(tick,new float[]{0F,1.8F,0F},parent,
                        bones(0));
                turned.advance(tick,new float[]{0F,1.8F,0F},parent,
                        bones(angle));
                float[] a=reference.lastBodyPosition();
                float[] b=turned.lastBodyPosition();
                float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
                float shift=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(!Float.isFinite(shift))
                    throw new AssertionError("Nonfinite rotation response");
                maxResponse=Math.max(maxResponse,shift);
            }
            if(maxResponse<.02F)
                throw new AssertionError("Root rotation ignored: max physical tip response="+maxResponse);
            System.out.println("Animated source-bone rotation produces native Jolt inertia: PASS displacement="+maxResponse);
        } finally { reference.close(); turned.close(); }
    }
}

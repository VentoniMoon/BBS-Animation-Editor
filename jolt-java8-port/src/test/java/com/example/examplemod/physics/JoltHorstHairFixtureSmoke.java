package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Geometry regression from the user's provided hair body1.geo.json.
 * Contains only bone pivot and terminal cube measurements, NOT the model.
 * Tests all three distinct chain shapes against the real native Jolt solver.
 */
public final class JoltHorstHairFixtureSmoke {
    private JoltHorstHairFixtureSmoke() { }

    private static float[] m(float xPx,float yPx,float zPx) {
        // Source anchor rotates Y=180; output is model-relative WORLD blocks.
        return new float[] {-1F,0F,0F,0F,0F,1F,0F,0F,
                0F,0F,-1F,0F,-xPx/16F,yPx/16F,-zPx/16F,1F};
    }

    private static void test(String name,float[][] pivots,float[] tip) {
        float[] anchor=m(0F,6F,0F);
        float[] sourceTip=new float[] {-tip[0]/16F,tip[1]/16F,-tip[2]/16F};
        JoltChameleonPreviewRig rig=JoltChameleonPreviewRig.create(0,
                pivots,sourceTip,anchor,0.15F,0.30F,0.60F);
        try {
            float[] first=JoltChameleonPreviewRig.position(pivots[0]);
            for(int i=1;i<=150;i++) {
                rig.advance(i,first,anchor);
                float[][] swings=rig.localBoneSwings(pivots,anchor);
                if(swings.length!=pivots.length)
                    throw new AssertionError(name+" lost segments");
                for(float[] axisAngle:swings)for(float n:axisAngle)
                    if(!Float.isFinite(n))
                        throw new AssertionError(name+" produced non-finite swing");
            }
            System.out.println("Horst "+name+" ("+pivots.length+" bones): PASS");
        } finally {rig.close();}
    }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new IllegalStateException("Jolt factory");
        Jolt.registerTypes();

        // Pivots in original Blockbench pixel coordinates.
        test("left",new float[][] {
                m(-3.08F,12F,-1.58F),m(-3.08F,9F,-2.08F),
                m(-3.08F,6F,-2.08F)
        },new float[] {-3.08F,1F,-1.58F});

        test("right",new float[][] {
                m(3.08F,12F,-1.58F),m(3.08F,9F,-2.08F),
                m(3.08F,6F,-2.08F)
        },new float[] {3.08F,1F,-1.58F});

        test("back",new float[][] {
                m(0F,12F,1.5F),m(0F,9F,2.08F),
                m(0F,6F,2.08F),m(0F,3F,2.08F)
        },new float[] {0F,0.92F,1.5F});
    }
}

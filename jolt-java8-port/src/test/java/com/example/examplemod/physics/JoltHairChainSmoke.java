package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.RVec3;

/** Java 8 real-native Jolt smoke test for a four-segment hair chain. */
public final class JoltHairChainSmoke {
    private JoltHairChainSmoke() { }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if (!Jolt.newFactory()) throw new IllegalStateException("No Jolt factory");
        Jolt.registerTypes();

        JoltHairChain chain = new JoltHairChain(4, 0.25F, 2F, 0.15F, 0.25F, 0.6F);
        try {
            // Repeated 20Hz samples emulate the character swaying its low_body.
            for (int t = 0; t < 10; t++) {
                chain.moveAnchor(t < 5 ? 0.35D : 0D, 2D, 0D,
                        0F, 0F, 0F, 1F);
                chain.step();
            }
            double movingX = chain.position(3).xx();
            for (int t = 0; t < 300; t++) {
                chain.moveAnchor(0D, 2D, 0D, 0F, 0F, 0F, 1F);
                chain.step();
            }
            RVec3 end = chain.position(3);
            double tipX = end.xx(), tipY = end.yy();
            if (!Double.isFinite(tipX) || !Double.isFinite(tipY))
                throw new AssertionError("Non-finite tip pose");
            if (tipY > 1.40D || tipY < 0.60D)
                throw new AssertionError("Hair did not hang from anchor; tipY=" + tipY);
            if (Math.abs(tipX) > 0.35D)
                throw new AssertionError("Hair failed to relax after sway; tipX=" + tipX);
            System.out.println("JoltHairChain 4-link: PASS; movingX=" + movingX
                    + " settled tip=(" + tipX + ", " + tipY + ")");
        } finally {
            chain.close();
        }
    }
}

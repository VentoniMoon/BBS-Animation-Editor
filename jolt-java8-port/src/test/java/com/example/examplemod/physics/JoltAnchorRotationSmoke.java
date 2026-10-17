package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.RVec3;

/**
 * Regression stand: rotating a strand's kinematic anchor around X through
 * 60 degrees must not teleport physical particles above the attachment.
 */
public final class JoltAnchorRotationSmoke {
    private JoltAnchorRotationSmoke() { }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if (!Jolt.newFactory()) throw new IllegalStateException("No Jolt factory");
        Jolt.registerTypes();
        JoltHairChain chain = new JoltHairChain(4, 0.25F, 2F, 0.10F, 0.35F, 0.60F);
        try {
            for (int i = 0; i < 30; i++) chain.step();
            double[] angles = {0, 15, 30, 45, 55, 59, 60, 61, 65, 70, 65,
                    61, 60, 59, 55, 45, 30, 15, 0};
            RVec3 previous = chain.position(3);
            double maxDisplacement = 0D, highest = Double.NEGATIVE_INFINITY;
            for (double angle : angles) {
                double half = Math.toRadians(angle) * 0.5D;
                chain.moveAnchor(0D, 2D, 0D, (float)Math.sin(half), 0F, 0F,
                        (float)Math.cos(half));
                chain.step();
                RVec3 tip = chain.position(3);
                double dx = tip.xx() - previous.xx();
                double dy = tip.yy() - previous.yy();
                double dz = tip.zz() - previous.zz();
                double shift = Math.sqrt(dx * dx + dy * dy + dz * dz);
                maxDisplacement = Math.max(maxDisplacement, shift);
                highest = Math.max(highest, tip.yy());
                if (!Double.isFinite(shift) || shift > 0.45D)
                    throw new AssertionError("Large jump at root X=" + angle + " shift=" + shift);
                previous = tip;
            }
            for (int t = 0; t < 240; t++) {
                chain.moveAnchor(0D, 2D, 0D, 0F, 0F, 0F, 1F);
                chain.step();
            }
            RVec3 settled = chain.position(3);
            if (!Double.isFinite(settled.yy()) ||
                    settled.yy() < 0.55D || settled.yy() > 1.50D)
                throw new AssertionError("No gravity-restored pose: " + settled.yy());
            System.out.println("Jolt anchor rotation 55-70deg: PASS, maxJump="
                    + maxDisplacement + ", highestTip=" + highest
                    + ", settledY=" + settled.yy());
        } finally {
            chain.close();
        }
    }
}

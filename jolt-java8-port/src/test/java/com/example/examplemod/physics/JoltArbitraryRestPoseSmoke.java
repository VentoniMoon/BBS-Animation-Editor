package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.RVec3;

/** Test a tilted model rooted in arbitrary world space, not only at (0,y,0). */
public final class JoltArbitraryRestPoseSmoke {
    private JoltArbitraryRestPoseSmoke() { }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if (!Jolt.newFactory()) throw new IllegalStateException("Jolt factory unavailable");
        Jolt.registerTypes();

        JoltHairChain rig = new JoltHairChain(4, 0.25F,
                3F, 5F, -2F, 0F, -0.50F, 0.8660254F,
                0.12F, 0.30F, 0.60F);
        try {
            RVec3 initial = rig.position(3);
            if (Math.abs(initial.xx() - 3D) > 0.01D ||
                    Math.abs(initial.yy() - 4.5625D) > 0.01D ||
                    Math.abs(initial.zz() + 1.24223D) > 0.01D)
                throw new AssertionError("Unexpected tilted rest pose: "
                        + initial.xx() + "," + initial.yy() + "," + initial.zz());
            float[] q = rig.initialWorldRotation();
            for (int i=0; i<180; i++) {
                rig.moveAnchor(3D, 5D, -2D, q[0], q[1], q[2], q[3]);
                rig.step();
            }
            RVec3 finalTip = rig.position(3);
            if (!Double.isFinite(finalTip.yy()) ||
                    finalTip.yy() >= 4.5D || Math.abs(finalTip.xx()-3D) > 0.4D)
                throw new AssertionError("Gravity did not settle tilted hair: "
                        + finalTip.xx() + "," + finalTip.yy() + "," + finalTip.zz());
            System.out.println("Jolt arbitrary Chameleon rest pose: PASS; tipY=" + finalTip.yy());
        } finally {
            rig.close();
        }
    }
}

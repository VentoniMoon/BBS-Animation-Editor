package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Root-attached decorative scalp cubes must not kick out their own first
 * hair link. External chest boxes are checked separately in
 * JoltFirstLinkChestContactSmoke.
 */
public final class JoltOwnRootColliderExclusionSmoke {
    private JoltOwnRootColliderExclusionSmoke() {}

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Missing native Jolt");
        Jolt.registerTypes();
        float[][] pivots={{0F,2F,0F},{0F,1.65F,0F}};
        JoltHairChain free=new JoltHairChain(pivots,.6F,.5F,.6F);
        JoltHairChain sameRoot=new JoltHairChain(pivots,.6F,.5F,.6F);
        try {
            // This decor box surrounds the kinematic anchor. Only the first
            // hair link exists, so ANY collision would incorrectly eject it.
            sameRoot.setAdditionalBoxes(new float[][]{
                    {0F,2F,0F,0F,0F,0F,1F,.12F,.06F,.12F}
            });
            double maxDeviation=0D;
            for(int i=0;i<75;i++) {
                free.step();sameRoot.step();
                com.github.stephengold.joltjni.RVec3 a=free.position(0);
                com.github.stephengold.joltjni.RVec3 b=sameRoot.position(0);
                double dx=a.xx()-b.xx(),dy=a.yy()-b.yy(),dz=a.zz()-b.zz();
                maxDeviation=Math.max(maxDeviation,
                        Math.sqrt(dx*dx+dy*dy+dz*dz));
            }
            if(maxDeviation>.005D)
                throw new AssertionError("Own scalp collider disturbed hair root: "+maxDeviation);
            System.out.println("Attached root-scap capsule exclusion: PASS maxDiff="+maxDeviation);
        } finally {free.close();sameRoot.close();}
    }
}

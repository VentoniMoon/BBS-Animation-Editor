package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Regression for front strands clipping straight through an added chest:
 * a one-bone dynamic strand has ONLY link 1. Old filtering disabled
 * collision of link 1 with EVERY BodyPart collider.
 */
public final class JoltFirstLinkChestContactSmoke {
    private JoltFirstLinkChestContactSmoke() {}

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt factory");
        Jolt.registerTypes();
        float[][] joints={{0F,1.95F,0F},{0F,1.53F,0F}};
        JoltHairChain free=new JoltHairChain(joints,0.35F,0.45F,0.5F);
        JoltHairChain chest=new JoltHairChain(joints,0.35F,0.45F,0.5F);
        try {
            // Root y=1.95 is OUTSIDE box; its first dynamic link center
            // y=1.74 overlaps the box. The box is slightly left of hair
            // to create a unique outwards collision normal (+X).
            float[][] box={{-0.15F,1.66F,0F,0F,0F,0F,1F,
                    0.19F,0.14F,0.18F}};
            chest.setAdditionalBoxes(box);
            double maxOffset=0D;
            for(int i=0;i<90;i++) {
                free.step();chest.step();
                double xFree=free.position(0).xx();
                double xChest=chest.position(0).xx();
                if(!Double.isFinite(xChest))throw new AssertionError("Nonfinite contact");
                maxOffset=Math.max(maxOffset,Math.abs(xChest-xFree));
            }
            if(maxOffset<0.008D)
                throw new AssertionError("First link ignored chest collider: "+maxOffset);
            System.out.println("First hair link responds to external chest: PASS offset="+maxOffset);
        } finally {
            free.close();chest.close();
        }
    }
}

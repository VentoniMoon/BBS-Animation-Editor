package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** First link must interact with an external torso capsule, while a root
 * enclosure must not repel its own attachment. Both are native JNI tests.
 */
public final class JoltFirstLinkTorsoContactSmoke {
    private JoltFirstLinkTorsoContactSmoke() {}
    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt factory");
        Jolt.registerTypes();
        float[][] pivots={{0F,1.95F,0F},{0F,1.53F,0F}};
        JoltHairChain free=new JoltHairChain(pivots,.4F,.45F,.5F);
        JoltHairChain torso=new JoltHairChain(pivots,.4F,.45F,.5F);
        try {
            // Horizontal shoulder/chest capsule below root, intersecting
            // a portion of the otherwise exposed first dynamic segment.
            torso.setTorsoCollider(
                    new float[]{-.15F,1.55F,-.2F},
                    new float[]{-.15F,1.55F,.2F},.16F);
            double maxX=0D;
            for(int tick=0;tick<90;tick++) {
                free.step();torso.step();
                double x=free.position(0).xx()-torso.position(0).xx();
                if(!Double.isFinite(x))throw new AssertionError("Nonfinite torso collision");
                maxX=Math.max(maxX,Math.abs(x));
            }
            if(maxX<.008D)
                throw new AssertionError("First hair link ignores torso capsule: "+maxX);
            System.out.println("First hair link torso contact: PASS displacement="+maxX);
        } finally {free.close();torso.close();}
    }
}

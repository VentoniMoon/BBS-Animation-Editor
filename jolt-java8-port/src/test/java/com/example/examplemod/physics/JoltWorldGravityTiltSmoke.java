package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** A tilted actor's hair must eventually point DOWN in world coordinates.
 * This tests gravity vs angular motors, not just that a 60-degree head key
 * produces some motion. No torso contacts; isolate physical suspension.
 */
public final class JoltWorldGravityTiltSmoke {
    private JoltWorldGravityTiltSmoke() {}
    private static float[] bone(float deg,float y) {
        float a=(float)Math.toRadians(deg);
        float c=(float)Math.cos(a),s=(float)Math.sin(a);
        return new float[]{1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,y,0,1};
    }
    private static float[][] bones(float deg) {
        return new float[][] {bone(deg,2.2F),bone(deg,1.93F),
                bone(deg,1.66F),bone(deg,1.39F)};
    }
    private static float tipY(JoltChameleonPreviewRig rig) {
        float[][] dirs=rig.rawPhysicalWorldDirections();
        return dirs[0][1];
    }
    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Native Jolt unavailable");
        Jolt.registerTypes();
        float[] parent=bone(0,0);
        JoltChameleonPreviewRig tilted=JoltChameleonPreviewRig.create(
                0,bones(0),new float[]{0,1.12F,0},parent,.55F,.25F,1F);
        try {
            tilted.setGravity(1F);
            tilted.settle(20);
            float strongest=0F;
            for(int frame=1;frame<=120;frame++){
                float angle=Math.min(75F,frame*7.5F);
                tilted.advance(frame,new float[]{0,2.2F,0},parent,bones(angle));
                if(frame==20)strongest=tipY(tilted);
            }
            float finalY=tipY(tilted);
            if(!(finalY<-.65F))
                throw new AssertionError("World gravity no longer hangs hair down at 75-degree tilt: "+finalY+" early="+strongest);
            System.out.println("World gravity still points down after animated tilt: PASS finalY="+finalY+" at frame20="+strongest);
        } finally {tilted.close();}
    }
}

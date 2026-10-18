package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Native regression: physics must receive keyed INNER hair-bone rotations,
 * not only world actor travel and the first chain bone's animation.
 */
public final class JoltAnimatedInternalBoneSmoke {
    private JoltAnimatedInternalBoneSmoke() {}

    private static float[] bone(float rotationX,float y) {
        float radians=(float)Math.toRadians(rotationX);
        float c=(float)Math.cos(radians),s=(float)Math.sin(radians);
        return new float[]{1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,y,0,1};
    }
    private static float[][] bones(float middleAngle) {
        return new float[][]{bone(0F,2.1F),
                bone(middleAngle,1.85F),bone(middleAngle,1.60F)};
    }
    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("Missing Jolt factory");
        Jolt.registerTypes();
        float[] parent=bone(0F,0F);
        float[] tip={0F,1.35F,0F};
        JoltChameleonPreviewRig still=JoltChameleonPreviewRig.create(
                0,bones(0F),tip,parent,0.8F,0.5F,0.5F);
        JoltChameleonPreviewRig keyed=JoltChameleonPreviewRig.create(
                0,bones(0F),tip,parent,0.8F,0.5F,0.5F);
        try {
            still.setGravity(0.25F);keyed.setGravity(0.25F);
            still.settle(12);keyed.settle(12);
            float max=0F;
            for(int frame=1;frame<=35;frame++) {
                float degrees=Math.min(55F,frame*5F);
                still.advance(frame,new float[]{0F,2.1F,0F},parent,bones(0F));
                keyed.advance(frame,new float[]{0F,2.1F,0F},parent,bones(degrees));
                float[] a=still.lastBodyPosition(),b=keyed.lastBodyPosition();
                float dx=b[0]-a[0],dy=b[1]-a[1],dz=b[2]-a[2];
                float distance=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                if(!Float.isFinite(distance))
                    throw new AssertionError("Nonfinite internal rotation");
                max=Math.max(max,distance);
            }
            if(max<0.02F)
                throw new AssertionError("Inner hair bone animation ignored by native solver: "+max);
            System.out.println("Native keyed internal hair rotation: PASS displacement="+max);
        } finally {still.close();keyed.close();}
    }
}

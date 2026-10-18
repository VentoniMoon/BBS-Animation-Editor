package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/** Ensure Chameleon's additive LOCAL swing projects the native world-gravity
 * hair pose through a TILTED parent bone. The native body could hang down
 * while an incorrect world-to-local quaternion bridge renders it sideways.
 */
public final class JoltVisualGravityBridgeSmoke {
    private static float[] bone(float deg,float y) {
        float a=(float)Math.toRadians(deg),c=(float)Math.cos(a),s=(float)Math.sin(a);
        return new float[]{1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,y,0,1};
    }
    private static float[][] bones(float deg) {
        return new float[][]{bone(deg,2.2F),bone(deg,1.93F),
                bone(deg,1.66F)};
    }
    private static float[] quatAxisAngle(float[] a){
        float h=a[3]*.5F,ss=(float)Math.sin(h);
        return new float[]{a[0]*ss,a[1]*ss,a[2]*ss,(float)Math.cos(h)};
    }
    private static float[] negativeY(float[] q){
        float x=q[0],y=q[1],z=q[2],w=q[3];
        return new float[]{-2F*(x*y-z*w),-1F+2F*(x*x+z*z),
                -2F*(y*z+x*w)};
    }
    public static void main(String[] args){
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new AssertionError("No Jolt");
        Jolt.registerTypes();
        JoltChameleonPreviewRig rig=JoltChameleonPreviewRig.create(
                0,bones(0),new float[]{0,1.39F,0},bone(0,0),
                .55F,.25F,1F);
        try{
            rig.setGravity(1F);
            rig.settle(20);
            for(int tick=1;tick<=80;tick++){
                float angle=Math.min(75F,tick*7.5F);
                float[] parent=bone(angle,0);
                rig.advance(tick,new float[]{0,2.2F,0},parent,bones(angle));
            }
            float[][] nativeDirections=rig.rawPhysicalWorldDirections();
            float[] cleanParent=bone(75F,0);
            float[][] swings=rig.localBoneSwings(bones(75F),cleanParent);
            float[] delta=quatAxisAngle(swings[0]);
            float[] parentQ=JoltChameleonPreviewRig.rotation(cleanParent);
            // The authoring FIRST hair bone has identity LOCAL rotation,
            // so Chameleon's composed local swing equals this delta.
            float[] visualWorld=JoltBoneQuaternionMapper.multiply(parentQ,delta);
            float[] rendered=negativeY(visualWorld);
            float[] physical=nativeDirections[0];
            float err=0F;
            for(int i=0;i<3;i++){
                float diff=rendered[i]-physical[i];
                err+=diff*diff;
            }
            if(err>.018F)
                throw new AssertionError("Visible additive swing lost world gravity: "+
                        "nativeY="+physical[1]+" renderedY="+rendered[1]+
                        " errorSq="+err);
            if(rendered[1]>-.6F)
                throw new AssertionError("Visible strand stayed in tilted local frame: "+rendered[1]);
            System.out.println("Tilted Emoticons parent -> Chameleon local swing preserves world gravity: PASS "+
                    "nativeY="+physical[1]+" renderedY="+rendered[1]);
        }finally{rig.close();}
    }
}

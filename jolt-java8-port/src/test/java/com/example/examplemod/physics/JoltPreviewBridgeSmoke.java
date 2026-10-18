package com.example.examplemod.physics;

import java.io.File;
import java.util.Map;
import com.example.examplemod.BodyPartJoltPreviewAdapter;

/** Simulates the same opt-in reflection calls used by BodyPartsPreviewRenderer. */
public final class JoltPreviewBridgeSmoke {
    private JoltPreviewBridgeSmoke() { }
    private static float[] matrix(float x,float y,float z) {
        // Column-major 180 degrees around Chameleon Y.
        return new float[] {-1F,0F,0F,0F,0F,1F,0F,0F,
                0F,0F,-1F,0F,x,y,z,1F};
    }
    private static boolean sameSample(Map<String,float[]> a,Map<String,float[]> b) {
        if(a==null || b==null || a.size()!=b.size())return false;
        for(String name:a.keySet()) {
            float[] x=a.get(name),y=b.get(name);
            if(x==null || y==null || x.length!=y.length)return false;
            for(int i=0;i<x.length;i++)
                if(Float.floatToIntBits(x[i])!=Float.floatToIntBits(y[i]))return false;
        }
        return true;
    }
    public static void main(String[] args) {
        if(!BodyPartJoltPreviewAdapter.isRequested())
            throw new AssertionError("Missing opt-in Java property");
        File data = new File(".");
        String[] bones={"back_strand_1","back_strand_2",
                "back_strand_3","back_strand_4"};
        float[][] world=new float[bones.length][];
        float dy=-0.125F,dz=0.21650635F;
        for(int i=0;i<world.length;i++)
            world[i]=matrix(3F,5F+i*dy,-2F+i*dz);
        float[] tip={3F,5F+4F*dy,-2F+4F*dz};
        BodyPartJoltPreviewAdapter bridge=new BodyPartJoltPreviewAdapter();
        try {
            Map<String,float[]> first=bridge.sample(data,"hair/back",bones,0,
                    world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1.0F);
            if(first==null || first.size()!=bones.length ||
                    !bridge.wasRecreatedThisSample())
                throw new AssertionError("Jolt first frame must return settled bone rotations");
            if(Boolean.getBoolean("bbs.editor.physics.jolt.diagnostic")) {
                Map<String,float[]> direct=bridge.rawDirectionsFor("hair/back",0);
                if(direct.size()!=bones.length)
                    throw new AssertionError("Native body direction API did not return every bone");
                for(float[] direction:direct.values()) {
                    float norm=direction[0]*direction[0]+
                            direction[1]*direction[1]+direction[2]*direction[2];
                    if(Math.abs(norm-1F)>0.001F)
                        throw new AssertionError("Invalid native Jolt direction");
                }
            }
            Map<String,float[]> original12=null;
            for(int tick=1;tick<=50;tick++) {
                Map<String,float[]> result=bridge.sample(data,"hair/back",bones,
                        tick,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
                if(result==null || result.size()!=4)
                    throw new AssertionError("No physical preview swing at frame "+tick);
                if(tick==12)original12=result;
                for(float[] swing:result.values())
                    for(float value:swing)if(!Float.isFinite(value))
                        throw new AssertionError("Non-finite visual pose");
            }
            Map<String,float[]> repeated=bridge.sample(data,"hair/back",bones,
                    50,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(repeated==null || repeated.size()!=4)
                throw new AssertionError("Repeated render must reuse snapshot");
            if(bridge.wasRecreatedThisSample())
                throw new AssertionError("Repeated paused frame unnecessarily rebuilt Jolt scene");
            // Already simulated source ticks are now read from immutable
            // recorded snapshots. No native reinitialization may destroy
            // accumulated inertia merely because the playhead moved back.
            Map<String,float[]> seek=bridge.sample(data,"hair/back",bones,
                    12,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(seek==null || seek.size()!=bones.length ||
                    bridge.wasRecreatedThisSample() ||
                    !sameSample(seek,original12))
                throw new AssertionError("Backward seek failed exact Jolt history replay");
            Map<String,float[]> cached20=bridge.sample(data,"hair/back",bones,
                    20,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(cached20==null || bridge.wasRecreatedThisSample())
                throw new AssertionError("Previously simulated forward seek reset rig");
            // Return to earlier frame before exercising paused 200-pass
            // render feedback; the highest native scene tick remains 50.
            seek=bridge.sample(data,"hair/back",bones,
                    12,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            // The editor can render ~200 times while the timeline is at
            // a single frame. Render interpolation or physics-feedback must
            // NEVER reinitialize a rig on those draws, even if clean matrices
            // shift around within the same nominal frame.
            float[][] noisy=world.clone();
            noisy[2]=noisy[2].clone();
            noisy[2][12]+=0.12F;
            noisy[0]=noisy[0].clone();
            noisy[0][13]+=0.06F;
            float[] noisyTip=tip.clone();
            noisyTip[2]+=0.18F;
            for(int draw=0;draw<200;draw++) {
                Map<String,float[]> repeatedPose=bridge.sample(data,"hair/back",
                        bones,12,noisy,noisyTip,matrix(3F,5F,-2F),
                        0.15F,0.3F,1F);
                if(bridge.wasRecreatedThisSample())
                    throw new AssertionError("Render feedback reset same-frame Jolt rig");
                if(!sameSample(repeatedPose,seek))
                    throw new AssertionError("Render feedback altered frozen physics sample");
            }
            // A committed transform/key edit can explicitly invalidate the
            // cached physical scene. Scene render must not infer this from
            // noisy Chameleon matrices.
            bridge.invalidate("hair/back");
            Map<String,float[]> changed=bridge.sample(data,"hair/back",bones,
                    12,noisy,noisyTip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(changed==null || changed.size()!=bones.length ||
                    !bridge.wasRecreatedThisSample())
                throw new AssertionError("Explicit key edit failed to invalidate rig");
            if (BodyPartJoltPreviewAdapter.torsoContactsRequested()) {
                // Exercise the SAME reflective optional method the renderer
                // will invoke for skin-mesh-fitted torso contacts.
                float[] torsoLow={4F,4.0F,-2F};
                float[] torsoHigh={4F,4.6F,-2F};
                Map<String,float[]> torsoFrame=bridge.sample(
                        data,"hair/torso",bones,0,world,tip,
                        matrix(3F,5F,-2F),0.15F,0.3F,1F,
                        torsoLow,torsoHigh,0.15F);
                if(torsoFrame==null || torsoFrame.size()!=bones.length ||
                        !bridge.wasRecreatedThisSample())
                    throw new AssertionError("Optional fitted torso rig unavailable");
                torsoLow[0]+=0.01F;torsoHigh[0]+=0.01F;
                torsoFrame=bridge.sample(data,"hair/torso",bones,1,world,tip,
                        matrix(3F,5F,-2F),0.15F,0.3F,1F,
                        torsoLow,torsoHigh,0.15F);
                if(torsoFrame==null || torsoFrame.size()!=bones.length ||
                        bridge.wasRecreatedThisSample())
                    throw new AssertionError("Kinematic contact update restarted live rig");
            }
            System.out.println("Forge-style reflection preview bridge: PASS");
        } finally {
            bridge.clear();
        }
    }
}

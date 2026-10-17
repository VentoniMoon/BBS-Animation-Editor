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
            for(int tick=1;tick<=50;tick++) {
                Map<String,float[]> result=bridge.sample(data,"hair/back",bones,
                        tick,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
                if(result==null || result.size()!=4)
                    throw new AssertionError("No physical preview swing at frame "+tick);
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
            // Reversing timeline must reset the native world safely.
            Map<String,float[]> seek=bridge.sample(data,"hair/back",bones,
                    12,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(seek==null || seek.size()!=bones.length ||
                    !bridge.wasRecreatedThisSample())
                throw new AssertionError("Seek must rebuild a settled Jolt world");
            // Editing a *child* bone on a paused frame must also rebuild;
            // checking only root and parent would keep the stale hair rig.
            world[2]=world[2].clone();
            world[2][12]+=0.03F;
            Map<String,float[]> changed=bridge.sample(data,"hair/back",bones,
                    12,world,tip,matrix(3F,5F,-2F),0.15F,0.3F,1F);
            if(changed==null || changed.size()!=bones.length ||
                    !bridge.wasRecreatedThisSample())
                throw new AssertionError("Paused child bone edit kept stale Jolt scene");
            System.out.println("Forge-style reflection preview bridge: PASS");
        } finally {
            bridge.clear();
        }
    }
}

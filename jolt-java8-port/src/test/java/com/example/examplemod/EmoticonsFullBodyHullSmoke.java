package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

/** Exercise segmented skinned parent-body hull independently from Forge:
 * one region for head, torso, each arm and leg, at multiple poses.
 */
public final class EmoticonsFullBodyHullSmoke {
    private static final class Region {
        float[] positions;
        int[] triangles;
        Region(float[] positions,int[] triangles){
            this.positions=positions;this.triangles=triangles;
        }
    }
    private static Region cuboid(float x,float y,float z,
            float hx,float hy,float hz) {
        float[] p=new float[8*3];
        for(int i=0;i<8;i++){
            p[i*3]=x+((i&1)==0?-hx:hx);
            p[i*3+1]=y+((i&2)==0?-hy:hy);
            p[i*3+2]=z+((i&4)==0?-hz:hz);
        }
        return new Region(p,new int[]{0,1,2,1,3,2,4,5,6,5,7,6,
                0,1,4,1,5,4,2,3,6,3,7,6,0,2,4,2,6,4,1,3,5,3,7,5});
    }
    private static void near(float a,float b,String name) {
        if(Math.abs(a-b)>.0005F)
            throw new AssertionError(name+": "+a+" vs "+b);
    }

    private static boolean covered(float[] point,List<float[]> boxes) {
        for(float[] b:boxes)
            if(Math.abs(point[0]-b[0])<=b[7]+.001 &&
                    Math.abs(point[1]-b[1])<=b[8]+.001 &&
                    Math.abs(point[2]-b[2])<=b[9]+.001)
                return true;
        return false;
    }
    public static void main(String[] args) {
        Region[] regions={cuboid(0,1.85F,0,.2F,.2F,.19F),
                cuboid(0,1.25F,0,.29F,.38F,.18F),
                cuboid(-.55F,1.35F,0,.12F,.3F,.12F),
                cuboid(.55F,1.35F,0,.12F,.3F,.12F),
                cuboid(-.16F,.6F,0,.12F,.45F,.12F),
                cuboid(.16F,.6F,0,.12F,.45F,.12F)};
        float[][] checks={{0,1.85F,0},{0,1.25F,0},
                {-.55F,1.35F,0},{.55F,1.35F,0},
                {-.16F,.6F,0},{.16F,.6F,0}};
        List<float[]> all=new ArrayList<float[]>();
        for(int i=0;i<regions.length;i++){
            List<float[]> shape=EmoticonsSkinnedBodyHull.boxes(
                    regions[i].positions,regions[i].triangles,i==0?1:3,.018F);
            if(shape.isEmpty())throw new AssertionError("Missing group "+i);
            if(!covered(checks[i],shape))
                throw new AssertionError("Missing center coverage "+i);
            all.addAll(shape);
        }
        if(all.size()<6||all.size()>18)
            throw new AssertionError("Unexpected segmented hull count "+all.size());
        if(!EmoticonsSkinnedBodyHull.boxes(new float[3],new int[0],2,.018F).isEmpty())
            throw new AssertionError("Degenerate region fabricated collider");
        // Fit a swung arm ONCE in its animated bone frame, then drive
        // it with a 90-degree rotated bone. Dimensions must NOT change or
        // Jolt would rebuild its hair scene every animation tick.
        Region arm=cuboid(.5F,1.4F,0F,.10F,.36F,.12F);
        float[] initial={1,0,0,0, 0,1,0,0, 0,0,1,0, .5F,1.4F,0F,1F};
        List<float[]> local=EmoticonsSkinnedBodyHull.boneLocalBoxes(
                arm.positions,arm.triangles,initial,1,.018F);
        if(local.size()!=1)throw new AssertionError("No bone-local limb hull");
        float[] tilted={1,0,0,0, 0,0,1,0, 0,-1,0,0, .5F,1.4F,0F,1};
        float[] before=EmoticonsSkinnedBodyHull.transformRigid(initial,local.get(0));
        float[] after=EmoticonsSkinnedBodyHull.transformRigid(tilted,local.get(0));
        for(int k=7;k<10;k++)
            near(before[k],after[k],"fixed native shape dimension");
        if(Math.abs(after[3])<.5F)
            throw new AssertionError("Animated arm did not rotate Box");
        System.out.println("Rigid Emoticons arm hull moves with bone without resizing: PASS");
        System.out.println("Emoticons full parent: head/torso/arms/legs skin hull: PASS boxes="+all.size());
    }
}

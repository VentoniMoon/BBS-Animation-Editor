package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Geometry regression from the user's provided hair body1.geo.json.
 * Contains only bone pivot and terminal cube measurements, NOT the model.
 * Tests all three distinct chain shapes against the real native Jolt solver.
 */
public final class JoltHorstHairFixtureSmoke {
    private JoltHorstHairFixtureSmoke() { }

    private static float[] m(float xPx,float yPx,float zPx) {
        // Source anchor rotates Y=180; output is model-relative WORLD blocks.
        return new float[] {-1F,0F,0F,0F,0F,1F,0F,0F,
                0F,0F,-1F,0F,-xPx/16F,yPx/16F,-zPx/16F,1F};
    }

    private static float[] rotateX(float[] matrix,int degrees) {
        double radians=Math.toRadians(degrees);
        float c=(float)Math.cos(radians), sn=(float)Math.sin(radians);
        float[] out=matrix.clone();
        // Rotate world bone axes and translation about anchor at Y=6/16.
        for(int axis=0;axis<3;axis++) {
            int i=axis*4;
            out[i+1]=c*matrix[i+1]-sn*matrix[i+2];
            out[i+2]=sn*matrix[i+1]+c*matrix[i+2];
        }
        float pivotY=6F/16F;
        float dy=matrix[13]-pivotY;
        out[13]=pivotY+c*dy-sn*matrix[14];
        out[14]=sn*dy+c*matrix[14];
        return out;
    }

    private static float[][] rotateChain(float[][] pivots,int degrees) {
        float[][] result=new float[pivots.length][];
        for(int i=0;i<pivots.length;i++)
            result[i]=rotateX(pivots[i],degrees);
        return result;
    }

    private static void assertContinuous(String name,float[] from,float[] to,
            int degrees) {
        float delta=0F;
        for(int i=0;i<3;i++) {
            if(!Float.isFinite(to[i]))
                throw new AssertionError(name+" non-finite physical position");
            float diff=to[i]-from[i];
            delta+=diff*diff;
        }
        if(Math.sqrt(delta)>0.35D)
            throw new AssertionError(name+" physical jump at X="+degrees);
    }

    private static void test(String name,float[][] pivots,float[] tip) {
        float[] anchor=m(0F,6F,0F);
        float[] sourceTip=new float[] {-tip[0]/16F,tip[1]/16F,-tip[2]/16F};
        JoltChameleonPreviewRig rig=JoltChameleonPreviewRig.create(0,
                pivots,sourceTip,anchor,0.15F,0.30F,0.60F);
        try {
            float[] first=JoltChameleonPreviewRig.position(pivots[0]);
            for(int i=1;i<=150;i++) {
                rig.advance(i,first,anchor);
                float[][] swings=rig.localBoneSwings(pivots,anchor);
                if(swings.length!=pivots.length)
                    throw new AssertionError(name+" lost segments");
                for(float[] axisAngle:swings)for(float n:axisAngle)
                    if(!Float.isFinite(n))
                        throw new AssertionError(name+" produced non-finite swing");
            }
            int frame=150;
            float[] previous=rig.lastBodyPosition();
            // The user's precise failure: anchor passes 60 degrees, then
            // returns. Geometry and Y=180 rest transform remain realistic.
            for(int x=1;x<=80;x++) {
                float[] turnedParent=rotateX(anchor,x);
                float[][] turned=rotateChain(pivots,x);
                rig.advance(++frame,JoltChameleonPreviewRig.position(turned[0]),
                        turnedParent);
                float[][] solved=rig.localBoneSwings(turned,turnedParent);
                if(solved.length!=pivots.length)
                    throw new AssertionError("Lost segment at X="+x);
                float[] current=rig.lastBodyPosition();
                assertContinuous(name,previous,current,x);
                previous=current;
            }
            for(int x=79;x>=0;x--) {
                float[] turnedParent=rotateX(anchor,x);
                float[][] turned=rotateChain(pivots,x);
                rig.advance(++frame,JoltChameleonPreviewRig.position(turned[0]),
                        turnedParent);
                rig.localBoneSwings(turned,turnedParent);
                float[] current=rig.lastBodyPosition();
                assertContinuous(name,previous,current,x);
                previous=current;
            }
            System.out.println("Horst "+name+" ("+pivots.length+
                    " bones), anchor X=0..80..0: PASS");
        } finally {rig.close();}
    }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if(!Jolt.newFactory())throw new IllegalStateException("Jolt factory");
        Jolt.registerTypes();

        // Pivots in original Blockbench pixel coordinates.
        test("left",new float[][] {
                m(-3.08F,12F,-1.58F),m(-3.08F,9F,-2.08F),
                m(-3.08F,6F,-2.08F)
        },new float[] {-3.08F,1F,-1.58F});

        test("right",new float[][] {
                m(3.08F,12F,-1.58F),m(3.08F,9F,-2.08F),
                m(3.08F,6F,-2.08F)
        },new float[] {3.08F,1F,-1.58F});

        test("back",new float[][] {
                m(0F,12F,1.5F),m(0F,9F,2.08F),
                m(0F,6F,2.08F),m(0F,3F,2.08F)
        },new float[] {0F,0.92F,1.5F});
    }
}

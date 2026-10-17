package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Jolt;

/**
 * Integration stand that consumes column-major model matrices and returns
 * local quaternion rotations for an Emoticons-body-attached Chameleon strand.
 */
public final class JoltChameleonPreviewRigSmoke {
    private JoltChameleonPreviewRigSmoke() { }

    private static float[] matrix(double angleX, float x, float y, float z) {
        double h=Math.toRadians(angleX)*0.5D;
        float[] q=JoltBoneQuaternionMapper.multiply(
                new float[] {(float)Math.sin(h),0F,0F,(float)Math.cos(h)},
                new float[] {0F,1F,0F,0F}); // anchor Y=180
        float a=q[0],b=q[1],c=q[2],d=q[3];
        return new float[] {
                1F-2F*(b*b+c*c),2F*(a*b+c*d),2F*(a*c-b*d),0F,
                2F*(a*b-c*d),1F-2F*(a*a+c*c),2F*(b*c+a*d),0F,
                2F*(a*c+b*d),2F*(b*c-a*d),1F-2F*(a*a+b*b),0F,
                x,y,z,1F};
    }

    private static void expectRejected(float[][] matrices, float[] tip,
            float[] parent) {
        try {
            JoltChameleonPreviewRig bad=JoltChameleonPreviewRig.create(0,
                    matrices,tip,parent,0.15F,0.25F,0.6F);
            bad.close();
            throw new AssertionError("Invalid geometry was accepted");
        } catch (IllegalArgumentException expected) {
            // No silent approximation of unsupported hair shapes.
        }
    }

    public static void main(String[] args) {
        System.load(System.getProperty("jolt.native.path"));
        Jolt.registerDefaultAllocator();
        if (!Jolt.newFactory()) throw new IllegalStateException("No Jolt factory");
        Jolt.registerTypes();

        float[] parent=matrix(0D,3F,5F,-2F);
        float[][] bone=new float[4][];
        final float dx=0F,dy=-0.125F,dz=0.21650635F;
        for (int i=0;i<bone.length;i++)
            bone[i]=matrix(0D,3F+i*dx,5F+i*dy,-2F+i*dz);
        float[] tip=new float[] {3F,5F+4*dy,-2F+4*dz};

        // Pure quaternion decomposition must match the native Y=180 basis.
        float[] extracted=JoltChameleonPreviewRig.rotation(parent);
        float[] original={0F,1F,0F,0F};
        float dot=Math.abs(extracted[0]*original[0]+
                extracted[1]*original[1]+extracted[2]*original[2]+
                extracted[3]*original[3]);
        if(dot<0.999F)throw new AssertionError("Incorrect native Y180 extraction");

        JoltChameleonPreviewRig rig=JoltChameleonPreviewRig.create(0,
                bone,tip,parent,0.15F,0.30F,0.60F);
        try {
            float[][] initial=rig.localBoneRotations();
            if(initial.length!=4)throw new AssertionError("Wrong Chameleon bone count");
            for(int frame=1;frame<=180;frame++) {
                rig.advance(frame,new float[] {3F,5F,-2F},parent);
                rig.localBoneRotations();
            }
            int frame=180;
            float[] previous=rig.localBoneRotations()[0];
            for (int degree=55;degree<=70;degree++) {
                frame++;
                float[] animatedParent=matrix(degree,3F,5F,-2F);
                rig.advance(frame,new float[] {3F,5F,-2F},animatedParent);
                float[][] solved=rig.localBoneRotations();
                float[] root=solved[0];
                for(float[] q:solved)
                    for(float v:q)if(!Float.isFinite(v))
                        throw new AssertionError("Non-finite local quaternion");
                float continuity=Math.abs(root[0]*previous[0]+
                        root[1]*previous[1]+root[2]*previous[2]+
                        root[3]*previous[3]);
                if(continuity<0.70F)
                    throw new AssertionError("Discontinuity at anchor X="+degree);
                previous=root;
            }
            System.out.println("Chameleon native matrices -> Jolt -> local bone quaternions: PASS");
        } finally {
            rig.close();
        }
        float[][] uneven=bone.clone();
        uneven[2]=matrix(0D,3F,5F-3F*0.125F,-2F+3F*0.21650635F);
        expectRejected(uneven,tip,parent);
        float[][] mirrored=bone.clone();
        mirrored[1]=bone[1].clone();
        mirrored[1][0]=-mirrored[1][0];
        expectRejected(mirrored,tip,parent);
        System.out.println("Unsupported strand geometry fails safely: PASS");
    }
}

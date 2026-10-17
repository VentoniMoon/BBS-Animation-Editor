package com.example.examplemod.physics;

import com.github.stephengold.joltjni.Quat;

/**
 * Experimental adapter for native Chameleon bone WORLD matrices (GL
 * column-major) and a Jolt chain of the same authored bones.
 *
 * Does not write to the legacy renderer or .dat keys. This is deliberately
 * kept independent from Metamorph classes for standalone Java 8 validation.
 * Rebuild on seeking, reversing, topology change or discontinuous source pose.
 */
public final class JoltChameleonPreviewRig implements AutoCloseable {
    private final JoltHairChain chain;
    private final float[][] corrections;
    private final float[][] previousLocals;
    private final float[] restParentRotation;
    private float[] currentParentRotation;
    private int lastFrame;

    private JoltChameleonPreviewRig(JoltHairChain chain, float[][] corrections,
            float[] restParentRotation, int frame) {
        this.chain = chain;
        this.corrections = corrections;
        this.previousLocals = new float[corrections.length][];
        this.restParentRotation = restParentRotation;
        this.currentParentRotation = restParentRotation;
        this.lastFrame = frame;
    }

    /**
     * @param boneWorld  one clean native Chameleon 4x4 matrix per driven bone
     * @param terminalTipWorld extra end of the final bone, in world blocks
     * @param parentWorld the immediate ancestor (often 'anchor') 4x4 world matrix
     * @throws IllegalArgumentException when the first prototype cannot model the
     *         native chain faithfully (uneven bone lengths, curved rest shape,
     *         negative/mirrored determinant or unusable short segments).
     */
    public static JoltChameleonPreviewRig create(int frame, float[][] boneWorld,
            float[] terminalTipWorld, float[] parentWorld,
            float stiffness, float damping, float falloff) {
        if (boneWorld == null || boneWorld.length < 2 ||
                terminalTipWorld == null || terminalTipWorld.length != 3)
            throw new IllegalArgumentException("Missing bone world poses / terminal tip");
        int count = boneWorld.length;
        float[][] positions = new float[count+1][];
        float[][] boneRestQuats = new float[count][];
        for (int i=0;i<count;i++) {
            validateMatrix(boneWorld[i]);
            positions[i] = position(boneWorld[i]);
            boneRestQuats[i] = rotation(boneWorld[i]);
        }
        positions[count] = terminalTipWorld.clone();
        float[][] directions = new float[count][3];
        float[] lengths = new float[count];
        float total = 0F;
        for (int i=0;i<count;i++) {
            float dx=positions[i+1][0]-positions[i][0];
            float dy=positions[i+1][1]-positions[i][1];
            float dz=positions[i+1][2]-positions[i][2];
            float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            if (!(length >= 0.10F) || !Float.isFinite(length))
                throw new IllegalArgumentException("Jolt strand bone too short at index "+i);
            lengths[i]=length;
            directions[i]=new float[] {dx/length,dy/length,dz/length};
            total+=length;
        }
        float average=total/count;
        // Single-axis straight equal-length strands only in the *first*
        // adapter. No silent flattening of curved hair or varying cuboid sizes.
        for (int i=0;i<count;i++) {
            if (Math.abs(lengths[i]-average)>average*0.15F)
                throw new IllegalArgumentException("Unequal Jolt strand lengths at "+i);
            float dot=directions[0][0]*directions[i][0] +
                    directions[0][1]*directions[i][1] +
                    directions[0][2]*directions[i][2];
            if (dot < 0.97F)
                throw new IllegalArgumentException("Bent model rest strand at "+i);
        }
        float[] parentRest = rotation(parentWorld);
        float[] origin=positions[0], dir=directions[0];
        JoltHairChain chain = new JoltHairChain(count, average,
                origin[0],origin[1],origin[2],
                dir[0],dir[1],dir[2],stiffness,damping,falloff);
        float[][] corrections = new float[count][];
        for (int i=0;i<count;i++)
            corrections[i]=JoltBoneQuaternionMapper.restCorrection(
                    chain.initialWorldRotation(),boneRestQuats[i]);
        return new JoltChameleonPreviewRig(chain,corrections,parentRest,frame);
    }

    /** Only adjacent timeline frames are valid; caller must recreate on seek. */
    public void advance(int frame, float[] rootWorldPosition,
            float[] currentParentWorldMatrix) {
        if (frame != lastFrame+1)
            throw new IllegalStateException("Jolt preview seek: recreate rig");
        if (rootWorldPosition == null || rootWorldPosition.length != 3)
            throw new IllegalArgumentException("Missing animated root world pivot");
        currentParentRotation=rotation(currentParentWorldMatrix);
        float[] parentDelta=JoltBoneQuaternionMapper.multiply(
                currentParentRotation,
                JoltBoneQuaternionMapper.inverse(restParentRotation));
        float[] pinOrientation=JoltBoneQuaternionMapper.multiply(
                parentDelta,chain.initialWorldRotation());
        chain.moveAnchor(rootWorldPosition[0],rootWorldPosition[1],rootWorldPosition[2],
                pinOrientation[0],pinOrientation[1],pinOrientation[2],pinOrientation[3]);
        chain.step();
        lastFrame=frame;
    }

    /** Returns one LOCAL quaternion per bone; no Euler decomposition. */
    public float[][] localBoneRotations() {
        float[][] result = new float[corrections.length][];
        float[] parent=currentParentRotation;
        for(int i=0;i<corrections.length;i++) {
            Quat simulated=chain.rotation(i);
            float[] physical=new float[] {simulated.getX(),simulated.getY(),
                    simulated.getZ(),simulated.getW()};
            float[] worldBone=JoltBoneQuaternionMapper.multiply(physical,corrections[i]);
            float[] local=JoltBoneQuaternionMapper.localBoneRotation(
                    parent,physical,corrections[i]);
            if(previousLocals[i]!=null)
                local=JoltBoneQuaternionMapper.continuous(previousLocals[i],local);
            result[i]=local;
            previousLocals[i]=local;
            parent=worldBone;
        }
        return result;
    }

    public static float[] position(float[] m) {
        validateMatrix(m);
        return new float[] {m[12],m[13],m[14]};
    }

    /**
     * Extract a pure quaternion from a native matrix with positive scale.
     * Reject reflections and shear instead of guessing Chameleon handedness.
     */
    public static float[] rotation(float[] m) {
        validateMatrix(m);
        float sx=(float)Math.sqrt(m[0]*m[0]+m[1]*m[1]+m[2]*m[2]);
        float sy=(float)Math.sqrt(m[4]*m[4]+m[5]*m[5]+m[6]*m[6]);
        float sz=(float)Math.sqrt(m[8]*m[8]+m[9]*m[9]+m[10]*m[10]);
        if(sx<1E-6F || sy<1E-6F || sz<1E-6F)
            throw new IllegalArgumentException("Degenerate bone basis");
        float m00=m[0]/sx, m01=m[4]/sy, m02=m[8]/sz;
        float m10=m[1]/sx, m11=m[5]/sy, m12=m[9]/sz;
        float m20=m[2]/sx, m21=m[6]/sy, m22=m[10]/sz;
        float d01=m00*m01+m10*m11+m20*m21;
        float d02=m00*m02+m10*m12+m20*m22;
        float d12=m01*m02+m11*m12+m21*m22;
        if (Math.abs(d01)>0.02F || Math.abs(d02)>0.02F ||
                Math.abs(d12)>0.02F)
            throw new IllegalArgumentException("Sheared bone transform");
        float determinant=m00*(m11*m22-m12*m21)-
                m01*(m10*m22-m12*m20)+m02*(m10*m21-m11*m20);
        if(!(determinant>0.95F))
            throw new IllegalArgumentException("Mirrored bone transform; handedness unknown");
        float x,y,z,w,trace=m00+m11+m22;
        if(trace>0F) {
            float s=(float)Math.sqrt(trace+1F)*2F;
            w=0.25F*s; x=(m21-m12)/s; y=(m02-m20)/s; z=(m10-m01)/s;
        } else if(m00>m11 && m00>m22) {
            float s=(float)Math.sqrt(1F+m00-m11-m22)*2F;
            w=(m21-m12)/s; x=0.25F*s; y=(m01+m10)/s; z=(m02+m20)/s;
        } else if(m11>m22) {
            float s=(float)Math.sqrt(1F+m11-m00-m22)*2F;
            w=(m02-m20)/s; x=(m01+m10)/s; y=0.25F*s; z=(m12+m21)/s;
        } else {
            float s=(float)Math.sqrt(1F+m22-m00-m11)*2F;
            w=(m10-m01)/s; x=(m02+m20)/s; y=(m12+m21)/s; z=0.25F*s;
        }
        return JoltBoneQuaternionMapper.normalized(new float[] {x,y,z,w});
    }

    private static void validateMatrix(float[] m) {
        if(m==null || m.length!=16)
            throw new IllegalArgumentException("Expected column-major 4x4 world matrix");
        for(float v:m) if(!Float.isFinite(v))
            throw new IllegalArgumentException("Non-finite bone transform");
        if (Math.abs(m[3])>1E-4F || Math.abs(m[7])>1E-4F ||
                Math.abs(m[11])>1E-4F || Math.abs(m[15]-1F)>1E-4F)
            throw new IllegalArgumentException("Invalid affine bone transform");
    }

    @Override public void close() { chain.close(); }
}

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
     * @throws IllegalArgumentException on invalid native matrices, mirrored
     *         transforms or bone segments too short for stable Jolt physics.
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
        // Preserve each native pivot and the true terminal tip, including
        // curved rest poses and individually varying segment lengths.
        // Jolt builds one inertia capsule for each authored bone.
        float[] parentRest = rotation(parentWorld);
        JoltHairChain chain = new JoltHairChain(
                positions,stiffness,damping,falloff);
        float[][] corrections = new float[count][];
        for (int i=0;i<count;i++)
            corrections[i]=JoltBoneQuaternionMapper.restCorrection(
                    chain.initialWorldRotation(i),boneRestQuats[i]);
        return new JoltChameleonPreviewRig(chain,corrections,parentRest,frame);
    }

    /** World center of the final physical bone, for continuity diagnostics. */
    public float[] lastBodyPosition() {
        com.github.stephengold.joltjni.RVec3 p=chain.position(chain.size()-1);
        return new float[] {(float)p.xx(),(float)p.yy(),(float)p.zz()};
    }

    public void setGravity(float multiplier) {
        chain.setGravity(multiplier);
    }

    /** Optional actual-Jolt contact against a fitted Emoticons torso proxy. */
    public void setTorsoCollider(float[] a,float[] b,float radius) {
        chain.setTorsoCollider(a,b,radius);
    }

    public boolean hasTorsoCollider() {
        return chain.hasTorsoCollider();
    }

    /**
     * On a seek or a stationary pose edit, resolve free-hanging gravity at the
     * CURRENT anchor pose before publishing an initial preview. This does not
     * change the timeline frame and never writes animation keyframes.
     * Repeated draws reuse the cached output; they do not re-run these ticks.
     */
    public void settle(int ticks) {
        if (ticks < 0 || ticks > 120)
            throw new IllegalArgumentException("Invalid bounded settle duration");
        for (int i = 0; i < ticks; i++) chain.step();
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

    /**
     * Diagnostic: the physical segment direction read DIRECTLY from Jolt's
     * rigid-body quaternion (local cylinder -Y axis), in world coordinates.
     *
     * Deliberately bypasses the body-to-bone rest correction, Chameleon
     * hierarchy, axis-angle bridge and Euler pose writer. Comparing this
     * value with the rendered direction identifies which stage flips a
     * strand. No physics state is modified by this read.
     */
    public float[][] rawPhysicalWorldDirections() {
        float[][] result=new float[chain.size()][3];
        for (int i=0; i<result.length; i++) {
            Quat body=chain.rotation(i);
            float[] q=JoltBoneQuaternionMapper.normalized(new float[] {
                    body.getX(),body.getY(),body.getZ(),body.getW()
            });
            float x=q[0], y=q[1], z=q[2], w=q[3];
            // The Jolt capsule's local long axis was authored as -Y.
            // This is the negative second column of R(q), not a guessed
            // Chameleon rotation sign or a rest-pose Euler channel.
            result[i][0]=-2F*(x*y-z*w);
            result[i][1]=-1F+2F*(x*x+z*z);
            result[i][2]=-2F*(y*z+x*w);
            float length=(float)Math.sqrt(
                    result[i][0]*result[i][0]+
                    result[i][1]*result[i][1]+
                    result[i][2]*result[i][2]);
            if (!(length > 1E-6F) || !Float.isFinite(length))
                throw new IllegalStateException("Invalid native Jolt segment direction");
            for (int component=0; component<3; component++)
                result[i][component]/=length;
        }
        return result;
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

    /**
     * Preview compatibility bridge: map the NEW Jolt physical local bone
     * quaternions back to additive local axis-angle deltas for the existing
     * Chameleon pose writer. No Euler decompositions occur in the solver.
     *
     * This is a transition adapter, not the ultimate direct-quaternion
     * Chameleon renderer. Never feed it already physics-modified matrices.
     */
    public float[][] localBoneSwings(float[][] cleanBoneWorld,
            float[] cleanParentWorld) {
        if (cleanBoneWorld == null || cleanBoneWorld.length != corrections.length)
            throw new IllegalArgumentException("Clean pose bone count mismatch");
        float[][] physical = localBoneRotations();
        float[][] swings = new float[physical.length][4];
        float[] parent = rotation(cleanParentWorld);
        for (int i=0; i<physical.length; i++) {
            float[] currentWorld = rotation(cleanBoneWorld[i]);
            float[] animatedLocal = JoltBoneQuaternionMapper.multiply(
                    JoltBoneQuaternionMapper.inverse(parent), currentWorld);
            // Current visual bone rotation followed by this delta equals
            // the physically solved local rotation.
            float[] delta = JoltBoneQuaternionMapper.multiply(
                    JoltBoneQuaternionMapper.inverse(animatedLocal),physical[i]);
            if (delta[3] < 0F) {
                for (int c=0;c<4;c++) delta[c]=-delta[c];
            }
            float norm=(float)Math.sqrt(delta[0]*delta[0]+
                    delta[1]*delta[1]+delta[2]*delta[2]);
            if(norm<1E-7F) {
                swings[i]=new float[] {1F,0F,0F,0F};
            } else {
                float angle=2F*(float)Math.atan2(norm,delta[3]);
                swings[i]=new float[] {delta[0]/norm,delta[1]/norm,
                        delta[2]/norm,angle};
            }
            parent=currentWorld;
        }
        return swings;
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

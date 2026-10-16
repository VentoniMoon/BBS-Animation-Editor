package com.example.examplemod.physics;

/** No JNI or Minecraft dependency: pure quaternion frame test at anchor X=60. */
public final class JoltBoneQuaternionMapperSmoke {
    private JoltBoneQuaternionMapperSmoke() { }

    private static float[] axisX(double degrees) {
        double h=Math.toRadians(degrees)*0.5D;
        return new float[] {(float)Math.sin(h),0F,0F,(float)Math.cos(h)};
    }

    private static void sameRotation(float[] expected, float[] actual) {
        float[] a=JoltBoneQuaternionMapper.normalized(expected);
        float[] b=JoltBoneQuaternionMapper.normalized(actual);
        double dot=Math.abs(a[0]*b[0]+a[1]*b[1]+a[2]*b[2]+a[3]*b[3]);
        if (dot < 0.99999D) throw new AssertionError("Frame roundtrip mismatch: " + dot);
    }

    public static void main(String[] args) {
        float[] identity={0F,0F,0F,1F};
        float[] boneRest={0F,1F,0F,0F}; // model's 180-degree Y anchor
        float[] correction=JoltBoneQuaternionMapper.restCorrection(identity,boneRest);
        float[] prev=null;
        for(int deg=50;deg<=75;deg++) {
            float[] parent=JoltBoneQuaternionMapper.multiply(axisX(deg),boneRest);
            // Simulated physical segment keeps the same WORLD orientation.
            float[] local=JoltBoneQuaternionMapper.localBoneRotation(parent,identity,correction);
            if(prev!=null) {
                local=JoltBoneQuaternionMapper.continuous(prev,local);
                double dot=prev[0]*local[0]+prev[1]*local[1]+
                        prev[2]*local[2]+prev[3]*local[3];
                if(dot<0.999D)
                    throw new AssertionError("Unexpected discontinuity near X=" + deg);
            }
            sameRotation(JoltBoneQuaternionMapper.multiply(identity,correction),
                    JoltBoneQuaternionMapper.multiply(parent,local));
            prev=local;
        }
        System.out.println("World/local quaternion mapping 50-75deg with Y180: PASS");
    }
}

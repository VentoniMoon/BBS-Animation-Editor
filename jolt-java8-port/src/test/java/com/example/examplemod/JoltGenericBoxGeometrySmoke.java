package com.example.examplemod;

/**
 * Pure Java 8 regression for attached Blockbench cubes, including both
 * independently rotated components of the supplied chest model.
 */
public final class JoltGenericBoxGeometrySmoke
{
    private JoltGenericBoxGeometrySmoke() {}

    private static float[][] corners(float[] origin,float[] size)
    {
        float[][] vertices=new float[8][3];
        for(int k=0;k<8;k++)
            for(int axis=0;axis<3;axis++)
                vertices[k][axis]=(origin[axis]+
                        (((k>>axis)&1)==0?0F:size[axis]))/16F;
        return vertices;
    }

    private static void close(float a,float b,String label)
    {
        if(Math.abs(a-b)>0.001F)
            throw new AssertionError(label+" expected "+b+" got "+a);
    }

    private static void check(float[] origin,float[] size)
    {
        float[] pivot={-1F,8.91777F,-1.93934F};
        float[] cubePivot={-1F,7.15F,-3F};
        float[] box=BodyPartJoltBoxGeometry.fit(
                BodyPartPhysicsMatrixSpace.identity(),pivot,cubePivot,
                new float[]{45F,0F,0F},corners(origin,size));
        if(box.length!=10)throw new AssertionError("Not a Jolt box description");
        close(box[7],size[0]/32F,"x width");
        close(box[8],size[1]/32F,"y height");
        close(box[9],size[2]/32F,"z depth");
        close(Math.abs(box[3]),0.38268343F,"45-degree X quaternion");
        close(Math.abs(box[6]),0.9238795F,"45-degree X scalar");
        float[] orientation=BodyPartJoltBoxGeometry.fit(
                BodyPartPhysicsMatrixSpace.identity(),pivot,cubePivot,
                new float[]{0F,0F,0F},corners(origin,size));
        if(Math.abs(box[1]-orientation[1])<0.01F &&
                Math.abs(box[2]-orientation[2])<0.01F)
            throw new AssertionError("Cube-local 45-degree rotation was lost");
    }

    public static void main(String[] args)
    {
        // Both cubes, not a single sphere enclosing them.
        check(new float[]{-5.15F,5.90F,-2.75F},
                new float[]{8.3F,3.5F,3F});
        check(new float[]{-4.9F,6.15F,-2.50F},
                new float[]{7.8F,3F,3F});
        System.out.println("Generic attached Chameleon rotated cubes: PASS");
    }
}

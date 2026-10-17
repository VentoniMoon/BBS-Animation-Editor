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


    /** An independent renderer-space reference. Chameleon uses:
     * bone postRender = worldPart * T(bonePivot),
     * visible cube = worldPart * T(cubePivot) * Rcube * T(-cubePivot).
     * The fitted Jolt box MUST have the same eight corners even with the
     * actor tilted and the nested Chameleon Y=180 render transform.
     */
    private static void checkRenderedCorners(float[] cubeEuler)
    {
        float[] bonePivot={-1F,8.91777F,-1.93934F};
        float[] cubePivot={-1F,7.15F,-3F};
        float[] origin={-5.15F,5.9F,-2.75F};
        float[] size={8.3F,3.5F,3F};
        float[][] vertices=corners(origin,size);

        float x=(float)Math.toRadians(-83.23D);
        float cx=(float)Math.cos(x),sx=(float)Math.sin(x);
        float[] worldPart=BodyPartPhysicsMatrixSpace.identity();
        // R_y(180 degrees) * R_x(-83.23 degrees), column-major.
        worldPart[0]=-1F;
        worldPart[5]=cx;worldPart[6]=-sx;
        worldPart[9]=-sx;worldPart[10]=-cx;
        worldPart[12]=2.2F;worldPart[13]=1.4F;worldPart[14]=-1.3F;

        float[] boneMatrix=worldPart.clone();
        for(int row=0;row<3;row++)
            for(int col=0;col<3;col++)
                boneMatrix[12+row]+=
                        worldPart[col*4+row]*bonePivot[col]/16F;
        float[] fit=BodyPartJoltBoxGeometry.fit(boneMatrix,bonePivot,
                cubePivot,cubeEuler,vertices);

        double ax=Math.toRadians(cubeEuler[0]);
        double ay=Math.toRadians(cubeEuler[1]);
        double az=Math.toRadians(cubeEuler[2]);
        float cx2=(float)Math.cos(ax),sx2=(float)Math.sin(ax);
        float cy2=(float)Math.cos(ay),sy2=(float)Math.sin(ay);
        float cz2=(float)Math.cos(az),sz2=(float)Math.sin(az);
        float[] pivot={cubePivot[0]/16F,cubePivot[1]/16F,cubePivot[2]/16F};

        float qx=fit[3],qy=fit[4],qz=fit[5],qw=fit[6];
        float[][] cornersFromFit=new float[8][3];
        for(int k=0;k<8;k++)
        {
            float xx=((k&1)==0?-1:1)*fit[7];
            float yy=((k&2)==0?-1:1)*fit[8];
            float zz=((k&4)==0?-1:1)*fit[9];
            float[] v=cornersFromFit[k];
            v[0]=fit[0]+(1-2*(qy*qy+qz*qz))*xx+
                    2*(qx*qy-qw*qz)*yy+2*(qx*qz+qw*qy)*zz;
            v[1]=fit[1]+2*(qx*qy+qw*qz)*xx+
                    (1-2*(qx*qx+qz*qz))*yy+2*(qy*qz-qw*qx)*zz;
            v[2]=fit[2]+2*(qx*qz-qw*qy)*xx+
                    2*(qy*qz+qw*qx)*yy+(1-2*(qx*qx+qy*qy))*zz;
        }
        for(float[] vertex:vertices)
        {
            // Z, Y, X in the same order as Chameleon's MatrixStack.
            float a=vertex[0]-pivot[0];
            float b=vertex[1]-pivot[1];
            float c=vertex[2]-pivot[2];
            float xp=a;
            float yp=cx2*b-sx2*c;
            float zp=sx2*b+cx2*c;
            float xpp=cy2*xp+sy2*zp;
            float ypp=yp;
            float zpp=-sy2*xp+cy2*zp;
            float xppp=cz2*xpp-sz2*ypp+pivot[0];
            float yppp=sz2*xpp+cz2*ypp+pivot[1];
            float zppp=zpp+pivot[2];
            float[] expected=new float[3];
            for(int row=0;row<3;row++)
                expected[row]=worldPart[12+row]+
                        worldPart[row]*xppp+worldPart[4+row]*yppp+
                        worldPart[8+row]*zppp;
            float minimum=Float.POSITIVE_INFINITY;
            for(float[] corner:cornersFromFit)
            {
                float dx=corner[0]-expected[0],dy=corner[1]-expected[1],
                        dz=corner[2]-expected[2];
                minimum=Math.min(minimum,dx*dx+dy*dy+dz*dz);
            }
            if(minimum>1E-7F)
                throw new AssertionError("Chameleon visual cube != Jolt collider, "
                        +"squared corner displacement="+minimum+
                        ", Euler="+java.util.Arrays.toString(cubeEuler));
        }
    }

    public static void main(String[] args)
    {
        // Both cubes, not a single sphere enclosing them.
        check(new float[]{-5.15F,5.90F,-2.75F},
                new float[]{8.3F,3.5F,3F});
        check(new float[]{-4.9F,6.15F,-2.50F},
                new float[]{7.8F,3F,3F});
        checkRenderedCorners(new float[]{-45F,0F,0F});
        checkRenderedCorners(new float[]{-35F,27F,15F});
        System.out.println("Nested Chameleon X=-83.23, Y180, cube pivot/rotation and Jolt box corners: PASS");
        System.out.println("Generic attached Chameleon rotated cubes: PASS");
    }
}

package com.example.examplemod;

/**
 * Model-independent conversion of decoded Chameleon cube vertices to a
 * conservative, rigid Jolt box. All coordinates are in model blocks; the
 * world bone matrix is column-major and already includes the actor, BodyPart
 * attachment, Chameleon scale, and the nested +180 degree Y render transform.
 *
 * The resulting ten floats are [world center xyz, world quaternion xyzw,
 * positive HALF extents xyz]. Explicitly reject sheared or mirrored transforms:
 * a native BoxShape cannot reproduce shear and guessing would cause false
 * hair contacts.
 */
public final class BodyPartJoltBoxGeometry
{
    private BodyPartJoltBoxGeometry() {}

    public static float[] fit(float[] worldBone, float[] bonePivotPixels,
            float[] cubePivotPixels, float[] cubeEulerDegrees,
            float[][] decodedVertexBlocks)
    {
        require(worldBone,16,"world bone matrix");
        require(bonePivotPixels,3,"bone pivot");
        require(cubePivotPixels,3,"cube pivot");
        require(cubeEulerDegrees,3,"cube rotation");
        if(decodedVertexBlocks==null || decodedVertexBlocks.length<4)
            throw new IllegalArgumentException("Missing decoded cube vertices");

        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(float[] xyz:decodedVertexBlocks)
        {
            require(xyz,3,"quad vertex");
            for(int axis=0;axis<3;axis++)
            {
                min[axis]=Math.min(min[axis],xyz[axis]);
                max[axis]=Math.max(max[axis],xyz[axis]);
            }
        }
        float[] midpoint=new float[3],localHalf=new float[3];
        for(int i=0;i<3;i++)
        {
            midpoint[i]=(min[i]+max[i])*0.5F;
            localHalf[i]=(max[i]-min[i])*0.5F;
            if(!(localHalf[i]>0.001F))
                throw new IllegalArgumentException("Degenerate cube axis "+i);
        }
        float[] spin=BodyPartPhysicsRotation.eulerXYZ(
                (float)Math.toRadians(cubeEulerDegrees[0]),
                (float)Math.toRadians(cubeEulerDegrees[1]),
                (float)Math.toRadians(cubeEulerDegrees[2]));
        float[] relative=new float[3];
        for(int i=0;i<3;i++)
            relative[i]=midpoint[i]-cubePivotPixels[i]/16F;
        float[] rotated=new float[] {
                spin[0]*relative[0]+spin[3]*relative[1]+spin[6]*relative[2],
                spin[1]*relative[0]+spin[4]*relative[1]+spin[7]*relative[2],
                spin[2]*relative[0]+spin[5]*relative[1]+spin[8]*relative[2]
        };
        for(int i=0;i<3;i++)
            rotated[i]+= (cubePivotPixels[i]-bonePivotPixels[i])/16F;

        float[] result=new float[10];
        for(int row=0;row<3;row++)
        {
            result[row]=worldBone[12+row];
            for(int col=0;col<3;col++)
                result[row]+=worldBone[col*4+row]*rotated[col];
        }

        // Each column of worldBone * R_cube is one physical box axis.
        float[] axes=new float[9];
        for(int col=0;col<3;col++)
        {
            float length=0F;
            for(int row=0;row<3;row++)
            {
                float component=0F;
                for(int k=0;k<3;k++)
                    component+=worldBone[k*4+row]*spin[col*3+k];
                axes[col*3+row]=component;
                length+=component*component;
            }
            length=(float)Math.sqrt(length);
            if(!(length>1E-6F) || !Float.isFinite(length))
                throw new IllegalArgumentException("Invalid cube scale");
            result[7+col]=length*localHalf[col];
            if(!(result[7+col]>=0.003F) || result[7+col]>2F)
                throw new IllegalArgumentException("Unsupported cube size");
            for(int row=0;row<3;row++)axes[col*3+row]/=length;
        }
        for(int a=0;a<3;a++)
            for(int b=a+1;b<3;b++)
            {
                float dot=0F;
                for(int row=0;row<3;row++)
                    dot+=axes[a*3+row]*axes[b*3+row];
                if(Math.abs(dot)>0.025F)
                    throw new IllegalArgumentException("Sheared cube frame");
            }
        float determinant=axes[0]*(axes[4]*axes[8]-axes[5]*axes[7])-
                axes[3]*(axes[1]*axes[8]-axes[2]*axes[7])+
                axes[6]*(axes[1]*axes[5]-axes[2]*axes[4]);
        if(!(determinant>0.95F))
            throw new IllegalArgumentException("Mirrored cube frame");
        float[] q=quaternionFromBasis(axes);
        System.arraycopy(q,0,result,3,4);
        return result;
    }

    private static float[] quaternionFromBasis(float[] m)
    {
        float m00=m[0],m01=m[3],m02=m[6];
        float m10=m[1],m11=m[4],m12=m[7];
        float m20=m[2],m21=m[5],m22=m[8];
        float x,y,z,w,trace=m00+m11+m22;
        if(trace>0F)
        {
            float s=2F*(float)Math.sqrt(trace+1F);
            w=0.25F*s;x=(m21-m12)/s;y=(m02-m20)/s;z=(m10-m01)/s;
        }
        else if(m00>m11 && m00>m22)
        {
            float s=2F*(float)Math.sqrt(1F+m00-m11-m22);
            w=(m21-m12)/s;x=0.25F*s;y=(m01+m10)/s;z=(m02+m20)/s;
        }
        else if(m11>m22)
        {
            float s=2F*(float)Math.sqrt(1F+m11-m00-m22);
            w=(m02-m20)/s;x=(m01+m10)/s;y=0.25F*s;z=(m12+m21)/s;
        }
        else
        {
            float s=2F*(float)Math.sqrt(1F+m22-m00-m11);
            w=(m10-m01)/s;x=(m02+m20)/s;y=(m12+m21)/s;z=0.25F*s;
        }
        float len=(float)Math.sqrt(x*x+y*y+z*z+w*w);
        if(!(len>0F))throw new IllegalArgumentException("Invalid box orientation");
        return new float[]{x/len,y/len,z/len,w/len};
    }

    private static void require(float[] values,int length,String label)
    {
        if(values==null || values.length!=length)
            throw new IllegalArgumentException("Invalid "+label);
        for(float v:values)
            if(!Float.isFinite(v))
                throw new IllegalArgumentException("Nonfinite "+label);
    }
}

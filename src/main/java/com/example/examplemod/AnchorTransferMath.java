package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * World-preserving anchor reparenting for 1.12.2 (OpenGL column-major).
 *
 * worldFromItem = worldFromBone * boneFromItem
 * boneFromItem  = inverse(worldFromBone) * worldFromItem
 *
 * No Euler subtraction or independent X/Y/Z offset guesses are allowed:
 * rotation, scale and translated pivots must be accounted for together.
 */
public final class AnchorTransferMath
{
    private AnchorTransferMath(){}

    public static float[] rebase(float[] worldFromItem,float[] worldFromBone)
    {
        return BodyPartPhysicsMatrixSpace.multiply(
                inverseAffine(worldFromBone),worldFromItem);
    }

    public static float[] inverseAffine(float[] input)
    {
        if(input==null||input.length!=16)
            throw new IllegalArgumentException("Expected 4x4 matrix");
        for(float v:input)
            if(!Float.isFinite(v))
                throw new IllegalArgumentException("Non-finite anchor matrix");
        if(Math.abs(input[3])>1E-5F||Math.abs(input[7])>1E-5F||
                Math.abs(input[11])>1E-5F||Math.abs(input[15]-1F)>1E-5F)
            throw new IllegalArgumentException("Expected affine matrix");
        final double a=input[0],b=input[4],c=input[8],
                d=input[1],e=input[5],f=input[9],
                g=input[2],h=input[6],i=input[10];
        double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        if(!Double.isFinite(det)||Math.abs(det)<1E-9D)
            throw new IllegalArgumentException("Singular anchor transform");
        final double id=1D/det;
        float[] out=BodyPartPhysicsMatrixSpace.identity();
        out[0]=(float)((e*i-f*h)*id);
        out[4]=(float)((c*h-b*i)*id);
        out[8]=(float)((b*f-c*e)*id);
        out[1]=(float)((f*g-d*i)*id);
        out[5]=(float)((a*i-c*g)*id);
        out[9]=(float)((c*d-a*f)*id);
        out[2]=(float)((d*h-e*g)*id);
        out[6]=(float)((b*g-a*h)*id);
        out[10]=(float)((a*e-b*d)*id);
        float tx=input[12],ty=input[13],tz=input[14];
        out[12]=-out[0]*tx-out[4]*ty-out[8]*tz;
        out[13]=-out[1]*tx-out[5]*ty-out[9]*tz;
        out[14]=-out[2]*tx-out[6]*ty-out[10]*tz;
        return out;
    }

    /** Decompose to Metamorph BodyPart's T * Rz * Ry * Rx * S.
     * Translation in the *editor* is pixels, while the underlying
     * BodyPart uses blocks. Reject shears instead of silently snapping.
     */
    public static AnimationTransform toBodyPartTransform(float[] local)
    {
        if(local==null||local.length!=16)
            throw new IllegalArgumentException("Missing local matrix");
        double sx=norm(local,0),sy=norm(local,4),sz=norm(local,8);
        if(Math.min(sx,Math.min(sy,sz))<1E-7D)
            throw new IllegalArgumentException("Degenerate attachment scale");
        double a=local[0]/sx,b=local[4]/sy,c=local[8]/sz;
        double d=local[1]/sx,e=local[5]/sy,f=local[9]/sz;
        double g=local[2]/sx,h=local[6]/sy,i=local[10]/sz;
        if(Math.abs(a*b+d*e+g*h)>1E-3D||
                Math.abs(a*c+d*f+g*i)>1E-3D||
                Math.abs(b*c+e*f+h*i)>1E-3D)
            throw new IllegalArgumentException(
                    "Sheared anchor transform cannot be represented by BodyPart");
        // Preserve mirrored source geometry with signed X scale.
        if(a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g)<0D)
        {sx=-sx;a=-a;d=-d;g=-g;}
        double ry=Math.asin(Math.max(-1D,Math.min(1D,-g)));
        double rx,rz;
        if(Math.abs(Math.cos(ry))>1E-5D)
        {
            rx=Math.atan2(h,i);
            rz=Math.atan2(d,a);
        }
        else
        {
            rx=0D;
            rz=Math.atan2(-b,e);
        }
        AnimationTransform t=new AnimationTransform();
        t.setPosition(local[12]*16F,local[13]*16F,local[14]*16F);
        t.setRotation((float)Math.toDegrees(rx),
                (float)Math.toDegrees(ry),(float)Math.toDegrees(rz));
        t.setScale((float)sx,(float)sy,(float)sz);
        return t;
    }
    private static double norm(float[] m,int column)
    {
        return Math.sqrt((double)m[column]*m[column]+
                (double)m[column+1]*m[column+1]+
                (double)m[column+2]*m[column+2]);
    }

    public static NBTTagCompound matrixToNBT(float[] matrix)
    {
        if(matrix==null||matrix.length!=16)
            throw new IllegalArgumentException("Expected 4x4 matrix");
        NBTTagCompound tag=new NBTTagCompound();
        for(int k=0;k<16;k++)
        {
            if(!Float.isFinite(matrix[k]))
                throw new IllegalArgumentException("Non-finite anchor");
            tag.setDouble("m"+k,matrix[k]);
        }
        return tag;
    }

    public static float[] matrixFromNBT(NBTTagCompound tag)
    {
        if(tag==null)return null;
        float[] out=new float[16];
        for(int i=0;i<16;i++)
        {
            if(!tag.hasKey("m"+i,6))return null;
            out[i]=(float)tag.getDouble("m"+i);
            if(!Float.isFinite(out[i]))return null;
        }
        return out;
    }

    /** For regression: exact local BodyPart T * Rz * Ry * Rx * S */
    public static float[] composeBodyPart(AnimationTransform t)
    {
        float rx=(float)Math.toRadians(t.getRotationX()),
                ry=(float)Math.toRadians(t.getRotationY()),
                rz=(float)Math.toRadians(t.getRotationZ());
        float cx=(float)Math.cos(rx),sx=(float)Math.sin(rx);
        float cy=(float)Math.cos(ry),sy=(float)Math.sin(ry);
        float cz=(float)Math.cos(rz),sz=(float)Math.sin(rz);
        float[] m=BodyPartPhysicsMatrixSpace.identity();
        // Matrix columns of Rz*Ry*Rx, multiplied by each column scale.
        m[0]=cz*cy*t.getScaleX();
        m[1]=sz*cy*t.getScaleX();
        m[2]=-sy*t.getScaleX();
        m[4]=(cz*sy*sx-sz*cx)*t.getScaleY();
        m[5]=(sz*sy*sx+cz*cx)*t.getScaleY();
        m[6]=cy*sx*t.getScaleY();
        m[8]=(cz*sy*cx+sz*sx)*t.getScaleZ();
        m[9]=(sz*sy*cx-cz*sx)*t.getScaleZ();
        m[10]=cy*cx*t.getScaleZ();
        m[12]=t.getPositionX()/16F;
        m[13]=t.getPositionY()/16F;
        m[14]=t.getPositionZ()/16F;
        return m;
    }
}

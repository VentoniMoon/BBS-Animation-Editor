package com.example.examplemod;

/**
 * Jolt primitive descriptor independent of Forge, Blockbench and JNI.
 *
 * Each row: world center xyz, quaternion xyzw, shape half extents xyz,
 * integer primitive code in slot 10: 0 box, 1 sphere, 2 capsule.
 * Sphere radius = half[0], capsule axis = local Y, radius = half[0],
 * half-cylinder height = max(.005, half[1]-radius).
 *
 * A decoded Chameleon cube may have its own pivot/rotation. The shape
 * is measured from its actual rendered OBB, NEVER inferred from the
 * model's filename or mirrored according to a guessed left/right side.
 */
public final class JoltColliderPrimitives
{
    public static final int BOX=0,SPHERE=1,CAPSULE=2;
    private JoltColliderPrimitives() {}

    public static float[] encode(float[] obb,int primitive,float scale)
    {
        if(obb==null || obb.length!=10 || !Float.isFinite(scale) ||
                scale<0.25F || scale>2F || primitive<BOX ||
                primitive>CAPSULE)
            throw new IllegalArgumentException("Invalid collider geometry");
        float[] shape=new float[11];
        System.arraycopy(obb,0,shape,0,10);
        for(float value:obb)
            if(!Float.isFinite(value))
                throw new IllegalArgumentException("Nonfinite source cube");
        float minHalf=Math.min(obb[7],Math.min(obb[8],obb[9]));
        if(!(minHalf>0F))throw new IllegalArgumentException("Degenerate source cube");
        shape[10]=primitive;
        if(primitive==BOX)
        {
            for(int i=7;i<10;i++)shape[i]*=scale;
        }
        else if(primitive==SPHERE)
        {
            float radius=minHalf*scale;
            shape[7]=shape[8]=shape[9]=radius;
        }
        else
        {
            // Orient Jolt's native Y-aligned capsule along the longest
            // actual cube axis, in decoded/rotated bone-local space.
            int longest=7;
            if(obb[8]>obb[longest])longest=8;
            if(obb[9]>obb[longest])longest=9;
            float radius=0F;
            for(int i=7;i<10;i++)
                if(i!=longest)radius=radius==0F?
                        obb[i]:Math.min(radius,obb[i]);
            radius*=scale;
            float halfLength=obb[longest]*scale;
            if(longest!=8)
            {
                float h=(float)Math.sqrt(0.5D);
                // To rotate local +Y onto +X use a -90° Z quaternion;
                // to rotate it onto +Z use a +90° X quaternion.
                float[] q=longest==7 ?
                        new float[]{0F,0F,-h,h} :
                        new float[]{h,0F,0F,h};
                float x=shape[3],y=shape[4],z=shape[5],w=shape[6];
                shape[3]=w*q[0]+x*q[3]+y*q[2]-z*q[1];
                shape[4]=w*q[1]-x*q[2]+y*q[3]+z*q[0];
                shape[5]=w*q[2]+x*q[1]-y*q[0]+z*q[3];
                shape[6]=w*q[3]-x*q[0]-y*q[1]-z*q[2];
            }
            shape[7]=radius;
            shape[8]=Math.max(halfLength,radius+0.005F);
            shape[9]=radius;
        }
        for(int i=7;i<10;i++)
            if(!(shape[i]>=0.003F && shape[i]<=2F))
                throw new IllegalArgumentException("Invalid physical primitive size");
        return shape;
    }

    public static float[] toOutline(float[] primitive)
    {
        if(primitive==null || primitive.length!=11)
            throw new IllegalArgumentException("Invalid primitive row");
        float[] result=new float[10];
        System.arraycopy(primitive,0,result,0,10);
        return result;
    }

    public static boolean sameTopology(float[][] a,float[][] b)
    {
        if(a==null || b==null)return a==b;
        if(a.length!=b.length)return false;
        for(int i=0;i<a.length;i++)
        {
            if(a[i]==null||b[i]==null||a[i].length!=11||b[i].length!=11)
                return false;
            if(a[i][10]!=b[i][10])return false;
            for(int k=7;k<10;k++)
                if(Math.abs(a[i][k]-b[i][k])>0.025F)return false;
        }
        return true;
    }
}

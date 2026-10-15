package com.example.examplemod;

/**
 * Java 8 pure-math rotations for Chameleon physics.
 * Rotation matrices use column-major layout and right-handed axes.
 * The XYZ Euler representation means R = Rz * Ry * Rx.
 * This helper does not mutate an animation pose or assume bone-axis signs.
 */
public final class BodyPartPhysicsRotation
{
    private BodyPartPhysicsRotation() {}

    public static float[] multiply3(float[] a, float[] b)
    {
        if (a==null || b==null || a.length!=9 || b.length!=9)
            throw new IllegalArgumentException("Need 3x3 matrices");
        float[] out=new float[9];
        for(int col=0;col<3;col++)
            for(int row=0;row<3;row++)
                for(int k=0;k<3;k++)
                    out[col*3+row]+=a[k*3+row]*b[col*3+k];
        return out;
    }

    public static float[] eulerXYZ(float x, float y, float z)
    {
        float cx=(float)Math.cos(x),sx=(float)Math.sin(x);
        float cy=(float)Math.cos(y),sy=(float)Math.sin(y);
        float cz=(float)Math.cos(z),sz=(float)Math.sin(z);
        return new float[] {
            cz*cy,sz*cy,-sy,
            cz*sy*sx-sz*cx,sz*sy*sx+cz*cx,cy*sx,
            cz*sy*cx+sz*sx,sz*sy*cx-cz*sx,cy*cx
        };
    }

    public static float[] axisAngle(float[] swing)
    {
        if(swing==null || swing.length!=4) throw new IllegalArgumentException("Need axis angle");
        float x=swing[0],y=swing[1],z=swing[2],a=swing[3];
        float len=(float)Math.sqrt(x*x+y*y+z*z);
        if(len<1e-8F) return new float[]{1,0,0,0,1,0,0,0,1};
        x/=len;y/=len;z/=len;
        float c=(float)Math.cos(a),s=(float)Math.sin(a),t=1-c;
        return new float[]{
            t*x*x+c,t*x*y+s*z,t*x*z-s*y,
            t*x*y-s*z,t*y*y+c,t*y*z+s*x,
            t*x*z+s*y,t*y*z-s*x,t*z*z+c
        };
    }

    public static float[] toEulerXYZ(float[] m)
    {
        if(m==null || m.length!=9) throw new IllegalArgumentException("Need 3x3");
        float y=(float)Math.asin(Math.max(-1,Math.min(1,-m[2])));
        float cy=(float)Math.cos(y);
        float x,z;
        if(Math.abs(cy)>0.00001F)
        {
            x=(float)Math.atan2(m[5],m[8]);
            z=(float)Math.atan2(m[1],m[0]);
        }
        else
        {
            x=0F;
            z=(float)Math.atan2(-m[3],m[4]);
        }
        return new float[]{x,y,z};
    }

    /** Swing expressed in the already-rotated bone basis: Rnew = Rbase * Rswing. */
    public static float[] composeInBoneSpace(float[] originalEuler, float[] localSwing)
    {
        if (originalEuler == null || originalEuler.length != 3)
            throw new IllegalArgumentException("Need Euler XYZ");
        return toEulerXYZ(multiply3(
                eulerXYZ(originalEuler[0], originalEuler[1], originalEuler[2]),
                axisAngle(localSwing)));
    }

    /** Compose a swing relative to an existing Euler pose, not by adding
     * axis-angle components as Euler offsets.
     */
    public static float[] compose(float[] originalEuler, float[] localSwing)
    {
        if(originalEuler==null || originalEuler.length!=3)
            throw new IllegalArgumentException("Need Euler XYZ");
        return toEulerXYZ(multiply3(
                axisAngle(localSwing),
                eulerXYZ(originalEuler[0],originalEuler[1],originalEuler[2])));
    }
}

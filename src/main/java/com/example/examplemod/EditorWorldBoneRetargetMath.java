package com.example.examplemod;

/**
 * Rotation-only bridge from FABRIK joint directions to local XYZ Euler.
 *
 * Its matrices deliberately match AnimationTransform's Rz*Ry*Rx order.
 * No translation or scale is returned or changed by this class.
 */
public final class EditorWorldBoneRetargetMath
{
    private EditorWorldBoneRetargetMath(){}

    public static float[] alignLocalEuler(float[] parentWorldEuler,
            float[] localEuler,float[] fromWorld,float[] toWorld)
    {
        if(!finite3(parentWorldEuler)||!finite3(localEuler)||
                !finite3(fromWorld)||!finite3(toWorld))
            throw new IllegalArgumentException("Bad bone retarget input");
        double[][] parent=fromEuler(parentWorldEuler);
        double[][] local=fromEuler(localEuler);
        double[][] originalWorld=multiply(parent,local);
        double[][] delta=fromTo(fromWorld,toWorld);
        return toEuler(multiply(transpose(parent),
                multiply(delta,originalWorld)));
    }

    public static float[] rotate(float[] euler,float[] direction)
    {
        if(!finite3(euler)||!finite3(direction))
            throw new IllegalArgumentException("Bad rotation input");
        double[][] m=fromEuler(euler);
        return new float[]{
                (float)(m[0][0]*direction[0]+m[0][1]*direction[1]+m[0][2]*direction[2]),
                (float)(m[1][0]*direction[0]+m[1][1]*direction[1]+m[1][2]*direction[2]),
                (float)(m[2][0]*direction[0]+m[2][1]*direction[1]+m[2][2]*direction[2])};
    }

    /** Preserve nearby winding when the pose crosses +/-180 degrees. */
    public static float nearestAngle(float candidate,float reference)
    {
        while(candidate-reference>180F)candidate-=360F;
        while(candidate-reference< -180F)candidate+=360F;
        return candidate;
    }

    private static boolean finite3(float[] xyz)
    {
        if(xyz==null||xyz.length!=3)return false;
        for(float v:xyz)if(!Float.isFinite(v))return false;
        return true;
    }

    private static double[][] fromEuler(float[] e)
    {
        double x=Math.toRadians(e[0]),y=Math.toRadians(e[1]),
                z=Math.toRadians(e[2]);
        double cx=Math.cos(x),sx=Math.sin(x);
        double cy=Math.cos(y),sy=Math.sin(y);
        double cz=Math.cos(z),sz=Math.sin(z);
        return new double[][]{
                {cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx},
                {sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx},
                {-sy,cy*sx,cy*cx}};
    }

    private static float[] toEuler(double[][] m)
    {
        double sinY=Math.max(-1D,Math.min(1D,-m[2][0]));
        double y=Math.asin(sinY),x,z;
        if(Math.abs(Math.cos(y))>0.000001D)
        {
            x=Math.atan2(m[2][1],m[2][2]);
            z=Math.atan2(m[1][0],m[0][0]);
        }
        else
        {
            x=Math.atan2(-m[0][1],m[1][1]);
            z=0D;
        }
        return new float[]{(float)Math.toDegrees(x),
                (float)Math.toDegrees(y),(float)Math.toDegrees(z)};
    }

    private static double[][] fromTo(float[] from,float[] to)
    {
        double f=norm(from),t=norm(to);
        if(f<1E-6D||t<1E-6D)
            throw new IllegalArgumentException("Cannot align zero bone vector");
        double ax=from[0]/f,ay=from[1]/f,az=from[2]/f;
        double bx=to[0]/t,by=to[1]/t,bz=to[2]/t;
        double dot=Math.max(-1D,Math.min(1D,ax*bx+ay*by+az*bz));
        if(dot>1D-1E-10D)return identity();
        double qx,qy,qz,qw;
        if(dot< -1D+1E-9D)
        {
            // Stable perpendicular for exact 180-degree turns.
            double xx=Math.abs(ax)<0.75D?1D:0D;
            double yy=xx==0D?1D:0D;
            qx=ay*0D-az*yy;
            qy=az*xx-ax*0D;
            qz=ax*yy-ay*xx;
            double d=Math.sqrt(qx*qx+qy*qy+qz*qz);
            qx/=d;qy/=d;qz/=d;qw=0D;
        }
        else
        {
            qx=ay*bz-az*by;
            qy=az*bx-ax*bz;
            qz=ax*by-ay*bx;
            qw=1D+dot;
            double d=Math.sqrt(qx*qx+qy*qy+qz*qz+qw*qw);
            qx/=d;qy/=d;qz/=d;qw/=d;
        }
        return new double[][]{
                {1D-2D*(qy*qy+qz*qz),2D*(qx*qy-qz*qw),2D*(qx*qz+qy*qw)},
                {2D*(qx*qy+qz*qw),1D-2D*(qx*qx+qz*qz),2D*(qy*qz-qx*qw)},
                {2D*(qx*qz-qy*qw),2D*(qy*qz+qx*qw),1D-2D*(qx*qx+qy*qy)}};
    }

    private static double norm(float[] p)
    {
        return Math.sqrt((double)p[0]*p[0]+(double)p[1]*p[1]+(double)p[2]*p[2]);
    }
    private static double[][] identity()
    {
        return new double[][]{{1D,0D,0D},{0D,1D,0D},{0D,0D,1D}};
    }
    private static double[][] transpose(double[][] a)
    {
        double[][] b=new double[3][3];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)b[i][j]=a[j][i];
        return b;
    }
    private static double[][] multiply(double[][] a,double[][] b)
    {
        double[][] c=new double[3][3];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)
            for(int k=0;k<3;k++)c[i][j]+=a[i][k]*b[k][j];
        return c;
    }
}

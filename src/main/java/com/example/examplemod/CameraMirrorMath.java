package com.example.examplemod;

/** Java 8-only planar geometry (independent of Minecraft / GL). */
public final class CameraMirrorMath
{
    private CameraMirrorMath(){}
    public static double[] normal(double yaw)
    {
        double a=Math.toRadians(yaw);
        return new double[]{Math.sin(a),0D,Math.cos(a)};
    }
    public static double[] right(double yaw)
    {
        double a=Math.toRadians(yaw);
        return new double[]{Math.cos(a),0D,-Math.sin(a)};
    }
    public static double[][] corners(double x,double y,double z,
            double yaw,double width,double height)
    {
        double[] r=right(yaw);
        double w=width*0.5D,h=height*0.5D;
        return new double[][]{
            {x-r[0]*w,y-h,z-r[2]*w},
            {x+r[0]*w,y-h,z+r[2]*w},
            {x+r[0]*w,y+h,z+r[2]*w},
            {x-r[0]*w,y+h,z-r[2]*w}
        };
    }
    public static double side(double x,double z,double px,double pz,
            double yaw)
    {
        double[] n=normal(yaw);
        return (px-x)*n[0]+(pz-z)*n[2];
    }
    public static double[] reflectPosition(double x,double y,double z,
            double px,double py,double pz,double yaw)
    {
        double[] n=normal(yaw);
        double d=(px-x)*n[0]+(pz-z)*n[2];
        return new double[]{px-2D*d*n[0],py,pz-2D*d*n[2]};
    }
    public static double[] reflectDirection(double yaw,double dx,double dy,
            double dz)
    {
        double[] n=normal(yaw);
        double d=dx*n[0]+dz*n[2];
        return new double[]{dx-2D*d*n[0],dy,dz-2D*d*n[2]};
    }
    /**
     * A reflected eye needs its *direction* reflected as well. Roll reverses
     * on a vertical mirror, while pitch is preserved. Camera yaw zero looks
     * at -Z in BBS FS / PreviewCameraProjection.
     */
    public static PreviewCameraPose reflect(PreviewCameraPose view,
            double x,double y,double z,double yaw)
    {
        double[] p=reflectPosition(x,y,z,view.x,view.y,view.z,yaw);
        double a=Math.toRadians(view.yaw),b=Math.toRadians(view.pitch);
        double[] d=reflectDirection(yaw,
                -Math.sin(a)*Math.cos(b),
                -Math.sin(b),-Math.cos(a)*Math.cos(b));
        float reflectedYaw=(float)Math.toDegrees(Math.atan2(-d[0],-d[2]));
        float reflectedPitch=(float)Math.toDegrees(Math.atan2(
                -d[1],Math.hypot(d[0],d[2])));
        return new PreviewCameraPose(p[0],p[1],p[2],
                reflectedYaw,reflectedPitch,-view.roll,view.fov,
                view.nearPlane,view.farPlane);
    }
    /**
     * Intersect a camera ray with the bounded rectangle. Returns world XYZ
     * and local half-width/height offsets, or null when outside/behind.
     */
    public static double[] hit(double x,double y,double z,double yaw,
            double width,double height,double ox,double oy,double oz,
            double dx,double dy,double dz)
    {
        double[] n=normal(yaw);
        double denom=dx*n[0]+dz*n[2];
        if(Math.abs(denom)<1E-7D)return null;
        double t=((x-ox)*n[0]+(z-oz)*n[2])/denom;
        if(t<0.01D||t>2048D)return null;
        double hx=ox+dx*t,hy=oy+dy*t,hz=oz+dz*t;
        double[] right=right(yaw);
        double u=(hx-x)*right[0]+(hz-z)*right[2];
        double v=hy-y;
        if(Math.abs(u)>width*0.5D||Math.abs(v)>height*0.5D)
            return null;
        return new double[]{hx,hy,hz,u,v};
    }
}

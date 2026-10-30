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
     * Transform a world-space vector into the same eye space used by
     * PreviewCameraProjection and the native BBS FS camera.
     */
    public static double[] viewVector(PreviewCameraPose camera,
            double dx,double dy,double dz)
    {
        double yaw=Math.toRadians(camera.yaw);
        double pitch=Math.toRadians(camera.pitch);
        double roll=Math.toRadians(camera.roll);
        double cy=Math.cos(yaw),sy=Math.sin(yaw);
        double localX=cy*dx-sy*dz;
        double localZ=sy*dx+cy*dz;
        double cp=Math.cos(pitch),sp=Math.sin(pitch);
        double localY=cp*dy+sp*localZ;
        localZ=-sp*dy+cp*localZ;
        double cr=Math.cos(roll),sr=Math.sin(roll);
        return new double[]{
                cr*localX+sr*localY,
                cr*localY-sr*localX,
                localZ
        };
    }

    /**
     * An eye-space OpenGL clip plane for the mirror's FRONT half-space.
     *
     * The reflected eye is physically behind the surface and must not
     * see walls, blocks or actors behind it. Values are (A,B,C,D) where
     * A*xEye+B*yEye+C*zEye+D >= 0 remains visible. The positive margin
     * avoids drawing a wall coplanar with the mirror.
     *
     * Set this with GL_MODELVIEW identity; OpenGL transforms clip planes
     * by whichever modelview is active when glClipPlane() is called.
     */
    public static double[] frontClipPlane(PreviewCameraPose virtualCamera,
            double x,double y,double z,double yaw,double margin)
    {
        double[] n=normal(yaw);
        double[] eyeN=viewVector(virtualCamera,n[0],n[1],n[2]);
        double[] eyeP=viewVector(virtualCamera,
                x-virtualCamera.x,y-virtualCamera.y,z-virtualCamera.z);
        double d=-(eyeN[0]*eyeP[0]+eyeN[1]*eyeP[1]+
                eyeN[2]*eyeP[2])-Math.max(0D,margin);
        return new double[]{eyeN[0],eyeN[1],eyeN[2],d};
    }

    /** Positive means retained, negative means behind the mirror. */
    public static double clipSide(double[] plane,double[] eyePoint)
    {
        return plane[0]*eyePoint[0]+plane[1]*eyePoint[1]+
                plane[2]*eyePoint[2]+plane[3];
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

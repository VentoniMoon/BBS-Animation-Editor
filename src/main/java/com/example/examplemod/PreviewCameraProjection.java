package com.example.examplemod;

/**
 * One CPU projection source for gizmo hit tests, Jolt overlays and camera
 * guides. Algebra mirrors PreviewWorldRenderer model-view and perspective:
 * rotate -roll around Z, -pitch around X, -yaw around Y, then translate by
 * negative camera eye. Screen Y increases down; FOV is vertical.
 *
 * Pure Java 8; no GL state or Minecraft classes.
 */
public final class PreviewCameraProjection
{
    private PreviewCameraProjection() {}

    public static float[] project(double x,double y,double z,
            PreviewCameraPose camera,int viewportX,int viewportY,
            int width,int height)
    {
        if(camera==null||width<=0||height<=0)return null;
        double dx=x-camera.x,dy=y-camera.y,dz=z-camera.z;
        double yaw=Math.toRadians(camera.yaw),pitch=Math.toRadians(camera.pitch);
        double cy=Math.cos(yaw),sy=Math.sin(yaw);
        double localX=cy*dx-sy*dz;
        double localZ=sy*dx+cy*dz;
        double cp=Math.cos(pitch),sp=Math.sin(pitch);
        double localY=cp*dy+sp*localZ;
        localZ=-sp*dy+cp*localZ;
        if(localZ>=-camera.nearPlane || !Double.isFinite(localZ))return null;
        double roll=Math.toRadians(camera.roll);
        double cr=Math.cos(roll),sr=Math.sin(roll);
        double viewX=cr*localX+sr*localY;
        double viewY=cr*localY-sr*localX;
        double focal=height/(2D*Math.tan(Math.toRadians(camera.fov)*0.5D));
        double sx=viewportX+width*0.5D+viewX*focal/-localZ;
        double py=viewportY+height*0.5D-viewY*focal/-localZ;
        if(!Double.isFinite(sx)||!Double.isFinite(py)||
                Math.abs(sx)>1E7D||Math.abs(py)>1E7D)return null;
        return new float[]{(float)sx,(float)py};
    }

    /** World-space ray direction, normalized, through GUI pixel coordinates. */
    public static double[] ray(PreviewCameraPose camera,double screenX,
            double screenY,int left,int top,int width,int height)
    {
        if(camera==null||width<=0||height<=0)return null;
        double focal=height/(2D*Math.tan(Math.toRadians(camera.fov)*0.5D));
        double x=(screenX-left-width*0.5D)/focal;
        double y=-(screenY-top-height*0.5D)/focal;
        double z=-1D;
        double roll=Math.toRadians(camera.roll);
        double cr=Math.cos(roll),sr=Math.sin(roll);
        double rx=cr*x-sr*y;
        double ry=sr*x+cr*y;
        double pitch=Math.toRadians(camera.pitch);
        double cp=Math.cos(pitch),sp=Math.sin(pitch);
        double yy=cp*ry-sp*z;
        double zz=sp*ry+cp*z;
        double yaw=Math.toRadians(camera.yaw);
        double cy=Math.cos(yaw),sy=Math.sin(yaw);
        double xx=cy*rx+sy*zz;
        double worldZ=-sy*rx+cy*zz;
        double len=Math.sqrt(xx*xx+yy*yy+worldZ*worldZ);
        return new double[]{xx/len,yy/len,worldZ/len};
    }
}

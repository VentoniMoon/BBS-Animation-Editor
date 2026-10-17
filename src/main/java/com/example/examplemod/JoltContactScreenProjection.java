package com.example.examplemod;

/**
 * Pure Java 8 mapping from Jolt native-world coordinates to screen pixels.
 * Kept algebraically identical to EditorGizmoController.project(): no GL,
 * Minecraft or OptiFine dependencies and independently testable in CI.
 */
public final class JoltContactScreenProjection
{
    private JoltContactScreenProjection() {}

    public static float[] project(double x,double y,double z,
            EditorCamera camera,int viewportX,int viewportY,
            int viewportWidth,int viewportHeight)
    {
        if(camera==null || viewportWidth<=0 || viewportHeight<=0)
            return null;
        double dx=x-camera.getCameraX();
        double dy=y-camera.getCameraY();
        double dz=z-camera.getCameraZ();

        double yaw=Math.toRadians(camera.getYaw());
        double pitch=Math.toRadians(camera.getPitch());
        double cy=Math.cos(yaw);
        double sy=Math.sin(yaw);
        double cameraX=cy*dx-sy*dz;
        double cameraZ=sy*dx+cy*dz;
        double cp=Math.cos(pitch);
        double sp=Math.sin(pitch);
        double cameraY=cp*dy+sp*cameraZ;
        cameraZ=-sp*dy+cp*cameraZ;

        if(!Double.isFinite(cameraZ) || cameraZ>=-0.05D)
            return null;

        double fov=Math.toRadians(60D);
        double focal=viewportHeight/(2D*Math.tan(fov/2D));
        double sx=viewportX+viewportWidth/2D+cameraX*focal/-cameraZ;
        double syScreen=viewportY+viewportHeight/2D-cameraY*focal/-cameraZ;
        if(!Double.isFinite(sx) || !Double.isFinite(syScreen) ||
                Math.abs(sx)>1E7D || Math.abs(syScreen)>1E7D)
            return null;
        return new float[] {(float)sx,(float)syScreen};
    }
}

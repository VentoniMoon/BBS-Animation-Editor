package com.example.examplemod;

/** No Minecraft runtime required: verify geometric mirror invariants. */
public final class CameraMirrorMathSmokeTest
{
    private static void close(double actual,double expected)
    {
        if(Math.abs(actual-expected)>1E-5D)
            throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args)
    {
        PreviewCameraPose front=new PreviewCameraPose(
                0D,65D,3D,0F,0F,0F,70F,0.05F,256F);
        PreviewCameraPose r=CameraMirrorMath.reflect(front,
                0D,65D,0D,0D);
        close(r.x,0D);close(r.y,65D);close(r.z,-3D);
        close(Math.abs(r.yaw),180D);
        close(r.pitch,0D);
        PreviewCameraPose twice=CameraMirrorMath.reflect(r,
                0D,65D,0D,0D);
        close(twice.x,front.x);close(twice.y,front.y);
        close(twice.z,front.z);
        close(Math.sin(Math.toRadians(twice.yaw)),0D);
        close(Math.cos(Math.toRadians(twice.yaw)),1D);
        double[][] quad=CameraMirrorMath.corners(
                5D,10D,-3D,0D,4D,2D);
        close(quad[0][0],3D);close(quad[0][1],9D);
        close(quad[2][0],7D);close(quad[2][1],11D);
        double[] center=CameraMirrorMath.hit(
                0D,65D,0D,0D,2D,3D,0D,65D,3D,0D,0D,-1D);
        if(center==null)throw new AssertionError("Center not picked");
        close(center[0],0D);close(center[2],0D);
        if(CameraMirrorMath.hit(0D,65D,0D,0D,2D,3D,
                4D,65D,3D,0D,0D,-1D)!=null)
            throw new AssertionError("Outside hitbox selected");
        if(CameraMirrorMath.hit(0D,65D,0D,0D,2D,3D,
                0D,65D,3D,0D,0D,1D)!=null)
            throw new AssertionError("Behind ray selected");
        double[] side=CameraMirrorMath.normal(90D);
        close(side[0],1D);close(side[2],0D);
        double[] reflected=CameraMirrorMath.reflectPosition(
                0D,0D,0D,4D,2D,3D,90D);
        close(reflected[0],-4D);
        close(reflected[1],2D);
        close(reflected[2],3D);
        System.out.println("Planar mirror geometry smoke OK");
    }
}

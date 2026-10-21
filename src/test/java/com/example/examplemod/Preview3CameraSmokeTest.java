package com.example.examplemod;

/** Pure Java 8 test of the shared Preview 3.0 projection and camera core. */
public final class Preview3CameraSmokeTest
{
    private static void near(double a,double b,double tolerance)
    {
        if(Math.abs(a-b)>tolerance)
            throw new AssertionError(a+" != "+b);
    }

    private static void require(boolean condition,String error)
    {
        if(!condition)throw new AssertionError(error);
    }

    public static void main(String[] args)
    {
        EditorCamera camera=new EditorCamera();
        camera.reset();
        camera.rotate(0,-15F); // look straight ahead
        camera.setTarget(0,0,0);
        camera.setDistance(8);
        PreviewCameraPose pose=camera.sample();
        require(camera.getMode()==EditorCamera.Mode.ORBIT,"default navigation");
        near(pose.fov,60,0.001);
        near(pose.roll,0,0.001);

        float[] center=PreviewCameraProjection.project(
                0,0,0,pose,100,200,800,450);
        require(center!=null,"target visible");
        near(center[0],500,1e-3);
        near(center[1],425,1e-3);
        double[] ray=PreviewCameraProjection.ray(pose,500,425,100,200,800,450);
        require(ray!=null,"view ray");
        near(ray[0],0,1e-6);
        near(ray[1],0,1e-6);
        near(ray[2],-1,1e-6);

        float[] side=PreviewCameraProjection.project(
                1,0,0,pose,100,200,800,450);
        require(side!=null && side[0]>center[0],"positive screen x");
        camera.setFov(100);
        float[] wide=PreviewCameraProjection.project(
                1,0,0,camera.sample(),100,200,800,450);
        require(wide[0]<side[0],"FOV changes focal distance");

        camera.setFov(60F);
        camera.setRoll(90F);
        float[] rotated=PreviewCameraProjection.project(
                1,0,0,camera.sample(),100,200,800,450);
        near(rotated[0],500,0.01);
        require(rotated[1]>425,"positive camera roll");
        float[] legacy=JoltContactScreenProjection.project(
                1,0,0,camera,100,200,800,450);
        near(legacy[0],rotated[0],0.001);
        near(legacy[1],rotated[1],0.001);
        // An explicit GL View/Projection matrix is the canonical camera
        // sample for future camera clips, stencil selection and render hooks.
        PreviewCameraMatrices matrices=new PreviewCameraMatrices(
                camera.sample(),800,450);
        double[] ndc=matrices.projectNdc(1,0,0);
        require(ndc!=null,"valid View/Projection matrix");
        near(100+(ndc[0]+1D)*800D/2D,rotated[0],0.001);
        near(200+(1D-ndc[1])*450D/2D,rotated[1],0.001);
        // The world point exactly at the camera eye cannot project.
        require(matrices.projectNdc(
                camera.getCameraX(),camera.getCameraY(),
                camera.getCameraZ())==null,
                "eye position projection should be rejected");


        camera.setMode(EditorCamera.Mode.TRACK);
        PreviewCameraPose animated=new PreviewCameraPose(24,6,-13,170,30,45,
                35,0.03F,800);
        camera.apply(animated);
        PreviewCameraPose actual=camera.sample();
        near(actual.x,animated.x,1e-6);
        near(actual.y,animated.y,1e-6);
        near(actual.z,animated.z,1e-6);
        near(actual.roll,45,1e-6);
        near(actual.fov,35,1e-6);
        near(actual.nearPlane,0.03,1e-6);
        require(camera.getMode()==EditorCamera.Mode.TRACK,
                "track mode must not change on pose application");
        // The cinematic path must interpolate rotations across +/- 180.
        PreviewCameraPose a=new PreviewCameraPose(0,0,0,170,0,0,60,0.05F,256);
        PreviewCameraPose b=new PreviewCameraPose(0,0,0,-170,0,0,60,0.05F,256);
        PreviewCameraPose mid=PreviewCameraPose.interpolate(a,b,0.5D);
        near(mid.yaw,180,1e-5);

        camera.reset();
        require(camera.getMode()==EditorCamera.Mode.ORBIT,"reset navigation");
        near(camera.getFov(),60,1e-5);
        near(camera.getRoll(),0,1e-5);
        System.out.println("Preview 3.0 camera, projection, Roll/FOV, track and ray PASS");
    }
}

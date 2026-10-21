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
        require(camera.getMode()==EditorCamera.Mode.FREE,"FREE must be default navigation");
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

        /*
         * Native Minecraft yaw includes +180 degrees from entity orientation
         * during camera setup. Verify that it produces the exact same GL
         * rotation as EditorCamera's manual actor/gizmo camera for every
         * cardinal axis, and preserves pitch/roll (no X/Z hand tuning).
         */
        for (float editorYaw : new float[]{-180F,-90F,-45F,0F,45F,90F,180F})
        {
            float entityYaw=BbsFsCameraConventions.entityYaw(editorYaw);
            float eventYaw=BbsFsCameraConventions.cameraSetupYaw(editorYaw);
            float delta=(eventYaw + editorYaw)%360F;
            near(delta,0,0.001);
            near((entityYaw+180F-eventYaw)%360F,0,0.001);
        }
        for (float angle : new float[]{-80F,-15F,0F,20F,60F})
        {
            near(BbsFsCameraConventions.entityPitch(angle),-angle,0.001);
            near(BbsFsCameraConventions.cameraSetupPitch(angle),-angle,0.001);
            near(BbsFsCameraConventions.cameraSetupRoll(angle),-angle,0.001);
        }

        /*
         * FREE RMB movement must change ONLY the look direction. Previously
         * rotate() moved the eye around a virtual target in front of it.
         */
        camera.reset();
        require(camera.getMode()==EditorCamera.Mode.FREE,"reset navigation");
        PreviewCameraPose initialEye = camera.sample();
        camera.rotate(53F, -20F);
        PreviewCameraPose freeEye = camera.sample();
        near(freeEye.x,initialEye.x,1e-8);
        near(freeEye.y,initialEye.y,1e-8);
        near(freeEye.z,initialEye.z,1e-8);
        near(freeEye.yaw,initialEye.yaw+53F,1e-6);
        near(freeEye.pitch,initialEye.pitch-20F,1e-6);

        /* Switching navigation keeps eye and angles frozen. */
        camera.setMode(EditorCamera.Mode.ORBIT);
        PreviewCameraPose orbitBefore = camera.sample();
        near(orbitBefore.x,freeEye.x,1e-8);
        near(orbitBefore.y,freeEye.y,1e-8);
        near(orbitBefore.z,freeEye.z,1e-8);
        near(orbitBefore.yaw,freeEye.yaw,1e-6);
        double pivotX=camera.getTargetX();
        double pivotY=camera.getTargetY();
        double pivotZ=camera.getTargetZ();
        camera.rotate(32F,18F);
        PreviewCameraPose orbitAfter=camera.sample();
        near(camera.getTargetX(),pivotX,1e-8);
        near(camera.getTargetY(),pivotY,1e-8);
        near(camera.getTargetZ(),pivotZ,1e-8);
        require(Math.abs(orbitAfter.x-orbitBefore.x)>0.1 ||
                Math.abs(orbitAfter.z-orbitBefore.z)>0.1,
                "ORBIT must move eye around pivot");
        camera.setMode(EditorCamera.Mode.FREE);
        PreviewCameraPose switchedEye=camera.sample();
        near(switchedEye.x,orbitAfter.x,1e-8);
        near(switchedEye.y,orbitAfter.y,1e-8);
        near(switchedEye.z,orbitAfter.z,1e-8);
        camera.rotate(-75F,40F);
        PreviewCameraPose afterSwitch=camera.sample();
        near(afterSwitch.x,switchedEye.x,1e-8);
        near(afterSwitch.y,switchedEye.y,1e-8);
        near(afterSwitch.z,switchedEye.z,1e-8);

        /* FREE wheel dollies along gaze instead of changing orbit radius. */
        camera.reset();
        camera.rotate(0F,-15F);
        PreviewCameraPose beforeDolly=camera.sample();
        double storedDistance=camera.getDistance();
        camera.zoom(-1.5D);
        PreviewCameraPose afterDolly=camera.sample();
        near(afterDolly.x,beforeDolly.x,1e-8);
        near(afterDolly.y,beforeDolly.y,1e-8);
        near(afterDolly.z,beforeDolly.z-1.5D,1e-8);
        near(camera.getDistance(),storedDistance,1e-8);

        /* ORBIT wheel must continue changing distance, not the target. */
        camera.setMode(EditorCamera.Mode.ORBIT);
        pivotX=camera.getTargetX();
        pivotY=camera.getTargetY();
        pivotZ=camera.getTargetZ();
        camera.zoom(1.5D);
        near(camera.getDistance(),storedDistance+1.5D,1e-8);
        near(camera.getTargetX(),pivotX,1e-8);
        near(camera.getTargetY(),pivotY,1e-8);
        near(camera.getTargetZ(),pivotZ,1e-8);

        camera.setMode(EditorCamera.Mode.TRACK);
        camera.apply(animated);
        camera.setMode(EditorCamera.Mode.FREE);
        PreviewCameraPose trackToFree=camera.sample();
        near(trackToFree.x,animated.x,1e-8);
        near(trackToFree.y,animated.y,1e-8);
        near(trackToFree.z,animated.z,1e-8);
        camera.rotate(10F, -10F);
        near(camera.getCameraX(),trackToFree.x,1e-8);
        near(camera.getCameraY(),trackToFree.y,1e-8);
        near(camera.getCameraZ(),trackToFree.z,1e-8);

        camera.reset();
        require(camera.getMode()==EditorCamera.Mode.FREE,"reset navigation");
        near(camera.getFov(),60,1e-5);
        near(camera.getRoll(),0,1e-5);
        System.out.println("Preview 3.0 camera, projection, Roll/FOV, track and ray PASS");
    }
}

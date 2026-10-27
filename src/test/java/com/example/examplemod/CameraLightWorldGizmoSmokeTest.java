package com.example.examplemod;

/** Java 8 regression for a CAMERA scene light, without any world or GL. */
public final class CameraLightWorldGizmoSmokeTest
{
    private static void require(boolean test,String message)
    {
        if(!test)throw new AssertionError(message);
    }
    private static void near(double actual,double expected,double tolerance)
    {
        require(Math.abs(actual-expected)<=tolerance,
                actual+" != "+expected);
    }

    public static void main(String[] args)
    {
        PreviewCameraPose camera=new PreviewCameraPose(
                0,0,0,0,0,0,60,0.05F,500);
        int left=100,top=100,width=800,height=500;
        double[] forward=PreviewCameraProjection.ray(camera,
                .5D,.5D,0,0,1,1);
        near(forward[0],0,1E-6);
        near(forward[1],0,1E-6);
        near(forward[2],-1,1E-6);
        double x=0,y=0,z=-10;
        CameraLightWorldGizmo.Handle handle=
                CameraLightWorldGizmo.project(camera,x,y,z,
                        left,top,width,height);
        require(handle!=null,"new light in front of film camera");
        near(handle.x,500,1E-3);near(handle.y,350,1E-3);
        require(handle.tips[0][0]>handle.x,"X arrow points right");
        require(handle.tips[1][1]<handle.y,"Y arrow points up");
        require(CameraLightWorldGizmo.hitAxis(handle,
                handle.tips[0][0]-5,handle.tips[0][1])==0,
                "hit test must include X arrow stem");
        require(CameraLightWorldGizmo.hitAxis(handle,
                handle.tips[1][0],handle.tips[1][1]+5)==1,
                "hit test must include Y arrow stem");

        double startX=handle.x+(handle.tips[0][0]-handle.x)*0.65;
        double startY=handle.y+(handle.tips[0][1]-handle.y)*0.65;
        CameraLightWorldGizmo.Drag drag=new CameraLightWorldGizmo.Drag(
                0,camera,x,y,z,left,top,width,height,startX,startY);
        near(drag.delta(startX,startY),0,1E-8);
        double delta=drag.delta(startX+35,startY);
        require(delta>0.1,"dragging X arrow right advances world X");
        // A world-space edit keeps the source's Target independent,
        // exactly like the working POSE light rig.
        BetterLightsWorldState light=BetterLightsWorldState.points(
                x+delta,y,z,2,0,-14);
        require(light!=null,"edited static light remains valid");
        near(light.targetX,2,1E-6);near(light.targetZ,-14,1E-6);

        // Camera changes must reproject geometry but NEVER move the stored
        // light world point. The handle is offscreen/behind, not camera-locked.
        PreviewCameraPose side=new PreviewCameraPose(
                10,0,0,90,0,0,60,.05F,500);
        require(CameraLightWorldGizmo.project(side,
                x,y,z,left,top,width,height)!=null,
                "world position still projects from side view");
        require(CameraLightWorldGizmo.project(camera,
                0,0,10,left,top,width,height)==null,
                "behind-camera source never produces screen handles");

        PreviewCameraPose rolled=new PreviewCameraPose(
                0,0,0,0,0,45,90,.05F,500);
        CameraLightWorldGizmo.Handle h=CameraLightWorldGizmo.project(
                rolled,0,0,-10,left,top,width,height);
        require(h!=null && h.tips[1]!=null,
                "roll and FOV do not corrupt world axes");
        CameraLightWorldGizmo.Drag yDrag=new CameraLightWorldGizmo.Drag(
                1,rolled,0,0,-10,left,top,width,height,h.x,h.y);
        double vx=h.tips[1][0]-h.x,vy=h.tips[1][1]-h.y;
        require(yDrag.delta(h.x+vx*.5,h.y+vy*.5)>0,
                "rolled-camera drag follows positive Y arrow");

        System.out.println("CAMERA light world gizmo, picking and drag PASS");
    }
}

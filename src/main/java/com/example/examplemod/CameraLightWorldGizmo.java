package com.example.examplemod;

/**
 * CAMERA's static-light move gizmo. Uses the EXACT same world-block origin,
 * target and camera projection as BetterLightsWorldState and the POSE rig.
 * This has no Minecraft / LWJGL dependency so picking and dragging can be
 * verified using Java 8 camera regression tests.
 *
 * Axes represent +world X/Y/Z, never model pixels or camera coordinates.
 */
public final class CameraLightWorldGizmo
{
    public static final int ORIGIN=0;
    public static final int TARGET=1;
    public static final int NONE=-1;
    private static final double HANDLE_PIXELS=65D;

    public static final class Handle
    {
        public final double x,y;
        public final double[][] tips;
        public final double axisWorldLength;
        Handle(double x,double y,double[][] tips,double length)
        {
            this.x=x;this.y=y;this.tips=tips;this.axisWorldLength=length;
        }
    }

    private CameraLightWorldGizmo(){}

    public static Handle project(PreviewCameraPose camera,
            double x,double y,double z,int left,int top,int width,int height)
    {
        float[] center=PreviewCameraProjection.project(x,y,z,camera,
                left,top,width,height);
        if(center==null)return null;
        double focal=height/(2D*Math.tan(Math.toRadians(camera.fov)*0.5D));
        double dx=x-camera.x,dy=y-camera.y,dz=z-camera.z;
        double dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double length=Math.max(0.25D,Math.min(24D,
                HANDLE_PIXELS*dist/Math.max(1D,focal)));
        double[][] tips=new double[3][];
        for(int axis=0;axis<3;axis++)
        {
            float[] p=PreviewCameraProjection.project(
                    x+(axis==0?length:0D),
                    y+(axis==1?length:0D),
                    z+(axis==2?length:0D),
                    camera,left,top,width,height);
            if(p!=null)tips[axis]=new double[]{p[0],p[1]};
        }
        return new Handle(center[0],center[1],tips,length);
    }

    /** Hit arrow stems as well as their tips, like the POSE move gizmo. */
    public static int hitAxis(Handle handle,double mx,double my)
    {
        if(handle==null)return NONE;
        int selected=NONE;
        double best=100D;
        for(int i=0;i<3;i++)
        {
            double[] p=handle.tips[i];
            if(p==null)continue;
            double vx=p[0]-handle.x,vy=p[1]-handle.y;
            double length2=vx*vx+vy*vy;
            if(length2<64D)continue; // an axis aligned with view is invisible
            double t=((mx-handle.x)*vx+(my-handle.y)*vy)/length2;
            if(t<0.18D||t>1.23D)continue;
            double sx=handle.x+Math.min(1D,t)*vx;
            double sy=handle.y+Math.min(1D,t)*vy;
            double d=(mx-sx)*(mx-sx)+(my-sy)*(my-sy);
            if(d<best){best=d;selected=i;}
        }
        return selected;
    }

    /** A frozen drag plane/axis. Sampling the moving position causes drift. */
    public static final class Drag
    {
        public final int axis;
        private final PreviewCameraPose camera;
        private final double[] origin;
        private final int left,top,width,height;
        private final double startX,startY;
        private final double startClosest;
        private final boolean useRay;
        private final double projX,projY;

        public Drag(int axis,PreviewCameraPose camera,
                double x,double y,double z,
                int left,int top,int width,int height,
                double mouseX,double mouseY)
        {
            this.axis=axis;this.camera=camera;
            this.origin=new double[]{x,y,z};
            this.left=left;this.top=top;this.width=width;this.height=height;
            this.startX=mouseX;this.startY=mouseY;
            Handle projected=project(camera,x,y,z,left,top,width,height);
            double[] tip=projected==null?null:projected.tips[axis];
            this.projX=tip==null?0D:
                    (tip[0]-projected.x)/projected.axisWorldLength;
            this.projY=tip==null?0D:
                    (tip[1]-projected.y)/projected.axisWorldLength;
            double initial=closestParameter(mouseX,mouseY);
            this.useRay=Double.isFinite(initial) &&
                    projX*projX+projY*projY>=4D;
            this.startClosest=initial;
        }

        /**
         * Closest points between the static world axis and camera mouse ray.
         * Unlike a fixed .025-per-pixel drag, this respects zoom, roll,
         * perspective and any arbitrary CAMERA shot.
         */
        private double closestParameter(double mx,double my)
        {
            double[] v=PreviewCameraProjection.ray(camera,mx,my,
                    left,top,width,height);
            if(v==null)return Double.NaN;
            double[] u=new double[3];u[axis]=1D;
            double wx=origin[0]-camera.x,wy=origin[1]-camera.y,
                    wz=origin[2]-camera.z;
            double b=v[axis];
            double den=1D-b*b;
            if(den<0.035D)return Double.NaN;
            double d=axis==0?wx:axis==1?wy:wz;
            double e=v[0]*wx+v[1]*wy+v[2]*wz;
            return (b*e-d)/den;
        }

        public double delta(double mx,double my)
        {
            if(useRay)
            {
                double closest=closestParameter(mx,my);
                if(Double.isFinite(closest))
                    return clamp(closest-startClosest);
            }
            double mag=projX*projX+projY*projY;
            if(mag<1E-5D)return 0D;
            return clamp(((mx-startX)*projX+(my-startY)*projY)/mag);
        }

        private static double clamp(double n)
        {
            return Math.max(-512D,Math.min(512D,n));
        }
    }
}

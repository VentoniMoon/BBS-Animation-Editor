package com.example.examplemod;

/**
 * Camera matrices for Preview 3.0, OpenGL column-major order.
 * They are calculated from a frozen PreviewCameraPose and have no OpenGL,
 * Minecraft, OpenAL or OptiFine side effects.
 *
 * Composition is exactly P * Rz(-roll) * Rx(-pitch) * Ry(-yaw) * T(-eye).
 * Can be consumed by game renderer, gizmo picking and a future Aperture
 * camera-track adapter without changing the camera source.
 */
public final class PreviewCameraMatrices
{
    private final double[] view;
    private final double[] projection;
    private final double[] viewProjection;

    public PreviewCameraMatrices(PreviewCameraPose camera,int width,int height)
    {
        if(camera==null || width<=0 || height<=0)
            throw new IllegalArgumentException("Invalid Preview camera dimensions");
        double aspect=(double)width/height;
        this.view=mul(mul(mul(rotationZ(-camera.roll),
                rotationX(-camera.pitch)),rotationY(-camera.yaw)),
                translate(-camera.x,-camera.y,-camera.z));
        double f=1D/Math.tan(Math.toRadians(camera.fov)*0.5D);
        double near=camera.nearPlane,far=camera.farPlane;
        this.projection=new double[16];
        projection[0]=f/aspect;
        projection[5]=f;
        projection[10]=-(far+near)/(far-near);
        projection[11]=-1D;
        projection[14]=-2D*near*far/(far-near);
        this.viewProjection=mul(projection,view);
    }

    public double[] view() { return view.clone(); }
    public double[] projection() { return projection.clone(); }
    public double[] viewProjection() { return viewProjection.clone(); }

    /** Convert world xyz to normalized device coordinates, or null behind near. */
    public double[] projectNdc(double x,double y,double z)
    {
        double[] m=viewProjection;
        double w=m[3]*x+m[7]*y+m[11]*z+m[15];
        if(!Double.isFinite(w)||w<=0D)return null;
        double nx=(m[0]*x+m[4]*y+m[8]*z+m[12])/w;
        double ny=(m[1]*x+m[5]*y+m[9]*z+m[13])/w;
        double nz=(m[2]*x+m[6]*y+m[10]*z+m[14])/w;
        if(!Double.isFinite(nx)||!Double.isFinite(ny)||!Double.isFinite(nz))
            return null;
        return new double[]{nx,ny,nz};
    }

    private static double[] identity()
    {
        return new double[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    private static double[] translate(double x,double y,double z)
    {
        double[] m=identity();
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }

    private static double[] rotationX(double degrees)
    {
        double c=Math.cos(Math.toRadians(degrees));
        double s=Math.sin(Math.toRadians(degrees));
        double[] m=identity();m[5]=c;m[6]=s;m[9]=-s;m[10]=c;
        return m;
    }
    private static double[] rotationY(double degrees)
    {
        double c=Math.cos(Math.toRadians(degrees));
        double s=Math.sin(Math.toRadians(degrees));
        double[] m=identity();m[0]=c;m[2]=-s;m[8]=s;m[10]=c;
        return m;
    }
    private static double[] rotationZ(double degrees)
    {
        double c=Math.cos(Math.toRadians(degrees));
        double s=Math.sin(Math.toRadians(degrees));
        double[] m=identity();m[0]=c;m[1]=s;m[4]=-s;m[5]=c;
        return m;
    }

    private static double[] mul(double[] a,double[] b)
    {
        double[] result=new double[16];
        for(int col=0;col<4;col++)
            for(int row=0;row<4;row++)
                for(int i=0;i<4;i++)
                    result[col*4+row]+=a[i*4+row]*b[col*4+i];
        return result;
    }
}

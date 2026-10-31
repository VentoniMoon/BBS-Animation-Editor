package com.example.examplemod;

/**
 * Pure Java 8 shadow-coordinate invariance test; no Minecraft/GL required.
 */
public final class CameraMirrorShadowMatrixInvariantTest
{
    private static double[] transform(float[] m,double[] p)
    {
        double[] out=new double[4];
        for(int i=0;i<4;i++)
            out[i]=m[i]*p[0]+m[4+i]*p[1]+
                    m[8+i]*p[2]+m[12+i]*p[3];
        return out;
    }
    private static void verify(float[] shadow,
            double[] main,double[] mirror,double[] world)
    {
        float[] shifted=CameraMirrorShadowMatrix.rebase(shadow,
                mirror[0]-main[0],mirror[1]-main[1],
                mirror[2]-main[2]);
        double[] a=transform(shadow,new double[]{
                world[0]-main[0],world[1]-main[1],
                world[2]-main[2],1});
        double[] b=transform(shifted,new double[]{
                world[0]-mirror[0],world[1]-mirror[1],
                world[2]-mirror[2],1});
        for(int i=0;i<4;i++)
            if(Math.abs(a[i]-b[i])>0.0008)
                throw new AssertionError("Shadow drifts on coordinate "+i+
                        ": main="+a[i]+" mirror="+b[i]);
    }
    /**
     * The private shadow must use reflected-camera snapping instead of
     * retaining OptiFine's main-camera remainder translation.
     */
    private static void verifyReflectedSnap()
    {
        final float step=2F;
        float[] rotate={
                0.6F,0.8F,0F,0F,
                -0.8F,0.6F,0F,0F,
                0F,0F,1F,0F,
                0F,0F,-100F,1F
        };
        double mx=12.3,my=64.2,mz=-8.2;
        float[] nativeView=CameraMirrorShadowMatrix.rebase(rotate,
                (float)mx%step-step/2,
                (float)my%step-step/2,
                (float)mz%step-step/2);
        double rx=-7.4,ry=65.6,rz=14.3;
        float[] target=CameraMirrorShadowMatrix.rebase(rotate,
                (float)rx%step-step/2,
                (float)ry%step-step/2,
                (float)rz%step-step/2);
        float[] fixed=CameraMirrorShadowMatrix.retargetOrthographicSnap(
                nativeView,mx,my,mz,rx,ry,rz,step);
        for(int i=0;i<16;i++)
            if(Math.abs(fixed[i]-target[i])>0.0001)
                throw new AssertionError("Reflected snap differs at "+i+
                        ": "+fixed[i]+" vs "+target[i]);

        // Two sub-interval camera movements may alter relative object
        // coordinates but MUST NOT move their shadow coordinate in world.
        double[] point={5.2,70.4,23.0};
        float[] other=CameraMirrorShadowMatrix.retargetOrthographicSnap(
                nativeView,mx,my,mz,-7.2,65.7,14.6,step);
        double[] a=transform(fixed,new double[]{
                point[0]-rx,point[1]-ry,point[2]-rz,1});
        double[] b=transform(other,new double[]{
                point[0]+7.2,point[1]-65.7,point[2]-14.6,1});
        for(int i=0;i<4;i++)
            if(Math.abs(a[i]-b[i])>0.0001)
                throw new AssertionError("Private shadow drifts with eye on "+i+
                        ": "+a[i]+" != "+b[i]);
    }

    private static float[] nativeWithMainSnap(float[] rotation,
            double[] main,float interval)
    {
        return CameraMirrorShadowMatrix.rebase(rotation,
                (float)main[0]%interval-interval/2,
                (float)main[1]%interval-interval/2,
                (float)main[2]%interval-interval/2);
    }

    /**
     * Moving either the real spectator or reflected eye must not move
     * the shadow of a world block when the mirror itself is stationary.
     */
    private static void verifyFixedWorldAnchor()
    {
        float[] rotation={
                0.6F,0.8F,0F,0F,
                -0.8F,0.6F,0F,0F,
                0F,0F,1F,0F,
                0F,0F,-100F,1F
        };
        float interval=2F;
        double[] anchor={18.25,65.5,-12.75};
        double[] point={7.25,67.0,-3.50};
        double[][] mains={
                {12.3,64.2,-8.2},{20.8,67.7,-13.2},
                {-10.7,72.2,5.5}
        };
        double[][] reflected={
                {-7.4,65.6,14.3},{-3.2,65.7,12.6},
                {12.2,66.3,-22.8}
        };
        double[] expected=null;
        for(int i=0;i<mains.length;i++)
        {
            double[] m=mains[i],e=reflected[i];
            float[] nativeView=nativeWithMainSnap(rotation,m,interval);
            float[] anchored=CameraMirrorShadowMatrix.fixedWorldAnchor(
                    nativeView,m[0],m[1],m[2],e[0],e[1],e[2],
                    anchor[0],anchor[1],anchor[2],interval);
            double[] actual=transform(anchored,new double[]{
                    point[0]-e[0],point[1]-e[1],
                    point[2]-e[2],1});
            if(expected==null)expected=actual;
            else for(int k=0;k<4;k++)
                if(Math.abs(actual[k]-expected[k])>0.0001)
                    throw new AssertionError("Fixed mirror shadow drifts "+
                            "with camera at iteration "+i+" coordinate "+
                            k+": "+actual[k]+" vs "+expected[k]);
        }
    }

    public static void main(String[] args)
    {
        float[] shadow={
                0.64f,0.36f,0.68f,0f,
                -0.76f,0.48f,0.43f,0f,
                0f,-0.8f,0.6f,0f,
                5.75f,-24.25f,-100f,1f
        };
        float[] before=shadow.clone();
        double[] point={17.0,64.0,-12.0};
        verify(shadow,new double[]{12,65,-8},
                new double[]{18,65,-8},point);
        verify(shadow,new double[]{12,65,-8},
                new double[]{-7,68,24},point);
        verify(shadow,new double[]{12,65,-8},
                new double[]{12,65,-8},point);
        verifyReflectedSnap();
        verifyFixedWorldAnchor();
        for(int i=0;i<16;i++)
            if(shadow[i]!=before[i])
                throw new AssertionError("Native matrix was modified");
        boolean rejected=false;
        try{CameraMirrorShadowMatrix.rebase(shadow,Double.NaN,0,0);}
        catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("NaN offset accepted");
        System.out.println("PASS: reflected shadow coordinates anchored in world space");
    }
}

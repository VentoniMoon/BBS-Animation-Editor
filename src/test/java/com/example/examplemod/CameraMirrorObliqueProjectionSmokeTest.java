package com.example.examplemod;

/**
 * Standalone Java 8 clip-space contract test. Demonstrates why the
 * programmable-shader mirror must reject the wall BEHIND the plane,
 * while preserving reflected-world geometry in FRONT of it.
 */
public final class CameraMirrorObliqueProjectionSmokeTest
{
    private static float[] perspective(double fovDegrees,double aspect,
            double near,double far)
    {
        double f=1D/Math.tan(Math.toRadians(fovDegrees)*0.5D);
        float[] p=new float[16];
        p[0]=(float)(f/aspect);
        p[5]=(float)f;
        p[10]=(float)(-(far+near)/(far-near));
        p[11]=-1F;
        p[14]=(float)(-2D*far*near/(far-near));
        return p;
    }
    private static void check(boolean value,String why)
    {
        if(!value)throw new AssertionError(why);
    }
    public static void main(String[] args)
    {
        float[] p=perspective(70D,16D/9D,0.05D,300D);
        float[] original=p.clone();
        // Mirror at eyeZ=-2, front side faces toward -eyeZ.
        // Stone behind the mirror is eyeZ>-2; distant reflected
        // landscape is eyeZ<-2.
        double[] plane={0D,0D,-1D,-2D};
        float[] clipped=CameraMirrorObliqueProjection.clip(p,plane);
        check(CameraMirrorObliqueProjection.nearDistance(clipped,
                0D,0D,-1D)<-0.01D,"stone behind mirror not clipped");
        check(Math.abs(CameraMirrorObliqueProjection.nearDistance(clipped,
                0D,0D,-2D))<0.005D,"mirror plane not at near boundary");
        check(CameraMirrorObliqueProjection.nearDistance(clipped,
                0D,0D,-5D)>0.01D,
                "reflected landscape incorrectly clipped");
        check(p[10]==original[10]&&p[14]==original[14],
                "caller projection mutated");

        // Tilted plane must still clip by the SAME plane half-space,
        // including when actors lie to the side of the reflection.
        double[] oblique={0.27D,-0.14D,-1D,-1.4D};
        float[] tilted=CameraMirrorObliqueProjection.clip(
                perspective(60D,1.6D,0.05D,256D),oblique);
        double[] points={0D,0D,-1D,2D,1D,-3D,-2D,-1D,-2D};
        for(int i=0;i<points.length;i+=3)
        {
            double x=points[i],y=points[i+1],z=points[i+2];
            double expected=oblique[0]*x+oblique[1]*y+
                    oblique[2]*z+oblique[3];
            double actual=CameraMirrorObliqueProjection.nearDistance(
                    tilted,x,y,z);
            check((expected>=0D)==(actual>=0D),
                    "tilted mirror clip sign mismatch");
        }
        boolean rejected=false;
        try
        {
            CameraMirrorObliqueProjection.clip(p,
                    new double[]{0D,0D,-1D,2D});
        }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reflection camera on wrong side accepted");
        System.out.println(
                "Mirror GLSL-safe oblique near-plane clipping: PASS");
    }
}

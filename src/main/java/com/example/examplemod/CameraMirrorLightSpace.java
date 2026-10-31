package com.example.examplemod;

/**
 * Math-only part of the BetterLights mirror uniform correction.
 *
 * BetterLights 1.0.3 renders world LightCaster positions relative to the
 * current player/camera, optionally rotating that vector into eye space.
 * The main frame has already uploaded these camera-relative values by
 * the time our auxiliary mirror starts. We must not merely move
 * Minecraft.player: existing GL uniforms are still from the MAIN view.
 *
 * This helper matches the old data against actual registered world sources
 * before ever replacing a GLSL value. A world-space uniform is unchanged.
 */
public final class CameraMirrorLightSpace
{
    public static final int UNKNOWN=0, RELATIVE=1, EYE=2, WORLD=3;

    private CameraMirrorLightSpace(){}

    public static float[] transform(float[] matrix,double x,double y,double z)
    {
        if(matrix==null||matrix.length<16)return null;
        return new float[]{
            (float)(matrix[0]*x+matrix[4]*y+matrix[8]*z+matrix[12]),
            (float)(matrix[1]*x+matrix[5]*y+matrix[9]*z+matrix[13]),
            (float)(matrix[2]*x+matrix[6]*y+matrix[10]*z+matrix[14])
        };
    }

    public static float[] relative(double[] source,double[] camera)
    {
        return new float[]{
            (float)(source[0]-camera[0]),
            (float)(source[1]-camera[1]),
            (float)(source[2]-camera[2])
        };
    }

    public static float distanceSquared(float[] a,float[] b)
    {
        if(a==null||b==null)return Float.POSITIVE_INFINITY;
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return x*x+y*y+z*z;
    }

    public static int match(float[] observed,double[] source,
            double[] main,float[] mainMatrix)
    {
        // Require a numerical match to the existing frame, not an
        // arbitrary uniform name or a guessed light-camera offset.
        final float epsilon=0.02F*0.02F;
        float[] rel=relative(source,main);
        if(distanceSquared(observed,rel)<epsilon)return RELATIVE;
        float[] eye=transform(mainMatrix,rel[0],rel[1],rel[2]);
        if(distanceSquared(observed,eye)<epsilon)return EYE;
        float[] world=new float[]{
                (float)source[0],(float)source[1],(float)source[2]};
        if(distanceSquared(observed,world)<epsilon)return WORLD;
        return UNKNOWN;
    }

    public static float[] reflected(int representation,double[] source,
            double[] mirror,float[] mirrorMatrix)
    {
        float[] rel=relative(source,mirror);
        if(representation==RELATIVE)return rel;
        if(representation==EYE)
            return transform(mirrorMatrix,rel[0],rel[1],rel[2]);
        if(representation==WORLD)
            return new float[]{
                (float)source[0],(float)source[1],(float)source[2]};
        return null;
    }
}

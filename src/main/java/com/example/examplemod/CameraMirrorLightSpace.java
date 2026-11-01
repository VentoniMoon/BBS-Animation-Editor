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

    public static final int SCREEN_UNKNOWN=0;
    public static final int SCREEN_CLIP=1;
    public static final int SCREEN_NDC=2;
    public static final int SCREEN_UV=3;
    public static final int SCREEN_VIEW=4;

    private static float[] multiply4(float[] m,float[] v)
    {
        if(m==null||m.length<16||v==null||v.length<4)
            return null;
        float[] out=new float[4];
        for(int row=0;row<4;row++)
            out[row]=m[row]*v[0]+m[4+row]*v[1]+
                    m[8+row]*v[2]+m[12+row]*v[3];
        return out;
    }

    public static float[] screen(float[] projection,float[] view,
            double[] source,double[] camera,int kind)
    {
        if(projection==null||view==null||source==null||camera==null)
            return null;
        float[] position={
            (float)(source[0]-camera[0]),
            (float)(source[1]-camera[1]),
            (float)(source[2]-camera[2]),1F
        };
        float[] eye=multiply4(view,position);
        if(kind==SCREEN_VIEW)return eye;
        float[] clip=multiply4(projection,eye);
        if(clip==null)return null;
        if(kind==SCREEN_CLIP)return clip;
        if(Math.abs(clip[3])<1E-6F)return null;
        float[] ndc={
            clip[0]/clip[3],clip[1]/clip[3],
            clip[2]/clip[3],1F
        };
        if(kind==SCREEN_NDC)return ndc;
        if(kind==SCREEN_UV)return new float[]{
            ndc[0]*0.5F+0.5F,ndc[1]*0.5F+0.5F,
            ndc[2]*0.5F+0.5F,1F
        };
        return null;
    }

    /**
     * Do not guess BetterLights' screen-space encoding. Identify an
     * encoding only when the currently loaded GPU value matches a
     * registered world caster projected through the ACTUAL native
     * camera and matrices. Unknown values are never changed.
     */
    public static int matchScreen(float[] observed,
            float[] projection,float[] view,
            double[] source,double[] mainCamera)
    {
        if(observed==null||observed.length<4)return SCREEN_UNKNOWN;
        for(int kind=SCREEN_CLIP;kind<=SCREEN_VIEW;kind++)
        {
            float[] expected=screen(projection,view,
                    source,mainCamera,kind);
            if(expected==null)continue;
            float error=0F;
            for(int i=0;i<4;i++)
            {
                float allowed=0.02F*Math.max(1F,
                        Math.abs(expected[i]));
                float difference=Math.abs(observed[i]-expected[i]);
                if(!Float.isFinite(difference)||difference>allowed)
                {
                    error=Float.POSITIVE_INFINITY;
                    break;
                }
                error+=difference;
            }
            if(Float.isFinite(error))return kind;
        }
        return SCREEN_UNKNOWN;
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

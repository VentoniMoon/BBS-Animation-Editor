package com.example.examplemod;

/**
 * Read-only reference-cuboid geometry in the SAME OpenGL column-major,
 * parent-attached world coordinates as the post-render Chameleon bone pivots.
 *
 * This is intentionally pure Java 8 and independent of Minecraft or JNI.
 * It describes a single unrotated Blockbench reference cube and does NOT
 * pretend to capture the final GL triangle buffer or Chameleon cube-local
 * rotations. Never use these measurements to modify animation keys.
 */
public final class BodyPartJoltCubeGeometry
{
    private BodyPartJoltCubeGeometry() {}

    public static final class Measurement
    {
        /** Direction from the cube's upper (+Y) face to its lower (-Y) face. */
        public final float[] cubeDown;
        /** Direction from bone pivot to the cuboid center. */
        public final float[] pivotToCenter;
        /** Direction from bone pivot to lower cube face center. */
        public final float[] pivotToTip;
        public final float[] cubeCenterWorld;
        public final float[] cubeTipWorld;
        public final float minWorldY;
        public final float maxWorldY;
        public final float pivotToTipWorldY;

        private Measurement(float[] down, float[] centerDirection,
                float[] tipDirection, float[] center, float[] tip,
                float minY, float maxY, float verticalTip)
        {
            this.cubeDown=down;
            this.pivotToCenter=centerDirection;
            this.pivotToTip=tipDirection;
            this.cubeCenterWorld=center;
            this.cubeTipWorld=tip;
            this.minWorldY=minY;
            this.maxWorldY=maxY;
            this.pivotToTipWorldY=verticalTip;
        }
    }

    /**
     * Model units are 16 pixels per block; matrix is the native bone pivot
     * pose already multiplied by the animated Emoticons/Metamorph attachment.
     * The reference cube must have no independent cube-local rotation.
     */
    public static Measurement measure(float[] worldBone,
            float[] pivotPx, float[] originPx, float[] sizePx, float inflatePx)
    {
        check(worldBone,16,"native bone 4x4");
        check(pivotPx,3,"pivot");
        check(originPx,3,"origin");
        check(sizePx,3,"dimensions");
        if (!Float.isFinite(inflatePx) || inflatePx < 0F)
            throw new IllegalArgumentException("Invalid inflate");
        for (float size : sizePx)
            if (size < 0F)
                throw new IllegalArgumentException("Negative cube size");
        if (!(sizePx[1] > 0.0001F))
            throw new IllegalArgumentException("Cube has no long Y axis");

        float cx=originPx[0]+0.5F*sizePx[0];
        float cy=originPx[1]+0.5F*sizePx[1];
        float cz=originPx[2]+0.5F*sizePx[2];
        float[] bonePivot={worldBone[12],worldBone[13],worldBone[14]};
        float[] center=worldPoint(worldBone,pivotPx,cx,cy,cz);
        float[] tip=worldPoint(worldBone,pivotPx,
                cx,originPx[1]-inflatePx,cz);
        float[] top=worldPoint(worldBone,pivotPx,
                cx,originPx[1]+sizePx[1]+inflatePx,cz);

        float minY=Float.POSITIVE_INFINITY, maxY=Float.NEGATIVE_INFINITY;
        for (int i=0;i<8;i++)
        {
            float x=originPx[0]+(((i&1)==0)?-inflatePx:sizePx[0]+inflatePx);
            float y=originPx[1]+(((i&2)==0)?-inflatePx:sizePx[1]+inflatePx);
            float z=originPx[2]+(((i&4)==0)?-inflatePx:sizePx[2]+inflatePx);
            float[] corner=worldPoint(worldBone,pivotPx,x,y,z);
            minY=Math.min(minY,corner[1]);
            maxY=Math.max(maxY,corner[1]);
        }
        return new Measurement(
                unit(sub(tip,top)),
                unit(sub(center,bonePivot)),
                unit(sub(tip,bonePivot)),
                center,tip,minY,maxY,tip[1]-bonePivot[1]);
    }

    public static float alignmentDegrees(float[] a, float[] b)
    {
        float[] p=unit(a),q=unit(b);
        double dot=(double)p[0]*q[0]+(double)p[1]*q[1]+(double)p[2]*q[2];
        return (float)Math.toDegrees(Math.acos(Math.max(-1D,Math.min(1D,dot))));
    }

    private static float[] worldPoint(float[] m,float[] pivot,
            float x,float y,float z)
    {
        float a=(x-pivot[0])/16F, b=(y-pivot[1])/16F, c=(z-pivot[2])/16F;
        return new float[] {
                m[0]*a+m[4]*b+m[8]*c+m[12],
                m[1]*a+m[5]*b+m[9]*c+m[13],
                m[2]*a+m[6]*b+m[10]*c+m[14]
        };
    }

    private static float[] sub(float[] a,float[] b)
    {
        return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};
    }

    private static float[] unit(float[] v)
    {
        double length=Math.sqrt((double)v[0]*v[0]+(double)v[1]*v[1]+
                (double)v[2]*v[2]);
        if (!(length>1E-8D) || !Double.isFinite(length))
            throw new IllegalArgumentException("Degenerate cube direction");
        return new float[] {(float)(v[0]/length),
                (float)(v[1]/length),(float)(v[2]/length)};
    }

    private static void check(float[] v,int length,String label)
    {
        if (v==null || v.length!=length)
            throw new IllegalArgumentException("Expected "+label);
        for (float x:v)
            if (!Float.isFinite(x))
                throw new IllegalArgumentException("Non-finite "+label);
    }
}

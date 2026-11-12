package com.example.examplemod;

/**
 * Read-only seam planning for two neighboring Chameleon skirt cubes.
 *
 * Each candidate is a vertical upper/lower edge of the actual decoded cube,
 * transformed with the complete authored cube pivot/rotation and posed bone
 * transform. In a real skirt ring, paired edges are close in REST space,
 * but can pull apart when their Jolt strands animate independently.
 *
 * The target is a shared world-space line with bounded correction on each
 * mesh vertex. No keyframe, native Jolt body, or authored cube is modified.
 */
public final class SkirtRingSeamGeometry
{
    public static final float MAX_REST_GAP=.115F;
    public static final float MAX_VERTEX_MOVE=.11F;
    public static final float MAX_POSED_GAP=.50F;

    public static final class Edge
    {
        public final float[][] points;
        public Edge(float[] top,float[] bottom)
        {
            if(!validPoint(top)||!validPoint(bottom))
                throw new IllegalArgumentException("Invalid skirt edge");
            points=new float[][] {top.clone(),bottom.clone()};
        }
    }

    public static final class Plan
    {
        public final float[][] sourceLocal,targetLocal;
        public final boolean reverse;
        public final float restGap;
        public final float remainingGap;
        Plan(float[][] a,float[][] b,boolean reverse,
                float restGap,float remainingGap)
        {
            this.sourceLocal=a;
            this.targetLocal=b;
            this.reverse=reverse;
            this.restGap=restGap;
            this.remainingGap=remainingGap;
        }
    }

    private SkirtRingSeamGeometry(){}

    public static Plan plan(float[] aRest,float[] bRest,
            float[] aPosed,float[] bPosed,Edge a,Edge b)
    {
        if(!validMatrix(aRest)||!validMatrix(bRest)||
                !validMatrix(aPosed)||!validMatrix(bPosed)||
                a==null||b==null)return null;
        float[][] ar=new float[2][3],br=new float[2][3];
        float[][] ap=new float[2][3],bp=new float[2][3];
        for(int k=0;k<2;k++)
        {
            ar[k]=ChameleonWeldGeometry.transform(aRest,a.points[k]);
            br[k]=ChameleonWeldGeometry.transform(bRest,b.points[k]);
            ap[k]=ChameleonWeldGeometry.transform(aPosed,a.points[k]);
            bp[k]=ChameleonWeldGeometry.transform(bPosed,b.points[k]);
        }
        float normal=dist(ar[0],br[0])+dist(ar[1],br[1]);
        float flipped=dist(ar[0],br[1])+dist(ar[1],br[0]);
        boolean reverse=flipped<normal;
        float restGap=Math.max(dist(ar[0],br[reverse?1:0]),
                dist(ar[1],br[reverse?0:1]));
        if(restGap>MAX_REST_GAP)return null;
        float[][] aw=new float[2][3],bw=new float[2][3];
        float remaining=0F;
        for(int k=0;k<2;k++)
        {
            int other=reverse?1-k:k;
            float original=dist(ap[k],bp[other]);
            // Very distant parts should not generate long pulled triangles.
            if(original>MAX_POSED_GAP)return null;
            float[] mid=average(ap[k],bp[other]);
            aw[k]=clampTo(ap[k],mid,MAX_VERTEX_MOVE);
            bw[other]=clampTo(bp[other],mid,MAX_VERTEX_MOVE);
            remaining=Math.max(remaining,dist(aw[k],bw[other]));
        }
        float[] inverseA=invert(aPosed),inverseB=invert(bPosed);
        if(inverseA==null||inverseB==null)return null;
        float[][] localA=new float[2][3],localB=new float[2][3];
        for(int k=0;k<2;k++)
        {
            localA[k]=ChameleonWeldGeometry.transform(inverseA,aw[k]);
            localB[k]=ChameleonWeldGeometry.transform(inverseB,bw[k]);
        }
        return new Plan(localA,localB,reverse,restGap,remaining);
    }

    private static float[] clampTo(float[] original,float[] desired,float limit)
    {
        float gap=dist(original,desired);
        if(gap<=limit)return desired;
        float t=limit/gap;
        return new float[]{
                original[0]+(desired[0]-original[0])*t,
                original[1]+(desired[1]-original[1])*t,
                original[2]+(desired[2]-original[2])*t
        };
    }
    private static float[] average(float[] a,float[] b)
    {
        return new float[]{(a[0]+b[0])*.5F,
                (a[1]+b[1])*.5F,(a[2]+b[2])*.5F};
    }
    private static float dist(float[] a,float[] b)
    {
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
    private static boolean validPoint(float[] v)
    {
        if(v==null||v.length!=3)return false;
        for(float x:v)if(!Float.isFinite(x))return false;
        return true;
    }
    private static boolean validMatrix(float[] m)
    {
        if(m==null||m.length!=16)return false;
        for(float x:m)if(!Float.isFinite(x))return false;
        return true;
    }
    /** Invert column-major affine bone*cube transform, including scale. */
    private static float[] invert(float[] m)
    {
        float a=m[0],b=m[4],c=m[8],
                d=m[1],e=m[5],f=m[9],
                g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=c*h-b*i,C=b*f-c*e;
        float D=f*g-d*i,E=a*i-c*g,F=c*d-a*f;
        float G=d*h-e*g,H=b*g-a*h,I=a*e-b*d;
        float det=a*A+b*D+c*G;
        if(!Float.isFinite(det)||Math.abs(det)<1E-8F)return null;
        float v=1F/det;
        float[] out={
                A*v,D*v,G*v,0F,
                B*v,E*v,H*v,0F,
                C*v,F*v,I*v,0F,
                0F,0F,0F,1F
        };
        float[] shift=ChameleonWeldGeometry.transform(out,
                new float[]{-m[12],-m[13],-m[14]});
        out[12]=shift[0];out[13]=shift[1];out[14]=shift[2];
        return out;
    }
}

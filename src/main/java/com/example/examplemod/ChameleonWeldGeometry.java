package com.example.examplemod;

/**
 * Render-only seam solver for two Chameleon cube faces on different bones.
 *
 * Rest face corners are matched by their positions in MODEL space; each
 * animated face is then welded to the same world-space four-corner seam.
 * Resulting positions are returned in the original bone-local coordinates.
 * This class does not change source cubes, animation keys or physics.
 *
 * Matrices are OpenGL-style column-major affine 4x4 (as used by Chameleon
 * MatrixStack). UV coordinates and textures are deliberately untouched.
 *
 * First-pass seam deformation; curved falloff / normal sharing are separate
 * renderer features, not part of the rigid pose or the physics solver.
 */
public final class ChameleonWeldGeometry
{
    public static final int CORNERS=4;
    private static final float MIN_DET=1E-8F;

    private ChameleonWeldGeometry() {}

    public static final class Seam
    {
        /** Four positions in the SOURCE bone's own cube coordinates. */
        public final float[][] sourceLocal;
        /** Four positions in the TARGET bone's own cube coordinates. */
        public final float[][] targetLocal;
        /** Maps source corner index to target corner index. */
        public final int[] targetOrder;
        public final float[][] sourceWorld;
        public final float[][] targetWorld;

        private Seam(float[][] a,float[][] b,int[] indices,
                float[][] worldA,float[][] worldB)
        {
            this.sourceLocal=a;
            this.targetLocal=b;
            this.targetOrder=indices;
            this.sourceWorld=worldA;
            this.targetWorld=worldB;
        }
    }

    /**
     * @param sourceRest parent bone/cube transform at authored rest pose
     * @param targetRest child bone/cube transform at authored rest pose
     * @param sourceCurrent parent bone/cube transform at the current frame
     * @param targetCurrent child bone/cube transform at the current frame
     * @param sourceFace four original face corners (bone/cube local)
     * @param targetFace four original face corners (bone/cube local)
     * @param parentShare portion of seam correction assigned to the parent
     * @param maxRestGap maximum corner mismatch in blocks, at rest
     * @return matched, synchronized seam or null when rest topology differs
     */
    public static Seam weld(float[] sourceRest,float[] targetRest,
            float[] sourceCurrent,float[] targetCurrent,
            float[][] sourceFace,float[][] targetFace,
            float parentShare,float maxRestGap)
    {
        if(!validMatrix(sourceRest)||!validMatrix(targetRest)||
                !validMatrix(sourceCurrent)||!validMatrix(targetCurrent)||
                !validFace(sourceFace)||!validFace(targetFace)||
                !Float.isFinite(parentShare)||parentShare<0F||parentShare>1F||
                !Float.isFinite(maxRestGap)||maxRestGap<0F)
            throw new IllegalArgumentException("Invalid Chameleon Weld input");

        float[][] restA=transformFace(sourceRest,sourceFace);
        float[][] restB=transformFace(targetRest,targetFace);
        int[] matching=matchCorners(restA,restB,maxRestGap);
        if(matching==null)return null;

        float[][] posedA=transformFace(sourceCurrent,sourceFace);
        float[][] posedB=transformFace(targetCurrent,targetFace);
        float[] inverseA=inverseAffine(sourceCurrent);
        float[] inverseB=inverseAffine(targetCurrent);
        float[][] localA=new float[CORNERS][3];
        float[][] localB=new float[CORNERS][3];
        float[][] worldA=new float[CORNERS][3];
        float[][] worldB=new float[CORNERS][3];

        for(int i=0;i<CORNERS;i++)
        {
            int j=matching[i];
            float[] seam=new float[3];
            for(int axis=0;axis<3;axis++)
                seam[axis]=posedA[i][axis]*(1F-parentShare)+
                        posedB[j][axis]*parentShare;
            localA[i]=transform(inverseA,seam);
            localB[j]=transform(inverseB,seam);
            worldA[i]=seam.clone();
            worldB[j]=seam.clone();
        }
        return new Seam(localA,localB,matching,worldA,worldB);
    }

    private static int[] matchCorners(float[][] a,float[][] b,float limit)
    {
        int[] best=null;
        float bestSum=Float.POSITIVE_INFINITY;
        float maxSq=limit*limit+1E-9F;
        for(int p=0;p<4;p++)for(int q=0;q<4;q++)
        for(int r=0;r<4;r++)for(int t=0;t<4;t++)
        {
            if(p==q||p==r||p==t||q==r||q==t||r==t)continue;
            int[] order={p,q,r,t};
            float sum=0F;
            boolean valid=true;
            for(int k=0;k<4;k++)
            {
                float d=squareDistance(a[k],b[order[k]]);
                if(d>maxSq){valid=false;break;}
                sum+=d;
            }
            if(valid && sum<bestSum){best=order;bestSum=sum;}
        }
        return best;
    }

    private static float squareDistance(float[] a,float[] b)
    {
        float dx=a[0]-b[0],dy=a[1]-b[1],dz=a[2]-b[2];
        return dx*dx+dy*dy+dz*dz;
    }

    private static float[][] transformFace(float[] m,float[][] vertices)
    {
        float[][] points=new float[CORNERS][3];
        for(int i=0;i<CORNERS;i++)points[i]=transform(m,vertices[i]);
        return points;
    }

    /** A model-space point transformed through a column-major affine 4x4. */
    public static float[] transform(float[] m,float[] point)
    {
        float x=point[0],y=point[1],z=point[2];
        return new float[] {
            m[0]*x+m[4]*y+m[8]*z+m[12],
            m[1]*x+m[5]*y+m[9]*z+m[13],
            m[2]*x+m[6]*y+m[10]*z+m[14]
        };
    }

    /** Complete 3x3 inverse including non-uniform scale, plus translation. */
    private static float[] inverseAffine(float[] m)
    {
        float a=m[0], b=m[4], c=m[8],
              d=m[1], e=m[5], f=m[9],
              g=m[2], h=m[6], i=m[10];
        float A=e*i-f*h, B=c*h-b*i, C=b*f-c*e;
        float D=f*g-d*i, E=a*i-c*g, F=c*d-a*f;
        float G=d*h-e*g, H=b*g-a*h, I=a*e-b*d;
        float det=a*A+b*D+c*G;
        if(!Float.isFinite(det)||Math.abs(det)<MIN_DET)
            throw new IllegalArgumentException("Singular Chameleon bone matrix");
        float inv=1F/det;
        float[] result={
            A*inv,D*inv,G*inv,0F,
            B*inv,E*inv,H*inv,0F,
            C*inv,F*inv,I*inv,0F,
            0F,0F,0F,1F
        };
        float[] shift=transform(result,new float[] {-m[12],-m[13],-m[14]});
        result[12]=shift[0];
        result[13]=shift[1];
        result[14]=shift[2];
        return result;
    }

    private static boolean validMatrix(float[] m)
    {
        if(m==null||m.length!=16)return false;
        for(float f:m)if(!Float.isFinite(f))return false;
        return Math.abs(m[3])<1E-5F && Math.abs(m[7])<1E-5F &&
                Math.abs(m[11])<1E-5F && Math.abs(m[15]-1F)<1E-5F;
    }

    private static boolean validFace(float[][] p)
    {
        if(p==null||p.length!=CORNERS)return false;
        for(float[] point:p)
        {
            if(point==null||point.length!=3)return false;
            for(float v:point)if(!Float.isFinite(v))return false;
        }
        return true;
    }
}

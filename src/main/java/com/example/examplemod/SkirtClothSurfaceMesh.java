package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 1 of the replacement skirt cloth pipeline.
 *
 * Capture only the OUTWARD-FACING, TEXTURED surface of each of the twenty
 * 2-cube Chameleon strips. All positions/UVs come from the decoded native
 * ModelCube.quads, including authored cube pivots, -180 degree side cubes
 * and the mirrored rear branch. This class never edits model vertices,
 * creates triangles with fabricated UVs, or advances any physics.
 *
 * The surface keeps UV SEAMS duplicated even when geometric neighbors
 * become shared XPBD particles in the next stage.
 */
public final class SkirtClothSurfaceMesh
{
    public static final int STRIP_COUNT=20;
    public static final int PATCH_COUNT=40;
    private static final float EPS=1E-6F;

    /** Chameleon ModelVertex.position and ModelVertex.uv snapshot. */
    public static final class Vertex
    {
        public final float[] position,uv;
        public Vertex(float[] position,float[] uv)
        {
            this.position=finite(position,3).clone();
            this.uv=finite(uv,2).clone();
        }
    }
    /** One decoded ModelQuad. Winding/UV are never regenerated. */
    public static final class Quad
    {
        public final Vertex[] vertices;
        public final float[] normal;
        public Quad(Vertex[] vertices,float[] normal)
        {
            if(vertices==null||vertices.length!=4)
                throw new IllegalArgumentException("Expected Chameleon quad");
            this.vertices=vertices.clone();
            for(Vertex v:vertices)
                if(v==null)throw new IllegalArgumentException("Missing vertex");
            this.normal=finite(normal,3).clone();
        }
    }
    /** Matrix includes both the full parent bone hierarchy and the cube
     * pivot/rotation, using Chameleon's column-major 4x4 convention. */
    public static final class Cube
    {
        public final String bone;
        public final float[] restMatrix;
        public final List<Quad> quads;
        public Cube(String bone,float[] restMatrix,List<Quad> quads)
        {
            if(bone==null||bone.isEmpty()||quads==null||quads.isEmpty())
                throw new IllegalArgumentException("Invalid decoded cube");
            this.bone=bone;
            this.restMatrix=finite(restMatrix,16).clone();
            this.quads=Collections.unmodifiableList(new ArrayList<Quad>(quads));
        }
    }
    public static final class Strip
    {
        public final Cube upper,lower;
        public Strip(Cube upper,Cube lower)
        {
            if(upper==null||lower==null||upper.bone.equals(lower.bone))
                throw new IllegalArgumentException("Invalid 2-cube strip");
            this.upper=upper;this.lower=lower;
        }
    }

    /** One original exterior quad with its four separate UV corners. */
    public static final class Patch
    {
        public final String bone;
        public final boolean lower;
        /** In unmodified ModelQuad winding: each corner has XYZ and UV. */
        public final float[][] world,uv;
        public final float[] center,normal;
        /** Indices in world/uv. Left/right use the ring's +angle tangent. */
        public final int topLeft,topRight,bottomLeft,bottomRight;
        public final int[] triangles={0,1,2,0,2,3};

        private Patch(String bone,boolean lower,float[][] world,
                float[][] uv,float[] center,float[] normal,
                int topLeft,int topRight,int bottomLeft,int bottomRight)
        {
            this.bone=bone;this.lower=lower;this.world=clone2(world);
            this.uv=clone2(uv);this.center=center.clone();
            this.normal=normal.clone();
            this.topLeft=topLeft;this.topRight=topRight;
            this.bottomLeft=bottomLeft;this.bottomRight=bottomRight;
        }
    }
    public static final class Seam
    {
        /** Parent bones and endpoints in the *source* vertex index order. */
        public final String a,b;
        public final int[] aEndpoints,bEndpoints;
        public final boolean horizontal;
        /** Largest endpoint-to-endpoint gap in the ORIGINAL static model. */
        public final float restGap;

        private Seam(String a,String b,int[] ae,int[] be,
                boolean horizontal,float gap)
        {
            this.a=a;this.b=b;
            this.aEndpoints=ae.clone();this.bEndpoints=be.clone();
            this.horizontal=horizontal;this.restGap=gap;
        }
    }
    public static final class Snapshot
    {
        /** Ordered as upper/lower per strip around circumference. */
        public final List<Patch> patches;
        /** 20 upper + 20 lower circumferential + 20 vertical connections. */
        public final List<Seam> seams;
        /** No tessellation: each decoded quad becomes exactly 2 triangles. */
        public final int triangleCount;
        public final float maxRestGap,meanRestGap,maxRoundTripError;
        public final float[] min,max;

        private Snapshot(List<Patch> patches,List<Seam> seams,
                float maxRestGap,float meanRestGap,float maxRoundTripError,
                float[] min,float[] max)
        {
            this.patches=Collections.unmodifiableList(patches);
            this.seams=Collections.unmodifiableList(seams);
            this.triangleCount=patches.size()*2;
            this.maxRestGap=maxRestGap;
            this.meanRestGap=meanRestGap;
            this.maxRoundTripError=maxRoundTripError;
            this.min=min.clone();this.max=max.clone();
        }
    }

    private static final class Pair
    {
        Patch upper,lower;
        double angle;
    }

    private SkirtClothSurfaceMesh(){}

    public static Snapshot capture(List<Strip> strips)
    {
        if(strips==null||strips.size()!=STRIP_COUNT)
            throw new IllegalArgumentException("Need 20 Chameleon strips");
        Set<String> names=new HashSet<String>();
        float[] center={0F,0F,0F};
        int cubeCount=0;
        for(Strip strip:strips)
        {
            if(strip==null)throw new IllegalArgumentException("Null strip");
            for(Cube cube:new Cube[]{strip.upper,strip.lower})
            {
                if(!names.add(cube.bone))
                    throw new IllegalArgumentException("Repeated bone "+cube.bone);
                float[] avg=cubeCenter(cube);
                center[0]+=avg[0];center[2]+=avg[2];
                cubeCount++;
            }
        }
        center[0]/=cubeCount;center[2]/=cubeCount;

        List<Pair> ordered=new ArrayList<Pair>();
        float parity=0F;
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY};
        for(Strip strip:strips)
        {
            Pair pair=new Pair();
            pair.upper=outside(strip.upper,false,center);
            pair.lower=outside(strip.lower,true,center);
            pair.angle=Math.atan2(pair.upper.center[2]-center[2],
                    pair.upper.center[0]-center[0]);
            ordered.add(pair);
            for(Cube cube:new Cube[]{strip.upper,strip.lower})
            {
                float[] inv=invert(cube.restMatrix);
                if(inv==null)
                    throw new IllegalArgumentException(
                            "Singular Chameleon cube rest matrix "+cube.bone);
                for(Quad quad:cube.quads)
                    for(Vertex v:quad.vertices)
                    {
                        float[] world=transform(cube.restMatrix,v.position);
                        float[] recovered=transform(inv,world);
                        parity=Math.max(parity,dist(recovered,v.position));
                    }
            }
            for(Patch p:new Patch[]{pair.upper,pair.lower})
                for(float[] v:p.world)
                    for(int axis=0;axis<3;axis++)
                    {
                        min[axis]=Math.min(min[axis],v[axis]);
                        max[axis]=Math.max(max[axis],v[axis]);
                    }
        }
        Collections.sort(ordered,new Comparator<Pair>()
        {
            @Override public int compare(Pair a,Pair b)
            {
                int cmp=Double.compare(a.angle,b.angle);
                return cmp!=0?cmp:a.upper.bone.compareTo(b.upper.bone);
            }
        });
        List<Patch> faces=new ArrayList<Patch>(PATCH_COUNT);
        List<Seam> seams=new ArrayList<Seam>(60);
        for(int i=0;i<STRIP_COUNT;i++)
        {
            Pair current=ordered.get(i);
            Pair next=ordered.get((i+1)%STRIP_COUNT);
            faces.add(current.upper);
            faces.add(current.lower);
            seams.add(seam(current.upper,next.upper,
                    new int[]{current.upper.topRight,
                            current.upper.bottomRight},
                    new int[]{next.upper.topLeft,
                            next.upper.bottomLeft},true));
            seams.add(seam(current.lower,next.lower,
                    new int[]{current.lower.topRight,
                            current.lower.bottomRight},
                    new int[]{next.lower.topLeft,
                            next.lower.bottomLeft},true));
            seams.add(seam(current.upper,current.lower,
                    new int[]{current.upper.bottomLeft,
                            current.upper.bottomRight},
                    new int[]{current.lower.topLeft,
                            current.lower.topRight},false));
        }
        float largest=0F,sum=0F;
        for(Seam seam:seams)
        {
            largest=Math.max(largest,seam.restGap);
            sum+=seam.restGap;
        }
        return new Snapshot(faces,seams,largest,sum/seams.size(),
                parity,min,max);
    }

    private static Seam seam(Patch a,Patch b,int[] ai,int[] bi,
            boolean around)
    {
        float gap=Math.max(
                dist(a.world[ai[0]],b.world[bi[0]]),
                dist(a.world[ai[1]],b.world[bi[1]]));
        return new Seam(a.bone,b.bone,ai,bi,around,gap);
    }

    private static Patch outside(Cube cube,boolean lower,float[] center)
    {
        Quad best=null;
        float score=-Float.MAX_VALUE;
        float[][] chosen=null;
        float[] normal=null,faceCenter=null;
        for(Quad q:cube.quads)
        {
            float[][] w=new float[4][];
            float[] avg={0,0,0};
            for(int i=0;i<4;i++)
            {
                w[i]=transform(cube.restMatrix,q.vertices[i].position);
                for(int j=0;j<3;j++)avg[j]+=w[i][j]*.25F;
            }
            float[] n=direction(cube.restMatrix,q.normal);
            float[] radial=unit(new float[]{avg[0]-center[0],
                    0,avg[2]-center[2]});
            float align=dot(n,radial);
            // Reject horizontal cap, inside-facing and unrelated walls.
            if(Math.abs(n[1])>.72F||align<.17F)continue;
            // Prefer the true outward skin, not the narrow end face.
            float merit=align+.03F*(float)Math.sqrt(
                    sq(avg[0]-center[0])+sq(avg[2]-center[2]));
            if(merit>score)
            {
                score=merit;best=q;chosen=w;
                normal=n;faceCenter=avg;
            }
        }
        if(best==null)
            throw new IllegalArgumentException(
                    "No outward Chameleon quad for "+cube.bone);
        float[][] uv=new float[4][];
        for(int i=0;i<4;i++)uv[i]=best.vertices[i].uv.clone();
        // Coordinates may be mirrored or rotated 180 degrees; derive
        // top/bottom and left/right from actual world geometry.
        Integer[] order={0,1,2,3};
        final float[][] w=chosen;
        Arrays.sort(order,new Comparator<Integer>()
        {
            @Override public int compare(Integer a,Integer b)
            {
                return Float.compare(w[b][1],w[a][1]);
            }
        });
        int topA=order[0],topB=order[1],
                bottomA=order[2],bottomB=order[3];
        float[] radial=unit(new float[]{
                faceCenter[0]-center[0],0,faceCenter[2]-center[2]});
        float[] tangent={-radial[2],0F,radial[0]};
        if(dot(w[topA],tangent)>dot(w[topB],tangent))
        {int t=topA;topA=topB;topB=t;}
        if(dot(w[bottomA],tangent)>dot(w[bottomB],tangent))
        {int t=bottomA;bottomA=bottomB;bottomB=t;}
        return new Patch(cube.bone,lower,chosen,uv,faceCenter,normal,
                topA,topB,bottomA,bottomB);
    }

    private static float[] cubeCenter(Cube cube)
    {
        float[] sum={0F,0F,0F};
        int count=0;
        for(Quad quad:cube.quads)
            for(Vertex vertex:quad.vertices)
            {
                float[] p=transform(cube.restMatrix,vertex.position);
                for(int k=0;k<3;k++)sum[k]+=p[k];
                count++;
            }
        if(count==0)throw new IllegalArgumentException("Cube has no points");
        for(int k=0;k<3;k++)sum[k]/=count;
        return sum;
    }
    private static float[] direction(float[] m,float[] n)
    {
        return unit(new float[]{m[0]*n[0]+m[4]*n[1]+m[8]*n[2],
                m[1]*n[0]+m[5]*n[1]+m[9]*n[2],
                m[2]*n[0]+m[6]*n[1]+m[10]*n[2]});
    }
    private static float[] transform(float[] m,float[] p)
    {
        return new float[]{m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],
                m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],
                m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]};
    }
    /** Affine inverse with 3x3 cofactors, including mirrored scale. */
    private static float[] invert(float[] m)
    {
        float a=m[0],b=m[4],c=m[8];
        float d=m[1],e=m[5],f=m[9];
        float g=m[2],h=m[6],i=m[10];
        float A=e*i-f*h,B=c*h-b*i,C=b*f-c*e;
        float D=f*g-d*i,E=a*i-c*g,F=c*d-a*f;
        float G=d*h-e*g,H=b*g-a*h,I=a*e-b*d;
        float det=a*A+b*D+c*G;
        if(Math.abs(det)<1E-9F||!Float.isFinite(det))return null;
        float z=1F/det;
        float[] inv={A*z,D*z,G*z,0,B*z,E*z,H*z,0,C*z,F*z,I*z,0,0,0,0,1};
        float x=m[12],y=m[13],w=m[14];
        inv[12]=-(inv[0]*x+inv[4]*y+inv[8]*w);
        inv[13]=-(inv[1]*x+inv[5]*y+inv[9]*w);
        inv[14]=-(inv[2]*x+inv[6]*y+inv[10]*w);
        return inv;
    }
    private static float[] unit(float[] v)
    {
        float n=(float)Math.sqrt(dot(v,v));
        if(n<EPS)return new float[]{0F,0F,0F};
        return new float[]{v[0]/n,v[1]/n,v[2]/n};
    }
    private static float dist(float[] a,float[] b)
    {
        return (float)Math.sqrt(sq(a[0]-b[0])+sq(a[1]-b[1])+
                sq(a[2]-b[2]));
    }
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float sq(float v){return v*v;}
    private static float[] finite(float[] v,int size)
    {
        if(v==null||v.length!=size)
            throw new IllegalArgumentException("Wrong vertex/matrix dimension");
        for(float f:v)
            if(!Float.isFinite(f))
                throw new IllegalArgumentException("Non-finite mesh data");
        return v;
    }
    private static float[][] clone2(float[][] source)
    {
        float[][] result=new float[source.length][];
        for(int i=0;i<source.length;i++)result[i]=source[i].clone();
        return result;
    }
}

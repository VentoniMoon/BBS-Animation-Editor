package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exact static CUBE VOLUME reference for the authored 44-bone Chameleon skirt.
 *
 * Unlike SkirtClothSurfaceMesh, which intentionally selected only one
 * outward-facing quad per moving cube, the parity reference copies every
 * decoded, textured ModelQuad of all 43 authored cubes (3 waistband +
 * 40 fabric). It preserves their original individual UVs, cube rotation and
 * bone rest transforms. This class does not edit native Chameleon resources.
 *
 * The reference is NOT a cloth mesh: it contains the original overlapping
 * box sides, caps and thickness, so it cannot be driven by the 60-node cloth
 * until a later volume-to-surface conversion is visually validated.
 */
public final class ChameleonSkirtVolumeSnapshot
{
    public static final int EXPECTED_CUBES=43;
    private static final float EPS=1E-9F;

    public static final class Part
    {
        public final SkirtClothSurfaceMesh.Cube cube;
        /** Per-bone native color RGBA, absoluteBrightness (0/1) and glow. */
        public final float[] material;

        public Part(SkirtClothSurfaceMesh.Cube cube,float[] material)
        {
            if(cube==null||material==null||material.length!=6)
                throw new IllegalArgumentException("Invalid native cube part");
            this.cube=cube;
            this.material=material.clone();
            for(float f:this.material)
                if(!Float.isFinite(f))
                    throw new IllegalArgumentException("Non-finite material");
        }
    }

    /** Exact native source quad after its own bone and cube rest transforms. */
    public static final class Face
    {
        public final String bone;
        public final float[][] world,uv;
        public final float[] normal,material;
        private Face(String bone,float[][] world,float[][] uv,
                float[] normal,float[] material)
        {
            this.bone=bone;
            this.world=copy(world);
            this.uv=copy(uv);
            this.normal=normal.clone();
            this.material=material.clone();
        }
    }

    public static final class Snapshot
    {
        public final List<Face> faces;
        public final int cuboidCount,quadCount,vertexCount;
        public final float[] boundsMin,boundsMax;

        private Snapshot(List<Face> faces,int cuboids,float[] min,float[] max)
        {
            this.faces=Collections.unmodifiableList(faces);
            this.cuboidCount=cuboids;
            this.quadCount=faces.size();
            this.vertexCount=faces.size()*4;
            this.boundsMin=min.clone();
            this.boundsMax=max.clone();
        }
    }

    private ChameleonSkirtVolumeSnapshot(){}

    public static Snapshot capture(List<Part> parts)
    {
        if(parts==null||parts.size()!=EXPECTED_CUBES)
            throw new IllegalArgumentException(
                    "Expected 3 waist and 40 fabric Chameleon cubes");
        List<Face> faces=new ArrayList<Face>(EXPECTED_CUBES*6);
        float[] min={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY};
        float[] max={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,
                Float.NEGATIVE_INFINITY};
        for(Part part:parts)
        {
            if(part==null||part.cube.quads.isEmpty())
                throw new IllegalArgumentException("Empty native cube");
            SkirtClothSurfaceMesh.Cube cube=part.cube;
            float[] matrix=cube.restMatrix;
            valid(matrix,16);
            for(SkirtClothSurfaceMesh.Quad quad:cube.quads)
            {
                if(quad.vertices.length!=4)
                    throw new IllegalArgumentException("Not a native quad");
                float[][] points=new float[4][3],uv=new float[4][2];
                for(int i=0;i<4;i++)
                {
                    SkirtClothSurfaceMesh.Vertex vertex=quad.vertices[i];
                    valid(vertex.position,3);
                    valid(vertex.uv,2);
                    points[i]=world(matrix,vertex.position);
                    uv[i]=vertex.uv.clone();
                    for(int k=0;k<3;k++)
                    {
                        min[k]=Math.min(min[k],points[i][k]);
                        max[k]=Math.max(max[k],points[i][k]);
                    }
                }
                faces.add(new Face(cube.bone,points,uv,
                        normal(matrix,quad.normal),part.material));
            }
        }
        if(faces.size()<EXPECTED_CUBES ||
                faces.size()>EXPECTED_CUBES*6)
            throw new IllegalArgumentException("Incomplete decoded volume");
        return new Snapshot(faces,parts.size(),min,max);
    }

    private static float[] world(float[] m,float[] p)
    {
        return valid(new float[]{
                m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12],
                m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13],
                m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]
        },3);
    }

    /** Inverse-transpose 3x3 normal transformation for nonuniform/mirrored
     * bone scale (instead of mistaking vertex and normal matrices). */
    private static float[] normal(float[] m,float[] n)
    {
        valid(n,3);
        float[] x={m[0],m[1],m[2]};
        float[] y={m[4],m[5],m[6]};
        float[] z={m[8],m[9],m[10]};
        float[] coX=cross(y,z),coY=cross(z,x),coZ=cross(x,y);
        float det=dot(x,coX);
        if(!Float.isFinite(det)||Math.abs(det)<EPS)
            throw new IllegalArgumentException("Singular native quad transform");
        float[] v={
                (coX[0]*n[0]+coY[0]*n[1]+coZ[0]*n[2])/det,
                (coX[1]*n[0]+coY[1]*n[1]+coZ[1]*n[2])/det,
                (coX[2]*n[0]+coY[2]*n[1]+coZ[2]*n[2])/det
        };
        float length=(float)Math.sqrt(dot(v,v));
        if(!Float.isFinite(length)||length<EPS)
            throw new IllegalArgumentException("Zero native quad normal");
        for(int j=0;j<3;j++)v[j]/=length;
        return v;
    }

    private static float[] cross(float[] a,float[] b)
    {
        return new float[]{
                a[1]*b[2]-a[2]*b[1],
                a[2]*b[0]-a[0]*b[2],
                a[0]*b[1]-a[1]*b[0]
        };
    }
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static float[] valid(float[] a,int size)
    {
        if(a==null||a.length!=size)
            throw new IllegalArgumentException("Invalid native quad dimension");
        for(float f:a)
            if(!Float.isFinite(f))
                throw new IllegalArgumentException("Non-finite native quad");
        return a;
    }
    private static float[][] copy(float[][] a)
    {
        float[][] out=new float[a.length][];
        for(int i=0;i<a.length;i++)out[i]=a[i].clone();
        return out;
    }
}

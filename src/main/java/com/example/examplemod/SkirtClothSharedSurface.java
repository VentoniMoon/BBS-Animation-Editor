package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Physics 3.0 Stage 2: immutable canonical surface of the REST Chameleon skirt.
 *
 * This is a pure Java 8 geometry builder, NOT a render replacement and NOT a
 * cloth solver. It joins 40 original textured exterior quads into a single
 * closed 20x3 vertex lattice while retaining four ORIGINAL UV corners on each
 * original quad. UV coordinates are never averaged across neighboring quads.
 *
 * Each candidate seam is deliberately treated as a hypothesis, not proof
 * that the two source cubes had a visible gap. Diagnostics describe exactly
 * how far the source quads would have to move to share one physical vertex.
 */
public final class SkirtClothSharedSurface
{
    private static final int PANELS=20;
    private static final int PATCHES=40;
    private static final int VERTICES=160;
    private static final int EXPECTED_NODES=60;
    private static final float TINY=1E-7F;

    public static final class Node
    {
        /** 0=waist, 1=upper/lower junction, 2=hem. */
        public final int row;
        public final float[] rest;
        /** Number of actual original ModelVertex corners sharing this node. */
        public final int contributors;
        private Node(int row,float[] rest,int contributors)
        {
            this.row=row;
            this.rest=rest.clone();
            this.contributors=contributors;
        }
    }

    public static final class SkinPatch
    {
        public final String bone;
        public final boolean lower;
        public final int[] nodes;
        /** Untouched Chameleon per-face UVs, including at geometric seams. */
        public final float[][] uv;
        /** Four source quad corners projected onto shared physical nodes. */
        public final float[][] rest;
        /** Original triangle winding, no UV re-atlas or new strip order. */
        public final int[] triangles;

        private SkinPatch(SkirtClothSurfaceMesh.Patch source,
                int[] nodes,float[][] rest)
        {
            this.bone=source.bone;
            this.lower=source.lower;
            this.nodes=nodes.clone();
            this.rest=copy(rest);
            this.uv=copy(source.uv);
            this.triangles=source.triangles.clone();
        }
    }

    public static final class Surface
    {
        public final List<Node> nodes;
        public final List<SkinPatch> patches;
        public final int triangleCount;
        public final int[] rowCounts;
        /** Maximum and average displacement of original decoded corners. */
        public final float maxRestCorrection,meanRestCorrection;
        /** Number of projected triangles whose winding flips relative
         * to their ORIGINAL Chameleon quad; not silently accepted. */
        public final int flippedTriangles,degenerateTriangles;
        /** Maximum absolute side edge-length change divided by source
         * edge length (including reconnected top/bottom seams). */
        public final float maxRelativeEdgeChange;
        /** Source candidate-seam maximum before vertex unification. */
        public final float sourceMaxSeamGap;
        /** Largest residual geometric seam gap after unification. */
        public final float residualSeamGap;

        private Surface(List<Node> nodes,List<SkinPatch> patches,
                int[] counts,float maximum,float mean,
                int flipped,int degenerate,float relative,
                float before,float after)
        {
            this.nodes=Collections.unmodifiableList(nodes);
            this.patches=Collections.unmodifiableList(patches);
            this.triangleCount=patches.size()*2;
            this.rowCounts=counts.clone();
            this.maxRestCorrection=maximum;
            this.meanRestCorrection=mean;
            this.flippedTriangles=flipped;
            this.degenerateTriangles=degenerate;
            this.maxRelativeEdgeChange=relative;
            this.sourceMaxSeamGap=before;
            this.residualSeamGap=after;
        }
    }

    private SkirtClothSharedSurface(){}

    /**
     * A complete capture must contain 40 outward faces, 60 candidate seam
     * pairs (20 upper, 20 lower, 20 vertical), and exactly 60 unique shared
     * nodes after union. Fail closed if topology is not a 20x3 closed strip.
     */
    public static Surface connect(SkirtClothSurfaceMesh.Snapshot source)
    {
        if(source==null||source.patches.size()!=PATCHES||
                source.seams.size()!=PANELS*3)
            throw new IllegalArgumentException("Need full 20x2 source skin");
        Map<String,Integer> byBone=new HashMap<String,Integer>();
        int[] row=new int[VERTICES];
        int[] parents=new int[VERTICES];
        byte[] rank=new byte[VERTICES];
        for(int p=0;p<PATCHES;p++)
        {
            SkirtClothSurfaceMesh.Patch patch=source.patches.get(p);
            if(patch==null||byBone.put(patch.bone,p)!=null)
                throw new IllegalArgumentException("Repeated source patch");
            if(patch.lower!=((p&1)==1))
                throw new IllegalArgumentException("Unexpected upper/lower order");
            for(int v=0;v<4;v++)
            {
                int idx=p*4+v;
                parents[idx]=idx;
                int top=patch.lower?1:0;
                int bottom=patch.lower?2:1;
                if(v==patch.topLeft||v==patch.topRight)row[idx]=top;
                else if(v==patch.bottomLeft||v==patch.bottomRight)
                    row[idx]=bottom;
                else
                    throw new IllegalArgumentException(
                            "Missing original skin corner row");
                if(patch.world[v]==null||patch.world[v].length!=3||
                        patch.uv[v]==null||patch.uv[v].length!=2)
                    throw new IllegalArgumentException("Missing source UV/XYZ");
                for(float x:patch.world[v])
                    if(!Float.isFinite(x))
                        throw new IllegalArgumentException("Bad XYZ");
                for(float x:patch.uv[v])
                    if(!Float.isFinite(x))
                        throw new IllegalArgumentException("Bad UV");
            }
        }

        int[] seamCounts=new int[3];
        for(SkirtClothSurfaceMesh.Seam seam:source.seams)
        {
            if(seam==null||seam.aEndpoints.length!=2||
                    seam.bEndpoints.length!=2)
                throw new IllegalArgumentException("Incomplete seam");
            Integer a=byBone.get(seam.a),b=byBone.get(seam.b);
            if(a==null||b==null||a.equals(b))
                throw new IllegalArgumentException("Seam outside the ring");
            int category="upper".equals(seam.category)?0:
                    "lower".equals(seam.category)?1:
                    "vertical".equals(seam.category)?2:-1;
            if(category<0||++seamCounts[category]>PANELS)
                throw new IllegalArgumentException("Invalid seam group");
            if(category==0 &&
                    (source.patches.get(a).lower||source.patches.get(b).lower)
               ||category==1 &&
                    (!source.patches.get(a).lower||!source.patches.get(b).lower)
               ||category==2 &&
                    (source.patches.get(a).lower||!source.patches.get(b).lower))
                throw new IllegalArgumentException("Wrong seam level");
            for(int k=0;k<2;k++)
            {
                int va=seam.aEndpoints[k],vb=seam.bEndpoints[k];
                if(va<0||va>3||vb<0||vb>3)
                    throw new IllegalArgumentException("Bad source vertex");
                int ai=a*4+va,bi=b*4+vb;
                if(row[ai]!=row[bi])
                    throw new IllegalArgumentException(
                            "Seam crosses waist/middle/hem rows");
                union(ai,bi,parents,rank);
            }
        }
        for(int n:seamCounts)
            if(n!=PANELS)
                throw new IllegalArgumentException("Missing ring seam category");

        // Deterministic root enumeration in input skin order. Do not sort
        // spatial coordinates to match two frames: their IDs derive from
        // actual decoded patch vertices and original seam correspondence.
        Map<Integer,Integer> rootToIndex=new LinkedHashMap<Integer,Integer>();
        List<float[]> sums=new ArrayList<float[]>();
        List<Integer> counts=new ArrayList<Integer>();
        List<Integer> rows=new ArrayList<Integer>();
        int[] cornerToNode=new int[VERTICES];
        for(int i=0;i<VERTICES;i++)
        {
            int root=find(i,parents);
            Integer idx=rootToIndex.get(root);
            if(idx==null)
            {
                idx=rootToIndex.size();
                rootToIndex.put(root,idx);
                sums.add(new float[3]);
                counts.add(0);
                rows.add(row[i]);
            }
            else if(rows.get(idx)!=row[i])
                throw new IllegalArgumentException("Mixed lattice row");
            cornerToNode[i]=idx;
            float[] p=source.patches.get(i/4).world[i%4];
            float[] sum=sums.get(idx);
            for(int k=0;k<3;k++)sum[k]+=p[k];
            counts.set(idx,counts.get(idx)+1);
        }
        if(rootToIndex.size()!=EXPECTED_NODES)
            throw new IllegalArgumentException("Expected 60 shared nodes, got "+
                    rootToIndex.size());
        List<Node> nodes=new ArrayList<Node>(EXPECTED_NODES);
        int[] rowCounts=new int[3];
        for(int n=0;n<EXPECTED_NODES;n++)
        {
            int r=rows.get(n);
            if(r<0||r>2)throw new IllegalArgumentException("Invalid row");
            rowCounts[r]++;
            float[] p=sums.get(n);
            int count=counts.get(n);
            for(int k=0;k<3;k++)p[k]/=count;
            nodes.add(new Node(r,p,count));
        }
        for(int r:rowCounts)
            if(r!=PANELS)
                throw new IllegalArgumentException("Incomplete ring latitude");

        List<SkinPatch> patches=new ArrayList<SkinPatch>(PATCHES);
        float maxCorrection=0F,totalCorrection=0F,maxRelative=0F;
        int badWinding=0,badArea=0;
        for(int p=0;p<PATCHES;p++)
        {
            SkirtClothSurfaceMesh.Patch original=source.patches.get(p);
            int[] ids=new int[4];
            float[][] connected=new float[4][3];
            for(int i=0;i<4;i++)
            {
                ids[i]=cornerToNode[p*4+i];
                connected[i]=nodes.get(ids[i]).rest.clone();
                float correction=distance(
                        connected[i],original.world[i]);
                maxCorrection=Math.max(maxCorrection,correction);
                totalCorrection+=correction;
                int j=(i+1)%4;
                float originalLength=distance(
                        original.world[i],original.world[j]);
                float projectedLength=distance(
                        connected[i],nodes.get(cornerToNode[p*4+j]).rest);
                if(originalLength>TINY)
                    maxRelative=Math.max(maxRelative,
                            Math.abs(projectedLength/originalLength-1F));
            }
            for(int t=0;t<6;t+=3)
            {
                int a=original.triangles[t],b=original.triangles[t+1],
                        c=original.triangles[t+2];
                if(a<0||b<0||c<0||a>3||b>3||c>3)
                    throw new IllegalArgumentException("Invalid source triangle");
                float[] oldNormal=triangle(original.world[a],
                        original.world[b],original.world[c]);
                float[] newNormal=triangle(connected[a],
                        connected[b],connected[c]);
                float areaOld=length(oldNormal),areaNew=length(newNormal);
                if(areaOld<TINY||areaNew<TINY)
                    badArea++;
                else if(dot(oldNormal,newNormal)<0F)
                    badWinding++;
            }
            patches.add(new SkinPatch(original,ids,connected));
        }
        float residual=0F;
        for(SkirtClothSurfaceMesh.Seam seam:source.seams)
        {
            SkinPatch a=patches.get(byBone.get(seam.a));
            SkinPatch b=patches.get(byBone.get(seam.b));
            for(int k=0;k<2;k++)
            {
                int ai=seam.aEndpoints[k],bi=seam.bEndpoints[k];
                residual=Math.max(residual,distance(a.rest[ai],b.rest[bi]));
                if(a.nodes[ai]!=b.nodes[bi])
                    throw new IllegalArgumentException("Unshared seam node");
            }
        }
        return new Surface(nodes,patches,rowCounts,
                maxCorrection,totalCorrection/VERTICES,
                badWinding,badArea,maxRelative,
                source.maxRestGap,residual);
    }

    private static float[][] copy(float[][] a)
    {
        float[][] b=new float[a.length][];
        for(int i=0;i<a.length;i++)b[i]=a[i].clone();
        return b;
    }
    private static int find(int p,int[] parents)
    {
        if(parents[p]!=p)parents[p]=find(parents[p],parents);
        return parents[p];
    }
    private static void union(int a,int b,int[] parents,byte[] rank)
    {
        a=find(a,parents);b=find(b,parents);
        if(a==b)return;
        if(rank[a]<rank[b])parents[a]=b;
        else if(rank[a]>rank[b])parents[b]=a;
        else{parents[b]=a;rank[a]++;}
    }
    private static float[] triangle(float[] a,float[] b,float[] c)
    {
        float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2];
        float vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
        return new float[]{uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx};
    }
    private static float distance(float[] a,float[] b)
    {
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
    private static float length(float[] v)
    {return (float)Math.sqrt(dot(v,v));}
    private static float dot(float[] a,float[] b)
    {return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}

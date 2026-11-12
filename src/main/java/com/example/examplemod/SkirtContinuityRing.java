package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A render-only continuous skirt ring built from already-posed Chameleon
 * cuboids. Instead of 20 unrelated hanging boxes, the two rows share
 * neighboring SIDE boundaries and their upper/lower joint corners.
 *
 * Only vertex positions are returned. The renderer restores every modified
 * model vertex immediately after drawing; animation keys, original cubes,
 * Jolt 6.0, skin and export pose are never changed.
 *
 * This is geometry stitching, NOT 40 Jolt bodies with real lateral
 * constraints. It is deliberately restricted to the exact 20x2 skirt rig.
 */
public final class SkirtContinuityRing
{
    public static final int STRIPS=20;
    private static final int CORNERS=8;
    private static final float MAX_REST_SEAM=.26F;
    private static final float MAX_POSED_MOVE=.28F;
    private static final float MIN_EDGE_HEIGHT=.07F;

    /** x*4 + y*2 + z index, where y=0 is the cube's low local Y. */
    public static final class Cube
    {
        public final String name;
        public final float[][] rest,posed;
        public Cube(String name,float[][] rest,float[][] posed)
        {
            this.name=name;
            this.rest=rest;
            this.posed=posed;
        }
    }

    public static final class Strip
    {
        public final Cube upper,lower;
        public Strip(Cube upper,Cube lower)
        {
            this.upper=upper;
            this.lower=lower;
        }
    }

    public static final class Result
    {
        public final Map<String,float[][]> targets;
        public final int lateralSeams,verticalSeams,joinedVertices,skippedGroups;

        private Result(Map<String,float[][]> targets,int lateral,
                int vertical,int joined,int skipped)
        {
            this.targets=targets;
            lateralSeams=lateral;
            verticalSeams=vertical;
            joinedVertices=joined;
            skippedGroups=skipped;
        }
    }

    private static final class Band
    {
        final Strip strip;
        final double polar;
        Band(Strip strip,double polar){this.strip=strip;this.polar=polar;}
    }

    private static final class Edge
    {
        final int low,high;
        Edge(int low,int high){this.low=low;this.high=high;}
    }

    private SkirtContinuityRing(){}

    public static Result stitch(List<Strip> strips)
    {
        if(strips==null||strips.size()!=STRIPS)return empty();
        Map<String,Integer> cubeIndices=new LinkedHashMap<String,Integer>();
        List<Cube> cubes=new ArrayList<Cube>();
        float cx=0F,cz=0F;
        for(Strip strip:strips)
        {
            if(strip==null||!valid(strip.upper)||!valid(strip.lower))
                return empty();
            for(Cube c:new Cube[]{strip.upper,strip.lower})
            {
                if(cubeIndices.containsKey(c.name))return empty();
                cubeIndices.put(c.name,cubes.size());
                cubes.add(c);
            }
            float[] mid=centroid(strip.upper.rest);
            cx+=mid[0];cz+=mid[2];
        }
        cx/=STRIPS;cz/=STRIPS;

        List<Band> ring=new ArrayList<Band>();
        for(Strip strip:strips)
        {
            float[] mid=centroid(strip.upper.rest);
            ring.add(new Band(strip,Math.atan2(mid[2]-cz,mid[0]-cx)));
        }
        Collections.sort(ring,new Comparator<Band>()
        {
            @Override public int compare(Band a,Band b)
            {
                int c=Double.compare(a.polar,b.polar);
                return c==0?a.strip.upper.name.compareTo(b.strip.upper.name):c;
            }
        });

        int total=cubes.size()*CORNERS;
        int[] parent=new int[total];
        for(int i=0;i<total;i++)parent[i]=i;
        int lateral=0,vertical=0;

        for(int i=0;i<STRIPS;i++)
        {
            Strip a=ring.get(i).strip,b=ring.get((i+1)%STRIPS).strip;
            if(joinLateral(a.upper,b.upper,cubeIndices,parent))lateral++;
            if(joinLateral(a.lower,b.lower,cubeIndices,parent))lateral++;
        }
        for(Band band:ring)
        {
            if(joinVertical(band.strip.upper,band.strip.lower,
                    cubeIndices,parent))vertical++;
        }

        Map<Integer,List<Integer>> groups=new LinkedHashMap<Integer,List<Integer>>();
        for(int id=0;id<total;id++)
        {
            int root=find(parent,id);
            List<Integer> members=groups.get(root);
            if(members==null)
            {
                members=new ArrayList<Integer>();
                groups.put(root,members);
            }
            members.add(id);
        }

        Map<String,float[][]> adjusted=new LinkedHashMap<String,float[][]>();
        for(Cube cube:cubes)
        {
            float[][] current=new float[CORNERS][];
            for(int j=0;j<CORNERS;j++)current[j]=cube.posed[j].clone();
            adjusted.put(cube.name,current);
        }
        int joined=0,skipped=0;
        for(List<Integer> members:groups.values())
        {
            if(members.size()<2)continue;
            float[] target=new float[3];
            Set<Integer> owners=new HashSet<Integer>();
            boolean oneCornerPerCube=true;
            for(Integer id:members)
            {
                int cube=id/CORNERS,corner=id%CORNERS;
                if(!owners.add(cube))oneCornerPerCube=false;
                float[] pt=cubes.get(cube).posed[corner];
                for(int axis=0;axis<3;axis++)target[axis]+=pt[axis];
            }
            if(!oneCornerPerCube){skipped++;continue;}
            for(int axis=0;axis<3;axis++)target[axis]/=members.size();
            boolean safe=true;
            for(Integer id:members)
            {
                float[] old=cubes.get(id/CORNERS).posed[id%CORNERS];
                if(distance(target,old)>MAX_POSED_MOVE){safe=false;break;}
            }
            if(!safe){skipped++;continue;}
            for(Integer id:members)
            {
                Cube cube=cubes.get(id/CORNERS);
                adjusted.get(cube.name)[id%CORNERS]=target.clone();
                joined++;
            }
        }
        return new Result(adjusted,lateral,vertical,joined,skipped);
    }

    private static Result empty()
    {
        return new Result(Collections.<String,float[][]>emptyMap(),0,0,0,0);
    }

    private static boolean joinLateral(Cube a,Cube b,
            Map<String,Integer> ids,int[] union)
    {
        // Two upright edges bound the neighboring face: its inner AND
        // outer corners must both be shared. Joining just the nearest
        // edge produced the user's original disconnected tab silhouette.
        List<Edge> ea=uprightEdges(a),eb=uprightEdges(b);
        if(ea.size()<2||eb.size()<2)return false;
        final float[] ac=centroid(a.rest),bc=centroid(b.rest);
        final float[] towardB=sub(bc,ac),towardA=sub(ac,bc);
        Collections.sort(ea,new Comparator<Edge>()
        {
            @Override public int compare(Edge left,Edge right)
            {
                return Float.compare(faceScore(a,left,towardB,ac),
                        faceScore(a,right,towardB,ac))*-1;
            }
        });
        Collections.sort(eb,new Comparator<Edge>()
        {
            @Override public int compare(Edge left,Edge right)
            {
                return Float.compare(faceScore(b,left,towardA,bc),
                        faceScore(b,right,towardA,bc))*-1;
            }
        });
        Edge[] sidesA={ea.get(0),ea.get(1)};
        Edge[] sidesB={eb.get(0),eb.get(1)};
        float direct=Math.max(restGap(a,sidesA[0],b,sidesB[0]),
                restGap(a,sidesA[1],b,sidesB[1]));
        float crossed=Math.max(restGap(a,sidesA[0],b,sidesB[1]),
                restGap(a,sidesA[1],b,sidesB[0]));
        if(crossed<direct)
        {
            Edge tmp=sidesB[0];sidesB[0]=sidesB[1];sidesB[1]=tmp;
            direct=crossed;
        }
        if(direct>MAX_REST_SEAM)return false;
        for(int e=0;e<2;e++)
        {
            Edge x=sidesA[e],y=sidesB[e];
            link(union,ids.get(a.name)*CORNERS+x.low,
                    ids.get(b.name)*CORNERS+y.low);
            link(union,ids.get(a.name)*CORNERS+x.high,
                    ids.get(b.name)*CORNERS+y.high);
        }
        return true;
    }

    private static float faceScore(Cube cube,Edge edge,
            float[] direction,float[] center)
    {
        float[] a=cube.rest[edge.low],b=cube.rest[edge.high];
        return ((a[0]+b[0])*.5F-center[0])*direction[0]+
                ((a[1]+b[1])*.5F-center[1])*direction[1]+
                ((a[2]+b[2])*.5F-center[2])*direction[2];
    }

    private static float restGap(Cube a,Edge x,Cube b,Edge y)
    {
        return Math.max(distance(a.rest[x.low],b.rest[y.low]),
                distance(a.rest[x.high],b.rest[y.high]));
    }

    private static float[] sub(float[] a,float[] b)
    {
        return new float[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};
    }

    private static List<Edge> uprightEdges(Cube cube)
    {
        List<Edge> out=new ArrayList<Edge>();
        for(int x=0;x<2;x++)for(int z=0;z<2;z++)
        {
            int a=x*4+z,b=x*4+2+z;
            float[] low=cube.rest[a],high=cube.rest[b];
            float length=distance(low,high);
            if(length<MIN_EDGE_HEIGHT)continue;
            float upright=Math.abs(high[1]-low[1])/length;
            if(upright<.60F)continue;
            out.add(low[1]<=high[1]?new Edge(a,b):new Edge(b,a));
        }
        return out;
    }

    private static boolean joinVertical(Cube upper,Cube lower,
            Map<String,Integer> ids,int[] union)
    {
        // Join the four low-world-Y upper-cube corners to the four
        // high-world-Y child-cube corners, in source REST space.
        int[] bottom=sortY(upper.rest,false);
        int[] top=sortY(lower.rest,true);
        boolean[] used=new boolean[4];
        int matched=0;
        for(int i=0;i<4;i++)
        {
            float best=MAX_REST_SEAM;
            int hit=-1;
            for(int j=0;j<4;j++)
            {
                if(used[j])continue;
                float dist=distance(upper.rest[bottom[i]],lower.rest[top[j]]);
                if(dist<=best){hit=j;best=dist;}
            }
            if(hit<0)continue;
            used[hit]=true;
            link(union,ids.get(upper.name)*CORNERS+bottom[i],
                    ids.get(lower.name)*CORNERS+top[hit]);
            matched++;
        }
        return matched>=2;
    }

    private static int[] sortY(float[][] vertices,boolean descending)
    {
        Integer[] n=new Integer[CORNERS];
        for(int i=0;i<CORNERS;i++)n[i]=i;
        Arrays.sort(n,new Comparator<Integer>()
        {
            @Override public int compare(Integer a,Integer b)
            {
                int c=Float.compare(vertices[a][1],vertices[b][1]);
                return descending?-c:c;
            }
        });
        int[] out=new int[4];
        for(int i=0;i<4;i++)out[i]=n[i];
        return out;
    }

    private static void link(int[] p,int a,int b)
    {
        int ra=find(p,a),rb=find(p,b);
        if(ra!=rb)p[rb]=ra;
    }

    private static int find(int[] p,int i)
    {
        while(p[i]!=i){p[i]=p[p[i]];i=p[i];}
        return i;
    }

    private static float[] centroid(float[][] vertices)
    {
        float[] mid=new float[3];
        for(float[] pt:vertices)
            for(int axis=0;axis<3;axis++)mid[axis]+=pt[axis]/CORNERS;
        return mid;
    }

    private static boolean valid(Cube cube)
    {
        if(cube==null||cube.name==null||cube.name.isEmpty() ||
                cube.rest==null||cube.posed==null||
                cube.rest.length!=CORNERS||cube.posed.length!=CORNERS)
            return false;
        for(float[][] set:new float[][][]{cube.rest,cube.posed})
            for(float[] p:set)
            {
                if(p==null||p.length!=3)return false;
                for(float f:p)if(!Float.isFinite(f))return false;
            }
        return true;
    }

    private static float distance(float[] a,float[] b)
    {
        float x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
}

package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, mesh-local broad phase and nearest-triangle query for an
 * already-skinned Emoticons mesh. Never applies a correction across spaces.
 * Rebuild only after the parent mesh has been updated for the current frame.
 */
public final class EmoticonsMeshCollisionSolver
{
    private static final float CELL=0.125F;
    private static final int MAX_CELL_RANGE=8;
    private final List<Triangle> triangles=new ArrayList<Triangle>();
    private final Map<Long,List<Integer>> cells=new HashMap<Long,List<Integer>>();

    private static final class Triangle
    {
        final BodyPartChainSolver.Vec3 a,b,c;
        Triangle(BodyPartChainSolver.Vec3 a,BodyPartChainSolver.Vec3 b,
                 BodyPartChainSolver.Vec3 c){this.a=a;this.b=b;this.c=c;}
    }

    public static final class Contact
    {
        public final BodyPartChainSolver.Vec3 nearest;
        public final BodyPartChainSolver.Vec3 normal;
        public final float distance;
        public final int triangleIndex;
        Contact(BodyPartChainSolver.Vec3 nearest,BodyPartChainSolver.Vec3 normal,
                float distance,int triangleIndex)
        {this.nearest=nearest;this.normal=normal;this.distance=distance;
         this.triangleIndex=triangleIndex;}
    }

    public EmoticonsMeshCollisionSolver(
            List<EmoticonsCollisionGeometryProvider.MeshSnapshot> meshes)
    {
        if(meshes==null)return;
        for(EmoticonsCollisionGeometryProvider.MeshSnapshot mesh:meshes)
        {
            if(mesh==null)continue;
            float[] v=mesh.vertices;int[] idx=mesh.triangles;
            for(int i=0;i+2<idx.length;i+=3)
            {
                int ia=idx[i]*3,ib=idx[i+1]*3,ic=idx[i+2]*3;
                if(ia<0||ib<0||ic<0||ia+2>=v.length||
                        ib+2>=v.length||ic+2>=v.length)continue;
                BodyPartChainSolver.Vec3 a=vec(v,ia),b=vec(v,ib),c=vec(v,ic);
                BodyPartChainSolver.Vec3 n=cross(b.sub(a),c.sub(a));
                if(n.length()<1.0E-9F)continue;
                Triangle triangle=new Triangle(a,b,c);
                int id=triangles.size();triangles.add(triangle);
                float minX=Math.min(a.x,Math.min(b.x,c.x));
                float minY=Math.min(a.y,Math.min(b.y,c.y));
                float minZ=Math.min(a.z,Math.min(b.z,c.z));
                float maxX=Math.max(a.x,Math.max(b.x,c.x));
                float maxY=Math.max(a.y,Math.max(b.y,c.y));
                float maxZ=Math.max(a.z,Math.max(b.z,c.z));
                int x0=cell(minX),y0=cell(minY),z0=cell(minZ);
                int x1=cell(maxX),y1=cell(maxY),z1=cell(maxZ);
                // Very large decorative triangles should not exhaust the grid.
                if((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>32768L)continue;
                for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)
                    for(int z=z0;z<=z1;z++)
                    {
                        long key=key(x,y,z);
                        List<Integer> ids=cells.get(key);
                        if(ids==null){ids=new ArrayList<Integer>();cells.put(key,ids);}
                        ids.add(id);
                    }
            }
        }
    }

    /**
     * Ray parity diagnostics. UNKNOWN is expected with open, overlapping,
     * duplicate or non-manifold Emoticons meshes. Never use this diagnostic
     * as an authoritative solid-body test or apply forces from it.
     */
    public static final class RayProbe
    {
        public final int intersections;
        public final boolean odd;
        RayProbe(int intersections)
        {this.intersections=intersections;this.odd=(intersections&1)!=0;}
    }

    public RayProbe probeRayPositiveX(BodyPartChainSolver.Vec3 point)
    {
        if(point==null)return new RayProbe(0);
        // Mesh bounds are small in actor-local units. Full scan is deliberate
        // for diagnostic frames only; no expensive repeated solver calls.
        java.util.ArrayList<Float> hits=new java.util.ArrayList<Float>();
        for(Triangle t:triangles)
        {
            BodyPartChainSolver.Vec3 ab=t.b.sub(t.a),ac=t.c.sub(t.a);
            // Moller-Trumbore intersection for direction (1,0,0).
            BodyPartChainSolver.Vec3 h=cross(
                    new BodyPartChainSolver.Vec3(1F,0F,0F),ac);
            float det=dot(ab,h);
            if(Math.abs(det)<1E-8F)continue;
            float inv=1F/det;
            BodyPartChainSolver.Vec3 q=point.sub(t.a);
            float u=inv*dot(q,h);
            if(u<-1E-6F||u>1.000001F)continue;
            BodyPartChainSolver.Vec3 v=cross(q,ab);
            float w=inv*v.x;
            if(w<-1E-6F||u+w>1.000001F)continue;
            float distance=inv*dot(ac,v);
            if(distance<=1E-5F)continue;
            boolean duplicate=false;
            for(Float old:hits)
                if(Math.abs(old.floatValue()-distance)<1E-4F)
                {duplicate=true;break;}
            if(!duplicate)hits.add(Float.valueOf(distance));
        }
        return new RayProbe(hits.size());
    }

    public int triangleCount(){return triangles.size();}

    /**
     * Query only triangles within searchRadius of the point. The returned
     * normal is oriented toward the query point; inside/outside is UNKNOWN.
     */
    public Contact nearest(BodyPartChainSolver.Vec3 point,float searchRadius)
    {
        if(point==null || !(searchRadius>0F) || !Float.isFinite(searchRadius))
            return null;
        int radius=Math.min(MAX_CELL_RANGE,(int)Math.ceil(searchRadius/CELL));
        int cx=cell(point.x),cy=cell(point.y),cz=cell(point.z);
        Set<Integer> candidates=new HashSet<Integer>();
        for(int x=cx-radius;x<=cx+radius;x++)
            for(int y=cy-radius;y<=cy+radius;y++)
                for(int z=cz-radius;z<=cz+radius;z++)
                {
                    List<Integer> ids=cells.get(key(x,y,z));
                    if(ids!=null)candidates.addAll(ids);
                }
        float best=searchRadius*searchRadius;Contact result=null;
        for(Integer id:candidates)
        {
            Triangle t=triangles.get(id);
            BodyPartChainSolver.Vec3 nearest=closest(point,t.a,t.b,t.c);
            BodyPartChainSolver.Vec3 delta=point.sub(nearest);
            float d2=dot(delta,delta);
            if(d2<best)
            {
                float distance=(float)Math.sqrt(d2);
                BodyPartChainSolver.Vec3 normal;
                if(distance>1.0E-7F)normal=delta.scale(1F/distance);
                else
                {
                    normal=cross(t.b.sub(t.a),t.c.sub(t.a));
                    normal=normal.scale(1F/normal.length());
                }
                best=d2;result=new Contact(nearest,normal,distance,id);
            }
        }
        return result;
    }

    /**
     * Diagnostic signed distance along the nearest triangle's winding normal.
     * Positive/negative describes triangle winding ONLY. It is not a
     * confirmed inside/outside classification of the animated body.
     */
    public float windingSignedDistance(BodyPartChainSolver.Vec3 point, Contact contact)
    {
        if (point == null || contact == null ||
                contact.triangleIndex < 0 || contact.triangleIndex >= triangles.size())
            return Float.NaN;
        Triangle t = triangles.get(contact.triangleIndex);
        BodyPartChainSolver.Vec3 n = cross(t.b.sub(t.a), t.c.sub(t.a));
        float length = n.length();
        if (!(length > 1.0E-8F)) return Float.NaN;
        BodyPartChainSolver.Vec3 delta = point.sub(contact.nearest);
        return dot(delta, n) / length;
    }

    /** Winding normal of the actual contacted triangle; never assumed outward. */
    public BodyPartChainSolver.Vec3 windingNormal(Contact contact)
    {
        if (contact == null || contact.triangleIndex < 0 ||
                contact.triangleIndex >= triangles.size()) return null;
        Triangle triangle = triangles.get(contact.triangleIndex);
        BodyPartChainSolver.Vec3 n = cross(
                triangle.b.sub(triangle.a), triangle.c.sub(triangle.a));
        float length = n.length();
        return length > 1.0E-8F ? n.scale(1F/length) : null;
    }

    /** Segment/surface intersection in the same mesh-local coordinate frame.
     * This is a geometric hit only, not a reliable solid-body inside test.
     * The returned normal follows triangle winding, not an assumed outward side.
     */
    public static final class SegmentHit
    {
        public final float fraction;
        public final BodyPartChainSolver.Vec3 position;
        public final BodyPartChainSolver.Vec3 windingNormal;
        public final int triangleIndex;
        SegmentHit(float fraction, BodyPartChainSolver.Vec3 position,
                BodyPartChainSolver.Vec3 windingNormal, int triangleIndex)
        {
            this.fraction=fraction;
            this.position=position;
            this.windingNormal=windingNormal;
            this.triangleIndex=triangleIndex;
        }
    }

    public SegmentHit intersectSegment(BodyPartChainSolver.Vec3 start,
            BodyPartChainSolver.Vec3 end)
    {
        if (start == null || end == null) return null;
        BodyPartChainSolver.Vec3 dir=end.sub(start);
        if (dir.length() < 1.0E-7F) return null;
        float bestFraction=Float.POSITIVE_INFINITY;
        SegmentHit best=null;
        // Diagnostic calls are limited to a few bones on selected frames.
        // Full scan avoids missing long segments with a point-based grid.
        for (int id=0; id<triangles.size(); id++)
        {
            Triangle triangle=triangles.get(id);
            BodyPartChainSolver.Vec3 edge1=triangle.b.sub(triangle.a);
            BodyPartChainSolver.Vec3 edge2=triangle.c.sub(triangle.a);
            BodyPartChainSolver.Vec3 p=cross(dir, edge2);
            float determinant=dot(edge1,p);
            if (Math.abs(determinant)<1.0E-8F) continue;
            float inv=1F/determinant;
            BodyPartChainSolver.Vec3 t=start.sub(triangle.a);
            float u=dot(t,p)*inv;
            if (u<-1.0E-6F || u>1.000001F) continue;
            BodyPartChainSolver.Vec3 q=cross(t,edge1);
            float v=dot(dir,q)*inv;
            if (v<-1.0E-6F || u+v>1.000001F) continue;
            float fraction=dot(edge2,q)*inv;
            if (fraction<=1.0E-5F || fraction>=1F-1.0E-5F ||
                    fraction>=bestFraction) continue;
            BodyPartChainSolver.Vec3 normal=cross(edge1,edge2);
            float length=normal.length();
            if (length<1.0E-8F)continue;
            bestFraction=fraction;
            best=new SegmentHit(fraction,start.add(dir.scale(fraction)),
                    normal.scale(1F/length),id);
        }
        return best;
    }

    private static BodyPartChainSolver.Vec3 closest(BodyPartChainSolver.Vec3 p,
            BodyPartChainSolver.Vec3 a,BodyPartChainSolver.Vec3 b,
            BodyPartChainSolver.Vec3 c)
    {
        BodyPartChainSolver.Vec3 ab=b.sub(a),ac=c.sub(a),ap=p.sub(a);
        float d1=dot(ab,ap),d2=dot(ac,ap);
        if(d1<=0F&&d2<=0F)return a;
        BodyPartChainSolver.Vec3 bp=p.sub(b);
        float d3=dot(ab,bp),d4=dot(ac,bp);
        if(d3>=0F&&d4<=d3)return b;
        float vc=d1*d4-d3*d2;
        if(vc<=0F&&d1>=0F&&d3<=0F)
            return a.add(ab.scale(d1/(d1-d3)));
        BodyPartChainSolver.Vec3 cp=p.sub(c);
        float d5=dot(ab,cp),d6=dot(ac,cp);
        if(d6>=0F&&d5<=d6)return c;
        float vb=d5*d2-d1*d6;
        if(vb<=0F&&d2>=0F&&d6<=0F)
            return a.add(ac.scale(d2/(d2-d6)));
        float va=d3*d6-d5*d4;
        if(va<=0F&&(d4-d3)>=0F&&(d5-d6)>=0F)
            return b.add(c.sub(b).scale((d4-d3)/((d4-d3)+(d5-d6))));
        float denom=va+vb+vc;
        if(Math.abs(denom)<1E-12F)return a;
        float inv=1F/denom,v=vb*inv,w=vc*inv;
        return a.add(ab.scale(v)).add(ac.scale(w));
    }

    private static BodyPartChainSolver.Vec3 vec(float[] a,int i)
    {return new BodyPartChainSolver.Vec3(a[i],a[i+1],a[i+2]);}
    private static float dot(BodyPartChainSolver.Vec3 a,BodyPartChainSolver.Vec3 b)
    {return a.x*b.x+a.y*b.y+a.z*b.z;}
    private static BodyPartChainSolver.Vec3 cross(BodyPartChainSolver.Vec3 a,
            BodyPartChainSolver.Vec3 b)
    {return new BodyPartChainSolver.Vec3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,
            a.x*b.y-a.y*b.x);}
    private static int cell(float x){return (int)Math.floor(x/CELL);}
    private static long key(int x,int y,int z)
    {
        // Three signed, 21-bit cell indices packed into one spatial key.
        return (((long)x&0x1fffffL)<<42)|(((long)y&0x1fffffL)<<21)
                |((long)z&0x1fffffL);
    }
}

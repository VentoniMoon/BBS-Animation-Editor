package com.example.examplemod;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Read-only geometry snapshot AFTER Emoticons' normal render/updateMesh pass.
 * Vertex positions are already CPU-skinned by AnimationMesh.updateMesh().
 * They are in mesh/render-local coordinates; do not interpret as world-space.
 */
public final class EmoticonsCollisionGeometryProvider
{
    public static final class MeshSnapshot
    {
        public final String name;
        public final float[] vertices;
        public final int[] triangles;
        MeshSnapshot(String name,float[] vertices,int[] triangles)
        {this.name=name;this.vertices=vertices;this.triangles=triangles;}
        public int vertexCount(){return vertices.length/3;}
        public int triangleCount(){return triangles.length/3;}
    }

    /** Coordinate diagnostics; values remain in Emoticons mesh-local units. */
    public static final class Bounds
    {
        public final float minX,minY,minZ,maxX,maxY,maxZ;
        public final int count;
        Bounds(float a,float b,float c,float d,float e,float f,int n)
        {minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;count=n;}
        @Override public String toString()
        {
            return "min=("+minX+","+minY+","+minZ+") max=("
                    +maxX+","+maxY+","+maxZ+") vertices="+count;
        }
    }

    public static Bounds bounds(List<MeshSnapshot> meshes)
    {
        float minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,minZ=Float.POSITIVE_INFINITY;
        float maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY,maxZ=Float.NEGATIVE_INFINITY;
        int n=0;
        if(meshes!=null)
            for(MeshSnapshot mesh:meshes)
            {
                if(mesh==null)continue;
                for(int i=0;i+2<mesh.vertices.length;i+=3)
                {
                    float x=mesh.vertices[i],y=mesh.vertices[i+1],z=mesh.vertices[i+2];
                    if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))continue;
                    minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
                    maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
                    n++;
                }
            }
        return n==0?null:new Bounds(minX,minY,minZ,maxX,maxY,maxZ,n);
    }

    private EmoticonsCollisionGeometryProvider() {}

    public static List<MeshSnapshot> snapshot(Object morph)
    {
        if (!(morph instanceof AnimatedMorph)) return Collections.emptyList();
        List<MeshSnapshot> result=new ArrayList<MeshSnapshot>();
        for (AnimationMesh mesh:EmoticonsModelAccess.getMeshes((AnimatedMorph)morph))
        {
            if(mesh==null || mesh.data==null || mesh.vertices==null ||
                    mesh.data.indexData==null)continue;
            try
            {
                FloatBuffer buffer=mesh.vertices.asReadOnlyBuffer();
                buffer.position(0);
                int count=mesh.data.posData.length/4;
                if(count==0 || buffer.limit()<count*4)continue;
                float[] positions=new float[count*3];
                for(int i=0;i<count;i++)
                {
                    float x=buffer.get(i*4),y=buffer.get(i*4+1),z=buffer.get(i*4+2);
                    if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                        throw new IllegalArgumentException("Nonfinite skinned vertex");
                    positions[i*3]=x;positions[i*3+1]=y;positions[i*3+2]=z;
                }
                int[] raw=mesh.data.indexData;
                int[] triangles=new int[raw.length];
                System.arraycopy(raw,0,triangles,0,raw.length);
                if(triangles.length%3!=0)continue;
                boolean valid=true;
                for(int index:triangles)
                    if(index<0||index>=count){valid=false;break;}
                if(valid) result.add(new MeshSnapshot(mesh.name,positions,triangles));
            }
            catch(RuntimeException ignored)
            {
                // A pending/incompatible native mesh should not break preview.
            }
        }
        return result;
    }
}

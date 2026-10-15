package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative torso candidate triangles from the CPU-skinned 'body' mesh.
 * This is a broad-phase diagnostic, NOT a watertight torso volume.
 *
 * The dynamic Y interval follows the rendered Emoticons body/low_body bone
 * positions, rather than using global mesh bounds (which include head/legs).
 * Selection uses intersection with the interval, retaining boundary triangles.
 */
public final class EmoticonsTorsoGeometry
{
    private EmoticonsTorsoGeometry() {}

    public static EmoticonsCollisionGeometryProvider.MeshSnapshot selectBodyTorso(
            List<EmoticonsCollisionGeometryProvider.MeshSnapshot> meshes,
            float pelvisY, float upperBodyY)
    {
        if(meshes==null || !Float.isFinite(pelvisY) ||
                !Float.isFinite(upperBodyY))return null;
        EmoticonsCollisionGeometryProvider.MeshSnapshot body=null;
        for(EmoticonsCollisionGeometryProvider.MeshSnapshot mesh:meshes)
            if(mesh!=null && "body".equals(mesh.name)){body=mesh;break;}
        if(body==null)return null;

        // The BOBJ skeleton uses model-local units; verify this alignment
        // in diagnostics before using the filtered surface for collision.
        float bottom=Math.min(pelvisY,upperBodyY)-0.22F;
        float top=Math.max(pelvisY,upperBodyY)+0.55F;
        int[] source=body.triangles;
        float[] vertices=body.vertices;
        List<Integer> selected=new ArrayList<Integer>();
        for(int i=0;i+2<source.length;i+=3)
        {
            int ia=source[i]*3,ib=source[i+1]*3,ic=source[i+2]*3;
            float minY=Math.min(vertices[ia+1],Math.min(vertices[ib+1],vertices[ic+1]));
            float maxY=Math.max(vertices[ia+1],Math.max(vertices[ib+1],vertices[ic+1]));
            if(maxY>=bottom && minY<=top)
            {
                selected.add(source[i]);selected.add(source[i+1]);selected.add(source[i+2]);
            }
        }
        int[] triangles=new int[selected.size()];
        for(int i=0;i<triangles.length;i++)triangles[i]=selected.get(i);
        return new EmoticonsCollisionGeometryProvider.MeshSnapshot(
                "body_torso_candidate",vertices,triangles);
    }
}

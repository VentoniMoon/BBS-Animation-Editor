package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Vector3f;

import mchorse.chameleon.lib.data.model.Model;
import mchorse.chameleon.lib.data.model.ModelBone;
import mchorse.chameleon.lib.data.model.ModelCube;
import mchorse.chameleon.lib.data.model.ModelQuad;
import mchorse.chameleon.lib.data.model.ModelVertex;

/**
 * Execute the EXACT native render-hook vertex edit and restore path against
 * a full 40-bone, 20-segment rotated Chameleon skirt fixture on Java 8.
 */
public final class ChameleonSkirtRingNativeSmoke
{
    private static final String[][] PAIRS={
            {"group2","group22"},{"group6","group24"},
            {"group3","group25"},{"group4","group23"},
            {"group5","group26"},{"group18","group29"},
            {"group17","group27"},{"group7","group38"},
            {"group16","group39"},{"group8","group40"},
            {"group9","group37"},{"group10","group41"},
            {"group20","group35"},{"group19","group36"},
            {"group14","group31"},{"group15","group32"},
            {"group21","group30"},{"group13","group34"},
            {"group12","group28"},{"group11","group33"}
    };

    public static void main(String[] args)
    {
        exactRigPatchesAndRestores();
        unrelatedMorphIsNotStitched();
        System.out.println("ChameleonSkirtRingNativeSmoke OK");
    }

    private static void exactRigPatchesAndRestores()
    {
        Model model=new Model();
        ModelBone all=bone("ALL"),front=bone("group42"),
                back=bone("group43");
        all.children.add(front);
        all.children.add(back);
        model.bones.add(all);
        List<Vector3f> points=new ArrayList<Vector3f>();
        List<float[]> original=new ArrayList<float[]>();
        final float radius=.40F;
        for(int i=0;i<PAIRS.length;i++)
        {
            double angle=i*2*Math.PI/PAIRS.length;
            float x=radius*(float)Math.cos(angle);
            float z=radius*(float)Math.sin(angle);
            float rotation=(float)Math.toDegrees(-angle-Math.PI/2D);
            ModelBone top=bone(PAIRS[i][0]),low=bone(PAIRS[i][1]);
            (i>=10?back:front).children.add(top);
            top.children.add(low);
            addCube(top,x,z,.18F,.38F,rotation,points,original);
            addCube(low,x,z,-.02F,.18F,rotation,points,original);
        }
        // Different Jolt swings at two neighboring strips must not leave
        // permanent mesh deformations in any following camera/preview pass.
        ModelBone child=front.children.get(4);
        child.current.rotation.z=10F;
        child.children.get(0).current.rotation.x=12F;
        int changed=0,reported;
        try(ChameleonWeldPreviewPass pass=
                ChameleonWeldPreviewPass.beginPreparedModel(model))
        {
            reported=pass.getSeamCount();
            require(reported>=25,"20x2 skirt ring was not recognized: "+reported);
            for(int i=0;i<points.size();i++)
                if(distance(points.get(i),original.get(i))>1E-5F)changed++;
            require(changed>30,
                    "the native renderer did not apply continuous ring corners");
        }
        for(int i=0;i<points.size();i++)
            require(distance(points.get(i),original.get(i))<1E-6F,
                    "original Chameleon model vertex leaked after close "+i);
        require(changed<points.size(),"all original vertices changed unnecessarily");
    }

    private static void unrelatedMorphIsNotStitched()
    {
        Model model=new Model();
        model.bones.add(bone("unrelated_hair"));
        try(ChameleonWeldPreviewPass pass=
                ChameleonWeldPreviewPass.beginPreparedModel(model))
        {
            require(pass.getSeamCount()==0,
                    "weld skirt logic touched unrelated rig");
        }
    }

    private static ModelBone bone(String id)
    {
        ModelBone b=new ModelBone();
        b.id=id;
        return b;
    }

    private static void addCube(ModelBone bone,float x,float z,float minY,
            float maxY,float rotation,List<Vector3f> points,
            List<float[]> originals)
    {
        float width=.125F,depth=.03F;
        ModelCube cube=new ModelCube();
        cube.origin.set((x+width*.5F)*16F,minY*16F,
                (z+depth*.5F)*16F);
        cube.size.set(width*16F,(maxY-minY)*16F,depth*16F);
        cube.pivot.set(x*16F,((minY+maxY)*.5F)*16F,z*16F);
        cube.rotation.y=rotation;
        ModelQuad quad=new ModelQuad();
        for(int sx=0;sx<2;sx++)
            for(int sy=0;sy<2;sy++)
                for(int sz=0;sz<2;sz++)
                {
                    float vx=x+(sx==0?-width/2F:width/2F);
                    float vy=sy==0?minY:maxY;
                    float vz=z+(sz==0?-depth/2F:depth/2F);
                    ModelVertex vert=new ModelVertex(vx,vy,vz);
                    quad.vertices.add(vert);
                    points.add(vert.position);
                    originals.add(new float[]{vx,vy,vz});
                }
        cube.quads.add(quad);
        bone.cubes.add(cube);
    }

    private static float distance(Vector3f p,float[] q)
    {
        float x=p.x-q[0],y=p.y-q[1],z=p.z-q[2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }

    private static void require(boolean ok,String reason)
    {
        if(!ok)throw new AssertionError(reason);
    }
}

package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Verifies generic Chameleon cube extraction without a Forge runtime:
 * the rotated two-cube chest is captured, an incompatible third cube is
 * skipped, and simulated hair bones never become passive collider bodies.
 */
public final class JoltChameleonColliderCaptureSmoke
{
    private JoltChameleonColliderCaptureSmoke() {}

    public static class Vector
    {
        public float x,y,z;
        public Vector(float x,float y,float z)
        {
            this.x=x;this.y=y;this.z=z;
        }
    }

    public static class Vertex
    {
        public Vector position;
        public Vertex(float x,float y,float z)
        {
            position=new Vector(x,y,z);
        }
    }

    public static class Quad
    {
        public List<Vertex> vertices=new ArrayList<Vertex>();
        public Quad(float[] start,float[] size)
        {
            for(int i=0;i<8;i++)
                vertices.add(new Vertex(
                        (start[0]+(((i&1)==0)?0F:size[0]))/16F,
                        (start[1]+(((i&2)==0)?0F:size[1]))/16F,
                        (start[2]+(((i&4)==0)?0F:size[2]))/16F));
        }
    }

    public static class Cube
    {
        public Vector pivot=new Vector(-1F,7.15F,-3F);
        public Vector rotation=new Vector(45F,0F,0F);
        public List<Quad> quads=new ArrayList<Quad>();
        public Cube(float[] start,float[] size)
        {
            quads.add(new Quad(start,size));
        }
    }

    public static class MissingQuadCube
    {
        // A model-specific unsupported cube must not discard valid siblings.
    }

    public static class Pose
    {
        public Vector translate=new Vector(-1F,8.91777F,-1.93934F);
    }

    public static class Bone
    {
        public String id;
        public Pose initial=new Pose();
        public List<Object> cubes=new ArrayList<Object>();
        public List<Bone> children=new ArrayList<Bone>();
        public Bone(String id) {this.id=id;}
    }

    public static class Model
    {
        public List<Bone> bones=new ArrayList<Bone>();
    }

    public static class Wrapper
    {
        public Model model=new Model();
    }

    public static class ChameleonMorph
    {
        private final Wrapper wrapper=new Wrapper();
        public Wrapper getModel() {return wrapper;}
    }

    public static void main(String[] args)
    {
        final ChameleonMorph morph=new ChameleonMorph();
        Bone chest=new Bone("boobs");
        chest.cubes.add(new Cube(
                new float[]{-5.15F,5.9F,-2.75F},
                new float[]{8.3F,3.5F,3F}));
        chest.cubes.add(new MissingQuadCube());
        chest.cubes.add(new Cube(
                new float[]{-4.9F,6.15F,-2.5F},
                new float[]{7.8F,3F,3F}));
        Bone dynamic=new Bone("front_hair_1");
        dynamic.cubes.add(new Cube(
                new float[]{0F,1F,0F},
                new float[]{1F,2F,1F}));
        morph.getModel().model.bones.add(chest);
        morph.getModel().model.bones.add(dynamic);

        final float[] identity=BodyPartPhysicsMatrixSpace.identity();
        BodyPartPhysicsWorldPose.MatrixSource world=
                new BodyPartPhysicsWorldPose.MatrixSource()
                {
                    @Override public float[] getBoneWorldMatrix(String name)
                    {
                        return identity.clone();
                    }
                };
        BodyPartChameleonCollisionBoxes.Capture capture=
                BodyPartChameleonCollisionBoxes.capture(morph,world,
                        new HashSet<String>(Arrays.asList("front_hair_1")));
        if(capture.boxes.size()!=2 || capture.visitedCubes!=3 ||
                capture.rejectedCubes!=1 || capture.visitedBones!=2 ||
                capture.error!=null)
            throw new AssertionError("Chameleon collision capture counts "
                    +capture.boxes.size()+"/"+capture.visitedCubes+
                    "/"+capture.rejectedCubes+" "+capture.error);

        if(Math.abs(capture.boxes.get(0)[3])<0.37F)
            throw new AssertionError("Cube-local X45 was not preserved");
        if(BodyPartChameleonCollisionBoxes.collect(morph,world,
                Collections.<String>emptySet()).size()!=3)
            throw new AssertionError("Undriven hair cube should remain passive");
        System.out.println("Chameleon two-cube chest, unsupported cube and "
                +"same-model dynamic hair exclusion: PASS");
    }
}

package com.example.examplemod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure Java 8 stand-ins for ModelBone/ModelCube/ModelQuad/ModelVertex:
 * verifies real decoded-quads reflection with Chameleon's importer-space X
 * and independent cube pivot rotation, not a guessed Blockbench orientation.
 */
public final class JoltChameleonActualCubeSmoke
{
    public static final class V {
        public float x,y,z;
        public V(float x,float y,float z) {this.x=x;this.y=y;this.z=z;}
    }
    public static final class Transform {
        public V translate=new V(0F,12F,1.5F);
    }
    public static final class Vertex {
        public V position;
        public Vertex(float x,float y,float z) {position=new V(x,y,z);}
    }
    public static final class Quad {
        public List<Vertex> vertices=new ArrayList<Vertex>();
    }
    public static final class Cube {
        public V pivot=new V(0F,12F,1.5F);
        public V rotation=new V(0F,0F,0F);
        public List<Quad> quads=new ArrayList<Quad>();
    }
    public static final class Bone {
        public String id="back_strand_1";
        public Transform initial=new Transform();
        public List<Bone> children=new ArrayList<Bone>();
        public List<Cube> cubes=new ArrayList<Cube>();
    }
    public static final class Model {
        public List<Bone> bones=new ArrayList<Bone>();
    }
    public static final class ModelHolder {
        public Model model=new Model();
    }
    public static final class Morph {
        private final ModelHolder holder=new ModelHolder();
        public ModelHolder getModel() {return holder;}
    }
    private static void near(String label,float actual,float expected)
    {
        if(Math.abs(actual-expected)>0.001F)
            throw new AssertionError(label+": "+actual+" != "+expected);
    }

    private static Cube importedCube(float lowY,float highY)
    {
        Cube cube=new Cube();
        // After Chameleon's ModelParser X mirroring and ModelCube.generateQuads:
        // back strand has X=-4..4 pixels, Y=low..high, Z=1..2.
        // Faces are already decoded into final BLOCK-unit vertex positions.
        Quad q=new Quad();
        for(float x:new float[]{-4F,4F})
            for(float y:new float[]{lowY,highY})
                for(float z:new float[]{1F,2F})
                    q.vertices.add(new Vertex(x/16F,y/16F,z/16F));
        cube.quads.add(q);
        return cube;
    }

    public static void main(String[] args)
    {
        Morph morph=new Morph();
        Bone anchor=new Bone();
        anchor.id="anchor";
        Bone hair=new Bone();
        anchor.children.add(hair);
        morph.getModel().model.bones.add(anchor);

        Cube cube=importedCube(9.08F,12F);
        hair.cubes.add(cube);
        hair.cubes.add(importedCube(11F,12F)); // second, shorter hair accessory

        // In ChameleonPostRenderer the WORLD matrix includes +bone pivot.
        float[] world={1,0,0,0, 0,1,0,0, 0,0,1,0,
                0F,12F/16F,1.5F/16F,1F};
        BodyPartChameleonRenderedCubeProbe.Result first=
                BodyPartChameleonRenderedCubeProbe.inspect(
                        morph,"back_strand_1",world);
        if(first==null || first.cubeCount!=2 || first.vertexCount!=8)
            throw new AssertionError("Chameleon quads not discovered");
        near("Imported local cube down",first.measurement.cubeDown[1],-1F);
        near("Imported lower-tip world Y",first.measurement.cubeTipWorld[1],
                9.08F/16F);
        near("Imported cube-center world Y",first.measurement.cubeCenterWorld[1],
                (9.08F+12F)/32F);
        near("Rendered cube minY",first.measurement.minWorldY,9.08F/16F);

        // Independent cube X=180-degree rotation around its own pivot.
        // Bone pivots stay fixed: our probe must reveal upward CUBE geometry,
        // despite the bone axes still pointing down.
        cube.rotation.x=180F;
        BodyPartChameleonRenderedCubeProbe.Result flipped=
                BodyPartChameleonRenderedCubeProbe.inspect(
                        morph,"back_strand_1",world);
        if(flipped==null)
            throw new AssertionError("Post-rotation probe lost the cube");
        near("Rotated geometry up",flipped.measurement.cubeDown[1],1F);
        float mismatch=BodyPartJoltCubeGeometry.alignmentDegrees(
                flipped.measurement.cubeDown,new float[]{0,-1,0});
        if(mismatch<179F)
            throw new AssertionError("Cube inversion missed: "+mismatch);
        if(BodyPartChameleonRenderedCubeProbe.inspect(
                morph,"absent_bone",world)!=null)
            throw new AssertionError("Should not invent missing bones");

        System.out.println("Actual decoded Chameleon quads with pivot and X rotation: PASS");
    }
}

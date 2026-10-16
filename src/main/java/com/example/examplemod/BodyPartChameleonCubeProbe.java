package com.example.examplemod;

/**
 * Exact Blockbench cube dimensions for the supplied hair body.geo model.
 * Coordinates are Blockbench pixels; the native Chameleon bone world matrix
 * maps offsets relative to the bone pivot. These are model-specific reference
 * values, not a fallback for unrelated Chameleon models.
 */
public final class BodyPartChameleonCubeProbe
{
    public static final class Cube
    {
        public final String bone;
        public final float[] pivot, origin, size;
        public final float inflate;

        Cube(String bone, float[] pivot, float[] origin, float[] size,
                float inflate)
        {
            this.bone=bone;this.pivot=pivot;this.origin=origin;
            this.size=size;this.inflate=inflate;
        }

        /**
         * Collision witnesses on every cube face: four corners, four edge
         * midpoints and face center. This catches a torso surface cutting
         * through a wide face even if none of its eight corners is nearby.
         * Unlike pivot-only collision, offsets stay anchored to the cube.
         */
        public BodyPartChainSolver.Vec3[] faceSamples(float[] worldBone)
        {
            BodyPartChainSolver.Vec3[] samples=new BodyPartChainSolver.Vec3[54];
            int n=0;
            for(int axis=0;axis<3;axis++)
                for(int side=0;side<2;side++)
                    for(int a=0;a<3;a++)
                        for(int b=0;b<3;b++)
                        {
                            float[] factors=new float[]{.5F,.5F,.5F};
                            factors[axis]=side;
                            factors[(axis+1)%3]=a*.5F;
                            factors[(axis+2)%3]=b*.5F;
                            float x=(origin[0]-inflate+
                                    factors[0]*(size[0]+2F*inflate)-pivot[0])/16F;
                            float y=(origin[1]-inflate+
                                    factors[1]*(size[1]+2F*inflate)-pivot[1])/16F;
                            float z=(origin[2]-inflate+
                                    factors[2]*(size[2]+2F*inflate)-pivot[2])/16F;
                            samples[n++]=BodyPartPhysicsPoseAdapter.transformPoint(
                                    worldBone,new BodyPartChainSolver.Vec3(x,y,z));
                        }
            return samples;
        }

        /** World-space corners using the evaluated bone matrix and attachment. */
        public BodyPartChainSolver.Vec3[] corners(float[] worldBone)
        {
            BodyPartChainSolver.Vec3[] out=new BodyPartChainSolver.Vec3[8];
            for(int i=0;i<8;i++)
            {
                float x=(origin[0]+((i&1)!=0?size[0]:0F)
                        +((i&1)!=0?inflate:-inflate)-pivot[0])/16F;
                float y=(origin[1]+((i&2)!=0?size[1]:0F)
                        +((i&2)!=0?inflate:-inflate)-pivot[1])/16F;
                float z=(origin[2]+((i&4)!=0?size[2]:0F)
                        +((i&4)!=0?inflate:-inflate)-pivot[2])/16F;
                out[i]=BodyPartPhysicsPoseAdapter.transformPoint(worldBone,
                        new BodyPartChainSolver.Vec3(x,y,z));
            }
            return out;
        }
    }

    private static final Cube[] REFERENCE={
        cube("left_strand_1",-3.08F,12F,-1.58F,-4.08F,9F,-2.08F,2F,3F,1F,0F),
        cube("left_strand_2",-3.08F,9F,-2.08F,-4.08F,6F,-2.08F,2F,3F,1F,0F),
        cube("left_strand_3",-3.08F,6F,-2.08F,-4.08F,1F,-2.08F,2F,5F,1F,0F),
        cube("right_strand_1",3.08F,12F,-1.58F,2.08F,9F,-2.08F,2F,3F,1F,0F),
        cube("right_strand_2",3.08F,9F,-2.08F,2.08F,6F,-2.08F,2F,3F,1F,0F),
        cube("right_strand_3",3.08F,6F,-2.08F,2.08F,1F,-2.08F,2F,5F,1F,0F),
        cube("back_strand_1",0F,12F,1.5F,-4F,9.08F,1F,8F,2.92F,1F,.08F),
        cube("back_strand_2",0F,9F,2.08F,-4F,6.08F,1F,8F,2.84F,1F,.08F),
        cube("back_strand_3",0F,6F,2.08F,-4F,3.08F,1F,8F,2.84F,1F,.08F),
        cube("back_strand_4",0F,3F,2.08F,-4F,1F,1F,8F,1.92F,1F,.08F)
    };
    private static Cube cube(String name,float px,float py,float pz,
            float ox,float oy,float oz,float sx,float sy,float sz,float inflate)
    {
        return new Cube(name,new float[]{px,py,pz},new float[]{ox,oy,oz},
                new float[]{sx,sy,sz},inflate);
    }
    private BodyPartChameleonCubeProbe(){}
    public static Cube getReference(String bone)
    {
        for(Cube c:REFERENCE)if(c.bone.equals(bone))return c;
        return null;
    }
}

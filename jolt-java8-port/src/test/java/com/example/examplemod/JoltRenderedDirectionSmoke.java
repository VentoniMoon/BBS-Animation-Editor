package com.example.examplemod;

import java.util.LinkedHashMap;
import java.util.Map;

/** Regression for diagnostic geometry at 0/45/55/60/65/80-degree inclines. */
public final class JoltRenderedDirectionSmoke
{
    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.001F)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static float[] pose(float x, float y, float z, float[] rot3) {
        return new float[] {
            rot3[0],rot3[1],rot3[2],0,
            rot3[3],rot3[4],rot3[5],0,
            rot3[6],rot3[7],rot3[8],0,
            x,y,z,1
        };
    }

    private static float[] rotate(float[] m, float[] vec) {
        return new float[] {
            m[0]*vec[0]+m[3]*vec[1]+m[6]*vec[2],
            m[1]*vec[0]+m[4]*vec[1]+m[7]*vec[2],
            m[2]*vec[0]+m[5]*vec[1]+m[8]*vec[2]
        };
    }

    private static float[] basis(float angleX, float angleY) {
        return BodyPartPhysicsRotation.multiply3(
                BodyPartPhysicsRotation.eulerXYZ(0,angleY,0),
                BodyPartPhysicsRotation.eulerXYZ(angleX,0,0));
    }

    private static Map<String,float[]> twoSwings(float[] first,float[] second) {
        Map<String,float[]> result=new LinkedHashMap<String,float[]>();
        result.put("back_strand_1",first);
        result.put("back_strand_2",second);
        return result;
    }

    public static void main(String[] args) {
        final String[] bones={"back_strand_1","back_strand_2"};
        float[] id=BodyPartPhysicsRotation.eulerXYZ(0F,0F,0F);
        float[][] plain={pose(0,0,0,id),pose(0,-1,0,id)};
        float[] tip={0,-2,0};
        Map<String,float[]> corrected=BodyPartJoltPoseDiagnostic.expectedDirections(
                bones,plain,tip,twoSwings(
                        new float[]{0,0,1,(float)Math.PI/2},
                        new float[]{1,0,0,(float)Math.PI/2}));
        close(corrected.get(bones[0])[0],1F);
        close(corrected.get(bones[0])[1],0F);
        close(corrected.get(bones[1])[2],-1F);
        close(BodyPartJoltPoseDiagnostic.errorDegrees(
                corrected.get(bones[0]),new float[]{1,0,0}),0F);
        close(BodyPartJoltPoseDiagnostic.errorDegrees(
                corrected.get(bones[0]),new float[]{-1,0,0}),180F);

        // Non-zero attachment Y=180 is the known historical sign-inversion
        // risk in the real Chameleon model. The local corrective X swing
        // must NOT be blindly inverted for all attachment orientations.
        for(int degrees:new int[]{0,45,55,60,65,80}) {
            float rad=(float)Math.toRadians(degrees);
            for(int mirroredY:new int[]{0,180}) {
                float[] matrix=basis(rad,(float)Math.toRadians(mirroredY));
                float[] dir=rotate(matrix,new float[]{0,-1,0});
                float[][] world={pose(0,0,0,matrix),
                        pose(dir[0],dir[1],dir[2],matrix)};
                float[] end={2*dir[0],2*dir[1],2*dir[2]};
                float localCorrector=(mirroredY==0?-rad:rad);
                Map<String,float[]> predicted=BodyPartJoltPoseDiagnostic.expectedDirections(
                        bones,world,end,twoSwings(
                                new float[]{1,0,0,localCorrector},
                                new float[]{1,0,0,0}));
                close(BodyPartJoltPoseDiagnostic.errorDegrees(
                        predicted.get(bones[0]),new float[]{0,-1,0}),0F);
                close(BodyPartJoltPoseDiagnostic.errorDegrees(
                        predicted.get(bones[1]),new float[]{0,-1,0}),0F);
            }
        }
        System.out.println("Jolt rendered-direction math (0/45/55/60/65/80, Y=0/180): PASS");
    }
}

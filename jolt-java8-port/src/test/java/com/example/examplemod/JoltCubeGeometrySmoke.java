package com.example.examplemod;

/**
 * Horst geo reference fixture. Does NOT assume that a visually upward
 * strand is reversed: verify cube axis vs child-pivot axis under X/Y tilts.
 * Pure Java 8; no JNI, Forge or Chameleon runtime required.
 */
public final class JoltCubeGeometrySmoke
{
    private JoltCubeGeometrySmoke() {}

    private static void near(String label,float measured,float expected,float tolerance)
    {
        if(Math.abs(measured-expected)>tolerance)
            throw new AssertionError(label+": "+measured+" vs "+expected);
    }

    private static float[] worldMatrix(float[] basis,float[] translation)
    {
        return new float[] {
                basis[0],basis[1],basis[2],0F,
                basis[3],basis[4],basis[5],0F,
                basis[6],basis[7],basis[8],0F,
                translation[0],translation[1],translation[2],1F
        };
    }

    private static float[] rotate(float[] r,float x,float y,float z)
    {
        return new float[] {
                r[0]*x+r[3]*y+r[6]*z,
                r[1]*x+r[4]*y+r[7]*z,
                r[2]*x+r[5]*y+r[8]*z
        };
    }

    private static void check(String name, float[] pivotPx,
            float[] originPx,float[] sizePx,float inflatePx,
            float[] childDeltaPixels)
    {
        for(int yaw:new int[]{0,180})
            for(int incline:new int[]{0,45,55,60,65,80})
            {
                float[] basis=BodyPartPhysicsRotation.multiply3(
                        BodyPartPhysicsRotation.eulerXYZ(0F,
                                (float)Math.toRadians(yaw),0F),
                        BodyPartPhysicsRotation.eulerXYZ(
                                (float)Math.toRadians(incline),0F,0F));
                float[] translation={4F,2F,-3F};
                float[] nativeBone=worldMatrix(basis,translation);
                BodyPartJoltCubeGeometry.Measurement geometry =
                        BodyPartJoltCubeGeometry.measure(
                                nativeBone,pivotPx,originPx,sizePx,inflatePx);

                float[] childWorld=rotate(basis,childDeltaPixels[0]/16F,
                        childDeltaPixels[1]/16F,childDeltaPixels[2]/16F);
                float diff=BodyPartJoltCubeGeometry.alignmentDegrees(
                        geometry.cubeDown,childWorld);
                if(diff>15F)
                    throw new AssertionError(name+" cube/pivot inverted under X="+
                            incline+" Y="+yaw+", diff="+diff);
                float[] down=rotate(basis,0F,-1F,0F);
                near(name+" world cube X",geometry.cubeDown[0],down[0],1E-5F);
                near(name+" world cube Y",geometry.cubeDown[1],down[1],1E-5F);
                near(name+" world cube Z",geometry.cubeDown[2],down[2],1E-5F);

                float pivotTip=geometry.pivotToTipWorldY;
                float expectedTip=rotate(basis,
                        (originPx[0]+0.5F*sizePx[0]-pivotPx[0])/16F,
                        (originPx[1]-inflatePx-pivotPx[1])/16F,
                        (originPx[2]+0.5F*sizePx[2]-pivotPx[2])/16F)[1];
                near(name+" world tip Y",pivotTip,expectedTip,1E-5F);
                if(geometry.minWorldY>geometry.maxWorldY)
                    throw new AssertionError("Broken world cube bounds");
            }
        System.out.println("Horst reference cuboid "+name+
                " cube axis agrees with child-pivot direction at X=0..80, Y=0/180: PASS");
    }

    public static void main(String[] args)
    {
        // Exact reference values from BodyPartChameleonCubeProbe, in BB pixels.
        check("back_strand_1",
                new float[]{0F,12F,1.5F},new float[]{-4F,9.08F,1F},
                new float[]{8F,2.92F,1F},.08F,
                new float[]{0F,-3F,.58F});
        check("back_strand_2",
                new float[]{0F,9F,2.08F},new float[]{-4F,6.08F,1F},
                new float[]{8F,2.84F,1F},.08F,
                new float[]{0F,-3F,0F});
        check("left_strand_1",
                new float[]{-3.08F,12F,-1.58F},
                new float[]{-4.08F,9F,-2.08F},
                new float[]{2F,3F,1F},0F,
                new float[]{0F,-3F,-.5F});
        check("right_strand_1",
                new float[]{3.08F,12F,-1.58F},
                new float[]{2.08F,9F,-2.08F},
                new float[]{2F,3F,1F},0F,
                new float[]{0F,-3F,-.5F});

        // A knowingly inverted mesh should be caught, not silently 'fixed'
        // by replacing axis signs in Jolt: cube is rotated 180 degrees
        // while the child pivot remains downward.
        float[] flipped=worldMatrix(
                BodyPartPhysicsRotation.eulerXYZ(0F,0F,(float)Math.PI),
                new float[]{0F,0F,0F});
        BodyPartJoltCubeGeometry.Measurement bad=
                BodyPartJoltCubeGeometry.measure(flipped,
                        new float[]{0F,12F,1.5F},
                        new float[]{-4F,9.08F,1F},
                        new float[]{8F,2.92F,1F},.08F);
        float reverse=BodyPartJoltCubeGeometry.alignmentDegrees(
                bad.cubeDown,new float[]{0F,-3F,.58F});
        if(reverse<160F)
            throw new AssertionError("Failed to detect a reversed cuboid: "+reverse);
        System.out.println("Reference cuboid reversal detector: PASS");
    }
}

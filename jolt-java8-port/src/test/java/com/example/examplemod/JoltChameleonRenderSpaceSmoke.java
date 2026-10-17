package com.example.examplemod;

/**
 * Demonstrates why raw Jolt, inferred pivots, and the actual screen can
 * disagree if we omit ChameleonMorph's +180° yaw. Chameleon's nested
 * MorphBodyPart zeroes entity yaw; ChameleonMorph.render rotates 180°.
 */
public final class JoltChameleonRenderSpaceSmoke
{
    public static final class DummyMorph {
        public float getScale(float partialTicks) {return 1.5F;}
    }

    private static void near(String label,float measured,float target,float tolerance) {
        if(Math.abs(measured-target)>tolerance)
            throw new AssertionError(label+" "+measured+" expected "+target);
    }

    private static float[] transformDirection(float[] matrix,float[] vector) {
        return new float[] {
                matrix[0]*vector[0]+matrix[4]*vector[1]+matrix[8]*vector[2],
                matrix[1]*vector[0]+matrix[5]*vector[1]+matrix[9]*vector[2],
                matrix[2]*vector[0]+matrix[6]*vector[1]+matrix[10]*vector[2]
        };
    }

    public static void main(String[] args) {
        float[] identity=BodyPartPhysicsMatrixSpace.identity();
        float[] nested=BodyPartChameleonChildRenderSpace.withChildRender(identity,1F);
        near("Child yaw X",-1F,nested[0],1E-6F);
        near("Child yaw Y",1F,nested[5],1E-6F);
        near("Child yaw Z",-1F,nested[10],1E-6F);
        float[] scaled=BodyPartChameleonChildRenderSpace.withChildRender(identity,2F);
        near("scale X",-2F,scaled[0],1E-6F);
        near("scale Y",2F,scaled[5],1E-6F);
        near("scale Z",-2F,scaled[10],1E-6F);
        near("reflection getScale",1.5F,
                BodyPartChameleonChildRenderSpace.renderScale(new DummyMorph(),0.5F),1E-6F);

        // Reproduce the actual 80-degree incline and last logged strand
        // direction raw=(0,-.75,+.66). Omitting the inner Chameleon Y180
        // makes this WORLD vector point DOWN, but the actual cube renderer's
        // Y180 reverses Z and makes the visually drawn strand point UP.
        final float angle=(float)Math.toRadians(80D);
        float[] rx=BodyPartPhysicsMatrixSpace.identity();
        rx[5]=(float)Math.cos(angle);
        rx[6]=(float)Math.sin(angle);
        rx[9]=-(float)Math.sin(angle);
        rx[10]=(float)Math.cos(angle);
        float[] rest={0F,-0.75F,0.66F};
        float wrongY=transformDirection(rx,rest)[1];
        float actualY=transformDirection(
                BodyPartChameleonChildRenderSpace.withChildRender(rx,1F),rest)[1];
        if(!(wrongY < -0.7F && actualY > 0.5F))
            throw new AssertionError("Missing yaw flip not reproduced: "+
                    wrongY+" -> "+actualY);
        System.out.println("Chameleon Y180 on X80: old Y="+wrongY+
                ", actual Y="+actualY+"; false down-vector diagnosis detected");

        try {
            BodyPartChameleonChildRenderSpace.withChildRender(identity,-1F);
            throw new AssertionError("Negative scale unexpectedly accepted");
        } catch(IllegalArgumentException expected) { }
        System.out.println("Nested Chameleon render transform/scale on Java 8: PASS");
    }
}

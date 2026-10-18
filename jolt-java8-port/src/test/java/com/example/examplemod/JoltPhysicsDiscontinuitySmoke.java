package com.example.examplemod;

public final class JoltPhysicsDiscontinuitySmoke
{
    private JoltPhysicsDiscontinuitySmoke() {}

    private static float[] at(float x,float y,float z)
    {
        float[] m=BodyPartPhysicsMatrixSpace.identity();
        m[12]=x;m[13]=y;m[14]=z;
        return m;
    }

    private static void kind(JoltPhysicsDiscontinuity.Kind actual,
            JoltPhysicsDiscontinuity.Kind wanted)
    {
        if(actual!=wanted)throw new AssertionError(actual+" != "+wanted);
    }

    public static void main(String[] args)
    {
        float[] zero={0,1.7F,0};
        float[] near={1.3F,1.7F,0.4F};
        float[] far={7,1.7F,0};
        float[] a=at(0,0,0);
        float[] b=BodyPartJoltActorSpace.worldFromActor(0,0,0,90F);
        float[] c=BodyPartJoltActorSpace.worldFromActor(0,0,0,180F);
        nearAngle(JoltPhysicsDiscontinuity.angularDistance(a,b),
                (float)(Math.PI/2D));
        kind(JoltPhysicsDiscontinuity.classify(5,6,zero,near,a,b,3F,
                        (float)Math.toRadians(155)),
                JoltPhysicsDiscontinuity.Kind.CONTINUOUS);
        kind(JoltPhysicsDiscontinuity.classify(5,6,zero,far,a,a,3F,
                        (float)Math.toRadians(155)),
                JoltPhysicsDiscontinuity.Kind.TELEPORT_TRANSLATION);
        kind(JoltPhysicsDiscontinuity.classify(5,6,zero,zero,a,c,3F,
                        (float)Math.toRadians(155)),
                JoltPhysicsDiscontinuity.Kind.TELEPORT_ROTATION);
        kind(JoltPhysicsDiscontinuity.classify(5,20,zero,zero,a,a,3F,
                        (float)Math.toRadians(155)),
                JoltPhysicsDiscontinuity.Kind.GAP);
        kind(JoltPhysicsDiscontinuity.classify(5,5,zero,zero,a,a,3F,
                        (float)Math.toRadians(155)),
                JoltPhysicsDiscontinuity.Kind.GAP);
        // Positive nonuniform scale should not cause artificial angular impulses
        float[] stretched=a.clone();
        stretched[0]*=2F;stretched[5]*=0.5F;stretched[10]*=1.3F;
        nearAngle(JoltPhysicsDiscontinuity.angularDistance(a,stretched),0F);
        System.out.println("Jolt genuine travel vs teleports / yaw changes / frame gaps: PASS");
    }

    private static void nearAngle(float got,float expected)
    {
        if(Math.abs(got-expected)>0.0005F)
            throw new AssertionError("Angular distance "+got+" != "+expected);
    }
}

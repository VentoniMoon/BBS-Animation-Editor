package com.example.examplemod;

/**
 * Actor-world frame shared by Jolt rig, kinematic contacts and debug drawing.
 *
 * Native Emoticons bone matrices are model-local. The entity renderer then
 * applies a Y rotation of (360 - renderYawOffset) degrees and translates the
 * actor. Feeding model-local bones to Jolt loses the actor's translation
 * velocity, so a free strand responds to bending its parent bone but not to
 * walking the actor around the scene.
 *
 * Apply this rigid transform ONCE, to *all* Jolt inputs, before simulation:
 * bone pivots, parent anchor, terminal tip, torso and passive BodyPart boxes.
 * Keep the visual Chameleon pose writer local; world yaw is cancelled when
 * native rotations are expressed relative to the transformed parent frame.
 *
 * Do not mix these world-space Jolt coordinates with the old GUI overlay's
 * actor-origin translation. This class is intentionally pure Java 8.
 */
public final class BodyPartJoltActorSpace
{
    private BodyPartJoltActorSpace() {}

    public static float[] worldFromActor(double x,double y,double z,
            float renderYawDegrees)
    {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
                !Float.isFinite(renderYawDegrees))
            throw new IllegalArgumentException("Invalid actor transform");
        double yaw=Math.toRadians(-renderYawDegrees);
        float c=(float)Math.cos(yaw),s=(float)Math.sin(yaw);
        float[] m=BodyPartPhysicsMatrixSpace.identity();
        m[0]=c; m[2]=-s;
        m[8]=s; m[10]=c;
        m[12]=(float)x;m[13]=(float)y;m[14]=(float)z;
        return m;
    }

    public static float[] matrix(float[] worldFromActor,float[] actorLocal)
    {
        return BodyPartPhysicsMatrixSpace.multiply(worldFromActor,actorLocal);
    }

    public static float[] point(float[] worldFromActor,float[] actorLocalPoint)
    {
        if(actorLocalPoint==null||actorLocalPoint.length!=3)
            throw new IllegalArgumentException("Expected actor-local xyz");
        return new float[]{
                worldFromActor[0]*actorLocalPoint[0]+
                    worldFromActor[4]*actorLocalPoint[1]+
                    worldFromActor[8]*actorLocalPoint[2]+worldFromActor[12],
                worldFromActor[1]*actorLocalPoint[0]+
                    worldFromActor[5]*actorLocalPoint[1]+
                    worldFromActor[9]*actorLocalPoint[2]+worldFromActor[13],
                worldFromActor[2]*actorLocalPoint[0]+
                    worldFromActor[6]*actorLocalPoint[1]+
                    worldFromActor[10]*actorLocalPoint[2]+worldFromActor[14]
        };
    }

    /** Apply actor yaw to a 10-float oriented box as well as its center.
     * quaternion = actorYaw * cubeOrientation, never the reverse.
     */
    public static float[] box(float[] worldFromActor,float[] localBox)
    {
        if(localBox==null||localBox.length!=10)
            throw new IllegalArgumentException("Expected 10-float box");
        float[] result=localBox.clone();
        float[] center=point(worldFromActor,
                new float[]{localBox[0],localBox[1],localBox[2]});
        System.arraycopy(center,0,result,0,3);
        // For the worldFromActor frame, the only rotation is yaw.
        float actorY=(float)Math.atan2(worldFromActor[8],worldFromActor[0]);
        float qy=(float)Math.sin(actorY*0.5F);
        float qw=(float)Math.cos(actorY*0.5F);
        float x=localBox[3],y=localBox[4],z=localBox[5],w=localBox[6];
        result[3]=qw*x+qy*z;
        result[4]=qw*y+qy*w;
        result[5]=qw*z-qy*x;
        result[6]=qw*w-qy*y;
        return result;
    }
}

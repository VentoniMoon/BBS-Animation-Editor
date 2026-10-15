package com.example.examplemod;

import mchorse.metamorph.bodypart.BodyPart;

/** Exact order used by Metamorph BodyPart.render(): T * Rz * Ry * Rx * S. */
public final class BodyPartPhysicsAttachment
{
    private BodyPartPhysicsAttachment() {}

    public static float[] localMatrix(BodyPart part)
    {
        if (part == null) throw new IllegalArgumentException("Missing BodyPart");
        float[] m = BodyPartPhysicsMatrixSpace.identity();
        m[12] = part.translate.x;
        m[13] = part.translate.y;
        m[14] = part.translate.z;
        m = BodyPartPhysicsMatrixSpace.multiply(m, rotationZ(part.rotate.z));
        m = BodyPartPhysicsMatrixSpace.multiply(m, rotationY(part.rotate.y));
        m = BodyPartPhysicsMatrixSpace.multiply(m, rotationX(part.rotate.x));
        float[] scale = BodyPartPhysicsMatrixSpace.identity();
        scale[0] = part.scale.x;
        scale[5] = part.scale.y;
        scale[10] = part.scale.z;
        return BodyPartPhysicsMatrixSpace.multiply(m, scale);
    }

    public static float[] worldMatrix(float[] parent, BodyPart part)
    {
        return BodyPartPhysicsMatrixSpace.multiply(parent, localMatrix(part));
    }

    private static float[] rotationX(float degrees)
    {
        float[] m = BodyPartPhysicsMatrixSpace.identity();
        float c=(float)Math.cos(Math.toRadians(degrees));
        float s=(float)Math.sin(Math.toRadians(degrees));
        m[5]=c; m[6]=s; m[9]=-s; m[10]=c;
        return m;
    }

    private static float[] rotationY(float degrees)
    {
        float[] m = BodyPartPhysicsMatrixSpace.identity();
        float c=(float)Math.cos(Math.toRadians(degrees));
        float s=(float)Math.sin(Math.toRadians(degrees));
        m[0]=c; m[2]=-s; m[8]=s; m[10]=c;
        return m;
    }

    private static float[] rotationZ(float degrees)
    {
        float[] m = BodyPartPhysicsMatrixSpace.identity();
        float c=(float)Math.cos(Math.toRadians(degrees));
        float s=(float)Math.sin(Math.toRadians(degrees));
        m[0]=c; m[1]=s; m[4]=-s; m[5]=c;
        return m;
    }
}

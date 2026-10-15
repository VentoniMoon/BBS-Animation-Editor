package com.example.examplemod;

/**
 * Explicit matrix composition for physics attachment space.
 *
 * All matrices use OpenGL column-major layout. The caller must supply the
 * *animated* native matrices from the render pass:
 *
 * worldFromChild = worldFromAttachment * attachmentFromChild.
 *
 * worldFromAttachment already includes the parent actor's animated anchor,
 * target low_body transform and Metamorph BodyPart translation/rotation/scale.
 * This helper does not guess those values from Euler angle signs.
 */
public final class BodyPartPhysicsMatrixSpace
{
    private BodyPartPhysicsMatrixSpace() {}

    public static float[] identity()
    {
        float[] out = new float[16];
        out[0] = out[5] = out[10] = out[15] = 1F;
        return out;
    }

    /** Multiply A * B, transforming B's points into A's parent space. */
    public static float[] multiply(float[] a, float[] b)
    {
        require(a);
        require(b);
        float[] result = new float[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
            {
                float value = 0F;
                for (int k = 0; k < 4; k++)
                    value += a[k * 4 + row] * b[col * 4 + k];
                result[col * 4 + row] = value;
            }
        return result;
    }

    /**
     * Combine world-space attachment frame with a native Chameleon
     * child-bone matrix evaluated relative to the attachment.
     */
    public static float[] childWorld(float[] worldFromAttachment,
                                     float[] attachmentFromBone)
    {
        return multiply(worldFromAttachment, attachmentFromBone);
    }

    /** Obtain a defensive copy, never retaining OpenGL's mutable buffer. */
    public static float[] snapshot(float[] current)
    {
        require(current);
        float[] copy = new float[16];
        System.arraycopy(current, 0, copy, 0, 16);
        return copy;
    }

    private static void require(float[] m)
    {
        if (m == null || m.length != 16)
            throw new IllegalArgumentException("Expected 4x4 transform");
        for (float value : m)
            if (Float.isNaN(value) || Float.isInfinite(value))
                throw new IllegalArgumentException("Non-finite bone transform");
    }
}

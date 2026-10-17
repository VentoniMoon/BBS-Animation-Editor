package com.example.examplemod;

/**
 * Mirrors the extra render transform applied by a nested ChameleonMorph.
 *
 * Metamorph's BodyPart.render() zeroes entity.renderYawOffset before calling
 * morph.render(), and ChameleonMorph.render() performs:
 *
 *   scale(getScale(partialTicks)); rotateY(-renderYawOffset + 180 degrees)
 *
 * AFTER the BodyPart's own translation, rotations and scale. The native
 * ChameleonRenderer.postRender() bone matrices do NOT contain those outer
 * transforms because the editor calls postRender() from identity.
 *
 * This transform is ONLY for the optional Jolt child-space A/B path. Do not
 * alter the legacy Verlet solver or the editor's ordinary rendering.
 */
public final class BodyPartChameleonChildRenderSpace
{
    private BodyPartChameleonChildRenderSpace() {}

    /** Compose M_bodyPart * S_child * R_y(180°) in GL column-major space. */
    public static float[] withChildRender(float[] worldFromPart,
            float childScale)
    {
        if(!Float.isFinite(childScale) || childScale<=0F)
            throw new IllegalArgumentException("Invalid Chameleon render scale");
        float[] scale=BodyPartPhysicsMatrixSpace.identity();
        scale[0]=scale[5]=scale[10]=childScale;
        float[] yaw180=BodyPartPhysicsMatrixSpace.identity();
        yaw180[0]=-1F;
        yaw180[10]=-1F;
        return BodyPartPhysicsMatrixSpace.multiply(
                BodyPartPhysicsMatrixSpace.multiply(worldFromPart,scale),
                yaw180);
    }

    /**
     * Read the scale from the actual morph, not a guessed editor setting.
     * ChameleonMorph#getScale is public in the 1.12.2 renderer.
     */
    public static float renderScale(Object chameleonMorph,float partialTicks)
    {
        if(chameleonMorph==null || !Float.isFinite(partialTicks))
            throw new IllegalArgumentException("Invalid Chameleon morph/partial tick");
        try
        {
            Object value=chameleonMorph.getClass()
                    .getMethod("getScale",float.class)
                    .invoke(chameleonMorph,partialTicks);
            if(!(value instanceof Number))
                throw new IllegalArgumentException("Invalid morph scale value");
            float scale=((Number)value).floatValue();
            if(!Float.isFinite(scale) || scale<=0F)
                throw new IllegalArgumentException("Non-positive morph scale");
            return scale;
        }
        catch(ReflectiveOperationException error)
        {
            throw new IllegalArgumentException("Chameleon render scale unavailable",error);
        }
    }
}

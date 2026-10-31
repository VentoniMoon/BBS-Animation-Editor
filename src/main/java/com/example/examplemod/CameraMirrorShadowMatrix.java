package com.example.examplemod;

/**
 * Adjusts a shadow-map transform when the GBuffer is rendered from a
 * different camera than the one that produced OptiFine's shadow textures.
 *
 * BSL uses:
 *     relativeWorld = inverse(gbufferModelView) * viewPosition
 *     shadowCoord   = shadowModelView * relativeWorld
 *
 * The native shadow map is rendered relative to the MAIN camera. A mirror
 * reconstructs positions relative to the REFLECTED camera. Therefore:
 *
 *     shadowModelViewMirror =
 *         shadowModelViewMain * translate(reflectedEye - mainEye)
 *
 * For any world point W, this guarantees:
 *   shadowMirror * (W-reflectedEye) == shadowMain * (W-mainEye).
 *
 * The shadow projection and shadow-map textures remain those of the main
 * OptiFine pass. No extra shadow render or second world is needed.
 */
public final class CameraMirrorShadowMatrix
{
    private CameraMirrorShadowMatrix(){}

    /**
     * Multiplies a column-major 4x4 matrix M by a right-hand translation.
     * Input is not modified. Uses double intermediates to avoid accumulating
     * unnecessary precision loss when the two eyes are far apart.
     */
    public static float[] rebase(float[] original,
            double dx,double dy,double dz)
    {
        if(original==null||original.length!=16)
            throw new IllegalArgumentException("Expected 4x4 shadow matrix");
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||
                !Double.isFinite(dz))
            throw new IllegalArgumentException("Non-finite camera displacement");
        float[] out=original.clone();
        for(int row=0;row<4;row++)
            out[12+row]=(float)(original[row]*dx+
                    original[4+row]*dy+
                    original[8+row]*dz+
                    original[12+row]);
        return out;
    }
}

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
    /**
     * OptiFine's setCameraShadow() builds an orthographic light matrix
     * ending in translate((float)eye % shadowIntervalSize - interval/2).
     * Reusing that MAIN-eye translation for a PRIVATE mirror shadow map
     * makes the shadow texture slide when the main camera moves.
     *
     * Preserve its sun rotation and replace ONLY the main-eye snapped
     * translation with the reflected-eye snapped translation. Both
     * rasterization and lookup must use the returned matrix.
     */
    public static float[] retargetOrthographicSnap(float[] nativeView,
            double mainX,double mainY,double mainZ,
            double mirrorX,double mirrorY,double mirrorZ,
            float interval)
    {
        if(!(interval>0F)||!Float.isFinite(interval))
            throw new IllegalArgumentException("Invalid OptiFine shadow interval");
        return rebase(nativeView,
                ((float)mirrorX % interval)-((float)mainX % interval),
                ((float)mirrorY % interval)-((float)mainY % interval),
                ((float)mirrorZ % interval)-((float)mainZ % interval));
    }

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

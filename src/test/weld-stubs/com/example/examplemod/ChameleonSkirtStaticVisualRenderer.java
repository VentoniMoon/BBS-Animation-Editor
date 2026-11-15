package com.example.examplemod;

/**
 * Test-only stub: full Forge build compiles the real Minecraft BufferBuilder
 * implementation, while Java 8 Weld hook tests only verify scope wiring.
 */
public final class ChameleonSkirtStaticVisualRenderer
{
    private ChameleonSkirtStaticVisualRenderer(){}
    public static void draw(SkirtClothRestBinding.Binding skin,
            SkirtClothSurfaceMesh.Snapshot source,float[][] props)
    {
        // No GL dependencies in the standalone bytecode/Weld smoke tests.
    }
}

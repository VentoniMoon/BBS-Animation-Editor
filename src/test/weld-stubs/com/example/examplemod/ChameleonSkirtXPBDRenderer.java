package com.example.examplemod;

/** Test-only replacement for the Minecraft/FML GL renderer.
 * Standalone Java 8 Weld hook tests exercise bytecode and scope cleanup.
 * The real class is compiled by full Forge CI instead. */
public final class ChameleonSkirtXPBDRenderer
{
    private ChameleonSkirtXPBDRenderer(){}
    public static boolean draw(SkirtXPBDRuntime.RenderFrame frame)
    {return frame!=null;}
}

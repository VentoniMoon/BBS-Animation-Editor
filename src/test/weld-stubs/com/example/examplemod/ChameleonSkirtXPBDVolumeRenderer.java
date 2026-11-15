package com.example.examplemod;

/** Standalone Java 8 Weld tests do not need Minecraft GL classes.
 * Production renderer is compiled in the full Forge CI. */
public final class ChameleonSkirtXPBDVolumeRenderer
{
    private ChameleonSkirtXPBDVolumeRenderer(){}
    public static boolean draw(SkirtXPBDVolumeSkin.Mesh mesh,
            SkirtXPBDRuntime.RenderFrame frame)
    {return mesh!=null&&frame!=null;}
}

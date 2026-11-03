package com.example.examplemod;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("BBS Animation Editor Shader Curves")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({
        "com.example.examplemod.CameraShaderCurveCorePlugin",
        "com.example.examplemod.CameraShaderCurveTransformer",
        "com.example.examplemod.LegacyForgeEventIsolationTransformer",
        "com.example.examplemod.ForgeNetworkSideGuardTransformer",
        "com.example.examplemod.DevIBlockStateSrgBridgeTransformer"})
public final class CameraShaderCurveCorePlugin implements IFMLLoadingPlugin
{
    private static volatile boolean loaded;
    public CameraShaderCurveCorePlugin(){loaded=true;}
    /** True only if Forge registered the core plugin at launch. */
    public static boolean registeredAtLaunch(){return loaded;}

    @Override public String[] getASMTransformerClass()
    {
        List<String> transformers = new ArrayList<String>();
        transformers.add("com.example.examplemod.CameraShaderCurveTransformer");
        if ("true".equalsIgnoreCase(System.getProperty(
                "bbs.editor.forgeEventIsolation", "false")))
        {
            transformers.add("com.example.examplemod.LegacyForgeEventIsolationTransformer");
        }
        if ("true".equalsIgnoreCase(System.getProperty(
                "bbs.editor.forgeNetworkSideGuard", "false")))
        {
            transformers.add("com.example.examplemod.ForgeNetworkSideGuardTransformer");
        }
        if ("true".equalsIgnoreCase(System.getProperty(
                "bbs.editor.mcpBlockStateSrgBridge", "false")))
        {
            transformers.add("com.example.examplemod.DevIBlockStateSrgBridgeTransformer");
        }
        return transformers.toArray(new String[transformers.size()]);
    }
    @Override public String getModContainerClass(){return null;}
    @Override public String getSetupClass(){return null;}
    @Override public void injectData(Map<String,Object> data){}
    @Override public String getAccessTransformerClass(){return null;}
}

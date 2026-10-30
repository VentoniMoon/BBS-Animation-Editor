package com.example.examplemod;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("BBS Animation Editor Shader Curves")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({
        "com.example.examplemod.CameraShaderCurveCorePlugin",
        "com.example.examplemod.CameraShaderCurveTransformer"})
public final class CameraShaderCurveCorePlugin implements IFMLLoadingPlugin
{
    @Override public String[] getASMTransformerClass()
    {
        return new String[]{
                "com.example.examplemod.CameraShaderCurveTransformer"};
    }
    @Override public String getModContainerClass(){return null;}
    @Override public String getSetupClass(){return null;}
    @Override public void injectData(Map<String,Object> data){}
    @Override public String getAccessTransformerClass(){return null;}
}

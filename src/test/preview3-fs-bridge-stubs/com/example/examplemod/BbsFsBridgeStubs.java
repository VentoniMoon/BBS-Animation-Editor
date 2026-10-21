package com.example.examplemod;
class PreviewRenderBackend {
    private final BbsFsPreviewTarget target;
    PreviewRenderBackend(net.minecraft.client.Minecraft mc) {
       target=new BbsFsPreviewTarget(mc);
    }
    BbsFsPreviewTarget getNativeTarget(){return target;}
}
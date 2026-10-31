package com.example.examplemod;
class PreviewRenderBackend {
    private final BbsFsPreviewTarget target;
    PreviewRenderBackend(net.minecraft.client.Minecraft mc) {
       target=new BbsFsPreviewTarget(mc);
    }
    BbsFsPreviewTarget getNativeTarget(){return target;}
}
/** Test-only surrogate. The real compositor is validated by Forge compile. */
class CameraMirrorLateComposite {
    static void composite(net.minecraft.client.Minecraft mc) {}
}

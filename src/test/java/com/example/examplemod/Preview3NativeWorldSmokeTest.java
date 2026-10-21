package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.client.shader.Framebuffer;

/** Compiles the opt-in full Minecraft world rendering bridge on Java 8. */
public final class Preview3NativeWorldSmokeTest
{
    private static void require(boolean ok,String message)
    {
        if(!ok)throw new AssertionError(message);
    }

    public static void main(String[] args)
    {
        System.setProperty("bbs.editor.preview3.nativeWorld","true");
        Minecraft mc=new Minecraft();
        Framebuffer gameFbo=mc.getFramebuffer();
        Entity player=mc.getRenderViewEntity();
        PreviewRenderBackend backend=new PreviewRenderBackend();
        EditorCamera editorCamera=new EditorCamera();
        editorCamera.setFov(48F);
        editorCamera.setRoll(15F);

        require(PreviewNativeWorldBridge.isRequested(),"experiment opt-in ignored");
        PreviewNativeWorldBridge.toggleRequested();
        require(!PreviewNativeWorldBridge.isRequested(),"could not switch Native OFF");
        require(!PreviewNativeWorldBridge.render(mc,editorCamera,backend),
                "OFF path still re-rendered the world");
        PreviewNativeWorldBridge.toggleRequested();
        require(PreviewNativeWorldBridge.isRequested(),"could not switch Native ON");
        boolean drawn=PreviewNativeWorldBridge.render(mc,editorCamera,backend);
        require(drawn,"native renderer did not start");
        require(mc.entityRenderer.draws==1,"native world not rendered");
        require(mc.getFramebuffer()==gameFbo,"real framebuffer leaked");
        require(mc.getRenderViewEntity()==player,"preview camera leaked");
        require(mc.gameSettings.fovSetting==70F,"real FOV leaked");
        require(backend.shaders.preview.bound,"preview FBO not rebound");

        mc.entityRenderer.fail=true;
        boolean recovered=PreviewNativeWorldBridge.render(mc,editorCamera,backend);
        require(!recovered,"failed renderer reported success");
        require(mc.getFramebuffer()==gameFbo,"real framebuffer leaked on failure");
        require(mc.getRenderViewEntity()==player,"view entity leaked on failure");
        require(mc.gameSettings.fovSetting==70F,"FOV leaked on failure");

        mc.entityRenderer.fail=false;
        require(!PreviewNativeWorldBridge.render(mc,editorCamera,backend),
                "failed native path should remain disabled this session");
        require(mc.entityRenderer.draws==2,"native renderer unexpectedly retried");
        System.out.println("Preview 3.0 native world FBO, camera and FOV restoration PASS");
    }
}

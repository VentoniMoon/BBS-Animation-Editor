package com.example.examplemod;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Optional Preview 3.0 experiment: render the *actual* Minecraft world
 * pipeline (EntityRenderer.renderWorld) into the editor's FBO.
 *
 * IMPORTANT: no default activation. OptiFine packs and other coremods may
 * redirect the game's framebuffer or entity render targets. The working
 * RenderGlobal split-world backend stays the fallback until verified in
 * the user's real 1.12.2 stack.
 */
public final class PreviewNativeWorldBridge
{
    private static final boolean REQUESTED=Boolean.getBoolean(
            "bbs.editor.preview3.nativeWorld");
    private static final PreviewNativeWorldBridge INSTANCE=
            new PreviewNativeWorldBridge();
    private static boolean handlerInstalled;

    private PreviewCameraEntity cameraEntity;
    private boolean active;
    private boolean disabledForSession;
    private boolean reported;
    private PreviewCameraPose currentPose;

    private PreviewNativeWorldBridge() {}

    public static boolean isRequested() { return REQUESTED; }

    public static boolean render(Minecraft mc,EditorCamera camera,
            PreviewRenderBackend backend)
    {
        if(!REQUESTED || mc==null || camera==null || backend==null ||
                mc.world==null)return false;
        if(!handlerInstalled)
        {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            handlerInstalled=true;
        }
        return INSTANCE.drawWorld(mc,camera.sample(),backend);
    }

    private boolean drawWorld(Minecraft mc,PreviewCameraPose pose,
            PreviewRenderBackend backend)
    {
        if(active || disabledForSession)return false;
        Framebuffer preview=backend.getShaderBridge().getPreviewFramebuffer();
        if(preview==null)return false;

        Field framebuffer=null;
        Framebuffer oldFramebuffer=null;
        Entity oldCamera=mc.getRenderViewEntity();
        float oldFov=mc.gameSettings.fovSetting;
        boolean swapped=false;
        try
        {
            if(cameraEntity==null || cameraEntity.world!=mc.world)
                cameraEntity=new PreviewCameraEntity(mc.world);
            // Editor yaw=0 looks towards -Z, vanilla yaw=180 towards -Z.
            cameraEntity.updateCamera(
                    pose.x,pose.y,pose.z,pose.yaw+180F,-pose.pitch);

            framebuffer=resolveMinecraftFramebuffer();
            if(framebuffer==null)throw new IllegalStateException(
                    "Minecraft framebufferMc field unavailable");
            oldFramebuffer=(Framebuffer)framebuffer.get(mc);
            if(oldFramebuffer==null)throw new IllegalStateException(
                    "Missing original Minecraft framebuffer");

            this.currentPose=pose;
            this.active=true;
            // The standard world renderer reads Minecraft.framebufferMc and
            // Minecraft.renderViewEntity. Both MUST be rolled back together.
            framebuffer.set(mc,preview);
            swapped=true;
            mc.setRenderViewEntity(cameraEntity);
            mc.gameSettings.fovSetting=pose.fov;
            preview.bindFramebuffer(true);
            mc.entityRenderer.renderWorld(mc.getRenderPartialTicks(),
                    System.nanoTime());
            preview.bindFramebuffer(false);
            if(!reported)
            {
                reported=true;
                System.out.println(
                        "[BBS Editor][Preview3] Native Minecraft world pass active "+
                        "(experimental; requires visual depth/shader check)");
            }
            return true;
        }
        catch(Throwable problem)
        {
            disabledForSession=true;
            System.err.println(
                    "[BBS Editor][Preview3] Native world pass disabled; "+
                    "using previous renderer. "+problem.getClass().getSimpleName()+
                    ": "+problem.getMessage());
            return false;
        }
        finally
        {
            // Never allow the preview camera, FOV or framebuffer to leak into
            // the real game's next frame, including on OptiFine exceptions.
            try { if(oldCamera!=null) mc.setRenderViewEntity(oldCamera); }
            finally
            {
                mc.gameSettings.fovSetting=oldFov;
                if(swapped && framebuffer!=null)
                {
                    try {framebuffer.set(mc,oldFramebuffer);}
                    catch(IllegalAccessException ignored){}
                }
                currentPose=null;
                active=false;
                // Rebind Preview for our temporary editor actors/depth passes.
                try { preview.bindFramebuffer(true); }
                catch(Throwable ignored){}
            }
        }
    }

    private static Field resolveMinecraftFramebuffer()
    {
        for(String name:new String[]{"framebufferMc","field_147124_at"})
        {
            try
            {
                Field f=Minecraft.class.getDeclaredField(name);
                f.setAccessible(true);
                if(Framebuffer.class.isAssignableFrom(f.getType()))return f;
            }
            catch(NoSuchFieldException ignored){}
        }
        return null;
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event)
    {
        if(active && currentPose!=null)
            event.setFOV(currentPose.fov);
    }

    @SubscribeEvent
    public void onCameraSetup(EntityViewRenderEvent.CameraSetup event)
    {
        if(active && currentPose!=null)
            event.setRoll(-currentPose.roll);
    }
}

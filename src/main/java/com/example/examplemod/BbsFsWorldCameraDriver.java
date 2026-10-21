package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Camera half of the BBS FS-style viewport.
 *
 * BBS FS 1.12.2 drives a camera entity BEFORE the vanilla Minecraft world
 * frame, using ForgeCameraHandler. We do the same instead of invoking
 * EntityRenderer.renderWorld recursively from GuiScreen.drawScreen.
 *
 * This is intentionally independent of Blockbuster recordings / Jolt.
 * Camera ownership is released on screen close or world switch.
 */
public final class BbsFsWorldCameraDriver
{
    private static final BbsFsWorldCameraDriver INSTANCE =
            new BbsFsWorldCameraDriver();

    private EditorSceneViewport viewport;
    private PreviewCameraEntity view;
    private Entity previousView;
    private World previousWorld;
    private int previousPerspective;
    private boolean previousHideGUI;
    private boolean hidHudThisFrame;
    private boolean registered;
    private boolean controlling;
    private PreviewCameraPose activePose;

    private BbsFsWorldCameraDriver() {}

    public static void attach(EditorSceneViewport viewport)
    {
        if (viewport == null) return;
        BbsFsWorldCameraDriver driver = INSTANCE;
        if (!driver.registered)
        {
            MinecraftForge.EVENT_BUS.register(driver);
            FMLCommonHandler.instance().bus().register(driver);
            driver.registered = true;
        }
        if (driver.viewport != viewport)
        {
            driver.release();
            driver.viewport = viewport;
        }
    }

    public static void detach(EditorSceneViewport viewport)
    {
        if (INSTANCE.viewport != viewport) return;
        INSTANCE.release();
        INSTANCE.viewport = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void renderTick(TickEvent.RenderTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            restoreHud();
            return;
        }
        if (event.phase != TickEvent.Phase.START) return;

        BbsFsWorldProjection.beginFrame();
        Minecraft mc = Minecraft.getMinecraft();
        if (viewport == null || !(mc.currentScreen instanceof AnimationEditorScreen)
                || mc.world == null || mc.player == null)
        {
            release();
            return;
        }

        if (previousWorld != null && previousWorld != mc.world)
            release();

        PreviewCameraPose pose = viewport.getCamera().sample();
        if (view == null || view.world != mc.world)
            view = new PreviewCameraEntity(mc.world);

        view.updateCamera(pose.x, pose.y, pose.z,
                pose.yaw + 180F, -pose.pitch);

        if (!controlling)
        {
            previousView = mc.getRenderViewEntity();
            previousPerspective = mc.gameSettings.thirdPersonView;
            previousWorld = mc.world;
            controlling = true;
        }

        /*
         * The normal Minecraft world frame is captured into the film
         * texture. Its hotbar/crosshair/debug overlay must NOT end up in
         * that texture. Suppress only the in-game HUD for this render tick;
         * GuiScreen and its editor panels remain visible.
         */
        if (!hidHudThisFrame)
        {
            previousHideGUI = mc.gameSettings.hideGUI;
            hidHudThisFrame = true;
        }
        mc.gameSettings.hideGUI = true;

        activePose = pose;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(view);
    }

    /**
     * Camera and shaderpack projection must come from the actual Minecraft
     * world render, not an approximation reconstructed in GuiScreen.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void nativeWorldLast(RenderWorldLastEvent event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (controlling && viewport != null &&
                mc.currentScreen instanceof AnimationEditorScreen &&
                mc.getRenderViewEntity() == view)
            BbsFsWorldProjection.capture(mc.displayWidth, mc.displayHeight);
    }

    @SubscribeEvent
    public void cameraSetup(EntityViewRenderEvent.CameraSetup event)
    {
        if (controlling && activePose != null &&
                Minecraft.getMinecraft().getRenderViewEntity() == view)
            event.setRoll(-activePose.roll);
    }

    @SubscribeEvent
    public void cameraFov(EntityViewRenderEvent.FOVModifier event)
    {
        if (controlling && activePose != null &&
                Minecraft.getMinecraft().getRenderViewEntity() == view)
        {
            /*
             * The native game renders at display aspect. Our film viewport
             * center-crops that frame. When the viewport is wider than the
             * screen, vertical cropping narrows the resulting effective
             * FOV; compensate here so the displayed view and actor/gizmo
             * projection agree on the editor's original FOV.
             */
            float adjusted = activePose.fov;
            if (viewport != null && viewport.getHeight() > 0)
            {
                Minecraft mc = Minecraft.getMinecraft();
                double screenAspect = (double) mc.displayWidth /
                        Math.max(1, mc.displayHeight);
                double previewAspect = (double) viewport.getWidth() /
                        viewport.getHeight();
                if (previewAspect > screenAspect)
                {
                    double tan = Math.tan(Math.toRadians(adjusted) * 0.5D);
                    adjusted = (float) Math.toDegrees(
                            2D * Math.atan(tan *
                                    (previewAspect / screenAspect)));
                    adjusted = Math.min(170F, adjusted);
                }
            }
            event.setFOV(adjusted);
        }
    }

    private void restoreHud()
    {
        if (!hidHudThisFrame) return;
        Minecraft mc = Minecraft.getMinecraft();
        mc.gameSettings.hideGUI = previousHideGUI;
        hidHudThisFrame = false;
    }

    private void release()
    {
        restoreHud();
        if (!controlling) return;

        Minecraft mc = Minecraft.getMinecraft();
        try
        {
            if (mc.getRenderViewEntity() == view)
            {
                Entity restore = previousView;
                if (restore == null || restore.world != mc.world)
                    restore = mc.player;
                if (restore != null)
                    mc.setRenderViewEntity(restore);
            }
            mc.gameSettings.thirdPersonView = previousPerspective;
        }
        finally
        {
            controlling = false;
            previousWorld = null;
            previousView = null;
            activePose = null;
        }
    }
}

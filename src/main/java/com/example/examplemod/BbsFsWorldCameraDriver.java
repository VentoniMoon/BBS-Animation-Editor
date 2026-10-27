package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
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
    private boolean previousViewBobbing;
    private boolean hidHudThisFrame;
    private boolean registered;
    private boolean controlling;
    private PreviewCameraPose activePose;

    /**
     * The exact camera used for the native Minecraft world frame.
     * Recorded actors must use this same pose, not the editor's FREE camera.
     */
    public static PreviewCameraPose nativeActorPose(EditorSceneViewport requested)
    {
        BbsFsWorldCameraDriver d=INSTANCE;
        return d.controlling&&d.viewport==requested?d.activePose:null;
    }

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
            // Fail-safe: never leave an export-size world target active after
            // a skipped/failed GUI pass.
            CameraVisualFrameClock.clearPreview();
            CameraBetterLightsCameraOrigin.end();
            CameraNativeFrameProbe.afterRenderTick(Minecraft.getMinecraft());
            Minecraft endMc=Minecraft.getMinecraft();
            if(endMc.currentScreen instanceof AnimationEditorScreen)
                ((AnimationEditorScreen)endMc.currentScreen)
                        .getCameraVideoExport().afterWorld();
            restoreHud();
            return;
        }
        if (event.phase != TickEvent.Phase.START) return;

        BbsFsWorldProjection.beginFrame();
        BbsFsWorldFrameCapture.beginNativeFrame();
        BbsFsNativeActorPass.beginFrame();
        Minecraft mc = Minecraft.getMinecraft();
        if (viewport == null || !(mc.currentScreen instanceof AnimationEditorScreen)
                || mc.world == null || mc.player == null)
        {
            release();
            return;
        }

        if (previousWorld != null && previousWorld != mc.world)
            release();

        AnimationEditorScreen editor=(AnimationEditorScreen)mc.currentScreen;
        // Offline VIDEO (when explicitly started) owns each exact scene
        // tick BEFORE the native camera entity and recorded actor pass sample
        // their poses. Normal CAMERA preview remains completely unchanged.
        editor.getCameraVideoExport().beforeWorld(editor);
        BodyPartPhysicsBakeController bake=editor.getPhysicsBakeController();
        CameraVisualFrameClock.beginPreview(editor.getCurrentFrame(),
                event.renderTickTime,editor.isCameraPlaying(),
                bake!=null&&bake.isRecording());
        PreviewCameraPose pose=editor.getCameraWorkspaceNativePose();
        if (view == null || view.world != mc.world)
            view = new PreviewCameraEntity(mc.world);

        view.updateCamera(pose.x, pose.y, pose.z,
                BbsFsCameraConventions.entityYaw(pose.yaw),
                BbsFsCameraConventions.entityPitch(pose.pitch));

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
            previousViewBobbing = mc.gameSettings.viewBobbing;
            hidHudThisFrame = true;
        }
        mc.gameSettings.hideGUI = true;
        /*
         * A fixed cinematic camera cannot inherit Minecraft's player
         * walking head-bob: our gizmo/record actor camera has no such bob,
         * so restoring this state at RenderTick END keeps depth in sync.
         */
        mc.gameSettings.viewBobbing = false;

        activePose = pose;
        mc.gameSettings.thirdPersonView = 0;
        mc.setRenderViewEntity(view);
        // BetterLights' shader offset currently assumes Minecraft.player
        // is the render viewpoint. CAMERA's detached entity is not that
        // player; temporarily match their interpolated render positions,
        // without changing the actual LightCaster world coordinates.
        CameraBetterLightsCameraOrigin.begin(mc,view);

        // The editor's light state MUST be uploaded before vanilla/OptiFine
        // render the world. RenderWorldLast is too late for shader lighting,
        // and historically left actor and CAMERA lights one frame behind.
        BetterLightsWorldEngine.prepare(mc,editor);

        // Normally a no-op. The one-frame diagnostic is guarded by an
        // explicit JVM flag and uses the native Minecraft world pass.
        CameraNativeFrameProbe.beforeWorld(mc);
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
        {
            net.minecraft.client.shader.Framebuffer source = mc.getFramebuffer();
            if (source != null)
            {
                BbsFsWorldFrameCapture.markNativeWorldRendered();

                /*
                 * OptiFine may render to its own internal framebuffer,
                 * possibly at a different shader resolution than
                 * Minecraft.framebufferMc. Capture the ACTUAL active world
                 * viewport dimensions; the final composited frame will be
                 * copied from Minecraft's resolved buffer later.
                 */
                java.nio.IntBuffer glViewport =
                        org.lwjgl.BufferUtils.createIntBuffer(16);
                org.lwjgl.opengl.GL11.glGetInteger(
                        org.lwjgl.opengl.GL11.GL_VIEWPORT, glViewport);
                int worldWidth = Math.max(1, glViewport.get(2));
                int worldHeight = Math.max(1, glViewport.get(3));
                CameraNativeFrameProbe.noteNativeWorld(
                        worldWidth,worldHeight,source);
                BbsFsWorldProjection.capture(worldWidth, worldHeight);

                /*
                 * Experimental next phase: recorded actors participate in
                 * the native world pass BEFORE BBS FS captures its texture.
                 * Never recursively render the world or touch the GUI FBO.
                 */
                BbsFsNativeActorPass.render(mc,
                        (AnimationEditorScreen) mc.currentScreen,
                        viewport, event.getPartialTicks(),
                        worldWidth, worldHeight);
            }
        }
    }

    /**
     * The detached editor camera sees the real Minecraft world, but the
     * local player is not a scene actor and must not enter the film frame.
     * RenderPlayerEvent.Pre is cancellable; do not mutate player invisibility
     * or remove them from the world (both alter gameplay and recordings).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hideLocalPlayer(RenderPlayerEvent.Pre event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if (controlling && mc.currentScreen instanceof AnimationEditorScreen
                && mc.getRenderViewEntity() == view
                && event.getEntityPlayer() == mc.player)
            event.setCanceled(true);
    }

    /**
     * Every CAMERA preview and offline MP4 is copied from the NATIVE
     * Minecraft framebuffer, before the editor UI. hideGUI alone is not
     * enough: Forge renders chat (including OptiFine's OpenGL error text)
     * through a dedicated RenderGameOverlayEvent.Chat even while a GuiScreen
     * is open, and other mods may bypass the hideGUI hint.
     *
     * Drop the entire in-game HUD pass, not the messages themselves.
     * CAMERA's own SUBTITLE film clips are composited independently AFTER
     * this world snapshot and must remain visible. The guard restricts
     * suppression to the current editor-controlled world render, and the
     * original GUI setting is restored at RenderTick.END/on close.
     */
    private boolean isCapturingCleanFilm()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return hidHudThisFrame && controlling && view != null
                && mc.currentScreen instanceof AnimationEditorScreen
                && mc.getRenderViewEntity() == view && mc.world != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void suppressNativeHud(RenderGameOverlayEvent.Pre event)
    {
        if (isCapturingCleanFilm())
        {
            // ALL cancels GuiIngameForge's HUD entry point, including chat,
            // debug strings, notification overlays and the vanilla crosshair.
            // Cancel nested events too if another renderer calls them alone.
            event.setCanceled(true);
        }
    }

    /** GuiIngameForge sends Chat as its own cancellable Pre subclass. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void suppressNativeChat(RenderGameOverlayEvent.Chat event)
    {
        if (isCapturingCleanFilm()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void cameraSetup(EntityViewRenderEvent.CameraSetup event)
    {
        if (controlling && activePose != null &&
                Minecraft.getMinecraft().getRenderViewEntity() == view)
        {
            /*
             * Set all three orientation axes explicitly, just as BBS FS
             * ForgeCameraHandler does. The editor's yaw convention differs
             * from Minecraft's entity rotation: the default native event
             * yaw can otherwise mirror actors/gizmo relative to chunks.
             */
            event.setYaw(
                    BbsFsCameraConventions.cameraSetupYaw(activePose.yaw));
            event.setPitch(
                    BbsFsCameraConventions.cameraSetupPitch(activePose.pitch));
            event.setRoll(
                    BbsFsCameraConventions.cameraSetupRoll(activePose.roll));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
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
                net.minecraft.client.shader.Framebuffer world =
                        mc.getFramebuffer();
                double screenAspect = world != null
                        ? (double) world.framebufferWidth /
                                Math.max(1, world.framebufferHeight)
                        : (double) mc.displayWidth /
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
        mc.gameSettings.viewBobbing = previousViewBobbing;
        hidHudThisFrame = false;
    }

    private void release()
    {
        CameraBetterLightsCameraOrigin.end();
        CameraSceneLightsRenderer.reset();
        CameraVisualFrameClock.clearPreview();
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

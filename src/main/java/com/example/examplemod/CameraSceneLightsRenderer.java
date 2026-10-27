package com.example.examplemod;

import net.minecraft.client.Minecraft;

/**
 * Compatibility facade for the retired CAMERA-specific light actor renderer.
 * All actor and CAMERA lights are owned by BetterLightsWorldEngine BEFORE
 * the world renders; constructing a detached Blockbuster actor here again
 * would double-register and update the same source at the wrong stage.
 */
@Deprecated
public final class CameraSceneLightsRenderer
{
    /** Disposes the same unified sources on viewport/editor teardown. */
    public static void reset()
    {
        BetterLightsWorldEngine.reset();
    }

    /** Legacy RenderWorldLast hook; intentionally no source mutation. */
    public static void render(Minecraft mc,EditorSceneViewport viewport,
            CameraSceneLights lights)
    {
        // Intentionally empty. See BbsFsWorldCameraDriver.renderTick START.
    }

    private CameraSceneLightsRenderer(){}
}

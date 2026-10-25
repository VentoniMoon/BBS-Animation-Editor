package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;

/**
 * BetterLights 1.0.3 OptiFine shader uniform compatibility.
 *
 * The BetterLights OptifineShaderUniformsHandler subtracts
 * Minecraft.player.{prevPosX,posX,...} from every world-space LightCaster
 * position before multiplying by its camera view matrix. That works in
 * standard first-person gameplay, but not when CAMERA switches render
 * view to a separate PreviewCameraEntity. Static lights then drift as
 * CAMERA moves because shaders subtract the PLAYER rather than the VIEW.
 *
 * Only while the native CAMERA world/shader pass is active, mirror the
 * detached camera's interpolated position into the player fields read by
 * BetterLights. Restore the exact originals BEFORE GUI and in the render
 * tick END / scene-detach fallbacks. Do not call Entity.setPosition:
 * changing the player's world registration, bounding box, or chunks is
 * neither required nor safe.
 *
 * There are no BetterLights binary references; missing optional mod is a
 * complete no-op. This is a render-time compatibility shim, not a
 * gameplay teleport or a transformation of LightCaster positions.
 */
public final class CameraBetterLightsCameraOrigin
{
    private static Entity savedPlayer;
    private static double x,y,z,prevX,prevY,prevZ,lastX,lastY,lastZ;
    private static boolean active;

    public static void begin(Minecraft mc,Entity camera)
    {
        // A failed/aborted frame must never leave stale player fields.
        end();
        if(mc==null||camera==null||mc.player==null||
                camera==mc.player||mc.getRenderViewEntity()!=camera||
                !Loader.isModLoaded("better_lights"))
            return;

        Entity player=mc.player;
        savedPlayer=player;
        x=player.posX;y=player.posY;z=player.posZ;
        prevX=player.prevPosX;prevY=player.prevPosY;prevZ=player.prevPosZ;
        lastX=player.lastTickPosX;lastY=player.lastTickPosY;
        lastZ=player.lastTickPosZ;
        active=true;
        // BetterLights shader calculations use the MC player's
        // interpolated XYZ origin, not the active camera entity. Mirror the
        // view origin for this one world pass; camera pose is already
        // sampled at the exact output-frame time in PreviewCameraEntity.
        player.posX=camera.posX;
        player.posY=camera.posY;
        player.posZ=camera.posZ;
        player.prevPosX=camera.prevPosX;
        player.prevPosY=camera.prevPosY;
        player.prevPosZ=camera.prevPosZ;
        player.lastTickPosX=camera.lastTickPosX;
        player.lastTickPosY=camera.lastTickPosY;
        player.lastTickPosZ=camera.lastTickPosZ;
    }

    /** Idempotent; safe on successful capture, cancellation and close. */
    public static void end()
    {
        if(!active)return;
        Entity player=savedPlayer;
        savedPlayer=null;
        active=false;
        if(player==null)return;
        player.posX=x;player.posY=y;player.posZ=z;
        player.prevPosX=prevX;player.prevPosY=prevY;player.prevPosZ=prevZ;
        player.lastTickPosX=lastX;
        player.lastTickPosY=lastY;
        player.lastTickPosZ=lastZ;
    }

    private CameraBetterLightsCameraOrigin(){}
}

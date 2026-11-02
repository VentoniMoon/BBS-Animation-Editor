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
    private static MirrorScope currentMirror;
    private static boolean mirrorReported;

    /**
     * Nested override for the auxiliary reflected pass.
     *
     * A native CAMERA frame already spoofs Minecraft.player to the
     * MAIN camera. During a mirror draw, BetterLights shaders must
     * instead subtract the REFLECTED camera. It is not enough to move
     * PreviewCameraEntity: BetterLights reads Minecraft.player.
     *
     * Restore to the previously-spoofed MAIN camera, not to the real
     * player's gameplay position. The outer render-tick END performs
     * that final restoration.
     */
    public static final class MirrorScope implements AutoCloseable
    {
        private final Entity player;
        private final double[] before;
        private boolean closed;

        private MirrorScope(Entity player)
        {
            this.player=player;
            before=player==null?null:new double[]{
                    player.posX,player.posY,player.posZ,
                    player.prevPosX,player.prevPosY,player.prevPosZ,
                    player.lastTickPosX,player.lastTickPosY,
                    player.lastTickPosZ
            };
        }

        @Override public void close()
        {
            if(closed)return;
            closed=true;
            if(player==null||currentMirror!=this)return;
            write(player,before);
            currentMirror=null;
        }
    }

    private static void write(Entity entity,double[] pos)
    {
        entity.posX=pos[0];entity.posY=pos[1];entity.posZ=pos[2];
        entity.prevPosX=pos[3];entity.prevPosY=pos[4];
        entity.prevPosZ=pos[5];
        entity.lastTickPosX=pos[6];
        entity.lastTickPosY=pos[7];entity.lastTickPosZ=pos[8];
    }

    /**
     * Keep BetterLights' player-derived coordinate origin aligned with the
     * mirror for the ENTIRE auxiliary render, including the private BSL
     * deferred/composite/final stages which run AFTER terrain/actors.
     *
     * The detached preview entity is moved later by the world callback;
     * the immutable reflected pose is already available before BSL starts.
     * All position history fields are set to the same pose so BetterLights
     * interpolation cannot blend main and reflected camera coordinates.
     */
    public static MirrorScope beginMirror(Minecraft mc,
            PreviewCameraPose reflected)
    {
        if(reflected==null)return new MirrorScope(null);
        return beginMirrorCoordinates(mc,new double[]{
                reflected.x,reflected.y,reflected.z,
                reflected.x,reflected.y,reflected.z,
                reflected.x,reflected.y,reflected.z});
    }

    public static MirrorScope beginMirror(Minecraft mc,Entity reflected)
    {
        if(reflected==null||mc==null||
                mc.getRenderViewEntity()!=reflected)
            return new MirrorScope(null);
        return beginMirrorCoordinates(mc,new double[]{
                reflected.posX,reflected.posY,reflected.posZ,
                reflected.prevPosX,reflected.prevPosY,reflected.prevPosZ,
                reflected.lastTickPosX,reflected.lastTickPosY,
                reflected.lastTickPosZ});
    }

    private static MirrorScope beginMirrorCoordinates(Minecraft mc,
            double[] pose)
    {
        if(!active||mc==null||pose==null||
                mc.player!=savedPlayer||
                mc.getRenderViewEntity()==null||
                mc.getRenderViewEntity()==mc.player||
                !Loader.isModLoaded("better_lights")||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLLightOrigin","true")))
            return new MirrorScope(null);
        if(currentMirror!=null)
            throw new IllegalStateException(
                    "Recursive CAMERA mirror BetterLights light origin");
        MirrorScope scope=new MirrorScope(mc.player);
        currentMirror=scope;
        write(mc.player,pose);
        if(!mirrorReported)
        {
            mirrorReported=true;
            System.out.println("[BBS Editor][Mirror][BetterLights] "+
                    "Reflected light origin applied; main="+
                    scope.before[0]+","+scope.before[1]+","+
                    scope.before[2]+" reflected="+pose[0]+","+
                    pose[1]+","+pose[2]+
                    " (restored to main camera after mirror)");
        }
        return scope;
    }

    private static boolean compositeOriginReported;

    /**
     * Verify that the player-derived BetterLights origin is still the
     * reflected eye AFTER the world callback and BEFORE BSL composite.
     * Only reports state, never changes the world or a GLSL uniform.
     */
    public static void verifyBeforeComposite(Minecraft mc,
            PreviewCameraPose reflected)
    {
        if(compositeOriginReported||!active||mc==null||
                mc.player!=savedPlayer||reflected==null||
                !Loader.isModLoaded("better_lights"))return;
        compositeOriginReported=true;
        Entity player=mc.player;
        boolean correct=currentMirror!=null&&
                Math.abs(player.posX-reflected.x)<0.001D&&
                Math.abs(player.posY-reflected.y)<0.001D&&
                Math.abs(player.posZ-reflected.z)<0.001D&&
                Math.abs(player.prevPosX-reflected.x)<0.001D&&
                Math.abs(player.prevPosY-reflected.y)<0.001D&&
                Math.abs(player.prevPosZ-reflected.z)<0.001D;
        System.out.println("[BBS Editor][Mirror][BetterLights] "+
                "BSL composite reflected origin held="+correct+
                " player="+player.posX+","+player.posY+","+player.posZ+
                " expected="+reflected.x+","+reflected.y+","+
                reflected.z+" (main origin restored after capture)");
    }

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
        // An aborted main frame must unwind both scopes. In particular
        // the reflected origin must never leak into gameplay state.
        if(currentMirror!=null)currentMirror.close();
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

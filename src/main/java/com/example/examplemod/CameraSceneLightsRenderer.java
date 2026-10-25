package com.example.examplemod;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;

/**
 * CAMERA scene sources are internally rendered as ephemeral BetterLights
 * morphs. They are NOT added to the Blockbuster scene's actor list or
 * written to any record .dat.
 *
 * This reuses the working BetterLightsMorph -> LightCaster registration
 * and OptiFine native world actor pass instead of guessing undocumented
 * BetterLights manager APIs. A renderer stays associated with each UUID
 * to avoid recreating temporary casters every film frame.
 */
public final class CameraSceneLightsRenderer
{
    private static final class Slot
    {
        final BlockbusterActorPreviewRenderer renderer=
                new BlockbusterActorPreviewRenderer();
        final BlockbusterSceneActor scene=new BlockbusterSceneActor();
        final BlockbusterSceneActorData actor=
                new BlockbusterSceneActorData(scene);
        Slot(CameraSceneLights.Light light)
        {
            scene.setId("camera-light-"+light.id);
            scene.setName(light.name);
        }
    }
    private static final Map<String,Slot> slots=new HashMap<String,Slot>();
    private static CameraSceneLights current;
    private static net.minecraft.world.World world;
    private static boolean warned;

    public static void reset()
    {
        // Existing BetterLights temporary casters expire through the
        // Blockbuster morph lifecycle once the preview-only entities stop
        // updating. No generated source is a real Minecraft entity.
        slots.clear();current=null;world=null;
    }

    public static void render(Minecraft mc,EditorSceneViewport viewport,
            CameraSceneLights lights)
    {
        if(mc==null||mc.world==null||viewport==null||lights==null||
                !BbsFsNativeActorPass.isRendering()||
                !Loader.isModLoaded("better_lights"))
        {
            if(lights==null||mc==null||mc.world==null)reset();
            return;
        }
        if(current!=lights||world!=mc.world)
        {
            reset();current=lights;world=mc.world;
        }
        java.util.HashSet<String> retained=new java.util.HashSet<String>();
        for(CameraSceneLights.Light l:lights.all())
        {
            retained.add(l.id);
            if(!l.enabled)continue;
            Slot slot=slots.get(l.id);
            if(slot==null){slot=new Slot(l);slots.put(l.id,slot);}
            slot.scene.setName(l.name);
            NBTTagCompound tag=l.morph.copy();
            tag.setString("Name","betterLights");
            tag.setBoolean("EnableAlways",true);
            slot.scene.setMorph(tag);
            BlockbusterRecordFrame frame=new BlockbusterRecordFrame();
            frame.setX(l.x);frame.setY(l.y);frame.setZ(l.z);
            double dx=l.targetX-l.x,dy=l.targetY-l.y,dz=l.targetZ-l.z;
            double flat=Math.sqrt(dx*dx+dz*dz);
            if(dx*dx+dy*dy+dz*dz<1E-10D){dz=1D;flat=1D;}
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
            float pitch=(float)Math.toDegrees(Math.atan2(-dy,flat));
            frame.setYaw(yaw);frame.setPitch(pitch);
            frame.setYawHead(yaw);frame.setBodyYaw(yaw);
            frame.setHasBodyYaw(true);
            slot.renderer.drawActor(mc,slot.actor,frame,
                    viewport.getCameraForPreviewInteractions(),
                    0,0,BbsFsNativeActorPass.worldViewportWidth(),
                    BbsFsNativeActorPass.worldViewportHeight());
        }
        // Drop removed editor-only lights rather than pinning their morphs
        // indefinitely in the Java session.
        slots.keySet().retainAll(retained);
    }
    private CameraSceneLightsRenderer(){}
}

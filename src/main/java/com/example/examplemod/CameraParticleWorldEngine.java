package com.example.examplemod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;

/**
 * One logical emission pass per Minecraft client tick. Never spawn in
 * drawScreen, native mirror/shadow passes, or each rendered video frame.
 * Existing vanilla particles are owned and animated by the MC world.
 */
public final class CameraParticleWorldEngine
{
    private static final class Clock
    {
        final Random random=new Random();
        int elapsed;
        int lastSceneFrame=Integer.MIN_VALUE;
    }
    private final Map<String,Clock> actors=new HashMap<String,Clock>();
    private final Map<String,Clock> statics=new HashMap<String,Clock>();
    private WorldClient lastWorld;
    private EditorSceneState lastScene;
    private CameraParticleWorldEngine(){}
    private static final CameraParticleWorldEngine INSTANCE=
            new CameraParticleWorldEngine();
    public static void tick(AnimationEditorScreen screen)
    {
        INSTANCE.update(screen);
    }
    public static void reset()
    {
        INSTANCE.actors.clear();INSTANCE.statics.clear();
        INSTANCE.lastWorld=null;INSTANCE.lastScene=null;
    }
    private void update(AnimationEditorScreen screen)
    {
        if(screen==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null||mc.world==null||screen.getSceneState()==null)
            return;
        WorldClient world=mc.world;
        EditorSceneState scene=screen.getSceneState();
        if(lastWorld!=world||lastScene!=scene)
        {
            actors.clear();statics.clear();
            lastWorld=world;lastScene=scene;
        }
        for(CameraSceneParticles.Source source:
                scene.getCameraParticles().all())
        {
            if(!source.enabled)continue;
            Clock clock=statics.get(source.id);
            if(clock==null)
            {
                clock=new Clock();statics.put(source.id,clock);
            }
            ParticleEmitterConfig.emit(world,source.morph,
                    source.x,source.y,source.z,source.yaw,
                    clock.random,clock.elapsed++);
        }
        if(!screen.isCameraPlaying())return;
        int frame=screen.getCurrentFrame();
        for(BlockbusterSceneActorData actor:scene.getActors())
        {
            if(!ParticleActorRig.isParticleActor(actor))continue;
            BlockbusterRecord record=actor.getRecord();
            if(record==null)continue;
            BlockbusterRecordFrame position=record.getFrame(frame);
            if(position==null)continue;
            Clock clock=actors.get(actor.getId());
            if(clock==null)
            {
                clock=new Clock();actors.put(actor.getId(),clock);
            }
            if(clock.lastSceneFrame==frame)continue;
            if(frame<clock.lastSceneFrame||
                    frame-clock.lastSceneFrame>2)
                clock.elapsed=0;
            clock.lastSceneFrame=frame;
            ActorAnimationData rig=ParticleActorRig.forActor(actor);
            NBTTagCompound settings=ParticleActorRig.settingsAt(rig,frame,
                    actor.getActor().getMorph());
            float[] offset=ParticleActorRig.offset(rig,frame);
            ParticleEmitterConfig.emit(world,settings,
                    position.getX()+offset[0],
                    position.getY()+offset[1],
                    position.getZ()+offset[2],
                    ParticleActorRig.yaw(rig,frame),
                    clock.random,clock.elapsed++);
        }
    }
}

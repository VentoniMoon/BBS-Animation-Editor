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
    private int lastSceneIndex=Integer.MIN_VALUE;
    private boolean reportedPausedParticleTick;
    private CameraParticleWorldEngine(){}
    private static final CameraParticleWorldEngine INSTANCE=
            new CameraParticleWorldEngine();

    public static void tick(AnimationEditorScreen screen)
    {
        INSTANCE.update(screen);

        /*
         * Vanilla ParticleManager.spawnEffectParticle() only QUEUES the
         * newly-created particle. Minecraft.runTick() normally flushes that
         * queue and updates live particles via effectRenderer.updateEffects().
         * In single-player the editor is a pausing GuiScreen, so vanilla
         * SKIPS updateEffects() while the GUI is open. Every emitter then
         * appears invisible until the user closes the editor, at which
         * point the queued particles suddenly become visible for a moment.
         *
         * The editor already advances its particle emitters once from
         * GuiScreen.updateScreen() (20 TPS). Drain/advance the client-only
         * particle manager here exactly once per GUI tick while Minecraft
         * is paused, without unpausing the world, changing actor physics,
         * advancing the timeline twice or rendering any extra world pass.
         *
         * When the world is NOT paused (multiplayer / unpaused GUI), vanilla
         * runs updateEffects() itself later in Minecraft.runTick(). Never
         * double-update those particles.
         */
        Minecraft mc=Minecraft.getMinecraft();
        if(screen!=null&&mc!=null&&mc.currentScreen==screen&&
                mc.world!=null&&mc.effectRenderer!=null&&mc.isGamePaused())
        {
            mc.effectRenderer.updateEffects();
            if(!INSTANCE.reportedPausedParticleTick&&
                    (!INSTANCE.statics.isEmpty()||
                     !INSTANCE.actors.isEmpty()))
            {
                INSTANCE.reportedPausedParticleTick=true;
                System.out.println("[BBS Editor][Particles] Paused world: "+
                        "advancing vanilla particle queue in editor "+
                        "GUI tick (20 TPS); native renderer unchanged.");
            }
        }
    }

    public static void reset()
    {
        INSTANCE.actors.clear();INSTANCE.statics.clear();
        INSTANCE.lastWorld=null;INSTANCE.lastScene=null;
        INSTANCE.lastSceneIndex=Integer.MIN_VALUE;
        INSTANCE.reportedPausedParticleTick=false;
    }
    private void update(AnimationEditorScreen screen)
    {
        if(screen==null)return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc==null||mc.world==null||screen.getSceneState()==null)
            return;
        WorldClient world=mc.world;
        EditorSceneState scene=screen.getSceneState();
        if(lastWorld!=world||lastScene!=scene||
                lastSceneIndex!=scene.getSelectedScene())
        {
            actors.clear();statics.clear();
            lastWorld=world;lastScene=scene;
            lastSceneIndex=scene.getSelectedScene();
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
                    source.x,source.y,source.z,
                    source.yaw,source.pitch,source.roll,
                    clock.random,clock.elapsed++);
        }
        if(!screen.isCameraPlaying())return;
        int frame=screen.getCurrentFrame();
        for(BlockbusterSceneActorData actor:scene.getActors())
        {
            if(!ParticleActorRig.isParticleActor(actor)||
                    !actor.getActor().isEnabled())continue;
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
                    (position.hasBodyYaw()?position.getBodyYaw():
                            position.getYaw())+
                            ParticleActorRig.yaw(rig,frame),
                    position.getPitch()+ParticleActorRig.pitch(rig,frame),
                    position.getRoll()+ParticleActorRig.roll(rig,frame),
                    clock.random,clock.elapsed++);
        }
    }
}

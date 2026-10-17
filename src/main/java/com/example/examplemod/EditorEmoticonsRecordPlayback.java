package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Deterministic animation clock for Emoticons in the BBS editor Preview.
 *
 * Emoticons Animator is stateful: walking, jump and the fade of the previous
 * action are selected in Animator.controlActions() on EVERY entity tick.
 * Rendering a recording at an arbitrary timeline frame therefore requires
 * recreating the historical state, not merely assigning ticksExisted or
 * applying the entity pose of the requested frame.
 *
 * This bridge advances at Record's 20 Hz clock, including while paused.
 * Repeated render passes of the same frame never advance ActionPlayback.
 * Backward/non-adjacent seeks rebuild the controller and replay record
 * states in order, so scrubbing does not inherit the animation from the
 * last frame that happened to be displayed.
 *
 * It is per preview actor; background actors must have independent clocks.
 */
public final class EditorEmoticonsRecordPlayback
{
    private EntityActor lastEntity;
    private AnimatedMorph lastMorph;
    private BlockbusterRecord lastRecord;
    private int lastFrame = -1;
    private String configSignature = "";

    public void reset()
    {
        lastEntity = null;
        lastMorph = null;
        lastRecord = null;
        lastFrame = -1;
        configSignature = "";
    }

    public void update(EntityActor entity, AnimatedMorph morph,
            BlockbusterRecord record, int requestedFrame)
    {
        if(entity==null || morph==null || record==null ||
                record.getFrames()==null || record.getFrames().isEmpty())
            return;

        int frame=Math.min(Math.max(requestedFrame,0),
                record.getFrames().size()-1);
        String config=morph.animationName+"|"+
                (morph.userConfigData==null ? "" : morph.userConfigData.toString());
        boolean sameSource=lastEntity==entity && lastMorph==morph &&
                lastRecord==record && config.equals(configSignature);

        // Avoid replaying native animation on every GUI draw.
        if(sameSource && frame==lastFrame)return;

        boolean contiguous=sameSource && frame==lastFrame+1;
        if(!contiguous)
        {
            // A new Morph, edited animation setup, reversed playhead or
            // arbitrary seek cannot reuse old Animator actions. Reconstruct
            // them from the beginning of this Record.
            morph.animator=new EditorAnimatorMorphController(
                    morph.animationName,morph.userConfigData,morph);
            morph.animator.fetchAnimation();
            if(morph.animator.animation==null)
            {
                reset();
                return;
            }
            morph.updateAnimator();
            lastFrame=-1;
        }

        int from=contiguous?frame:0;
        BlockbusterRecordFrame previous=from>0?record.getFrame(from-1):null;

        for(int tick=from;tick<=frame;tick++)
        {
            BlockbusterRecordFrame current=record.getFrame(tick);
            if(current==null)continue;
            installRecordState(entity,previous,current,tick);
            // AnimatedMorph.update() drives its native Animator.update()
            // and ActionPlayback.update(), not just its render pose.
            morph.update(entity);
            previous=current;
        }

        // Keep the visible actor at the editor-selected Record position.
        // Rendering after this call must not call update() a second time.
        lastEntity=entity;
        lastMorph=morph;
        lastRecord=record;
        lastFrame=frame;
        configSignature=config;
    }

    private static void installRecordState(EntityActor actor,
            BlockbusterRecordFrame previous,
            BlockbusterRecordFrame current,int tick)
    {
        actor.ticksExisted=tick;
        actor.prevPosX=previous==null?current.getX():previous.getX();
        actor.prevPosY=previous==null?current.getY():previous.getY();
        actor.prevPosZ=previous==null?current.getZ():previous.getZ();
        actor.posX=current.getX();
        actor.posY=current.getY();
        actor.posZ=current.getZ();

        // BBS Record stores actual MY, MX and MZ. Native Emoticons
        // requires motionY to detect a jump (wasOnGround -> !onGround,
        // |motionY| > .2). Reconstruct from positions only for older
        // recordings that lack nonzero motion samples.
        actor.motionX=current.getMotionX();
        actor.motionY=current.getMotionY();
        actor.motionZ=current.getMotionZ();
        if(previous!=null)
        {
            if(Math.abs(actor.motionX)<1E-6D)
                actor.motionX=current.getX()-previous.getX();
            if(Math.abs(actor.motionZ)<1E-6D)
                actor.motionZ=current.getZ()-previous.getZ();
            if(Math.abs(actor.motionY)<1E-6D)
                actor.motionY=current.getY()-previous.getY();
        }

        actor.onGround=current.isGround();
        actor.fallDistance=current.getFallDistance();
        actor.setSneaking(current.isSneaking());
        actor.setSprinting(current.isSprinting());
        actor.rotationYaw=current.getYaw();
        actor.prevRotationYaw=previous==null?current.getYaw():previous.getYaw();
        actor.rotationPitch=current.getPitch();
        actor.prevRotationPitch=previous==null?current.getPitch():previous.getPitch();

        // Emoticons uses moveForward only to choose the playback direction.
        // Recover it from recorded displacement, not from the Preview camera.
        double dx=actor.posX-actor.prevPosX,dz=actor.posZ-actor.prevPosZ;
        float yaw=(float)Math.toRadians(current.getYaw());
        double forward=dx*(-Math.sin(yaw))+dz*Math.cos(yaw);
        actor.moveForward=(float)(forward<0D?-1D:1D);
    }
}

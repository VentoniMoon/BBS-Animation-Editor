package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

/**
 * Java 8 regression of the real timeline driver (without Minecraft runtime).
 * Simulates walking from 0..5, jump at 6, landing at 9, and paused scrubbing.
 */
public final class EmoticonsRecordPlaybackSmoke
{
    private EmoticonsRecordPlaybackSmoke() {}
    private static void check(boolean condition,String description)
    {
        if(!condition)throw new AssertionError(description);
    }
    public static void main(String[] args)
    {
        List<BlockbusterRecordFrame> frames=new ArrayList<BlockbusterRecordFrame>();
        for(int tick=0;tick<16;tick++)
        {
            boolean ground=tick<6 || tick>=9;
            double x=tick*0.08D;
            double y=tick<6?0D:tick==6?0.42D:tick==7?0.70D:tick==8?0.40D:0D;
            double my=tick==6?0.42D:tick==7?0.28D:tick==8?-0.30D:0D;
            frames.add(new BlockbusterRecordFrame(x,y,0D,my,ground));
        }
        BlockbusterRecord record=new BlockbusterRecord(frames);
        EntityActor actor=new EntityActor();
        AnimatedMorph morph=new AnimatedMorph();
        EditorEmoticonsRecordPlayback clock=new EditorEmoticonsRecordPlayback();

        clock.update(actor,morph,record,5);
        EditorAnimatorMorphController initial=morph.animator;
        check(initial.walkUpdates==5,"Paused seek does not reconstruct walk phase");
        check(initial.jumpStarts==0,"Walk unexpectedly triggered a jump");
        int updates=initial.updates;
        clock.update(actor,morph,record,5);
        check(initial.updates==updates,"Repeated render advanced paused action clock");

        clock.update(actor,morph,record,6);
        check(morph.animator==initial,"Adjacent tick incorrectly rebuilt controller");
        check(initial.jumpStarts==1,"Recorded upward motion did not trigger jump");
        check(initial.walkUpdates==6,"Walk phase stopped before jump transition");

        clock.update(actor,morph,record,9);
        check(morph.animator!=initial,"Nonadjacent seek did not reconstruct action state");
        check(morph.animator.jumpStarts==1,"Jump missing after replaying walk -> jump");
        check(morph.animator.landed==1,"Ground transition was not reproduced");
        EditorAnimatorMorphController afterSeek=morph.animator;

        clock.update(actor,morph,record,2);
        check(morph.animator!=afterSeek,"Backward seek retained previous action state");
        check(morph.animator.walkUpdates==2,"Backward seek retained prior walking phase");
        clock.update(actor,morph,record,6);
        check(morph.animator.jumpStarts==1,"Jump absent after paused walk-first seek");
        check(morph.animator.walkUpdates==6,"Walking progress was not reconstructed");

        EditorAnimatorMorphController beforeConfig=morph.animator;
        morph.userConfigData="new action setup";
        clock.update(actor,morph,record,6);
        check(morph.animator!=beforeConfig,"Updated Character action setup kept stale animator");
        check(morph.animator.jumpStarts==1,"Jump missing after action setup changed");
        // Blockbuster records can mark airborne before the first Y increase.
        // If MY is zero at that boundary, Animator would miss its one-time
        // wasOnGround -> !onGround jump event. Recover the next tick's rise.
        List<BlockbusterRecordFrame> delayed=new ArrayList<BlockbusterRecordFrame>();
        for(int tick=0;tick<9;tick++)
        {
            boolean ground=tick<4||tick>=7;
            double y=tick<=4?0D:tick==5?0.42D:tick==6?0.70D:0D;
            delayed.add(new BlockbusterRecordFrame(tick*.08D,y,0D,
                    tick==5?.42D:tick==6?.28D:0D,ground));
        }
        BlockbusterRecord delayedRecord=new BlockbusterRecord(delayed);
        EntityActor delayedActor=new EntityActor();
        AnimatedMorph delayedMorph=new AnimatedMorph();
        EditorEmoticonsRecordPlayback delayedClock=new EditorEmoticonsRecordPlayback();
        delayedClock.update(delayedActor,delayedMorph,delayedRecord,4);
        check(delayedMorph.animator.jumpStarts==1,
                "Jump at first airborne frame was missed when MY was zero");
        check(delayedActor.isAirBorne,
                "Blockbuster airborne flag was not forwarded");
        check(delayedActor.motionY>.2D,
                "Takeoff impulse was not reconstructed from next frame");
        delayedClock.update(delayedActor,delayedMorph,delayedRecord,5);
        check(delayedMorph.animator.jumpStarts==1,
                "Jump was triggered twice during same airborne interval");
        delayedClock.update(delayedActor,delayedMorph,delayedRecord,7);
        check(delayedMorph.animator.jumpStarts==1,
                "Jump disappeared after full timeline seek");

        // A fall from a ledge must NOT count as jumping.
        List<BlockbusterRecordFrame> falling=new ArrayList<BlockbusterRecordFrame>();
        for(int tick=0;tick<5;tick++)
            falling.add(new BlockbusterRecordFrame(tick*.08D,
                    tick<2?1D:1D-(tick-1)*.25D,0D,0D,tick<2));
        AnimatedMorph fallingMorph=new AnimatedMorph();
        new EditorEmoticonsRecordPlayback().update(new EntityActor(),
                fallingMorph,new BlockbusterRecord(falling),4);
        check(fallingMorph.animator.jumpStarts==0,
                "Walk-off ledge incorrectly triggered jump");
        System.out.println("Emoticons walk, jump, delayed impulse, landing, seek and ledge: PASS");
    }
}

/* Minimal test-only stand-ins; production classes are in src/main/java. */
final class BlockbusterRecord
{
    private final List<BlockbusterRecordFrame> frames;
    BlockbusterRecord(List<BlockbusterRecordFrame> frames) {this.frames=frames;}
    List<BlockbusterRecordFrame> getFrames() {return frames;}
    BlockbusterRecordFrame getFrame(int index) {return frames.get(index);}
}

final class BlockbusterRecordFrame
{
    private final double x,y,z,my;
    private final boolean ground;
    BlockbusterRecordFrame(double x,double y,double z,double my,boolean ground)
    {this.x=x;this.y=y;this.z=z;this.my=my;this.ground=ground;}
    double getX(){return x;}
    double getY(){return y;}
    double getZ(){return z;}
    float getMotionX(){return 0F;}
    float getMotionY(){return (float)my;}
    float getMotionZ(){return 0F;}
    boolean isGround(){return ground;}
    boolean isAirborne(){return !ground;}
    float getYawHead(){return 0F;}
    boolean hasBodyYaw(){return false;}
    float getBodyYaw(){return 0F;}
    boolean isSneaking(){return false;}
    boolean isSprinting(){return false;}
    float getFallDistance(){return 0F;}
    float getYaw(){return 0F;}
    float getPitch(){return 0F;}
}

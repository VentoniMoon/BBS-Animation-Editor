package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

public final class EditorAnimatorMorphController
{
    final String name;
    final Object userData;
    final AnimatedMorph morph;
    Object animation;
    int updates,walkUpdates,jumpStarts,landed;
    boolean wasOnGround=true;

    public EditorAnimatorMorphController(String name,Object userData,AnimatedMorph morph)
    {this.name=name;this.userData=userData;this.morph=morph;}
    public void fetchAnimation() {animation=new Object();}
    public void update(EntityActor actor)
    {
        updates++;
        if(actor.posX>0.001D)walkUpdates++;
        if(!actor.onGround && wasOnGround && Math.abs(actor.motionY)>0.2D)
            jumpStarts++;
        if(actor.onGround && !wasOnGround)
            landed++;
        wasOnGround=actor.onGround;
    }
}

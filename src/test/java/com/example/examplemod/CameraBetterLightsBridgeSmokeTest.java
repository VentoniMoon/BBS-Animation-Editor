package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.BetterLightsMorph;
import net.minecraftforge.fml.common.Loader;

/** Synthetic optional-mod lifecycle regression, without a Minecraft client. */
public final class CameraBetterLightsBridgeSmokeTest {
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        EntityActor actor=new EntityActor();
        BetterLightsMorph morph=new BetterLightsMorph();
        actor.morph=morph;
        Loader.available=false;
        CameraBetterLightsBridge.prepare(actor,0);
        check(morph.updates==0,"no BetterLights -> no optional calls");
        Loader.available=true;
        CameraBetterLightsBridge.prepare(actor,0);
        check(morph.updates==1,"first scene frame registers light");
        CameraBetterLightsBridge.prepare(actor,0);
        CameraBetterLightsBridge.prepare(actor,0);
        check(morph.updates==1,"60 FPS subframes do not tick lights 3x");
        check(morph.getKeepAliveUntil()==5,
                "stopped timeline refreshes BetterLights caster TTL");
        CameraBetterLightsBridge.prepare(actor,1);
        check(morph.updates==2,"next 20TPS update renews caster once");
        EntityActor another=new EntityActor();
        another.morph=new BetterLightsMorph();
        CameraBetterLightsBridge.prepare(another,1);
        check(another.morph.updates==1,"actors have independent state");
        actor.morph=new BetterLightsMorph();
        CameraBetterLightsBridge.prepare(actor,1);
        check(actor.morph.updates==1,"changed morph starts fresh light");
        actor.world.isRemote=false;
        CameraBetterLightsBridge.prepare(actor,2);
        check(actor.morph.updates==1,"never mutate server world");
        System.out.println("CAMERA optional BetterLights morph lifecycle PASS");
    }
}

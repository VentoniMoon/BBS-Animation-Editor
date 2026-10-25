package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;

/**
 * BetterLights shader's player-relative offset must use the active detached
 * film camera position, without moving static LightCaster/world positions.
 */
public final class CameraBetterLightsCameraOriginSmokeTest {
    private static void check(boolean good,String msg){
        if(!good)throw new AssertionError(msg);
    }
    private static void near(double actual,double expected,String msg){
        if(Math.abs(actual-expected)>1E-9)
            throw new AssertionError(msg+" "+actual+" != "+expected);
    }
    public static void main(String[] args){
        Minecraft mc=new Minecraft();
        Entity player=new Entity();
        player.posX=7D;player.posY=65D;player.posZ=8D;
        player.prevPosX=6.5D;player.prevPosY=65D;player.prevPosZ=7.5D;
        player.lastTickPosX=6D;player.lastTickPosY=65D;
        player.lastTickPosZ=7D;
        mc.player=player;
        Entity view=new Entity();
        view.posX=100D;view.posY=75D;view.posZ=200D;
        view.prevPosX=100D;view.prevPosY=75D;view.prevPosZ=200D;
        view.lastTickPosX=100D;view.lastTickPosY=75D;
        view.lastTickPosZ=200D;
        mc.view=view;
        Loader.available=false;
        CameraBetterLightsCameraOrigin.begin(mc,view);
        near(player.posX,7D,"no BetterLights -> leave player untouched");
        CameraBetterLightsCameraOrigin.end();
        Loader.available=true;
        Entity wrongView=new Entity();
        CameraBetterLightsCameraOrigin.begin(mc,wrongView);
        near(player.posX,7D,"only override for actual render view");
        CameraBetterLightsCameraOrigin.begin(mc,view);
        near(player.posX,100D,"shader origin X tracks detached camera");
        near(player.posY,75D,"shader origin Y tracks detached camera");
        near(player.posZ,200D,"shader origin Z tracks detached camera");
        near(player.prevPosX,100D,"interpolated shader prevX camera");
        near(player.lastTickPosZ,200D,"last tick origin follows camera");
        // A world-fixed source is not modified; its relative position in
        // light uniforms moves exactly opposite the camera's movement.
        double worldLightX=112D;
        near(worldLightX-player.posX,12D,
                "static light relative to first camera pose");
        CameraBetterLightsCameraOrigin.end();
        near(player.posX,7D,"restore actual player X");
        near(player.posY,65D,"restore actual player Y");
        near(player.posZ,8D,"restore actual player Z");
        near(player.prevPosX,6.5D,"restore interpolated player X");
        near(player.prevPosZ,7.5D,"restore interpolated player Z");
        near(player.lastTickPosX,6D,"restore lastTick player X");
        CameraBetterLightsCameraOrigin.end();
        near(player.posX,7D,"double cleanup idempotent");
        view.posX=106D;
        view.prevPosX=106D;
        view.lastTickPosX=106D;
        CameraBetterLightsCameraOrigin.begin(mc,view);
        near(worldLightX-player.posX,6D,
                "static light stays fixed when CAMERA translates 6 blocks");
        CameraBetterLightsCameraOrigin.begin(mc,view);
        near(player.posX,106D,"re-entry restores then mirrors safely");
        CameraBetterLightsCameraOrigin.end();
        near(player.posX,7D,"re-entry still restores original player pose");
        System.out.println("CAMERA BetterLights shader origin and cleanup PASS");
    }
}

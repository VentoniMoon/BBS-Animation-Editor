package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

/**
 * Never give a reflected camera Minecraft's live RenderGlobal.
 *
 * RenderGlobal owns mutable ViewFrustum chunk positions, visibility lists,
 * render-info queues and chunk compilation state. Moving the reflected camera
 * there and immediately returning to the real camera corrupts/hollows the
 * normal world (visible missing chunk sections). Saving OpenGL state alone
 * cannot repair that CPU-side state.
 *
 * This adapter maintains a second RenderGlobal bound to the SAME world and
 * swaps it into Minecraft only for the mirror's auxiliary world render.
 * It is intentionally independent of CAMERA clips, scene files and Jolt.
 */
public final class CameraMirrorWorldIsolation
{
    public interface Pass
    {
        void render() throws Throwable;
    }

    private static RenderGlobal mirrorGlobal;
    private static WorldClient mirrorWorld;
    private static boolean rendering;
    private static boolean disabled;
    private static boolean diagnosed;

    private CameraMirrorWorldIsolation(){}

    public static boolean ready(){return mirrorGlobal!=null&&!disabled;}
    public static boolean disabled(){return disabled;}
    public static boolean rendering(){return rendering;}

    public static void draw(Minecraft mc,Pass pass) throws Throwable
    {
        if(mc==null||mc.world==null||mc.renderGlobal==null||pass==null||
                disabled||rendering)
            throw new IllegalStateException("Mirror renderer unavailable");

        // The old world must never retain mirror chunk listeners and VBOs.
        if(mirrorWorld!=null&&mirrorWorld!=mc.world)
            reset(mc);

        RenderGlobal main=mc.renderGlobal;
        if(mirrorGlobal==null)
        {
            RenderGlobal separate=null;
            try
            {
                separate=new RenderGlobal(mc);
                separate.setWorldAndLoadRenderers(mc.world);
                mirrorGlobal=separate;
                mirrorWorld=mc.world;
                if(!diagnosed)
                {
                    diagnosed=true;
                    System.out.println("[BBS Editor][Mirror] Using isolated "+
                            "RenderGlobal for reflection (main ViewFrustum "+
                            "and visible chunk list untouched).");
                }
            }
            catch(Throwable init)
            {
                disabled=true;
                // setWorldAndLoadRenderers may have partially allocated
                // chunks or registered a world listener before throwing.
                if(separate!=null)
                    try{separate.setWorldAndLoadRenderers(null);}
                    catch(Throwable cleanup){}
                // Restore the global RenderManager world association even
                // when auxiliary renderer initialization fails.
                try{mc.getRenderManager().set(mc.world);}
                catch(Throwable ignored){}
                throw init;
            }
        }

        rendering=true;
        try
        {
            mc.renderGlobal=mirrorGlobal;
            pass.render();
        }
        finally
        {
            mc.renderGlobal=main;
            rendering=false;
        }
    }

    public static void reset(Minecraft mc)
    {
        if(rendering)return;
        RenderGlobal old=mirrorGlobal;
        mirrorGlobal=null;
        mirrorWorld=null;
        disabled=false;
        if(old==null)return;
        try
        {
            // Unregister only the mirror renderer from WorldClient, release
            // its render chunks and dispatcher. Do not unload main chunks.
            old.setWorldAndLoadRenderers(null);
        }
        catch(Throwable problem)
        {
            System.err.println("[BBS Editor][Mirror] Isolated renderer "+
                    "cleanup failed: "+problem);
        }
        finally
        {
            if(mc!=null&&mc.world!=null)
                try{mc.getRenderManager().set(mc.world);}
                catch(Throwable ignored){}
        }
    }
}

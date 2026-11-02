package com.example.examplemod;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Reconcile the OpenGL driver with Minecraft 1.12.2's GlStateManager cache.
 *
 * Raw glPushAttrib/glPopAttrib restores the DRIVER, not GlStateManager's
 * cached booleans / per-unit boundTexture. An early hidden actor draw can
 * therefore make the subsequent vanilla/OptiFine world skip a necessary
 * enable, disable or texture bind. Do not use for normal actor rendering.
 *
 * This scope records the real driver state immediately BEFORE an auxiliary
 * pass, and must be restored AFTER its existing GL push/pop guard is closed.
 * Force each cached boolean through both values so GL and the Minecraft
 * cache converge to the recorded state even when they already disagree.
 */
public final class BbsFsGlStateResync
{
    public static final class Snapshot
    {
        final boolean blend,alpha,depth,cull,lighting,fog;
        final boolean texture0Enabled,texture1Enabled;
        final int texture0,texture1,activeTexture;

        private Snapshot(boolean blend,boolean alpha,boolean depth,
                boolean cull,boolean lighting,boolean fog,
                boolean texture0Enabled,boolean texture1Enabled,
                int texture0,int texture1,int activeTexture)
        {
            this.blend=blend;this.alpha=alpha;this.depth=depth;
            this.cull=cull;this.lighting=lighting;this.fog=fog;
            this.texture0Enabled=texture0Enabled;
            this.texture1Enabled=texture1Enabled;
            this.texture0=texture0;this.texture1=texture1;
            this.activeTexture=activeTexture;
        }
    }

    private BbsFsGlStateResync(){}

    public static Snapshot capture()
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean t0Enabled=false,t1Enabled=false;
        int t0=0,t1=0;
        try
        {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            t0Enabled=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            t0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            t1Enabled=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            t1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        finally
        {
            GL13.glActiveTexture(active);
        }
        return new Snapshot(
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL11.GL_FOG),
                t0Enabled,t1Enabled,t0,t1,active);
    }

    public static void restore(Snapshot snapshot)
    {
        if(snapshot==null)return;
        // First restore texture bindings on the two units owned by the
        // Minecraft block atlas and lightmap. Rebind through the public
        // state manager, never raw GL calls, to repair its boundTexture cache.
        forceTexture(GL13.GL_TEXTURE0,snapshot.texture0,
                snapshot.texture0Enabled);
        forceTexture(GL13.GL_TEXTURE1,snapshot.texture1,
                snapshot.texture1Enabled);

        // No assumption about what an arbitrary morph renderer did to
        // Minecraft's private state caches. Toggling twice is intentional:
        // each final call writes to both the cache and real GL state.
        forceBlend(snapshot.blend);
        forceAlpha(snapshot.alpha);
        forceDepth(snapshot.depth);
        forceCull(snapshot.cull);
        forceLighting(snapshot.lighting);
        forceFog(snapshot.fog);

        // Restore the original active sampler in both LWJGL and the cache.
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GlStateManager.setActiveTexture(snapshot.activeTexture);
    }

    private static void forceTexture(int unit,int texture,boolean enabled)
    {
        GlStateManager.setActiveTexture(unit==GL13.GL_TEXTURE0?
                GL13.GL_TEXTURE1:GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(unit);
        // Reset the cached binding independently of the old raw-GL
        // binding. Texture names are non-negative and 0 is always safe.
        GlStateManager.bindTexture(0);
        if(texture!=0)GlStateManager.bindTexture(texture);
        // The cache may have already claimed this name before raw
        // glPopAttrib restored a different binding. Reassert the actual
        // driver binding even if the cached operation was skipped.
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        if(enabled)
        {
            GlStateManager.disableTexture2D();
            GlStateManager.enableTexture2D();
        }
        else
        {
            GlStateManager.enableTexture2D();
            GlStateManager.disableTexture2D();
        }
    }

    private static void forceBlend(boolean flag)
    {
        if(flag){GlStateManager.disableBlend();GlStateManager.enableBlend();}
        else{GlStateManager.enableBlend();GlStateManager.disableBlend();}
    }

    private static void forceAlpha(boolean flag)
    {
        if(flag){GlStateManager.disableAlpha();GlStateManager.enableAlpha();}
        else{GlStateManager.enableAlpha();GlStateManager.disableAlpha();}
    }

    private static void forceDepth(boolean flag)
    {
        if(flag){GlStateManager.disableDepth();GlStateManager.enableDepth();}
        else{GlStateManager.enableDepth();GlStateManager.disableDepth();}
    }

    private static void forceCull(boolean flag)
    {
        if(flag){GlStateManager.disableCull();GlStateManager.enableCull();}
        else{GlStateManager.enableCull();GlStateManager.disableCull();}
    }

    private static void forceLighting(boolean flag)
    {
        if(flag){GlStateManager.disableLighting();GlStateManager.enableLighting();}
        else{GlStateManager.enableLighting();GlStateManager.disableLighting();}
    }

    private static void forceFog(boolean flag)
    {
        if(flag){GlStateManager.disableFog();GlStateManager.enableFog();}
        else{GlStateManager.enableFog();GlStateManager.disableFog();}
    }
}

package com.example.examplemod;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;

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
        final int clientActiveTexture,arrayBuffer,elementBuffer;
        final int alphaFunc,depthFunc;
        final float alphaRef;
        final boolean depthWrite,colorRed,colorGreen,colorBlue,colorAlpha;
        final int blendSrcRgb,blendDstRgb,blendSrcAlpha,blendDstAlpha;

        private Snapshot(boolean blend,boolean alpha,boolean depth,
                boolean cull,boolean lighting,boolean fog,
                boolean texture0Enabled,boolean texture1Enabled,
                int texture0,int texture1,int activeTexture,
                int clientActiveTexture,int arrayBuffer,int elementBuffer,
                int alphaFunc,float alphaRef,int depthFunc,boolean depthWrite,
                boolean colorRed,boolean colorGreen,
                boolean colorBlue,boolean colorAlpha,
                int blendSrcRgb,int blendDstRgb,int blendSrcAlpha,int blendDstAlpha)
        {
            this.blend=blend;this.alpha=alpha;this.depth=depth;
            this.cull=cull;this.lighting=lighting;this.fog=fog;
            this.texture0Enabled=texture0Enabled;
            this.texture1Enabled=texture1Enabled;
            this.texture0=texture0;this.texture1=texture1;
            this.activeTexture=activeTexture;
            this.clientActiveTexture=clientActiveTexture;
            this.arrayBuffer=arrayBuffer;
            this.elementBuffer=elementBuffer;
            this.alphaFunc=alphaFunc;
            this.alphaRef=alphaRef;
            this.depthFunc=depthFunc;
            this.depthWrite=depthWrite;
            this.colorRed=colorRed;
            this.colorGreen=colorGreen;
            this.colorBlue=colorBlue;
            this.colorAlpha=colorAlpha;
            this.blendSrcRgb=blendSrcRgb;
            this.blendDstRgb=blendDstRgb;
            this.blendSrcAlpha=blendSrcAlpha;
            this.blendDstAlpha=blendDstAlpha;
        }
    }

    private BbsFsGlStateResync(){}

    public static Snapshot capture()
    {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int clientActive=GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        int arrayBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int elementBuffer=GL11.glGetInteger(
                GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
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
        // glPopAttrib restores these on the DRIVER, but not Minecraft's
        // cached alpha/depth/color/blend parameters. A later actor can
        // otherwise silently skip a necessary state change.
        // LWJGL 2.9.4 glGetBoolean(pname, ByteBuffer) validates a minimum
        // of 16 remaining bytes even for GL_COLOR_WRITEMASK's four values.
        // Only bytes 0..3 contain the red/green/blue/alpha write flags.
        ByteBuffer color=BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,color);
        return new Snapshot(
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL11.GL_FOG),
                t0Enabled,t1Enabled,t0,t1,active,
                clientActive,arrayBuffer,elementBuffer,
                GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),
                GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                color.get(0)!=0,color.get(1)!=0,
                color.get(2)!=0,color.get(3)!=0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
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
        // GL's push/pop attribute stack does not synchronize these cached
        // Minecraft 1.12.2 parameters. Force a distinct intermediate value
        // before restoring the saved one so the cache cannot skip the write.
        forceAlphaFunc(snapshot.alphaFunc,snapshot.alphaRef);
        forceDepthFunc(snapshot.depthFunc);
        forceDepthMask(snapshot.depthWrite);
        forceColorMask(snapshot.colorRed,snapshot.colorGreen,
                snapshot.colorBlue,snapshot.colorAlpha);
        forceBlendFunc(snapshot.blendSrcRgb,snapshot.blendDstRgb,
                snapshot.blendSrcAlpha,snapshot.blendDstAlpha);

        // Restore the original active sampler in both LWJGL and the cache.
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GlStateManager.setActiveTexture(snapshot.activeTexture);
        // Emoticons GL15 vertex buffers and texture coordinate arrays use
        // CLIENT_ACTIVE_TEXTURE, not the ordinary ACTIVE_TEXTURE above.
        // Restore both client-array buffer targets so a subsequent
        // Blockbuster or Emoticons renderer starts from the expected state.
        GL13.glClientActiveTexture(snapshot.clientActiveTexture);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,snapshot.arrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,
                snapshot.elementBuffer);
    }

    private static void forceAlphaFunc(int func,float ref)
    {
        GlStateManager.alphaFunc(func==GL11.GL_ALWAYS
                ?GL11.GL_NEVER:GL11.GL_ALWAYS,0.5F);
        GlStateManager.alphaFunc(func,ref);
    }

    private static void forceDepthFunc(int func)
    {
        GlStateManager.depthFunc(func==GL11.GL_ALWAYS
                ?GL11.GL_NEVER:GL11.GL_ALWAYS);
        GlStateManager.depthFunc(func);
    }

    private static void forceDepthMask(boolean enabled)
    {
        GlStateManager.depthMask(!enabled);
        GlStateManager.depthMask(enabled);
    }

    private static void forceColorMask(boolean r,boolean g,
            boolean b,boolean a)
    {
        GlStateManager.colorMask(!r,!g,!b,!a);
        GlStateManager.colorMask(r,g,b,a);
    }

    private static void forceBlendFunc(int srcRgb,int dstRgb,
            int srcAlpha,int dstAlpha)
    {
        // Different from the recorded blend function even if it was the
        // usual ONE/ZERO opaque baseline.
        int alt=srcRgb==GL11.GL_ONE?GL11.GL_ZERO:GL11.GL_ONE;
        GlStateManager.tryBlendFuncSeparate(alt,GL11.GL_ZERO,
                GL11.GL_ONE,GL11.GL_ZERO);
        GlStateManager.tryBlendFuncSeparate(srcRgb,dstRgb,
                srcAlpha,dstAlpha);
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

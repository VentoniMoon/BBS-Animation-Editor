package com.example.examplemod;

import java.nio.ShortBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * A/B diagnostic for the diagonal stripes on shader-lit actor surfaces.
 *
 * When explicitly toggled by Ctrl+Shift+F7, replace ONLY the two OptiFine
 * sun-shadow samplers with a 1x1 maximum-depth map while drawing a recorded
 * actor. The world and the shader pack are never modified. This distinguishes
 * shadow-map sampling from normal/lighting GBuffer problems.
 *
 * Disabled by default. Must only run on the OpenGL render thread.
 */
public final class BbsFsActorShadowSamplingProbe implements AutoCloseable
{
    private static boolean bypass;
    private static int unshadowedDepthTexture;
    private static boolean warned;
    private final int oldActiveTexture;
    private final int[] previous = new int[2];
    private boolean active;

    private BbsFsActorShadowSamplingProbe()
    {
        oldActiveTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    }

    public static boolean isBypassed()
    {
        return bypass;
    }

    public static boolean toggle()
    {
        bypass=!bypass;
        System.out.println("[BBS Editor][Actor Shadow A/B] "+
                (bypass?"Shadow samplers bypassed for actors ONLY":
                        "Original shadow samplers restored"));
        return bypass;
    }

    public static BbsFsActorShadowSamplingProbe begin()
    {
        BbsFsActorShadowSamplingProbe probe=
                new BbsFsActorShadowSamplingProbe();
        if(!bypass || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0)
            return probe;
        try
        {
            if(unshadowedDepthTexture==0)
                unshadowedDepthTexture=createDepthTexture();
            if(unshadowedDepthTexture==0)return probe;

            for(int i=0;i<2;i++)
            {
                GL13.glActiveTexture(GL13.GL_TEXTURE4+i);
                probe.previous[i]=GL11.glGetInteger(
                        GL11.GL_TEXTURE_BINDING_2D);
                // Do not populate units which the pack left unused.
                if(probe.previous[i]==0)continue;
                int compareMode=GL11.glGetTexParameteri(
                        GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE);
                int compareFunc=GL11.glGetTexParameteri(
                        GL11.GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_FUNC);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                        unshadowedDepthTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL14.GL_TEXTURE_COMPARE_MODE,compareMode);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                        GL14.GL_TEXTURE_COMPARE_FUNC,compareFunc);
            }
            probe.active=true;
        }
        catch(Throwable t)
        {
            if(!warned)
            {
                warned=true;
                System.err.println("[BBS Editor][Actor Shadow A/B] "+
                        "Sampler override unavailable: "+t);
            }
            probe.restore();
        }
        finally
        {
            GL13.glActiveTexture(probe.oldActiveTexture);
        }
        return probe;
    }

    private static int createDepthTexture()
    {
        int priorUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE4);
        int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture=0;
        try
        {
            texture=GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            ShortBuffer white=BufferUtils.createShortBuffer(1);
            white.put((short)0xFFFF).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,
                    GL14.GL_DEPTH_COMPONENT16,1,1,0,
                    GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_SHORT,white);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_S,GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                    GL11.GL_TEXTURE_WRAP_T,GL11.GL_CLAMP);
            return texture;
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);
            GL13.glActiveTexture(priorUnit);
        }
    }

    private void restore()
    {
        for(int i=0;i<2;i++)
        {
            if(previous[i]==0)continue;
            GL13.glActiveTexture(GL13.GL_TEXTURE4+i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,previous[i]);
        }
        GL13.glActiveTexture(oldActiveTexture);
        active=false;
    }

    @Override public void close()
    {
        if(active)restore();
    }

    /** Dispose the private diagnostic texture on the GL thread at editor exit. */
    public static void dispose()
    {
        bypass=false;
        if(unshadowedDepthTexture!=0)
        {
            GL11.glDeleteTextures(unshadowedDepthTexture);
            unshadowedDepthTexture=0;
        }
    }
}

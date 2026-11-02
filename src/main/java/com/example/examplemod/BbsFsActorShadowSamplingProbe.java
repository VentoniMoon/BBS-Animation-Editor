package com.example.examplemod;

import java.nio.ShortBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * Actor-only workaround for incorrect BSL/OptiFine sun-shadow samples.
 *
 * The user-tested F7 result is the editor default: while drawing a recorded
 * actor, shadow samplers on OptiFine units 4/5 see a 1x1 maximum-depth map.
 * This preserves shaderpack colors and lighting, while preventing diagonal
 * bands on sunlit actor faces. The world's shadow textures are restored
 * immediately after each actor; the rest of the scene remains unchanged.
 *
 * Ctrl+Shift+F7 can still restore native receiving shadows for debugging.
 * Set -Dbbs.editor.actorShadowSamplingFix=false at startup to opt out.
 * Use only from the OpenGL render thread.
 */
public final class BbsFsActorShadowSamplingProbe implements AutoCloseable
{
    private static final boolean DEFAULT_BYPASS =
            !"false".equalsIgnoreCase(System.getProperty(
                    "bbs.editor.actorShadowSamplingFix", "true"));
    private static boolean bypass=DEFAULT_BYPASS;
    private static int unshadowedDepthTexture;
    private static boolean warned;
    private static boolean activeReported;
    private static boolean unavailableReported;
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
        activeReported=false;
        unavailableReported=false;
        System.out.println("[BBS Editor][Actor Sun Shadow] "+
                (bypass
                        ? "Actor-only shadow sampling fix enabled"
                        : "Native actor shadow sampling enabled (experimental)"));
        return bypass;
    }

    public static BbsFsActorShadowSamplingProbe begin()
    {
        // A null try-with-resources resource is legal in Java 8. Explicit
        // raw-shadow mode avoids any diagnostic OpenGL readbacks per actor.
        if(!bypass)return null;
        BbsFsActorShadowSamplingProbe probe=
                new BbsFsActorShadowSamplingProbe();
        if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==0)
        {
            if(!unavailableReported)
            {
                unavailableReported=true;
                System.out.println("[BBS Editor][Actor Shadow A/B] "+
                        "No active shader program for this actor draw");
            }
            return probe;
        }
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
            probe.active=probe.previous[0]!=0||probe.previous[1]!=0;
            if(probe.active&&!activeReported)
            {
                activeReported=true;
                System.out.println("[BBS Editor][Actor Shadow A/B] "+
                        "Replaced shadow samplers in program "+
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)+
                        " (original units 4/5="+probe.previous[0]+
                        "/"+probe.previous[1]+")");
            }
            else if(!probe.active&&!unavailableReported)
            {
                unavailableReported=true;
                System.out.println("[BBS Editor][Actor Shadow A/B] "+
                        "No textures bound on shadow sampler units 4/5");
            }
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

    /** Release the private texture on the GL thread and reset per-session state. */
    public static void dispose()
    {
        bypass=DEFAULT_BYPASS;
        activeReported=false;
        unavailableReported=false;
        warned=false;
        if(unshadowedDepthTexture!=0)
        {
            GL11.glDeleteTextures(unshadowedDepthTexture);
            unshadowedDepthTexture=0;
        }
    }
}

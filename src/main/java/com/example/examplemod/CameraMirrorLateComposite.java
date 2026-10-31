package com.example.examplemod;

import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Late composition of a finished BSL mirror.
 *
 * WorldLast is inside OptiFine's main shader frame, BEFORE its final pass.
 * Writing an already-composited reflection into main colortex0 makes it
 * participate in a second composite/tonemap as if it were raw GBuffer
 * geometry. Instead, rasterize the reflection's screen coverage with the
 * native world depth into a transparent private overlay, and blend that
 * overlay AFTER the native BSL final but BEFORE the editor GUI capture.
 *
 * Only one WorldClient, one RenderGlobal and the already prepared actors
 * are used. No main shaderpack framebuffer or temporal buffers are mutated.
 */
public final class CameraMirrorLateComposite
{
    public interface Draw
    {
        void draw();
    }

    private static Framebuffer overlay;
    private static boolean prepared;
    private static boolean unavailable;
    private static boolean reported;
    private static boolean errorReported;
    private static String problem="";

    private CameraMirrorLateComposite(){}

    public static boolean prepared(){return prepared;}
    public static boolean available(){return !unavailable;}
    public static String problem(){return problem;}

    public static void beginFrame(){prepared=false;}

    /**
     * Called during RenderWorldLast, while the real scene depth and
     * modelview/projection matrices still describe the main world camera.
     */
    public static boolean prepare(Minecraft mc,Draw quadDraw)
    {
        prepared=false;
        if(unavailable||mc==null||mc.getFramebuffer()==null||
                quadDraw==null)return false;
        Framebuffer main=mc.getFramebuffer();
        int w=main.framebufferWidth,h=main.framebufferHeight;
        if(w<=0||h<=0)return false;
        int worldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if(worldDraw<=0)return false;
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw=worldDraw;
        int oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try
        {
            if(overlay==null)overlay=new Framebuffer(w,h,true);
            else if(overlay.framebufferWidth!=w||
                    overlay.framebufferHeight!=h)
                overlay.createBindFramebuffer(w,h);
            overlay.bindFramebuffer(true);
            GL11.glViewport(0,0,w,h);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(0F,0F,0F,0F);
            GL11.glColorMask(true,true,true,true);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            // Copy native scene depth, including the foreground actors.
            // No separate world renderer or duplicate actor pose is needed.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,worldDraw);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    overlay.framebufferObject);
            GL30.glBlitFramebuffer(viewport.get(0),viewport.get(1),
                    viewport.get(0)+viewport.get(2),
                    viewport.get(1)+viewport.get(3),0,0,w,h,
                    GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
            int error=GL11.glGetError();
            if(error!=GL11.GL_NO_ERROR)
                throw new IllegalStateException(
                        "native depth copy GL error 0x"+
                        Integer.toHexString(error));

            // The caller's world projection/modelview are still current.
            // Draw the same planar mesh with its homographic reflection UV.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    overlay.framebufferObject);
            GL11.glViewport(0,0,w,h);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            quadDraw.draw();
            prepared=true;
            problem="";
            if(!reported)
            {
                reported=true;
                System.out.println("[BBS Editor][Mirror][Late] "+
                        "Depth-tested mirror overlay prepared; "+
                        "compositing after OptiFine final.");
            }
            return true;
        }
        catch(Throwable error)
        {
            unavailable=true;
            prepared=false;
            problem=String.valueOf(error);
            if(!errorReported)
            {
                errorReported=true;
                System.err.println("[BBS Editor][Mirror][Late] "+
                        "Late overlay unavailable, using WorldLast fallback: "+
                        error);
                error.printStackTrace();
            }
            return false;
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
            GL11.glPopAttrib();
            GL13.glActiveTexture(oldActive);
            GL20.glUseProgram(oldProgram);
            GL11.glMatrixMode(oldMode);
        }
    }

    /**
     * Called by BbsFsWorldFrameCapture.beginFrame() after the vanilla/
     * OptiFine frame completed, before any UI rectangles or previews.
     * Uses the resolved Minecraft color target, not the shared BSL dfb.
     */
    public static void composite(Minecraft mc)
    {
        if(!prepared)return;
        prepared=false;
        if(overlay==null||mc==null||mc.getFramebuffer()==null)return;
        Framebuffer main=mc.getFramebuffer();
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer vp=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,vp);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0D,1D,0D,1D,-1D,1D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    main.framebufferObject);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0,0,main.framebufferWidth,
                    main.framebufferHeight);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    overlay.framebufferTexture);
            GL11.glColor4f(1F,1F,1F,1F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F,0F);GL11.glVertex2f(0F,0F);
            GL11.glTexCoord2f(1F,0F);GL11.glVertex2f(1F,0F);
            GL11.glTexCoord2f(1F,1F);GL11.glVertex2f(1F,1F);
            GL11.glTexCoord2f(0F,1F);GL11.glVertex2f(0F,1F);
            GL11.glEnd();
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(vp.get(0),vp.get(1),vp.get(2),vp.get(3));
            GL13.glActiveTexture(oldUnit);
            GL20.glUseProgram(oldProgram);
            GL11.glMatrixMode(oldMode);
        }
    }

    public static void reset()
    {
        prepared=false;
        unavailable=false;
        reported=false;
        errorReported=false;
        problem="";
        if(overlay!=null)
        {
            overlay.deleteFramebuffer();
            overlay=null;
        }
    }
}

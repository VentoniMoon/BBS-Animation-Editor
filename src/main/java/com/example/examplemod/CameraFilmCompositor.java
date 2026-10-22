package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Composites screen-space IMAGE, VIDEO and SUBTITLE clips in the film FBO.
 *
 * This is intentionally not a GuiScreen paint operation: a future offline
 * video encoder reads exactly these film pixels, without editor chrome,
 * gizmos, dropdowns or cursors. World and OptiFine render have already
 * finished; no world passes or shaders are restarted.
 */
public final class CameraFilmCompositor
{
    public static void composite(Minecraft mc,Framebuffer film,
            int previewX,int previewY,int previewWidth,int previewHeight,
            List<CameraClipDocument.Clip> clips,int tick)
    {
        if(mc==null||film==null||clips==null||previewWidth<=0||previewHeight<=0)
            return;
        List<CameraClipDocument.Clip> images=new ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:clips)
            if(clip.enabled&&clip.contains(tick)&&
                    (clip.kind==CameraClipDocument.Kind.IMAGE||
                    clip.kind==CameraClipDocument.Kind.VIDEO||
                    clip.kind==CameraClipDocument.Kind.SUBTITLE))
                images.add(clip);
        if(images.isEmpty())return;
        Collections.sort(images,new Comparator<CameraClipDocument.Clip>(){
            @Override public int compare(CameraClipDocument.Clip a,
                    CameraClipDocument.Clip b)
            {return Integer.compare(a.layer,b.layer);}
        });

        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        java.nio.IntBuffer vp=org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,vp);
        ScaledResolution sr=new ScaledResolution(mc);
        int scale=Math.max(1,sr.getScaleFactor());
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0,sr.getScaledWidth(),sr.getScaledHeight(),0,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,film.framebufferObject);
            GL11.glViewport(0,0,film.framebufferWidth,film.framebufferHeight);
            GL11.glScissor(previewX*scale,
                    film.framebufferHeight-(previewY+previewHeight)*scale,
                    previewWidth*scale,previewHeight*scale);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            for(CameraClipDocument.Clip clip:images)
            {
                if(clip.kind==CameraClipDocument.Kind.IMAGE||
                        clip.kind==CameraClipDocument.Kind.VIDEO)
                    drawImage(clip,previewX,previewY,previewWidth,previewHeight,tick);
                else if(clip.kind==CameraClipDocument.Kind.SUBTITLE)
                    drawSubtitle(mc,clip,previewX,previewY,previewWidth,
                            previewHeight,tick);
            }
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
            GL11.glViewport(vp.get(0),vp.get(1),vp.get(2),vp.get(3));
            GL11.glPopAttrib();
            GL13.glActiveTexture(oldTexture);
            GL20.glUseProgram(oldProgram);
        }
    }
    private static void drawImage(CameraClipDocument.Clip clip,
            int px,int py,int pw,int ph,int tick)
    {
        DynamicTexture texture;
        if(clip.kind==CameraClipDocument.Kind.VIDEO)
        {
            int offset=(int)number(clip,"offset",0D);
            int local=tick-clip.tick+offset;
            texture=CameraVideoFrames.get(clip.settings.get("video"),local,
                    Boolean.parseBoolean(clip.settings.get("loop")));
        }
        else texture=CameraImageCache.get(clip.settings.get("texture"));
        if(texture==null)return;

        boolean fullscreen=Boolean.parseBoolean(clip.settings.get("fullscreen"));
        double x=number(clip,"x",0.5D);
        double y=number(clip,"y",0.5D);
        double w=number(clip,"width",1D);
        double h=number(clip,"height",1D);
        // Placement coordinates use normalized fractions relative to the
        // film viewport; half/center values place the overlay at the center.
        double left=fullscreen?px:px+(x-w/2D)*pw;
        double top=fullscreen?py:py+(y-h/2D)*ph;
        double right=fullscreen?px+pw:left+w*pw;
        double bottom=fullscreen?py+ph:top+h*ph;
        if(right<=left||bottom<=top)return;

        float alpha=(float)clamp(number(clip,"opacity",1D),0D,1D);
        alpha*=CameraClipEnvelope.factor(clip,tick);
        int rgba=color(clip.settings.get("color"));
        alpha*=((rgba>>>24)&255)/255F;
        GL11.glColor4f(((rgba>>>16)&255)/255F,
                ((rgba>>>8)&255)/255F,(rgba&255)/255F,alpha);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture.getGlTextureId());
        int filter=Boolean.parseBoolean(clip.settings.get("smooth"))
                ?GL11.GL_LINEAR:GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,filter);
        /*
         * Screen-space transform around the overlay center, applied after
         * normalized placement and before drawing texture coordinates.
         * This is a first step toward BBS FS's ValueTransform support.
         */
        double scaleX=clamp(number(clip,"scaleX",1D),-100D,100D);
        double scaleY=clamp(number(clip,"scaleY",1D),-100D,100D);
        double rotation=number(clip,"rotation",0D);
        if(Math.abs(scaleX)<1E-6D||Math.abs(scaleY)<1E-6D)return;
        double centerX=(left+right)/2D;
        double centerY=(top+bottom)/2D;
        GL11.glPushMatrix();
        try
        {
            GL11.glTranslated(centerX,centerY,0D);
            GL11.glRotated(rotation,0D,0D,1D);
            GL11.glScaled(scaleX,scaleY,1D);
            GL11.glTranslated(-centerX,-centerY,0D);

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2d(0D,1D);GL11.glVertex2d(left,top);
            GL11.glTexCoord2d(1D,1D);GL11.glVertex2d(right,top);
            GL11.glTexCoord2d(1D,0D);GL11.glVertex2d(right,bottom);
            GL11.glTexCoord2d(0D,0D);GL11.glVertex2d(left,bottom);
            GL11.glEnd();
        }
        finally {GL11.glPopMatrix();}
        GL11.glColor4f(1F,1F,1F,1F);
    }
    /** Subtitles are burned into the scene texture, never editor UI. */
    private static void drawSubtitle(Minecraft mc,CameraClipDocument.Clip clip,
            int px,int py,int pw,int ph,int tick)
    {
        String text=clip.settings.get("text");
        if(text==null||text.isEmpty())return;
        int packed=color(clip.settings.get("color"));
        double opacity=clamp(number(clip,"opacity",1D),0D,1D)*
                CameraClipEnvelope.factor(clip,tick);
        int alpha=(int)Math.round(((packed>>>24)&255)*opacity);
        int rgba=(packed&0xFFFFFF)|(alpha<<24);
        if(alpha==0)return;
        int x=px+(int)Math.round(pw*number(clip,"x",0.5D));
        int y=py+(int)Math.round(ph*number(clip,"y",0.88D));
        int maxWidth=(int)number(clip,"maxWidth",0D);
        if(maxWidth<=0)maxWidth=pw-12;
        maxWidth=Math.max(20,Math.min(pw,maxWidth));
        int lineHeight=(int)clamp(number(clip,"lineHeight",12D),8D,64D);
        String[] lines=text.replace("\\n","\n").split("\n",-1);
        boolean shadow=Boolean.parseBoolean(clip.settings.get("textShadow"));
        // Keep subtitles inside the film viewport. Do not disable scissor:
        // text outside the preview must never paint the surrounding editor.
        for(int i=0;i<Math.min(32,lines.length);i++)
        {
            String line=mc.fontRenderer.trimStringToWidth(lines[i],maxWidth);
            int width=mc.fontRenderer.getStringWidth(line);
            int left=x-width/2;
            int rowY=y+i*lineHeight;
            if(rowY<py||rowY+lineHeight>py+ph)continue;

            int bg=color(clip.settings.get("background"));
            int bgAlpha=(int)(((bg>>>24)&255)*opacity);
            if(bgAlpha>0)
            {
                int margin=(int)Math.max(0D,
                        Math.min(64D,number(clip,"backgroundOffset",2D)));
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(((bg>>>16)&255)/255F,
                        ((bg>>>8)&255)/255F,(bg&255)/255F,
                        bgAlpha/255F);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2d(left-margin,rowY-margin);
                GL11.glVertex2d(left+width+margin,rowY-margin);
                GL11.glVertex2d(left+width+margin,rowY+lineHeight+margin);
                GL11.glVertex2d(left-margin,rowY+lineHeight+margin);
                GL11.glEnd();
                GL11.glColor4f(1F,1F,1F,1F);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            // FontRenderer restores Minecraft's texture binding itself;
            // the GL projection is already the film's GUI-scaled pixel plane.
            if(shadow)mc.fontRenderer.drawStringWithShadow(line,left,rowY,rgba);
            else mc.fontRenderer.drawString(line,left,rowY,rgba);
        }
    }

    private static int color(String value)
    {
        if(value==null)return -1;
        try{return (int)Long.parseLong(value.replace("#",""),16);}
        catch(NumberFormatException invalid){return -1;}
    }
    private static double number(CameraClipDocument.Clip clip,String name,
            double fallback)
    {
        try
        {
            double value=Double.parseDouble(clip.settings.get(name));
            return Double.isFinite(value)?value:fallback;
        }
        catch(RuntimeException invalid){return fallback;}
    }
    private static double clamp(double value,double min,double max)
    {
        return Math.max(min,Math.min(max,value));
    }
    private CameraFilmCompositor(){}
}

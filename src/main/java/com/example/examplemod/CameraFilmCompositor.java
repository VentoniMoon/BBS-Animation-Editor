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
    /**
     * Native output-pixel film compositor for offline rendering and its PNG
     * diagnostic. Unlike composite(), this uses physical FBO coordinates,
     * with the frame origin at (0,0), not GUI-scale coordinates.
     *
     * IMAGE and SUBTITLE use the existing BBS-compatible clip transforms.
     * VIDEO needs a separate exact timestamp decoder: explicitly reject it
     * rather than silently reuse an asynchronously decoded preview frame.
     *
     * Must run on the Minecraft GL thread after native-world capture and
     * BEFORE readback. No mouse frames, editor selection or gizmos are drawn.
     */
    public static void compositeNative(Minecraft mc,Framebuffer film,
            List<CameraClipDocument.Clip> clips,int tick)
    {
        compositeNative(mc,film,clips,tick,null);
    }

    /**
     * Explicit offline VIDEO textures are provided by the export session.
     * A null decoder is used by legacy native PNG diagnostics, where VIDEO
     * intentionally still fails instead of rendering stale preview frames.
     */
    public static void compositeNative(Minecraft mc,Framebuffer film,
            List<CameraClipDocument.Clip> clips,int tick,
            CameraExportVideoFrames exactFrames)
    {
        if(mc==null||film==null||clips==null)
            throw new IllegalArgumentException("Missing native film target");
        int width=film.framebufferWidth;
        int height=film.framebufferHeight;
        if(width<1||height<1)
            throw new IllegalArgumentException("Invalid native film size");

        List<CameraClipDocument.Clip> overlays=
                new ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:clips)
        {
            if(!clip.enabled||!clip.contains(tick))continue;
            if(clip.kind==CameraClipDocument.Kind.VIDEO)
            {
                if(exactFrames==null)
                    throw new IllegalStateException(
                            "VIDEO export requires frame-exact decoding; "+
                            "preview frame cache is not safe for offline rendering");
                overlays.add(clip);
                continue;
            }
            if(clip.kind==CameraClipDocument.Kind.IMAGE)
            {
                String path=clip.settings.get("texture");
                java.io.File image=CameraImageCache.resolve(path);
                if(image==null||!image.isFile())
                    throw new IllegalStateException(
                            "Missing export IMAGE source: "+path);
                overlays.add(clip);
            }
            else if(clip.kind==CameraClipDocument.Kind.SUBTITLE||
                    clip.kind==CameraClipDocument.Kind.CURVE)
                overlays.add(clip);
        }
        if(overlays.isEmpty())return;
        Collections.sort(overlays,new Comparator<CameraClipDocument.Clip>()
        {
            @Override public int compare(CameraClipDocument.Clip a,
                    CameraClipDocument.Clip b)
            {return Integer.compare(a.layer,b.layer);}
        });

        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int textureUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        java.nio.IntBuffer viewport=org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0,width,height,0,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,film.framebufferObject);
            GL11.glViewport(0,0,width,height);
            GL11.glScissor(0,0,width,height);
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
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);

            for(CameraClipDocument.Clip clip:overlays)
            {
                if(clip.kind==CameraClipDocument.Kind.IMAGE)
                {
                    if(CameraImageCache.get(clip.settings.get("texture"))==null)
                        throw new IllegalStateException(
                                "Cannot decode export IMAGE: "+
                                clip.settings.get("texture"));
                    drawImage(clip,0,0,width,height,tick,null,true,
                            exactFrames);
                }
                else if(clip.kind==CameraClipDocument.Kind.VIDEO)
                {
                    // exactFrames.get() either returns the source's precise
                    // timestamp, transparent before an offset, or throws.
                    // Never ask CameraVideoFrames for an older preview frame.
                    drawImage(clip,0,0,width,height,tick,
                            exactFrames.get(clip,tick),true,exactFrames);
                }
                else if(clip.kind==CameraClipDocument.Kind.CURVE)
                    drawCurve(clip,0,0,width,height,tick);
                else
                    drawSubtitle(mc,clip,0,0,width,height,tick);
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
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
            GL11.glPopAttrib();
            GL13.glActiveTexture(textureUnit);
            GL20.glUseProgram(program);
        }
    }

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
                    clip.kind==CameraClipDocument.Kind.SUBTITLE||
                    clip.kind==CameraClipDocument.Kind.CURVE))
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
        // Film overlays must use the SAME logical GUI coordinates as the
        // world preview and 3D gizmo. Vanilla ScaledResolution follows the
        // user's Minecraft setting and used to shift media out of the film.
        int guiW=EditorGuiScale.width(mc.displayWidth,mc.displayHeight);
        int guiH=EditorGuiScale.height(mc.displayWidth,mc.displayHeight);
        int px0=EditorGuiScale.pixelEdge(previewX,film.framebufferWidth,guiW);
        int px1=EditorGuiScale.pixelEdge(previewX+previewWidth,
                film.framebufferWidth,guiW);
        int py0=EditorGuiScale.pixelEdge(previewY,film.framebufferHeight,guiH);
        int py1=EditorGuiScale.pixelEdge(previewY+previewHeight,
                film.framebufferHeight,guiH);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0,guiW,guiH,0,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,film.framebufferObject);
            GL11.glViewport(0,0,film.framebufferWidth,film.framebufferHeight);
            GL11.glScissor(px0,film.framebufferHeight-py1,
                    Math.max(1,px1-px0),Math.max(1,py1-py0));
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
                else if(clip.kind==CameraClipDocument.Kind.CURVE)
                    drawCurve(clip,previewX,previewY,previewWidth,
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
    /**
     * Keyframed film-space Curve channels, layered with IMAGE/VIDEO/SUBTITLE.
     * Multiplicative color channels and simple brightness/fade effects are
     * independent of optional OptiFine shader uniforms and BetterLights.
     * Render only into the existing native film framebuffer.
     */
    private static void drawCurve(CameraClipDocument.Clip clip,
            int px,int py,int pw,int ph,int tick)
    {
        CameraCurveSampler.Sample sample=CameraCurveSampler.sample(clip,
                CameraVisualFrameClock.sceneTime(tick));
        if(sample==null||Math.abs(sample.value)<1E-6D)return;
        double value=sample.value;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        String channel=sample.channel;
        if("red".equals(channel)||"green".equals(channel)||
                "blue".equals(channel))
        {
            GL11.glBlendFunc(GL11.GL_DST_COLOR,GL11.GL_ZERO);
            double factor=Math.max(0D,Math.min(2D,1D+value));
            // Add/subtract one color channel without affecting the others.
            GL11.glColor4d("red".equals(channel)?factor:1D,
                    "green".equals(channel)?factor:1D,
                    "blue".equals(channel)?factor:1D,1D);
        }
        else
        {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            double alpha=Math.min(1D,Math.abs(value));
            double c=value>0D&&"brightness".equals(channel)?1D:0D;
            GL11.glColor4d(c,c,c,alpha);
        }
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(px,py);
        GL11.glVertex2d(px+pw,py);
        GL11.glVertex2d(px+pw,py+ph);
        GL11.glVertex2d(px,py+ph);
        GL11.glEnd();
        GL11.glColor4f(1F,1F,1F,1F);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawImage(CameraClipDocument.Clip clip,
            int px,int py,int pw,int ph,int tick)
    {
        drawImage(clip,px,py,pw,ph,tick,null,false,null);
    }

    private static void drawImage(CameraClipDocument.Clip clip,
            int px,int py,int pw,int ph,int tick,
            DynamicTexture exactVideo,boolean offline,
            CameraExportVideoFrames exactFrames)
    {
        DynamicTexture texture;
        if(clip.kind==CameraClipDocument.Kind.VIDEO)
        {
            if(offline)texture=exactVideo;
            else
            {
                int offset=(int)number(clip,"offset",0D);
                int local=tick-clip.tick+offset;
                texture=CameraVideoFrames.get(clip.settings.get("video"),local);
            }
        }
        else texture=CameraImageCache.get(clip.settings.get("texture"));
        if(texture==null)return;

        boolean fullscreen=Boolean.parseBoolean(clip.settings.get("fullscreen"));
        // One source of truth for preview and native MP4. Fractional export
        // timestamps never duplicate the preceding 20 TPS media key.
        CameraExportPlan.Sample exact=offline?
                CameraExportRenderClock.current():null;
        double sceneTime=exact==null?tick:exact.sceneTime();
        java.util.Map<String,Double> animated=
                CameraMediaAnimation.sample(clip,sceneTime);
        double x=animated.get("x");
        double y=animated.get("y");
        double w=animated.get("width");
        double h=animated.get("height");
        // Placement coordinates use normalized fractions relative to the
        // film viewport; half/center values place the overlay at the center.
        double left=fullscreen?px:px+(x-w/2D)*pw;
        double top=fullscreen?py:py+(y-h/2D)*ph;
        double right=fullscreen?px+pw:left+w*pw;
        double bottom=fullscreen?py+ph:top+h*ph;
        if(right<=left||bottom<=top)return;

        // Source pixels remain at full image resolution in DynamicTexture;
        // geometry must respect their native aspect ratio when the editor
        // is switched from 16:9 to 9:16. Treat Width/Height as a box.
        // IMAGE and VIDEO now share source-aspect fitting in both preview
        // and offline output, including TikTok 9:16 compositions.
        int[] size=clip.kind==CameraClipDocument.Kind.IMAGE
                ?CameraImageCache.size(clip.settings.get("texture"))
                :clip.kind==CameraClipDocument.Kind.VIDEO
                ?(offline?exactFrames==null?null:exactFrames.size(clip,tick)
                         :CameraVideoFrames.size(clip.settings.get("video"),
                                 tick-clip.tick+(int)number(clip,"offset",0D)))
                :null;
        String fit=clip.settings.get("fit");
        if(fit==null)fit="contain";
        CameraImagePlacement.Rect rect=CameraImagePlacement.calculate(
                left,top,right,bottom,
                size==null?0:size[0],size==null?0:size[1],
                fit,Boolean.parseBoolean(clip.settings.get("flipX")),
                Boolean.parseBoolean(clip.settings.get("flipY")));
        left=rect.left;top=rect.top;
        right=rect.right;bottom=rect.bottom;

        float alpha=(float)clamp(animated.get("opacity"),0D,1D);
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
        // BBS-style per-clip overlay transform; do not rotate the world FBO.
        double cx=(left+right)/2D,cy=(top+bottom)/2D;
        GL11.glPushMatrix();
        try
        {
            GL11.glTranslated(cx,cy,0D);
            GL11.glRotated(animated.get("rotation"),0D,0D,1D);
            GL11.glScaled(animated.get("scaleX"),
                    animated.get("scaleY"),1D);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2d(rect.u0,rect.v0);GL11.glVertex2d(left-cx,top-cy);
            GL11.glTexCoord2d(rect.u1,rect.v0);GL11.glVertex2d(right-cx,top-cy);
            GL11.glTexCoord2d(rect.u1,rect.v1);GL11.glVertex2d(right-cx,bottom-cy);
            GL11.glTexCoord2d(rect.u0,rect.v1);GL11.glVertex2d(left-cx,bottom-cy);
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
        // Text transforms happen in GUI coordinates inside the film scissor.
        GL11.glPushMatrix();
        try
        {
            GL11.glTranslated(x,y,0D);
            GL11.glRotated(number(clip,"rotation",0D),0D,0D,1D);
            GL11.glScaled(number(clip,"scaleX",1D),
                    number(clip,"scaleY",1D),1D);
            for(int i=0;i<Math.min(32,lines.length);i++)
            {
                String line=mc.fontRenderer.trimStringToWidth(lines[i],maxWidth);
                int textWidth=mc.fontRenderer.getStringWidth(line);
                int rowY=i*lineHeight;
                if(y+rowY<py||y+rowY+lineHeight>py+ph)continue;
                if(shadow)mc.fontRenderer.drawStringWithShadow(
                        line,-textWidth/2,rowY,rgba);
                else mc.fontRenderer.drawString(line,-textWidth/2,rowY,rgba);
            }
        }
        finally {GL11.glPopMatrix();}
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

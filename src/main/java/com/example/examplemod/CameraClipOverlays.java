package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * CAMERA screen-space composition. One renderer for IMAGE, VIDEO and SUBTITLE
 * clips, after the captured Minecraft world and before the editor HUD.
 *
 * Preview video seeks run on a worker, not Minecraft's render thread.
 * Export MUST use a blocking, frame-exact pipeline in a later stage; the
 * asynchronous preview texture is deliberately not an export source.
 */
public final class CameraClipOverlays
{
    private final Map<CameraClipTrack.Clip,ImageState> imageStates=
        new IdentityHashMap<CameraClipTrack.Clip,ImageState>();
    private final Map<CameraClipTrack.Clip,VideoState> videoStates=
        new IdentityHashMap<CameraClipTrack.Clip,VideoState>();

    private static class ImageState
    {
        String path;
        ResourceLocation texture;
        int width,height;
    }

    private static final class VideoState extends ImageState
    {
        volatile BufferedImage decoded;
        volatile double requestedTime=-1;
        volatile boolean busy;
        double uploadedTime=-1;
        long lastRequest;
    }

    public void clear()
    {
        Minecraft mc=Minecraft.getMinecraft();
        for(ImageState s:imageStates.values())if(s.texture!=null)
            mc.getTextureManager().deleteTexture(s.texture);
        for(VideoState s:videoStates.values())if(s.texture!=null)
            mc.getTextureManager().deleteTexture(s.texture);
        imageStates.clear();videoStates.clear();
    }

    public void render(Minecraft mc,CameraClipTrack track,int frame,
        int left,int top,int w,int h)
    {
        if(mc==null||track==null||w<=0||h<=0)return;
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        java.nio.IntBuffer oldScissor=org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX,oldScissor);
        int sx=Math.max(1,mc.displayWidth/Math.max(1,mc.currentScreen.width));
        int sy=Math.max(1,mc.displayHeight/Math.max(1,mc.currentScreen.height));
        int xx=left*sx;
        int yy=mc.displayHeight-(top+h)*sy;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.max(0,xx),Math.max(0,yy),
            Math.max(1,w*sx),Math.max(1,h*sy));
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO);
        try
        {
            /* Overlays share one stacking order; original insertion order
             * breaks ties on a layer, as in the camera clip evaluator. */
            java.util.List<CameraClipTrack.Clip> clips=
                new java.util.ArrayList<CameraClipTrack.Clip>(track.all());
            java.util.Collections.sort(clips,java.util.Comparator.comparingInt(c->c.layer));
            for(CameraClipTrack.Clip c:clips)
            {
                if(!c.inside(frame))continue;
                switch(c.type)
                {
                case SUBTITLE:
                    drawSubtitle(mc,c,frame-c.tick,left,top,w,h);break;
                case IMAGE:
                case VIDEO:
                    drawImage(mc,c,frame-c.tick,left,top,w,h);break;
                default:break;
                }
            }
        }
        finally
        {
            GlStateManager.color(1F,1F,1F,1F);
            GlStateManager.depthMask(true);
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST);
            else GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(oldScissor.get(0),oldScissor.get(1),
                oldScissor.get(2),oldScissor.get(3));
        }
    }

    private void drawSubtitle(Minecraft mc,CameraClipTrack.Clip c,double local,
        int x,int y,int w,int h)
    {
        String value=c.text("text");
        if(value.isEmpty())return;
        float a=(float)c.alpha(local);
        int alpha=Math.max(0,Math.min(255,Math.round(255*a)));
        int ink=(alpha<<24)|0xFFFFFF;
        int xx=x+(int)(w*c.value("screenX",0.5));
        int yy=y+(int)(h*c.value("screenY",0.9));
        mc.fontRenderer.drawStringWithShadow(value,
            xx-mc.fontRenderer.getStringWidth(value)/2F,yy,ink);
    }

    private void drawImage(Minecraft mc,CameraClipTrack.Clip c,double local,
        int x,int y,int w,int h)
    {
        String path=c.text("resource");
        if(path.isEmpty())return;
        ImageState state;
        if(c.type==CameraClipTrack.Type.VIDEO)
        {
            VideoState video=videoStates.get(c);
            if(video==null){video=new VideoState();videoStates.put(c,video);}
            if(!path.equals(video.path))
            {
                if(video.texture!=null)mc.getTextureManager().deleteTexture(video.texture);
                video.path=path;video.texture=null;video.decoded=null;
                video.requestedTime=-1;video.uploadedTime=-1;
            }
            double seconds=local/20D+c.value("offset",0)/20D;
            updateVideo(mc,video,seconds);
            state=video;
        }
        else
        {
            ImageState image=imageStates.get(c);
            if(image==null){image=new ImageState();imageStates.put(c,image);}
            if(!path.equals(image.path))
            {
                if(image.texture!=null)mc.getTextureManager().deleteTexture(image.texture);
                image.path=path;image.texture=null;
                BufferedImage bitmap=readImage(mc,path);
                if(bitmap!=null)upload(mc,image,bitmap);
            }
            state=image;
        }
        if(state.texture==null||state.width<=0||state.height<=0)return;
        float alpha=(float)(c.alpha(local)*c.value("alpha",1D));
        if(alpha<=0)return;
        double scale=Math.max(0.01,c.value("scale",1));
        int dw=Math.max(1,(int)(state.width*scale));
        int dh=Math.max(1,(int)(state.height*scale));
        int px=x+(int)(w*c.value("screenX",.5))-dw/2;
        int py=y+(int)(h*c.value("screenY",.5))-dh/2;
        mc.getTextureManager().bindTexture(state.texture);
        GlStateManager.color(1F,1F,1F,Math.max(0,Math.min(1,alpha)));
        Gui.drawModalRectWithCustomSizedTexture(px,py,0,0,dw,dh,dw,dh);
        GlStateManager.color(1F,1F,1F,1F);
    }

    private static BufferedImage readImage(Minecraft mc,String path)
    {
        File file=new File(path);
        if(!file.isAbsolute())file=new File(mc.mcDataDir,path);
        if(!file.isFile())return null;
        try{return ImageIO.read(file);}
        catch(IOException error)
        {
            System.err.println("[BBS Editor][CAMERA] Image: "+error);
            return null;
        }
    }

    private static void upload(Minecraft mc,ImageState state,BufferedImage b)
    {
        if(b==null)return;
        if(state.texture!=null)mc.getTextureManager().deleteTexture(state.texture);
        state.width=b.getWidth();state.height=b.getHeight();
        DynamicTexture tex=new DynamicTexture(b);
        state.texture=mc.getTextureManager().getDynamicTextureLocation(
            "bbs_camera_overlay",tex);
    }

    /** Preview worker: frame-exact ffmpeg seek, bounded to one worker per clip. */
    private static void updateVideo(Minecraft mc,VideoState state,double seconds)
    {
        if(state.decoded!=null)
        {
            BufferedImage image=state.decoded;
            state.decoded=null;
            upload(mc,state,image);
            state.uploadedTime=state.requestedTime;
        }
        if(state.busy||seconds<0||Math.abs(seconds-state.uploadedTime)<0.025D)return;
        if(System.currentTimeMillis()-state.lastRequest<90)return;
        final String source=state.path;
        final double target=seconds;
        state.requestedTime=target;
        state.lastRequest=System.currentTimeMillis();
        state.busy=true;
        Thread worker=new Thread(()->{
            Process process=null;
            try
            {
                File file=new File(source);
                if(!file.isAbsolute())file=new File(Minecraft.getMinecraft().mcDataDir,source);
                if(!file.isFile())return;
                String ffmpeg=System.getProperty("bbs.editor.ffmpeg","ffmpeg");
                ProcessBuilder builder=new ProcessBuilder(ffmpeg,"-hide_banner",
                    "-loglevel","quiet","-ss",
                    String.format(java.util.Locale.ROOT,"%.4f",target),
                    "-i",file.getAbsolutePath(),
                    "-frames:v","1","-f","image2pipe","-vcodec","png","pipe:1");
                process=builder.start();
                BufferedImage decoded;
                try(InputStream stream=process.getInputStream())
                {decoded=ImageIO.read(stream);}
                if(decoded!=null&&target==state.requestedTime)
                    state.decoded=decoded;
            }
            catch(Exception e)
            {
                // Missing FFmpeg or an unsupported video is an optional overlay
                // failure; never break 3D preview or actor animation.
            }
            finally
            {
                if(process!=null)process.destroy();
                state.busy=false;
            }
        },"BBS Camera Video Preview");
        worker.setDaemon(true);
        worker.start();
    }
}

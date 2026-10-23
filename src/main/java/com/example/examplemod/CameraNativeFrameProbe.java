package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * EXPLICITLY OPT-IN, SINGLE-FRAME native-resolution export diagnostic.
 *
 * This class tests the dangerous part of the CAMERA video pipeline: make the
 * *actual Minecraft world render* target a framebuffer sized to the selected
 * film resolution, capture its completed color before GUI, and return the
 * user's display/framebuffer state without changing the working preview.
 *
 * This is NOT a full video recorder, nor an actor/media/physics export proof.
 * It runs only when the user launches with -Dbbs.editor.export.probe=true
 * and presses Ctrl+Shift+F9 in CAMERA. Nothing runs during normal editing.
 *
 * Render ownership: RenderTick START -> native world/OptiFine/actor pass ->
 * AnimationEditorScreen.drawScreen BEFORE editor GUI -> readback/restore.
 * RenderTick END is a fail-safe abort for missing/exceptional GUI passes.
 */
public final class CameraNativeFrameProbe
{
    private static final String FLAG="bbs.editor.export.probe";
    /** Optional IMAGE/SUBTITLE composition after native world snapshot. */
    private static final String MEDIA_FLAG="bbs.editor.export.probe.media";
    /** Bound CPU/GPU memory when diagnostic screenshots use custom 8K sizes. */
    private static final int MAX_PROBE_RGBA_BYTES=128*1024*1024;
    private static Field framebufferField;
    private static CameraExportPlan pending;
    private static CameraExportPlan running;
    private static Framebuffer original;
    private static Framebuffer exportTarget;
    private static boolean active;
    private static int originalWidth,originalHeight;
    private static int originalRead,originalDraw,originalTexture;
    private static final IntBuffer originalViewport=BufferUtils.createIntBuffer(16);
    private static volatile String lastStatus="No export probe requested";
    private static volatile boolean writerRunning;
    private static boolean sawNativeWorld;
    private static boolean wasCorrectWorldTarget;
    private static int actualWorldViewportWidth,actualWorldViewportHeight;

    private CameraNativeFrameProbe(){}

    public static boolean isEnabled()
    {
        return Boolean.getBoolean(FLAG);
    }

    public static String status()
    {
        return lastStatus;
    }

    /** Only called by an intentional CAMERA debug shortcut. */
    public static boolean request(int width,int height)
    {
        if(!isEnabled())
        {
            lastStatus="Enable -D"+FLAG+"=true to test native export";
            return false;
        }
        if(active||pending!=null||writerRunning)
        {
            lastStatus="A native export probe is already running";
            return false;
        }
        try
        {
            CameraExportPlan requested=new CameraExportPlan(width,height,20,1);
            if(requested.frameBytes>MAX_PROBE_RGBA_BYTES)
                throw new IllegalArgumentException(
                        "Single-frame probe exceeds 128 MiB RGBA limit");
            pending=requested;
            lastStatus="Native output probe armed: "+width+"x"+height;
            System.out.println("[BBS Editor][Export Probe] "+lastStatus);
            return true;
        }
        catch(RuntimeException invalid)
        {
            lastStatus="Unsupported output resolution: "+invalid.getMessage();
            return false;
        }
    }

    /**
     * Called on the GL thread at the beginning of a Minecraft world frame.
     * The normal Minecraft FBO remains allocated and must be restored before
     * the editor UI runs. Never call recursively from renderWorld().
     */
    public static void beforeWorld(Minecraft mc)
    {
        if(pending==null||active)return;
        if(mc==null||mc.world==null||
                !(mc.currentScreen instanceof AnimationEditorScreen))
        {
            pending=null;
            return;
        }
        CameraExportPlan plan=pending;
        pending=null;
        Framebuffer previous=mc.getFramebuffer();
        sawNativeWorld=false;
        wasCorrectWorldTarget=false;
        actualWorldViewportWidth=actualWorldViewportHeight=0;
        if(previous==null)
        {
            lastStatus="Minecraft has no world framebuffer";
            return;
        }
        original=previous;
        originalWidth=mc.displayWidth;
        originalHeight=mc.displayHeight;
        originalRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        originalDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        originalTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        originalViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT,originalViewport);

        boolean framebufferReplaced=false;
        try
        {
            if(!OpenGlHelper.isFramebufferEnabled())
                throw new IllegalStateException("Minecraft FBO is disabled");
            int gpuMax=GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if(plan.width>gpuMax||plan.height>gpuMax)
                throw new IllegalArgumentException("GPU texture limit "+
                        gpuMax+" is lower than "+plan.width+"x"+plan.height);

            if(framebufferField==null)
                framebufferField=ReflectionHelper.findField(Minecraft.class,
                        "framebufferMc","field_147124_at");

            // Creating/resizing a Minecraft Framebuffer mutates the active GL
            // texture, FBO, viewport and clear state. Restore before the next
            // native world render begins.
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try
            {
                if(exportTarget==null)
                    exportTarget=new Framebuffer(plan.width,plan.height,true);
                else if(exportTarget.framebufferWidth!=plan.width||
                        exportTarget.framebufferHeight!=plan.height)
                    exportTarget.createBindFramebuffer(plan.width,plan.height);
            }
            finally
            {
                GL11.glPopAttrib();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,originalRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,originalDraw);
                GL13.glActiveTexture(originalTexture);
                GL11.glViewport(originalViewport.get(0),
                        originalViewport.get(1),originalViewport.get(2),
                        originalViewport.get(3));
            }

            if(exportTarget.framebufferWidth!=plan.width||
                    exportTarget.framebufferHeight!=plan.height)
                throw new IllegalStateException("FBO allocation mismatch");
            mc.displayWidth=plan.width;
            mc.displayHeight=plan.height;
            framebufferField.set(mc,exportTarget);
            framebufferReplaced=true;
            exportTarget.bindFramebuffer(true);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=
                    GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Native export FBO incomplete");

            running=plan;
            active=true;
            lastStatus="Rendering native "+plan.width+"x"+plan.height+
                    " world frame";
            System.out.println("[BBS Editor][Export Probe] "+lastStatus);
        }
        catch(Throwable error)
        {
            lastStatus="Native target failed: "+error.getClass().getSimpleName()+
                    ": "+error.getMessage();
            System.err.println("[BBS Editor][Export Probe] "+lastStatus);
            if(framebufferReplaced||mc.displayWidth!=originalWidth||
                    mc.displayHeight!=originalHeight)
                restore(mc);
            else
            {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,originalRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,originalDraw);
                GL11.glViewport(originalViewport.get(0),
                        originalViewport.get(1),originalViewport.get(2),
                        originalViewport.get(3));
            }
            original=null;
            running=null;
            active=false;
        }
    }

    /**
     * Report the world render viewport from RenderWorldLast, independent of
     * the output pixel size (OptiFine shaders may use internal scaling).
     */
    public static void noteNativeWorld(int viewportWidth,int viewportHeight,
            Framebuffer source)
    {
        if(!active)return;
        sawNativeWorld=true;
        wasCorrectWorldTarget=source==exportTarget;
        actualWorldViewportWidth=viewportWidth;
        actualWorldViewportHeight=viewportHeight;
    }

    /**
     * Call AFTER the regular pre-GUI native frame snapshot, but BEFORE any
     * editor GUI rectangles/ScaledResolution are drawn. On errors, restore
     * even if the game rendered to an unexpected source FBO.
     */
    public static void beforeGui(Minecraft mc)
    {
        if(!active)return;
        byte[] rgba=null;
        int width=0,height=0;
        boolean withMedia=false;
        try
        {
            if(mc==null||mc.getFramebuffer()!=exportTarget||
                    !sawNativeWorld||!wasCorrectWorldTarget)
                throw new IllegalStateException(
                        "Native world render did not use our output FBO");
            if(!BbsFsWorldFrameCapture.isNativeFrameReady())
                throw new IllegalStateException(
                        "No complete pre-GUI world frame to capture");
            CameraExportPlan plan=running;
            if(plan==null)throw new IllegalStateException("Export plan lost");
            if(exportTarget.framebufferWidth!=plan.width||
                    exportTarget.framebufferHeight!=plan.height)
                throw new IllegalStateException(
                        "Native frame does not match selected output resolution");

            // A separate, explicitly enabled test of output-size
            // overlays. The default PNG remains a clean WORLD capture.
            // No video placeholder frames or GUI selection chrome are allowed.
            withMedia=Boolean.getBoolean(MEDIA_FLAG);
            if(withMedia)
            {
                AnimationEditorScreen editor=(AnimationEditorScreen)
                        mc.currentScreen;
                CameraFilmCompositor.compositeNative(mc,exportTarget,
                        editor.getSceneState().getCameraClips().clips(),
                        editor.getCurrentFrame());
            }

            ByteBuffer pixels=new CameraExportReadback(plan).capture(
                    exportTarget,0,0,plan.width,plan.height);
            rgba=new byte[plan.frameBytes];
            pixels.get(rgba);
            width=plan.width;height=plan.height;
            System.out.println("[BBS Editor][Export Probe] FBO "+
                    width+"x"+height+", world viewport "+
                    actualWorldViewportWidth+"x"+actualWorldViewportHeight+
                    ", actors rendered="+
                    BbsFsNativeActorPass.didRenderThisFrame());
        }
        catch(Throwable error)
        {
            lastStatus="Native readback failed: "+
                    error.getClass().getSimpleName()+": "+error.getMessage();
            System.err.println("[BBS Editor][Export Probe] "+lastStatus);
        }
        finally
        {
            restore(mc);
            active=false;
            running=null;
            original=null;
        }
        if(rgba!=null)
            saveImageAsync(mc,rgba,width,height,withMedia);
    }

    /**
     * Failsafe for a skipped/destroyed GUI pass. A native framebuffer must
     * NEVER remain bound between game frames after a failed probe.
     */
    public static void afterRenderTick(Minecraft mc)
    {
        if(!active)return;
        lastStatus="Native probe aborted: editor GUI pass did not finish";
        System.err.println("[BBS Editor][Export Probe] "+lastStatus);
        restore(mc);
        active=false;
        original=null;
        running=null;
    }

    /** Called on editor close, never while a target is still active. */
    public static void close(Minecraft mc)
    {
        if(active)afterRenderTick(mc);
        pending=null;
        if(exportTarget==null)return;
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try{exportTarget.deleteFramebuffer();}
        finally
        {
            exportTarget=null;
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
        }
    }

    private static void restore(Minecraft mc)
    {
        try
        {
            if(mc!=null)
            {
                mc.displayWidth=originalWidth;
                mc.displayHeight=originalHeight;
                if(framebufferField!=null&&original!=null)
                    framebufferField.set(mc,original);
            }
        }
        catch(Throwable error)
        {
            System.err.println("[BBS Editor][Export Probe] FATAL framebuffer "+
                    "restore error: "+error);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,originalRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,originalDraw);
            GL11.glViewport(originalViewport.get(0),
                    originalViewport.get(1),originalViewport.get(2),
                    originalViewport.get(3));
            GL13.glActiveTexture(originalTexture);
        }
    }

    private static void saveImageAsync(Minecraft mc,final byte[] rgba,
            final int width,final int height,boolean composited)
    {
        final File game=mc==null?new File("."):mc.mcDataDir;
        final File folder=new File(game,"config/bbs-editor/export-probes");
        final File output=new File(folder,(composited?"film-":"world-")+
                width+"x"+height+
                "-"+System.currentTimeMillis()+".png");
        writerRunning=true;
        Thread writer=new Thread(new Runnable()
        {
            @Override public void run()
            {
                try
                {
                    if(!folder.isDirectory()&&!folder.mkdirs())
                        throw new IOException("Cannot create "+folder);
                    BufferedImage image=new BufferedImage(width,height,
                            BufferedImage.TYPE_INT_RGB);
                    int[] result=((DataBufferInt)image.getRaster().
                            getDataBuffer()).getData();
                    for(int y=0;y<height;y++)
                    {
                        int source=(height-y-1)*width*4;
                        int dest=y*width;
                        for(int x=0;x<width;x++)
                        {
                            int offset=source+x*4;
                            int r=rgba[offset]&255,g=rgba[offset+1]&255;
                            int b=rgba[offset+2]&255;
                            result[dest+x]=(r<<16)|(g<<8)|b;
                        }
                    }
                    if(!ImageIO.write(image,"png",output))
                        throw new IOException("PNG writer unavailable");
                    lastStatus="Native world PNG: "+output.getAbsolutePath();
                    System.out.println("[BBS Editor][Export Probe] "+lastStatus);
                }
                catch(Throwable error)
                {
                    lastStatus="PNG write failed: "+error.getMessage();
                    System.err.println("[BBS Editor][Export Probe] "+lastStatus);
                }
                finally {writerRunning=false;}
            }
        },"BBS-CAMERA-png-probe");
        writer.setDaemon(true);
        writer.start();
    }
}

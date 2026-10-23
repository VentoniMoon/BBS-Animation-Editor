package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL13;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Native export framebuffer save/capture/restore state without real GPU. */
public final class CameraNativeFrameProbeSmokeTest
{
    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception
    {
        System.setProperty("bbs.editor.export.probe","true");
        Minecraft mc=new Minecraft();
        mc.currentScreen=new AnimationEditorScreen();
        mc.mcDataDir=Files.createTempDirectory("bbs-native-png-test").toFile();
        Framebuffer original=mc.framebufferMc;
        GL30.read=GL30.draw=original.framebufferObject;
        GL11.glViewport(0,0,800,600);
        // Regression: START RENDER preflight fails before native FBO capture.
        // The CAMERA export modal must still force a fresh atlas binding.
        GlStateManager.cachedTexture=17;
        GL11.boundTexture=73;
        GL11.texture2dEnabled=false;
        GlStateManager.cachedTexture2dEnabled=false;
        GL13.glActiveTexture(33985);
        CameraNativeFrameProbe.resyncDefaultTextureForGui();
        check(GL13.active==OpenGlHelper.defaultTexUnit&&
                GlStateManager.cachedTexture==0&&GL11.boundTexture==0&&
                GlStateManager.cachedTexture2dEnabled&&GL11.texture2dEnabled,
                "GUI font must be restored even without starting native capture");
        GlStateManager.bindTexture(42);
        check(GL11.boundTexture==42,
                "font glyph atlas must rebind after a failed encoder preflight");
        check(CameraNativeFrameProbe.request(128,128),"enable a valid probe");
        CameraNativeFrameProbe.beforeWorld(mc);
        check(mc.getFramebuffer()!=original,"native output framebuffer swapped");
        check(mc.displayWidth==128&&mc.displayHeight==128,
                "world must render at selected pixel resolution");
        Framebuffer target=mc.getFramebuffer();
        check(target.framebufferWidth==128&&target.framebufferHeight==128,
                "world FBO is not a scaled-up display screenshot");
        CameraNativeFrameProbe.noteNativeWorld(128,128,target);
        // Simulate a font atlas cached by GlStateManager while a film
        // overlay binds a different GL texture without informing that cache.
        GlStateManager.bindTexture(42);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,51);
        GL13.glActiveTexture(33985);
        CameraNativeFrameProbe.beforeGui(mc);
        check(GL13.active==OpenGlHelper.defaultTexUnit&&
                GL11.boundTexture==0&&GlStateManager.cachedTexture==0,
                "native export must restore real and cached font texture state");
        GlStateManager.bindTexture(42);
        check(GL11.boundTexture==42,
                "FontRenderer must rebind its atlas after CAMERA render");
        check(mc.getFramebuffer()==original,"original Minecraft FBO restored");
        check(mc.displayWidth==800&&mc.displayHeight==600,
                "Minecraft window dimensions restored");
        check(GL30.read==original.framebufferObject&&
                GL30.draw==original.framebufferObject,
                "GL read/draw FBO bindings restored");
        check(GL11.viewport[2]==800&&GL11.viewport[3]==600,
                "original GL viewport restored");

        long timeout=System.currentTimeMillis()+8000L;
        File folder=new File(mc.mcDataDir,"config/bbs-editor/export-probes");
        File image=null;
        while(System.currentTimeMillis()<timeout)
        {
            File[] files=folder.listFiles();
            if(files!=null&&files.length==1&&
                    CameraNativeFrameProbe.status().startsWith("Native world PNG:"))
            {
                image=files[0];break;
            }
            Thread.sleep(25L);
        }
        check(image!=null&&image.isFile(),"PNG probe must write an image");
        BufferedImage png=ImageIO.read(image);
        check(png!=null&&png.getWidth()==128&&png.getHeight()==128,
                "native PNG dimensions must exactly match selected output");
        check(((png.getRGB(0,0)>>16)&255)==200,
                "image must contain RGBA readback, not editor GUI");
        check(image.delete(),"remove test PNG");

        // The optional film pass must composite IMAGE/SUBTITLE only AFTER
        // native world snapshot and BEFORE RGBA readback, without touching
        // the Minecraft GUI or leaving its FBO rebound.
        System.setProperty("bbs.editor.export.probe.media","true");
        check(CameraNativeFrameProbe.request(128,128),
                "arm film overlay probe");
        CameraNativeFrameProbe.beforeWorld(mc);
        CameraNativeFrameProbe.noteNativeWorld(128,128,mc.getFramebuffer());
        CameraNativeFrameProbe.beforeGui(mc);
        check(CameraFilmCompositor.nativeCalls==1,
                "film compositor called once at native output resolution");
        check(mc.getFramebuffer()==original&&mc.displayWidth==800,
                "film composition restored original Minecraft renderer");
        File composited=null;
        timeout=System.currentTimeMillis()+8000L;
        while(System.currentTimeMillis()<timeout)
        {
            File[] files=folder.listFiles();
            if(files!=null&&files.length==1&&
                    CameraNativeFrameProbe.status().startsWith("Native film PNG:"))
            {
                composited=files[0];break;
            }
            Thread.sleep(25L);
        }
        check(composited!=null&&composited.getName().startsWith("film-"),
                "composited PNG is clearly distinct from clean world PNG");
        check(composited.delete(),"remove composited PNG");

        // A video-clip exact-timestamp failure must not falsely report
        // successful PNG export or leave the substituted GPU FBO active.
        CameraFilmCompositor.failNext=true;
        check(CameraNativeFrameProbe.request(128,128),
                "arm unsupported VIDEO test");
        CameraNativeFrameProbe.beforeWorld(mc);
        CameraNativeFrameProbe.noteNativeWorld(128,128,mc.getFramebuffer());
        CameraNativeFrameProbe.beforeGui(mc);
        check(CameraNativeFrameProbe.status().contains("failed"),
                "missing exact VIDEO decoder explicitly fails offline film");
        check(mc.getFramebuffer()==original&&mc.displayHeight==600,
                "VIDEO failure restores native world FBO");
        System.clearProperty("bbs.editor.export.probe.media");

        check(CameraNativeFrameProbe.request(128,128),"arm second frame");
        CameraNativeFrameProbe.beforeWorld(mc);
        check(mc.getFramebuffer()!=original,"second native FBO");
        CameraNativeFrameProbe.afterRenderTick(mc);
        check(mc.getFramebuffer()==original&&mc.displayWidth==800,
                "render tick abort must restore native framebuffer/window");
        check(CameraNativeFrameProbe.status().contains("aborted"),
                "missing GUI explicitly reported");

        check(CameraNativeFrameProbe.request(128,128),"arm third frame");
        CameraNativeFrameProbe.beforeWorld(mc);
        CameraNativeFrameProbe.noteNativeWorld(128,128,original);
        CameraNativeFrameProbe.beforeGui(mc);
        check(mc.getFramebuffer()==original&&mc.displayHeight==600,
                "unexpected OptiFine world target must safely restore");
        check(CameraNativeFrameProbe.status().contains("failed"),
                "unexpected world framebuffer is not claimed as rendered");

        // Simulate two actual CAMERA output frames, using the SAME native
        // FBO redirection/readback that will feed FFmpeg during real gameplay.
        // A fake sink lets us verify FBO/timeline ownership with no GPU.
        final java.util.concurrent.atomic.AtomicInteger encoded=
                new java.util.concurrent.atomic.AtomicInteger();
        CameraExportPlan videoPlan=new CameraExportPlan(128,128,20,2);
        CameraExportSession.FrameSink sink=new CameraExportSession.FrameSink()
        {
            @Override public void writeFrame(java.nio.ByteBuffer rgba)
            {
                check(rgba.remaining()==128*128*4,
                        "MP4 receives full-resolution native RGBA pixels");
                check((rgba.get(0)&255)==200,
                        "MP4 frame is real world pixels, not a GUI frame");
                encoded.incrementAndGet();
            }
            @Override public void finish(){}
            @Override public void cancel(){}
        };
        try(CameraExportSession video=new CameraExportSession(videoPlan,sink))
        {
            CameraExportFrameBridge bridge=new CameraExportFrameBridge(video);
            for(int index=0;index<2;index++)
            {
                long deadline=System.currentTimeMillis()+5000L;
                CameraExportPlan.Sample frame=bridge.nextFrame();
                while(frame==null&&
                        System.currentTimeMillis()<deadline)
                {
                    Thread.sleep(5L);
                    frame=bridge.nextFrame();
                }
                check(frame!=null&&frame.outputFrame==index,
                        "exporter requests each expected scene frame");
                check(CameraNativeFrameProbe.requestVideoFrame(bridge,frame),
                        "video frame uses the native framebuffer owner");
                CameraNativeFrameProbe.beforeWorld(mc);
                check(mc.getFramebuffer()!=original&&mc.displayWidth==128,
                        "MP4 uses real output-size native FBO");
                CameraNativeFrameProbe.noteNativeWorld(128,128,
                        mc.getFramebuffer());
                CameraNativeFrameProbe.beforeGui(mc);
                check(mc.getFramebuffer()==original&&mc.displayWidth==800,
                        "MP4 restores Minecraft FBO before GUI");
                check(!CameraNativeFrameProbe.isBusy(),
                        "native framebuffer released after video capture");
            }
            check(video.await(5,java.util.concurrent.TimeUnit.SECONDS),
                    "video worker finishes two scene frames");
            check(video.getStatus()==CameraExportSession.Status.COMPLETED,
                    "two native frames completed successfully");
            check(encoded.get()==2,
                    "native world film produced exactly two encoded frames");
            check(folder.listFiles()!=null&&folder.listFiles().length==0,
                    "MP4 render never creates PNG intermediates");
        }

        // Complete native FBO -> glReadPixels -> bounded Java queue ->
        // real FFmpeg/libx264 -> verified MP4. No PNG files are involved.
        File mp4=new File(mc.mcDataDir,"native-world.mp4");
        try(CameraExportSession actualVideo=CameraExportSession.openFfmpeg(
                videoPlan,null,mp4,18,"ultrafast"))
        {
            CameraExportFrameBridge actualBridge=
                    new CameraExportFrameBridge(actualVideo);
            for(int i=0;i<2;i++)
            {
                long deadline=System.currentTimeMillis()+5000L;
                CameraExportPlan.Sample next=actualBridge.nextFrame();
                while(next==null&&System.currentTimeMillis()<deadline)
                {
                    Thread.sleep(5L);
                    next=actualBridge.nextFrame();
                }
                check(next!=null&&next.outputFrame==i,
                        "real MP4 step requests exact native world frame");
                check(CameraNativeFrameProbe.requestVideoFrame(
                        actualBridge,next),"arm real FFmpeg frame");
                CameraNativeFrameProbe.beforeWorld(mc);
                CameraNativeFrameProbe.noteNativeWorld(128,128,
                        mc.getFramebuffer());
                CameraNativeFrameProbe.beforeGui(mc);
                check(mc.getFramebuffer()==original,
                        "real FFmpeg step restores world framebuffer");
            }
            check(actualVideo.await(20,java.util.concurrent.TimeUnit.SECONDS),
                    "native-FBO-to-FFmpeg MP4 finalizes");
            check(actualVideo.getStatus()==CameraExportSession.Status.COMPLETED,
                    "native MP4 export completes with FFprobe validation: "+
                            actualVideo.getFailure());
        }
        check(mp4.isFile()&&mp4.length()>100L,
                "real video bytes written from native world pixels");
        CameraExportVerifier.verify(null,mp4,videoPlan);
        check(mp4.delete(),"remove actual native MP4 fixture");

        CameraExportSession canceledVideo=new CameraExportSession(
                new CameraExportPlan(128,128,20,2),sink);
        CameraExportFrameBridge abortBridge=new CameraExportFrameBridge(
                canceledVideo);
        check(CameraNativeFrameProbe.requestVideoFrame(abortBridge,
                abortBridge.nextFrame()),"arm failed native video pass");
        CameraNativeFrameProbe.beforeWorld(mc);
        CameraNativeFrameProbe.afterRenderTick(mc);
        check(mc.getFramebuffer()==original&&
                canceledVideo.getStatus()==CameraExportSession.Status.CANCELED,
                "skipped GUI pass cancels video and restores Minecraft");
        canceledVideo.close();

        check(!CameraNativeFrameProbe.request(8192,8192),
                "reject unsafe large single-frame allocations");
        OpenGlHelper.enabled=false;
        check(CameraNativeFrameProbe.request(128,128),
                "arm disabled FBO test");
        CameraNativeFrameProbe.beforeWorld(mc);
        check(mc.getFramebuffer()==original&&mc.displayWidth==800,
                "disabled Minecraft FBO leaves world unchanged");
        OpenGlHelper.enabled=true;
        CameraNativeFrameProbe.close(mc);

        System.clearProperty("bbs.editor.export.probe");
        check(!CameraNativeFrameProbe.request(128,128),
                "probe cannot run without explicit JVM opt-in");
        File config=new File(mc.mcDataDir,"config/bbs-editor/export-probes");
        check(config.delete(),"delete empty probe dir");
        check(config.getParentFile().delete(),"delete bbs-editor");
        check(config.getParentFile().getParentFile().delete(),"delete config");
        check(mc.mcDataDir.delete(),"delete test game dir");
        System.out.println("CAMERA native FBO probe PNG/restore/failure PASS");
    }
}

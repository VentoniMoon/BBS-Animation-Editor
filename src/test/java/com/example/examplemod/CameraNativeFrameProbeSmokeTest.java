package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
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
        check(CameraNativeFrameProbe.request(128,128),"enable a valid probe");
        CameraNativeFrameProbe.beforeWorld(mc);
        check(mc.getFramebuffer()!=original,"native output framebuffer swapped");
        check(mc.displayWidth==128&&mc.displayHeight==128,
                "world must render at selected pixel resolution");
        Framebuffer target=mc.getFramebuffer();
        check(target.framebufferWidth==128&&target.framebufferHeight==128,
                "world FBO is not a scaled-up display screenshot");
        CameraNativeFrameProbe.noteNativeWorld(128,128,target);
        CameraNativeFrameProbe.beforeGui(mc);
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

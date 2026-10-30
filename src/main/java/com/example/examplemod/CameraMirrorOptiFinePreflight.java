package com.example.examplemod;

import java.util.List;
import java.util.Arrays;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Explicitly opt-in BSL GBuffer preflight. Runs on the normal Minecraft GL
 * render thread while the mirror is still safely showing the blue fallback.
 *
 * It confirms attachment formats, completeness and exact shader DRAWBUFFERS
 * metadata BEFORE any reflective deferred/composite rendering is enabled.
 * The allocated framebuffer and textures are deleted in the same call.
 * No OptiFine global state is changed and no world or actors are redrawn.
 */
public final class CameraMirrorOptiFinePreflight
{
    private static boolean done;
    private static String result="Not tested";
    private CameraMirrorOptiFinePreflight(){}

    public static String result(){return result;}

    public static void runOnce()
    {
        if(done||!Boolean.getBoolean(
                "bbs.editor.camera.mirrorBSLPreflight"))return;
        done=true;
        String prefix="[BBS Editor][Mirror][BSL Preflight] ";
        try
        {
            CameraMirrorOptiFineBackend.Support support=
                    CameraMirrorOptiFineBackend.inspect();
            if(support.usable)
            {
                result="Forward pack: MRT preflight not required";
                System.out.println(prefix+result);
                return;
            }
            // Do not probe other incompatible packs using BSL dimensions.
            if(!support.message.contains("BSL_v8.2.02-BetterLights.zip"))
            {
                result="SKIP: not the verified BSL 8.2.02 BetterLights pack";
                System.out.println(prefix+result);
                return;
            }
            int colors=8;
            int depths=3;
            int[] formats=CameraMirrorOptiFineBackend.getColorFormats(colors);
            List<CameraMirrorCompositePlan> plans=
                    CameraMirrorOptiFinePlanning.inspect(colors);
            System.out.println(prefix+"native formats="+
                    Arrays.toString(formats)+" stages="+plans.size());
            for(CameraMirrorCompositePlan plan:plans)
                System.out.println(prefix+plan);
            // Verify texture format/GL routing while preserving the main
            // native read/draw FBO and viewport, even if a stage fails.
            int previousRead=GL11.glGetInteger(
                    GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw=GL11.glGetInteger(
                    GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            IntBuffer previousViewport=BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_VIEWPORT,previousViewport);
            try
            {
                try(CameraMirrorOptiFineTargets targets=
                        CameraMirrorOptiFineTargets.allocate(
                                1024,576,colors,depths,formats))
                {
                    targets.bindGeometry();
                    for(CameraMirrorCompositePlan plan:plans)
                    {
                        targets.bindCompositeOutputs(plan);
                        targets.bindGeometry();
                    }
                    result="PASS: private "+targets.width()+"x"+
                            targets.height()+" MRT 8 colors / 3 depth, "+
                            plans.size()+" shader stage mappings validated";
                    System.out.println(prefix+result);
                }
            }
            finally
            {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,previousDraw);
                GL11.glViewport(previousViewport.get(0),
                        previousViewport.get(1),
                        previousViewport.get(2),
                        previousViewport.get(3));
                GL13.glActiveTexture(previousUnit);
            }
        }
        catch(Throwable error)
        {
            result="FAILED: "+error.getClass().getSimpleName()+
                    ": "+error.getMessage();
            System.err.println(prefix+result);
            error.printStackTrace();
        }
    }

    public static void reset()
    {
        done=false;
        result="Not tested";
    }
}

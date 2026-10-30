package com.example.examplemod;

import java.util.List;
import java.util.Arrays;

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
            // This diagnostics-only stage currently targets the confirmed
            // BSL 8.2.02 BetterLights configuration from the user's log.
            int colors=8;
            int depths=3;
            int[] formats=CameraMirrorOptiFineBackend.getColorFormats(colors);
            List<CameraMirrorCompositePlan> plans=
                    CameraMirrorOptiFinePlanning.inspect(colors);
            System.out.println(prefix+"native formats="+
                    Arrays.toString(formats)+" stages="+plans.size());
            for(CameraMirrorCompositePlan plan:plans)
                System.out.println(prefix+plan);
            // Allocation only; no shader writes or invocations. Building
            // the resources must not mutate the normal OptiFine framebuffer.
            try(CameraMirrorOptiFineTargets targets=
                    CameraMirrorOptiFineTargets.allocate(
                            1024,576,colors,depths,formats))
            {
                targets.bindGeometry();
                // FBO has 8 correct native attachment formats.
                // Test a transient per-shader routing then restore geometry.
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

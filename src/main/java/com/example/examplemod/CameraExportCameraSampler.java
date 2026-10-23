package com.example.examplemod;

/**
 * Preview camera sampling at fractional scene ticks for 24/30/60 FPS output.
 *
 * The existing CAMERA evaluator currently accepts integer 20 TPS ticks.
 * Until every clip has a continuous-time evaluator, interpolate the two
 * adjacent evaluated poses, but NEVER interpolate across a clip activation
 * boundary: a hard camera cut must remain a cut, not become a one-tick pan.
 *
 * This is a camera-only sampling bridge. It neither samples actor poses
 * nor advances Jolt. A later continuous evaluator can replace it without
 * changing the export session's rational frame clock.
 */
public final class CameraExportCameraSampler
{
    private CameraExportCameraSampler(){}

    public static PreviewCameraPose sample(CameraClipDocument document,
            CameraExportPlan plan,CameraExportPlan.Sample frame,
            PreviewCameraPose fallback,
            CameraClipDocument.TargetResolver targets)
    {
        if(document==null||plan==null||frame==null||fallback==null)
            throw new IllegalArgumentException(
                    "Missing offline CAMERA sampling inputs");
        int tick=frame.sceneTick;
        if(tick<0||tick>=plan.sceneTicks)
            throw new IllegalArgumentException(
                    "Requested camera tick outside the export scene");
        PreviewCameraPose start=document.sample(tick,fallback,targets);
        if(frame.tickFraction<=0D||tick+1>=plan.sceneTicks)
            return start;

        // A changed active camera stack is an intentional cut or modifier
        // onset/off-switch, not a continuous motion. Never tween across it.
        for(CameraClipDocument.Clip clip:document.clips())
        {
            if(!clip.enabled||!changesCamera(clip.kind))continue;
            if(clip.contains(tick)!=clip.contains(tick+1))
                return start;
        }
        PreviewCameraPose end=document.sample(tick+1,fallback,targets);
        return PreviewCameraPose.interpolate(start,end,frame.tickFraction);
    }

    private static boolean changesCamera(CameraClipDocument.Kind kind)
    {
        return kind!=CameraClipDocument.Kind.IMAGE&&
                kind!=CameraClipDocument.Kind.VIDEO&&
                kind!=CameraClipDocument.Kind.SUBTITLE&&
                kind!=CameraClipDocument.Kind.AUDIO;
    }
}

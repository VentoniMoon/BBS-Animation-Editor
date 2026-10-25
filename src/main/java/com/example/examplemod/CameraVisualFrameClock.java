package com.example.examplemod;

/**
 * Per-render fractional sample of the ordinary Minecraft 20 TPS scene.
 *
 * The timeline, Character actions, Emoticons Animator and Jolt solver still
 * advance only on logical ticks; rendering never mutates their clocks. The
 * offline exporter owns its independent exact sample and wins over preview.
 * A physics bake must never use an interpolated pose as a solver input.
 */
public final class CameraVisualFrameClock
{
    private static int previewTick=-1;
    private static float previewFraction;

    public static void beginPreview(int tick,float fraction,boolean playing,
            boolean baking)
    {
        if(!playing||baking||tick<0)
        {
            clearPreview();
            return;
        }
        previewTick=tick;
        previewFraction=Float.isFinite(fraction)
                ?Math.max(0F,Math.min(0.999999F,fraction)):0F;
    }

    public static void clearPreview()
    {
        previewTick=-1;
        previewFraction=0F;
    }

    public static double fraction(int sceneTick)
    {
        CameraExportPlan.Sample export=CameraExportRenderClock.current();
        if(export!=null)
            return export.sceneTick==sceneTick?export.tickFraction:0D;
        return previewTick==sceneTick?previewFraction:0D;
    }

    public static double sceneTime(int sceneTick)
    {
        return sceneTick+fraction(sceneTick);
    }

    public static float animationFrame(int sceneTick)
    {
        return (float)sceneTime(sceneTick);
    }

    public static float partialTick(int sceneTick,float fallback)
    {
        CameraExportPlan.Sample export=CameraExportRenderClock.current();
        if(export!=null)
            return export.sceneTick==sceneTick?(float)export.tickFraction:0F;
        if(previewTick==sceneTick)return previewFraction;
        return Float.isFinite(fallback)?Math.max(0F,Math.min(1F,fallback)):0F;
    }

    /** Whether the renderer is receiving a sub-tick sampled Record pose. */
    public static boolean hasSample(int sceneTick)
    {
        CameraExportPlan.Sample export=CameraExportRenderClock.current();
        return export!=null&&export.sceneTick==sceneTick ||
                previewTick==sceneTick;
    }

    private CameraVisualFrameClock(){}
}

package com.example.examplemod;

/**
 * Native render-thread ownership of one precise film timestamp.
 * Minecraft/Jolt still advance in integer 20 TPS steps; visual animation,
 * camera, and VIDEO sampling may use the fractional output timestamp.
 * This state is never visible to regular editor preview or scene storage.
 */
public final class CameraExportRenderClock
{
    private static CameraExportPlan.Sample active;

    public static void set(CameraExportPlan.Sample sample)
    {
        active=sample;
    }

    public static CameraExportPlan.Sample current()
    {
        return active;
    }

    public static float animationFrame(int frame)
    {
        CameraExportPlan.Sample sample=active;
        return sample!=null&&sample.sceneTick==frame
                ?(float)sample.sceneTime():(float)frame;
    }

    public static float partialTick(int frame,float vanilla)
    {
        CameraExportPlan.Sample sample=active;
        return sample!=null&&sample.sceneTick==frame
                ?(float)sample.tickFraction:vanilla;
    }

    public static void clear()
    {
        active=null;
    }

    private CameraExportRenderClock(){}
}

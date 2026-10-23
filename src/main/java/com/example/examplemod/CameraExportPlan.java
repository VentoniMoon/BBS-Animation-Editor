package com.example.examplemod;

/**
 * Immutable plan for an offline CAMERA render. This class deliberately has no
 * Minecraft, OpenGL, Java Sound or FFmpeg dependencies, so 20 TPS timeline
 * mapping can be tested before wiring the native world render loop.
 *
 * A scene has sceneTicks samples (0..sceneTicks-1) at the usual 20 TPS.
 * Output frames are sampled at constant FPS without relying on wall time.
 * Sampling beyond an integer tick requires pose interpolation by the caller:
 * rendering the same tick three times at 60 FPS would be incorrect.
 */
public final class CameraExportPlan
{
    public static final int TICKS_PER_SECOND=20;
    public static final int MAX_FRAMES=3_600_000;
    /**
     * One 4K RGBA frame is ~32 MiB. The encoder keeps two CPU frame slots,
     * in addition to a GPU texture and one direct readback buffer. Bound the
     * frame footprint before allocating queues or native OpenGL storage.
     */
    public static final long MAX_RGBA_FRAME_BYTES=64L*1024L*1024L;

    public static final class Sample
    {
        public final int outputFrame;
        public final int sceneTick;
        public final double tickFraction;
        public final double seconds;

        private Sample(int outputFrame,int sceneTick,double tickFraction,
                double seconds)
        {
            this.outputFrame=outputFrame;
            this.sceneTick=sceneTick;
            this.tickFraction=tickFraction;
            this.seconds=seconds;
        }

        public double sceneTime()
        {
            return sceneTick+tickFraction;
        }
    }

    public final int width;
    public final int height;
    public final int fps;
    public final int sceneTicks;
    public final int frameCount;
    public final int frameBytes;

    /**
     * @param sceneTicks the editor's scene length in 20 TPS timeline ticks.
     */
    public CameraExportPlan(int width,int height,int fps,int sceneTicks)
    {
        if(width<128||height<128||width>8192||height>8192||
                (width&1)!=0||(height&1)!=0||
                width/(double)height<0.2D||width/(double)height>5D)
            throw new IllegalArgumentException(
                    "CAMERA export requires an even 128..8192 pixel frame");
        if(fps<1||fps>240)
            throw new IllegalArgumentException("Export FPS must be 1..240");
        if(sceneTicks<1)
            throw new IllegalArgumentException("Empty CAMERA scene");

        long count=((long)sceneTicks*fps+TICKS_PER_SECOND-1L)/
                TICKS_PER_SECOND;
        if(count<1L||count>MAX_FRAMES)
            throw new IllegalArgumentException("Export exceeds frame limit");

        long bytes=(long)width*height*4L;
        if(bytes>MAX_RGBA_FRAME_BYTES)
            throw new IllegalArgumentException(
                    "Export RGBA frame exceeds 64 MiB safety limit; "+
                    "reduce the requested resolution");

        this.width=width;
        this.height=height;
        this.fps=fps;
        this.sceneTicks=sceneTicks;
        this.frameCount=(int)count;
        this.frameBytes=(int)bytes;
    }

    public Sample sample(int outputFrame)
    {
        if(outputFrame<0||outputFrame>=frameCount)
            throw new IndexOutOfBoundsException("Export frame "+outputFrame+
                    " of "+frameCount);
        long numerator=(long)outputFrame*TICKS_PER_SECOND;
        int tick=(int)(numerator/fps);
        double fraction=(numerator%fps)/(double)fps;
        if(tick>=sceneTicks)
            throw new IllegalStateException("Frame outside scene time");
        return new Sample(outputFrame,tick,fraction,
                outputFrame/(double)fps);
    }

    public double durationSeconds()
    {
        return sceneTicks/(double)TICKS_PER_SECOND;
    }
}

package com.example.examplemod;

import java.nio.ByteBuffer;
import net.minecraft.client.shader.Framebuffer;

/**
 * Strict seam between a future native offscreen render and the tested FFmpeg
 * export queue. It never renders Minecraft, never samples the editor GUI
 * and never advances the timeline automatically.
 *
 * Intended render-thread loop:
 *   1) ask for the next Sample (if null, render nothing / retain time);
 *   2) render scene + media at Sample.sceneTime() into an output-size FBO;
 *   3) after composition, call commit() with that same sample and film FBO;
 *   4) only commit success permits advancing to the next scene timestamp.
 *
 * The framebuffer must contain true native pixels at output resolution:
 * the readback refuses to upscale a small preview or silently crop actors.
 */
public final class CameraExportFrameBridge
{
    private final CameraExportSession session;
    private final CameraExportReadback readback;

    public CameraExportFrameBridge(CameraExportSession session)
    {
        if(session==null)
            throw new IllegalArgumentException("Missing export session");
        this.session=session;
        this.readback=new CameraExportReadback(session.getPlan());
    }

    public CameraExportPlan.Sample nextFrame()
    {
        return session.nextSample();
    }

    public boolean commit(CameraExportPlan.Sample sampled,
            Framebuffer completedFilm,int glX,int glY,
            int physicalWidth,int physicalHeight)
    {
        if(sampled==null)
            throw new IllegalArgumentException("Missing rendered timestamp");
        CameraExportPlan.Sample requested=session.nextSample();
        if(requested==null)return false;
        if(sampled.outputFrame!=requested.outputFrame)
            throw new IllegalArgumentException(
                    "Rendered output frame "+sampled.outputFrame+
                    " but next required frame is "+requested.outputFrame);
        ByteBuffer rgba=readback.capture(completedFilm,glX,glY,
                physicalWidth,physicalHeight);
        return session.submitFrame(sampled.outputFrame,rgba);
    }

    public CameraExportSession.Status status()
    {
        return session.getStatus();
    }

    public int encodedFrames(){return session.getEncodedFrames();}
    public int capturedFrames(){return session.getCapturedFrames();}
    public int totalFrames(){return session.getPlan().frameCount;}
    public void cancel(){session.cancel();}
}

package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.minecraft.client.Minecraft;

/**
 * Experimental, user-initiated, end-to-end CAMERA -> native Minecraft
 * world -> film framebuffer -> FFmpeg MP4 export.
 *
 * This first bridge intentionally exports at 20 FPS only, one exact
 * Minecraft scene tick per output frame. Higher FPS must wait for proper
 * actor/Jolt and procedural clip fractional-time support; repeating ticks
 * three times and labelling the MP4 "60 FPS" would be misleading.
 *
 * All render and GL work occurs in RenderTick/GuiScreen on the client thread.
 * The asynchronous encoder/FFprobe worker never accesses Minecraft state.
 * At most one output frame is requested per native world render and all
 * scene/GL resources are restored even on cancellation or failure.
 */
public final class CameraVideoRenderController
{
    public static final int INITIAL_VIDEO_FPS=20;

    private CameraExportSession session;
    private CameraExportFrameBridge bridge;
    private AnimationEditorScreen owner;
    private int previousFrame;
    private boolean previousPlaying;
    private File output;
    private String message="";

    public boolean isExporting()
    {
        return session!=null;
    }

    public String status()
    {
        if(session==null)return message;
        return "CAMERA MP4 20 FPS  "+session.getEncodedFrames()+"/"+
                session.getPlan().frameCount+" frames (captured "+
                session.getCapturedFrames()+") — Ctrl+Shift+F8 cancels";
    }

    /**
     * Starts an actual MP4 render, not a PNG capture. Called by Ctrl+Shift+F8
     * while the CAMERA editor is open. The first frame is requested on the
     * NEXT native Minecraft render tick, never by reentering renderWorld().
     */
    public void start(AnimationEditorScreen editor)
    {
        if(isExporting())
        {
            cancel("CAMERA export canceled");
            return;
        }
        if(editor==null||editor.getSceneState()==null)
        {
            message="Open a CAMERA scene before exporting";
            return;
        }
        if(CameraNativeFrameProbe.isBusy())
        {
            message="A native export probe is already active";
            return;
        }
        try
        {
            int sceneTicks=editor.getSceneLength();
            CameraOutputSettings size=editor.getCameraOutputSettings();
            CameraExportPlan plan=new CameraExportPlan(
                    size.getWidth(),size.getHeight(),INITIAL_VIDEO_FPS,
                    sceneTicks);
            if(!editor.isCameraMode())
                throw new IllegalStateException("Switch to CAMERA to export");

            // Don't silently omit an AUDIO track or export stale cached VIDEO
            // images. These clips require deterministic timestamp decoding/
            // sound mixing in the next video-export stage.
            for(CameraClipDocument.Clip clip:
                    editor.getSceneState().getCameraClips().clips())
            {
                if(!clip.enabled||clip.tick>=sceneTicks)continue;
                if(clip.kind==CameraClipDocument.Kind.AUDIO||
                        clip.kind==CameraClipDocument.Kind.VIDEO)
                    throw new IllegalStateException(
                            clip.kind.label+" clips need exact media export; "+
                            "disable them for the first silent MP4 test");
            }

            Minecraft mc=Minecraft.getMinecraft();
            if(mc==null||mc.world==null)
                throw new IllegalStateException(
                        "Minecraft world must be loaded for CAMERA export");

            File folder=new File(mc.mcDataDir,"config/bbs-editor/exports");
            if(!folder.isDirectory()&&!folder.mkdirs())
                throw new IOException("Cannot create "+folder);
            String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",
                    Locale.ROOT).format(new Date());
            File file=new File(folder,"camera-"+size.getWidth()+"x"+
                    size.getHeight()+"-"+stamp+".mp4");
            if(file.exists())
                throw new IOException("Output already exists: "+file);

            String executable=System.getProperty(
                    "bbs.editor.export.ffmpeg","ffmpeg").trim();
            if(executable.isEmpty())executable="ffmpeg";
            // Companion ffprobe resolution and availability are checked in
            // openFfmpeg BEFORE the encoder or frame buffers are allocated.
            CameraExportSession started=CameraExportSession.openFfmpeg(
                    plan,new File(executable),file,18,"medium");
            CameraExportFrameBridge frameBridge;
            try
            {
                frameBridge=new CameraExportFrameBridge(started);
            }
            catch(RuntimeException allocationFailure)
            {
                started.close();
                throw allocationFailure;
            }

            previousFrame=editor.getCurrentFrame();
            previousPlaying=editor.isCameraPlaying();
            owner=editor;
            output=file;
            bridge=frameBridge;
            session=started;
            editor.pauseForCameraVideoExport();
            message="Export started: "+file.getName();
            System.out.println("[BBS Editor][Video Export] "+message);
        }
        catch(Exception error)
        {
            message="Export cannot start: "+error.getMessage();
            System.err.println("[BBS Editor][Video Export] "+message);
        }
    }

    /**
     * Called BEFORE the view entity chooses the camera pose. Advancing the
     * editor's tick here makes native actors and camera clips use the same
     * scene frame. No frames are requested while the encoder is saturated.
     */
    public void beforeWorld(AnimationEditorScreen editor)
    {
        if(session==null)return;
        if(editor!=owner||editor==null||!editor.isCameraMode())
        {
            cancel("Export stopped: editor changed");
            return;
        }

        CameraExportSession.Status state=session.getStatus();
        if(state==CameraExportSession.Status.COMPLETED)
        {
            finish(true,"MP4 exported: "+output.getAbsolutePath());
            return;
        }
        if(state==CameraExportSession.Status.FAILED||
                state==CameraExportSession.Status.CANCELED)
        {
            IOException failure=session.getFailure();
            String detail=failure==null?CameraNativeFrameProbe.status():
                    failure.getMessage();
            finish(false,"MP4 export failed: "+detail);
            return;
        }

        if(CameraNativeFrameProbe.isBusy())return;
        CameraExportPlan.Sample next=bridge.nextFrame();
        if(next==null)return; // FFmpeg backpressure / finalizing.
        try
        {
            editor.setCameraWorkspaceExportFrame(next.sceneTick);
            if(!CameraNativeFrameProbe.requestVideoFrame(bridge,next))
                throw new IllegalStateException(CameraNativeFrameProbe.status());
        }
        catch(RuntimeException error)
        {
            fail("Cannot prepare scene frame: "+error.getMessage());
        }
    }

    /** Called on client render tick END to notice aborted FBO passes. */
    public void afterWorld()
    {
        if(session==null)return;
        if(session.getStatus()==CameraExportSession.Status.FAILED||
                session.getStatus()==CameraExportSession.Status.CANCELED)
            beforeWorld(owner);
    }

    public void fail(String reason)
    {
        if(session==null)return;
        message="MP4 export failed: "+reason;
        CameraNativeFrameProbe.cancelVideoFrame();
        finish(false,message);
    }

    public void cancel(String reason)
    {
        if(session==null)return;
        CameraNativeFrameProbe.cancelVideoFrame();
        finish(false,reason);
    }

    /** Always called on GuiScreen close, world change or user cancellation. */
    public void close()
    {
        if(session!=null)cancel("MP4 export stopped: editor closed");
    }

    private void finish(boolean success,String result)
    {
        CameraExportSession completed=session;
        AnimationEditorScreen editor=owner;
        session=null;
        bridge=null;
        owner=null;
        if(completed!=null&&!success)completed.cancel();
        if(editor!=null)
        {
            try
            {
                editor.restoreAfterCameraVideoExport(previousFrame,
                        previousPlaying);
            }
            catch(RuntimeException restore)
            {
                result+=" (timeline restore: "+restore.getMessage()+")";
            }
        }
        message=result;
        System.out.println("[BBS Editor][Video Export] "+message);
    }
}

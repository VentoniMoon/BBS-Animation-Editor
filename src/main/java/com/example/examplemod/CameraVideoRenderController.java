package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

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

    public static final class Options
    {
        public final int width,height,crf;
        public final String preset;
        public final File ffmpeg,output;

        public Options(int width,int height,int crf,String preset,
                File ffmpeg,File output)
        {
            this.width=width;this.height=height;
            this.crf=crf;this.preset=preset;
            this.ffmpeg=ffmpeg;this.output=output;
        }
    }

    private CameraExportSession session;
    private CameraExportFrameBridge bridge;
    private CameraExportVideoFrames media;
    private AnimationEditorScreen owner;
    private int previousFrame;
    private boolean previousPlaying;
    private File output;
    private String message="";
    private String stage="Ready";
    private boolean hasSoundtrack;

    public boolean isExporting()
    {
        return session!=null;
    }

    public String status()
    {
        if(session==null)return message;
        return "CAMERA MP4 20 FPS  "+session.getEncodedFrames()+"/"+
                session.getPlan().frameCount+" frames (captured "+
                session.getCapturedFrames()+") — Cancel from Export dialog";
    }

    public String getStage()
    {
        return stage;
    }

    public int getEncodedFrames()
    {
        return session==null?0:session.getEncodedFrames();
    }

    public int getCapturedFrames()
    {
        return session==null?0:session.getCapturedFrames();
    }

    public int getTotalFrames()
    {
        return session==null?0:session.getPlan().frameCount;
    }

    /**
     * Export VIDEO overlays are evaluated at the exact film timestamp.
     * The preview cache and all GL operations remain outside the FFmpeg worker.
     */
    public CameraExportVideoFrames videoFrames()
    {
        return media;
    }

    /**
     * Starts an actual MP4 render, not a PNG capture. Called by Ctrl+Shift+F8
     * while the CAMERA editor is open. The first frame is requested on the
     * NEXT native Minecraft render tick, never by reentering renderWorld().
     */
    /** Legacy shortcut starts with sensible defaults; modal UI supplies Options. */
    public void start(AnimationEditorScreen editor)
    {
        if(editor==null||editor.getSceneState()==null)return;
        CameraOutputSettings size=editor.getCameraOutputSettings();
        Minecraft mc=Minecraft.getMinecraft();
        String name="camera-"+new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",
                Locale.ROOT).format(new Date())+".mp4";
        File folder=new File(mc.mcDataDir,"config/bbs-editor/exports");
        start(editor,new Options(size.getWidth(),size.getHeight(),18,"medium",
                new File(System.getProperty("bbs.editor.export.ffmpeg",
                        "ffmpeg")),new File(folder,name)));
    }

    /** Start a user-configured CAMERA MP4 session from the Export dialog. */
    public boolean start(AnimationEditorScreen editor,Options options)
    {
        if(isExporting())
        {
            message="Another CAMERA export is already active";
            return false;
        }
        if(editor==null||editor.getSceneState()==null||options==null)
        {
            message="Open a CAMERA scene before exporting";
            return false;
        }
        if(CameraNativeFrameProbe.isBusy())
        {
            message="A native export frame is already active";
            return false;
        }
        CameraExportSession started=null;
        CameraExportVideoFrames exactMedia=null;
        try
        {
            int sceneTicks=editor.getSceneLength();
            CameraExportPlan plan=new CameraExportPlan(options.width,
                    options.height,INITIAL_VIDEO_FPS,sceneTicks);
            if(!editor.isCameraMode())
                throw new IllegalStateException("Switch to CAMERA to export");
            if(options.output==null||options.ffmpeg==null)
                throw new IllegalArgumentException("Select output and FFmpeg");

            CameraExportVideoFrames.validateSources(
                    editor.getSceneState().getCameraClips().clips(),
                    sceneTicks);
            // Resolve and validate AUDIO clips and embedded VIDEO sound
            // once, before the expensive native world rendering begins.
            List<CameraExportAudioMuxer.Track> sound=
                    CameraExportAudioTracks.collect(
                    editor.getSceneState().getCameraClips().clips(),
                    sceneTicks,CameraExportVerifier.companionProbe(options.ffmpeg));
            Minecraft mc=Minecraft.getMinecraft();
            if(mc==null||mc.world==null)
                throw new IllegalStateException(
                        "Minecraft world must be loaded for CAMERA export");

            File file=options.output.getAbsoluteFile();
            File folder=file.getParentFile();
            if(folder!=null&&!folder.isDirectory()&&!folder.mkdirs())
                throw new IOException("Cannot create output folder "+folder);
            if(file.exists())
                throw new IOException("Output already exists: "+file);
            // The MP4 pipeline checks availability of both ffmpeg/ffprobe.
            started=CameraExportSession.openFfmpeg(
                    plan,options.ffmpeg,file,options.crf,options.preset,sound);
            exactMedia=new CameraExportVideoFrames(options.ffmpeg);
            CameraExportFrameBridge frameBridge=new CameraExportFrameBridge(
                    started);

            previousFrame=editor.getCurrentFrame();
            previousPlaying=editor.isCameraPlaying();
            owner=editor;
            output=file;
            bridge=frameBridge;
            session=started;
            media=exactMedia;
            hasSoundtrack=!sound.isEmpty();
            editor.pauseForCameraVideoExport();
            stage="Preparing first native film frame...";
            message="Export started: "+file.getName();
            System.out.println("[BBS Editor][Video Export] "+message);
            return true;
        }
        catch(Exception error)
        {
            if(exactMedia!=null)exactMedia.close();
            if(started!=null)started.cancel();
            message="Export cannot start: "+error.getMessage();
            System.err.println("[BBS Editor][Video Export] "+message);
            return false;
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
        if(next==null)
        {
            stage=session.getStatus()==CameraExportSession.Status.FINALIZING
                    ?(hasSoundtrack?"Encoding AAC soundtrack and validating MP4...":
                      "Finalizing and validating MP4...")
                    :"Waiting for FFmpeg encoder queue...";
            return;
        }
        try
        {
            // Never capture stale preview VIDEO textures. If a requested
            // FFmpeg VIDEO frame is not decoded yet, leave the timeline
            // paused; this *same* native output timestamp will be retried.
            if(media!=null&&!media.prepare(
                    editor.getSceneState().getCameraClips().clips(),
                    next.sceneTick))
            {
                stage="Decoding VIDEO clips at timeline tick "+
                        next.sceneTick+"...";
                return;
            }
            stage="Rendering native film frame "+
                    (next.outputFrame+1)+"/"+bridge.totalFrames();
            editor.setCameraWorkspaceExportFrame(next.sceneTick);
            if(!CameraNativeFrameProbe.requestVideoFrame(bridge,next))
                throw new IllegalStateException(CameraNativeFrameProbe.status());
        }
        catch(Exception error)
        {
            // Exact VIDEO decode readiness reports IOException; do not let
            // it escape the Minecraft render callback and crash the client.
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
        CameraExportVideoFrames frames=media;
        AnimationEditorScreen editor=owner;
        session=null;
        bridge=null;
        media=null;
        owner=null;
        hasSoundtrack=false;
        if(completed!=null&&!success)completed.cancel();
        if(frames!=null)frames.close();
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
        stage=success?"Export completed":"Export stopped";
        System.out.println("[BBS Editor][Video Export] "+message);
    }
}

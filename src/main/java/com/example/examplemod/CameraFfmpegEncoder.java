package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline CAMERA raw-RGBA -> H.264 MP4 encoder.
 *
 * This class performs NO OpenGL calls and NEVER reads editor GUI pixels.
 * A future render session must supply exactly one completed, bottom-up RGBA
 * frame per output index, captured from a clean export-size framebuffer on
 * the Minecraft render thread. Do not supply the editor's scaled preview.
 *
 * Contract:
 *  - one writer thread calls writeFrame() and then finish()
 *  - cancel() may be called from a different UI thread
 *  - no shell interpreter, binary embedding or auto-download
 *  - no existing output file is overwritten without a future explicit UI
 *  - no wall-clock timestamps; plan.fps defines all encoded frame times
 */
public final class CameraFfmpegEncoder implements AutoCloseable
{
    private static final int CHUNK_BYTES=65536;
    private static final long FINISH_TIMEOUT_SECONDS=120L;

    private final CameraExportPlan plan;
    private final File output;
    private final File logFile;
    private final Process process;
    private final OutputStream stdin;
    private final AtomicBoolean stopped=new AtomicBoolean(false);
    private volatile boolean finished;
    private volatile int framesWritten;

    private CameraFfmpegEncoder(CameraExportPlan plan,File output,
            File logFile,Process process)
    {
        this.plan=plan;
        this.output=output;
        this.logFile=logFile;
        this.process=process;
        this.stdin=process.getOutputStream();
    }

    /**
     * A caller can pass the absolute path to ffmpeg.exe (Windows), or use
     * a bare "ffmpeg" name to resolve it from PATH. FFmpeg 7.1 essentials
     * contains the libx264 encoder needed by this initial MP4 backend.
     */
    public static CameraFfmpegEncoder start(File ffmpeg,File output,
            CameraExportPlan plan,int crf,String preset) throws IOException
    {
        if(plan==null||output==null)
            throw new IllegalArgumentException("Missing CAMERA export plan");
        if(output.exists())
            throw new IOException("Output already exists: "+output);
        File parent=output.getAbsoluteFile().getParentFile();
        if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())
            throw new IOException("Cannot create output folder: "+parent);
        File log=File.createTempFile("bbs-camera-ffmpeg-",".log");
        Process process=null;
        try
        {
            List<String> args=command(ffmpeg,output,plan,crf,preset);
            ProcessBuilder builder=new ProcessBuilder(args);
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);
            process=builder.start();
            return new CameraFfmpegEncoder(plan,output,log,process);
        }
        catch(IOException|RuntimeException problem)
        {
            if(process!=null)process.destroyForcibly();
            if(log.isFile())log.delete();
            throw problem;
        }
    }

    public static List<String> command(File ffmpeg,File output,
            CameraExportPlan plan,int crf,String preset)
    {
        if(plan==null||output==null)
            throw new IllegalArgumentException("Missing CAMERA export plan");
        if(crf<0||crf>51)
            throw new IllegalArgumentException("H.264 CRF must be 0..51");
        if(preset==null||
                !("ultrafast".equals(preset)||"superfast".equals(preset)||
                  "veryfast".equals(preset)||"faster".equals(preset)||
                  "fast".equals(preset)||"medium".equals(preset)||
                  "slow".equals(preset)||"slower".equals(preset)||
                  "veryslow".equals(preset)))
            throw new IllegalArgumentException("Invalid x264 preset");
        if(!output.getName().toLowerCase(Locale.ROOT).endsWith(".mp4"))
            throw new IllegalArgumentException("Expected .mp4 output path");

        ArrayList<String> cmd=new ArrayList<String>();
        cmd.add(ffmpeg==null?"ffmpeg":ffmpeg.getPath());
        Collections.addAll(cmd,"-hide_banner","-loglevel","error",
                "-nostdin","-n",
                "-f","rawvideo","-pixel_format","rgba",
                "-video_size",plan.width+"x"+plan.height,
                "-framerate",Integer.toString(plan.fps),
                "-i","pipe:0",
                // glReadPixels RGBA rows originate at bottom-left.
                "-vf","vflip",
                "-c:v","libx264","-preset",preset,
                "-crf",Integer.toString(crf),
                "-pix_fmt","yuv420p",
                "-movflags","+faststart",
                "-an",output.getAbsolutePath());
        return Collections.unmodifiableList(cmd);
    }

    /**
     * Writes one raw RGBA frame. The supplied buffer is NOT modified, and
     * its remaining byte count must exactly match the selected resolution.
     * Reading from a GL framebuffer must happen beforehand on the GL thread.
     */
    public void writeFrame(ByteBuffer rgba) throws IOException
    {
        if(rgba==null||rgba.remaining()!=plan.frameBytes)
            throw new IllegalArgumentException(
                    "Frame size does not match "+plan.width+"x"+plan.height);
        if(stopped.get()||finished)
            throw new IOException("CAMERA encoder is not accepting frames");
        if(framesWritten>=plan.frameCount)
            throw new IOException("Too many export frames");

        ByteBuffer source=rgba.asReadOnlyBuffer();
        byte[] chunk=new byte[CHUNK_BYTES];
        try
        {
            while(source.hasRemaining())
            {
                if(stopped.get())throw new IOException("CAMERA export canceled");
                int count=Math.min(source.remaining(),chunk.length);
                source.get(chunk,0,count);
                stdin.write(chunk,0,count);
            }
            framesWritten++;
        }
        catch(IOException failure)
        {
            if(!stopped.get())
                throw new IOException("FFmpeg frame "+framesWritten+
                        " failed: "+failure.getMessage()+" "+
                        readLog(logFile),failure);
            throw failure;
        }
    }

    /**
     * Finish must be called from the export worker, never from GuiScreen or
     * the GL draw callback. It waits for FFmpeg's delayed H.264 frames.
     */
    public void finish() throws IOException
    {
        if(finished)return;
        if(stopped.get())throw new IOException("CAMERA export canceled");
        if(framesWritten!=plan.frameCount)
            throw new IOException("Expected "+plan.frameCount+
                    " frames, received "+framesWritten);
        if(!stopped.compareAndSet(false,true))
            throw new IOException("CAMERA export interrupted");
        try
        {
            stdin.close();
            if(!process.waitFor(FINISH_TIMEOUT_SECONDS,TimeUnit.SECONDS))
            {
                process.destroyForcibly();
                throw new IOException("FFmpeg timed out while finalizing MP4");
            }
            int code=process.exitValue();
            if(code!=0||!output.isFile()||output.length()==0L)
                throw new IOException("FFmpeg exited with code "+code+
                        ": "+readLog(logFile));
            finished=true;
            logFile.delete();
        }
        catch(InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("CAMERA export interrupted",interrupted);
        }
        finally
        {
            if(!finished)process.destroyForcibly();
        }
    }

    /** Cancel immediately and delete a partial output; don't touch game GL. */
    public void cancel()
    {
        if(!stopped.compareAndSet(false,true))return;
        try{stdin.close();}catch(IOException ignored){}
        process.destroyForcibly();
        if(!finished&&output.isFile())output.delete();
        if(logFile.isFile())logFile.delete();
    }

    public int getFramesWritten(){return framesWritten;}
    public boolean isFinished(){return finished;}
    public File getOutput(){return output;}

    /** Closing an unfinished encoder is equivalent to canceling. */
    @Override public void close()
    {
        if(!finished)cancel();
    }

    private static String readLog(File file)
    {
        if(file==null||!file.isFile())return "";
        try(RandomAccessFile log=new RandomAccessFile(file,"r"))
        {
            long start=Math.max(0L,log.length()-4096L);
            log.seek(start);
            byte[] bytes=new byte[(int)(log.length()-start)];
            log.readFully(bytes);
            return new String(bytes,StandardCharsets.UTF_8).trim();
        }
        catch(IOException ignored){return "FFmpeg log unavailable";}
    }
}

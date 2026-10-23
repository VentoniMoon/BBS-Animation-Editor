package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

/**
 * Frame-exact offline VIDEO clip decoder.
 *
 * A VIDEO frame belongs to the FILM clock: 20 scene ticks per second. The
 * requested timestamp is relative to that clip plus its optional offset.
 * Every image is decoded from the exact requested time; unlike preview
 * CameraVideoFrames, there is deliberately NO stale/previous-frame fallback.
 *
 * ProcessBuilder uses an argv array rather than a shell (paths with spaces
 * work on Windows); FFmpeg stdout is a PNG pipe, NOT an on-disk PNG sequence.
 * Call decode() only on a worker thread, never during Minecraft GL render.
 * The owner must call cancel() if a decode takes longer than the allowed
 * watchdog interval, because ImageIO/pipe reads may block until FFmpeg ends.
 */
public final class CameraExportVideoDecoder
{
    public static final int MAX_PNG_BYTES=32*1024*1024;
    public static final int MAX_IMAGE_SIDE=4096;
    private final File ffmpeg;
    private final AtomicReference<Process> process=new AtomicReference<Process>();

    public CameraExportVideoDecoder(File ffmpeg)
    {
        this.ffmpeg=ffmpeg;
    }

    public static List<String> command(File ffmpeg,File source,int localTick,
            boolean loop)
    {
        if(source==null||!source.isFile())
            throw new IllegalArgumentException("Missing VIDEO source: "+source);
        if(localTick<0)
            throw new IllegalArgumentException("Negative VIDEO local tick");
        String executable=ffmpeg==null?"ffmpeg":ffmpeg.getPath();
        List<String> cmd=new ArrayList<String>();
        cmd.add(executable);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");cmd.add("error");
        cmd.add("-nostdin");
        if(loop)
        {
            cmd.add("-stream_loop");
            cmd.add("-1");
        }
        // Avoid decoding the entire movie from frame zero on *every*
        // output frame. The input-side seek locates an earlier keyframe,
        // while the remaining output-side seek still decodes/discards the
        // intervening B/P frames for exact final timestamp ownership.
        // Keep two seconds of pre-roll, rather than seeking directly to t
        // (which could select a preceding keyframe as the output frame).
        int coarseSecond=Math.max(0,localTick/20-2);
        if(coarseSecond>0)
        {
            cmd.add("-ss");
            cmd.add(String.format(Locale.ROOT,"%.6f",(double)coarseSecond));
        }
        cmd.add("-i");cmd.add(source.getAbsolutePath());
        cmd.add("-ss");
        cmd.add(String.format(Locale.ROOT,"%.6f",
                localTick/20D-coarseSecond));
        cmd.add("-map");cmd.add("0:v:0");
        cmd.add("-frames:v");cmd.add("1");
        cmd.add("-an");cmd.add("-sn");cmd.add("-dn");
        cmd.add("-f");cmd.add("image2pipe");
        cmd.add("-vcodec");cmd.add("png");
        cmd.add("pipe:1");
        return cmd;
    }

    public BufferedImage decode(File source,int localTick,boolean loop)
            throws IOException
    {
        List<String> argv;
        try{argv=command(ffmpeg,source,localTick,loop);}
        catch(IllegalArgumentException invalid)
        {throw new IOException(invalid.getMessage(),invalid);}

        Process current=null;
        File diagnostic=File.createTempFile("bbs-camera-video-decode-",".log");
        try
        {
            ProcessBuilder builder=new ProcessBuilder(argv);
            builder.redirectError(diagnostic);
            current=builder.start();
            if(!process.compareAndSet(null,current))
            {
                current.destroyForcibly();
                throw new IOException("An exact VIDEO decode is already active");
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(256*1024);
            byte[] chunk=new byte[8192];
            try(InputStream input=current.getInputStream())
            {
                for(int n;(n=input.read(chunk))!=-1;)
                {
                    if(Thread.currentThread().isInterrupted())
                        throw new IOException("VIDEO decoding interrupted");
                    if((long)bytes.size()+n>MAX_PNG_BYTES)
                        throw new IOException("VIDEO frame exceeds 32 MiB PNG safety limit");
                    bytes.write(chunk,0,n);
                }
            }
            int code=current.waitFor();
            if(code!=0)
                throw new IOException("FFmpeg VIDEO decode exited "+code+
                        " at tick "+localTick+": "+lastLog(diagnostic));
            if(bytes.size()==0)
                throw new IOException("VIDEO has no frame at tick "+localTick+
                        (loop?" (loop enabled)":
                                "; source ends before the clip; enable Loop"));
            BufferedImage image=ImageIO.read(
                    new ByteArrayInputStream(bytes.toByteArray()));
            if(image==null||image.getWidth()<1||image.getHeight()<1||
                    image.getWidth()>MAX_IMAGE_SIDE||
                    image.getHeight()>MAX_IMAGE_SIDE)
                throw new IOException("VIDEO frame has unsupported dimensions");
            return image;
        }
        catch(InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IOException("VIDEO decoding interrupted",interrupted);
        }
        finally
        {
            if(current!=null)
            {
                process.compareAndSet(current,null);
                if(current.isAlive())current.destroyForcibly();
            }
            diagnostic.delete();
        }
    }

    /** Called by the watchdog or cancellation from the Minecraft GL thread. */
    public void cancel()
    {
        Process current=process.getAndSet(null);
        if(current!=null)current.destroyForcibly();
    }

    private static String lastLog(File file)
    {
        try
        {
            byte[] data=Files.readAllBytes(file.toPath());
            int at=Math.max(0,data.length-1024);
            return new String(data,at,data.length-at,
                    StandardCharsets.UTF_8).trim();
        }
        catch(IOException ignored){return "No FFmpeg error log";}
    }
}

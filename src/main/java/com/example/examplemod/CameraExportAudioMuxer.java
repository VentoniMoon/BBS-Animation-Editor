package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Offline sound pass for CAMERA. Finished native 20 FPS H.264 pixels are
 * stream-copied, never re-encoded; audio is mixed at 48 kHz stereo/AAC.
 * FFmpeg receives argv directly without a shell or intermediate WAV.
 *
 * This lives on the export session's worker, never on the Minecraft GL thread.
 * A failed/canceled mix does not leave a success-named partial MP4 behind.
 */
public final class CameraExportAudioMuxer
{
    private static final int MAX_TRACKS=16;
    private static final long MAX_RUNTIME_SECONDS=1800L;

    /** All time values in exact 20 TPS timeline ticks. */
    public static final class Track
    {
        public final File source;
        public final boolean loop;
        public final int sceneStartTick,sourceStartTick,durationTicks;
        public final double volume;
        public final int fadeInTicks,fadeOutTicks;

        public Track(File source,boolean loop,int sceneStartTick,
                int sourceStartTick,int durationTicks,double volume,
                int fadeInTicks,int fadeOutTicks)
        {
            if(source==null||sceneStartTick<0||sourceStartTick<0||
                    durationTicks<1||!Double.isFinite(volume)||
                    volume<0D||volume>2D||
                    fadeInTicks<0||fadeOutTicks<0)
                throw new IllegalArgumentException("Invalid CAMERA audio track");
            this.source=source.getAbsoluteFile();
            this.loop=loop;
            this.sceneStartTick=sceneStartTick;
            this.sourceStartTick=sourceStartTick;
            this.durationTicks=durationTicks;
            this.volume=volume;
            this.fadeInTicks=fadeInTicks;
            this.fadeOutTicks=fadeOutTicks;
        }
    }

    private volatile Process running;
    private volatile boolean canceled;

    public static List<String> command(File ffmpeg,File video,File output,
            int sceneTicks,List<Track> tracks)
    {
        if(video==null||output==null||sceneTicks<1||tracks==null||
                tracks.isEmpty()||tracks.size()>MAX_TRACKS)
            throw new IllegalArgumentException("Invalid CAMERA audio mix");
        List<String> argv=new ArrayList<String>();
        argv.add(ffmpeg==null?"ffmpeg":ffmpeg.getPath());
        Collections.addAll(argv,"-hide_banner","-loglevel","error",
                "-nostdin","-n","-i",video.getAbsolutePath());
        for(Track track:tracks)
        {
            if(track.loop)Collections.addAll(argv,"-stream_loop","-1");
            Collections.addAll(argv,"-i",track.source.getAbsolutePath());
        }

        StringBuilder filter=new StringBuilder();
        for(int i=0;i<tracks.size();i++)
        {
            Track track=tracks.get(i);
            if(i>0)filter.append(";");
            // Trim first, so PTS and fades are relative to the local source
            // segment. All delays are measured from global film tick zero.
            filter.append("[").append(i+1).append(":a:0]");
            filter.append("aresample=48000,aformat=sample_fmts=fltp:");
            filter.append("channel_layouts=stereo,atrim=start=");
            filter.append(seconds(track.sourceStartTick));
            filter.append(":duration=").append(seconds(track.durationTicks));
            filter.append(",asetpts=PTS-STARTPTS");
            if(track.volume!=1D)
                filter.append(",volume=").append(number(track.volume));
            int fadeIn=Math.min(track.fadeInTicks,track.durationTicks);
            int fadeOut=Math.min(track.fadeOutTicks,track.durationTicks);
            if(fadeIn>0)
                filter.append(",afade=t=in:st=0:d=").append(seconds(fadeIn));
            if(fadeOut>0)
                filter.append(",afade=t=out:st=")
                        .append(seconds(track.durationTicks-fadeOut))
                        .append(":d=").append(seconds(fadeOut));
            filter.append(",adelay=delays=")
                    .append((long)track.sceneStartTick*50L)
                    .append(":all=1[a").append(i).append("]");
        }
        filter.append(";");
        for(int i=0;i<tracks.size();i++)
            filter.append("[a").append(i).append("]");
        filter.append("amix=inputs=").append(tracks.size());
        filter.append(":duration=longest:dropout_transition=0:normalize=0,");
        filter.append("apad,atrim=duration=")
                .append(seconds(sceneTicks)).append("[mixed]");
        Collections.addAll(argv,"-filter_complex",filter.toString(),
                "-map","0:v:0","-map","[mixed]","-c:v","copy",
                "-c:a","aac","-b:a","192k","-ar","48000","-ac","2",
                "-movflags","+faststart","-t",seconds(sceneTicks),
                output.getAbsolutePath());
        return argv;
    }

    private static String seconds(int ticks)
    {
        return String.format(Locale.ROOT,"%.6f",ticks/20D);
    }
    private static String number(double value)
    {
        return String.format(Locale.ROOT,"%.6f",value);
    }

    /**
     * Check a VIDEO's embedded sound before rendering. A VIDEO without an
     * audio stream is valid and simply contributes visuals. An explicit AUDIO
     * path, on the other hand, is always required to contain sound.
     */
    public static boolean hasAudioStream(File ffprobe,File file)
            throws IOException
    {
        if(file==null||!file.isFile())
            throw new IOException("Missing audio source: "+file);
        File log=File.createTempFile("bbs-camera-audio-probe-",".txt");
        Process process=null;
        try
        {
            process=new ProcessBuilder(
                    ffprobe==null?"ffprobe":ffprobe.getPath(),
                    "-v","error","-select_streams","a:0",
                    "-show_entries","stream=index","-of","csv=p=0",
                    file.getAbsolutePath())
                    .redirectErrorStream(true).redirectOutput(log).start();
            if(!process.waitFor(10,TimeUnit.SECONDS))
                throw new IOException("Audio stream probe timed out: "+
                        file.getName());
            if(log.length()>4096L||process.exitValue()!=0)
                throw new IOException("Cannot probe audio stream: "+
                        file.getName());
            return !new String(Files.readAllBytes(log.toPath()),
                    StandardCharsets.UTF_8).trim().isEmpty();
        }
        catch(InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Audio probe interrupted",interrupted);
        }
        finally
        {
            if(process!=null&&process.isAlive())process.destroyForcibly();
            log.delete();
        }
    }

    /**
     * Perform the mux once the silent H.264 video has been finished and
     * FFprobe-validated. Caller validates the final MP4 before deleting video.
     */
    public void mux(File ffmpeg,File video,File destination,int sceneTicks,
            List<Track> tracks) throws IOException
    {
        if(canceled)throw new IOException("Audio export canceled");
        if(destination.exists())
            throw new IOException("Audio destination already exists");
        List<String> argv=command(ffmpeg,video,destination,sceneTicks,tracks);
        File log=File.createTempFile("bbs-camera-audio-mux-",".log");
        Process process=null;
        try
        {
            process=new ProcessBuilder(argv).redirectErrorStream(true)
                    .redirectOutput(log).start();
            running=process;
            long timeout=Math.min(MAX_RUNTIME_SECONDS,Math.max(90L,
                    (long)Math.ceil(sceneTicks/20D)*4L));
            if(!process.waitFor(timeout,TimeUnit.SECONDS))
                throw new IOException("Audio mux timed out");
            if(canceled)throw new IOException("Audio mux canceled");
            if(process.exitValue()!=0)
            {
                String detail=log.length()>16384L?"FFmpeg error log too long":
                        new String(Files.readAllBytes(log.toPath()),
                                StandardCharsets.UTF_8).trim();
                throw new IOException("FFmpeg audio mix failed: "+detail);
            }
            if(!destination.isFile()||destination.length()==0L)
                throw new IOException("FFmpeg produced an empty audio MP4");
        }
        catch(InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Audio mux interrupted",interrupted);
        }
        finally
        {
            if(process!=null&&process.isAlive())process.destroyForcibly();
            running=null;
            log.delete();
            if(canceled||!destination.isFile()||destination.length()==0L)
                destination.delete();
        }
    }

    public void cancel()
    {
        canceled=true;
        Process process=running;
        if(process!=null)process.destroyForcibly();
    }
}

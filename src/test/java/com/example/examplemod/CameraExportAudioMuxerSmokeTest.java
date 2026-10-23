package com.example.examplemod;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/** FFmpeg-backed Java 8 tests for real 48 kHz AAC output and path persistence. */
public final class CameraExportAudioMuxerSmokeTest
{
    private static void check(boolean good,String text)
    {
        if(!good)throw new AssertionError(text);
    }
    private static void run(List<String> cmd) throws Exception
    {
        Process process=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if(!process.waitFor(30,TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            throw new AssertionError("FFmpeg test timed out: "+cmd);
        }
        check(process.exitValue()==0,"FFmpeg test command failed: "+cmd);
    }
    public static void main(String[] args) throws Exception
    {
        File folder=Files.createTempDirectory("bbs-camera-audio-").toFile();
        String win="C:\\MinecraftModding\\BBS-Animation-Editor\\run\\ffmpeg-7.1-essentials_build\\bin\\ffmpeg.exe";
        CameraExportPreferences.saveFfmpeg(folder,win);
        check(win.equals(CameraExportPreferences.loadFfmpeg(folder)),
                "Windows executable path must survive open/close");
        CameraExportPreferences.saveFfmpeg(folder,"ffmpeg");
        check("ffmpeg".equals(CameraExportPreferences.loadFfmpeg(folder)),
                "Machine preferences must update without touching a scene");
        File sound=new File(folder,"audio.wav");
        File silent=new File(folder,"source.mp4");
        File mixed=new File(folder,"final.mp4");
        run(Arrays.asList("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","sine=frequency=880:sample_rate=48000:duration=1",
                "-ac","2",sound.getAbsolutePath()));
        run(Arrays.asList("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","color=c=black:s=128x128:r=20:d=2",
                "-an","-c:v","libx264","-pix_fmt","yuv420p",
                silent.getAbsolutePath()));
        check(CameraExportAudioMuxer.hasAudioStream(null,sound),
                "WAV must contain sound");
        check(!CameraExportAudioMuxer.hasAudioStream(null,silent),
                "Silent video must be valid without audio");
        CameraExportAudioMuxer.Track track=new CameraExportAudioMuxer.Track(
                sound,false,10,0,20,0.8D,4,4);
        List<String> cmd=CameraExportAudioMuxer.command(
                null,silent,mixed,40,Collections.singletonList(track));
        check(cmd.contains("-c:v")&&cmd.contains("copy"),
                "Video must be stream-copied");
        check(cmd.toString().contains("adelay=delays=500:all=1"),
                "Sound must start at scene tick 10 = 500 ms");
        new CameraExportAudioMuxer().mux(null,silent,mixed,40,
                Collections.singletonList(track));
        check(CameraExportAudioMuxer.hasAudioStream(null,mixed),
                "Muxed MP4 must have AAC soundtrack");
        CameraExportVerifier.verify(null,mixed,
                new CameraExportPlan(128,128,20,40));
        File sample=new File(folder,"sample.pcm");
        run(Arrays.asList("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-i",mixed.getAbsolutePath(),"-vn","-ac","1",
                "-ar","48000","-f","s16le",sample.getAbsolutePath()));
        byte[] pcm=Files.readAllBytes(sample.toPath());
        check(pcm.length>=48000*2*2,"Audio length must match film");
        long early=energy(pcm,12000,17000),middle=energy(pcm,33000,38000);
        check(early<200L*5000L,"Audio must be quiet before clip starts: "+early);
        check(middle>1000L*5000L,"Audio must be present during clip: "+middle);
        System.out.println("CAMERA audio mux and persistent FFmpeg path PASS");
    }
    private static long energy(byte[] pcm,int from,int to)
    {
        long sum=0;
        for(int i=from;i<to;i++)
        {
            int p=i*2;
            int value=(short)(((pcm[p+1]&255)<<8)|(pcm[p]&255));
            sum+=Math.abs(value);
        }
        return sum;
    }
}

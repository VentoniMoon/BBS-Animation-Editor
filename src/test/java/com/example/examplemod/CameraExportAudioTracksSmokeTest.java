package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/** Test exact 20 TPS clip-to-sound routing without a running Minecraft GUI. */
public final class CameraExportAudioTracksSmokeTest
{
    static void check(boolean ok,String message)
    {if(!ok)throw new AssertionError(message);}
    static void ffmpeg(String... cmd) throws Exception
    {
        Process process=new ProcessBuilder(cmd).redirectErrorStream(true).start();
        check(process.waitFor(30,TimeUnit.SECONDS)&&process.exitValue()==0,
                "Cannot synthesize test sound input");
    }
    public static void main(String[] args) throws Exception
    {
        File dir=Files.createTempDirectory("bbs-sound-tracks-").toFile();
        File wav=new File(dir,"music.wav");
        File silentVideo=new File(dir,"silent.mp4");
        File videoSound=new File(dir,"movie.mp4");
        ffmpeg("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","sine=frequency=440:duration=2",
                wav.getAbsolutePath());
        ffmpeg("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","color=c=black:s=128x128:r=20:d=2",
                "-an",silentVideo.getAbsolutePath());
        ffmpeg("ffmpeg","-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","color=c=black:s=128x128:r=20:d=2",
                "-f","lavfi","-i","sine=frequency=220:duration=2",
                "-c:v","libx264","-c:a","aac","-shortest",
                videoSound.getAbsolutePath());

        CameraClipDocument.Clip a=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.AUDIO,10,40);
        a.settings.put("audio",wav.getAbsolutePath());
        a.settings.put("offset","-4");
        a.settings.put("volume","0.5");
        a.settings.put("envelope.enabled","true");
        a.settings.put("envelope.mode","0");
        a.settings.put("envelope.fadeIn","10");
        a.settings.put("envelope.fadeOut","10");
        List<CameraExportAudioMuxer.Track> result=
                CameraExportAudioTracks.collect(Collections.singletonList(a),
                        100,null);
        check(result.size()==1,"AUDIO clip recognized");
        CameraExportAudioMuxer.Track t=result.get(0);
        check(t.sceneStartTick==14&&t.sourceStartTick==0&&
                t.durationTicks==36&&t.fadeInTicks==6&&
                t.fadeOutTicks==10&&t.volume==0.5D,
                "Delayed WAV must preserve original timeline offsets/fades");

        CameraClipDocument.Clip v=new CameraClipDocument.Clip(
                CameraClipDocument.Kind.VIDEO,5,30);
        v.settings.put("video",silentVideo.getAbsolutePath());
        check(CameraExportAudioTracks.collect(Collections.singletonList(v),
                100,null).isEmpty(),
                "Silent VIDEO must not invent audio");
        v.settings.put("video",videoSound.getAbsolutePath());
        v.settings.put("loop","true");
        v.settings.put("offset","6");
        result=CameraExportAudioTracks.collect(Arrays.asList(a,v),100,null);
        check(result.size()==2&&result.get(1).loop&&
                result.get(1).sceneStartTick==5&&
                result.get(1).sourceStartTick==6,
                "VIDEO soundtrack must inherit clip start, seek and loop");
        a.settings.put("audio",new File(dir,"missing.wav").getAbsolutePath());
        try
        {
            CameraExportAudioTracks.collect(Collections.singletonList(a),100,null);
            throw new AssertionError("Missing AUDIO source must fail preflight");
        }
        catch(IOException expected)
        {
            check(expected.getMessage().contains("Missing CAMERA soundtrack"),
                    "Missing source error should be actionable");
        }
        System.out.println("CAMERA AUDIO and VIDEO soundtrack routing PASS");
    }
}

package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dependency-free Java 8 tests for the planned offline video encoder.
 * If ffmpeg is in PATH, also encodes a tiny actual H.264 MP4 from RGBA.
 * No Minecraft, Forge, Jolt or OpenGL required.
 */
public final class CameraExportCoreSmokeTest
{
    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }
    private static void close(double actual,double expected,String message)
    {
        if(Math.abs(actual-expected)>1.0E-9D)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }

    public static void main(String[] args) throws Exception
    {
        CameraExportPlan plan=new CameraExportPlan(1920,1080,60,40);
        check(plan.frameCount==120,"two scene seconds become 120 frames");
        check(plan.frameBytes==1920*1080*4,"RGBA bytes");
        check(plan.sample(0).sceneTick==0,"first frame at first tick");
        close(plan.sample(1).tickFraction,1D/3D,"first sub-tick");
        close(plan.sample(2).tickFraction,2D/3D,"second sub-tick");
        check(plan.sample(3).sceneTick==1,"20 TPS transition");
        check(plan.sample(119).sceneTick==39,"last sampled tick");
        close(plan.sample(119).tickFraction,2D/3D,
                "last subframe before scene ends");
        close(plan.durationSeconds(),2D,"scene duration");
        CameraExportPlan cinema=new CameraExportPlan(1920,1080,24,20);
        check(cinema.frameCount==24,"24 fps second");
        check(cinema.sample(23).sceneTick==19,"24fps last tick");
        close(cinema.sample(23).tickFraction,1D/6D,
                "non-divisible fps samples");
        CameraExportPlan shortScene=new CameraExportPlan(128,128,1,1);
        check(shortScene.frameCount==1&&
                shortScene.sample(0).sceneTick==0,"short scene");

        boolean rejected=false;
        try{new CameraExportPlan(1919,1080,60,20);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject odd export width");
        rejected=false;
        try{new CameraExportPlan(3840,2160,60,2000000);}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject excessive frame count");

        File path=new File("camera-export-test.mp4");
        List<String> cmd=CameraFfmpegEncoder.command(new File("ffmpeg"),path,
                plan,18,"medium");
        check(cmd.contains("pipe:0"),"raw RGBA frames come from stdin");
        check(cmd.contains("1920x1080"),"configured resolution");
        check(cmd.contains("60"),"configured FPS");
        check(cmd.contains("vflip"),"OpenGL bottom-left buffer is flipped");
        check(cmd.contains("yuv420p"),"H.264 browser-compatible pixel format");
        check(cmd.contains("-n"),"no silent replacement of output files");
        rejected=false;
        try{CameraFfmpegEncoder.command(null,path,plan,18,"foo");}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"only whitelisted x264 presets");
        rejected=false;
        try{CameraFfmpegEncoder.command(null,path,plan,-1,"medium");}
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"reject invalid encoding quality");

        if(!hasFfmpeg())
        {
            System.out.println("CAMERA export plan/arguments PASS; "+
                    "H.264 integration SKIPPED (FFmpeg absent)");
            return;
        }

        File temp=Files.createTempDirectory("bbs-camera-encoder-test").toFile();
        File output=new File(temp,"test clip with spaces.mp4");
        CameraExportPlan tiny=new CameraExportPlan(128,128,20,4);
        try(CameraFfmpegEncoder encoder=
                CameraFfmpegEncoder.start(null,output,tiny,18,"ultrafast"))
        {
            byte[] rgba=new byte[tiny.frameBytes];
            for(int frame=0;frame<tiny.frameCount;frame++)
            {
                for(int i=0;i<rgba.length;i+=4)
                {
                    rgba[i]=(byte)(frame*50+20);
                    rgba[i+1]=(byte)(255-frame*30);
                    rgba[i+2]=(byte)(frame*55);
                    rgba[i+3]=(byte)255;
                }
                ByteBuffer source=ByteBuffer.wrap(rgba);
                encoder.writeFrame(source);
                check(source.position()==0,
                        "FFmpeg writer must not consume caller buffer");
            }
            encoder.finish();
            check(encoder.getFramesWritten()==4,"frame counter");
            check(encoder.isFinished(),"encoder finished");
        }
        check(output.isFile()&&output.length()>100L,
                "tiny H.264 MP4 exists and is not empty");

        File abort=new File(temp,"partial.mp4");
        try(CameraFfmpegEncoder encoder=
                CameraFfmpegEncoder.start(null,abort,tiny,18,"ultrafast"))
        {
            encoder.writeFrame(ByteBuffer.wrap(new byte[tiny.frameBytes]));
            encoder.cancel();
        }
        check(!abort.exists(),"cancel deletes incomplete MP4");
        check(output.delete(),"cleanup exported MP4");
        check(temp.delete(),"cleanup test directory");
        System.out.println("CAMERA export 20TPS/FPS mapping and real FFmpeg H.264 PASS");
    }

    private static boolean hasFfmpeg()
    {
        Process process=null;
        try
        {
            process=new ProcessBuilder("ffmpeg","-version")
                    .redirectErrorStream(true)
                    .redirectOutput(new File(System.getProperty("os.name","")
                            .toLowerCase().contains("win")?"NUL":"/dev/null"))
                    .start();
            return process.waitFor(5,TimeUnit.SECONDS)&&
                    process.exitValue()==0;
        }
        catch(IOException|InterruptedException notFound)
        {
            return false;
        }
        finally
        {
            if(process!=null)process.destroyForcibly();
        }
    }
}

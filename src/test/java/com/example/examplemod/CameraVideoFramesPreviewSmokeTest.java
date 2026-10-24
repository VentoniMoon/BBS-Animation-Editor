package com.example.examplemod;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Integration regression: the FFmpeg path saved by Export Video must also
 * produce visible GUI preview textures, not only successful exported MP4.
 */
public final class CameraVideoFramesPreviewSmokeTest
{
    private static void check(boolean yes,String why)
    {if(!yes)throw new AssertionError(why);}

    private static DynamicTexture poll(String name,int tick) throws Exception
    {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
        DynamicTexture value;
        do
        {
            value=CameraVideoFrames.get(name,tick);
            if(value!=null)return value;
            Thread.sleep(15L);
        }
        while(System.nanoTime()<until);
        throw new AssertionError("VIDEO preview timeout: "+
                CameraVideoFrames.failureFor(name));
    }

    public static void main(String[] args) throws Exception
    {
        File dir=Files.createTempDirectory("bbs-camera-video-preview-").toFile();
        File source=new File(dir,"movie.mp4");
        File log=new File(dir,"ffmpeg.log");
        try
        {
            // This smoke runs on Linux CI. Resolve the FFmpeg executable to
            // an absolute file: the editor may be launched without PATH.
            Process which=new ProcessBuilder("sh","-c","command -v ffmpeg").start();
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            byte[] chunk=new byte[256];int n;
            try(java.io.InputStream in=which.getInputStream())
            {while((n=in.read(chunk))!=-1)output.write(chunk,0,n);}
            check(which.waitFor()==0,"ffmpeg executable installed");
            File ffmpeg=new File(output.toString("UTF-8").trim());
            check(ffmpeg.isFile(),"Absolute FFmpeg path exists");
            CameraExportPreferences.saveFfmpeg(dir,ffmpeg.getAbsolutePath());
            new net.minecraft.client.Minecraft(dir);
            ProcessBuilder cmd=new ProcessBuilder(ffmpeg.getAbsolutePath(),
                    "-hide_banner","-loglevel","error","-f","lavfi",
                    "-i","testsrc2=size=144x96:rate=20:duration=2",
                    "-c:v","libx264","-preset","ultrafast",
                    "-pix_fmt","yuv420p","-an","-y",source.getAbsolutePath());
            cmd.redirectErrorStream(true);cmd.redirectOutput(log);
            Process process=cmd.start();
            check(process.waitFor(20,TimeUnit.SECONDS)&&process.exitValue()==0,
                    "Generate known VIDEO source");
            DynamicTexture first=poll(source.getAbsolutePath(),0);
            check(first.image.getWidth()==144&&first.image.getHeight()==96,
                    "Frame decoded and uploaded using saved FFmpeg path");
            int[] size=CameraVideoFrames.size(source.getAbsolutePath(),0);
            check(size!=null&&size[0]==144&&size[1]==96,
                    "Source VIDEO aspect preserved");
            check(CameraVideoFrames.failureFor(source.getAbsolutePath())==null,
                    "Valid preview does not report phantom errors");
            DynamicTexture second=poll(source.getAbsolutePath(),10);
            check(second.image!=null,"Later seek decodes source frame");

            CameraVideoFrames.clear();
            CameraExportPreferences.saveFfmpeg(dir,
                    new File(dir,"intentionally-missing-ffmpeg").getAbsolutePath());
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            String error=null;
            do
            {
                CameraVideoFrames.get(source.getAbsolutePath(),2);
                error=CameraVideoFrames.failureFor(source.getAbsolutePath());
                if(error!=null)break;
                Thread.sleep(20L);
            }
            while(System.nanoTime()<until);
            check(error!=null&&error.contains("FFmpeg"),
                    "Bad encoder path is reported instead of invisible VIDEO");
        }
        finally
        {
            CameraVideoFrames.clear();
            source.delete();log.delete();
            File settings=new File(dir,"config/bbs-editor/camera-export.properties");
            settings.delete();
            new File(dir,"config/bbs-editor").delete();
            new File(dir,"config").delete();
            dir.delete();
        }
        System.out.println("CAMERA VIDEO GUI preview and FFmpeg preference PASS");
    }
}

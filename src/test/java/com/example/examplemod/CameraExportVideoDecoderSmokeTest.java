package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Real FFmpeg time-seek, end-of-clip and loop regression for VIDEO export. */
public final class CameraExportVideoDecoderSmokeTest
{
    private static void check(boolean condition,String message)
    {
        if(!condition)throw new AssertionError(message);
    }

    private static void waitFor(Process process,String action) throws Exception
    {
        if(!process.waitFor(20,TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            throw new AssertionError(action+" timed out");
        }
        check(process.exitValue()==0,action+" exited "+process.exitValue());
    }

    public static void main(String[] args) throws Exception
    {
        File dir=Files.createTempDirectory("bbs-video-exact-").toFile();
        File source=new File(dir,"test source with spaces.mp4");
        try
        {
            ProcessBuilder generator=new ProcessBuilder("ffmpeg",
                    "-hide_banner","-loglevel","error","-nostdin",
                    "-f","lavfi","-i",
                    "testsrc2=size=128x128:rate=20:duration=1",
                    "-c:v","libx264","-preset","ultrafast",
                    "-pix_fmt","yuv420p","-an","-y",
                    source.getAbsolutePath());
            generator.redirectErrorStream(true);
            generator.redirectOutput(new File(dir,"generator.log"));
            waitFor(generator.start(),"Synthetic 1s VIDEO");
            check(source.isFile()&&source.length()>100,"synthetic MP4 exists");

            List<String> seeking=CameraExportVideoDecoder.command(
                    null,source,10,false);
            check(seeking.contains("0.500000"),
                    "frame 10 selects exactly half a source second");
            check(seeking.indexOf("-ss")>seeking.indexOf("-i"),
                    "VIDEO seeks after input for frame-exact decoding");
            check(!seeking.contains("-stream_loop"),
                    "nonlooping source is not silently repeated");
            List<String> subframe=CameraExportVideoDecoder.command(
                    null,source,1D/3D,false);
            check(subframe.contains("0.016667"),
                    "60 FPS VIDEO frame seeks at 1/60 second, not floor tick");
            List<String> looping=CameraExportVideoDecoder.command(
                    null,source,25,true);
            check(looping.contains("-stream_loop")&&looping.contains("-1"),
                    "looping VIDEO repeats source rather than going stale");
            CameraExportVideoDecoder decoder=new CameraExportVideoDecoder(null);
            BufferedImage beginning=decoder.decode(source,0,false);
            BufferedImage middle=decoder.decode(source,10,false);
            check(beginning.getWidth()==128&&beginning.getHeight()==128,
                    "VIDEO decoder returns source pixels");
            int changes=0;
            for(int y=8;y<120;y+=16)
                for(int x=8;x<120;x+=16)
                    if(beginning.getRGB(x,y)!=middle.getRGB(x,y))changes++;
            check(changes>8,"seeking to new VIDEO timestamp changes image");

            boolean exhausted=false;
            try{decoder.decode(source,25,false);}
            catch(IOException expected){exhausted=true;}
            check(exhausted,
                    "VIDEO source exhaustion fails explicitly, no stale frame");
            BufferedImage loop=decoder.decode(source,25,true);
            BufferedImage base=decoder.decode(source,5,false);
            check(loop.getWidth()==base.getWidth()&&
                    loop.getHeight()==base.getHeight(),
                    "looped VIDEO retains source resolution");

            decoder.cancel();
            check(source.delete(),"remove test video source");
            File log=new File(dir,"generator.log");
            check(log.delete(),"remove generator log");
        }
        finally{dir.delete();}
        System.out.println("CAMERA VIDEO exact-seek, end-of-file and loop PASS");
    }
}

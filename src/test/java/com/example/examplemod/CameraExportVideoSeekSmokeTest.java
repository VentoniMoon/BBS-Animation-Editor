package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Test bounded keyframe seek against reference decode-from-start frames. */
public final class CameraExportVideoSeekSmokeTest
{
    private static void check(boolean condition,String message)
    {if(!condition)throw new AssertionError(message);}

    private static void waitFor(Process process,String message)
            throws Exception
    {
        if(!process.waitFor(30,TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            throw new AssertionError(message+" timed out");
        }
        check(process.exitValue()==0,message+" failed");
    }

    private static BufferedImage original(File source,int tick,boolean loop)
            throws Exception
    {
        List<String> cmd=new ArrayList<String>();
        cmd.add("ffmpeg");
        cmd.add("-hide_banner");cmd.add("-loglevel");cmd.add("error");
        cmd.add("-nostdin");
        if(loop)
        {
            cmd.add("-stream_loop");cmd.add("-1");
        }
        cmd.add("-i");cmd.add(source.getAbsolutePath());
        cmd.add("-ss");
        cmd.add(String.format(Locale.ROOT,"%.6f",tick/20D));
        cmd.add("-map");cmd.add("0:v:0");
        cmd.add("-frames:v");cmd.add("1");
        cmd.add("-an");cmd.add("-sn");cmd.add("-dn");
        cmd.add("-f");cmd.add("image2pipe");cmd.add("-vcodec");
        cmd.add("png");cmd.add("pipe:1");
        Process process=new ProcessBuilder(cmd).redirectErrorStream(false)
                .redirectError(new File(System.getProperty("os.name","")
                        .toLowerCase(Locale.ROOT).contains("win")
                        ?"NUL":"/dev/null")).start();
        BufferedImage image;
        try(InputStream input=process.getInputStream())
        {
            image=ImageIO.read(input);
        }
        waitFor(process,"Reference FFmpeg VIDEO seek");
        return image;
    }

    private static void compare(BufferedImage a,BufferedImage b,String title)
    {
        check(a!=null&&b!=null&&a.getWidth()==b.getWidth()&&
                a.getHeight()==b.getHeight(),title+" dimensions");
        for(int y=0;y<a.getHeight();y++)
            for(int x=0;x<a.getWidth();x++)
                if(a.getRGB(x,y)!=b.getRGB(x,y))
                    throw new AssertionError(title+" pixels differ at "+x+","+y);
    }

    public static void main(String[] args) throws Exception
    {
        File dir=Files.createTempDirectory("bbs-camera-fastseek-").toFile();
        File source=new File(dir,"long source with spaces.mp4");
        Process generator=new ProcessBuilder("ffmpeg","-hide_banner",
                "-loglevel","error","-nostdin","-f","lavfi","-i",
                "testsrc2=size=128x128:rate=20:duration=5",
                "-c:v","libx264","-pix_fmt","yuv420p",
                "-y",source.getAbsolutePath())
                .redirectError(new File(dir,"generate.log")).start();
        waitFor(generator,"Five-second 20 FPS test clip");
        CameraExportVideoDecoder decoder=new CameraExportVideoDecoder(null);
        try
        {
            List<String> cmd=CameraExportVideoDecoder.command(
                    null,source,80,false);
            check(cmd.indexOf("-ss")<cmd.indexOf("-i")&&
                    cmd.lastIndexOf("-ss")>cmd.indexOf("-i"),
                    "Long VIDEO must use an early keyframe seek plus exact tail");
            for(int tick:new int[]{0,30,50,65,80,98})
                compare(original(source,tick,false),
                        decoder.decode(source,tick,false),
                        "VIDEO frame "+tick);
            for(int tick:new int[]{102,115,147})
                compare(original(source,tick,true),
                        decoder.decode(source,tick,true),
                        "Looped VIDEO frame "+tick);
            System.out.println("Bounded VIDEO seek exact reference pixels PASS");
        }
        finally
        {
            decoder.cancel();
            source.delete();
            new File(dir,"generate.log").delete();
            dir.delete();
        }
    }
}

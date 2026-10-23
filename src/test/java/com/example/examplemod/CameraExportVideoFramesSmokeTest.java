package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Verifies that the native render thread waits for the exact VIDEO image,
 * never displays an older preview texture, and releases prior GL resources.
 */
public final class CameraExportVideoFramesSmokeTest
{
    private static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }
    private static boolean prepare(CameraExportVideoFrames media,
            CameraClipDocument.Clip clip,int frame) throws Exception
    {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<limit)
        {
            if(media.prepare(Collections.singletonList(clip),frame))
                return true;
            Thread.sleep(10L);
        }
        return false;
    }
    private static boolean prepareSubframe(CameraExportVideoFrames media,
            CameraClipDocument.Clip clip,CameraExportPlan.Sample sample)
            throws Exception
    {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<limit)
        {
            if(media.prepare(Collections.singletonList(clip),sample))
                return true;
            Thread.sleep(10L);
        }
        return false;
    }
    public static void main(String[] args) throws Exception
    {
        File dir=Files.createTempDirectory("bbs-video-gl-safe-").toFile();
        File src=new File(dir,"input.mp4");
        File log=new File(dir,"ffmpeg.log");
        try
        {
            ProcessBuilder pb=new ProcessBuilder("ffmpeg","-hide_banner",
                    "-loglevel","error","-f","lavfi","-i",
                    "testsrc2=size=128x128:rate=20:duration=1",
                    "-c:v","libx264","-preset","ultrafast",
                    "-pix_fmt","yuv420p","-an","-y",src.getAbsolutePath());
            pb.redirectErrorStream(true);pb.redirectOutput(log);
            Process p=pb.start();
            check(p.waitFor(15,TimeUnit.SECONDS)&&p.exitValue()==0,
                    "Generate synthetic FFmpeg VIDEO");
            CameraClipDocument.Clip clip=new CameraClipDocument.Clip(
                    CameraClipDocument.Kind.VIDEO,0,40);
            clip.settings.put("video",src.getAbsolutePath());
            clip.settings.put("loop","true");
            CameraExportVideoFrames.validateSources(
                    Collections.singletonList(clip),40);

            CameraExportVideoFrames media=new CameraExportVideoFrames(null);
            DynamicTexture t0=null,t10=null;
            try
            {
                check(prepare(media,clip,0),"First VIDEO frame becomes ready");
                t0=media.get(clip,0);
                check(t0!=null&&t0.image.getWidth()==128,
                        "Native renderer receives exactly the first frame");
                boolean oldRejected=false;
                try{media.get(clip,1);}
                catch(IllegalStateException expected){oldRejected=true;}
                check(oldRejected,
                        "Strict exact timestamp rejects stale texture");
                // 60 FPS: the next frame shares tick 0 but must request a
                // NEW timestamp and must never silently reuse its texture.
                CameraExportPlan plan60=new CameraExportPlan(128,128,60,40);
                CameraExportPlan.Sample next60=plan60.sample(1);
                check(prepareSubframe(media,clip,next60),
                        "Fractional VIDEO frame 1/60 sec decoded");
                DynamicTexture subframe=media.get(clip,0);
                check(subframe!=null&&subframe!=t0&&t0.deleted,
                        "60 FPS VIDEO requests distinct sub-tick decode");
                check(prepare(media,clip,10),
                        "Async FFmpeg decodes next requested clip timestamp");
                t10=media.get(clip,10);
                check(t10!=null&&t10!=t0,
                        "Next VIDEO tick gets a distinct decoded texture");
                check(t0.deleted,"Previous VIDEO texture is deleted promptly");
                int different=0;
                for(int x=8;x<120;x+=16)
                    if(t10.image.getRGB(x,64)!=t0.image.getRGB(x,64))
                        different++;
                check(different>0,"VIDEO frame advances to correct source time");

                // Negative media offset means transparent frame, never
                // an accidental previous VIDEO frame from another timestamp.
                clip.settings.put("offset","-30");
                check(prepare(media,clip,12),"Negative source time is ready");
                check(media.get(clip,12)==null,
                        "VIDEO before its offset is transparent");

                clip.settings.put("offset","0");
                check(prepare(media,clip,25),
                        "Looped VIDEO is decoded at scene time > source end");
                check(media.get(clip,25)!=null,"Looped VIDEO image exists");
            }
            finally{media.close();}
            check(t10==null||t10.deleted,"Close cleans last GL texture");
            clip.settings.put("video","definitely-missing.mp4");
            boolean missing=false;
            try{CameraExportVideoFrames.validateSources(
                    Collections.singletonList(clip),40);}
            catch(IOException expected){missing=true;}
            check(missing,"Missing VIDEO file rejected before export begins");
        }
        finally
        {
            if(src.isFile())src.delete();
            if(log.isFile())log.delete();
            dir.delete();
        }
        System.out.println("CAMERA export VIDEO readiness/exact texture/loop PASS");
    }
}

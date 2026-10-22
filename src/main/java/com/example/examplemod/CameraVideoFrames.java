package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Optional FFmpeg-backed decoded-frame cache for VIDEO clip overlays.
 *
 * Decoding never stalls Minecraft's render thread. The native frame's
 * timestamp is derived from scene time, not wall-clock time, preserving
 * deterministic requests during scrubbing. This is an initial decoder:
 * final high-FPS export will need a continuous stream, exact seeking,
 * source FPS handling, and audio muxing.
 */
public final class CameraVideoFrames
{
    private static final int LIMIT=12;
    private static final ThreadPoolExecutor DECODER=
            new ThreadPoolExecutor(1,1,30L,TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(4),
                    new java.util.concurrent.ThreadFactory()
                    {
                        @Override public Thread newThread(Runnable job)
                        {
                            Thread t=new Thread(job,"BBS-CAMERA-video-decode");
                            t.setDaemon(true);return t;
                        }
                    },new ThreadPoolExecutor.AbortPolicy());
    private static final Map<String,Frame> frames=
            new LinkedHashMap<String,Frame>(32,0.75F,true);
    private static boolean warnedFFmpeg;
    private static class Frame
    {
        Future<BufferedImage> job;
        DynamicTexture texture;
    }

    /** A GPU upload happens only when the async decoded result is ready. */
    public static DynamicTexture get(String video,int frame)
    {
        File file=CameraImageCache.resolve(video);
        if(file==null||!file.isFile()||frame<0)return null;
        String key=file.getAbsolutePath()+"#"+file.lastModified()+"#"+frame;
        Frame cached=frames.get(key);
        if(cached==null)
        {
            cached=new Frame();
            final File input=file;
            final int request=frame;
            try
            {
                cached.job=DECODER.submit(new Callable<BufferedImage>()
                {
                    @Override public BufferedImage call()
                    {
                        return decode(input,request);
                    }
                });
                frames.put(key,cached);
                while(frames.size()>LIMIT)
                {
                    String victim=frames.keySet().iterator().next();
                    Frame evicted=frames.remove(victim);
                    if(evicted!=null)
                    {
                        if(evicted.job!=null)evicted.job.cancel(true);
                        if(evicted.texture!=null)evicted.texture.deleteGlTexture();
                    }
                }
            }
            catch(java.util.concurrent.RejectedExecutionException busy)
            {
                return null;
            }
        }
        if(cached.texture!=null)return cached.texture;
        if(cached.job==null||!cached.job.isDone())
            return previousDecodedFrame(file,frame);
        try
        {
            BufferedImage image=cached.job.get();
            if(image!=null)cached.texture=new DynamicTexture(image);
            cached.job=null;
            return cached.texture;
        }
        catch(Exception ignored){cached.job=null;return null;}
    }

    /**
     * Avoid a black flicker every playback tick while FFmpeg decodes the
     * requested frame. Only reuse a close, already-decoded frame belonging
     * to the same source/version; a large timeline seek must not show a
     * stale image from a completely different moment.
     */
    private static DynamicTexture previousDecodedFrame(File source,int frame)
    {
        String prefix=source.getAbsolutePath()+"#"+source.lastModified()+"#";
        for(int delta=1;delta<=2;delta++)
        {
            Frame previous=frames.get(prefix+(frame-delta));
            if(previous!=null&&previous.texture!=null)return previous.texture;
        }
        return null;
    }

    private static BufferedImage decode(File file,int frame)
    {
        Process process=null;
        try
        {
            // Accurate input seeking: -ss after -i, not before it.
            String seconds=String.format(java.util.Locale.ROOT,"%.6f",
                    frame/20D);
            ProcessBuilder command=new ProcessBuilder(
                    "ffmpeg","-hide_banner","-loglevel","quiet",
                    "-nostdin","-i",file.getAbsolutePath(),"-ss",seconds,
                    "-frames:v","1","-f","image2pipe","-vcodec","png","-");
            // FFmpeg's stderr must not fill its pipe while stdout is decoded.
            // Java 8 supports DISCARD only in newer JDKs; redirect to the
            // platform null device instead (Windows and CI Linux).
            command.redirectError(new File(System.getProperty("os.name","")
                    .toLowerCase(java.util.Locale.ROOT).contains("win")
                    ? "NUL" : "/dev/null"));
            process=command.start();
            BufferedImage image;
            try(InputStream stream=process.getInputStream())
            {
                image=ImageIO.read(stream);
            }
            if(!process.waitFor(15,TimeUnit.SECONDS))
            {
                process.destroyForcibly();return null;
            }
            if(image!=null && image.getWidth()>=1 &&
                    image.getHeight()>=1 && image.getWidth()<=4096 &&
                    image.getHeight()<=4096)return image;
        }
        catch(Exception error)
        {
            if(!warnedFFmpeg)
            {
                warnedFFmpeg=true;
                System.err.println("[BBS CAMERA] FFmpeg video decode unavailable: "
                        +error.getMessage());
            }
        }
        finally
        {
            if(process!=null)process.destroy();
        }
        return null;
    }
    public static void clear()
    {
        for(Frame frame:frames.values())
        {
            if(frame.job!=null)frame.job.cancel(true);
            if(frame.texture!=null)frame.texture.deleteGlTexture();
        }
        frames.clear();
    }
    private CameraVideoFrames(){}
}

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
    private static volatile String lastFailure;
    private static volatile String lastFailureSource;

    /** Visible in CAMERA so failed FFmpeg decoding never looks like an empty quad. */
    public static String failureFor(String video)
    {
        File file=CameraImageCache.resolve(video);
        if(file==null||!file.isFile())return "VIDEO source missing: browse for a file";
        return file.getAbsolutePath().equals(lastFailureSource)?lastFailure:null;
    }

    private static void report(File file,String reason)
    {
        String source=file.getAbsolutePath();
        if(!source.equals(lastFailureSource)||!reason.equals(lastFailure))
            System.err.println("[BBS CAMERA] VIDEO preview: "+file.getName()+": "+reason);
        lastFailureSource=source;
        lastFailure=reason;
    }
    private static class Frame
    {
        Future<BufferedImage> job;
        DynamicTexture texture;
        int width,height;
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
            if(image!=null)
            {
                cached.width=image.getWidth();cached.height=image.getHeight();
                cached.texture=new DynamicTexture(image);
                if(file.getAbsolutePath().equals(lastFailureSource))
                {lastFailureSource=null;lastFailure=null;}
            }
            else report(file,"FFmpeg returned no video frame at tick "+frame);
            cached.job=null;
            return cached.texture;
        }
        catch(Exception invalid)
        {
            report(file,"Cannot upload decoded frame: "+invalid.getMessage());
            cached.job=null;return null;
        }
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

    /**
     * Preview uses the SAME saved FFmpeg executable as offline export, not
     * the system PATH. Offline's exact decoder gives us useful errors rather
     * than a silent black preview when the executable is not installed.
     */
    private static BufferedImage decode(File file,int frame)
    {
        try
        {
            net.minecraft.client.Minecraft mc=
                    net.minecraft.client.Minecraft.getMinecraft();
            File gameDir=mc==null?null:mc.mcDataDir;
            String ffmpeg=CameraExportPreferences.loadFfmpeg(gameDir);
            return new CameraExportVideoDecoder(new File(ffmpeg))
                    .decode(file,frame,false);
        }
        catch(Exception failure)
        {
            report(file,"FFmpeg could not decode tick "+frame+": "+
                    (failure.getMessage()==null?
                            failure.getClass().getSimpleName():failure.getMessage()));
            return null;
        }
    }

    /** Original VIDEO pixel dimensions available after an async frame decode. */
    public static int[] size(String video,int frame)
    {
        File source=CameraImageCache.resolve(video);
        if(source==null)return null;
        Frame cached=frames.get(source.getAbsolutePath()+"#"+
                source.lastModified()+"#"+frame);
        return cached!=null&&cached.width>0&&cached.height>0
                ?new int[]{cached.width,cached.height}:null;
    }

    public static void clear()
    {
        for(Frame frame:frames.values())
        {
            if(frame.job!=null)frame.job.cancel(true);
            if(frame.texture!=null)frame.texture.deleteGlTexture();
        }
        frames.clear();
        lastFailure=null;lastFailureSource=null;
    }
    private CameraVideoFrames(){}
}

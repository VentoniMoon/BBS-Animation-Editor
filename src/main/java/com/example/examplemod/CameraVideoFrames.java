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
    private static final int LIMIT=16;
    private static final ThreadPoolExecutor DECODER=
            new ThreadPoolExecutor(1,1,30L,TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(6),
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
    private static final Map<String,Future<Integer>> DURATION=
            new LinkedHashMap<String,Future<Integer>>();
    private static class Frame
    {
        Future<BufferedImage> job;
        DynamicTexture texture;
    }

    /** A GPU upload happens only when the async decoded result is ready. */
    public static DynamicTexture get(String video,int frame)
    {
        return get(video,frame,false);
    }

    public static DynamicTexture get(String video,int frame,boolean loop)
    {
        File file=CameraImageCache.resolve(video);
        if(file==null||!file.isFile()||frame<0)return null;
        int sourceFrame=frame;
        if(loop)
        {
            int duration=durationFrames(file);
            if(duration>0)sourceFrame=frame%duration;
        }
        String key=file.getAbsolutePath()+"#"+file.lastModified()+"#"+sourceFrame;
        Frame cached=frames.get(key);
        if(cached==null)
        {
            cached=new Frame();
            final File input=file;
            final int request=sourceFrame;
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
                return previousDecodedFrame(file,sourceFrame);
            }
        }
        DynamicTexture ready=materialize(cached);
        return ready!=null?ready:previousDecodedFrame(file,sourceFrame);
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
        for(int delta=1;delta<=12;delta++)
        {
            Frame previous=frames.get(prefix+(frame-delta));
            if(previous==null)continue;
            // A ready decode can be uploaded by a later request: the
            // frame that initiated it might no longer be at the playhead.
            DynamicTexture texture=materialize(previous);
            if(texture!=null)return texture;
        }
        return null;
    }

    /** Must be called on the client render thread, NEVER the decoder. */
    private static DynamicTexture materialize(Frame frame)
    {
        if(frame==null)return null;
        if(frame.texture!=null)return frame.texture;
        if(frame.job==null||!frame.job.isDone())return null;
        try
        {
            BufferedImage image=frame.job.get();
            if(image!=null)frame.texture=new DynamicTexture(image);
        }
        catch(Exception ignored){}
        finally {frame.job=null;}
        return frame.texture;
    }

    /** Async duration discovery for looping: render never blocks on FFprobe. */
    private static int durationFrames(File file)
    {
        String id=file.getAbsolutePath()+"#"+file.lastModified();
        Future<Integer> future=DURATION.get(id);
        if(future==null)
        {
            final File source=file;
            try
            {
                future=DECODER.submit(new Callable<Integer>()
                {
                    @Override public Integer call()
                    {
                        return probeDurationFrames(source);
                    }
                });
                if(DURATION.size()>=32)DURATION.clear();
                DURATION.put(id,future);
            }
            catch(java.util.concurrent.RejectedExecutionException busy)
            {
                return -1;
            }
        }
        if(!future.isDone())return -1;
        try{return future.get();}
        catch(Exception failed){return -1;}
    }

    private static int probeDurationFrames(File file)
    {
        Process p=null;
        try
        {
            ProcessBuilder command=new ProcessBuilder(
                    "ffprobe","-v","quiet","-show_entries","format=duration",
                    "-of","default=noprint_wrappers=1:nokey=1",
                    file.getAbsolutePath());
            command.redirectError(new File(System.getProperty("os.name","")
                    .toLowerCase(java.util.Locale.ROOT).contains("win")
                    ?"NUL":"/dev/null"));
            p=command.start();
            String line;
            try(java.io.BufferedReader reader=new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8)))
            {
                line=reader.readLine();
            }
            if(!p.waitFor(10,TimeUnit.SECONDS))
            {
                p.destroyForcibly();return -1;
            }
            double seconds=line==null?-1D:Double.parseDouble(line.trim());
            if(!Double.isFinite(seconds)||seconds<=0D||
                    seconds>86400D)return -1;
            return Math.max(1,(int)Math.ceil(seconds*20D));
        }
        catch(Exception error){return -1;}
        finally {if(p!=null)p.destroy();}
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
        for(Future<Integer> future:DURATION.values())future.cancel(true);
        DURATION.clear();
        for(Frame frame:frames.values())
        {
            if(frame.job!=null)frame.job.cancel(true);
            if(frame.texture!=null)frame.texture.deleteGlTexture();
        }
        frames.clear();
    }
    private CameraVideoFrames(){}
}

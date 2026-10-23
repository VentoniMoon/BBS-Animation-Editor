package com.example.examplemod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Isolated VIDEO decoder for one CAMERA export session.
 *
 * FFmpeg runs on a worker thread. The Minecraft render thread calls
 * prepare(tick) before requesting the native world framebuffer; it returns
 * false until EVERY visible VIDEO clip has the correct decoded image.
 * Only then does it upload DynamicTexture images and permit world rendering.
 * The compositor requests precisely those textures (no preview cache/fallback).
 *
 * No GL calls happen on the FFmpeg worker. A single decoder/worker bounds
 * external process and memory use; a 30-second watchdog reports stuck inputs.
 */
public final class CameraExportVideoFrames implements AutoCloseable
{
    private static final int MAX_OVERLAYS=8;
    private static final long TIMEOUT_NANOS=TimeUnit.SECONDS.toNanos(30);
    private final CameraExportVideoDecoder decoder;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(
            new java.util.concurrent.ThreadFactory()
            {
                @Override public Thread newThread(Runnable task)
                {
                    Thread thread=new Thread(task,"BBS-CAMERA-export-video");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private final Map<CameraClipDocument.Clip,Frame> frames=
            new IdentityHashMap<CameraClipDocument.Clip,Frame>();
    private boolean closed;

    private static final class Frame
    {
        final int localTick;
        final File source;
        final boolean transparent;
        final long startedAt;
        Future<BufferedImage> pending;
        DynamicTexture texture;
        Frame(int localTick,File source,boolean transparent)
        {
            this.localTick=localTick;
            this.source=source;
            this.transparent=transparent;
            this.startedAt=System.nanoTime();
        }
    }

    public CameraExportVideoFrames(File ffmpeg)
    {
        decoder=new CameraExportVideoDecoder(ffmpeg);
    }

    /** A clip can be decoded only if its source exists before the first frame. */
    public static void validateSources(List<CameraClipDocument.Clip> clips,
            int totalTicks) throws IOException
    {
        for(CameraClipDocument.Clip clip:clips)
        {
            if(clip==null||!clip.enabled||
                    clip.kind!=CameraClipDocument.Kind.VIDEO||
                    clip.tick>=totalTicks)continue;
            File source=CameraImageCache.resolve(clip.settings.get("video"));
            if(source==null||!source.isFile())
                throw new IOException("Missing VIDEO clip source: "+
                        clip.settings.get("video"));
        }
    }

    /**
     * Called on the Minecraft client render thread, before beginning an
     * export-size world pass. Never blocks for an FFmpeg process.
     */
    public boolean prepare(List<CameraClipDocument.Clip> clips,int tick)
            throws IOException
    {
        if(closed)throw new IOException("Exact VIDEO decoder closed");
        List<CameraClipDocument.Clip> active=
                new ArrayList<CameraClipDocument.Clip>();
        for(CameraClipDocument.Clip clip:clips)
            if(clip!=null&&clip.enabled&&
                    clip.kind==CameraClipDocument.Kind.VIDEO&&
                    clip.contains(tick))active.add(clip);
        if(active.size()>MAX_OVERLAYS)
            throw new IOException("More than eight simultaneous VIDEO layers");

        // Release frames that are no longer used on this film timestamp.
        for(CameraClipDocument.Clip old:
                new ArrayList<CameraClipDocument.Clip>(frames.keySet()))
            if(!active.contains(old))dispose(frames.remove(old));

        for(final CameraClipDocument.Clip clip:active)
        {
            int offset=offset(clip);
            final int local=tick-clip.tick+offset;
            final File source=CameraImageCache.resolve(
                    clip.settings.get("video"));
            if(source==null||!source.isFile())
                throw new IOException("Missing VIDEO clip: "+
                        clip.settings.get("video"));
            Frame prior=frames.get(clip);
            if(prior!=null&&prior.localTick==local&&
                    source.equals(prior.source))continue;
            if(prior!=null)dispose(prior);

            if(local<0)
            {
                frames.put(clip,new Frame(local,source,true));
                continue; // Source not started yet: transparent background.
            }

            final boolean loop=Boolean.parseBoolean(
                    clip.settings.get("loop"));
            Frame request=new Frame(local,source,false);
            request.pending=worker.submit(new Callable<BufferedImage>()
            {
                @Override public BufferedImage call() throws IOException
                {
                    return decoder.decode(source,local,loop);
                }
            });
            frames.put(clip,request);
        }

        boolean ready=true;
        for(CameraClipDocument.Clip clip:active)
        {
            Frame frame=frames.get(clip);
            if(frame.transparent||frame.texture!=null)continue;
            if(frame.pending==null)
                throw new IOException("VIDEO frame job missing");
            if(!frame.pending.isDone())
            {
                if(System.nanoTime()-frame.startedAt>TIMEOUT_NANOS)
                {
                    decoder.cancel();
                    frame.pending.cancel(true);
                    throw new IOException("VIDEO decode timed out at scene tick "+
                            tick+" ("+frame.source.getName()+")");
                }
                ready=false;
                continue;
            }
            try
            {
                BufferedImage image=frame.pending.get();
                if(image==null)
                    throw new IOException("VIDEO decoded a null image");
                // Only this render-thread call touches Minecraft/OpenGL.
                frame.texture=new DynamicTexture(image);
                frame.pending=null;
            }
            catch(InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                throw new IOException("VIDEO decoding interrupted",interrupted);
            }
            catch(ExecutionException invalid)
            {
                throw new IOException("VIDEO "+frame.source.getName()+
                        " at scene tick "+tick+": "+
                        invalid.getCause().getMessage(),invalid.getCause());
            }
        }
        return ready;
    }

    /** Strictly prepared film-frame texture, never the asynchronous preview. */
    public DynamicTexture get(CameraClipDocument.Clip clip,int tick)
    {
        Frame frame=frames.get(clip);
        if(frame==null||frame.localTick!=tick-clip.tick+offset(clip))
            throw new IllegalStateException(
                    "VIDEO export frame was not prepared at tick "+tick);
        if(frame.transparent)return null;
        if(frame.texture==null)
            throw new IllegalStateException(
                    "VIDEO frame is not ready; do not reuse a stale texture");
        return frame.texture;
    }

    private static int offset(CameraClipDocument.Clip clip)
    {
        try
        {
            double value=Double.parseDouble(clip.settings.get("offset"));
            if(!Double.isFinite(value)||Math.abs(value)>1000000D)
                return 0;
            return (int)value; // Match CameraFilmCompositor preview offset semantics.
        }
        catch(RuntimeException invalid){return 0;}
    }

    private void dispose(Frame frame)
    {
        if(frame==null)return;
        if(frame.pending!=null)frame.pending.cancel(true);
        if(frame.texture!=null)frame.texture.deleteGlTexture();
    }

    @Override public void close()
    {
        if(closed)return;
        closed=true;
        decoder.cancel();
        worker.shutdownNow();
        for(Frame frame:frames.values())dispose(frame);
        frames.clear();
    }
}

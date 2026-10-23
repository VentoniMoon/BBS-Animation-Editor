package com.example.examplemod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Collections;
import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The boundary between Minecraft's GL thread and the FFmpeg writer thread.
 *
 * Minecraft requests exactly one timed scene render via nextSample(), reads
 * finished RGBA pixels on its GL thread and submits them here. A bounded
 * queue lets FFmpeg encode without blocking render-world callbacks.
 *
 * The session NEVER advances Minecraft's timeline, renders worlds, changes
 * GL state or guesses missing samples. Native scene integration is separate.
 */
public final class CameraExportSession implements AutoCloseable
{
    public interface FrameSink
    {
        void writeFrame(ByteBuffer frame) throws IOException;
        void finish() throws IOException;
        void cancel();
    }

    public enum Status
    {
        RENDERING, FINALIZING, COMPLETED, FAILED, CANCELED
    }

    private final CameraExportPlan plan;
    private final FrameSink sink;
    private static final int FRAME_SLOTS=2;
    private final ArrayBlockingQueue<byte[]> pending=
            new ArrayBlockingQueue<byte[]>(FRAME_SLOTS);
    /**
     * Recycle two preallocated frame arrays. At 4K a single RGBA frame is
     * ~32 MiB; allocating a new one for every 60 FPS frame would continuously
     * pressure the Minecraft heap and cause severe GC pauses.
     */
    private final ArrayBlockingQueue<byte[]> available=
            new ArrayBlockingQueue<byte[]>(FRAME_SLOTS);
    private final Thread worker;
    private volatile int captured;
    private volatile int encoded;
    private volatile boolean completed;
    private volatile boolean canceled;
    private volatile IOException failure;

    /**
     * Start a bounded scene-video export using the supplied FFmpeg executable.
     * The caller must still render each requested Sample into a real native
     * output-resolution film framebuffer. This is not a screen recorder.
     */
    public static CameraExportSession openFfmpeg(CameraExportPlan plan,
            File ffmpeg,File output,int crf,String preset) throws IOException
    {
        return openFfmpeg(plan,ffmpeg,output,crf,preset,
                Collections.<CameraExportAudioMuxer.Track>emptyList());
    }

    /**
     * AUDIO and VIDEO sound clips are mixed in an offline second pass.
     * When no soundtrack exists, preserve the original silent path. For
     * sound projects the first encoded video has a private filename and is
     * never mistaken for the final user-requested MP4.
     */
    public static CameraExportSession openFfmpeg(final CameraExportPlan plan,
            final File ffmpeg,final File output,int crf,String preset,
            final List<CameraExportAudioMuxer.Track> tracks)
            throws IOException
    {
        if(tracks==null)throw new IllegalArgumentException("Missing sound plan");
        final File ffprobe=CameraExportVerifier.companionProbe(ffmpeg);
        CameraExportVerifier.requireAvailable(ffprobe);
        final boolean withAudio=!tracks.isEmpty();
        final File video=withAudio?new File(output.getAbsoluteFile().getParentFile(),
                output.getName()+".silent-"+System.nanoTime()+".mp4"):output;
        final CameraExportAudioMuxer muxer=new CameraExportAudioMuxer();
        final CameraFfmpegEncoder encoder=CameraFfmpegEncoder.start(
                ffmpeg,video,plan,crf,preset);
        try
        {
            return new CameraExportSession(plan,new FrameSink()
            {
                private volatile boolean verified;
                private volatile boolean stopped;

                @Override public void writeFrame(ByteBuffer frame)
                        throws IOException
                {encoder.writeFrame(frame);}

                @Override public void finish() throws IOException
                {
                    encoder.finish();
                    if(stopped)throw new IOException("Export canceled");
                    try
                    {
                        CameraExportVerifier.verify(ffprobe,video,plan);
                        if(withAudio)
                        {
                            muxer.mux(ffmpeg,video,output,plan.sceneTicks,
                                    tracks);
                            if(stopped)throw new IOException("Audio mux canceled");
                            CameraExportVerifier.verify(ffprobe,output,plan);
                            if(!CameraExportAudioMuxer.hasAudioStream(
                                    ffprobe,output))
                                throw new IOException(
                                        "Muxed MP4 has no audio stream");
                            video.delete();
                        }
                        verified=true;
                    }
                    catch(IOException invalid)
                    {
                        output.delete();
                        if(withAudio)video.delete();
                        throw new IOException("FFprobe/FFmpeg export validation: "+
                                invalid.getMessage(),invalid);
                    }
                }

                @Override public void cancel()
                {
                    stopped=true;
                    muxer.cancel();
                    encoder.cancel();
                    if(!verified)
                    {
                        output.delete();
                        if(withAudio)video.delete();
                    }
                }
            });
        }
        catch(RuntimeException failure)
        {
            muxer.cancel();
            encoder.cancel();
            if(withAudio)video.delete();
            throw failure;
        }
    }

    public CameraExportSession(CameraExportPlan plan,FrameSink sink)
    {
        if(plan==null||sink==null)
            throw new IllegalArgumentException("Missing CAMERA export input");
        this.plan=plan;
        this.sink=sink;
        for(int i=0;i<FRAME_SLOTS;i++)
            available.add(new byte[plan.frameBytes]);
        this.worker=new Thread(new Runnable()
        {
            @Override public void run(){runEncoder();}
        },"BBS-CAMERA-ffmpeg-writer");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Non-blocking render-thread query. Null means the encoder queue has no
     * free slot, all frames have been captured, or the session stopped.
     * Call this BEFORE advancing the scene or calling glReadPixels.
     */
    public synchronized CameraExportPlan.Sample nextSample()
    {
        if(completed||canceled||failure!=null||
                captured>=plan.frameCount||available.isEmpty())
            return null;
        return plan.sample(captured);
    }

    /**
     * Copy exactly one already-rendered, bottom-up RGBA film frame.
     * No GL functions may run here. Returns false on backpressure, so the
     * caller can retry the SAME frame without advancing scene time.
     *
     * Buffer ownership always stays with the GL caller; FFmpeg receives
     * an immutable snapshot, not the next frame's recycled PBO/readback.
     */
    public synchronized boolean submitFrame(int index,ByteBuffer pixels)
    {
        // FFmpeg may fail on its worker thread *between* nextSample() and
        // this GL-thread submit. Report a closed session without throwing
        // through Minecraft's render callback; the caller can read
        // getStatus()/getFailure() and abort safely.
        if(canceled||failure!=null||completed)return false;
        if(index!=captured)
            throw new IllegalArgumentException(
                    "Out-of-order frame "+index+"; expected "+captured);
        if(pixels==null||pixels.remaining()!=plan.frameBytes)
            throw new IllegalArgumentException("Invalid RGBA frame byte length");
        byte[] copy=available.poll();
        if(copy==null)return false;
        try
        {
            pixels.asReadOnlyBuffer().get(copy);
            if(!pending.offer(copy))
            {
                available.offer(copy);
                return false;
            }
            captured++;
            return true;
        }
        catch(RuntimeException failure)
        {
            available.offer(copy);
            throw failure;
        }
    }

    private void runEncoder()
    {
        try
        {
            for(int frame=0;frame<plan.frameCount;)
            {
                if(canceled)return;
                byte[] bytes=pending.poll(250L,TimeUnit.MILLISECONDS);
                if(bytes==null)continue;
                try
                {
                    if(canceled)return;
                    sink.writeFrame(ByteBuffer.wrap(bytes));
                    encoded=++frame;
                }
                finally
                {
                    available.offer(bytes);
                }
            }
            if(canceled)return;
            sink.finish();
            if(!canceled)completed=true;
        }
        catch(InterruptedException stopped)
        {
            if(!canceled)
            {
                Thread.currentThread().interrupt();
                failure=new IOException("CAMERA encoding thread interrupted",stopped);
            }
        }
        catch(IOException badFrame)
        {
            if(!canceled)failure=badFrame;
        }
        catch(RuntimeException badFrame)
        {
            if(!canceled)
                failure=new IOException("CAMERA encoder crashed",badFrame);
        }
        finally
        {
            if(!completed)
            {
                canceled=canceled||failure==null;
                sink.cancel();
            }
            pending.clear();
        }
    }

    public Status getStatus()
    {
        if(completed)return Status.COMPLETED;
        if(failure!=null)return Status.FAILED;
        if(canceled)return Status.CANCELED;
        if(captured==plan.frameCount)return Status.FINALIZING;
        return Status.RENDERING;
    }

    public CameraExportPlan getPlan(){return plan;}
    public int getCapturedFrames(){return captured;}
    public int getEncodedFrames(){return encoded;}
    public IOException getFailure(){return failure;}

    /**
     * Safe to call from the UI while the worker is blocked on FFmpeg stdin.
     * Does not wait on Minecraft's render thread or delete source assets.
     */
    public void cancel()
    {
        if(completed||canceled||failure!=null)return;
        canceled=true;
        sink.cancel();
        worker.interrupt();
    }

    /** For CLI/tests only. Never wait on this from Minecraft's GL thread. */
    public boolean await(long timeout,TimeUnit unit) throws InterruptedException
    {
        worker.join(Math.max(1L,unit.toMillis(timeout)));
        return !worker.isAlive();
    }

    @Override public void close()
    {
        if(!completed)cancel();
    }
}

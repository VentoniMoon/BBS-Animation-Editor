package com.example.examplemod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic order, GL buffer ownership and cancellation regression. */
public final class CameraExportSessionSmokeTest
{
    private static void check(boolean value,String message)
    {
        if(!value)throw new AssertionError(message);
    }

    private static final class CountingSink
            implements CameraExportSession.FrameSink
    {
        final List<Integer> observed=new ArrayList<Integer>();
        final AtomicInteger cancels=new AtomicInteger();
        final boolean failAtTwo;
        boolean finalized;
        CountingSink(boolean failAtTwo){this.failAtTwo=failAtTwo;}

        @Override public synchronized void writeFrame(ByteBuffer frame)
                throws IOException
        {
            if(frame.remaining()!=128*128*4)
                throw new AssertionError("incorrect frame length");
            int first=frame.get()&255;
            if(failAtTwo&&observed.size()==2)
                throw new IOException("Deliberate FFmpeg failure");
            observed.add(first);
            // Simulate the encoder being briefly slower than a GPU draw.
            try{Thread.sleep(4L);}catch(InterruptedException stop)
            {Thread.currentThread().interrupt();}
        }
        @Override public synchronized void finish(){finalized=true;}
        @Override public void cancel(){cancels.incrementAndGet();}
    }

    public static void main(String[] args) throws Exception
    {
        CameraExportPlan plan=new CameraExportPlan(128,128,60,4);
        check(plan.frameCount==12,"expected twelve output frames");
        CountingSink output=new CountingSink(false);
        CameraExportSession session=new CameraExportSession(plan,output);
        int observedBackpressure=0;
        try
        {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(session.getStatus()==CameraExportSession.Status.RENDERING)
            {
                if(System.nanoTime()>until)
                    throw new AssertionError("Encoder did not accept frames");
                CameraExportPlan.Sample sample=session.nextSample();
                if(sample==null)
                {
                    observedBackpressure++;
                    Thread.sleep(2L);
                    continue;
                }
                check(sample.outputFrame==session.getCapturedFrames(),
                        "frame ownership must be exact");
                ByteBuffer buffer=ByteBuffer.allocate(plan.frameBytes);
                for(int i=0;i<plan.frameBytes;i+=4)
                {
                    buffer.put(i,(byte)sample.outputFrame);
                    buffer.put(i+3,(byte)255);
                }
                check(session.submitFrame(sample.outputFrame,buffer),
                        "submit while capacity is known");
                check(buffer.position()==0,
                        "GL caller's readback must not be consumed");
                // Encoder receives copies. Change original immediately.
                buffer.put(0,(byte)0x7F);
            }
            check(session.await(10,TimeUnit.SECONDS),
                    "encoder worker should exit after final frame");
            check(session.getStatus()==CameraExportSession.Status.COMPLETED,
                    "completed only after sink finish");
            check(output.finalized,"backend finalization must run");
            check(session.getEncodedFrames()==plan.frameCount,
                    "all frames were encoded");
            check(output.observed.size()==plan.frameCount,
                    "all frames passed once");
            for(int i=0;i<output.observed.size();i++)
                check(output.observed.get(i)==i,
                        "exact order and protected buffer ownership at "+i);
            check(output.cancels.get()==0,"successful export never cancelled");
            System.out.println("CAMERA session: ordered frames "+
                    plan.frameCount+", backpressure polls "+
                    observedBackpressure);
        }
        finally{session.close();}

        CountingSink failureSink=new CountingSink(true);
        CameraExportSession failure=new CameraExportSession(plan,failureSink);
        try
        {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(failure.getStatus()==CameraExportSession.Status.RENDERING)
            {
                if(System.nanoTime()>until)throw new AssertionError("Failure timeout");
                CameraExportPlan.Sample sample=failure.nextSample();
                if(sample==null){Thread.sleep(2L);continue;}
                failure.submitFrame(sample.outputFrame,
                        ByteBuffer.wrap(new byte[plan.frameBytes]));
            }
            check(failure.await(10,TimeUnit.SECONDS),"failed encoder must stop");
            check(failure.getStatus()==CameraExportSession.Status.FAILED,
                    "backend error must be reported instead of success");
            check(failure.getFailure()!=null,"preserve encoding error");
            check(failureSink.cancels.get()>0,"failed output cleaned");
        }
        finally{failure.close();}

        CountingSink canceledSink=new CountingSink(false);
        CameraExportSession canceled=new CameraExportSession(plan,canceledSink);
        canceled.cancel();
        check(canceled.await(10,TimeUnit.SECONDS),
                "cancel stops export worker");
        check(canceled.getStatus()==CameraExportSession.Status.CANCELED,
                "explicit user cancellation is not an error");
        check(canceledSink.cancels.get()>0,"cancel cleans backend");
        System.out.println("CAMERA export session order/backpressure/cancel PASS");
    }
}

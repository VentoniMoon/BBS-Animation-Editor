package com.example.examplemod;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Verify the final video *content* with an external ffprobe executable,
 * not merely FFmpeg's exit code and a non-empty file.
 *
 * This utility never launches a shell and never changes OpenGL/Minecraft
 * state. It can be run by the export worker after MP4 finalization.
 */
public final class CameraExportVerifier
{
    private CameraExportVerifier(){}

    public static void verify(File ffprobe,File video,CameraExportPlan plan)
            throws IOException
    {
        if(video==null||!video.isFile()||plan==null)
            throw new IOException("Missing MP4 export or frame plan");
        String probe=ffprobe==null?"ffprobe":ffprobe.getPath();

        ProcessBuilder cmd=new ProcessBuilder(probe,
                "-v","error",
                "-select_streams","v:0",
                "-count_frames",
                "-show_entries","stream=width,height,avg_frame_rate,nb_read_frames",
                "-of","default=noprint_wrappers=1",
                video.getAbsolutePath());
        cmd.redirectErrorStream(true);
        Process process=cmd.start();
        StringBuilder result=new StringBuilder();
        try
        {
            // ffprobe normally responds immediately to a complete local MP4.
            // Read bounded output while the worker is running. We request
            // only four small keys; a huge response is a broken probe.
            try(BufferedReader in=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),StandardCharsets.UTF_8)))
            {
                String line;
                while((line=in.readLine())!=null)
                {
                    if(result.length()>16384)
                        throw new IOException("Unexpectedly large FFprobe output");
                    result.append(line).append('\n');
                }
            }
            if(!process.waitFor(30,TimeUnit.SECONDS))
                throw new IOException("FFprobe verification timed out");
            if(process.exitValue()!=0)
                throw new IOException("FFprobe failed: "+result.toString().trim());
            validate(result.toString(),plan);
        }
        catch(InterruptedException stop)
        {
            Thread.currentThread().interrupt();
            throw new IOException("FFprobe interrupted",stop);
        }
        finally
        {
            if(process.isAlive())process.destroyForcibly();
        }
    }

    /**
     * Exposed for dependency-free deterministic tests; each property must
     * match. Fractional FPS is accepted only when numerically equivalent.
     */
    public static void validate(String output,CameraExportPlan plan)
            throws IOException
    {
        if(plan==null||output==null)
            throw new IOException("Missing FFprobe metadata");
        Map<String,String> values=new HashMap<String,String>();
        String[] lines=output.split("\n");
        for(String line:lines)
        {
            int equals=line.indexOf('=');
            if(equals<1)continue;
            values.put(line.substring(0,equals).trim(),
                    line.substring(equals+1).trim());
        }
        int width=parseInt(values,"width");
        int height=parseInt(values,"height");
        int frames=parseInt(values,"nb_read_frames");
        if(width!=plan.width||height!=plan.height)
            throw new IOException("Export dimension mismatch: "+width+"x"+
                    height+", expected "+plan.width+"x"+plan.height);
        if(frames!=plan.frameCount)
            throw new IOException("Export frame count mismatch: "+frames+
                    ", expected "+plan.frameCount);
        String rate=values.get("avg_frame_rate");
        double fps;
        try
        {
            String[] fractional=rate.split("/");
            double numerator=Double.parseDouble(fractional[0]);
            double denominator=fractional.length==2?
                    Double.parseDouble(fractional[1]):1D;
            fps=numerator/denominator;
        }
        catch(RuntimeException invalid)
        {
            throw new IOException("Invalid FFprobe frame rate: "+rate,invalid);
        }
        if(!Double.isFinite(fps)||Math.abs(fps-plan.fps)>0.00001D)
            throw new IOException("Export FPS mismatch: "+rate+
                    ", expected "+plan.fps);
    }

    private static int parseInt(Map<String,String> values,String name)
            throws IOException
    {
        String value=values.get(name);
        try{return Integer.parseInt(value);}
        catch(RuntimeException invalid)
        {
            throw new IOException("Missing or invalid FFprobe "+name+
                    ": "+value,invalid);
        }
    }
}

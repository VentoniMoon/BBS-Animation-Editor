package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    /**
     * Windows FFmpeg essentials ships ffmpeg.exe and ffprobe.exe together.
     * If the user selected an absolute executable, prefer its sibling instead
     * of assuming that system PATH also contains that build's ffprobe.
     * Bare executable names still resolve through PATH.
     */
    public static File companionProbe(File ffmpeg)
    {
        if(ffmpeg==null||ffmpeg.getParent()==null)return null;
        File folder=ffmpeg.getAbsoluteFile().getParentFile();
        String executable=ffmpeg.getName().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".exe")?"ffprobe.exe":"ffprobe";
        File companion=new File(folder,executable);
        return companion.isFile()?companion:null;
    }

    /**
     * Fail before an expensive Minecraft render if ffprobe is unavailable.
     * A bare command resolves via PATH, while an explicitly selected FFmpeg
     * 7.1 executable prefers the companion utility in the same bin folder.
     */
    public static void requireAvailable(File ffprobe) throws IOException
    {
        String probe=ffprobe==null?"ffprobe":ffprobe.getPath();
        File discard=new File(System.getProperty("os.name","")
                .toLowerCase(java.util.Locale.ROOT).contains("win")
                ?"NUL":"/dev/null");
        Process process=null;
        try
        {
            process=new ProcessBuilder(probe,"-version")
                    .redirectErrorStream(true)
                    .redirectOutput(discard)
                    .start();
            if(!process.waitFor(5,TimeUnit.SECONDS))
                throw new IOException("FFprobe did not respond within five seconds");
            if(process.exitValue()!=0)
                throw new IOException("FFprobe could not start: "+probe);
        }
        catch(InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IOException("FFprobe availability check interrupted",
                    interrupted);
        }
        catch(IOException problem)
        {
            throw new IOException("FFprobe unavailable ("+probe+"): "+
                    problem.getMessage(),problem);
        }
        finally
        {
            if(process!=null&&process.isAlive())process.destroyForcibly();
        }
    }

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
        // Avoid an unbounded pipe read blocking before the wait timeout.
        File log=File.createTempFile("bbs-camera-probe-",".txt");
        Process process=null;
        try
        {
            cmd.redirectErrorStream(true);
            cmd.redirectOutput(log);
            process=cmd.start();
            if(!process.waitFor(30,TimeUnit.SECONDS))
                throw new IOException("FFprobe verification timed out");
            if(log.length()>16384L)
                throw new IOException("Unexpectedly large FFprobe output");
            String result=new String(Files.readAllBytes(log.toPath()),
                    StandardCharsets.UTF_8);
            if(process.exitValue()!=0)
                throw new IOException("FFprobe failed: "+result.trim());
            validate(result,plan);
        }
        catch(InterruptedException stop)
        {
            Thread.currentThread().interrupt();
            throw new IOException("FFprobe interrupted",stop);
        }
        finally
        {
            if(process!=null&&process.isAlive())process.destroyForcibly();
            if(log.isFile())log.delete();
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

package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable export-time snapshot of CAMERA AUDIO and VIDEO sound sources. */
public final class CameraExportAudioTracks
{
    public static List<CameraExportAudioMuxer.Track> collect(
            List<CameraClipDocument.Clip> clips,int sceneTicks,
            File ffprobe) throws IOException
    {
        List<CameraExportAudioMuxer.Track> result=
                new ArrayList<CameraExportAudioMuxer.Track>();
        Map<File,Boolean> probed=new HashMap<File,Boolean>();
        for(CameraClipDocument.Clip clip:clips)
        {
            if(clip==null||!clip.enabled||clip.tick>=sceneTicks||
                    (clip.kind!=CameraClipDocument.Kind.AUDIO&&
                     clip.kind!=CameraClipDocument.Kind.VIDEO))continue;

            boolean video=clip.kind==CameraClipDocument.Kind.VIDEO;
            String explicit=clip.settings.get("audio");
            if(!video&&(explicit==null||explicit.trim().isEmpty()))
                throw new IOException("Missing AUDIO clip file at tick "+clip.tick);
            boolean embedded=video&&(explicit==null||explicit.trim().isEmpty());
            String sourcePath=embedded?clip.settings.get("video"):explicit;
            File source=CameraImageCache.resolve(sourcePath);
            if(source==null||!source.isFile())
                throw new IOException("Missing CAMERA soundtrack: "+sourcePath);
            source=source.getAbsoluteFile();

            Boolean available=probed.get(source);
            if(available==null)
            {
                available=CameraExportAudioMuxer.hasAudioStream(ffprobe,source);
                probed.put(source,available);
            }
            if(!available)
            {
                if(embedded)continue; // Silent VIDEO is a valid visual layer.
                throw new IOException("CAMERA audio source has no sound: "+
                        source.getName());
            }

            int offset=integer(clip,"offset",0);
            int delay=offset<0?-offset:0;
            long start=(long)clip.tick+delay;
            long clipEnd=(long)clip.tick+clip.duration;
            long end=Math.min(sceneTicks,clipEnd);
            if(start>=end)continue;
            if(start>Integer.MAX_VALUE||end-start>Integer.MAX_VALUE)
                throw new IOException("Audio clip exceeds export timeline");

            double volume=Math.max(0D,Math.min(2D,
                    number(clip,"volume",1D)));
            if(volume==0D)continue;
            boolean envelope=Boolean.parseBoolean(
                    clip.settings.get("envelope.enabled"));
            int mode=integer(clip,"envelope.mode",0);
            int fadeIn=envelope&&mode!=2?
                    Math.max(0,integer(clip,"envelope.fadeIn",10)):0;
            int fadeOut=envelope&&mode!=1?
                    Math.max(0,integer(clip,"envelope.fadeOut",10)):0;
            // The negative offset delays audible playback. Preserve the
            // original scene-relative envelope instead of restarting it.
            fadeIn=Math.max(0,fadeIn-delay);
            // Trimming at the scene end must not invent an early fade-out.
            if(end<clipEnd)fadeOut=0;

            result.add(new CameraExportAudioMuxer.Track(
                    source,video&&Boolean.parseBoolean(
                            clip.settings.get("loop")),
                    (int)start,Math.max(0,offset),(int)(end-start),
                    volume,fadeIn,fadeOut));
            if(result.size()>16)
                throw new IOException("More than 16 CAMERA sound tracks");
        }
        return result;
    }

    private static int integer(CameraClipDocument.Clip clip,
            String key,int fallback)
    {
        double value=number(clip,key,fallback);
        if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)
            return fallback;
        return (int)value;
    }
    private static double number(CameraClipDocument.Clip clip,
            String key,double fallback)
    {
        try
        {
            double value=Double.parseDouble(clip.settings.get(key));
            return Double.isFinite(value)?value:fallback;
        }
        catch(RuntimeException invalid){return fallback;}
    }
    private CameraExportAudioTracks(){}
}

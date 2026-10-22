package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * Scene-time synchronized soundtrack playback for CAMERA's WAV Audio clips.
 * The offline film render will eventually mix this same timeline on disk;
 * for now this is live editor preview only. No dependency on Minema/Aperture.
 */
public final class CameraAudioClips
{
    private static final int MAX_TRACKS=8;
    private final Map<CameraClipDocument.Clip,Track> tracks=
            new IdentityHashMap<CameraClipDocument.Clip,Track>();

    private static final class Track
    {
        final Clip sound;
        final File file;
        int lastFrame=-1;
        boolean wasPlaying;
        Track(Clip sound,File file)
        {
            this.sound=sound;this.file=file;
        }
        void close(){sound.stop();sound.close();}
        void pause(){sound.stop();wasPlaying=false;lastFrame=-1;}
    }

    public synchronized void update(List<CameraClipDocument.Clip> clips,
            int sceneFrame,boolean playing)
    {
        // Tear down any stale or changed clips immediately, including when
        // switching scenes, deleting clips or replacing an audio path.
        Iterator<Map.Entry<CameraClipDocument.Clip,Track>> iter=
                tracks.entrySet().iterator();
        while(iter.hasNext())
        {
            Map.Entry<CameraClipDocument.Clip,Track> entry=iter.next();
            CameraClipDocument.Clip key=entry.getKey();
            File desired=fileFor(key);
            if(!clips.contains(key)||desired==null||
                    !desired.equals(entry.getValue().file))
            {
                entry.getValue().close();iter.remove();
            }
        }

        for(CameraClipDocument.Clip clip:clips)
        {
            if(clip.kind!=CameraClipDocument.Kind.AUDIO&&
                    clip.kind!=CameraClipDocument.Kind.VIDEO)continue;
            if(!clip.enabled||!clip.contains(sceneFrame))
            {
                Track t=tracks.get(clip);
                if(t!=null)t.pause();
                continue;
            }
            File file=fileFor(clip);
            if(file==null||!file.isFile())continue;
            Track track=tracks.get(clip);
            if(track==null)
            {
                if(tracks.size()>=MAX_TRACKS)continue;
                track=open(file);
                if(track==null)continue;
                tracks.put(clip,track);
            }

            setVolume(track,clip,sceneFrame);
            int offset=(int)number(clip,"offset",0D);
            int local=sceneFrame-clip.tick+offset;
            if(local<0){track.pause();continue;}
            long target=Math.min(track.sound.getMicrosecondLength(),
                    Math.max(0L,(long)local*50000L));
            if(target>=track.sound.getMicrosecondLength())
            {
                track.pause();continue;
            }
            boolean jumped=track.lastFrame>=0&&(sceneFrame<track.lastFrame||
                    sceneFrame>track.lastFrame+2);
            boolean seek=!playing||jumped||!track.wasPlaying||
                    Math.abs(track.sound.getMicrosecondPosition()-target)>150000L;
            if(seek)
                track.sound.setMicrosecondPosition(target);
            if(playing&&!track.sound.isRunning())track.sound.start();
            if(!playing&&track.sound.isRunning())track.sound.stop();
            track.lastFrame=sceneFrame;
            track.wasPlaying=playing;
        }
    }

    private static File fileFor(CameraClipDocument.Clip clip)
    {
        String raw=clip.settings.get("audio");
        if(raw==null||raw.trim().isEmpty())return null;
        File file=CameraImageCache.resolve(raw);
        return file==null?null:file.getAbsoluteFile();
    }
    private static Track open(File file)
    {
        if(!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".wav")
                ||file.length()>64L*1024L*1024L)return null;
        AudioInputStream source=null,pcm=null;
        Clip sound=null;
        try
        {
            source=AudioSystem.getAudioInputStream(file);
            AudioFormat original=source.getFormat();
            AudioFormat target=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    original.getSampleRate(),16,original.getChannels(),
                    original.getChannels()*2,original.getSampleRate(),false);
            pcm=AudioSystem.getAudioInputStream(target,source);
            sound=AudioSystem.getClip();
            sound.open(pcm);
            return new Track(sound,file);
        }
        catch(Exception error)
        {
            if(sound!=null)sound.close();
            System.err.println("[BBS CAMERA] Audio clip load failed: "+
                    file.getName()+" ("+error.getClass().getSimpleName()+")");
            return null;
        }
        finally
        {
            if(pcm!=null)try{pcm.close();}catch(IOException ignored){}
            if(source!=null)try{source.close();}catch(IOException ignored){}
        }
    }
    private static void setVolume(Track track,CameraClipDocument.Clip clip,
            int sceneFrame)
    {
        if(!track.sound.isControlSupported(FloatControl.Type.MASTER_GAIN))return;
        FloatControl volume=(FloatControl)track.sound.getControl(
                FloatControl.Type.MASTER_GAIN);
        double linear=Math.max(0D,Math.min(2D,
                number(clip,"volume",1D)*
                CameraClipEnvelope.factor(clip,sceneFrame)));
        float db=(float)(linear<=0D?volume.getMinimum():
                Math.min(volume.getMaximum(),Math.max(volume.getMinimum(),
                        20D*Math.log10(linear))));
        volume.setValue(db);
    }
    private static double number(CameraClipDocument.Clip clip,
            String key,double fallback)
    {
        try
        {
            double result=Double.parseDouble(clip.settings.get(key));
            return Double.isFinite(result)?result:fallback;
        }
        catch(RuntimeException error){return fallback;}
    }
    public synchronized void pause()
    {
        for(Track track:tracks.values())track.pause();
    }
    public synchronized void clear()
    {
        for(Track track:tracks.values())track.close();
        tracks.clear();
    }
}

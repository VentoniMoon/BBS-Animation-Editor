package com.example.examplemod;

import java.io.File;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import net.minecraft.client.Minecraft;

/**
 * Lightweight preview playback for CAMERA AUDIO clips.
 *
 * Playback seeks from the same 20 TPS clock as actors/lip-sync and pauses
 * when the editor timeline pauses. WAV/Java-Sound-compatible sources work
 * directly; audio embedded in MP4 is part of a separate export/audio-mux
 * integration and is not silently represented as working by this class.
 */
public final class CameraClipAudio
{
    private final Map<CameraClipTrack.Clip,Played> playing=
        new IdentityHashMap<CameraClipTrack.Clip,Played>();
    private static final class Played
    {
        Clip audio;
        String path;
        long lastFrame;
        void dispose()
        {
            if(audio!=null)
            {
                audio.stop();audio.close();audio=null;
            }
        }
    }

    public void reset()
    {
        for(Played p:playing.values())p.dispose();
        playing.clear();
    }

    public void update(CameraClipTrack track,int tick,boolean running)
    {
        if(track==null){reset();return;}
        for(CameraClipTrack.Clip c:track.all())
        {
            if(c.type!=CameraClipTrack.Type.AUDIO)continue;
            Played state=playing.get(c);
            if(state==null){state=new Played();playing.put(c,state);}
            String path=c.text("resource");
            if(!path.equals(state.path))
            {
                state.dispose();
                state.path=path;
                state.audio=open(path);
            }
            if(state.audio==null)continue;
            double clipSeconds=(tick-c.tick+c.value("offset",0D))/20D;
            if(!c.enabled||tick<c.tick||tick>=c.tick+c.duration||
                clipSeconds<0||clipSeconds>=state.audio.getMicrosecondLength()/1E6)
            {
                if(state.audio.isRunning())state.audio.stop();
                continue;
            }
            long desired=Math.max(0,Math.min(state.audio.getMicrosecondLength(),
                Math.round(clipSeconds*1000000)));
            if(Math.abs(state.audio.getMicrosecondPosition()-desired)>150000)
                state.audio.setMicrosecondPosition(desired);
            adjustVolume(state.audio,c.value("volume",1D)*c.alpha(tick-c.tick));
            if(running)
            {
                if(!state.audio.isRunning())state.audio.start();
            }
            else
            {
                if(state.audio.isRunning())state.audio.stop();
            }
        }
        // Prevent orphan sounds after replacing a scene or removing a clip.
        Iterator<Map.Entry<CameraClipTrack.Clip,Played>> iter=
            playing.entrySet().iterator();
        while(iter.hasNext())
        {
            Map.Entry<CameraClipTrack.Clip,Played> entry=iter.next();
            if(!track.all().contains(entry.getKey()))
            {
                entry.getValue().dispose();
                iter.remove();
            }
        }
    }

    private static void adjustVolume(Clip clip,double gain)
    {
        if(!clip.isControlSupported(FloatControl.Type.MASTER_GAIN))return;
        try
        {
            FloatControl volume=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db=(float)(20D*Math.log10(Math.max(0.0001D,gain)));
            volume.setValue(Math.max(volume.getMinimum(),Math.min(volume.getMaximum(),db)));
        }
        catch(Exception ignored){}
    }

    private static Clip open(String resource)
    {
        if(resource==null||resource.isEmpty())return null;
        File file=new File(resource);
        if(!file.isAbsolute())file=new File(Minecraft.getMinecraft().mcDataDir,resource);
        if(!file.isFile())return null;
        AudioInputStream source=null,converted=null;
        try
        {
            source=AudioSystem.getAudioInputStream(file);
            AudioFormat format=source.getFormat();
            AudioFormat pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                format.getSampleRate(),16,format.getChannels(),
                format.getChannels()*2,format.getSampleRate(),false);
            converted=AudioSystem.getAudioInputStream(pcm,source);
            Clip clip=AudioSystem.getClip();
            clip.open(converted);
            return clip;
        }
        catch(Exception e)
        {
            System.err.println("[BBS Editor][CAMERA] Audio preview unsupported: "+
                file.getName()+" ("+e.getClass().getSimpleName()+")");
            return null;
        }
        finally
        {
            try{if(converted!=null)converted.close();}catch(Exception ignored){}
            try{if(source!=null)source.close();}catch(Exception ignored){}
        }
    }
}

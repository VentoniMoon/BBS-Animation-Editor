package com.example.examplemod;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;

/** WAV playback is slaved to the editor's 20 FPS timeline, never the reverse. */
public final class EditorWavAudioController
{
    private Clip clip;
    private File file;
    private boolean wasPlaying;
    private int lastFrame = -1;

    public String getFileName()
    {
        return file == null ? "No WAV loaded" : file.getName();
    }

    public boolean isLoaded() { return clip != null; }

    public synchronized File getLoadedFile() { return file; }

    public synchronized void load(File input) throws Exception
    {
        if (input == null || !input.isFile() ||
                !input.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".wav"))
            throw new IOException("Choose an existing .wav file");
        AudioInputStream source = null;
        AudioInputStream decoded = null;
        Clip next = null;
        try
        {
            source = AudioSystem.getAudioInputStream(input);
            AudioFormat original = source.getFormat();
            AudioFormat playable = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, original.getSampleRate(),
                    16, original.getChannels(), original.getChannels() * 2,
                    original.getSampleRate(), false);
            decoded = AudioSystem.getAudioInputStream(playable, source);
            next = AudioSystem.getClip();
            next.open(decoded);
            unload();
            clip = next;
            file = input;
            lastFrame = -1;
            wasPlaying = false;
        }
        catch (Exception e)
        {
            if (next != null) next.close();
            throw e;
        }
        finally
        {
            if (decoded != null) try { decoded.close(); } catch (IOException ignored) {}
            if (source != null) try { source.close(); } catch (IOException ignored) {}
        }
    }

    public synchronized void update(int frame, boolean playing)
    {
        if (clip == null) return;
        long desired = Math.max(0L, (long)frame * 50000L);
        long limited = Math.min(clip.getMicrosecondLength(), desired);
        int target = (int)Math.min(Integer.MAX_VALUE, limited);
        int position = (int)Math.min(Integer.MAX_VALUE, clip.getMicrosecondPosition());
        if (target >= clip.getMicrosecondLength())
        {
            if (clip.isRunning()) clip.stop();
            wasPlaying = playing;
            lastFrame = frame;
            return;
        }
        boolean jumped = lastFrame >= 0 && (frame < lastFrame ||
                frame > lastFrame + 2);
        boolean seek = !playing || jumped || !wasPlaying ||
                Math.abs(position - target) > 150000;
        if (seek) clip.setMicrosecondPosition(target);
        if (playing && !clip.isRunning()) clip.start();
        if (!playing && clip.isRunning()) clip.stop();
        lastFrame = frame;
        wasPlaying = playing;
    }

    public synchronized void pause()
    {
        if (clip != null) clip.stop();
        wasPlaying = false;
    }

    public synchronized void unload()
    {
        if (clip != null)
        {
            clip.stop();
            clip.close();
        }
        clip = null;
        file = null;
        wasPlaying = false;
        lastFrame = -1;
    }
}

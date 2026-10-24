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
    /** Monitoring only: does not affect WAV loading, lip-sync or CAMERA MP4. */
    private boolean audible = true;

    public String getFileName()
    {
        return file == null ? "No WAV loaded" : file.getName();
    }

    public boolean isLoaded() { return clip != null; }

    public synchronized File getLoadedFile() { return file; }

    public synchronized boolean isAudible() { return audible; }

    /**
     * Mute only the editor's local WAV monitoring. It must not unload the
     * sample or change the scene's independent CAMERA AUDIO clips.
     * Restoring sound resumes at the CURRENT timeline frame, never where
     * the Java Sound clip had been stopped.
     */
    public synchronized void setAudible(boolean enabled)
    {
        if(audible==enabled)return;
        audible=enabled;
        if(clip!=null && clip.isRunning())clip.stop();
        wasPlaying=false;
        lastFrame=-1;
        rememberAudible();
    }

    public synchronized void toggleAudible()
    {
        setAudible(!audible);
    }

    private File monitorSetting()
    {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                "config/bbs-editor/wav-monitor-muted.txt");
    }

    /** Machine-local monitoring preference, intentionally not scene data. */
    public synchronized void restoreAudible()
    {
        File setting=monitorSetting();
        if(!setting.isFile())return;
        try
        {
            byte[] bytes=java.nio.file.Files.readAllBytes(setting.toPath());
            String value=new String(bytes,
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            if("true".equalsIgnoreCase(value))setAudible(false);
            else if("false".equalsIgnoreCase(value))setAudible(true);
        }
        catch(IOException failure)
        {
            System.err.println("[BBS Editor] Cannot read WAV monitor state: "+
                    failure.getMessage());
        }
    }

    private void rememberAudible()
    {
        try
        {
            File setting=monitorSetting();
            File folder=setting.getParentFile();
            if(!folder.isDirectory()&&!folder.mkdirs())return;
            java.nio.file.Files.write(setting.toPath(),
                    Boolean.toString(!audible).getBytes(
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        catch(IOException failure)
        {
            System.err.println("[BBS Editor] Cannot save WAV monitor state: "+
                    failure.getMessage());
        }
    }

    private File selectionFile()
    {
        return new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                "config/bbs-editor/last-wav.txt");
    }

    /** Restore the editor's last selected WAV, including after reopening Minecraft. */
    public synchronized void restoreLastLoaded()
    {
        if (isLoaded()) return;
        File selection = selectionFile();
        if (!selection.isFile()) return;
        try
        {
            byte[] bytes = java.nio.file.Files.readAllBytes(selection.toPath());
            File previous = new File(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim());
            if (previous.isFile()) load(previous);
        }
        catch (Exception e)
        {
            System.err.println("[BBS Editor] Could not restore WAV: " + e);
        }
    }

    private void rememberLoaded(File selected)
    {
        try
        {
            File setting = selectionFile();
            File folder = setting.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            java.nio.file.Files.write(setting.toPath(),
                    selected.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            System.err.println("[BBS Editor] Could not remember WAV selection: " + e);
        }
    }

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
            rememberLoaded(input);
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
        if (!audible)
        {
            if (clip.isRunning())clip.stop();
            wasPlaying=false;
            lastFrame=-1;
            return;
        }
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

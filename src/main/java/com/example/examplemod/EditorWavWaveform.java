package com.example.examplemod;

import java.io.File;
import javax.sound.sampled.*;
import net.minecraft.client.gui.Gui;
import java.io.IOException;

/** Cached 20-Hz peak envelope, drawn with the editor's active theme and timeline coordinates. */
public final class EditorWavWaveform
{
    public static final int HEIGHT = 54;
    private static final int MAX_HALF_AMPLITUDE = 11;
    private File cachedFile;
    private float[] peaks = new float[0];

    public void sync(File file)
    {
        if (file == cachedFile) return;
        cachedFile = file;
        peaks = new float[0];
        if (file == null) return;
        AudioInputStream source = null;
        AudioInputStream pcm = null;
        try
        {
            source = AudioSystem.getAudioInputStream(file);
            AudioFormat input = source.getFormat();
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    input.getSampleRate(), 16, input.getChannels(),
                    input.getChannels() * 2, input.getSampleRate(), false);
            pcm = AudioSystem.getAudioInputStream(format, source);
            int bytesPerSampleFrame = format.getFrameSize();
            int audioFramesPerTick = Math.max(1, Math.round(format.getSampleRate() / 20F));
            int bytesPerTick = audioFramesPerTick * bytesPerSampleFrame;
            java.util.ArrayList<Float> values = new java.util.ArrayList<Float>();
            byte[] buffer = new byte[8192];
            int tickBytes = 0;
            float tickPeak = 0F;
            int read;
            while ((read = pcm.read(buffer, 0, buffer.length)) > 0)
            {
                // PCM frames are interleaved. All 16-bit channels contribute to the peak.
                for (int i = 0; i + 1 < read; i += 2)
                {
                    int sample = (short)((buffer[i] & 255) | (buffer[i + 1] << 8));
                    tickPeak = Math.max(tickPeak, Math.abs((float)sample) / 32768F);
                    tickBytes += 2;
                    if (tickBytes >= bytesPerTick)
                    {
                        values.add(tickPeak);
                        tickPeak = 0F;
                        tickBytes = 0;
                    }
                }
            }
            if (tickBytes > 0) values.add(tickPeak);
            peaks = new float[values.size()];
            for (int i = 0; i < values.size(); i++) peaks[i] = values.get(i);
        }
        catch (Exception ex)
        {
            peaks = new float[0];
            System.out.println("[BBS Animation Editor] WAV waveform: " + ex);
        }
        finally
        {
            try { if (pcm != null) pcm.close(); } catch (IOException ignored) {}
            try { if (source != null) source.close(); } catch (IOException ignored) {}
        }
    }

    public void draw(int width, int top, int left, EditorTimeline timeline, int currentFrame)
    {
        Gui.drawRect(0, top, width, top + HEIGHT, 0xFF102027);
        Gui.drawRect(0, top, width, top + 1, EditorThemeManager.get().getAccent());
        Gui.drawRect(left - 1, top + 1, left, top + HEIGHT, 0xFF304149);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        mc.fontRenderer.drawString("AUDIO", 12, top + 9,
                EditorThemeManager.get().getAccentBright());
        if (peaks.length == 0)
        {
            mc.fontRenderer.drawString("No waveform", left + 10, top + 21, 0xFF75818A);
            return;
        }
        int center = top + HEIGHT / 2;
        Gui.drawRect(left, center, width, center + 1, 0xFF304149);
        int accent = EditorThemeManager.get().getAccent();
        int bright = EditorThemeManager.get().getAccentBright();
        float ppf = timeline.getPixelsPerFrame();
        // At wide zoom aggregate all covered frames rather than skipping peaks.
        for (int x = left; x < width; x++)
        {
            float start = (x - left + timeline.getOffset()) / ppf;
            float end = (x + 1 - left + timeline.getOffset()) / ppf;
            int first = Math.max(0, (int)Math.floor(start));
            if (first >= peaks.length) continue;
            int last = Math.min(peaks.length - 1,
                    Math.max(first, (int)Math.ceil(end) - 1));
            float peak = 0F;
            for (int f = first; f <= last; f++) peak = Math.max(peak, peaks[f]);
            // Keep the waveform visually compact and strictly inside its own strip.
            int extent = Math.max(0, Math.min(MAX_HALF_AMPLITUDE,
                    Math.round(peak * MAX_HALF_AMPLITUDE)));
            extent = Math.min(extent, Math.min(center - top - 3,
                    top + HEIGHT - center - 4));
            if (extent > 0)
            {
                Gui.drawRect(x, center - extent, x + 1, center + extent + 1,
                        first <= currentFrame ? bright : accent);
            }
        }
        int cursorX = timeline.getFrameX(currentFrame, left);
        if (cursorX >= left && cursorX < width)
            Gui.drawRect(cursorX, top + 1, cursorX + 1, top + HEIGHT, bright);
    }
}

package com.example.examplemod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Offline Vosk bridge: an external process returns word times, Java makes visemes. */
public final class EditorVoskRecognizer
{
    public static final class Word
    {
        public final float start, end;
        public final String text;
        Word(float s, float e, String t) { start = s; end = e; text = t; }
    }

    private EditorVoskRecognizer() {}

    public static File modelFolder()
    {
        return new File(Minecraft.getMinecraft().mcDataDir,
                "config/bbs-editor/vosk_models/model-small-ru");
    }

    public static List<Word> recognize(File wav, String python) throws Exception
    {
        if (wav == null || !wav.isFile())
            throw new IOException("Load a WAV file first");
        File model = modelFolder();
        if (!new File(model, "am").isDirectory())
            throw new IOException("Vosk model missing: config/bbs-editor/vosk_models/model-small-ru");
        InputStream resource = EditorVoskRecognizer.class.getResourceAsStream(
                "/assets/bbsanimationeditor/vosk_recognize.py");
        if (resource == null) throw new IOException("Bundled recognizer script missing");
        File script = File.createTempFile("bbs-vosk-", ".py");
        try
        {
            try (InputStream in = resource; OutputStream out = new FileOutputStream(script))
            {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    python == null || python.trim().isEmpty() ? "python" : python.trim(),
                    script.getAbsolutePath(), wav.getAbsolutePath(), model.getAbsolutePath());
            // On Windows Python often uses cp1251 for redirected stdout.
            // Vosk word text must always arrive as UTF-8, not mojibake.
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            List<Word> words = new ArrayList<Word>();
            StringBuilder error = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    String[] parts = line.split("\\t", 3);
                    if (parts.length == 3)
                    {
                        try
                        {
                            float start = Float.parseFloat(parts[0]);
                            float end = Float.parseFloat(parts[1]);
                            if (!parts[2].trim().isEmpty() && end > start)
                                words.add(new Word(start, end, parts[2].trim()));
                            continue;
                        }
                        catch (NumberFormatException ignored) {}
                    }
                    if (error.length() < 1600) error.append(line).append(' ');
                }
            }
            int exit = process.waitFor();
            if (exit != 0)
                throw new IOException("Vosk failed: " + error.toString().trim());
            return words;
        }
        finally
        {
            if (!script.delete()) script.deleteOnExit();
        }
    }

    /**
     * Smooth pose timeline inspired by Wemppy's approach:
     * hold closed with two IDLE anchors in long pauses. Unlike Skin mode,
     * keep repeated IDLE keys because they control the interpolation interval.
     */
    public static List<LipSyncTimeline.Key> toSmoothBoneVisemes(List<Word> words, int offset)
    {
        TreeMap<Integer, LipSyncViseme> timeline = new TreeMap<Integer, LipSyncViseme>();
        int startOffset = Math.max(0, offset);
        timeline.put(startOffset, LipSyncViseme.IDLE);
        int previousEnd = startOffset;
        for (Word word : words)
        {
            int start = Math.max(startOffset, startOffset + Math.round(word.start * 20F));
            int end = Math.max(start + 1, startOffset + Math.round(word.end * 20F));
            // Two identical closed poses protect the middle of long silences.
            // The second anchor is held close to the next spoken word.
            if (start - previousEnd >= 5)
            {
                timeline.put(previousEnd + 2, LipSyncViseme.IDLE);
                timeline.put(start - 2, LipSyncViseme.IDLE);
            }
            String letters = word.text.toLowerCase(Locale.ROOT);
            float total = 0F;
            for (int i = 0; i < letters.length(); i++)
                if (LipSyncViseme.forLetter(letters.charAt(i)) != null)
                    total += LipSyncViseme.weight(letters.charAt(i));
            if (total <= 0F) continue;
            float cursor = start;
            for (int i = 0; i < letters.length(); i++)
            {
                char letter = letters.charAt(i);
                LipSyncViseme viseme = LipSyncViseme.forLetter(letter);
                if (viseme == null) continue;
                int frame = Math.min(end - 1, Math.round(cursor));
                timeline.put(frame, viseme);
                cursor += (end - start) * LipSyncViseme.weight(letter) / total;
            }
            previousEnd = end;
        }
        // Return to the closed pose shortly after the last spoken word.
        timeline.put(previousEnd + 2, LipSyncViseme.IDLE);
        ArrayList<LipSyncTimeline.Key> keys = new ArrayList<LipSyncTimeline.Key>();
        LipSyncViseme previous = null;
        for (Map.Entry<Integer, LipSyncViseme> entry : timeline.entrySet())
        {
            // Consecutive silence anchors must be kept, while repeated
            // speech shapes can be collapsed.
            if (entry.getValue() == LipSyncViseme.IDLE || entry.getValue() != previous)
                keys.add(new LipSyncTimeline.Key(entry.getKey(), entry.getValue()));
            previous = entry.getValue();
        }
        return keys;
    }

    public static List<LipSyncTimeline.Key> toVisemes(List<Word> words, int offset)
    {
        TreeMap<Integer, LipSyncViseme> timeline = new TreeMap<Integer, LipSyncViseme>();
        timeline.put(Math.max(0, offset), LipSyncViseme.IDLE);
        int previousEnd = Math.max(0, offset);
        for (Word word : words)
        {
            int start = Math.max(0, offset + Math.round(word.start * 20F));
            int end = Math.max(start + 1, offset + Math.round(word.end * 20F));
            if (start - previousEnd >= 3)
                timeline.put(previousEnd + 1, LipSyncViseme.IDLE);
            String text = word.text.toLowerCase(Locale.ROOT);
            float total = 0;
            for (int i = 0; i < text.length(); i++)
                if (LipSyncViseme.forLetter(text.charAt(i)) != null)
                    total += LipSyncViseme.weight(text.charAt(i));
            if (total == 0) continue;
            float cursor = start;
            for (int i = 0; i < text.length(); i++)
            {
                char letter = text.charAt(i);
                LipSyncViseme viseme = LipSyncViseme.forLetter(letter);
                if (viseme == null) continue;
                float duration = (end - start) * LipSyncViseme.weight(letter) / total;
                int frame = Math.min(end - 1, Math.round(cursor));
                timeline.put(frame, viseme);
                cursor += duration;
            }
            previousEnd = end;
        }
        timeline.put(previousEnd + 1, LipSyncViseme.IDLE);
        if (!words.isEmpty() && timeline.values().stream()
                .noneMatch(v -> v != LipSyncViseme.IDLE))
            throw new IllegalArgumentException(
                    "No mouth visemes found in recognized words; check Russian UTF-8 output");
        ArrayList<LipSyncTimeline.Key> result = new ArrayList<LipSyncTimeline.Key>();
        LipSyncViseme previous = null;
        for (Map.Entry<Integer, LipSyncViseme> e : timeline.entrySet())
        {
            if (e.getValue() != previous)
            {
                result.add(new LipSyncTimeline.Key(e.getKey(), e.getValue()));
                previous = e.getValue();
            }
        }
        return result;
    }
}

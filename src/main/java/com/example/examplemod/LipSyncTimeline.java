package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure text-to-viseme timing. No Minecraft, morph or renderer state.
 * Frame values are integer ticks (20 ticks per second in MC 1.12.2).
 */
public final class LipSyncTimeline
{
    public static final float TICKS_PER_SECOND = 20F;

    public static final class Key
    {
        public final int frame;
        public final LipSyncViseme viseme;

        public Key(int frame, LipSyncViseme viseme)
        {
            this.frame = frame;
            this.viseme = viseme;
        }
    }

    private LipSyncTimeline() {}

    /**
     * @param startFrame first frame of the phrase
     * @param wordsPerMinute tempo, 20..600
     * @return ordered, immutable keys including a final closed-mouth key
     */
    public static List<Key> fromText(String text, int startFrame, float wordsPerMinute)
    {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        if (!(wordsPerMinute >= 20F && wordsPerMinute <= 600F))
        {
            throw new IllegalArgumentException("Speech rate must be between 20 and 600 WPM");
        }

        String[] words = text.trim().split("\\s+");
        float totalWeight = 0F;

        for (String word : words)
        {
            for (int i = 0; i < word.length(); i++)
            {
                if (Character.isLetter(word.charAt(i)))
                    totalWeight += LipSyncViseme.weight(word.charAt(i));
            }
        }

        if (totalWeight <= 0F) return Collections.emptyList();

        float speechFrames = words.length * 60F / wordsPerMinute * TICKS_PER_SECOND;
        float framesPerWeight = speechFrames / totalWeight;
        float cursor = Math.max(0, startFrame) + 1.5F;
        List<Key> keys = new ArrayList<Key>();
        put(keys, Math.max(0, startFrame), LipSyncViseme.IDLE);

        for (String word : words)
        {
            int length = word.length();
            for (int i = 0; i < length; i++)
            {
                char letter = word.charAt(i);
                if (!Character.isLetter(letter)) continue;
                LipSyncViseme viseme = LipSyncViseme.forLetter(letter);
                float duration = LipSyncViseme.weight(letter) * framesPerWeight;
                if (viseme != null)
                {
                    put(keys, Math.round(cursor + duration * 0.5F), viseme);
                }
                cursor += duration;
            }

            char last = word.charAt(length - 1);
            if (".!?…".indexOf(last) >= 0)
            {
                cursor += 12F;
                put(keys, Math.round(cursor - 10F), LipSyncViseme.IDLE);
            }
            else if (",;:—–".indexOf(last) >= 0)
            {
                cursor += 6F;
            }
            else
            {
                cursor += 1.2F;
            }
        }

        put(keys, Math.round(cursor + 2F), LipSyncViseme.IDLE);
        return Collections.unmodifiableList(keys);
    }

    private static void put(List<Key> keys, int frame, LipSyncViseme shape)
    {
        if (!keys.isEmpty())
        {
            Key last = keys.get(keys.size() - 1);
            // No two mouth shapes may occupy the same whole-number frame.
            if (last.frame == frame)
            {
                keys.set(keys.size() - 1, new Key(frame, shape));
                return;
            }
            if (last.viseme == shape) return;
        }
        keys.add(new Key(frame, shape));
    }
}

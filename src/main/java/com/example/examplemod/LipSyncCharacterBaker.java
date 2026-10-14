package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes recognized mouth shapes as native Chameleon animation selections on
 * the Body Part's Character timeline. No internal-bone keys are generated.
 */
public final class LipSyncCharacterBaker
{
    private LipSyncCharacterBaker() {}

    private static String animationName(LipSyncViseme viseme)
    {
        if (viseme == LipSyncViseme.IDLE) return "closed";
        if (viseme == LipSyncViseme.EE) return "e";
        return viseme.name().toLowerCase(Locale.ROOT);
    }

    public static int generate(BodyPartModelData part, List<LipSyncTimeline.Key> sequence)
    {
        if (part == null || !part.hasMorphModel())
            throw new IllegalArgumentException("Select a Chameleon Mouth RIG Body Part");
        if (sequence == null || sequence.isEmpty())
            throw new IllegalArgumentException("No speech visemes to generate");

        List<String> available = ChameleonBodyPartAnimations.listAnimations(
                part, sequence.get(0).frame);
        if (available.isEmpty())
            throw new IllegalArgumentException("No Chameleon animations found for selected Body Part");

        Set<String> names = new HashSet<String>();
        for (String value : available) names.add(value.toLowerCase(Locale.ROOT));

        // Validate the entire operation before touching the Character timeline.
        Set<Integer> planned = new HashSet<Integer>();
        List<String> clips = new ArrayList<String>();
        for (LipSyncTimeline.Key step : sequence)
        {
            if (step == null || step.viseme == null)
                throw new IllegalArgumentException("Invalid speech viseme");
            if (step.frame < part.getStartFrame() || step.frame >= part.getEndFrame())
                throw new IllegalArgumentException("Speech exceeds Mouth RIG frame range");
            if (!ChameleonBodyPartAnimations.isChameleon(part, step.frame))
                throw new IllegalArgumentException("No Chameleon morph at frame " + step.frame);
            String clip = animationName(step.viseme);
            if (!names.contains(clip.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Missing Chameleon animation: " + clip);
            if (!planned.add(step.frame))
                throw new IllegalArgumentException("Duplicate speech frame: " + step.frame);
            for (CharacterTrack track : part.getCharacterTimeline().getTracks())
            {
                if (track == null) continue;
                CharacterKey existing = track.getKeyAtFrame(step.frame);
                if (existing != null && existing.hasData("ChameleonAnimation"))
                    throw new IllegalArgumentException(
                            "Manual Character animation key at frame " + step.frame);
            }
            clips.add(clip);
        }

        for (int i = 0; i < sequence.size(); i++)
        {
            CharacterKey key = part.getOrCreateCharacterStateKey(sequence.get(i).frame);
            ChameleonBodyPartAnimations.set(key, clips.get(i));
            ChameleonBodyPartAnimations.setPlaying(key, false);
        }
        return sequence.size();
    }
}

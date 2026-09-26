package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmoticonsPreviewAnimationState
{
    private static List<AnimationBoneSnapshot> snapshots =
            new ArrayList<AnimationBoneSnapshot>();

    private static int frame = 0;

    private EmoticonsPreviewAnimationState()
    {
    }

    public static void set(
            List<AnimationBoneSnapshot> newSnapshots,
            int newFrame)
    {
        List<AnimationBoneSnapshot> copy =
                new ArrayList<AnimationBoneSnapshot>();

        if (newSnapshots != null)
        {
            for (
                    AnimationBoneSnapshot snapshot :
                    newSnapshots
            )
            {
                if (snapshot != null)
                {
                    copy.add(snapshot);
                }
            }
        }

        snapshots = copy;
        frame = newFrame;
    }

    public static List<AnimationBoneSnapshot> getSnapshots()
    {
        return Collections.unmodifiableList(
                snapshots
        );
    }

    public static int getFrame()
    {
        return frame;
    }

    public static void clear()
    {
        snapshots =
                new ArrayList<AnimationBoneSnapshot>();

        frame = 0;
    }
}
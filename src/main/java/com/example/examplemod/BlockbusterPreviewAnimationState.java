package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockbusterPreviewAnimationState
{
    private static List<AnimationBoneSnapshot> snapshots =
            new ArrayList<AnimationBoneSnapshot>();

    private static int frame = 0;

    /*
     * Состояние воспроизведения.
     *
     * true  = Timeline играет
     * false = Timeline стоит на паузе
     *
     * Это состояние используется рендерером актёра,
     * чтобы правильно работать с partialTicks.
     */
    private static boolean playing = false;


    private BlockbusterPreviewAnimationState()
    {
    }


    /*
     * =========================================================
     * SET STATE
     * =========================================================
     */

    public static void set(
            List<AnimationBoneSnapshot> newSnapshots,
            int newFrame)
    {
        List<AnimationBoneSnapshot> copy =
                new ArrayList<AnimationBoneSnapshot>();

        if (newSnapshots != null)
        {
            for (AnimationBoneSnapshot snapshot :
                    newSnapshots)
            {
                if (snapshot != null)
                {
                    copy.add(snapshot);
                }
            }
        }

        snapshots = copy;

        frame =
                Math.max(
                        0,
                        newFrame
                );
    }


    /*
     * =========================================================
     * PLAYING
     * =========================================================
     */

    public static void setPlaying(
            boolean value)
    {
        playing = value;
    }


    public static boolean isPlaying()
    {
        return playing;
    }


    /*
     * =========================================================
     * SNAPSHOTS
     * =========================================================
     */

    public static List<AnimationBoneSnapshot> getSnapshots()
    {
        if (snapshots.isEmpty())
        {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
                snapshots
        );
    }


    /*
     * =========================================================
     * FRAME
     * =========================================================
     */

    public static int getFrame()
    {
        return frame;
    }


    /*
     * =========================================================
     * STATE CHECKS
     * =========================================================
     */

    public static boolean hasSnapshots()
    {
        return !snapshots.isEmpty();
    }


    public static int getSnapshotCount()
    {
        return snapshots.size();
    }


    /*
     * =========================================================
     * CLEAR
     * =========================================================
     */

    public static void clear()
    {
        snapshots =
                new ArrayList<AnimationBoneSnapshot>();

        frame = 0;

        /*
         * При очистке preview обязательно
         * возвращаемся в состояние паузы.
         */
        playing = false;
    }
}
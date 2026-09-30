package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Вычисляет состояние Character Timeline на текущем кадре.
 *
 * Сейчас отвечает за MORPH.
 *
 * Логика:
 *
 *  frame 0  -> исходный Morph Actor
 *  frame 20 -> Morph из последнего MORPH key <= 20
 *  frame 50 -> следующий MORPH key
 *
 * Если MORPH key после удаления больше не существует,
 * предыдущий Morph автоматически становится активным.
 */
public class CharacterStateResolver
{
    /*
     * =========================================================
     * MORPH STATE
     * =========================================================
     */

    private String lastAppliedMorphSignature = null;


    /*
     * =========================================================
     * APPLY
     * =========================================================
     */

    /**
     * Применить Character Timeline к Runtime Actor.
     */
    public void apply(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int currentFrame)
    {
        if (actorData == null || runtimeActor == null)
        {
            return;
        }

        int frame = Math.max(0, currentFrame);

        CharacterKey morphKey =
                findLatestMorphKey(
                        actorData,
                        frame
                );

        if (morphKey == null)
        {
            System.out.println(
                    "[BBS Character] frame=" + frame
                            + " | no MORPH key -> BASE MORPH"
            );

            applyBaseMorph(
                    actorData,
                    runtimeActor
            );

            return;
        }

        NBTTagCompound morphNBT =
                morphKey.getCompound("Morph");

        System.out.println(
                "[BBS Character] frame=" + frame
                        + " | MORPH key frame="
                        + morphKey.getFrame()
                        + " | NBT="
                        + morphNBT
        );

        if (morphNBT == null || morphNBT.hasNoTags())
        {
            System.out.println(
                    "[BBS Character] MORPH key has empty NBT"
            );

            applyBaseMorph(
                    actorData,
                    runtimeActor
            );

            return;
        }

        applyMorphNBT(
                runtimeActor,
                morphNBT
        );
    }


    /*
     * =========================================================
     * FIND MORPH KEY
     * =========================================================
     */

    /**
     * Найти последний MORPH key,
     * который уже наступил на Timeline.
     *
     * Дорожка не имеет значения.
     * Важен только самый поздний MORPH key.
     */
    private CharacterKey findLatestMorphKey(
            BlockbusterSceneActorData actorData,
            int currentFrame)
    {
        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return null;
        }

        CharacterKey result = null;

        for (
                CharacterTrack track :
                timeline.getTracks()
        )
        {
            if (track == null)
            {
                continue;
            }

            for (
                    CharacterKey key :
                    track.getKeys()
            )
            {
                if (key == null)
                {
                    continue;
                }

                if (key.getType()
                        != CharacterKey.Type.MORPH)
                {
                    continue;
                }

                if (key.getFrame() > currentFrame)
                {
                    continue;
                }

                if (
                        result == null
                                ||
                                key.getFrame() > result.getFrame()
                )
                {
                    result = key;
                }
            }
        }

        return result;
    }


    /*
     * =========================================================
     * BASE MORPH
     * =========================================================
     */

    /**
     * Вернуть Actor к Morph, который записан
     * непосредственно в Blockbuster Scene Actor.
     */
    private void applyBaseMorph(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor)
    {
        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }

        NBTTagCompound baseMorphNBT =
                sceneActor.getMorph();

        if (
                baseMorphNBT == null
                        ||
                        baseMorphNBT.hasNoTags()
        )
        {
            return;
        }

        applyMorphNBT(
                runtimeActor,
                baseMorphNBT
        );
    }


    /*
     * =========================================================
     * APPLY MORPH
     * =========================================================
     */

    /**
     * Создать Morph из NBT и установить его
     * через официальный Morph API EntityActor.
     */
    private void applyMorphNBT(
            EntityActor runtimeActor,
            NBTTagCompound morphNBT)
    {
        if (
                runtimeActor == null
                        ||
                        morphNBT == null
                        ||
                        morphNBT.hasNoTags()
        )
        {
            System.out.println(
                    "[BBS Character] applyMorphNBT: invalid input"
            );

            return;
        }

        try
        {
            System.out.println(
                    "[BBS Character] Creating Morph from NBT: "
                            + morphNBT
            );

            AbstractMorph morph =
                    MorphManager.INSTANCE
                            .morphFromNBT(
                                    morphNBT.copy()
                            );

            if (morph == null)
            {
                System.out.println(
                        "[BBS Character] morphFromNBT returned NULL"
                );

                return;
            }

            System.out.println(
                    "[BBS Character] Created Morph class: "
                            + morph.getClass().getName()
            );

            System.out.println(
                    "[BBS Character] Runtime EntityActor: "
                            + runtimeActor
            );

            System.out.println(
                    "[BBS Character] Setting runtimeActor.morph..."
            );

            runtimeActor.morph.set(morph);

            System.out.println(
                    "[BBS Character] Morph SET successfully"
            );
        }
        catch (Exception exception)
        {
            System.out.println(
                    "[BBS Character] FAILED TO APPLY MORPH"
            );

            exception.printStackTrace();
        }
    }


    /*
     * =========================================================
     * INTERNAL STATE
     * =========================================================
     */

    private String createSignature(
            NBTTagCompound morphNBT)
    {
        if (morphNBT == null)
        {
            return "";
        }

        return morphNBT.toString();
    }


    /**
     * Сбросить внутреннее состояние.
     *
     * Вызывается при смене Actor/Scene.
     */
    public void reset()
    {
        this.lastAppliedMorphSignature = null;
    }


    public String getLastAppliedMorphSignature()
    {
        return this.lastAppliedMorphSignature;
    }
}
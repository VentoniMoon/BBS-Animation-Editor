package com.example.examplemod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Применяет состояние Character Timeline к runtime EntityActor.
 *
 * BlockbusterSceneActor.morph является базовым состоянием актёра.
 *
 * Character Timeline MORPH key действует начиная со своего frame.
 *
 * Например:
 *
 * Base morph = Steve
 *
 * MORPH @ 20 = Zombie
 *
 * Тогда:
 *
 * 0..19  -> Steve
 * 20..   -> Zombie
 */
public class CharacterTimelineRuntimeController
{
    /*
     * ---------------------------------------------------------
     * BASE MORPHS
     * ---------------------------------------------------------
     *
     * Сохраняем исходный morph каждого Actor отдельно.
     *
     * Это состояние используется как fallback до первого
     * MORPH key в Character Timeline.
     */

    private final Map<String, NBTTagCompound> baseMorphs =
            new HashMap<String, NBTTagCompound>();

    /*
     * ---------------------------------------------------------
     * LAST APPLIED
     * ---------------------------------------------------------
     */

    private String lastActorId = "";

    private String lastMorphSignature = "";

    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        this.baseMorphs.clear();

        this.lastActorId = "";

        this.lastMorphSignature = "";
    }

    /*
     * =========================================================
     * ACTOR
     * =========================================================
     */

    public void resetActor()
    {
        this.lastActorId = "";

        this.lastMorphSignature = "";
    }

    /*
     * =========================================================
     * CAPTURE BASE MORPH
     * =========================================================
     */

    private void captureBaseMorph(
            BlockbusterSceneActorData actorData)
    {
        if (actorData == null)
        {
            return;
        }

        String actorId =
                actorData.getId();

        if (actorId == null)
        {
            actorId = "";
        }

        if (this.baseMorphs.containsKey(actorId))
        {
            return;
        }

        /*
         * Morph хранится в BlockbusterSceneActor,
         * а не в BlockbusterSceneActorData.
         */

        BlockbusterSceneActor actor =
                actorData.getActor();

        if (actor == null)
        {
            this.baseMorphs.put(
                    actorId,
                    new NBTTagCompound()
            );

            return;
        }

        NBTTagCompound morph =
                actor.getMorph();

        if (morph != null)
        {
            this.baseMorphs.put(
                    actorId,
                    morph.copy()
            );
        }
        else
        {
            this.baseMorphs.put(
                    actorId,
                    new NBTTagCompound()
            );
        }
    }

    /*
     * =========================================================
     * APPLY
     * =========================================================
     */

    public void apply(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor,
            int currentFrame)
    {
        if (actorData == null ||
                runtimeActor == null)
        {
            return;
        }

        captureBaseMorph(actorData);

        String actorId =
                actorData.getId();

        if (actorId == null)
        {
            actorId = "";
        }

        if (!actorId.equals(this.lastActorId))
        {
            this.lastActorId = actorId;

            this.lastMorphSignature = "";
        }

        NBTTagCompound resolvedMorph =
                resolveMorph(
                        actorData,
                        currentFrame
                );

        if (resolvedMorph == null)
        {
            return;
        }

        String signature =
                resolvedMorph.toString();

        /*
         * Не пересоздаём Morph каждый tick.
         *
         * Это особенно важно для паузы:
         * постоянный setDirect() может сбрасывать внутреннее
         * состояние Morph и давать визуальное дёрганье.
         */

        if (signature.equals(this.lastMorphSignature))
        {
            return;
        }

        AbstractMorph morph;

        try
        {
            morph =
                    MorphManager.INSTANCE.morphFromNBT(
                            resolvedMorph.copy()
                    );
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return;
        }

        if (morph == null)
        {
            return;
        }

        /*
         * EntityActor из Blockbuster 1.12 имеет публичный
         * runtime Morph.
         */

        try
        {
            runtimeActor.morph.setDirect(morph);

            this.lastMorphSignature =
                    signature;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }
    }

    /*
     * =========================================================
     * RESOLVE MORPH
     * =========================================================
     */

    private NBTTagCompound resolveMorph(
            BlockbusterSceneActorData actorData,
            int currentFrame)
    {
        String actorId =
                actorData.getId();

        if (actorId == null)
        {
            actorId = "";
        }

        NBTTagCompound baseMorph =
                this.baseMorphs.get(actorId);

        /*
         * Fallback, если базовый Morph ещё не был сохранён.
         */

        if (baseMorph == null)
        {
            BlockbusterSceneActor actor =
                    actorData.getActor();

            if (actor != null)
            {
                baseMorph =
                        actor.getMorph();
            }

            if (baseMorph == null)
            {
                baseMorph =
                        new NBTTagCompound();
            }
        }

        NBTTagCompound result =
                baseMorph.copy();

        int bestFrame = -1;

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return result;
        }

        List<CharacterTrack> tracks =
                timeline.getTracks();

        if (tracks == null)
        {
            return result;
        }

        /*
         * Ищем последний MORPH key, который уже наступил.
         *
         * Поэтому:
         *
         * MORPH @ 40
         *
         * не влияет на кадры:
         *
         * 0..39
         */

        for (CharacterTrack track : tracks)
        {
            if (track == null)
            {
                continue;
            }

            List<CharacterKey> keys =
                    track.getKeys();

            if (keys == null)
            {
                continue;
            }

            for (CharacterKey key : keys)
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

                int keyFrame =
                        key.getFrame();

                if (keyFrame > currentFrame)
                {
                    continue;
                }

                if (keyFrame < bestFrame)
                {
                    continue;
                }

                NBTTagCompound data =
                        key.getData();

                if (data == null ||
                        !data.hasKey("Morph", 10))
                {
                    continue;
                }

                NBTTagCompound morph =
                        data.getCompoundTag("Morph");

                if (morph == null)
                {
                    continue;
                }

                /*
                 * Если несколько MORPH key находятся на одном
                 * кадре в разных дорожках, последний найденный
                 * получает приоритет.
                 */

                result =
                        morph.copy();

                bestFrame =
                        keyFrame;
            }
        }

        return result;
    }
}
package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Единственный runtime resolver Character Mode.
 *
 * Character Timeline
 *        ↓
 * CharacterKey
 *        ↓
 * состояние Character
 *        ↓
 * EntityActor
 *
 * CharacterKey является источником истины.
 *
 * Runtime EntityActor НЕ является хранилищем
 * Character Timeline.
 *
 * ---------------------------------------------------------
 *
 * ВАЖНО:
 *
 * CharacterKey теперь является универсальным контейнером
 * изменений состояния.
 *
 * Например:
 *
 * Frame 0:
 *
 *     Morph = Zombie
 *     Skin  = Default
 *
 * Frame 40:
 *
 *     Bones = ...
 *
 * При разрешении состояния отсутствующие поля НЕ сбрасывают
 * предыдущие значения.
 *
 * Этот resolver пока занимается runtime-состоянием
 * внешнего вида:
 *
 *     Morph
 *     Skin
 *
 * Body/Bones и другие Character-поля обрабатываются
 * отдельными системами.
 *
 * Type CharacterKey намеренно НЕ используется для определения
 * наличия Morph/Skin.
 *
 * Это позволяет одному CharacterKey содержать одновременно:
 *
 *     Morph
 *     Skin
 *     Animation
 *     Action
 *     Bones
 *
 * без потери данных.
 */
public class CharacterStateResolver
{
    /*
     * Последний применённый Morph.
     *
     * Morph и Skin хранят полноценный Morph NBT,
     * поэтому runtime использует единый signature.
     */
    private String lastMorphSignature;

    private String lastActorId;


    public CharacterStateResolver()
    {
        reset();
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


        String actorId =
                actorData.getId();

        if (actorId == null)
        {
            actorId = "";
        }


        /*
         * При переключении Actor старое состояние
         * больше не считается применённым.
         */
        if (!actorId.equals(this.lastActorId))
        {
            this.lastActorId =
                    actorId;

            this.lastMorphSignature =
                    null;
        }


        /*
         * =====================================================
         * 1. ПОСЛЕДНИЙ MORPH
         * =====================================================
         *
         * ВАЖНО:
         *
         * Больше не ищем:
         *
         *     Type.MORPH
         *
         * потому что один универсальный CharacterKey
         * может одновременно содержать Morph + Skin.
         *
         * Ищем именно ключ, содержащий поле Morph.
         */

        CharacterKey morphKey =
                findLatestMorphKey(
                        actorData,
                        currentFrame
                );


        /*
         * =====================================================
         * 2. ПОСЛЕДНИЙ SKIN
         * =====================================================
         *
         * Аналогично Morph.
         *
         * Ищем фактическое поле Skin внутри ключа,
         * а не Type.SKIN.
         */

        CharacterKey skinKey =
                findLatestSkinKey(
                        actorData,
                        currentFrame
                );


        /*
         * =====================================================
         * 3. ВЫБОР ПОСЛЕДНЕГО ИЗМЕНЕНИЯ ВНЕШНЕГО ВИДА
         * =====================================================
         *
         * Пример:
         *
         * Frame 0:
         *     Morph = Zombie
         *
         * Frame 20:
         *     Skin = Default
         *
         * Frame 30:
         *
         *     применяется Skin.
         *
         *
         * Frame 40:
         *     Morph = Creeper
         *
         * Frame 50:
         *
         *     применяется Creeper.
         *
         *
         * Если Morph и Skin находятся в одном универсальном
         * ключе, Skin считается более поздним изменением.
         *
         * Это сохраняет старое поведение resolver:
         * при одинаковом frame Skin имел приоритет над Morph.
         */

        CharacterKey latestKey =
                getLatestAppearanceKey(
                        morphKey,
                        skinKey
                );


        if (latestKey != null)
        {
            NBTTagCompound appearanceNBT =
                    getAppearanceNBT(
                            latestKey,
                            morphKey,
                            skinKey
                    );


            if (appearanceNBT != null &&
                    !appearanceNBT.hasNoTags())
            {
                applyMorph(
                        runtimeActor,
                        appearanceNBT
                );

                return;
            }
        }


        /*
         * -----------------------------------------------------
         * BASE MORPH
         * -----------------------------------------------------
         *
         * До первого Morph/Skin изменения используется
         * исходный Morph Scene Actor.
         *
         * Важно:
         *
         * Наличие, например, Bones в CharacterKey
         * само по себе НЕ считается изменением Morph.
         *
         * Поэтому базовый Morph продолжает работать.
         */

        applyBaseMorph(
                actorData,
                runtimeActor
        );
    }


    /*
     * =========================================================
     * GET LATEST APPEARANCE KEY
     * =========================================================
     */

    private CharacterKey getLatestAppearanceKey(
            CharacterKey morphKey,
            CharacterKey skinKey)
    {
        if (morphKey == null)
        {
            return skinKey;
        }


        if (skinKey == null)
        {
            return morphKey;
        }


        /*
         * Skin был изменён позже Morph.
         */
        if (skinKey.getFrame() >
                morphKey.getFrame())
        {
            return skinKey;
        }


        /*
         * Morph был изменён позже Skin.
         */
        if (morphKey.getFrame() >
                skinKey.getFrame())
        {
            return morphKey;
        }


        /*
         * Оба изменения находятся на одном кадре.
         *
         * Если один универсальный CharacterKey содержит
         * одновременно Morph и Skin, skinKey и morphKey
         * будут ссылаться на один и тот же объект.
         *
         * Skin сохраняет старый приоритет на одном кадре.
         */
        return skinKey;
    }


    /*
     * =========================================================
     * GET APPEARANCE NBT
     * =========================================================
     *
     * Возвращает фактически хранящееся изменение.
     *
     * Type здесь НЕ используется.
     *
     * Это принципиально важно для универсального
     * CharacterKey.
     */

    private NBTTagCompound getAppearanceNBT(
            CharacterKey latestKey,
            CharacterKey morphKey,
            CharacterKey skinKey)
    {
        if (latestKey == null)
        {
            return null;
        }


        /*
         * Если последним изменением является Skin,
         * используем Skin.
         */
        if (skinKey != null &&
                latestKey == skinKey &&
                skinKey.hasSkin())
        {
            return skinKey.getSkin();
        }


        /*
         * Если последним изменением является Morph,
         * используем Morph.
         */
        if (morphKey != null &&
                latestKey == morphKey &&
                morphKey.hasMorph())
        {
            return morphKey.getMorph();
        }


        /*
         * -----------------------------------------------------
         * Защитный fallback
         * -----------------------------------------------------
         *
         * Нужен для ситуации, когда данные были загружены
         * из старого/нестандартного .dat и ссылки на
         * latestKey не позволяют однозначно определить
         * источник.
         */

        if (latestKey.hasSkin())
        {
            return latestKey.getSkin();
        }


        if (latestKey.hasMorph())
        {
            return latestKey.getMorph();
        }


        return null;
    }


    /*
     * =========================================================
     * FIND LATEST MORPH KEY
     * =========================================================
     */

    private CharacterKey findLatestMorphKey(
            BlockbusterSceneActorData actorData,
            int currentFrame)
    {
        if (actorData == null)
        {
            return null;
        }


        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return null;
        }


        CharacterKey result =
                null;


        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }


            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null)
                {
                    continue;
                }


                /*
                 * -------------------------------------------------
                 * ВАЖНО:
                 *
                 * Не проверяем:
                 *
                 *     key.getType() == MORPH
                 *
                 * потому что Morph теперь может находиться
                 * внутри универсального CUSTOM key.
                 * -------------------------------------------------
                 */

                if (!key.hasMorph())
                {
                    continue;
                }


                if (key.getFrame() > currentFrame)
                {
                    continue;
                }


                if (result == null ||
                        key.getFrame() >
                                result.getFrame())
                {
                    result =
                            key;
                }


                /*
                 * Если на одном кадре несколько track'ов
                 * содержат Morph, сохраняем существующее
                 * поведение: первый найденный ключ остаётся.
                 *
                 * Timeline обычно гарантирует один CharacterKey
                 * на frame в рамках track.
                 */
            }
        }


        return result;
    }


    /*
     * =========================================================
     * FIND LATEST SKIN KEY
     * =========================================================
     */

    private CharacterKey findLatestSkinKey(
            BlockbusterSceneActorData actorData,
            int currentFrame)
    {
        if (actorData == null)
        {
            return null;
        }


        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return null;
        }


        CharacterKey result =
                null;


        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }


            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null)
                {
                    continue;
                }


                /*
                 * Ищем фактические Skin-данные,
                 * а не Type.SKIN.
                 */
                if (!key.hasSkin())
                {
                    continue;
                }


                if (key.getFrame() > currentFrame)
                {
                    continue;
                }


                if (result == null ||
                        key.getFrame() >
                                result.getFrame())
                {
                    result =
                            key;
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

    private void applyBaseMorph(
            BlockbusterSceneActorData actorData,
            EntityActor runtimeActor)
    {
        if (actorData == null ||
                runtimeActor == null)
        {
            return;
        }


        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }


        NBTTagCompound morph =
                sceneActor.getMorph();


        if (morph == null ||
                morph.hasNoTags())
        {
            return;
        }


        applyMorph(
                runtimeActor,
                morph
        );
    }


    /*
     * =========================================================
     * APPLY MORPH / SKIN
     * =========================================================
     */

    private void applyMorph(
            EntityActor runtimeActor,
            NBTTagCompound morphNBT)
    {
        if (runtimeActor == null ||
                morphNBT == null ||
                morphNBT.hasNoTags())
        {
            return;
        }


        String signature =
                morphNBT.toString();


        /*
         * Уже применён.
         *
         * НЕ пересоздаём Morph каждый tick.
         */
        if (signature.equals(
                this.lastMorphSignature))
        {
            return;
        }


        try
        {
            AbstractMorph morph =
                    MorphManager.INSTANCE
                            .morphFromNBT(
                                    morphNBT.copy()
                            );


            if (morph == null)
            {
                return;
            }


            /*
             * Применяем новый runtime Morph.
             *
             * Это одинаково работает для:
             *
             * - MORPH
             * - SKIN
             * - CustomMorph
             * - AnimatedMorph
             */
            runtimeActor.morph.setDirect(
                    morph
            );


            /*
             * AnimatedMorph требует повторной
             * подготовки animator.
             */
            if (morph instanceof
                    mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
            {
                EmoticonsActorPreviewRenderer.prepareMorph(
                        (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                                morph
                );
            }


            this.lastMorphSignature =
                    signature;
        }
        catch (Throwable error)
        {
            error.printStackTrace();
        }
    }


    /*
     * =========================================================
     * REFRESH
     * =========================================================
     */

    /**
     * Заставляет следующий apply()
     * повторно применить текущее состояние.
     */
    public void invalidate()
    {
        this.lastMorphSignature =
                null;
    }


    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        this.lastMorphSignature =
                null;

        this.lastActorId =
                "";
    }


    /*
     * =========================================================
     * DEBUG / STATE
     * =========================================================
     */

    public String getLastAppliedMorphSignature()
    {
        return this.lastMorphSignature;
    }
}
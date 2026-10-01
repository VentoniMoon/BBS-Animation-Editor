package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;


/**
 * Вычисляет Character Timeline состояние Actor.
 *
 * Отдельные состояния:
 *
 * MORPH:
 *      CharacterKey
 *          Data
 *              Morph
 *
 * SKIN:
 *      CharacterKey
 *          Data
 *              Skin
 *
 *
 * Каждый тип имеет свою независимую шкалу времени.
 *
 * Пример:
 *
 * Frame 0:
 *      Morph = Steve
 *
 * Frame 47:
 *      Morph = Slim
 *
 * Frame 100:
 *      Morph = Zombie
 *
 *
 * Morph применяется только начиная
 * с собственного ключа.
 */
public class CharacterStateResolver
{
    private String lastAppliedMorphSignature;

    private String lastAppliedSkinSignature;



    public CharacterStateResolver()
    {
        this.lastAppliedMorphSignature = null;

        this.lastAppliedSkinSignature = null;
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



        /*
         * MORPH и SKIN ищутся отдельно.
         */
        CharacterKey morphKey =
                findLatestCharacterKey(
                        actorData,
                        CharacterKey.Type.MORPH,
                        currentFrame
                );


        CharacterKey skinKey =
                findLatestCharacterKey(
                        actorData,
                        CharacterKey.Type.SKIN,
                        currentFrame
                );



        /*
         * -------------------------
         * MORPH
         * -------------------------
         */

        if (morphKey != null)
        {
            NBTTagCompound morphNBT =
                    morphKey.getCompound(
                            "Morph"
                    );


            applyMorphNBT(
                    runtimeActor,
                    morphNBT
            );
        }
        else
        {
            applyBaseMorph(
                    actorData,
                    runtimeActor
            );
        }



        /*
         * -------------------------
         * SKIN
         * -------------------------
         *
         * Сейчас Skin хранится
         * отдельно от Morph.
         *
         * Здесь оставляем место
         * для Skin resolver.
         */
        if (skinKey != null)
        {
            NBTTagCompound skinNBT =
                    skinKey.getCompound(
                            "Skin"
                    );


            applySkinNBT(
                    runtimeActor,
                    skinNBT
            );
        }
    }



    /*
     * =========================================================
     * FIND KEY
     * =========================================================
     */

    private CharacterKey findLatestCharacterKey(
            BlockbusterSceneActorData actorData,
            CharacterKey.Type wantedType,
            int frame)
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



        for(CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }


            for(CharacterKey key :
                    track.getKeys())
            {
                if (key == null)
                {
                    continue;
                }



                /*
                 * ВАЖНО:
                 *
                 * Morph ищет только Morph.
                 * Skin ищет только Skin.
                 */
                if (key.getType() != wantedType)
                {
                    continue;
                }



                /*
                 * Будущие ключи
                 * не учитываются.
                 */
                if (key.getFrame() > frame)
                {
                    continue;
                }



                if (result == null ||
                        key.getFrame() >
                                result.getFrame())
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


        NBTTagCompound base =
                sceneActor.getMorph();


        if (base == null ||
                base.hasNoTags())
        {
            return;
        }


        applyMorphNBT(
                runtimeActor,
                base
        );
    }



    /*
     * =========================================================
     * APPLY MORPH
     * =========================================================
     */

    private void applyMorphNBT(
            EntityActor runtimeActor,
            NBTTagCompound nbt)
    {
        if (runtimeActor == null ||
                nbt == null ||
                nbt.hasNoTags())
        {
            return;
        }


        try
        {
            String signature =
                    nbt.toString();



            if (signature.equals(
                    this.lastAppliedMorphSignature))
            {
                return;
            }



            AbstractMorph morph =
                    MorphManager.INSTANCE
                            .morphFromNBT(
                                    nbt.copy()
                            );



            if (morph == null)
            {
                return;
            }



            /*
             * ВАЖНО:
             *
             * Всегда создаём новый Morph.
             * Старый AnimatedMorph содержит
             * старый AnimatorController.
             */
            runtimeActor.morph.set(
                    morph
            );



            /*
             * Восстанавливаем Emoticons animator
             */
            if (morph instanceof
                    mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
            {
                EmoticonsActorPreviewRenderer.prepareMorph(
                        (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)morph
                );
            }



            this.lastAppliedMorphSignature =
                    signature;


            System.out.println(
                    "[BBS Animation Editor] Applied Morph "
                            +
                            morph.getClass().getName()
                            +
                            " frame updated"
            );

        }
        catch(Throwable error)
        {
            error.printStackTrace();
        }
    }



    /*
     * =========================================================
     * APPLY SKIN
     * =========================================================
     *
     * Пока Skin хранится отдельно.
     *
     * Метод оставлен специально,
     * чтобы Skin не попадал в Morph.
     */

    private void applySkinNBT(
            EntityActor runtimeActor,
            NBTTagCompound nbt)
    {
        if (runtimeActor == null ||
                nbt == null ||
                nbt.hasNoTags())
        {
            return;
        }



        String signature =
                nbt.toString();



        if (signature.equals(
                this.lastAppliedSkinSignature))
        {
            return;
        }



        /*
         * Здесь позже подключим
         * полноценное применение Skin.
         *
         * ВАЖНО:
         *
         * Skin НЕ должен вызывать
         * runtimeActor.morph.set()
         *
         * иначе снова будет
         * глобальная замена.
         */



        this.lastAppliedSkinSignature =
                signature;
    }



    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        this.lastAppliedMorphSignature = null;

        this.lastAppliedSkinSignature = null;
    }



    public String getLastAppliedMorphSignature()
    {
        return this.lastAppliedMorphSignature;
    }



    public String getLastAppliedSkinSignature()
    {
        return this.lastAppliedSkinSignature;
    }
}
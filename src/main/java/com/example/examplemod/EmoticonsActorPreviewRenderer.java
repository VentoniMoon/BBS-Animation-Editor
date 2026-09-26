package com.example.examplemod;

import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorController;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.entity.EntityLivingBase;

public class EmoticonsActorPreviewRenderer
{
    private EmoticonsActorPreviewRenderer()
    {
    }

    public static boolean isSupported(
            Object morph)
    {
        return morph instanceof AnimatedMorph;
    }

    public static void prepareMorph(
            AnimatedMorph morph)
    {
        if (morph == null)
        {
            return;
        }

        /*
         * AnimatedMorph лениво создаёт
         * стандартный AnimatorMorphController.
         */
        morph.initiateAnimator();

        AnimatorController current =
                morph.animator;

        /*
         * Если наш контроллер уже установлен,
         * повторно создавать его не нужно.
         */
        if (
                current instanceof
                        EditorAnimatorMorphController
        )
        {
            EditorAnimatorMorphController editorController =
                    (EditorAnimatorMorphController) current;

            editorController.morph =
                    morph;

            return;
        }

        /*
         * Создаём контроллер редактора,
         * наследующий штатный контроллер Emoticons.
         */
        EditorAnimatorMorphController controller =
                new EditorAnimatorMorphController(
                        morph.animationName,
                        morph.userConfigData,
                        morph
                );

        /*
         * Подменяем стандартный контроллер.
         */
        morph.animator =
                controller;

        /*
         * Загружаем стандартную Animation.
         */
        controller.fetchAnimation();
    }

    /**
     * Обновляет штатный Animator Emoticons.
     *
     * Это важно делать ДО render().
     *
     * Именно внутри AnimatedMorph.update()
     * вызывается:
     *
     * AnimatedMorph.updateAnimator()
     *        ↓
     * AnimatorController.update()
     *        ↓
     * IAnimator.update()
     *        ↓
     * Animator.update()
     *        ↓
     * ActionPlayback.update()
     *
     * Благодаря этому начинают продвигаться
     * idle / walking / running и остальные
     * стандартные анимации Emoticons.
     */
    public static void updateMorph(
            AnimatedMorph morph,
            EntityLivingBase entity)
    {
        if (morph == null || entity == null)
        {
            return;
        }

        /*
         * Убеждаемся, что AnimatorController
         * уже подготовлен.
         */
        prepareMorph(morph);

        /*
         * Запускаем штатный update-cycle
         * AnimatedMorph.
         */
        morph.update(entity);
    }
}
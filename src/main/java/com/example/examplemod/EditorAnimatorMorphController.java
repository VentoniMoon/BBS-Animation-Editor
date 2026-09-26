package com.example.examplemod;

import java.util.List;

import mchorse.emoticons.skin_n_bones.api.animation.Animation;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatorMorphController;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;

public class EditorAnimatorMorphController
        extends AnimatorMorphController
{
    private int debugCalls = 0;

    private int lastDebugFrame = -1;


    public EditorAnimatorMorphController(
            String animationName,
            NBTTagCompound userData,
            AnimatedMorph morph)
    {
        super(
                animationName,
                userData,
                morph
        );
    }


    @Override
    protected void setupBoneTransformations(
            EntityLivingBase entity,
            BOBJArmature armature,
            float yaw,
            float partialTicks)
    {
        /*
         * =====================================================
         * 1. СНАЧАЛА EMOTICONS
         * =====================================================
         *
         * Это принципиально важно.
         *
         * AnimatorMorphController должен сначала применить
         * собственную стандартную анимацию Emoticons.
         *
         * Только после этого мы накладываем BBS keyframes.
         */
        super.setupBoneTransformations(
                entity,
                armature,
                yaw,
                partialTicks
        );


        /*
         * =====================================================
         * 2. BBS SNAPSHOTS
         * =====================================================
         */

        List<AnimationBoneSnapshot> snapshots =
                EmoticonsPreviewAnimationState
                        .getSnapshots();

        if (
                snapshots == null ||
                        snapshots.isEmpty()
        )
        {
            return;
        }


        int frame =
                EmoticonsPreviewAnimationState
                        .getFrame();


        /*
         * =====================================================
         * 3. ДИАГНОСТИКА CONTROLLER
         * =====================================================
         *
         * Проверяем:
         *
         * - какая animation загружена;
         * - какое animationName;
         * - есть ли animation;
         * - какой кадр Timeline сейчас активен.
         */

        if (
                frame != this.lastDebugFrame
                        &&
                        this.debugCalls < 100
        )
        {
            this.lastDebugFrame =
                    frame;

            this.debugCalls++;


            Animation animation =
                    this.animation;


            System.out.println(
                    "[BBS Animation Editor] "
                            + "EMOTICONS CONTROLLER: "
                            + "frame="
                            + frame
                            + " animationName="
                            + this.animationName
                            + " animation="
                            + (
                            animation == null
                                    ? "NULL"
                                    : animation.getClass()
                                    .getSimpleName()
                    )
            );


            /*
             * Показываем наличие самой animation.
             */

            if (animation != null)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Emoticons animation "
                                + "IS LOADED"
                );
            }
            else
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Emoticons animation "
                                + "IS NULL"
                );
            }
        }


        /*
         * =====================================================
         * 4. НАКЛАДЫВАЕМ BBS KEYFRAMES
         * =====================================================
         *
         * Standard Emoticons animation уже применена
         * через super.setupBoneTransformations().
         *
         * Теперь добавляем сверху пользовательскую
         * трансформацию BBS Animation Editor.
         */

        for (
                AnimationBoneSnapshot snapshot :
                snapshots
        )
        {
            if (snapshot == null)
            {
                continue;
            }


            String name =
                    snapshot.getName();

            if (
                    name == null ||
                            name.isEmpty()
            )
            {
                continue;
            }


            BOBJBone bone =
                    armature.bones.get(
                            name
                    );

            if (bone == null)
            {
                continue;
            }


            applySnapshot(
                    bone,
                    snapshot
            );
        }
    }


    private void applySnapshot(
            BOBJBone bone,
            AnimationBoneSnapshot snapshot)
    {
        /*
         * Position
         */

        bone.x +=
                snapshot.getPositionX();

        bone.y +=
                snapshot.getPositionY();

        bone.z +=
                snapshot.getPositionZ();


        /*
         * Rotation
         *
         * AnimationBone stores degrees.
         *
         * BOBJ uses radians.
         */

        bone.rotateX +=
                (float) Math.toRadians(
                        snapshot.getRotationX()
                );

        bone.rotateY +=
                (float) Math.toRadians(
                        snapshot.getRotationY()
                );

        bone.rotateZ +=
                (float) Math.toRadians(
                        snapshot.getRotationZ()
                );


        /*
         * Scale
         */

        bone.scaleX *=
                snapshot.getScaleX();

        bone.scaleY *=
                snapshot.getScaleY();

        bone.scaleZ *=
                snapshot.getScaleZ();
    }
}
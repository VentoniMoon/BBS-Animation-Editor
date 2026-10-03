package com.example.examplemod;

import java.util.List;

import mchorse.emoticons.skin_n_bones.api.animation.Animation;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorPoseTransform;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedPose;
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
         * 1. TEMPORARY BODY PART OVERRIDES
         * =====================================================
         *
         * Не меняем реальный Morph навсегда.
         *
         * Создаём временную копию pose и меняем только
         * fixed у костей, для которых Character Timeline
         * имеет explicit ENABLE/DISABLE.
         *
         * Затем AnimatorMorphController получает именно
         * этот временный pose и штатно применяет его.
         */
        AnimatedPose originalPose =
                this.morph.pose;

        AnimatedPose temporaryPose =
                originalPose == null
                        ? null
                        : originalPose.clone();

        int frame =
                CharacterBodyPartPreviewState.getFrame();

        BlockbusterSceneActorData actorData =
                CharacterBodyPartPreviewState.getActorData();

        if (temporaryPose == null &&
                hasExplicitBodyPartOverrides(
                        actorData,
                        frame,
                        armature
                ))
        {
            temporaryPose =
                    new AnimatedPose();
        }

        boolean changed =
                applyBodyPartOverrides(
                        temporaryPose,
                        actorData,
                        frame,
                        armature
                );

        if (changed)
        {
            this.morph.pose =
                    temporaryPose;
        }

        try
        {
            /*
             * =================================================
             * 2. EMOTICONS
             * =================================================
             */
            super.setupBoneTransformations(
                    entity,
                    armature,
                    yaw,
                    partialTicks
            );
        }
        finally
        {
            /*
             * Очень важно:
             *
             * Character Body Parts не должны мутировать
             * сам AnimatedMorph.
             */
            this.morph.pose =
                    originalPose;
        }


        /*
         * =====================================================
         * 3. BBS SNAPSHOTS
         * =====================================================
         *
         * BBS keyframes идут ПОСЛЕ стандартной Emoticons
         * анимации. Поэтому отключение Body Part не мешает
         * пользовательской BBS трансформации.
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


        /*
         * =====================================================
         * 4. ДИАГНОСТИКА CONTROLLER
         * =====================================================
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
         * 5. BBS KEYFRAMES
         * =====================================================
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


    /**
     * Накладывает explicit Character Body Part state
     * на временный AnimatedPose.
     */
    private boolean applyBodyPartOverrides(
            AnimatedPose pose,
            BlockbusterSceneActorData actorData,
            int frame,
            BOBJArmature armature)
    {
        if (actorData == null ||
                armature == null)
        {
            return false;
        }

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return false;
        }

        if (!hasExplicitBodyPartOverrides(
                actorData,
                frame,
                armature
        ))
        {
            return false;
        }

        if (pose == null)
        {
            return false;
        }

        for (String boneName :
                armature.bones.keySet())
        {
            int state =
                    CharacterBodyPartOverrideController
                            .getEffectiveKeyState(
                                    actorData,
                                    frame,
                                    boneName
                            );

            if (state !=
                    CharacterBodyPartOverrideController.STATE_ENABLE
                    &&
                    state !=
                            CharacterBodyPartOverrideController.STATE_DISABLE)
            {
                continue;
            }

            AnimatorPoseTransform transform =
                    pose.bones.get(
                            boneName
                    );

            if (transform == null)
            {
                transform =
                        new AnimatorPoseTransform(
                                boneName
                        );

                pose.bones.put(
                        boneName,
                        transform
                );
            }

            transform.fixed =
                    state ==
                            CharacterBodyPartOverrideController.STATE_DISABLE
                            ? AnimatorPoseTransform.FIXED
                            : AnimatorPoseTransform.ANIMATED;
        }

        return true;
    }


    private boolean hasExplicitBodyPartOverrides(
            BlockbusterSceneActorData actorData,
            int frame,
            BOBJArmature armature)
    {
        if (actorData == null ||
                armature == null)
        {
            return false;
        }

        for (String boneName :
                armature.bones.keySet())
        {
            int state =
                    CharacterBodyPartOverrideController
                            .getEffectiveKeyState(
                                    actorData,
                                    frame,
                                    boneName
                            );

            if (state ==
                    CharacterBodyPartOverrideController.STATE_ENABLE
                    ||
                    state ==
                    CharacterBodyPartOverrideController.STATE_DISABLE)
            {
                return true;
            }
        }

        return false;
    }


    private void applySnapshot(
            BOBJBone bone,
            AnimationBoneSnapshot snapshot)
    {
        bone.x +=
                snapshot.getPositionX();

        bone.y +=
                snapshot.getPositionY();

        bone.z +=
                snapshot.getPositionZ();


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


        bone.scaleX *=
                snapshot.getScaleX();

        bone.scaleY *=
                snapshot.getScaleY();

        bone.scaleZ *=
                snapshot.getScaleZ();
    }
}

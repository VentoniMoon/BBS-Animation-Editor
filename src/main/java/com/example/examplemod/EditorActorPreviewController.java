package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Отвечает за Actor/Preview часть редактора.
 *
 * Здесь находятся:
 * - AnimationAdapterManager
 * - BlockbusterModelAccess
 * - AnimationBone список
 * - TransformPanel
 * - AnimationPreview
 * - создание/выбор ActorAnimationData
 * - применение AnimationBone к адаптерам
 */
public class EditorActorPreviewController
{
    private final AnimationAdapterManager adapterManager;

    private final BlockbusterModelAccess blockbusterModelAccess;

    private final TransformPanel transformPanel;

    private final AnimationPreview animationPreview;

    private List<AnimationBone> bones =
            new ArrayList<AnimationBone>();


    public EditorActorPreviewController()
    {
        this.adapterManager =
                new AnimationAdapterManager();

        this.blockbusterModelAccess =
                new BlockbusterModelAccess(null);

        this.transformPanel =
                new TransformPanel();

        this.animationPreview =
                new AnimationPreview();
    }


    public void initializeAdapters()
    {
        BlockbusterAnimationAdapter blockbusterAdapter =
                new BlockbusterAnimationAdapter(
                        this.blockbusterModelAccess
                );

        this.adapterManager.register(
                blockbusterAdapter
        );

        EmoticonsAnimationAdapter emoticonsAdapter =
                new EmoticonsAnimationAdapter();

        this.adapterManager.register(
                emoticonsAdapter
        );

        if (blockbusterAdapter.supports())
        {
            this.blockbusterModelAccess
                    .loadModelByName(
                            "steve"
                    );
        }
    }


    public AnimationAdapterManager getAdapterManager()
    {
        return this.adapterManager;
    }


    public BlockbusterModelAccess getBlockbusterModelAccess()
    {
        return this.blockbusterModelAccess;
    }


    public TransformPanel getTransformPanel()
    {
        return this.transformPanel;
    }


    public AnimationPreview getAnimationPreview()
    {
        return this.animationPreview;
    }


    public List<AnimationBone> getBones()
    {
        return this.bones;
    }


    public void setBones(
            List<AnimationBone> bones)
    {
        if (bones == null)
        {
            this.bones =
                    new ArrayList<AnimationBone>();
        }
        else
        {
            this.bones =
                    bones;
        }
    }


    public void clearBones()
    {
        this.bones.clear();
    }


    public ActorAnimationData getOrCreateActorAnimation(
            String actorId,
            EditorSceneState sceneState)
    {
        if (actorId == null ||
                actorId.length() == 0 ||
                sceneState == null)
        {
            return null;
        }

        ActorAnimationData data =
                sceneState
                        .getAnimationData()
                        .getActor(
                                actorId
                        );

        if (data != null)
        {
            return data;
        }

        data =
                createAnimationDataForActor(
                        actorId,
                        sceneState
                );

        if (data != null)
        {
            sceneState
                    .getAnimationData()
                    .putActor(
                            actorId,
                            data
                    );
        }

        return data;
    }


    public ActorAnimationData createAnimationDataForActor(
            String actorId,
            EditorSceneState sceneState)
    {
        if (sceneState == null)
        {
            return null;
        }

        BlockbusterSceneActorData actorData =
                sceneState.getSelectedActorData();

        if (actorData == null)
        {
            return null;
        }

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return null;
        }

        NBTTagCompound morphNBT =
                sceneActor.getMorph();

        if (morphNBT != null)
        {
            try
            {
                AbstractMorph morph =
                        MorphManager.INSTANCE
                                .morphFromNBT(
                                        morphNBT.copy()
                                );

                if (morph instanceof
                        mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                {
                    mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph animatedMorph =
                            (mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph)
                                    morph;

                    EmoticonsActorPreviewRenderer
                            .prepareMorph(
                                    animatedMorph
                            );

                    return new ActorAnimationData(
                            actorId,
                            animatedMorph
                    );
                }
            }
            catch (Exception exception)
            {
                System.out.println(
                        "Failed to create actor animation for "
                                + actorId
                );

                exception.printStackTrace();
            }
        }

        /*
         * Chameleon models have their own real bone hierarchy.  Do this
         * before the Blockbuster fallback: otherwise every Chameleon
         * actor was being converted to the default Blockbuster skeleton
         * (or an empty list), which is why Pose and Body Parts saw no
         * Chameleon bones at all.
         */
        if (morph != null &&
                morph.getClass().getName().endsWith(".ChameleonMorph"))
        {
            return new ActorAnimationData(
                    actorId,
                    morph
            );
        }

        /*
         * Blockbuster actors store their custom model as a Metamorph
         * morph. Resolve that model first so the skeleton belongs to
         * the selected actor rather than the editor's default model.
         */
        String modelName = getBlockbusterModelName(morphNBT);

        if (modelName != null && !modelName.isEmpty())
        {
            this.blockbusterModelAccess.loadModelByName(modelName);
        }
        else if (!this.blockbusterModelAccess.isValid())
        {
            this.blockbusterModelAccess.loadModelByName("steve");
        }

        List<BlockbusterLimbData> limbs =
                this.blockbusterModelAccess
                        .getLimbData();

        return new ActorAnimationData(
                actorId,
                limbs
        );
    }


    private String getBlockbusterModelName(
            NBTTagCompound morphNBT)
    {
        if (morphNBT == null || !morphNBT.hasKey("Name"))
        {
            return null;
        }

        String name = morphNBT.getString("Name");

        if (name == null || name.isEmpty())
        {
            return null;
        }

        if (name.startsWith("blockbuster."))
        {
            return name.substring("blockbuster.".length());
        }

        return null;
    }


    public void selectActorAnimation(
            String actorId,
            EditorSceneState sceneState,
            EditorKeyframeController keyframeController)
    {
        ActorAnimationData data =
                getOrCreateActorAnimation(
                        actorId,
                        sceneState
                );

        if (data == null)
        {
            clearBones();

            keyframeController.reset();

            return;
        }

        setBones(
                data.getBones()
        );

        keyframeController
                .setSelectedBoneIndex(
                        keyframeController
                                .getSelectedBoneIndex(),
                        this.bones
                );

        keyframeController
                .resetSelectionOnly();
    }


    public void resetPreviewReference(
            BlockbusterRecord record)
    {
        if (record == null)
        {
            this.animationPreview
                    .setReferencePosition(
                            0.0D,
                            0.0D,
                            0.0D
                    );

            return;
        }

        BlockbusterRecordFrame frame =
                record.getFrame(0);

        if (frame == null)
        {
            this.animationPreview
                    .setReferencePosition(
                            0.0D,
                            0.0D,
                            0.0D
                    );

            return;
        }

        this.animationPreview
                .setReferencePosition(
                        frame.getX(),
                        frame.getY(),
                        frame.getZ()
                );
    }


    /**
     * Применяет позу на текущем integer кадре.
     *
     * Используется на паузе и при обычном
     * переключении кадров.
     */
    public void applyAdapters(
            int animationFrame)
    {
        applyAdapters(
                (float) animationFrame
        );
    }


    /**
     * Применяет позу на дробном кадре.
     *
     * Используется во время воспроизведения,
     * чтобы сохранить плавную интерполяцию.
     */
    public void applyAdapters(
            float animationFrame)
    {
        List<AnimationBoneSnapshot> snapshots =
                new ArrayList<AnimationBoneSnapshot>();

        if (this.bones == null ||
                this.bones.isEmpty())
        {
            BlockbusterPreviewAnimationState.clear();

            EmoticonsPreviewAnimationState.clear();

            return;
        }

        for (
                AnimationBone bone :
                this.bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            AnimationTransform transform =
                    bone.getTransformAt(
                            animationFrame
                    );

            if (transform == null)
            {
                continue;
            }

            String parentName = null;

            if (bone.getParent() != null)
            {
                parentName =
                        bone.getParent()
                                .getName();
            }

            snapshots.add(
                    new AnimationBoneSnapshot(
                            bone.getName(),
                            parentName,
                            transform
                    )
            );
        }

        this.adapterManager.applyAll(
                snapshots,
                (int) animationFrame
        );
    }


    public AnimationBone getSelectedBone(
            EditorKeyframeController keyframeController)
    {
        return keyframeController
                .getSelectedBone(
                        this.bones
                );
    }


    public AnimationTransform getCurrentTransform(
            EditorKeyframeController keyframeController,
            int currentFrame)
    {
        AnimationBone bone =
                keyframeController
                        .getSelectedBone(
                                this.bones
                        );

        if (bone == null)
        {
            return new AnimationTransform();
        }

        AnimationKeyframe selectedKeyframe =
                keyframeController
                        .getSelectedKeyframe();

        if (selectedKeyframe != null)
        {
            boolean belongsToBone =
                    false;

            for (
                    AnimationKeyframe keyframe :
                    bone.getKeyframes()
            )
            {
                if (keyframe == selectedKeyframe)
                {
                    belongsToBone = true;
                    break;
                }
            }

            if (belongsToBone)
            {
                return selectedKeyframe
                        .getTransform();
            }
        }

        AnimationTransform transform =
                bone.getTransformAt(
                        currentFrame
                );

        if (transform == null)
        {
            return new AnimationTransform();
        }

        return transform;
    }
}
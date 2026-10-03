package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class BodyPartsEditorController
{
    private final EditorTimeline localTimeline;
    private final EditorKeyframeController keyframeController;
    private final TransformPanel transformPanel;

    private final List<BodyPartModelData> models =
            new ArrayList<BodyPartModelData>();

    private List<AnimationBone> actorBones =
            new ArrayList<AnimationBone>();

    private BodyPartModelData selectedModel;
    private int selectedActorBone = -1;

    public BodyPartsEditorController()
    {
        this.localTimeline = new EditorTimeline();
        this.localTimeline.setLength(1);
        this.keyframeController =
                new EditorKeyframeController(
                        this.localTimeline,
                        180
                );
        this.transformPanel = new TransformPanel();
    }

    public EditorTimeline getTimeline() { return this.localTimeline; }
    public EditorKeyframeController getKeyframeController() { return this.keyframeController; }
    public TransformPanel getTransformPanel() { return this.transformPanel; }
    public List<BodyPartModelData> getModels() { return this.models; }

    public void setActorBones(List<AnimationBone> bones)
    {
        this.actorBones = bones == null
                ? new ArrayList<AnimationBone>()
                : bones;

        if (this.actorBones.isEmpty())
        {
            this.selectedActorBone = -1;
        }
        else if (this.selectedActorBone < 0 ||
                this.selectedActorBone >= this.actorBones.size())
        {
            this.selectedActorBone = 0;
        }
    }

    public List<AnimationBone> getActorBones() { return this.actorBones; }

    public int getSelectedActorBone() { return this.selectedActorBone; }

    public void setSelectedActorBone(int index)
    {
        if (index < 0 || index >= this.actorBones.size())
        {
            this.selectedActorBone = -1;
            return;
        }

        this.selectedActorBone = index;
    }

    public BodyPartModelData getSelectedModel() { return this.selectedModel; }

    public void selectModel(BodyPartModelData model)
    {
        this.selectedModel = model;
        this.keyframeController.clearSelection();

        if (model == null)
        {
            return;
        }

        this.localTimeline.setLength(
                Math.max(
                        1,
                        model.getEndFrame()
                )
        );

        this.keyframeController.setSelectedBoneIndex(
                0,
                model.getBones()
        );
    }

    public void backToModelTracks()
    {
        this.selectedModel = null;
        this.keyframeController.clearSelection();
    }

    public String getSelectedActorBoneName()
    {
        if (this.selectedActorBone < 0 ||
                this.selectedActorBone >= this.actorBones.size())
        {
            return "";
        }

        AnimationBone bone = this.actorBones.get(this.selectedActorBone);
        return bone == null ? "" : bone.getName();
    }

    public BodyPartModelData addModel(
            String modelName,
            int sceneLength)
    {
        String attachment = getSelectedActorBoneName();

        if (attachment.length() == 0 ||
                modelName == null ||
                modelName.length() == 0)
        {
            return null;
        }

        BlockbusterModelAccess access =
                new BlockbusterModelAccess(null);

        if (!access.loadModelByName(modelName))
        {
            return null;
        }

        List<AnimationBone> bones =
                createAnimationBones(
                        access.getLimbData()
                );

        if (bones.isEmpty())
        {
            return null;
        }

        int end = Math.max(1, sceneLength);

        BodyPartModelData model =
                new BodyPartModelData(
                        modelName,
                        attachment,
                        access,
                        bones,
                        0,
                        end
                );

        this.models.add(model);

        /*
         * The new model is represented by a Level 1 attachment bar.
         * The local model timeline opens only when that bar is clicked.
         */
        this.selectedModel = null;
        this.keyframeController.clearSelection();

        return model;
    }

    public boolean removeSelectedModel()
    {
        if (this.selectedModel == null)
        {
            return false;
        }

        boolean removed = this.models.remove(this.selectedModel);
        this.selectedModel = null;
        this.keyframeController.clearSelection();

        return removed;
    }

    public List<String> getAvailableModelNames()
    {
        return BlockbusterModelAccess.getAvailableModelNames();
    }

    private List<AnimationBone> createAnimationBones(
            List<BlockbusterLimbData> limbs)
    {
        List<AnimationBone> result =
                new ArrayList<AnimationBone>();

        if (limbs == null)
        {
            return result;
        }

        for (BlockbusterLimbData limb : limbs)
        {
            if (limb == null)
            {
                continue;
            }

            AnimationBone bone =
                    new AnimationBone(
                            limb.getName()
                    );

            bone.setLocalPosition(
                    limb.getX(),
                    limb.getY(),
                    limb.getZ()
            );

            result.add(bone);
        }

        for (AnimationBone bone : result)
        {
            String parentName = null;

            for (BlockbusterLimbData limb : limbs)
            {
                if (limb != null &&
                        limb.getName().equals(bone.getName()))
                {
                    parentName = limb.getParentName();
                    break;
                }
            }

            if (parentName != null)
            {
                for (AnimationBone parent : result)
                {
                    if (parentName.equals(parent.getName()))
                    {
                        bone.setParent(parent);
                        break;
                    }
                }
            }
        }

        return result;
    }
}

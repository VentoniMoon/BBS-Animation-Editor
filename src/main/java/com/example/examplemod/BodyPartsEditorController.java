package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import mchorse.metamorph.api.morphs.AbstractMorph;

public class BodyPartsEditorController
{
    private final EditorTimeline localTimeline;
    private final EditorKeyframeController keyframeController;
    private final TransformPanel transformPanel;

    private final List<BodyPartModelData> models =
            new ArrayList<BodyPartModelData>();

    private List<AnimationBone> actorBones =
            new ArrayList<AnimationBone>();

    /*
     * selectedAttachment is the rectangle currently selected on Level 1.
     * selectedModel is the same attachment only while Level 2 is open.
     */
    private BodyPartModelData selectedAttachment;
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

    public BodyPartModelData getSelectedAttachment()
    {
        return this.selectedAttachment;
    }

    public BodyPartModelData getSelectedModel()
    {
        return this.selectedModel;
    }

    /**
     * Creates the Level 1 rectangle only.
     *
     * No model is loaded here. The rectangle always starts at the
     * current timeline tick and has a fixed initial duration of 20 ticks.
     */
    public BodyPartModelData createAttachment(
            int startFrame,
            int sceneLength)
    {
        String attachment = getSelectedActorBoneName();

        if (attachment.length() == 0)
        {
            return null;
        }

        int start =
                Math.max(
                        0,
                        startFrame
                );

        BodyPartModelData model =
                new BodyPartModelData(
                        "",
                        attachment,
                        null,
                        new ArrayList<AnimationBone>(),
                        start,
                        start + 20
                );

        this.models.add(model);
        this.selectedAttachment = model;
        this.selectedModel = null;
        this.keyframeController.clearSelection();

        this.localTimeline.setLength(
                Math.max(
                        1,
                        Math.max(
                                sceneLength,
                                model.getEndFrame()
                        ) + 1
                )
        );

        this.localTimeline.setTick(start);

        return model;
    }

    /**
     * Assigns a Blockbuster model to the currently selected rectangle.
     */
    /** Assign any Metamorph morph (including Chameleon) to the selected attachment. */
    public boolean assignMorphToSelected(AbstractMorph morph)
    {
        BodyPartModelData selected = this.selectedAttachment;

        if (selected == null || morph == null)
        {
            return false;
        }

        AbstractMorph copy = morph.copy();
        selected.setMorph(copy);

        List<AnimationBone> bones = createAnimationBonesFromMorph(copy);
        if (!bones.isEmpty())
        {
            selected.setModel(
                    copy.name == null ? "" : copy.name,
                    null,
                    bones
            );
            selected.setMorph(copy);
        }

        return true;
    }

    public boolean assignModelToSelected(String modelName)
    {
        BodyPartModelData selected =
                this.selectedAttachment;

        if (selected == null ||
                modelName == null ||
                modelName.length() == 0)
        {
            return false;
        }

        BlockbusterModelAccess access =
                new BlockbusterModelAccess(null);

        if (!access.loadModelByName(modelName))
        {
            return false;
        }

        List<AnimationBone> bones =
                createAnimationBones(
                        access.getLimbData()
                );

        if (bones.isEmpty())
        {
            return false;
        }

        selected.setModel(
                modelName,
                access,
                bones
        );

        return true;
    }

    public void selectAttachment(BodyPartModelData attachment)
    {
        if (attachment == null ||
                !this.models.contains(attachment))
        {
            this.selectedAttachment = null;
            this.selectedModel = null;
            this.keyframeController.clearSelection();
            return;
        }

        this.selectedAttachment = attachment;
        this.selectedModel = null;
        this.keyframeController.clearSelection();

        this.setSelectedActorBone(
                findActorBoneIndex(
                        attachment.getAttachmentBoneName()
                )
        );
    }

    public void selectModel(BodyPartModelData model)
    {
        if (model == null ||
                !this.models.contains(model) ||
                !model.hasModel())
        {
            return;
        }

        this.selectedAttachment = model;
        this.selectedModel = model;
        this.keyframeController.clearSelection();

        this.localTimeline.setLength(
                Math.max(
                        1,
                        model.getEndFrame() + 1
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

    public boolean removeSelectedAttachment()
    {
        BodyPartModelData target =
                this.selectedAttachment != null
                        ? this.selectedAttachment
                        : this.selectedModel;

        if (target == null)
        {
            return false;
        }

        boolean removed =
                this.models.remove(target);

        if (removed)
        {
            this.selectedAttachment = null;
            this.selectedModel = null;
            this.keyframeController.clearSelection();
        }

        return removed;
    }

    public List<String> getAvailableModelNames()
    {
        return BlockbusterModelAccess.getAvailableModelNames();
    }

    private int findActorBoneIndex(String name)
    {
        if (name == null)
        {
            return -1;
        }

        for (int i = 0; i < this.actorBones.size(); i++)
        {
            AnimationBone bone = this.actorBones.get(i);

            if (bone != null &&
                    name.equals(bone.getName()))
            {
                return i;
            }
        }

        return -1;
    }

    private List<AnimationBone> createAnimationBonesFromMorph(
            AbstractMorph morph)
    {
        List<AnimationBone> result =
                new ArrayList<AnimationBone>();

        if (morph == null)
        {
            return result;
        }

        try
        {
            /*
             * ChameleonMorph.getModel() returns ChameleonModel.
             * ChameleonModel.getBoneNames() gives the names, but names
             * alone are not enough for the editor: we also need the
             * real ModelBone hierarchy and initial pivots.
             *
             * Read the underlying "model.bones" reflectively so this
             * code keeps compiling even when Chameleon is optional.
             */
            Object chameleonModel =
                    morph.getClass()
                            .getMethod("getModel")
                            .invoke(morph);

            if (chameleonModel == null)
            {
                return createAnimationBonesFromMorphNames(morph);
            }

            Object model =
                    null;

            try
            {
                java.lang.reflect.Field modelField =
                        chameleonModel.getClass()
                                .getField("model");

                model = modelField.get(chameleonModel);
            }
            catch (Throwable ignored)
            {
            }

            if (model == null)
            {
                return createAnimationBonesFromMorphNames(chameleonModel);
            }

            java.lang.reflect.Field bonesField =
                    model.getClass()
                            .getField("bones");

            Object roots =
                    bonesField.get(model);

            if (!(roots instanceof List))
            {
                return createAnimationBonesFromMorphNames(
                        chameleonModel
                );
            }

            for (Object root :
                    (List<?>) roots)
            {
                createAnimationBonesFromChameleonBone(
                        root,
                        null,
                        result
                );
            }
        }
        catch (Throwable error)
        {
            /*
             * Do not silently turn a real Chameleon skeleton into an
             * empty Body Part.  Keep the old name-only fallback for
             * other morph implementations which expose getBoneNames().
             */
            try
            {
                Object model =
                        morph.getClass()
                                .getMethod("getModel")
                                .invoke(morph);

                Object names =
                        model.getClass()
                                .getMethod("getBoneNames")
                                .invoke(model);

                if (names instanceof List)
                {
                    for (Object name :
                            (List<?>) names)
                    {
                        if (name != null)
                        {
                            result.add(
                                    new AnimationBone(
                                            String.valueOf(name)
                                    )
                            );
                        }
                    }
                }
            }
            catch (Throwable ignored)
            {
            }
        }

        /*
         * Some morphs expose a usable getModel()/bones hierarchy, while
         * others expose only getBoneNames() on either the morph or its
         * model. Do not silently leave the Body Part timeline without
         * tracks when the richer hierarchy is unavailable.
         */
        if (result.isEmpty())
        {
            Object modelCandidate = null;

            try
            {
                modelCandidate = morph.getClass()
                        .getMethod("getModel")
                        .invoke(morph);
            }
            catch (Throwable ignored)
            {
            }

            result = createAnimationBonesFromMorphNames(modelCandidate);
            if (result.isEmpty())
            {
                result = createAnimationBonesFromMorphNames(morph);
            }
        }

        return result;
    }

    private List<AnimationBone> createAnimationBonesFromMorphNames(
            Object chameleonModel)
    {
        List<AnimationBone> result =
                new ArrayList<AnimationBone>();

        if (chameleonModel == null)
        {
            return result;
        }

        try
        {
            Object names =
                    chameleonModel.getClass()
                            .getMethod("getBoneNames")
                            .invoke(chameleonModel);

            if (names instanceof List)
            {
                for (Object name :
                        (List<?>) names)
                {
                    if (name != null)
                    {
                        String boneName = String.valueOf(name);
                        if (boneName.length() > 0)
                        {
                            result.add(new AnimationBone(boneName));
                        }
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return result;
    }

    private void createAnimationBonesFromChameleonBone(
            Object modelBone,
            AnimationBone parent,
            List<AnimationBone> result)
    {
        if (modelBone == null)
        {
            return;
        }

        try
        {
            java.lang.reflect.Field idField =
                    modelBone.getClass()
                            .getField("id");

            String name =
                    String.valueOf(
                            idField.get(modelBone)
                    );

            AnimationBone bone =
                    new AnimationBone(name);

            /*
             * ModelBone.initial.translate is the actual Chameleon
             * pivot.  ChameleonRenderer moves to this pivot before
             * rotation/scale, so this is the point where the gizmo
             * belongs.
             */
            java.lang.reflect.Field initialField =
                    modelBone.getClass()
                            .getField("initial");

            Object initial =
                    initialField.get(modelBone);

            if (initial != null)
            {
                java.lang.reflect.Field translateField =
                        initial.getClass()
                                .getField("translate");

                Object translate =
                        translateField.get(initial);

                if (translate != null)
                {
                    float x =
                            readVectorComponent(
                                    translate,
                                    "x"
                            );

                    float y =
                            readVectorComponent(
                                    translate,
                                    "y"
                            );

                    float z =
                            readVectorComponent(
                                    translate,
                                    "z"
                            );

                    bone.setLocalPosition(
                            x,
                            y,
                            z
                    );
                }
            }

            if (parent != null)
            {
                bone.setParent(parent);
            }

            result.add(bone);

            java.lang.reflect.Field childrenField =
                    modelBone.getClass()
                            .getField("children");

            Object children =
                    childrenField.get(modelBone);

            if (children instanceof List)
            {
                for (Object child :
                        (List<?>) children)
                {
                    createAnimationBonesFromChameleonBone(
                            child,
                            bone,
                            result
                    );
                }
            }
        }
        catch (Throwable ignored)
        {
            /*
             * A malformed optional Chameleon bone must not prevent the
             * rest of the editor from opening.
             */
        }
    }

    private float readVectorComponent(
            Object vector,
            String field)
    {
        try
        {
            java.lang.reflect.Field value =
                    vector.getClass().getField(field);

            Object number =
                    value.get(vector);

            return number instanceof Number
                    ? ((Number) number).floatValue()
                    : 0.0F;
        }
        catch (Throwable ignored)
        {
            return 0.0F;
        }
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

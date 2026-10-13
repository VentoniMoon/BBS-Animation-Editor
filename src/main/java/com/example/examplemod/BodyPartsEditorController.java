package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import net.minecraft.nbt.NBTTagCompound;

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

    /*
     * Embedded Metamorph Body Parts come from the original actor morph in
     * the Blockbuster scene. Keep them separate from manually added tracks
     * so a repeated Preview render never imports the same parts twice.
     */
    private final List<BodyPartModelData> importedRecordParts =
            new ArrayList<BodyPartModelData>();
    private String importedActorSignature = "";

    public void importRecordedBodyParts(BlockbusterSceneActorData actorData)
    {
        if (actorData == null || actorData.getActor() == null)
        {
            return;
        }

        NBTTagCompound morphNBT = actorData.getActor().getMorph();
        String signature = actorData.getId() + "|"
                + (morphNBT == null ? "" : morphNBT.toString());

        if (signature.equals(this.importedActorSignature))
        {
            return;
        }

        /*
         * The previous actor's imported Body Parts should not leak into
         * the new actor. Preserve user-created editor tracks independently.
         */
        this.models.removeAll(this.importedRecordParts);
        this.importedRecordParts.clear();
        this.importedActorSignature = signature;

        if (morphNBT == null || morphNBT.hasNoTags())
        {
            return;
        }

        try
        {
            AbstractMorph morph = MorphManager.INSTANCE.morphFromNBT(
                    morphNBT.copy());
            if (!(morph instanceof IBodyPartProvider))
            {
                return;
            }

            BodyPartManager source = ((IBodyPartProvider) morph).getBodyPart();
            if (source == null || source.parts == null)
            {
                return;
            }

            int lastFrame = Math.max(1, actorData.getLength() - 1);
            for (BodyPart part : source.parts)
            {
                if (part == null || part.limb == null
                        || part.limb.isEmpty() || part.morph == null
                        || part.morph.get() == null)
                {
                    continue;
                }

                AbstractMorph child = part.morph.get().copy();
                BodyPartModelData data = new BodyPartModelData(
                        child.name == null ? "" : child.name,
                        part.limb, null,
                        createAnimationBonesFromMorph(child),
                        0, lastFrame);
                data.setMorph(child);

                /*
                 * BodyPart.translate uses blocks; the editor's global
                 * transform stores model pixels, as in createMorphBodyPart.
                 */
                data.getGlobalTransform().setPosition(
                        part.translate.x * 16.0F,
                        part.translate.y * 16.0F,
                        part.translate.z * 16.0F);
                data.getGlobalTransform().setRotation(
                        part.rotate.x, part.rotate.y, part.rotate.z);
                data.getGlobalTransform().setScale(
                        part.scale.x, part.scale.y, part.scale.z);

                this.models.add(data);
                this.importedRecordParts.add(data);
            }
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] Could not import recorded Body Parts");
            error.printStackTrace();
        }
    }


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

        List<AnimationBone> bones = new ArrayList<AnimationBone>();
        Object apiModel = access.getApiModel();

        if (apiModel instanceof mchorse.blockbuster.api.Model)
        {
            bones = createAnimationBonesFromApiModel(
                    (mchorse.blockbuster.api.Model) apiModel
            );
        }

        if (bones.isEmpty())
        {
            bones = createAnimationBones(access.getLimbData());
        }

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

        /*
         * The Metamorph picker also returns Blockbuster CustomMorphs
         * (for example blockbuster.eyes).  They do not expose the
         * Chameleon getModel()/getBoneNames() API, so the old reflective
         * path silently produced an empty skeleton for these models.
         * Resolve their actual Blockbuster model through the same access
         * class used by the standalone model picker, then copy its limb
         * hierarchy into the Body Parts timeline.
         */
        if (morph instanceof CustomMorph)
        {
            /*
             * Import the exact rest pose that BodyPartsPreviewRenderer uses.
             * A selected CustomMorph may carry a customPose/currentPose with
             * different bone rotations than the API model's standing pose.
             * Reading limb metadata first discards those rest rotations and
             * makes the gizmo orientation disagree with the rendered child.
             */
            result = createAnimationBonesFromCustomMorphModel((CustomMorph) morph);
            if (!result.isEmpty())
            {
                return result;
            }

            String name = morph.name == null ? "" : morph.name;
            String modelName = name.startsWith("blockbuster.")
                    ? name.substring("blockbuster.".length())
                    : name;

            if (!modelName.isEmpty())
            {
                BlockbusterModelAccess access = new BlockbusterModelAccess(null);
                if (access.loadModelByName(modelName))
                {
                    Object apiModel = access.getApiModel();
                    if (apiModel instanceof mchorse.blockbuster.api.Model)
                    {
                        result = createAnimationBonesFromApiModel(
                                (mchorse.blockbuster.api.Model) apiModel);
                    }

                    if (result.isEmpty())
                    {
                        result = createAnimationBones(access.getLimbData());
                    }

                    if (!result.isEmpty())
                    {
                        return result;
                    }
                }
            }
        }

        /*
         * Emoticons AnimatedMorph stores its skeleton in BOBJ armatures,
         * not in the reflective Chameleon getModel()/getBoneNames() API.
         * Reuse the existing Character bone resolver so these models get
         * real selectable tracks instead of an empty Body Parts list.
         */
        if (morph instanceof AnimatedMorph)
        {
            return createAnimationBonesFromEmoticons((AnimatedMorph) morph);
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

    /**
     * Build the editor skeleton from Emoticons' actual BOBJ armature.
     * Names alone are not sufficient: each AnimationBone needs the source
     * pivot, rest orientation and parent relation so its gizmo is positioned
     * relative to the attached model rather than the actor attachment bone.
     */
    private List<AnimationBone> createAnimationBonesFromEmoticons(
            AnimatedMorph morph)
    {
        List<AnimationBone> result = new ArrayList<AnimationBone>();

        if (morph == null)
        {
            return result;
        }

        try
        {
            morph.initiateAnimator();

            for (mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone source
                    : EmoticonsModelAccess.getBones(morph))
            {
                if (source == null || source.name == null || source.name.isEmpty())
                {
                    continue;
                }

                AnimationBone bone = new AnimationBone(source.name);
                javax.vecmath.Matrix4f rest = source.relBoneMat != null
                        ? source.relBoneMat
                        : source.boneMat;

                if (rest != null)
                {
                    bone.setLocalPosition(
                            rest.m03,
                            rest.m13,
                            rest.m23
                    );

                    float[] rotation = extractEulerDegrees(rest);
                    bone.setBaseRotation(rotation[0], rotation[1], rotation[2]);
                }

                result.add(bone);
            }

            for (AnimationBone bone : result)
            {
                mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone source =
                        EmoticonsModelAccess.findBone(morph, bone.getName());

                if (source == null)
                {
                    continue;
                }

                String parentName = EmoticonsModelAccess.getParentName(source);
                if (parentName == null || parentName.isEmpty())
                {
                    continue;
                }

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
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor][BodyParts] Could not read Emoticons armature"
            );
            error.printStackTrace();
        }

        return result;
    }

    /**
     * Extract XYZ Euler angles from the source rest matrix. BOBJ rest
     * matrices are used only to initialize the editor hierarchy; animation
     * keys remain in the editor's degree-based transform convention.
     */
    private float[] extractEulerDegrees(javax.vecmath.Matrix4f matrix)
    {
        double sy = -matrix.m20;
        sy = Math.max(-1.0D, Math.min(1.0D, sy));

        double y = Math.asin(sy);
        double cy = Math.cos(y);
        double x;
        double z;

        if (Math.abs(cy) > 0.00001D)
        {
            x = Math.atan2(matrix.m21, matrix.m22);
            z = Math.atan2(matrix.m10, matrix.m00);
        }
        else
        {
            x = Math.atan2(-matrix.m12, matrix.m11);
            z = 0.0D;
        }

        return new float[]
        {
            (float) Math.toDegrees(x),
            (float) Math.toDegrees(y),
            (float) Math.toDegrees(z)
        };
    }

    private List<AnimationBone> createAnimationBonesFromCustomMorphModel(
            CustomMorph morph)
    {
        List<AnimationBone> result = new ArrayList<AnimationBone>();

        if (morph == null)
        {
            return result;
        }

        try
        {
            morph.updateModel();
            if (morph.model != null)
            {
                mchorse.blockbuster.api.ModelPose pose =
                        morph.customPose != null
                                ? morph.customPose
                                : morph.model.getPose(morph.currentPose);

                result = createAnimationBonesFromApiModel(morph.model, pose);
            }
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor][BodyParts] "
                            + "Could not read CustomMorph API skeleton directly"
            );
            error.printStackTrace();
        }

        return result;
    }

    /**
     * Import Blockbuster's standing-pose pivots and rest rotations for both
     * a CustomMorph child and a model selected directly from the picker.
     */
    private List<AnimationBone> createAnimationBonesFromApiModel(
            mchorse.blockbuster.api.Model model)
    {
        return createAnimationBonesFromApiModel(
                model, model == null ? null : model.getPose("standing"));
    }

    private List<AnimationBone> createAnimationBonesFromApiModel(
            mchorse.blockbuster.api.Model model,
            mchorse.blockbuster.api.ModelPose selectedPose)
    {
        List<AnimationBone> result = new ArrayList<AnimationBone>();

        if (model == null || model.limbs == null || model.limbs.isEmpty())
        {
            return result;
        }

        mchorse.blockbuster.api.ModelPose standing =
                selectedPose != null ? selectedPose : model.getPose("standing");

        for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelLimb> entry
                : model.limbs.entrySet())
        {
            mchorse.blockbuster.api.ModelLimb limb = entry.getValue();
            if (limb == null)
            {
                continue;
            }

            String name = limb.name == null || limb.name.isEmpty()
                    ? entry.getKey()
                    : limb.name;

            if (name == null || name.isEmpty())
            {
                continue;
            }

            AnimationBone bone = new AnimationBone(name);
            mchorse.blockbuster.api.ModelTransform source =
                    standing == null ? null : standing.limbs.get(name);

            if (source != null)
            {
                bone.setLocalPosition(
                        source.translate[0],
                        -source.translate[1],
                        -source.translate[2]
                );
                bone.setBaseRotation(
                        source.rotate[0],
                        source.rotate[1],
                        source.rotate[2]
                );
            }

            result.add(bone);
        }

        for (AnimationBone bone : result)
        {
            for (mchorse.blockbuster.api.ModelLimb limb : model.limbs.values())
            {
                if (limb != null && bone.getName().equals(limb.name)
                        && limb.parent != null && !limb.parent.isEmpty())
                {
                    for (AnimationBone parent : result)
                    {
                        if (limb.parent.equals(parent.getName()))
                        {
                            bone.setParent(parent);
                            break;
                        }
                    }
                    break;
                }
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
                /*
                 * Preserve the model's rest rotation as well as its pivot.
                 * The gizmo basis must start from the same bind pose the
                 * Chameleon renderer uses, then compose editor keyframes.
                 */
                try
                {
                    java.lang.reflect.Field rotateField =
                            initial.getClass().getField("rotate");
                    Object rotate = rotateField.get(initial);
                    if (rotate != null)
                    {
                        bone.setBaseRotation(
                                readVectorComponent(rotate, "x"),
                                readVectorComponent(rotate, "y"),
                                readVectorComponent(rotate, "z")
                        );
                    }
                }
                catch (Throwable ignored)
                {
                }

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

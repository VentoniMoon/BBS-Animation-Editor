package com.example.examplemod;

import java.util.List;

import mchorse.blockbuster.api.Model;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import mchorse.metamorph.bodypart.BodyPart;
import mchorse.metamorph.bodypart.BodyPartManager;
import mchorse.metamorph.bodypart.IBodyPartProvider;
import mchorse.blockbuster.common.entity.EntityActor;

/**
 * Bridges the editor's Body Parts data to Metamorph's real body-part system.
 *
 * The important rule here is that the editor does NOT render an attached
 * model itself.  It creates the same runtime objects Blockbuster creates:
 *
 *   parent CustomMorph
 *       -> BodyPartManager
 *           -> BodyPart
 *               -> child CustomMorph
 *
 * Blockbuster's own LayerBodyPart then renders those objects.  This keeps
 * attachment transforms, model rendering, lighting and nested morph
 * rendering on the exact 1.12.2 code path used by Metamorph/Blockbuster.
 */
public class BodyPartsPreviewRenderer
{
    /**
     * Rebuild the parent morph's real Metamorph body-part list for the
     * current editor frame.
     *
     * This is called BEFORE RenderCustomActor renders the actor, so its
     * original LayerBodyPart sees the generated BodyPart objects.
     */
    public void prepare(
            BodyPartsEditorController controller,
            EntityActor actor,
            int frame)
    {
        if (actor == null)
        {
            return;
        }

        AbstractMorph abstractMorph =
                actor.getMorph();

        /*
         * Both Blockbuster CustomMorph and Emoticons AnimatedMorph
         * expose the same Metamorph IBodyPartProvider contract.
         *
         * Previously this renderer accepted only CustomMorph.  That
         * accidentally made Body Parts a no-op for AnimatedMorph:
         * prepare() returned before even creating the BodyPart objects.
         *
         * This is the exact abstraction used by Metamorph itself.
         */
        if (!(abstractMorph instanceof IBodyPartProvider))
        {
            return;
        }

        BodyPartManager manager =
                ((IBodyPartProvider) abstractMorph)
                        .getBodyPart();

        if (manager == null)
        {
            return;
        }

        /*
         * The editor is the source of truth for the current frame.
         * Do not leave body parts from a previous frame alive.
         */
        manager.parts.clear();

        if (controller == null)
        {
            return;
        }

        List<BodyPartModelData> models =
                controller.getModels();

        if (models == null || models.isEmpty())
        {
            return;
        }

        for (BodyPartModelData data : models)
        {
            if (data == null ||
                    !data.hasModel() ||
                    frame < data.getStartFrame() ||
                    frame > data.getEndFrame())
            {
                continue;
            }

            BodyPart part =
                    createBodyPart(
                            data,
                            frame
                    );

            if (part != null)
            {
                manager.parts.add(part);
            }
        }
    }

    /**
     * Create one genuine Metamorph BodyPart.
     */
    private BodyPart createBodyPart(
            BodyPartModelData data,
            int frame)
    {
        if (data == null)
        {
            return null;
        }

        /* Chameleon and other Metamorph add-ons are stored as real morphs. */
        if (data.hasMorphModel())
        {
            AbstractMorph morph = null;
            CharacterKey stateKey = data.getCharacterStateKeyAt(frame);

            if (stateKey != null && stateKey.hasSkin())
            {
                try
                {
                    morph = MorphManager.INSTANCE.morphFromNBT(stateKey.getSkin());
                }
                catch (Throwable error)
                {
                    error.printStackTrace();
                }
            }

            if (morph == null)
            {
                morph = data.getMorphAt(frame);
            }

            if (morph != null)
            {
                return createMorphBodyPart(data, morph, frame);
            }
        }

        String modelName =
                data.getModelNameAt(frame);

        BlockbusterModelAccess modelAccess =
                data.getModelAccess();

        if (!modelName.equals(data.getModelName()))
        {
            modelAccess = new BlockbusterModelAccess(null);

            if (!modelAccess.loadModelByName(modelName))
            {
                return null;
            }
        }

        if (modelAccess == null)
        {
            return null;
        }

        if (modelName == null ||
                modelName.length() == 0)
        {
            return null;
        }

        Object apiModelObject =
                modelAccess.getApiModel();

        if (!(apiModelObject instanceof Model))
        {
            /*
             * The editor deliberately refuses to cast ModelCustom to
             * Model.  They are different classes in Blockbuster 1.12.2.
             *
             * If the render-side model has no API-model reference, try
             * resolving it again through BlockbusterModelAccess.
             */
            BlockbusterModelAccess access =
                    modelAccess;

            if (!access.loadModelByName(modelName))
            {
                return null;
            }

            apiModelObject =
                    access.getApiModel();
        }

        if (!(apiModelObject instanceof Model))
        {
            return null;
        }

        Model apiModel =
                (Model) apiModelObject;

        CustomMorph child =
                createChildMorph(
                        modelName,
                        apiModel,
                        data,
                        frame
                );

        if (child == null)
        {
            return null;
        }

        BodyPart part =
                new BodyPart();

        part.limb =
                data.getAttachmentBoneName();

        if (part.limb == null ||
                part.limb.length() == 0)
        {
            return null;
        }

        /*
         * BodyPart transforms are expressed in Minecraft world/model
         * units.  The editor's global translation is expressed in model
         * pixels, hence the same /16 conversion used by Blockbuster's
         * renderer.
         */
        AnimationTransform global =
                data.getGlobalTransform();

        if (global != null)
        {
            part.translate.set(
                    global.getPositionX() / 16.0F,
                    global.getPositionY() / 16.0F,
                    global.getPositionZ() / 16.0F
            );

            part.rotate.set(
                    global.getRotationX(),
                    global.getRotationY(),
                    global.getRotationZ()
            );

            part.scale.set(
                    global.getScaleX(),
                    global.getScaleY(),
                    global.getScaleZ()
            );
        }

        /*
         * The attached morph must use the real actor entity.  Metamorph's
         * default is false (DummyEntity), which is useful for the morph GUI
         * but is wrong for our live actor preview.
         */
        part.useTarget = true;
        part.enabled = true;
        part.animate = false;

        part.morph.setDirect(child);

        return part;
    }

    /** Build a BodyPart around a non-Blockbuster Metamorph (notably Chameleon). */
    private BodyPart createMorphBodyPart(BodyPartModelData data, AbstractMorph child, int frame)
    {
        BodyPart part = new BodyPart();
        part.limb = data.getAttachmentBoneName();

        if (part.limb == null || part.limb.length() == 0)
        {
            return null;
        }

        AnimationTransform global = data.getGlobalTransform();
        if (global != null)
        {
            part.translate.set(global.getPositionX() / 16.0F,
                    global.getPositionY() / 16.0F,
                    global.getPositionZ() / 16.0F);
            part.rotate.set(global.getRotationX(), global.getRotationY(), global.getRotationZ());
            part.scale.set(global.getScaleX(), global.getScaleY(), global.getScaleZ());
        }

        /*
         * CustomMorphs selected from the Metamorph browser enter this
         * morph-backed path, not createChildMorph(). Without applying the
         * editor pose here, internal keyframe values change in the UI but
         * the actual attached model keeps its original pose.
         */
        if (child instanceof CustomMorph)
        {
            applyInternalBonePose((CustomMorph) child, data, frame);
        }

        part.useTarget = true;
        part.enabled = true;
        /* Chameleon animations must keep ticking while the actor preview plays. */
        part.animate = true;
        part.morph.setDirect(child);
        return part;
    }

    /**
     * Apply the Body Parts internal-bone keyframes to a CustomMorph pose.
     */
    private void applyInternalBonePose(
            CustomMorph child,
            BodyPartModelData data,
            int frame)
    {
        if (child == null || data == null)
        {
            return;
        }

        try
        {
            child.updateModel(true);

            Model apiModel = child.model;
            if (apiModel == null)
            {
                String name = child.name == null ? "" : child.name;
                if (name.startsWith("blockbuster."))
                {
                    name = name.substring("blockbuster.".length());
                }

                if (!name.isEmpty())
                {
                    BlockbusterModelAccess access =
                            new BlockbusterModelAccess(null);

                    if (access.loadModelByName(name)
                            && access.getApiModel() instanceof Model)
                    {
                        apiModel = (Model) access.getApiModel();
                        child.model = apiModel;
                    }
                }
            }

            if (apiModel == null)
            {
                return;
            }

            /*
             * Start from the morph's actual pose rather than an empty pose.
             * A blank ModelProperties silently replaces the model's standing
             * pose and can lose limb defaults/properties. Convert every source
             * transform to LimbProperties so Blockbuster keeps the per-limb
             * rendering flags as well as its transform.
             */
            CustomMorph.ModelProperties pose =
                    new CustomMorph.ModelProperties();

            mchorse.blockbuster.api.ModelPose sourcePose =
                    child.customPose != null
                            ? child.customPose
                            : apiModel.getPose(child.currentPose);

            if (sourcePose != null)
            {
                pose.size = new float[] {
                        sourcePose.size[0],
                        sourcePose.size[1],
                        sourcePose.size[2]
                };

                for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelTransform> entry
                        : sourcePose.limbs.entrySet())
                {
                    if (entry.getKey() == null || entry.getValue() == null)
                    {
                        continue;
                    }

                    CustomMorph.LimbProperties properties =
                            new CustomMorph.LimbProperties();
                    properties.copy(entry.getValue());
                    pose.limbs.put(entry.getKey(), properties);
                }

                for (mchorse.blockbuster.api.formats.obj.ShapeKey shape : sourcePose.shapes)
                {
                    pose.shapes.add(shape.copy());
                }
            }

            pose.updateLimbs(apiModel, false);

            int matchedBones = 0;
            int missingBones = 0;

            for (AnimationBone bone : data.getBones())
            {
                if (bone == null || bone.getName() == null
                        || bone.getKeyframes().isEmpty())
                {
                    /*
                     * An unkeyed bone must retain the source model's standing
                     * transform. getTransformAt() returns identity for an
                     * empty track, and writing that identity to every limb
                     * was erasing the model's default pose on every preview
                     * refresh.
                     */
                    continue;
                }

                mchorse.blockbuster.api.ModelTransform target =
                        pose.limbs.get(bone.getName());

                if (target == null)
                {
                    /* Model limb names are normally exact, but tolerate case-only
                     * differences from third-party/custom model metadata. */
                    for (java.util.Map.Entry<String, mchorse.blockbuster.api.ModelTransform> entry
                            : pose.limbs.entrySet())
                    {
                        if (entry.getKey() != null
                                && entry.getKey().equalsIgnoreCase(bone.getName()))
                        {
                            target = entry.getValue();
                            break;
                        }
                    }
                }

                if (target == null)
                {
                    missingBones++;
                    continue;
                }

                AnimationTransform transform =
                        bone.getTransformAt(frame);

                if (transform == null)
                {
                    continue;
                }

                target.translate[0] = bone.getLocalX() + transform.getPositionX();
                target.translate[1] = bone.getLocalY() + transform.getPositionY();
                target.translate[2] = bone.getLocalZ() + transform.getPositionZ();

                /* Keyframe rotations/scales are offsets from the source pose. */
                target.rotate[0] += transform.getRotationX();
                target.rotate[1] += transform.getRotationY();
                target.rotate[2] += transform.getRotationZ();
                target.scale[0] *= transform.getScaleX();
                target.scale[1] *= transform.getScaleY();
                target.scale[2] *= transform.getScaleZ();
                matchedBones++;
            }

            System.out.println(
                    "[BBS Animation Editor][BodyParts] Internal pose applied: model="
                            + child.name + ", frame=" + frame
                            + ", matchedBones=" + matchedBones
                            + ", missingBones=" + missingBones
            );

            child.customPose = pose;
            child.currentPose = "";
        }
        catch (Throwable error)
        {
            System.err.println(
                    "[BBS Animation Editor] Failed to apply Body Parts internal bone pose"
            );
            error.printStackTrace();
        }
    }

    /**
     * Build the child CustomMorph which Metamorph's BodyPart will render.
     */
    private CustomMorph createChildMorph(
            String modelName,
            Model apiModel,
            BodyPartModelData data,
            int frame)
    {
        CustomMorph child = null;

        CharacterKey stateKey = data.getCharacterStateKeyAt(frame);

        if (stateKey != null && stateKey.hasSkin())
        {
            try
            {
                AbstractMorph savedSkin = MorphManager.INSTANCE.morphFromNBT(stateKey.getSkin());
                if (savedSkin instanceof CustomMorph)
                {
                    child = (CustomMorph) savedSkin;
                    child.model = apiModel;
                }
            }
            catch (Throwable error)
            {
                error.printStackTrace();
            }
        }

        if (child == null)
        {
            child = new CustomMorph();
            child.name = "blockbuster." + modelName;
            child.model = apiModel;
        }

        /*
         * CustomMorph.updateModel() is the normal Blockbuster model
         * resolution path.  The API model is assigned first so the morph
         * remains valid even before ModelHandler's next update.
         */
        child.updateModel(true);

        if (child.model == null)
        {
            child.model = apiModel;
        }

        CustomMorph.ModelProperties pose =
                new CustomMorph.ModelProperties();

        pose.updateLimbs(
                apiModel,
                true
        );

        List<AnimationBone> bones =
                modelName.equals(data.getModelName())
                        ? data.getBones()
                        : null;

        if (bones != null)
        {
            for (AnimationBone bone : bones)
            {
                if (bone == null ||
                        bone.getName() == null)
                {
                    continue;
                }

                mchorse.blockbuster.api.ModelTransform target =
                        pose.limbs.get(
                                bone.getName()
                        );

                if (target == null)
                {
                    continue;
                }

                AnimationTransform transform =
                        bone.getTransformAt(
                                frame
                        );

                if (transform == null)
                {
                    continue;
                }

                /*
                 * Keep the editor's local skeleton position and add the
                 * animated transform exactly once.
                 */
                target.translate[0] =
                        bone.getLocalX()
                                + transform.getPositionX();

                target.translate[1] =
                        bone.getLocalY()
                                + transform.getPositionY();

                target.translate[2] =
                        bone.getLocalZ()
                                + transform.getPositionZ();

                target.rotate[0] =
                        transform.getRotationX();

                target.rotate[1] =
                        transform.getRotationY();

                target.rotate[2] =
                        transform.getRotationZ();

                target.scale[0] =
                        transform.getScaleX();

                target.scale[1] =
                        transform.getScaleY();

                target.scale[2] =
                        transform.getScaleZ();
            }
        }

        child.customPose =
                pose;

        /*
         * Force the child to use the freshly prepared pose and model.
         */
        child.currentPose = "";

        CharacterBodyPartOverrideController.apply(
                child,
                data.getCharacterTimeline(),
                frame
        );

        return child;
    }

    public void clear()
    {
        /*
         * There is no renderer-owned LayerRenderer anymore.
         * Body parts live inside the actor's CustomMorph and are rebuilt
         * on the next prepare() call.
         */
    }
}

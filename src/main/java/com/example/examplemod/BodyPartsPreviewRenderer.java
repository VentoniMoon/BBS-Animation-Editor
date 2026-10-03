package com.example.examplemod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.FloatBuffer;

import javax.vecmath.Matrix4f;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;

import org.lwjgl.opengl.GL11;

/**
 * Renders the temporary Body Parts attachments on top of the
 * selected Blockbuster actor.
 *
 * Body Parts are editor-only data, so they are rendered here rather
 * than being inserted into the real Character morph.
 */
public class BodyPartsPreviewRenderer
{
    private final Map<String, CustomMorph> morphs =
            new HashMap<String, CustomMorph>();

    private final Map<String, EntityActor> entities =
            new HashMap<String, EntityActor>();

    public void clear()
    {
        this.morphs.clear();

        for (EntityActor entity : this.entities.values())
        {
            if (entity != null)
            {
                entity.setDead();
            }
        }

        this.entities.clear();
    }

    public void render(
            Minecraft mc,
            EntityActor actor,
            BodyPartsEditorController controller,
            int frame,
            float partialTicks)
    {
        if (mc == null ||
                mc.world == null ||
                actor == null ||
                controller == null)
        {
            return;
        }

        List<BodyPartModelData> models =
                controller.getModels();

        if (models == null ||
                models.isEmpty())
        {
            return;
        }

        for (BodyPartModelData data : models)
        {
            if (data == null ||
                    !data.hasModel())
            {
                continue;
            }

            if (frame < data.getStartFrame() ||
                    frame > data.getEndFrame())
            {
                continue;
            }

            if (data.getAttachmentBoneName() == null ||
                    data.getAttachmentBoneName().isEmpty())
            {
                continue;
            }

            CustomMorph morph =
                    getMorph(
                            mc,
                            data.getModelName()
                    );

            if (morph == null ||
                    morph.model == null)
            {
                continue;
            }

            applyLocalAnimation(
                    morph,
                    data,
                    frame
            );

            BoneAttachment attachment =
                    findAttachment(
                            actor,
                            controller,
                            data.getAttachmentBoneName(),
                            frame
                    );

            if (attachment == null)
            {
                continue;
            }

            EntityActor renderEntity =
                    getRenderEntity(
                            mc,
                            data.getModelName()
                    );

            renderEntity.posX = actor.posX;
            renderEntity.posY = actor.posY;
            renderEntity.posZ = actor.posZ;
            renderEntity.prevPosX = actor.posX;
            renderEntity.prevPosY = actor.posY;
            renderEntity.prevPosZ = actor.posZ;

            renderEntity.rotationYaw =
                    actor.rotationYaw;
            renderEntity.prevRotationYaw =
                    actor.rotationYaw;

            renderEntity.rotationPitch =
                    actor.rotationPitch;
            renderEntity.prevRotationPitch =
                    actor.rotationPitch;

            renderEntity.setSneaking(
                    actor.isSneaking()
            );

            renderEntity.isDead = false;
            renderEntity.noClip = true;

            renderAttached(
                    mc,
                    actor,
                    renderEntity,
                    morph,
                    attachment,
                    data.getGlobalTransform(),
                    partialTicks
            );
        }
    }

    private CustomMorph getMorph(
            Minecraft mc,
            String name)
    {
        CustomMorph morph =
                this.morphs.get(name);

        if (morph == null)
        {
            morph =
                    new CustomMorph();

            morph.name =
                    "blockbuster." + name;

            morph.updateModel(true);

            this.morphs.put(
                    name,
                    morph
            );
        }
        else
        {
            morph.updateModel();
        }

        if (morph.model == null)
        {
            return null;
        }

        return morph;
    }

    private EntityActor getRenderEntity(
            Minecraft mc,
            String name)
    {
        EntityActor entity =
                this.entities.get(name);

        if (entity == null ||
                entity.world != mc.world ||
                entity.isDead)
        {
            entity =
                    new EntityActor(
                            mc.world
                    );

            entity.noClip = true;
            entity.isDead = false;

            this.entities.put(
                    name,
                    entity
            );
        }

        return entity;
    }

    private void applyLocalAnimation(
            CustomMorph morph,
            BodyPartModelData data,
            int frame)
    {
        if (morph == null ||
                morph.model == null)
        {
            return;
        }

        CustomMorph.ModelProperties pose =
                new CustomMorph.ModelProperties();

        pose.updateLimbs(
                morph.model,
                true
        );

        for (AnimationBone bone : data.getBones())
        {
            if (bone == null ||
                    bone.getName() == null)
            {
                continue;
            }

            ModelTransform target =
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

        morph.customPose =
                pose;
    }

    private BoneAttachment findAttachment(
            EntityActor actor,
            BodyPartsEditorController controller,
            String boneName,
            int frame)
    {
        if (controller == null ||
                boneName == null ||
                boneName.isEmpty())
        {
            return null;
        }

        /*
         * AnimatedMorph is the important case for the current actor
         * preview.  Its BOBJ bones contain the already evaluated
         * Emoticons animation, so this is the transform the attachment
         * must follow.
         */
        if (actor != null &&
                actor.getMorph() instanceof AnimatedMorph)
        {
            BOBJBone runtimeBone =
                    EmoticonsModelAccess.findBone(
                            (AnimatedMorph) actor.getMorph(),
                            boneName
                    );

            if (runtimeBone != null)
            {
                if (runtimeBone.mat != null)
                {
                    return new BoneAttachment(
                            new Matrix4f(runtimeBone.mat)
                    );
                }
            }
        }

        /*
         * Fallback to the editor skeleton for Blockbuster/other actors.
         * This still includes the BBS parent chain and keyframes.
         */
        List<AnimationBone> bones =
                controller.getActorBones();

        if (bones == null)
        {
            return null;
        }

        for (AnimationBone bone : bones)
        {
            if (bone != null &&
                    boneName.equals(bone.getName()))
            {
                AnimationTransform transform =
                        bone.getWorldTransformAt(frame);

                if (transform != null)
                {
                    return new BoneAttachment(transform);
                }

                return null;
            }
        }

        /*
         * Keep the attachment visible even if the actor skeleton
         * contains a bone without a transform.
         */
        return null;
    }

    private void renderAttached(
            Minecraft mc,
            EntityActor actor,
            EntityActor renderEntity,
            CustomMorph morph,
            BoneAttachment attachment,
            AnimationTransform modelTransform,
            float partialTicks)
    {
        GL11.glPushMatrix();

        attachment = attachment.withModelTransform(modelTransform);

        if (attachment.runtimeMatrix != null)
        {
            FloatBuffer matrixBuffer =
                    java.nio.ByteBuffer
                            .allocateDirect(16 * 4)
                            .order(java.nio.ByteOrder.nativeOrder())
                            .asFloatBuffer();

            Matrix4f matrix =
                    attachment.runtimeMatrix;

            matrixBuffer.put(matrix.m00).put(matrix.m10).put(matrix.m20).put(matrix.m30);
            matrixBuffer.put(matrix.m01).put(matrix.m11).put(matrix.m21).put(matrix.m31);
            matrixBuffer.put(matrix.m02).put(matrix.m12).put(matrix.m22).put(matrix.m32);
            matrixBuffer.put(matrix.m03).put(matrix.m13).put(matrix.m23).put(matrix.m33);
            matrixBuffer.flip();

            GL11.glMultMatrix(matrixBuffer);
        }
        else
        {
            /*
             * ModelTransform coordinates are Blockbuster model pixels.
             * The render engine uses 1/16 block units.
             */
            GL11.glTranslatef(
                    attachment.transform.getPositionX() / 16.0F,
                    attachment.transform.getPositionY() / 16.0F,
                    attachment.transform.getPositionZ() / 16.0F
            );

            GL11.glRotatef(
                    attachment.transform.getRotationZ(),
                    0.0F, 0.0F, 1.0F
            );
            GL11.glRotatef(
                    attachment.transform.getRotationY(),
                    0.0F, 1.0F, 0.0F
            );
            GL11.glRotatef(
                    attachment.transform.getRotationX(),
                    1.0F, 0.0F, 0.0F
            );
        }

        GL11.glScalef(
                attachment.transform.getScaleX(),
                attachment.transform.getScaleY(),
                attachment.transform.getScaleZ()
        );

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        RenderHelper.enableStandardItemLighting();

        /*
         * The temporary entity is rendered at the actor origin.
         * The attachment translation above moves it onto the
         * selected actor limb.
         */
        morph.render(
                renderEntity,
                actor.posX,
                actor.posY,
                actor.posZ,
                actor.rotationYaw,
                partialTicks
        );

        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
    }

    private static class BoneAttachment
    {
        private final AnimationTransform transform;
        private final Matrix4f runtimeMatrix;
        private final AnimationTransform modelTransform;

        private BoneAttachment(AnimationTransform transform)
        {
            this(transform, null, null);
        }

        private BoneAttachment(Matrix4f runtimeMatrix)
        {
            this(null, runtimeMatrix, null);
        }

        private BoneAttachment(
                AnimationTransform transform,
                Matrix4f runtimeMatrix,
                AnimationTransform modelTransform)
        {
            this.transform = transform;
            this.runtimeMatrix = runtimeMatrix;
            this.modelTransform = modelTransform;
        }

        private BoneAttachment withModelTransform(
                AnimationTransform modelTransform)
        {
            return new BoneAttachment(
                    this.transform,
                    this.runtimeMatrix,
                    modelTransform
            );
        }
    }
}

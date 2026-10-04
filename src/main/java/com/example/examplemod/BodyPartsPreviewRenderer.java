package com.example.examplemod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;

public class BodyPartsPreviewRenderer
{
    private final Map<String, CustomMorph> morphs =
            new HashMap<String, CustomMorph>();

    private final Map<String, EntityActor> entities =
            new HashMap<String, EntityActor>();

    private BodyPartsEditorController preparedController;
    private EntityActor preparedActor;
    private int preparedFrame;
    private boolean renderingAttachments;

    private net.minecraft.client.renderer.entity.layers.LayerRenderer<EntityActor> layer;

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
        this.preparedController = null;
        this.preparedActor = null;
        this.renderingAttachments = false;
    }

    public void prepare(
            BodyPartsEditorController controller,
            EntityActor actor,
            int frame)
    {
        this.preparedController = controller;
        this.preparedActor = actor;
        this.preparedFrame = frame;

        if (this.layer == null)
        {
            this.layer =
                    new net.minecraft.client.renderer.entity.layers.LayerRenderer<EntityActor>()
                    {
                        @Override
                        public void doRenderLayer(
                                EntityActor entity,
                                float limbSwing,
                                float limbSwingAmount,
                                float partialTicks,
                                float ageInTicks,
                                float netHeadYaw,
                                float headPitch,
                                float scale)
                        {
                            if (renderingAttachments ||
                                    entity != preparedActor ||
                                    preparedController == null)
                            {
                                return;
                            }

                            renderingAttachments = true;

                            try
                            {
                                renderInsideParentModel(
                                        entity,
                                        preparedController,
                                        preparedFrame,
                                        partialTicks,
                                        scale
                                );
                            }
                            finally
                            {
                                renderingAttachments = false;
                            }
                        }

                        @Override
                        public boolean shouldCombineTextures()
                        {
                            return false;
                        }
                    };
        }

        installLayer();
    }

    private void installLayer()
    {
        if (this.layer == null)
        {
            return;
        }

        mchorse.blockbuster_pack.client.render.RenderCustomActor renderer =
                mchorse.blockbuster.ClientProxy.actorRenderer;

        if (renderer == null)
        {
            return;
        }

        try
        {
            java.lang.reflect.Method addLayer =
                    net.minecraft.client.renderer.entity.RenderLivingBase.class
                            .getDeclaredMethod(
                                    "addLayer",
                                    net.minecraft.client.renderer.entity.layers.LayerRenderer.class
                            );

            addLayer.setAccessible(true);

            if (!isLayerInstalled(renderer))
            {
                addLayer.invoke(
                        renderer,
                        this.layer
                );
            }
        }
        catch (Exception exception)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to install Body Parts preview layer"
            );
            exception.printStackTrace();
        }
    }

    private boolean isLayerInstalled(
            net.minecraft.client.renderer.entity.RenderLivingBase renderer)
    {
        try
        {
            java.lang.reflect.Field field =
                    net.minecraft.client.renderer.entity.RenderLivingBase.class
                            .getDeclaredField("layerRenderers");

            field.setAccessible(true);

            java.util.List<?> layers =
                    (java.util.List<?>) field.get(renderer);

            return layers != null &&
                    layers.contains(this.layer);
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    private void renderInsideParentModel(
            EntityActor actor,
            BodyPartsEditorController controller,
            int frame,
            float partialTicks,
            float scale)
    {
        mchorse.blockbuster_pack.client.render.RenderCustomActor renderer =
                mchorse.blockbuster.ClientProxy.actorRenderer;

        if (renderer == null ||
                renderer.getMainModel() == null)
        {
            return;
        }

        if (!(renderer.getMainModel() instanceof
                mchorse.blockbuster.client.model.ModelCustom))
        {
            return;
        }

        mchorse.blockbuster.client.model.ModelCustom model =
                (mchorse.blockbuster.client.model.ModelCustom)
                        renderer.getMainModel();

        CustomMorph parentMorph =
                renderer.current;

        if (parentMorph == null)
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
                    !data.hasModel() ||
                    frame < data.getStartFrame() ||
                    frame > data.getEndFrame())
            {
                continue;
            }

            String boneName =
                    data.getAttachmentBoneName();

            if (boneName == null ||
                    boneName.isEmpty())
            {
                continue;
            }

            mchorse.blockbuster.client.model.ModelCustomRenderer limb =
                    model.get(boneName);

            if (limb == null)
            {
                continue;
            }

            CustomMorph morph =
                    getMorph(
                            data
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

            GL11.glPushMatrix();

            limb.postRender(
                    1.0F / 16.0F
            );

            applyGlobalTransform(
                    data.getGlobalTransform()
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

            morph.render(
                    actor,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0F,
                    partialTicks
            );

            RenderHelper.disableStandardItemLighting();

            GL11.glPopMatrix();

            renderer.current = parentMorph;
            renderer.setupModel(
                    actor,
                    partialTicks
            );
        }
    }

    private CustomMorph getMorph(
            BodyPartModelData data)
    {
        if (data == null ||
                data.getModelName() == null ||
                data.getModelName().isEmpty() ||
                data.getModelAccess() == null)
        {
            return null;
        }

        String name =
                data.getModelName();

        CustomMorph morph =
                this.morphs.get(name);

        if (morph == null)
        {
            morph =
                    new CustomMorph();

            /*
             * Use the exact ModelCustom object already resolved by
             * BlockbusterModelAccess.  In 1.12.2 CustomMorph.updateModel()
             * can resolve through a different model registry, while the
             * editor already has the render-side model that was selected.
             */
            morph.name =
                    name;

            morph.model =
                    data.getModelAccess().getModel();

            this.morphs.put(
                    name,
                    morph
            );
        }
        else
        {
            Object loadedModel =
                    data.getModelAccess().getModel();

            if (loadedModel != null &&
                    morph.model != loadedModel)
            {
                morph.model =
                        loadedModel;
            }

            morph.customPose = null;
        }

        if (morph.model == null)
        {
            return null;
        }

        return morph;
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

    private void applyGlobalTransform(
            AnimationTransform transform)
    {
        if (transform == null)
        {
            return;
        }

        GL11.glTranslatef(
                transform.getPositionX() / 16.0F,
                transform.getPositionY() / 16.0F,
                transform.getPositionZ() / 16.0F
        );

        GL11.glRotatef(
                transform.getRotationZ(),
                0.0F, 0.0F, 1.0F
        );
        GL11.glRotatef(
                transform.getRotationY(),
                0.0F, 1.0F, 0.0F
        );
        GL11.glRotatef(
                transform.getRotationX(),
                1.0F, 0.0F, 0.0F
        );

        GL11.glScalef(
                transform.getScaleX(),
                transform.getScaleY(),
                transform.getScaleZ()
        );
    }
}

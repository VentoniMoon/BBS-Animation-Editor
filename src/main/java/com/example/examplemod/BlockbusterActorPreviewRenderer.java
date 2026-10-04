package com.example.examplemod;

import java.util.List;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class BlockbusterActorPreviewRenderer
{
    private EntityActor actor;

    private String loadedMorphSignature = "";

    private double previousX;
    private double previousY;
    private double previousZ;

    private boolean hasPreviousPosition;

    private int lastPreviewFrame = -1;

    private int lastEmoticonsUpdateFrame = -1;

    private final BodyPartsPreviewRenderer bodyPartsPreviewRenderer =
            new BodyPartsPreviewRenderer();

    private BodyPartsEditorController bodyPartsController;


    /*
     * =========================================================
     * ACTOR CREATION
     * =========================================================
     */

    private EntityActor getOrCreateActor(Minecraft mc)
    {
        if (mc == null ||
                mc.world == null)
        {
            return null;
        }

        if (this.actor == null ||
                this.actor.world != mc.world ||
                this.actor.isDead)
        {
            this.actor =
                    new EntityActor(
                            mc.world
                    );

            this.actor.noClip = true;
            this.actor.isDead = false;

            this.loadedMorphSignature = "";

            this.previousX = 0.0D;
            this.previousY = 0.0D;
            this.previousZ = 0.0D;

            this.hasPreviousPosition = false;

            this.lastPreviewFrame = -1;
            this.lastEmoticonsUpdateFrame = -1;
        }

        return this.actor;
    }


    /*
     * =========================================================
     * MORPH
     * =========================================================
     */

    private void updateMorph(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            EntityActor entityActor)
    {
        if (mc == null ||
                actorData == null ||
                entityActor == null)
        {
            return;
        }

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }

        NBTTagCompound morphNBT =
                CharacterMorphResolver.getCurrentMorph(
                        actorData,
                        BlockbusterPreviewAnimationState.getFrame()
                );

        if (morphNBT == null)
        {
            return;
        }

        String signature =
                morphNBT.toString();

        if (signature.equals(
                this.loadedMorphSignature))
        {
            return;
        }

        try
        {
            AbstractMorph morph =
                    MorphManager.INSTANCE.morphFromNBT(
                            morphNBT.copy()
                    );

            if (morph != null)
            {
                if (EmoticonsActorPreviewRenderer
                        .isSupported(morph))
                {
                    EmoticonsActorPreviewRenderer
                            .prepareMorph(
                                    (AnimatedMorph) morph
                            );
                }

                entityActor.morph.setDirect(
                        morph
                );

                this.loadedMorphSignature =
                        signature;

                this.previousX = 0.0D;
                this.previousY = 0.0D;
                this.previousZ = 0.0D;

                this.hasPreviousPosition = false;

                this.lastPreviewFrame = -1;

                this.lastEmoticonsUpdateFrame = -1;
            }
        }
        catch (Exception exception)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to load actor morph"
            );

            exception.printStackTrace();
        }
    }


    /*
     * =========================================================
     * BBS ANIMATION POSE
     * =========================================================
     */

    private void applyAnimationPose(
            EntityActor entityActor)
    {
        if (entityActor == null)
        {
            return;
        }

        AbstractMorph currentMorph =
                entityActor.getMorph();

        if (!(currentMorph instanceof CustomMorph))
        {
            return;
        }

        CustomMorph customMorph =
                (CustomMorph) currentMorph;

        List<AnimationBoneSnapshot> snapshots =
                BlockbusterPreviewAnimationState
                        .getSnapshots();

        if (snapshots == null ||
                snapshots.isEmpty())
        {
            customMorph.customPose = null;
            return;
        }

        Minecraft mc =
                Minecraft.getMinecraft();

        if (mc == null)
        {
            return;
        }

        float partialTicks;

        if (BlockbusterPreviewAnimationState.isPlaying())
        {
            partialTicks =
                    mc.getRenderPartialTicks();
        }
        else
        {
            partialTicks =
                    0.0F;
        }

        ModelPose basePose =
                customMorph.getPose(
                        entityActor,
                        true,
                        partialTicks
                );

        if (basePose == null)
        {
            return;
        }

        CustomMorph.ModelProperties animationPose =
                customMorph.convertProp(
                        basePose.copy()
                );

        if (animationPose == null)
        {
            return;
        }

        for (AnimationBoneSnapshot snapshot :
                snapshots)
        {
            if (snapshot == null)
            {
                continue;
            }

            String boneName =
                    snapshot.getName();

            if (boneName == null ||
                    boneName.isEmpty())
            {
                continue;
            }

            ModelTransform transform =
                    animationPose.limbs.get(
                            boneName
                    );

            if (transform == null)
            {
                continue;
            }

            transform.translate[0] +=
                    snapshot.getPositionX();

            transform.translate[1] +=
                    snapshot.getPositionY();

            transform.translate[2] +=
                    snapshot.getPositionZ();

            transform.rotate[0] +=
                    snapshot.getRotationX();

            transform.rotate[1] +=
                    snapshot.getRotationY();

            transform.rotate[2] +=
                    snapshot.getRotationZ();

            transform.scale[0] *=
                    snapshot.getScaleX();

            transform.scale[1] *=
                    snapshot.getScaleY();

            transform.scale[2] *=
                    snapshot.getScaleZ();
        }

        customMorph.customPose =
                animationPose;
    }



    /*
     * =========================================================
     * CHAMELEON ANIMATION POSE
     * =========================================================
     *
     * Chameleon does not consume Blockbuster CustomMorph.customPose.
     * Its renderer resets ModelBone.current and then rebuilds the
     * current pose from ChameleonMorph.pose every render.
     *
     * Therefore changing ModelBone.current directly is not sufficient:
     * Chameleon immediately overwrites it in applyPose(). The editor
     * snapshot must be converted into a real AnimatedPose instead.
     */
    private void applyChameleonAnimationPose(
            AbstractMorph morph)
    {
        if (morph == null ||
                !morph.getClass().getName().endsWith(".ChameleonMorph"))
        {
            return;
        }

        List<AnimationBoneSnapshot> snapshots =
                BlockbusterPreviewAnimationState.getSnapshots();

        if (snapshots == null)
        {
            return;
        }

        try
        {
            Class<?> poseClass =
                    Class.forName(
                            "mchorse.chameleon.metamorph.pose.AnimatedPose"
                    );

            Class<?> transformClass =
                    Class.forName(
                            "mchorse.chameleon.metamorph.pose.AnimatedPoseTransform"
                    );

            Object pose =
                    poseClass.newInstance();

            java.lang.reflect.Field bonesField =
                    poseClass.getField("bones");

            Object poseBones =
                    bonesField.get(pose);

            if (!(poseBones instanceof java.util.Map))
            {
                return;
            }

            java.util.Map poseMap =
                    (java.util.Map) poseBones;

            java.lang.reflect.Constructor<?> transformConstructor =
                    transformClass.getConstructor(
                            String.class
                    );

            java.lang.reflect.Field xField =
                    transformClass.getField("x");

            java.lang.reflect.Field yField =
                    transformClass.getField("y");

            java.lang.reflect.Field zField =
                    transformClass.getField("z");

            java.lang.reflect.Field rotateXField =
                    transformClass.getField("rotateX");

            java.lang.reflect.Field rotateYField =
                    transformClass.getField("rotateY");

            java.lang.reflect.Field rotateZField =
                    transformClass.getField("rotateZ");

            java.lang.reflect.Field scaleXField =
                    transformClass.getField("scaleX");

            java.lang.reflect.Field scaleYField =
                    transformClass.getField("scaleY");

            java.lang.reflect.Field scaleZField =
                    transformClass.getField("scaleZ");

            final float degreesToRadians =
                    (float) (Math.PI / 180.0D);

            for (AnimationBoneSnapshot snapshot : snapshots)
            {
                if (snapshot == null ||
                        snapshot.getName() == null ||
                        snapshot.getName().isEmpty())
                {
                    continue;
                }

                Object transform =
                        transformConstructor.newInstance(
                                snapshot.getName()
                        );

                /*
                 * AnimationBone stores local editor translation.
                 * Chameleon AnimatedPoseTransform adds these values
                 * directly to ModelBone.initial.translate.
                 */
                xField.setFloat(
                        transform,
                        snapshot.getPositionX()
                );

                yField.setFloat(
                        transform,
                        snapshot.getPositionY()
                );

                zField.setFloat(
                        transform,
                        snapshot.getPositionZ()
                );

                /*
                 * Editor rotations are degrees.
                 * Chameleon AnimatedPoseTransform expects radians.
                 */
                rotateXField.setFloat(
                        transform,
                        snapshot.getRotationX()
                                * degreesToRadians
                );

                rotateYField.setFloat(
                        transform,
                        snapshot.getRotationY()
                                * degreesToRadians
                );

                rotateZField.setFloat(
                        transform,
                        snapshot.getRotationZ()
                                * degreesToRadians
                );

                scaleXField.setFloat(
                        transform,
                        snapshot.getScaleX()
                );

                scaleYField.setFloat(
                        transform,
                        snapshot.getScaleY()
                );

                scaleZField.setFloat(
                        transform,
                        snapshot.getScaleZ()
                );

                poseMap.put(
                        snapshot.getName(),
                        transform
                );
            }

            /*
             * The Chameleon renderer reads this public field from the
             * actual morph immediately before rendering its model.
             */
            java.lang.reflect.Field poseField =
                    morph.getClass().getField("pose");

            poseField.set(
                    morph,
                    pose
            );
        }
        catch (Throwable ignored)
        {
            /*
             * Chameleon is optional. Reflection failures must never
             * break the editor or the Blockbuster preview.
             */
        }
    }

    /*
     * =========================================================
     * MOVEMENT
     * =========================================================
     */

    private void updateMovementState(
            EntityActor entityActor,
            BlockbusterRecordFrame frame,
            int previewFrame)
    {
        if (entityActor == null ||
                frame == null)
        {
            return;
        }

        /*
         * =====================================================
         * SAME FRAME
         * =====================================================
         *
         * При паузе текущий кадр постоянно приходит снова.
         * Поэтому не изменяем состояние движения повторно.
         */

        if (this.lastPreviewFrame ==
                previewFrame)
        {
            if (!BlockbusterPreviewAnimationState.isPlaying())
            {
                entityActor.prevLimbSwingAmount =
                        entityActor.limbSwingAmount;
            }

            return;
        }

        /*
         * =====================================================
         * FIRST FRAME
         * =====================================================
         */

        if (!this.hasPreviousPosition)
        {
            this.previousX =
                    frame.getX();

            this.previousY =
                    frame.getY();

            this.previousZ =
                    frame.getZ();

            entityActor.prevLimbSwingAmount =
                    0.0F;

            entityActor.limbSwingAmount =
                    0.0F;

            entityActor.limbSwing =
                    0.0F;

            this.hasPreviousPosition =
                    true;

            this.lastPreviewFrame =
                    previewFrame;

            return;
        }

        /*
         * =====================================================
         * CALCULATE MOVEMENT
         * =====================================================
         */

        double dx =
                frame.getX() -
                        this.previousX;

        double dz =
                frame.getZ() -
                        this.previousZ;

        float movement =
                (float) Math.sqrt(
                        dx * dx +
                                dz * dz
                ) * 4.0F;

        if (movement > 1.0F)
        {
            movement = 1.0F;
        }

        /*
         * =====================================================
         * LIMB SWING
         * =====================================================
         *
         * В Minecraft 1.12.2 у используемого здесь EntityActor
         * нет поля prevLimbSwing.
         *
         * Поэтому работаем только с доступными:
         *
         * limbSwing
         * limbSwingAmount
         * prevLimbSwingAmount
         *
         * prevLimbSwingAmount сохраняет предыдущее значение
         * интенсивности движения ног.
         */

        entityActor.prevLimbSwingAmount =
                entityActor.limbSwingAmount;

        entityActor.limbSwingAmount +=
                (
                        movement -
                                entityActor.limbSwingAmount
                ) * 0.4F;

        entityActor.limbSwing +=
                entityActor.limbSwingAmount;

        /*
         * =====================================================
         * SAVE POSITION
         * =====================================================
         */

        this.previousX =
                frame.getX();

        this.previousY =
                frame.getY();

        this.previousZ =
                frame.getZ();

        this.lastPreviewFrame =
                previewFrame;
    }


    /*
     * =========================================================
     * EMOTICONS
     * =========================================================
     */

    private void updateEmoticonsAnimation(
            EntityActor entityActor,
            int previewFrame)
    {
        if (entityActor == null)
        {
            return;
        }

        if (!BlockbusterPreviewAnimationState.isPlaying())
        {
            return;
        }

        AbstractMorph currentMorph =
                entityActor.getMorph();

        if (!(currentMorph instanceof AnimatedMorph))
        {
            return;
        }

        if (this.lastEmoticonsUpdateFrame ==
                previewFrame)
        {
            return;
        }

        AnimatedMorph animatedMorph =
                (AnimatedMorph) currentMorph;

        EmoticonsActorPreviewRenderer
                .updateMorph(
                        animatedMorph,
                        entityActor
                );

        this.lastEmoticonsUpdateFrame =
                previewFrame;
    }


    /*
     * =========================================================
     * ENTITY TICK STATE
     * =========================================================
     */

    private void updateEntityTickState(
            EntityActor entityActor,
            int previewFrame)
    {
        if (entityActor == null)
        {
            return;
        }

        entityActor.ticksExisted =
                Math.max(
                        0,
                        previewFrame
                );
    }


    public void setBodyPartsController(
            BodyPartsEditorController controller)
    {
        this.bodyPartsController = controller;
    }

    /*
     * =========================================================
     * DRAW ACTOR
     * =========================================================
     */

    public void drawActor(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            BlockbusterRecordFrame frame,
            EditorCamera camera,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight)
    {
        if (mc == null ||
                mc.world == null ||
                actorData == null ||
                frame == null ||
                camera == null ||
                viewportWidth <= 0 ||
                viewportHeight <= 0)
        {
            return;
        }

        EntityActor entityActor =
                getOrCreateActor(mc);

        if (entityActor == null)
        {
            return;
        }

        updateMorph(
                mc,
                actorData,
                entityActor
        );

        CharacterBodyPartPreviewState.set(
                actorData,
                BlockbusterPreviewAnimationState.getFrame()
        );

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (sceneActor == null)
        {
            return;
        }

        if (!sceneActor.isEnabled() ||
                sceneActor.isInvisible())
        {
            this.hasPreviousPosition = false;
            this.lastPreviewFrame = -1;
            this.lastEmoticonsUpdateFrame = -1;

            AbstractMorph morph =
                    entityActor.getMorph();

            if (morph instanceof CustomMorph)
            {
                ((CustomMorph) morph).customPose = null;
            }

            EmoticonsPreviewAnimationState.clear();

            return;
        }

        entityActor.isDead = false;
        entityActor.noClip = true;

        boolean playing =
                BlockbusterPreviewAnimationState.isPlaying();

        /*
         * =====================================================
         * PARTIAL TICKS
         * =====================================================
         *
         * При воспроизведении используем Minecraft render
         * partial ticks.
         *
         * При паузе строго 0, чтобы актёр не продолжал
         * визуально интерполироваться между состояниями.
         */

        float actorPartialTicks;

        if (playing)
        {
            actorPartialTicks =
                    mc.getRenderPartialTicks();
        }
        else
        {
            actorPartialTicks =
                    0.0F;
        }

        int previewFrame =
                BlockbusterPreviewAnimationState
                        .getFrame();

        if (previewFrame < 0)
        {
            previewFrame = 0;
        }

        /*
         * =====================================================
         * ENTITY TICK
         * =====================================================
         */

        if (playing)
        {
            updateEntityTickState(
                    entityActor,
                    previewFrame
            );
        }

        /*
         * =====================================================
         * POSITION
         * =====================================================
         */

        entityActor.posX =
                frame.getX();

        entityActor.posY =
                frame.getY();

        entityActor.posZ =
                frame.getZ();

        if (playing)
        {
            if (this.hasPreviousPosition)
            {
                entityActor.prevPosX =
                        this.previousX;

                entityActor.prevPosY =
                        this.previousY;

                entityActor.prevPosZ =
                        this.previousZ;
            }
            else
            {
                entityActor.prevPosX =
                        entityActor.posX;

                entityActor.prevPosY =
                        entityActor.posY;

                entityActor.prevPosZ =
                        entityActor.posZ;
            }
        }
        else
        {
            /*
             * На паузе prevPos и pos должны быть одинаковыми.
             * Иначе Minecraft может продолжать интерполяцию
             * положения даже при остановленном Timeline.
             */

            entityActor.prevPosX =
                    entityActor.posX;

            entityActor.prevPosY =
                    entityActor.posY;

            entityActor.prevPosZ =
                    entityActor.posZ;
        }

        /*
         * =====================================================
         * MOVEMENT
         * =====================================================
         */

        updateMovementState(
                entityActor,
                frame,
                previewFrame
        );

        /*
         * При паузе полностью фиксируем интенсивность
         * движения ног.
         */

        if (!playing)
        {
            entityActor.prevLimbSwingAmount =
                    entityActor.limbSwingAmount;
        }

        /*
         * =====================================================
         * ROTATION
         * =====================================================
         *
         * Сохраняем предыдущее состояние поворота перед
         * установкой нового кадра.
         *
         * Это позволяет Minecraft корректно интерполировать
         * вращение во время воспроизведения.
         */

        float newYaw =
                frame.getYaw();

        float newPitch =
                frame.getPitch();

        float newYawHead =
                frame.getYawHead();

        float newBodyYaw;

        if (frame.hasBodyYaw())
        {
            newBodyYaw =
                    frame.getBodyYaw();
        }
        else
        {
            newBodyYaw =
                    newYaw;
        }


        /*
         * =====================================================
         * PAUSED
         * =====================================================
         *
         * На паузе никакой интерполяции быть не должно.
         */

        if (!playing)
        {
            entityActor.rotationYaw =
                    newYaw;

            entityActor.prevRotationYaw =
                    newYaw;

            entityActor.rotationPitch =
                    newPitch;

            entityActor.prevRotationPitch =
                    newPitch;

            entityActor.rotationYawHead =
                    newYawHead;

            entityActor.prevRotationYawHead =
                    newYawHead;

            entityActor.renderYawOffset =
                    newBodyYaw;

            entityActor.prevRenderYawOffset =
                    newBodyYaw;
        }
        else
        {
            /*
             * =================================================
             * PLAYING
             * =================================================
             *
             * Сначала сохраняем старое значение.
             */

            entityActor.prevRotationYaw =
                    entityActor.rotationYaw;

            entityActor.prevRotationPitch =
                    entityActor.rotationPitch;

            entityActor.prevRotationYawHead =
                    entityActor.rotationYawHead;

            entityActor.prevRenderYawOffset =
                    entityActor.renderYawOffset;


            /*
             * Затем устанавливаем состояние нового кадра.
             */

            entityActor.rotationYaw =
                    newYaw;

            entityActor.rotationPitch =
                    newPitch;

            entityActor.rotationYawHead =
                    newYawHead;

            entityActor.renderYawOffset =
                    newBodyYaw;
        }

        /*
         * =====================================================
         * ENTITY STATE
         * =====================================================
         */

        entityActor.setSneaking(
                frame.isSneaking()
        );

        entityActor.setSprinting(
                frame.isSprinting()
        );

        entityActor.onGround =
                frame.isGround();

        entityActor.fallDistance =
                frame.getFallDistance();

        /*
         * =====================================================
         * SWING
         * =====================================================
         */

        entityActor.prevSwingProgress =
                entityActor.swingProgress;

        entityActor.swingProgress =
                0.0F;

        /*
         * =====================================================
         * CUSTOM ANIMATION POSE
         * =====================================================
         */

        applyAnimationPose(
                entityActor
        );

        /*
         * ChameleonMorph has its own ModelBone hierarchy and does not
         * use CustomMorph.customPose. Apply the same editor snapshots
         * directly to that hierarchy after the normal pose pass.
         */
        applyChameleonAnimationPose(
                entityActor.getMorph()
        );

        /*
         * =====================================================
         * EMOTICONS ANIMATION
         * =====================================================
         */

        updateEmoticonsAnimation(
                entityActor,
                previewFrame
        );

        /*
         * =====================================================
         * RENDER MANAGER
         * =====================================================
         */

        RenderManager renderManager =
                mc.getRenderManager();

        if (renderManager == null)
        {
            return;
        }

        double oldViewerPosX =
                renderManager.viewerPosX;

        double oldViewerPosY =
                renderManager.viewerPosY;

        double oldViewerPosZ =
                renderManager.viewerPosZ;

        /*
         * The Preview framebuffer is full-screen sized, but the actor is
         * rendered into the Preview viewport inside that framebuffer.
         *
         * The old code used mc.displayWidth/displayHeight for the
         * perspective aspect ratio.  That is wrong when the editor
         * Preview is only a rectangle of the screen: the GL viewport is
         * previewWidth x previewHeight, while the projection was using
         * the whole display aspect.  The Gizmo uses the Preview aspect,
         * so the two projections no longer described the same camera.
         *
         * Keep framebuffer dimensions out of the actor projection.
         */
        int previewWidth =
                width;

        int previewHeight =
                height;

        if (previewWidth <= 0 ||
                previewHeight <= 0)
        {
            return;
        }

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        /*
         * =====================================================
         * SAVE OPENGL STATE
         * =====================================================
         */

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

        /*
         * =====================================================
         * SAVE MATRICES
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPushMatrix();

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPushMatrix();

        /*
         * =====================================================
         * RENDER MANAGER CAMERA
         * =====================================================
         */

        renderManager.viewerPosX =
                0.0D;

        renderManager.viewerPosY =
                0.0D;

        renderManager.viewerPosZ =
                0.0D;

        /*
         * =====================================================
         * PROJECTION
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glLoadIdentity();

        float aspect =
                (float) previewWidth /
                        (float) previewHeight;

        GLU.gluPerspective(
                60.0F,
                aspect,
                0.05F,
                500.0F
        );

        /*
         * =====================================================
         * MODELVIEW
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glLoadIdentity();

        GL11.glRotatef(
                -camera.getPitch(),
                1.0F,
                0.0F,
                0.0F
        );

        GL11.glRotatef(
                -camera.getYaw(),
                0.0F,
                1.0F,
                0.0F
        );

        double cameraX =
                camera.getCameraX();

        double cameraY =
                camera.getCameraY();

        double cameraZ =
                camera.getCameraZ();

        GL11.glTranslated(
                -cameraX,
                -cameraY,
                -cameraZ
        );

        /*
         * =====================================================
         * ACTOR RENDER STATE
         * =====================================================
         */

        GlStateManager.enableDepth();

        GlStateManager.depthFunc(
                GL11.GL_LEQUAL
        );

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

        GlStateManager.enableLighting();

        RenderHelper.enableStandardItemLighting();

        /*
         * =====================================================
         * ACTOR RENDER
         * =====================================================
         */

        /*
         * Body Parts must be rendered as a real RenderCustomModel layer.
         * This gives us the same GL transform context used by
         * Blockbuster's own LayerBodyPart.
         */
        if (this.bodyPartsController != null)
        {
            this.bodyPartsPreviewRenderer.prepare(
                    this.bodyPartsController,
                    entityActor,
                    previewFrame
            );
        }

        renderManager.renderEntity(
                entityActor,
                entityActor.posX,
                entityActor.posY,
                entityActor.posZ,
                entityActor.rotationYaw,
                actorPartialTicks,
                true
        );

        /*
         * =====================================================
         * LIGHTING
         * =====================================================
         */

        RenderHelper.disableStandardItemLighting();

        /*
         * =====================================================
         * RESTORE MATRICES
         * =====================================================
         */

        GL11.glMatrixMode(
                GL11.GL_MODELVIEW
        );

        GL11.glPopMatrix();

        GL11.glMatrixMode(
                GL11.GL_PROJECTION
        );

        GL11.glPopMatrix();

        /*
         * =====================================================
         * RESTORE RENDER MANAGER
         * =====================================================
         */

        renderManager.viewerPosX =
                oldViewerPosX;

        renderManager.viewerPosY =
                oldViewerPosY;

        renderManager.viewerPosZ =
                oldViewerPosZ;

        /*
         * =====================================================
         * RESTORE OPENGL STATE
         * =====================================================
         *
         * После glPopAttrib() намеренно не вызываем
         * enable/disable GL-состояний.
         */

        GL11.glPopAttrib();

        /*
         * =====================================================
         * RESTORE MATRIX MODE
         * =====================================================
         */

        GL11.glMatrixMode(
                oldMatrixMode
        );
    }


    /*
     * =========================================================
     * GET ACTOR
     * =========================================================
     */

    public EntityActor getActor()
    {
        return this.actor;
    }


    /*
     * =========================================================
     * RESET
     * =========================================================
     */

    public void reset()
    {
        if (this.actor != null)
        {
            this.actor.setDead();
        }

        this.actor = null;

        this.loadedMorphSignature = "";

        this.previousX = 0.0D;
        this.previousY = 0.0D;
        this.previousZ = 0.0D;

        this.hasPreviousPosition = false;

        this.lastPreviewFrame = -1;

        this.lastEmoticonsUpdateFrame = -1;

        this.bodyPartsPreviewRenderer.clear();

        EmoticonsPreviewAnimationState.clear();
    }
}
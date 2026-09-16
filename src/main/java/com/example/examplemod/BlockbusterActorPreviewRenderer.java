package com.example.examplemod;

import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.metamorph.api.MorphManager;
import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.nbt.NBTTagCompound;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class BlockbusterActorPreviewRenderer
{
    private EntityActor actor;

    private String loadedMorphSignature = "";

    /*
     * ---------------------------------------------------------
     * LIMB ANIMATION STATE
     * ---------------------------------------------------------
     *
     * Эти значения нужны для процедурной анимации
     * движения конечностей Blockbuster ModelCustom.
     */

    private double previousX;
    private double previousY;
    private double previousZ;

    private boolean hasPreviousPosition;

    private float limbSwing;


    /*
     * ---------------------------------------------------------
     * ACTOR
     * ---------------------------------------------------------
     */

    private EntityActor getOrCreateActor(
            Minecraft mc)
    {
        if (
                mc == null ||
                        mc.world == null
        )
        {
            return null;
        }

        if (
                this.actor == null ||
                        this.actor.world != mc.world ||
                        this.actor.isDead
        )
        {
            this.actor =
                    new EntityActor(
                            mc.world
                    );

            this.actor.noClip = true;
            this.actor.isDead = false;

            this.loadedMorphSignature = "";

            /*
             * После создания EntityActor
             * предыдущего положения ещё нет.
             */
            this.previousX = 0.0D;
            this.previousY = 0.0D;
            this.previousZ = 0.0D;

            this.hasPreviousPosition = false;

            this.limbSwing = 0.0F;
        }

        return this.actor;
    }


    /*
     * ---------------------------------------------------------
     * MORPH
     * ---------------------------------------------------------
     */

    private void updateMorph(
            Minecraft mc,
            BlockbusterSceneActorData actorData,
            EntityActor entityActor)
    {
        if (
                mc == null ||
                        actorData == null ||
                        entityActor == null
        )
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
                sceneActor.getMorph();

        if (morphNBT == null)
        {
            return;
        }

        String signature =
                morphNBT.toString();

        if (
                signature.equals(
                        this.loadedMorphSignature
                )
        )
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
                entityActor.morph.setDirect(
                        morph
                );

                this.loadedMorphSignature =
                        signature;
            }
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }
    }


    /*
     * ---------------------------------------------------------
     * DRAW ACTOR
     * ---------------------------------------------------------
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
        if (
                mc == null ||
                        mc.world == null ||
                        actorData == null ||
                        frame == null ||
                        camera == null ||
                        viewportWidth <= 0 ||
                        viewportHeight <= 0
        )
        {
            return;
        }

        EntityActor entityActor =
                getOrCreateActor(mc);

        if (entityActor == null)
        {
            return;
        }

        /*
         * -----------------------------------------------------
         * MORPH
         * -----------------------------------------------------
         */

        updateMorph(
                mc,
                actorData,
                entityActor
        );

        BlockbusterSceneActor sceneActor =
                actorData.getActor();

        if (
                sceneActor == null ||
                        !sceneActor.isEnabled() ||
                        sceneActor.isInvisible()
        )
        {
            /*
             * Даже если актёр невидим,
             * сбрасываем состояние движения,
             * чтобы при повторном появлении
             * не было скачка limbSwing.
             */
            this.hasPreviousPosition = false;
            this.limbSwing = 0.0F;

            return;
        }


        /*
         * =====================================================
         * ACTOR POSITION
         * =====================================================
         */

        entityActor.isDead = false;
        entityActor.noClip = true;

        entityActor.posX =
                frame.getX();

        entityActor.posY =
                frame.getY();

        entityActor.posZ =
                frame.getZ();

        /*
         * -----------------------------------------------------
         * PREVIOUS POSITION
         * -----------------------------------------------------
         *
         * Здесь намеренно сохраняем предыдущее положение
         * EntityActor отдельно от текущего.
         */

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
            /*
             * Первый отображаемый кадр.
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
         * LIMB SWING
         * =====================================================
         *
         * Blockbuster ModelCustom использует:
         *
         * limbSwing
         * limbSwingAmount
         *
         * для процедурного движения рук и ног.
         *
         * Record не содержит готовых углов ног/рук,
         * поэтому восстанавливаем фазу шага
         * из фактического движения Actor между кадрами.
         */

        if (this.hasPreviousPosition)
        {
            double dx =
                    frame.getX() -
                            this.previousX;

            double dy =
                    frame.getY() -
                            this.previousY;

            double dz =
                    frame.getZ() -
                            this.previousZ;

            /*
             * Используем только горизонтальное
             * перемещение.
             *
             * Прыжок / падение не должны
             * превращаться в шаг.
             */
            double horizontalDistance =
                    Math.sqrt(
                            dx * dx +
                                    dz * dz
                    );

            /*
             * Сила движения.
             *
             * Коэффициент подобран так,
             * чтобы нормальная скорость записи
             * давала заметную анимацию ног,
             * но не превращала персонажа
             * в "ветряную мельницу".
             */
            float swingAmount =
                    (float)
                            Math.min(
                                    1.0D,
                                    horizontalDistance * 4.0D
                            );

            /*
             * Наращиваем фазу движения.
             */
            this.limbSwing +=
                    (float)
                            horizontalDistance * 1.5F;

            /*
             * Не даём float бесконечно расти
             * при длинном воспроизведении.
             */
            if (
                    this.limbSwing >
                            100000.0F
            )
            {
                this.limbSwing -=
                        100000.0F;
            }

            if (
                    this.limbSwing <
                            -100000.0F
            )
            {
                this.limbSwing +=
                        100000.0F;
            }

            entityActor.limbSwing =
                    this.limbSwing;

            entityActor.limbSwingAmount =
                    swingAmount;
        }
        else
        {
            /*
             * Первый кадр:
             * персонаж стоит спокойно.
             */
            entityActor.limbSwing =
                    this.limbSwing;

            entityActor.limbSwingAmount =
                    0.0F;

            this.hasPreviousPosition =
                    true;
        }

        /*
         * Запоминаем текущую позицию
         * для следующего кадра.
         */
        this.previousX =
                frame.getX();

        this.previousY =
                frame.getY();

        this.previousZ =
                frame.getZ();


        /*
         * =====================================================
         * ROTATION
         * =====================================================
         */

        entityActor.rotationYaw =
                frame.getYaw();

        entityActor.rotationPitch =
                frame.getPitch();

        entityActor.prevRotationYaw =
                entityActor.rotationYaw;

        entityActor.prevRotationPitch =
                entityActor.rotationPitch;

        entityActor.rotationYawHead =
                frame.getYawHead();

        entityActor.prevRotationYawHead =
                entityActor.rotationYawHead;


        /*
         * Body yaw.
         */

        if (frame.hasBodyYaw())
        {
            entityActor.renderYawOffset =
                    frame.getBodyYaw();

            entityActor.prevRenderYawOffset =
                    frame.getBodyYaw();
        }
        else
        {
            entityActor.renderYawOffset =
                    frame.getYaw();

            entityActor.prevRenderYawOffset =
                    frame.getYaw();
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
         * GUI → DISPLAY COORDINATES
         * =====================================================
         */

        ScaledResolution resolution =
                new ScaledResolution(mc);

        int scaledWidth =
                resolution.getScaledWidth();

        int scaledHeight =
                resolution.getScaledHeight();

        float scaleX =
                (float) mc.displayWidth /
                        (float) scaledWidth;

        float scaleY =
                (float) mc.displayHeight /
                        (float) scaledHeight;

        int realViewportX =
                Math.round(
                        viewportX * scaleX
                );

        int realViewportY =
                mc.displayHeight
                        - Math.round(
                        (viewportY + viewportHeight)
                                * scaleY
                );

        int realViewportWidth =
                Math.round(
                        viewportWidth * scaleX
                );

        int realViewportHeight =
                Math.round(
                        viewportHeight * scaleY
                );


        /*
         * =====================================================
         * SAVE OPENGL
         * =====================================================
         */

        int oldMatrixMode =
                GL11.glGetInteger(
                        GL11.GL_MATRIX_MODE
                );

        GL11.glPushAttrib(
                GL11.GL_ALL_ATTRIB_BITS
        );

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
         * VIEWPORT + SCISSOR
         * =====================================================
         */

        GL11.glViewport(
                realViewportX,
                realViewportY,
                realViewportWidth,
                realViewportHeight
        );

        GL11.glEnable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glScissor(
                realViewportX,
                realViewportY,
                realViewportWidth,
                realViewportHeight
        );


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
                (float) viewportWidth /
                        (float) viewportHeight;

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


        /*
         * Та же камера,
         * что используется Preview.
         */

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

        GL11.glTranslated(
                -camera.getTargetX(),
                -camera.getTargetY(),
                -camera.getTargetZ()
        );

        GL11.glTranslated(
                0.0D,
                0.0D,
                -camera.getDistance()
        );


        /*
         * =====================================================
         * RENDER POSITION
         * =====================================================
         */

        RenderManager renderManager =
                mc.getRenderManager();

        renderManager.setRenderPosition(
                camera.getCameraX(),
                camera.getCameraY(),
                camera.getCameraZ()
        );


        /*
         * =====================================================
         * RENDER STATE
         * =====================================================
         */

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();

        GlStateManager.disableLighting();

        GlStateManager.color(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );


        /*
         * =====================================================
         * RENDER ACTOR
         * =====================================================
         */

        renderManager.renderEntity(
                entityActor,
                entityActor.posX,
                entityActor.posY,
                entityActor.posZ,
                entityActor.rotationYaw,
                0.0F,
                true
        );


        /*
         * =====================================================
         * RESTORE
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

        GL11.glMatrixMode(
                oldMatrixMode
        );

        GL11.glDisable(
                GL11.GL_SCISSOR_TEST
        );

        GL11.glPopAttrib();


        /*
         * =====================================================
         * GUI SAFETY
         * =====================================================
         */

        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();

        GL11.glViewport(
                0,
                0,
                mc.displayWidth,
                mc.displayHeight
        );
    }


    /*
     * ---------------------------------------------------------
     * ACCESS
     * ---------------------------------------------------------
     */

    public EntityActor getActor()
    {
        return this.actor;
    }


    /*
     * ---------------------------------------------------------
     * RESET
     * ---------------------------------------------------------
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

        this.limbSwing = 0.0F;
    }
}
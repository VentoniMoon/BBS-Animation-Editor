package com.example.examplemod;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

public class AnimationPreview
{
    private int x;
    private int y;
    private int width;
    private int height;

    /*
     * World position of the actor when the preview
     * reference was established.
     */
    private double referenceX;
    private double referenceY;
    private double referenceZ;

    /*
     * Temporary scale used by the transitional
     * 2D preview.
     */
    private static final float WORLD_TO_SCREEN = 8.0F;

    public AnimationPreview()
    {
        this.x = 0;
        this.y = 0;
        this.width = 300;
        this.height = 300;

        this.referenceX = 0.0D;
        this.referenceY = 0.0D;
        this.referenceZ = 0.0D;
    }

    public void setBounds(
            int x,
            int y,
            int width,
            int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setReferencePosition(
            double x,
            double y,
            double z)
    {
        this.referenceX = x;
        this.referenceY = y;
        this.referenceZ = z;
    }

    /**
     * Рисует временное 2D-представление
     * всех костей текущего Blockbuster-скелета.
     *
     * Количество костей больше не фиксировано.
     */
    public void draw(
            Minecraft mc,
            ActorPose actorPose,
            List<AnimationBone> bones,
            int currentFrame)
    {
        if (mc == null || actorPose == null)
        {
            return;
        }

        /*
         * Background.
         */

        Gui.drawRect(
                this.x,
                this.y,
                this.x + this.width,
                this.y + this.height,
                0xFF151619
        );

        mc.fontRenderer.drawString(
                "PREVIEW",
                this.x + 8,
                this.y + 8,
                0xFFAAAAAA
        );

        /*
         * Preview origin.
         */

        int centerX =
                this.x + this.width / 2;

        int centerY =
                this.y + this.height / 2;

        /*
         * Actor movement relative to the
         * reference position.
         */

        float actorOffsetX =
                (float)
                        (
                                actorPose.getX()
                                        - this.referenceX
                        )
                        * WORLD_TO_SCREEN;

        float actorOffsetY =
                (float)
                        (
                                actorPose.getY()
                                        - this.referenceY
                        )
                        * WORLD_TO_SCREEN;

        float actorOffsetZ =
                (float)
                        (
                                actorPose.getZ()
                                        - this.referenceZ
                        )
                        * WORLD_TO_SCREEN;

        int actorX =
                (int)
                        (
                                centerX
                                        + actorOffsetX
                        );

        int actorY =
                (int)
                        (
                                centerY
                                        - actorOffsetY
                                        - actorOffsetZ
                        );

        /*
         * Draw every bone dynamically.
         *
         * We intentionally do not assume
         * any specific bone names.
         */
        if (bones == null)
        {
            return;
        }

        for (
                AnimationBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            drawBone(
                    bone,
                    currentFrame,
                    actorX,
                    actorY
            );
        }
    }

    private void drawBone(
            AnimationBone bone,
            int currentFrame,
            int originX,
            int originY)
    {
        if (bone == null)
        {
            return;
        }

        AnimationTransform pivot =
                bone.getWorldPivotAt(
                        currentFrame
                );

        if (pivot == null)
        {
            return;
        }

        AnimationTransform transform =
                bone.getWorldTransformAt(
                        currentFrame
                );

        if (transform == null)
        {
            return;
        }

        float x =
                originX
                        + pivot.getPositionX();

        float y =
                originY
                        + pivot.getPositionY();

        float scaleX =
                transform.getScaleX();

        float scaleY =
                transform.getScaleY();

        float rotation =
                transform.getRotationZ();

        /*
         * Temporary generic bone size.
         *
         * The real Blockbuster renderer will later
         * obtain the actual geometry from ModelCustom.
         */
        int boneWidth = 12;
        int boneHeight = 24;

        float finalWidth =
                Math.max(
                        2.0F,
                        boneWidth * scaleX
                );

        float finalHeight =
                Math.max(
                        2.0F,
                        boneHeight * scaleY
                );

        /*
         * Different shade for root bones.
         */
        int color =
                bone.getParent() == null
                        ? 0xFFE0E0E0
                        : 0xFFAAAAAA;

        GlStateManager.pushMatrix();

        GlStateManager.translate(
                x,
                y,
                0.0D
        );

        GlStateManager.rotate(
                rotation,
                0.0F,
                0.0F,
                1.0F
        );

        Gui.drawRect(
                (int) (-finalWidth / 2.0F),
                (int) (-finalHeight / 2.0F),
                (int) (finalWidth / 2.0F),
                (int) (finalHeight / 2.0F),
                color
        );

        GlStateManager.popMatrix();

        /*
         * Pivot point.
         */

        Gui.drawRect(
                (int) x - 2,
                (int) y - 2,
                (int) x + 2,
                (int) y + 2,
                0xFFFF5555
        );

        /*
         * Rotation indicator.
         */

        double angle =
                Math.toRadians(
                        rotation
                );

        int indicatorLength = 12;

        int indicatorX =
                (int)
                        (
                                x
                                        + Math.cos(angle)
                                        * indicatorLength
                        );

        int indicatorY =
                (int)
                        (
                                y
                                        + Math.sin(angle)
                                        * indicatorLength
                        );

        Gui.drawRect(
                indicatorX - 2,
                indicatorY - 2,
                indicatorX + 2,
                indicatorY + 2,
                0xFFFFAA00
        );
    }
}
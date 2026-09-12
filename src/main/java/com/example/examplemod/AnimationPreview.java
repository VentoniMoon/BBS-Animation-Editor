package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

public class AnimationPreview
{
    private int x;
    private int y;
    private int width;
    private int height;

    public AnimationPreview()
    {
        this.x = 0;
        this.y = 0;
        this.width = 300;
        this.height = 300;
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

    public void draw(
            Minecraft mc,
            AnimationBone body,
            AnimationBone head,
            AnimationBone armLeft,
            AnimationBone armRight,
            AnimationBone legLeft,
            AnimationBone legRight,
            int currentFrame)
    {
        /*
         * Background
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
         * Model origin.
         *
         * This belongs to Anchor.
         */

        int centerX =
                this.x + this.width / 2;

        int centerY =
                this.y + this.height / 2;

        /*
         * Body
         */

        drawBone(
                body,
                currentFrame,
                centerX,
                centerY,
                32,
                50,
                0xFFAAAAAA
        );

        /*
         * Child bones.
         *
         * Their positions are calculated from
         * the parent hierarchy.
         */

        drawBone(
                head,
                currentFrame,
                centerX,
                centerY,
                24,
                24,
                0xFFE0E0E0
        );

        drawBone(
                armLeft,
                currentFrame,
                centerX,
                centerY,
                18,
                42,
                0xFFAAAAAA
        );

        drawBone(
                armRight,
                currentFrame,
                centerX,
                centerY,
                18,
                42,
                0xFFAAAAAA
        );

        drawBone(
                legLeft,
                currentFrame,
                centerX,
                centerY,
                18,
                45,
                0xFF999999
        );

        drawBone(
                legRight,
                currentFrame,
                centerX,
                centerY,
                18,
                45,
                0xFF999999
        );
    }

    private void drawBone(
            AnimationBone bone,
            int currentFrame,
            int originX,
            int originY,
            int boneWidth,
            int boneHeight,
            int color)
    {
        /*
         * Position of the attachment point in world space.
         */

        AnimationTransform pivot =
                bone.getWorldPivotAt(
                        currentFrame
                );

        /*
         * Complete world transform.
         */

        AnimationTransform transform =
                bone.getWorldTransformAt(
                        currentFrame
                );

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
         * Draw the bone around its attachment point.
         */

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
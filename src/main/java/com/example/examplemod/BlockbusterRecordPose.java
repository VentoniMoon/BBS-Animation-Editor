package com.example.examplemod;

/**
 * Базовая поза актёра, полученная непосредственно
 * из одного кадра Blockbuster Record.
 *
 * ВАЖНО:
 *
 * Этот класс НЕ содержит пользовательскую анимацию.
 * Он представляет только состояние актёра из BBS Record.
 *
 * Пользовательские изменения накладываются позднее
 * через AnimationBone.
 */
public class BlockbusterRecordPose
{
    private double x;
    private double y;
    private double z;

    private float yaw;
    private float pitch;

    public BlockbusterRecordPose()
    {
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;

        this.yaw = 0.0F;
        this.pitch = 0.0F;
    }

    public BlockbusterRecordPose(
            double x,
            double y,
            double z,
            float yaw,
            float pitch)
    {
        this.x = x;
        this.y = y;
        this.z = z;

        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getX()
    {
        return this.x;
    }

    public double getY()
    {
        return this.y;
    }

    public double getZ()
    {
        return this.z;
    }

    public float getYaw()
    {
        return this.yaw;
    }

    public float getPitch()
    {
        return this.pitch;
    }

    public void setPosition(
            double x,
            double y,
            double z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setRotation(
            float yaw,
            float pitch)
    {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Применяет базовую позу Record к ActorPose.
     *
     * Пользовательские AnimationBone здесь
     * НЕ учитываются.
     */
    public void applyTo(
            ActorPose actorPose)
    {
        if (actorPose == null)
        {
            return;
        }

        actorPose.setPosition(
                this.x,
                this.y,
                this.z
        );

        actorPose.setRotation(
                this.yaw,
                this.pitch
        );
    }
}
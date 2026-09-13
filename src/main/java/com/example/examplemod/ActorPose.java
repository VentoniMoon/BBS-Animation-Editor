package com.example.examplemod;

/**
 * Итоговое состояние актёра на одном кадре.
 *
 * ActorPose является промежуточным слоем между:
 *
 * BlockbusterRecord
 * и
 * AnimationBone
 *
 * Record предоставляет базовое состояние актёра,
 * а AnimationBone позднее будет добавлять
 * пользовательские изменения поверх него.
 */
public class ActorPose
{
    private double x;

    private double y;

    private double z;

    private float yaw;

    private float pitch;

    public ActorPose()
    {
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;

        this.yaw = 0.0F;
        this.pitch = 0.0F;
    }

    public ActorPose(
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

    public void addPosition(
            double x,
            double y,
            double z)
    {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    public void addRotation(
            float yaw,
            float pitch)
    {
        this.yaw += yaw;
        this.pitch += pitch;
    }
}
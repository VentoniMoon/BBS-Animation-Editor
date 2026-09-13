package com.example.examplemod;

/**
 * Итоговое пространственное состояние актёра
 * на определённом кадре редактора.
 *
 * Сюда попадает базовое состояние из Blockbuster Record,
 * после чего поверх него могут применяться изменения
 * AnimationBone.
 */
public class AnimationActorTransform
{
    private double x;
    private double y;
    private double z;

    private float yaw;
    private float pitch;

    public AnimationActorTransform()
    {
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;

        this.yaw = 0.0F;
        this.pitch = 0.0F;
    }

    public AnimationActorTransform(
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

    public void setFromRecord(
            BlockbusterRecordFrame frame)
    {
        if (frame == null)
        {
            this.setPosition(
                    0.0D,
                    0.0D,
                    0.0D
            );

            this.setRotation(
                    0.0F,
                    0.0F
            );

            return;
        }

        this.setPosition(
                frame.getX(),
                frame.getY(),
                frame.getZ()
        );

        this.setRotation(
                frame.getYaw(),
                frame.getPitch()
        );
    }
}
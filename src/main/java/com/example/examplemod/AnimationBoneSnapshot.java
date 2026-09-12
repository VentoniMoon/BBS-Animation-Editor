package com.example.examplemod;

public class AnimationBoneSnapshot
{
    private final String name;

    private final String parentName;

    private final float positionX;
    private final float positionY;
    private final float positionZ;

    private final float rotationX;
    private final float rotationY;
    private final float rotationZ;

    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;

    public AnimationBoneSnapshot(
            String name,
            String parentName,
            AnimationTransform transform)
    {
        this.name = name;
        this.parentName = parentName;

        this.positionX =
                transform.getPositionX();

        this.positionY =
                transform.getPositionY();

        this.positionZ =
                transform.getPositionZ();

        this.rotationX =
                transform.getRotationX();

        this.rotationY =
                transform.getRotationY();

        this.rotationZ =
                transform.getRotationZ();

        this.scaleX =
                transform.getScaleX();

        this.scaleY =
                transform.getScaleY();

        this.scaleZ =
                transform.getScaleZ();
    }

    public String getName()
    {
        return this.name;
    }

    public String getParentName()
    {
        return this.parentName;
    }

    public float getPositionX()
    {
        return this.positionX;
    }

    public float getPositionY()
    {
        return this.positionY;
    }

    public float getPositionZ()
    {
        return this.positionZ;
    }

    public float getRotationX()
    {
        return this.rotationX;
    }

    public float getRotationY()
    {
        return this.rotationY;
    }

    public float getRotationZ()
    {
        return this.rotationZ;
    }

    public float getScaleX()
    {
        return this.scaleX;
    }

    public float getScaleY()
    {
        return this.scaleY;
    }

    public float getScaleZ()
    {
        return this.scaleZ;
    }
}
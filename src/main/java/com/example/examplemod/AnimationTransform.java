package com.example.examplemod;

public class AnimationTransform
{
    private float positionX;
    private float positionY;
    private float positionZ;

    private float rotationX;
    private float rotationY;
    private float rotationZ;

    private float scaleX;
    private float scaleY;
    private float scaleZ;

    public AnimationTransform()
    {
        this.positionX = 0.0F;
        this.positionY = 0.0F;
        this.positionZ = 0.0F;

        this.rotationX = 0.0F;
        this.rotationY = 0.0F;
        this.rotationZ = 0.0F;

        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
    }

    /*
     * ---------------------------------------------------------
     * POSITION
     * ---------------------------------------------------------
     */

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

    public void setPosition(
            float x,
            float y,
            float z)
    {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
    }

    /*
     * ---------------------------------------------------------
     * ROTATION
     * ---------------------------------------------------------
     */

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

    public void setRotation(
            float x,
            float y,
            float z)
    {
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }

    /*
     * ---------------------------------------------------------
     * SCALE
     * ---------------------------------------------------------
     */

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

    public void setScale(
            float x,
            float y,
            float z)
    {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
    }

    /*
     * ---------------------------------------------------------
     * COPY
     * ---------------------------------------------------------
     */

    public AnimationTransform copy()
    {
        AnimationTransform result =
                new AnimationTransform();

        result.setPosition(
                this.positionX,
                this.positionY,
                this.positionZ
        );

        result.setRotation(
                this.rotationX,
                this.rotationY,
                this.rotationZ
        );

        result.setScale(
                this.scaleX,
                this.scaleY,
                this.scaleZ
        );

        return result;
    }

    /*
     * ---------------------------------------------------------
     * WORLD TRANSFORM
     * ---------------------------------------------------------
     *
     * Combines this local transform with a parent transform.
     *
     * Position:
     *   child position is first affected by parent scale,
     *   then rotated by parent rotation,
     *   then translated by parent position.
     *
     * Rotation:
     *   parent + child.
     *
     * Scale:
     *   parent * child.
     *
     */

    public AnimationTransform combine(
            AnimationTransform child)
    {
        AnimationTransform result =
                new AnimationTransform();

        /*
         * Scale child's local position
         * by parent's scale.
         */

        float x =
                child.getPositionX()
                        * this.scaleX;

        float y =
                child.getPositionY()
                        * this.scaleY;

        float z =
                child.getPositionZ()
                        * this.scaleZ;

        /*
         * Rotate the position by the parent's
         * X, Y and Z rotations.
         */

        double rx =
                Math.toRadians(
                        this.rotationX
                );

        double ry =
                Math.toRadians(
                        this.rotationY
                );

        double rz =
                Math.toRadians(
                        this.rotationZ
                );

        /*
         * Rotation around X.
         */

        float cosX =
                (float) Math.cos(rx);

        float sinX =
                (float) Math.sin(rx);

        float rotatedY =
                y * cosX
                        - z * sinX;

        float rotatedZ =
                y * sinX
                        + z * cosX;

        y = rotatedY;
        z = rotatedZ;

        /*
         * Rotation around Y.
         */

        float cosY =
                (float) Math.cos(ry);

        float sinY =
                (float) Math.sin(ry);

        float rotatedX =
                x * cosY
                        + z * sinY;

        rotatedZ =
                -x * sinY
                        + z * cosY;

        x = rotatedX;
        z = rotatedZ;

        /*
         * Rotation around Z.
         */

        float cosZ =
                (float) Math.cos(rz);

        float sinZ =
                (float) Math.sin(rz);

        rotatedX =
                x * cosZ
                        - y * sinZ;

        rotatedY =
                x * sinZ
                        + y * cosZ;

        x = rotatedX;
        y = rotatedY;

        /*
         * Translate into parent's world position.
         */

        result.setPosition(
                this.positionX + x,
                this.positionY + y,
                this.positionZ + z
        );

        /*
         * Combine rotations.
         */

        result.setRotation(
                this.rotationX
                        + child.getRotationX(),

                this.rotationY
                        + child.getRotationY(),

                this.rotationZ
                        + child.getRotationZ()
        );

        /*
         * Combine scales.
         */

        result.setScale(
                this.scaleX
                        * child.getScaleX(),

                this.scaleY
                        * child.getScaleY(),

                this.scaleZ
                        * child.getScaleZ()
        );

        return result;
    }
}
package com.example.examplemod;

public class EditorCamera
{
    private double targetX;
    private double targetY;
    private double targetZ;

    private float yaw;
    private float pitch;

    private double distance;

    /** Interactive navigation or externally sampled cinematic camera. */
    public enum Mode { ORBIT, FREE, TRACK }
    private Mode mode=Mode.ORBIT;
    private float roll=0F;
    private float fov=60F;
    private float nearPlane=0.05F;
    private float farPlane=512F;

    private static final double MIN_DISTANCE = 2.0D;
    private static final double MAX_DISTANCE = 100.0D;

    private static final float MIN_PITCH = -89.0F;
    private static final float MAX_PITCH = 89.0F;

    /*
     * Базовая скорость свободного перемещения.
     */
    private static final double MOVE_SPEED = 0.20D;

    /*
     * Ускорение при зажатом Ctrl.
     */
    private static final double FAST_MOVE_MULTIPLIER = 2.5D;

    public EditorCamera()
    {
        this.reset();
    }

    public void reset()
    {
        this.targetX = 0.0D;
        this.targetY = 1.0D;
        this.targetZ = 0.0D;

        this.yaw = 0.0F;
        this.pitch = 15.0F;

        this.distance = 8.0D;
        this.roll=0F;
        this.fov=60F;
        this.nearPlane=0.05F;
        this.farPlane=512F;
        this.mode=Mode.ORBIT;
    }

    /** Changing navigation mode never moves or rotates the camera. */
    public void setMode(Mode mode)
    {
        if(mode!=null)this.mode=mode;
    }

    public Mode getMode() { return mode; }

    public float getRoll() { return roll; }
    public void setRoll(float roll)
    {
        if(Float.isFinite(roll))this.roll=roll;
    }

    public float getFov() { return fov; }
    public void setFov(float fov)
    {
        if(Float.isFinite(fov))
            this.fov=Math.max(1F,Math.min(170F,fov));
    }

    public float getNearPlane() { return nearPlane; }
    public void setNearPlane(float near)
    {
        if(Float.isFinite(near) && near>0F && near<farPlane)
            nearPlane=near;
    }

    public float getFarPlane() { return farPlane; }
    public void setFarPlane(float far)
    {
        if(Float.isFinite(far) && far>nearPlane)
            farPlane=far;
    }

    /** World-space eye position with camera optics, independent of target. */
    public PreviewCameraPose sample()
    {
        return new PreviewCameraPose(getCameraX(),getCameraY(),getCameraZ(),
                yaw,pitch,roll,fov,nearPlane,farPlane);
    }

    /**
     * Apply a camera-track sample WITHOUT rebuilding the actor or preview
     * scene. Retain the previous orbit radius for seamless manual takeover.
     */
    public void apply(PreviewCameraPose pose)
    {
        if(pose==null)return;
        this.yaw=pose.yaw;
        this.pitch=pose.pitch;
        this.roll=pose.roll;
        this.fov=pose.fov;
        this.nearPlane=pose.nearPlane;
        this.farPlane=pose.farPlane;
        double y=Math.toRadians(this.yaw);
        double p=Math.toRadians(this.pitch);
        double horizontal=Math.cos(p)*this.distance;
        this.targetX=pose.x-Math.sin(y)*horizontal;
        this.targetY=pose.y+Math.sin(p)*this.distance;
        this.targetZ=pose.z-Math.cos(y)*horizontal;
    }

    public void setTarget(
            double x,
            double y,
            double z)
    {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public double getTargetX()
    {
        return this.targetX;
    }

    public double getTargetY()
    {
        return this.targetY;
    }

    public double getTargetZ()
    {
        return this.targetZ;
    }

    /*
     * =========================================================
     * ROTATION
     * =========================================================
     */

    public void rotate(
            float deltaYaw,
            float deltaPitch)
    {
        this.yaw += deltaYaw;
        this.pitch += deltaPitch;

        this.pitch =
                Math.max(
                        MIN_PITCH,
                        Math.min(
                                MAX_PITCH,
                                this.pitch
                        )
                );
    }

    /*
     * =========================================================
     * ZOOM
     * =========================================================
     */

    public void zoom(
            double amount)
    {
        this.distance += amount;

        this.distance =
                Math.max(
                        MIN_DISTANCE,
                        Math.min(
                                MAX_DISTANCE,
                                this.distance
                        )
                );
    }

    public void setDistance(
            double distance)
    {
        this.distance =
                Math.max(
                        MIN_DISTANCE,
                        Math.min(
                                MAX_DISTANCE,
                                distance
                        )
                );
    }

    public float getYaw()
    {
        return this.yaw;
    }

    public float getPitch()
    {
        return this.pitch;
    }

    public double getDistance()
    {
        return this.distance;
    }

    /*
     * =========================================================
     * FREE CAMERA MOVEMENT
     * =========================================================
     */

    /*
     * Движение вперёд/назад относительно
     * ПОЛНОГО направления взгляда камеры.
     *
     * W = вперёд
     * S = назад
     *
     * Теперь учитывается и pitch.
     *
     * Если камера смотрит вверх,
     * W действительно двигает её вверх.
     *
     * Если камера смотрит вниз,
     * W двигает её вниз.
     */
    public void moveForward(
            double amount)
    {
        double yawRadians =
                Math.toRadians(
                        this.yaw
                );

        double pitchRadians =
                Math.toRadians(
                        this.pitch
                );

        double horizontal =
                Math.cos(
                        pitchRadians
                );

        /*
         * Направление взгляда камеры.
         *
         * Камера находится со стороны
         * +Z/+X относительно target,
         * поэтому направление вперёд
         * противоположно положению камеры.
         */
        double directionX =
                -Math.sin(
                        yawRadians
                ) * horizontal;

        double directionY =
                Math.sin(pitchRadians);

        double directionZ =
                -Math.cos(
                        yawRadians
                ) * horizontal;

        this.targetX +=
                directionX * amount;

        this.targetY +=
                directionY * amount;

        this.targetZ +=
                directionZ * amount;
    }

    /*
     * Движение влево/вправо.
     *
     * A = влево
     * D = вправо
     *
     * Движение остаётся горизонтальным,
     * независимо от pitch.
     */
    public void moveStrafe(
            double amount)
    {
        double yawRadians =
                Math.toRadians(
                        this.yaw
                );

        /*
         * Правый вектор камеры.
         */
        double rightX =
                Math.cos(
                        yawRadians
                );

        double rightZ =
                -Math.sin(
                        yawRadians
                );

        this.targetX +=
                rightX * amount;

        this.targetZ +=
                rightZ * amount;
    }

    /*
     * Отдельное вертикальное движение.
     *
     * Space = вверх
     * Shift = вниз
     */
    public void moveVertical(
            double amount)
    {
        this.targetY += amount;
    }

    /*
     * Универсальное перемещение.
     */
    public void move(
            double forward,
            double strafe,
            double vertical,
            boolean fast)
    {
        double multiplier =
                fast
                        ?
                        FAST_MOVE_MULTIPLIER
                        :
                        1.0D;

        double speed =
                MOVE_SPEED *
                        multiplier;

        /*
         * Нормализуем горизонтальное
         * диагональное движение.
         */
        if (forward != 0.0D &&
                strafe != 0.0D)
        {
            double length =
                    Math.sqrt(
                            forward * forward +
                                    strafe * strafe
                    );

            forward /=
                    length;

            strafe /=
                    length;
        }

        /*
         * W / S.
         */
        if (forward != 0.0D)
        {
            this.moveForward(
                    forward * speed
            );
        }

        /*
         * A / D.
         */
        if (strafe != 0.0D)
        {
            this.moveStrafe(
                    strafe * speed
            );
        }

        /*
         * Space / Shift.
         */
        if (vertical != 0.0D)
        {
            this.moveVertical(
                    vertical * speed
            );
        }
    }

    /*
     * =========================================================
     * ACTUAL CAMERA POSITION
     * =========================================================
     */

    public double getCameraX()
    {
        double yawRadians =
                Math.toRadians(
                        this.yaw
                );

        double pitchRadians =
                Math.toRadians(
                        this.pitch
                );

        return this.targetX
                + Math.sin(
                yawRadians
        )
                * Math.cos(
                pitchRadians
        )
                * this.distance;
    }

    public double getCameraY()
    {
        double pitchRadians = Math.toRadians(this.pitch);

        return this.targetY
                - Math.sin(pitchRadians)
                * this.distance;
    }

    public double getCameraZ()
    {
        double yawRadians =
                Math.toRadians(
                        this.yaw
                );

        double pitchRadians =
                Math.toRadians(
                        this.pitch
                );

        return this.targetZ
                + Math.cos(
                yawRadians
        )
                * Math.cos(
                pitchRadians
        )
                * this.distance;
    }
}
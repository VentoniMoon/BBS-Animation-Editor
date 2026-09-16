package com.example.examplemod;

import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;

public class AnimationKeyframe
{
    private int frame;
    private final AnimationTransform transform;

    /*
     * Оригинальная система интерполяции McLib.
     *
     * Эти значения соответствуют mchorse.mclib.utils.keyframes.Keyframe:
     *
     * interpolation = LINEAR
     * easing = IN
     * rx = 5
     * ry = 0
     * lx = 5
     * ly = 0
     */
    private KeyframeInterpolation interpolation;
    private KeyframeEasing easing;

    private float rx;
    private float ry;
    private float lx;
    private float ly;

    public AnimationKeyframe(int frame)
    {
        this.frame = frame;
        this.transform = new AnimationTransform();

        this.interpolation = KeyframeInterpolation.LINEAR;
        this.easing = KeyframeEasing.IN;

        this.rx = 5.0F;
        this.ry = 0.0F;
        this.lx = 5.0F;
        this.ly = 0.0F;
    }

    public int getFrame()
    {
        return this.frame;
    }

    public void setFrame(int frame)
    {
        this.frame = frame;
    }

    public AnimationTransform getTransform()
    {
        return this.transform;
    }

    public KeyframeInterpolation getInterpolation()
    {
        return this.interpolation;
    }

    public void setInterpolation(KeyframeInterpolation interpolation)
    {
        if (interpolation == null)
        {
            interpolation = KeyframeInterpolation.LINEAR;
        }

        this.interpolation = interpolation;
    }

    public KeyframeEasing getEasing()
    {
        return this.easing;
    }

    public void setEasing(KeyframeEasing easing)
    {
        if (easing == null)
        {
            easing = KeyframeEasing.IN;
        }

        this.easing = easing;
    }

    public void setInterpolation(
            KeyframeInterpolation interpolation,
            KeyframeEasing easing)
    {
        setInterpolation(interpolation);
        setEasing(easing);
    }

    public float getRX()
    {
        return this.rx;
    }

    public void setRX(float rx)
    {
        this.rx = rx;
    }

    public float getRY()
    {
        return this.ry;
    }

    public void setRY(float ry)
    {
        this.ry = ry;
    }

    public float getLX()
    {
        return this.lx;
    }

    public void setLX(float lx)
    {
        this.lx = lx;
    }

    public float getLY()
    {
        return this.ly;
    }

    public void setLY(float ly)
    {
        this.ly = ly;
    }
}
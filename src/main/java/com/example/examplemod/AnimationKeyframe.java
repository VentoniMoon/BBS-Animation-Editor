package com.example.examplemod;

public class AnimationKeyframe
{
    private int frame;

    private final AnimationTransform transform;

    public AnimationKeyframe(int frame)
    {
        this.frame = frame;
        this.transform = new AnimationTransform();
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
}
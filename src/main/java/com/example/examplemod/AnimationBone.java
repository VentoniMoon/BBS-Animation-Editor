package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationBone
{
    private final String name;

    private final List<AnimationKeyframe> keyframes;

    private AnimationBone parent;

    private final List<AnimationBone> children;

    private float localX;
    private float localY;
    private float localZ;

    public AnimationBone(
            String name)
    {
        this.name = name;

        this.keyframes =
                new ArrayList<AnimationKeyframe>();

        this.children =
                new ArrayList<AnimationBone>();

        this.parent = null;

        this.localX = 0.0F;
        this.localY = 0.0F;
        this.localZ = 0.0F;
    }

    public String getName()
    {
        return this.name;
    }

    public List<AnimationKeyframe> getKeyframes()
    {
        return this.keyframes;
    }

    public void setLocalPosition(
            float x,
            float y,
            float z)
    {
        this.localX = x;
        this.localY = y;
        this.localZ = z;
    }

    public float getLocalX()
    {
        return this.localX;
    }

    public float getLocalY()
    {
        return this.localY;
    }

    public float getLocalZ()
    {
        return this.localZ;
    }

    public AnimationBone getParent()
    {
        return this.parent;
    }

    public void setParent(
            AnimationBone parent)
    {
        if (parent == this)
        {
            return;
        }

        if (
                parent != null
                        && parent.isChildOf(this)
        )
        {
            return;
        }

        if (this.parent != null)
        {
            this.parent.children.remove(this);
        }

        this.parent = parent;

        if (
                this.parent != null
                        && !this.parent.children.contains(this)
        )
        {
            this.parent.children.add(this);
        }
    }

    public List<AnimationBone> getChildren()
    {
        return this.children;
    }

    public void addChild(
            AnimationBone child)
    {
        if (child == null)
        {
            return;
        }

        child.setParent(this);
    }

    public void removeChild(
            AnimationBone child)
    {
        if (child == null)
        {
            return;
        }

        if (this.children.contains(child))
        {
            this.children.remove(child);

            if (child.parent == this)
            {
                child.parent = null;
            }
        }
    }

    private boolean isChildOf(
            AnimationBone bone)
    {
        AnimationBone current =
                this.parent;

        while (current != null)
        {
            if (current == bone)
            {
                return true;
            }

            current =
                    current.parent;
        }

        return false;
    }

    /*
     * ---------------------------------------------------------
     * Keyframes
     * ---------------------------------------------------------
     */

    public boolean hasKeyframe(
            int frame)
    {
        for (
                AnimationKeyframe keyframe :
                this.keyframes
        )
        {
            if (
                    keyframe != null
                            && keyframe.getFrame() == frame
            )
            {
                return true;
            }
        }

        return false;
    }

    public void addKeyframe(
            int frame)
    {
        if (hasKeyframe(frame))
        {
            return;
        }

        this.keyframes.add(
                new AnimationKeyframe(
                        frame
                )
        );

        sortKeyframes();
    }

    public void removeKeyframe(
            int frame)
    {
        for (
                int i =
                this.keyframes.size() - 1;
                i >= 0;
                i--
        )
        {
            AnimationKeyframe keyframe =
                    this.keyframes.get(i);

            if (
                    keyframe != null
                            && keyframe.getFrame() == frame
            )
            {
                this.keyframes.remove(i);

                return;
            }
        }
    }

    /**
     * Перемещает существующий keyframe на другой кадр.
     *
     * Сам объект keyframe не создаётся заново, поэтому
     * Transform, interpolation, easing и параметры кривой
     * полностью сохраняются.
     *
     * @return true, если перемещение выполнено
     */
    public boolean moveKeyframe(
            AnimationKeyframe keyframe,
            int newFrame)
    {
        if (keyframe == null)
        {
            return false;
        }

        if (!this.keyframes.contains(keyframe))
        {
            return false;
        }

        if (newFrame < 0)
        {
            newFrame = 0;
        }

        /*
         * Если на новом кадре уже находится другой ключ,
         * перемещение запрещаем.
         */
        for (
                AnimationKeyframe existing :
                this.keyframes
        )
        {
            if (
                    existing != null
                            && existing != keyframe
                            && existing.getFrame() == newFrame
            )
            {
                return false;
            }
        }

        keyframe.setFrame(newFrame);

        sortKeyframes();

        return true;
    }

    public AnimationKeyframe
    getPreviousKeyframe(
            int frame)
    {
        AnimationKeyframe previous =
                null;

        for (
                AnimationKeyframe keyframe :
                this.keyframes
        )
        {
            if (keyframe == null)
            {
                continue;
            }

            if (
                    keyframe.getFrame()
                            <= frame
            )
            {
                previous = keyframe;
            }
            else
            {
                break;
            }
        }

        return previous;
    }

    public AnimationKeyframe
    getNextKeyframe(
            int frame)
    {
        for (
                AnimationKeyframe keyframe :
                this.keyframes
        )
        {
            if (keyframe == null)
            {
                continue;
            }

            if (
                    keyframe.getFrame()
                            >= frame
            )
            {
                return keyframe;
            }
        }

        return null;
    }

    /*
     * ---------------------------------------------------------
     * Fractional animation
     * ---------------------------------------------------------
     */

    public AnimationTransform
    getTransformAt(
            int frame)
    {
        return getTransformAt(
                (float) frame
        );
    }

    public AnimationTransform
    getTransformAt(
            float frame)
    {
        if (this.keyframes.isEmpty())
        {
            return new AnimationTransform();
        }

        AnimationKeyframe first =
                this.keyframes.get(0);

        if (
                first != null
                        && frame <= first.getFrame()
        )
        {
            return first.getTransform().copy();
        }

        AnimationKeyframe last =
                this.keyframes.get(
                        this.keyframes.size() - 1
                );

        if (
                last != null
                        && frame >= last.getFrame()
        )
        {
            return last.getTransform().copy();
        }

        AnimationKeyframe previous =
                null;

        AnimationKeyframe next =
                null;

        for (
                int i = 0;
                i < this.keyframes.size();
                i++
        )
        {
            AnimationKeyframe keyframe =
                    this.keyframes.get(i);

            if (keyframe == null)
            {
                continue;
            }

            float keyframeFrame =
                    (float) keyframe.getFrame();

            if (keyframeFrame <= frame)
            {
                previous = keyframe;
            }

            if (keyframeFrame >= frame)
            {
                next = keyframe;
                break;
            }
        }

        if (previous == null)
        {
            return next != null
                    ? next.getTransform().copy()
                    : new AnimationTransform();
        }

        if (next == null)
        {
            return previous.getTransform().copy();
        }

        if (previous == next)
        {
            return previous.getTransform().copy();
        }

        AnimationKeyframe previousPrevious =
                null;

        AnimationKeyframe nextNext =
                null;

        int previousIndex =
                this.keyframes.indexOf(
                        previous
                );

        int nextIndex =
                this.keyframes.indexOf(
                        next
                );

        if (previousIndex > 0)
        {
            previousPrevious =
                    this.keyframes.get(
                            previousIndex - 1
                    );
        }

        if (
                nextIndex >= 0
                        && nextIndex + 1 <
                        this.keyframes.size()
        )
        {
            nextNext =
                    this.keyframes.get(
                            nextIndex + 1
                    );
        }

        return AnimationInterpolator.interpolate(
                previousPrevious,
                previous,
                next,
                nextNext,
                frame
        );
    }

    public AnimationBoneSnapshot
    createSnapshot(
            int frame)
    {
        AnimationTransform transform =
                getWorldTransformAt(
                        frame
                );

        String parentName =
                null;

        if (this.parent != null)
        {
            parentName =
                    this.parent.getName();
        }

        return new AnimationBoneSnapshot(
                this.name,
                parentName,
                transform
        );
    }

    public AnimationBone
    getRootBone()
    {
        AnimationBone root =
                this;

        while (root.parent != null)
        {
            root =
                    root.parent;
        }

        return root;
    }

    public AnimationTransform
    getWorldTransformAt(
            int frame)
    {
        return getWorldTransformAt(
                (float) frame
        );
    }

    public AnimationTransform
    getWorldTransformAt(
            float frame)
    {
        AnimationTransform localTransform =
                getTransformAt(
                        frame
                );

        if (localTransform == null)
        {
            localTransform =
                    new AnimationTransform();
        }

        AnimationTransform local =
                new AnimationTransform();

        local.setPosition(
                this.localX
                        + localTransform.getPositionX(),

                this.localY
                        + localTransform.getPositionY(),

                this.localZ
                        + localTransform.getPositionZ()
        );

        local.setRotation(
                localTransform.getRotationX(),
                localTransform.getRotationY(),
                localTransform.getRotationZ()
        );

        local.setScale(
                localTransform.getScaleX(),
                localTransform.getScaleY(),
                localTransform.getScaleZ()
        );

        if (this.parent == null)
        {
            return local;
        }

        AnimationTransform parentWorld =
                this.parent.getWorldTransformAt(
                        frame
                );

        if (parentWorld == null)
        {
            return local;
        }

        return parentWorld.combine(
                local
        );
    }

    public AnimationTransform
    getWorldPivotAt(
            int frame)
    {
        return getWorldPivotAt(
                (float) frame
        );
    }

    public AnimationTransform
    getWorldPivotAt(
            float frame)
    {
        AnimationTransform localTransform =
                getTransformAt(
                        frame
                );

        if (localTransform == null)
        {
            localTransform =
                    new AnimationTransform();
        }

        AnimationTransform pivot =
                new AnimationTransform();

        pivot.setPosition(
                this.localX
                        + localTransform.getPositionX(),

                this.localY
                        + localTransform.getPositionY(),

                this.localZ
                        + localTransform.getPositionZ()
        );

        pivot.setRotation(
                localTransform.getRotationX(),
                localTransform.getRotationY(),
                localTransform.getRotationZ()
        );

        pivot.setScale(
                localTransform.getScaleX(),
                localTransform.getScaleY(),
                localTransform.getScaleZ()
        );

        if (this.parent == null)
        {
            return pivot;
        }

        AnimationTransform parentPivot =
                this.parent.getWorldPivotAt(
                        frame
                );

        if (parentPivot == null)
        {
            return pivot;
        }

        return parentPivot.combine(
                pivot
        );
    }

    private void sortKeyframes()
    {
        for (
                int i = 0;
                i < this.keyframes.size() - 1;
                i++
        )
        {
            for (
                    int j = 0;
                    j < this.keyframes.size() - i - 1;
                    j++
            )
            {
                AnimationKeyframe current =
                        this.keyframes.get(j);

                AnimationKeyframe next =
                        this.keyframes.get(j + 1);

                if (
                        current == null
                                || next == null
                )
                {
                    continue;
                }

                if (
                        current.getFrame()
                                > next.getFrame()
                )
                {
                    this.keyframes.set(
                            j,
                            next
                    );

                    this.keyframes.set(
                            j + 1,
                            current
                    );
                }
            }
        }
    }
}
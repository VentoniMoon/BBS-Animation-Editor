package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationBone
{
    private final String name;

    private final List<AnimationKeyframe> keyframes;

    private AnimationBone parent;

    private final List<AnimationBone> children;

    /*
     * Static local position of the bone.
     *
     * This defines the bone's default attachment
     * position inside the model.
     */
    private float localX;
    private float localY;
    private float localZ;

    public AnimationBone(String name)
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

    /*
     * ---------------------------------------------------------
     * Local attachment position
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * Parent / children
     * ---------------------------------------------------------
     */

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
                parent != null &&
                        parent.isChildOf(this)
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
                this.parent != null &&
                        !this.parent.children.contains(this)
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

            current = current.parent;
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
                    keyframe.getFrame() ==
                            frame
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
        if (!hasKeyframe(frame))
        {
            this.keyframes.add(
                    new AnimationKeyframe(frame)
            );

            sortKeyframes();
        }
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
            if (
                    this.keyframes
                            .get(i)
                            .getFrame() ==
                            frame
            )
            {
                this.keyframes.remove(i);
                return;
            }
        }
    }

    public AnimationKeyframe
    getPreviousKeyframe(
            int frame)
    {
        AnimationKeyframe previous = null;

        for (
                AnimationKeyframe keyframe :
                this.keyframes
        )
        {
            if (
                    keyframe.getFrame() <=
                            frame
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
            if (
                    keyframe.getFrame() >=
                            frame
            )
            {
                return keyframe;
            }
        }

        return null;
    }

    public AnimationTransform
    getTransformAt(
            int frame)
    {
        AnimationKeyframe previous =
                getPreviousKeyframe(frame);

        AnimationKeyframe next =
                getNextKeyframe(frame);

        return AnimationInterpolator.interpolate(
                previous,
                next,
                frame
        );
    }

    /*
     * ---------------------------------------------------------
     * Adapter snapshot
     * ---------------------------------------------------------
     */

    public AnimationBoneSnapshot
    createSnapshot(
            int frame)
    {
        AnimationTransform transform =
                getTransformAt(frame);

        String parentName = null;

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

    /*
     * ---------------------------------------------------------
     * Anchor
     * ---------------------------------------------------------
     *
     * The Anchor is the ONLY parent whose animated
     * transform is inherited by all model bones.
     */

    private AnimationBone
    getAnchorBone()
    {
        AnimationBone current = this;

        while (
                current.parent != null
        )
        {
            current = current.parent;
        }

        return current;
    }

    /*
     * ---------------------------------------------------------
     * Anchor
     * ---------------------------------------------------------
     *
     * The Anchor is the ONLY parent whose animated
     * transform is inherited by all model bones.
     */

    public AnimationTransform
    getAnchorTransformAt(
            int frame)
    {
        AnimationBone anchor =
                getAnchorBone();

        return anchor.getTransformAt(
                frame
        );
    }

    /*
     * ---------------------------------------------------------
     * Model transform
     * ---------------------------------------------------------
     *
     * Anchor transform affects the whole model.
     *
     * The current bone's own animation transform
     * affects only this bone.
     *
     * Parent bone animation transforms are NOT inherited.
     */

    public AnimationTransform
    getWorldTransformAt(
            int frame)
    {
        AnimationTransform anchorTransform =
                getAnchorTransformAt(
                        frame
                );

        AnimationTransform localTransform =
                getTransformAt(
                        frame
                );

        /*
         * The bone's static attachment position
         * belongs to the model coordinate system.
         */

        AnimationTransform boneTransform =
                new AnimationTransform();

        boneTransform.setPosition(
                this.localX
                        + localTransform.getPositionX(),

                this.localY
                        + localTransform.getPositionY(),

                this.localZ
                        + localTransform.getPositionZ()
        );

        boneTransform.setRotation(
                localTransform.getRotationX(),
                localTransform.getRotationY(),
                localTransform.getRotationZ()
        );

        boneTransform.setScale(
                localTransform.getScaleX(),
                localTransform.getScaleY(),
                localTransform.getScaleZ()
        );

        /*
         * Anchor is the global model transform.
         */

        return anchorTransform.combine(
                boneTransform
        );
    }

    /*
     * ---------------------------------------------------------
     * World pivot
     * ---------------------------------------------------------
     */

    public AnimationTransform
    getWorldPivotAt(
            int frame)
    {
        AnimationTransform anchorTransform =
                getAnchorTransformAt(
                        frame
                );

        AnimationTransform localTransform =
                getTransformAt(
                        frame
                );

        AnimationTransform pivot =
                new AnimationTransform();

        /*
         * Static attachment position + this bone's
         * own animated position.
         */

        pivot.setPosition(
                this.localX
                        + localTransform.getPositionX(),

                this.localY
                        + localTransform.getPositionY(),

                this.localZ
                        + localTransform.getPositionZ()
        );

        /*
         * The pivot itself does not need to inherit
         * the bone's rotation for positioning.
         *
         * Its rotation is still returned for consistency.
         */

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

        return anchorTransform.combine(
                pivot
        );
    }

    /*
     * ---------------------------------------------------------
     * Sorting
     * ---------------------------------------------------------
     */

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
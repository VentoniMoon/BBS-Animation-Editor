package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationBone
{
    private final String name;

    /*
     * Пользовательские ключевые кадры этой кости.
     *
     * Здесь находятся только изменения,
     * созданные пользователем редактора.
     */
    private final List<AnimationKeyframe> keyframes;

    /*
     * Иерархия модели.
     */
    private AnimationBone parent;

    private final List<AnimationBone> children;

    /*
     * Статическая позиция точки привязки кости
     * внутри исходной Blockbuster-модели.
     */
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

    /*
     * ---------------------------------------------------------
     * Basic information
     * ---------------------------------------------------------
     */

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
     * Static local attachment
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
        /*
         * Нельзя сделать кость родителем самой себя.
         */
        if (parent == this)
        {
            return;
        }

        /*
         * Нельзя создать цикл:
         *
         * A -> B -> C -> A
         */
        if (
                parent != null
                        && parent.isChildOf(this)
        )
        {
            return;
        }

        /*
         * Удаляем кость из старого родителя.
         */
        if (this.parent != null)
        {
            this.parent.children.remove(this);
        }

        this.parent = parent;

        /*
         * Добавляем кость новому родителю.
         */
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

    /**
     * Получает пользовательскую трансформацию
     * на дробном кадре.
     *
     * Например:
     *
     * 10.0
     * 10.25
     * 10.50
     * 10.75
     * 11.0
     *
     * Это позволяет рендерить анимацию
     * плавнее, чем 20 дискретных кадров в секунду.
     */
    public AnimationTransform
    getTransformAt(
            float frame)
    {
        if (this.keyframes.isEmpty())
        {
            return new AnimationTransform();
        }

        /*
         * Если кадр находится до первого keyframe,
         * используем первый keyframe.
         */
        AnimationKeyframe first =
                this.keyframes.get(0);

        if (
                first != null
                        && frame <= first.getFrame()
        )
        {
            return first.getTransform().copy();
        }

        /*
         * Если кадр находится после последнего keyframe,
         * используем последний keyframe.
         */
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

        /*
         * Ищем два keyframe, между которыми
         * находится текущий дробный кадр.
         *
         * Например:
         *
         * 10.5
         *
         * previous = 10
         * next     = 20
         */
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

        /*
         * Если по какой-то причине один из keyframe
         * не найден, используем найденный.
         */
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

        /*
         * Если это один и тот же keyframe,
         * интерполяция не нужна.
         */
        if (previous == next)
        {
            return previous.getTransform().copy();
        }

        /*
         * Находим keyframe до previous
         * и после next.
         *
         * Они нужны McLib-интерполятору
         * для некоторых типов кривых.
         */
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

    /*
     * ---------------------------------------------------------
     * Snapshot
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * Root
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * Root transform
     * ---------------------------------------------------------
     */

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

    /*
     * ---------------------------------------------------------
     * World pivot
     * ---------------------------------------------------------
     */

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
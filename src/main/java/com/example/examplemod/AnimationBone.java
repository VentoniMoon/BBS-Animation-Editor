package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

public class AnimationBone
{
    private final String name;

    /*
     * Пользовательские ключевые кадры этой кости.
     *
     * ВАЖНО:
     *
     * Здесь находятся только изменения,
     * созданные пользователем редактора.
     *
     * Кадры Blockbuster Record сюда НЕ импортируются.
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
     *
     * Эти значения приходят из ModelCustomRenderer:
     *
     * rotationPointX
     * rotationPointY
     * rotationPointZ
     *
     * Они НЕ являются анимацией.
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
     * Получает пользовательскую трансформацию
     * этой кости на конкретном кадре.
     */
    public AnimationTransform
    getTransformAt(
            int frame)
    {
        AnimationKeyframe previous =
                getPreviousKeyframe(
                        frame
                );

        AnimationKeyframe next =
                getNextKeyframe(
                        frame
                );

        return AnimationInterpolator.interpolate(
                previous,
                next,
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

    /**
     * Возвращает корневую кость этой иерархии.
     *
     * Никакого специального имени вроде "Anchor"
     * здесь нет.
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

    /**
     * Совместимость со старым кодом.
     *
     * Раньше метод был завязан на Anchor.
     *
     * Теперь он просто получает transform
     * настоящей корневой кости.
     */
    public AnimationTransform
    getAnchorTransformAt(
            int frame)
    {
        AnimationBone root =
                getRootBone();

        return root.getTransformAt(
                frame
        );
    }

    /*
     * ---------------------------------------------------------
     * World transform
     * ---------------------------------------------------------
     *
     * Здесь находится основная логика иерархии.
     *
     * Например:
     *
     * anchor
     *   |
     *  body
     *   |
     * left_arm
     *
     * Итоговый transform left_arm:
     *
     * anchor
     *      +
     * body
     *      +
     * left_arm
     *
     * Причём это работает для любого количества
     * уровней вложенности.
     */

    public AnimationTransform
    getWorldTransformAt(
            int frame)
    {
        AnimationTransform localTransform =
                getTransformAt(
                        frame
                );

        /*
         * Если у кости нет пользовательских
         * ключевых кадров, interpolator должен
         * вернуть базовую пустую трансформацию.
         *
         * На всякий случай создаём её здесь.
         */
        if (localTransform == null)
        {
            localTransform =
                    new AnimationTransform();
        }

        /*
         * Сначала создаём локальную трансформацию
         * этой кости.
         *
         * localX/Y/Z:
         *
         * статическая точка привязки Blockbuster.
         *
         * AnimationTransform:
         *
         * пользовательское изменение.
         */
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

        /*
         * Корневая кость не имеет родителя.
         *
         * Поэтому её локальный transform
         * одновременно является мировым.
         */
        if (this.parent == null)
        {
            return local;
        }

        /*
         * Получаем уже вычисленный мировой transform
         * родителя.
         *
         * В результате рекурсия идёт вверх:
         *
         * left_leg_shoe
         *      ↓
         * left_leg
         *      ↓
         * body
         *      ↓
         * anchor
         */
        AnimationTransform parentWorld =
                this.parent.getWorldTransformAt(
                        frame
                );

        if (parentWorld == null)
        {
            return local;
        }

        /*
         * Накладываем локальный transform этой кости
         * на мировой transform родителя.
         */
        return parentWorld.combine(
                local
        );
    }

    /*
     * ---------------------------------------------------------
     * World pivot
     * ---------------------------------------------------------
     *
     * Pivot использует ту же иерархию, что и world
     * transform.
     *
     * Это важно для редактора:
     *
     * если родитель повернулся,
     * pivot ребёнка должен переместиться вместе
     * с родителем.
     */

    public AnimationTransform
    getWorldPivotAt(
            int frame)
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

        /*
         * Для корня pivot является мировым.
         */
        if (this.parent == null)
        {
            return pivot;
        }

        /*
         * Pivot ребёнка также находится
         * в системе координат родителя.
         */
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
        /*
         * Оставляем простую сортировку.
         *
         * Количество пользовательских ключей обычно
         * небольшое, поэтому здесь важнее простота
         * и совместимость с Java 8.
         */
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
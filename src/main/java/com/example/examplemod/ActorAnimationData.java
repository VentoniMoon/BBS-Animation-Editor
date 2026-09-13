package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActorAnimationData
{
    private final String actorId;

    private final List<AnimationBone> bones =
            new ArrayList<AnimationBone>();

    public ActorAnimationData(
            String actorId)
    {
        this.actorId = actorId;
    }

    /**
     * Создаёт AnimationBone непосредственно
     * из реальной структуры Blockbuster-модели.
     *
     * Никаких заранее заданных имён костей здесь нет.
     */
    public ActorAnimationData(
            String actorId,
            List<BlockbusterLimbData> limbs)
    {
        this.actorId = actorId;

        createBones(
                limbs
        );
    }

    public String getActorId()
    {
        return this.actorId;
    }

    public List<AnimationBone> getBones()
    {
        return this.bones;
    }

    public AnimationBone getBone(
            String name)
    {
        if (name == null)
        {
            return null;
        }

        for (
                AnimationBone bone :
                this.bones
        )
        {
            if (bone != null
                    && name.equals(
                    bone.getName()
            ))
            {
                return bone;
            }
        }

        return null;
    }

    /**
     * Строит полную иерархию AnimationBone
     * из BlockbusterLimbData.
     *
     * Важно:
     *
     * порядок limbs не имеет значения.
     *
     * Сначала создаются ВСЕ AnimationBone,
     * затем отдельно устанавливаются parent/child связи.
     */
    private void createBones(
            List<BlockbusterLimbData> limbs)
    {
        this.bones.clear();

        if (limbs == null)
        {
            return;
        }

        /*
         * Первый проход:
         *
         * создаём AnimationBone для каждого
         * реального limb.
         */
        Map<String, AnimationBone> boneMap =
                new HashMap<String, AnimationBone>();

        for (
                BlockbusterLimbData limb :
                limbs
        )
        {
            if (limb == null)
            {
                continue;
            }

            String name =
                    limb.getName();

            if (name == null
                    || name.isEmpty())
            {
                continue;
            }

            /*
             * Не создаём дубликат,
             * если Blockbuster каким-либо образом
             * вернул одинаковое имя.
             */
            if (boneMap.containsKey(name))
            {
                continue;
            }

            AnimationBone bone =
                    new AnimationBone(
                            name
                    );

            /*
             * Позиция attachment point
             * берётся непосредственно из
             * Blockbuster-модели.
             */
            bone.setLocalPosition(
                    limb.getX(),
                    limb.getY(),
                    limb.getZ()
            );

            boneMap.put(
                    name,
                    bone
            );

            this.bones.add(
                    bone
            );
        }

        /*
         * Второй проход:
         *
         * устанавливаем реальные связи
         * parent -> child.
         */
        for (
                BlockbusterLimbData limb :
                limbs
        )
        {
            if (limb == null)
            {
                continue;
            }

            String name =
                    limb.getName();

            if (name == null
                    || name.isEmpty())
            {
                continue;
            }

            AnimationBone bone =
                    boneMap.get(name);

            if (bone == null)
            {
                continue;
            }

            String parentName =
                    limb.getParentName();

            if (parentName == null
                    || parentName.isEmpty())
            {
                /*
                 * Это корневая кость.
                 */
                continue;
            }

            AnimationBone parent =
                    boneMap.get(parentName);

            if (parent == null)
            {
                /*
                 * Родитель указан Blockbuster,
                 * но отсутствует среди импортированных
                 * limbs.
                 *
                 * Не создаём искусственную кость.
                 */
                continue;
            }

            parent.addChild(
                    bone
            );
        }
    }
}
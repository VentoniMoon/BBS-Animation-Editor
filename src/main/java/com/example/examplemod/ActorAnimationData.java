package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

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
     * Создаёт AnimationBone из BlockbusterLimbData.
     */
    public ActorAnimationData(
            String actorId,
            List<BlockbusterLimbData> limbs)
    {
        this.actorId = actorId;

        createBonesFromBlockbuster(
                limbs
        );
    }


    /**
     * Создаёт AnimationBone непосредственно
     * из реального Emoticons AnimatedMorph.
     *
     * Источником скелета является BOBJArmature/BOBJBone.
     */
    public ActorAnimationData(
            String actorId,
            AnimatedMorph morph)
    {
        this.actorId = actorId;

        createBonesFromEmoticons(
                morph
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
            if (
                    bone != null
                            &&
                            name.equals(
                                    bone.getName()
                            )
            )
            {
                return bone;
            }
        }

        return null;
    }


    /**
     * =========================================================
     * BLOCKBUSTER
     * =========================================================
     */

    private void createBonesFromBlockbuster(
            List<BlockbusterLimbData> limbs)
    {
        this.bones.clear();

        if (limbs == null)
        {
            return;
        }

        Map<String, AnimationBone> boneMap =
                new HashMap<String, AnimationBone>();

        /*
         * Первый проход:
         * создаём все кости.
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

            if (
                    name == null
                            ||
                            name.isEmpty()
            )
            {
                continue;
            }

            if (boneMap.containsKey(name))
            {
                continue;
            }

            AnimationBone bone =
                    new AnimationBone(
                            name
                    );

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
         * устанавливаем parent/child.
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

            if (
                    name == null
                            ||
                            name.isEmpty()
            )
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

            if (
                    parentName == null
                            ||
                            parentName.isEmpty()
            )
            {
                continue;
            }

            AnimationBone parent =
                    boneMap.get(parentName);

            if (parent == null)
            {
                continue;
            }

            parent.addChild(
                    bone
            );
        }
    }


    /**
     * =========================================================
     * EMOTICONS
     * =========================================================
     */

    private void createBonesFromEmoticons(
            AnimatedMorph morph)
    {
        this.bones.clear();

        if (morph == null)
        {
            return;
        }

        List<BOBJArmature> armatures =
                EmoticonsModelAccess.getArmatures(
                        morph
                );

        if (
                armatures == null
                        ||
                        armatures.isEmpty()
        )
        {
            return;
        }

        /*
         * Один Emoticons morph может иметь
         * несколько meshes, ссылающихся на одну
         * и ту же armature.
         *
         * Поэтому используем identity map,
         * чтобы одна и та же armature не
         * добавлялась несколько раз.
         */
        List<BOBJArmature> uniqueArmatures =
                new ArrayList<BOBJArmature>();

        for (
                BOBJArmature armature :
                armatures
        )
        {
            if (armature == null)
            {
                continue;
            }

            if (
                    !containsArmature(
                            uniqueArmatures,
                            armature
                    )
            )
            {
                uniqueArmatures.add(
                        armature
                );
            }
        }

        /*
         * В настоящее время AnimatedMorph обычно
         * использует одну armature.
         *
         * Если их несколько, объединяем их
         * в один список AnimationBone.
         */
        Map<String, AnimationBone> boneMap =
                new HashMap<String, AnimationBone>();

        for (
                BOBJArmature armature :
                uniqueArmatures
        )
        {
            List<BOBJBone> sourceBones =
                    getArmatureBones(
                            armature
                    );

            for (
                    BOBJBone sourceBone :
                    sourceBones
            )
            {
                if (sourceBone == null)
                {
                    continue;
                }

                String name =
                        sourceBone.name;

                if (
                        name == null
                                ||
                                name.isEmpty()
                )
                {
                    continue;
                }

                if (boneMap.containsKey(name))
                {
                    continue;
                }

                AnimationBone bone =
                        new AnimationBone(
                                name
                        );

                bone.setLocalPosition(
                        sourceBone.x,
                        sourceBone.y,
                        sourceBone.z
                );

                boneMap.put(
                        name,
                        bone
                );

                this.bones.add(
                        bone
                );
            }
        }

        /*
         * Второй проход:
         * устанавливаем иерархию.
         */
        for (
                BOBJArmature armature :
                uniqueArmatures
        )
        {
            List<BOBJBone> sourceBones =
                    getArmatureBones(
                            armature
                    );

            for (
                    BOBJBone sourceBone :
                    sourceBones
            )
            {
                if (sourceBone == null)
                {
                    continue;
                }

                String name =
                        sourceBone.name;

                if (
                        name == null
                                ||
                                name.isEmpty()
                )
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
                        EmoticonsModelAccess.getParentName(
                                sourceBone
                        );

                if (
                        parentName == null
                                ||
                                parentName.isEmpty()
                )
                {
                    continue;
                }

                AnimationBone parent =
                        boneMap.get(parentName);

                if (parent == null)
                {
                    continue;
                }

                parent.addChild(
                        bone
                );
            }
        }
    }


    private List<BOBJBone> getArmatureBones(
            BOBJArmature armature)
    {
        List<BOBJBone> result =
                new ArrayList<BOBJBone>();

        if (armature == null)
        {
            return result;
        }

        if (
                armature.orderedBones != null
                        &&
                        !armature.orderedBones.isEmpty()
        )
        {
            for (
                    BOBJBone bone :
                    armature.orderedBones
            )
            {
                if (bone != null)
                {
                    result.add(
                            bone
                    );
                }
            }

            return result;
        }

        if (armature.bones != null)
        {
            for (
                    BOBJBone bone :
                    armature.bones.values()
            )
            {
                if (bone != null)
                {
                    result.add(
                            bone
                    );
                }
            }
        }

        return result;
    }


    private boolean containsArmature(
            List<BOBJArmature> armatures,
            BOBJArmature target)
    {
        for (
                BOBJArmature armature :
                armatures
        )
        {
            if (armature == target)
            {
                return true;
            }
        }

        return false;
    }
}
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
     * Creates AnimationBone data directly from an optional Chameleon
     * morph.  Chameleon is intentionally accessed through reflection so
     * the editor still compiles and runs when Chameleon is not installed.
     */
    public ActorAnimationData(
            String actorId,
            mchorse.metamorph.api.morphs.AbstractMorph morph)
    {
        this.actorId = actorId;

        if (morph != null && isChameleonMorph(morph))
        {
            createBonesFromChameleon(morph);
        }
    }


    private boolean isChameleonMorph(
            mchorse.metamorph.api.morphs.AbstractMorph morph)
    {
        String name = morph.getClass().getName();

        return name.equals("mchorse.chameleon.morph.ChameleonMorph")
                || name.endsWith(".ChameleonMorph");
    }


    private void createBonesFromChameleon(
            mchorse.metamorph.api.morphs.AbstractMorph morph)
    {
        this.bones.clear();

        try
        {
            Object chameleonModel =
                    morph.getClass()
                            .getMethod("getModel")
                            .invoke(morph);

            if (chameleonModel == null)
            {
                return;
            }

            Object model = null;

            try
            {
                java.lang.reflect.Field modelField =
                        chameleonModel.getClass().getField("model");
                model = modelField.get(chameleonModel);
            }
            catch (Throwable ignored)
            {
            }

            if (model == null)
            {
                return;
            }

            Object roots =
                    model.getClass().getField("bones").get(model);

            if (!(roots instanceof List))
            {
                return;
            }

            for (Object root : (List<?>) roots)
            {
                createChameleonBone(
                        root,
                        null,
                        this.bones
                );
            }
        }
        catch (Throwable error)
        {
            /* Optional Chameleon: never break actor initialization. */
        }
    }


    private void createChameleonBone(
            Object source,
            AnimationBone parent,
            List<AnimationBone> result)
    {
        if (source == null)
        {
            return;
        }

        try
        {
            java.lang.reflect.Field idField =
                    source.getClass().getField("id");

            Object id = idField.get(source);

            if (id == null)
            {
                return;
            }

            AnimationBone bone =
                    new AnimationBone(String.valueOf(id));

            try
            {
                Object initial =
                        source.getClass()
                                .getField("initial")
                                .get(source);

                if (initial != null)
                {
                    Object translate =
                            initial.getClass()
                                    .getField("translate")
                                    .get(initial);

                    if (translate != null)
                    {
                        bone.setLocalPosition(
                                readChameleonVector(translate, "x"),
                                readChameleonVector(translate, "y"),
                                readChameleonVector(translate, "z")
                        );
                    }
                }
            }
            catch (Throwable ignored)
            {
            }

            if (parent != null)
            {
                parent.addChild(bone);
            }

            result.add(bone);

            Object children =
                    source.getClass()
                            .getField("children")
                            .get(source);

            if (children instanceof List)
            {
                for (Object child : (List<?>) children)
                {
                    createChameleonBone(
                            child,
                            bone,
                            result
                    );
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }


    private float readChameleonVector(
            Object vector,
            String field)
    {
        try
        {
            Object value =
                    vector.getClass()
                            .getField(field)
                            .get(vector);

            return value instanceof Number
                    ? ((Number) value).floatValue()
                    : 0.0F;
        }
        catch (Throwable ignored)
        {
            return 0.0F;
        }
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
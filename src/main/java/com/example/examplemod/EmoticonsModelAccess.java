package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mchorse.emoticons.skin_n_bones.api.animation.Animation;
import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorController;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;

public class EmoticonsModelAccess
{
    private EmoticonsModelAccess()
    {
    }

    /**
     * Проверяет, является ли morph моделью,
     * поддерживаемой Emoticons.
     */
    public static boolean isSupported(
            Object morph)
    {
        return morph instanceof AnimatedMorph;
    }

    /**
     * Получает AnimatorController из AnimatedMorph.
     */
    public static AnimatorController getController(
            AnimatedMorph morph)
    {
        if (morph == null)
        {
            return null;
        }

        return morph.animator;
    }

    /**
     * Получает Animation, которую использует
     * Emoticons AnimatorController.
     */
    public static Animation getAnimation(
            AnimatedMorph morph)
    {
        AnimatorController controller =
                getController(morph);

        if (controller == null)
        {
            return null;
        }

        return controller.animation;
    }

    /**
     * Получает все AnimationMesh текущей модели.
     */
    public static List<AnimationMesh> getMeshes(
            AnimatedMorph morph)
    {
        Animation animation =
                getAnimation(morph);

        if (animation == null)
        {
            return Collections.emptyList();
        }

        if (animation.meshes == null)
        {
            return Collections.emptyList();
        }

        return animation.meshes;
    }

    /**
     * Получает все armature, используемые
     * AnimationMesh текущей модели.
     */
    public static List<BOBJArmature> getArmatures(
            AnimatedMorph morph)
    {
        List<BOBJArmature> armatures =
                new ArrayList<BOBJArmature>();

        List<AnimationMesh> meshes =
                getMeshes(morph);

        for (
                AnimationMesh mesh :
                meshes
        )
        {
            if (mesh == null)
            {
                continue;
            }

            BOBJArmature armature =
                    mesh.getCurrentArmature();

            if (armature == null)
            {
                armature =
                        mesh.getArmature();
            }

            if (armature != null)
            {
                armatures.add(armature);
            }
        }

        return armatures;
    }

    /**
     * Получает все кости всех armature.
     */
    public static List<BOBJBone> getBones(
            AnimatedMorph morph)
    {
        List<BOBJBone> result =
                new ArrayList<BOBJBone>();

        List<BOBJArmature> armatures =
                getArmatures(morph);

        for (
                BOBJArmature armature :
                armatures
        )
        {
            if (armature == null)
            {
                continue;
            }

            /*
             * orderedBones предпочтительнее bones,
             * потому что Emoticons уже хранит порядок
             * костей для вычисления скелета.
             */
            if (
                    armature.orderedBones != null &&
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
                        result.add(bone);
                    }
                }
            }
            else if (armature.bones != null)
            {
                for (
                        BOBJBone bone :
                        armature.bones.values()
                )
                {
                    if (bone != null)
                    {
                        result.add(bone);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Ищет кость по имени.
     */
    public static BOBJBone findBone(
            AnimatedMorph morph,
            String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }

        List<BOBJBone> bones =
                getBones(morph);

        for (
                BOBJBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            if (name.equals(bone.name))
            {
                return bone;
            }
        }

        return null;
    }

    /**
     * Получает имя родительской кости.
     */
    public static String getParentName(
            BOBJBone bone)
    {
        if (bone == null)
        {
            return null;
        }

        if (
                bone.parent != null &&
                        !bone.parent.isEmpty()
        )
        {
            return bone.parent;
        }

        if (bone.parentBone != null)
        {
            return bone.parentBone.name;
        }

        return null;
    }

    /**
     * Выводит краткую информацию о найденной
     * Emoticons-модели в консоль.
     *
     * Используется только для диагностики.
     */
    public static void debugPrint(
            AnimatedMorph morph)
    {
        if (morph == null)
        {
            System.out.println(
                    "[BBS Animation Editor] " +
                            "Emoticons morph: null"
            );

            return;
        }

        AnimatorController controller =
                getController(morph);

        Animation animation =
                getAnimation(morph);

        System.out.println(
                "[BBS Animation Editor] " +
                        "Emoticons controller: " +
                        controller
        );

        System.out.println(
                "[BBS Animation Editor] " +
                        "Emoticons animation: " +
                        animation
        );

        List<AnimationMesh> meshes =
                getMeshes(morph);

        System.out.println(
                "[BBS Animation Editor] " +
                        "Emoticons meshes: " +
                        meshes.size()
        );

        List<BOBJArmature> armatures =
                getArmatures(morph);

        System.out.println(
                "[BBS Animation Editor] " +
                        "Emoticons armatures: " +
                        armatures.size()
        );

        List<BOBJBone> bones =
                getBones(morph);

        System.out.println(
                "[BBS Animation Editor] " +
                        "Emoticons bones: " +
                        bones.size()
        );

        for (
                BOBJBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }

            System.out.println(
                    "[BBS Animation Editor] " +
                            "  bone=" +
                            bone.name +
                            " parent=" +
                            getParentName(bone)
            );
        }
    }
}
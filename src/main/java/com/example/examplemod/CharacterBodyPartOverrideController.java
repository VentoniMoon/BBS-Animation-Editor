package com.example.examplemod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mchorse.blockbuster.api.ModelPose;
import mchorse.blockbuster.api.ModelTransform;
import mchorse.blockbuster.common.entity.EntityActor;
import mchorse.blockbuster_pack.morphs.CustomMorph;
import mchorse.blockbuster_pack.morphs.CustomMorph.LimbProperties;

import mchorse.emoticons.skin_n_bones.api.animation.AnimationMesh;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJArmature;
import mchorse.emoticons.skin_n_bones.api.bobj.BOBJBone;
import mchorse.emoticons.skin_n_bones.api.metamorph.AnimatedMorph;

import mchorse.metamorph.api.morphs.AbstractMorph;

import net.minecraft.nbt.NBTTagCompound;


/**
 * =========================================================
 * Character Body Part Override Controller
 * =========================================================
 *
 * Управление состоянием костей Character Timeline.
 *
 * CharacterKey является универсальным контейнером.
 *
 * Один CharacterKey может одновременно содержать:
 *
 *     Morph
 *     Skin
 *     Bones
 *     Animation
 *     Action
 *
 * Этот контроллер работает только с:
 *
 *     Data.Bones
 *
 * Остальные данные CharacterKey он не изменяет.
 *
 *
 * =========================================================
 * ВАЖНО
 * =========================================================
 *
 * CharacterKey больше НЕ обязан иметь:
 *
 *     Type.BODY_PART_OVERRIDE
 *
 * Любой существующий CharacterKey может содержать Bones.
 *
 *
 * =========================================================
 * ПОДДЕРЖИВАЕМЫЕ MORPH
 * =========================================================
 *
 * 1. CustomMorph
 *
 *     Кости находятся в:
 *
 *         custom.model.limbs
 *
 *
 * 2. AnimatedMorph / EmoticonsMorph
 *
 *     Кости находятся в:
 *
 *         AnimatedMorph
 *             -> animator
 *             -> animation
 *             -> meshes
 *             -> AnimationMesh
 *             -> BOBJArmature
 *             -> bones
 *
 *
 * Это особенно важно для:
 *
 *     mchorse.emoticons.api.metamorph.EmoticonsMorph
 *
 *
 * =========================================================
 */
public class CharacterBodyPartOverrideController
{
    private static final String BONES_TAG = "Bones";

    public static final int STATE_DEFAULT = 0;
    public static final int STATE_ENABLE = 1;
    public static final int STATE_DISABLE = 2;


    /*
     * =========================================================
     * DIAGNOSTICS
     * =========================================================
     */

    private static final String DEBUG_PREFIX =
            "[BBS Animation Editor][BodyParts]";

    private static String lastBonesDebugSignature =
            "";

    private static long lastBonesDebugTime =
            0L;

    private static final long DEBUG_REPEAT_DELAY =
            1000L;


    private static void debug(String message)
    {
        System.out.println(
                DEBUG_PREFIX + " " + message
        );
    }


    /**
     * Чтобы GUI не засыпал консоль одинаковыми строками
     * каждый render tick.
     */
    private static void debugBonesThrottled(
            String signature,
            String message)
    {
        long now =
                System.currentTimeMillis();

        if (!signature.equals(
                lastBonesDebugSignature
        )
                ||
                now - lastBonesDebugTime >=
                        DEBUG_REPEAT_DELAY)
        {
            lastBonesDebugSignature =
                    signature;

            lastBonesDebugTime =
                    now;

            debug(message);
        }
    }


    /*
     * =========================================================
     * BONE LIST
     * =========================================================
     */

    /**
     * Возвращает список всех костей текущего Morph.
     *
     * Поддерживаются:
     *
     *     CustomMorph
     *     AnimatedMorph
     *     EmoticonsMorph
     */
    public static List<String> getBones(
            AbstractMorph morph)
    {
        List<String> result =
                new ArrayList<String>();

        if (morph == null)
        {
            debugBonesThrottled(
                    "null",
                    "getBones(): morph = null"
            );

            return result;
        }


        /*
         * =====================================================
         * DIAGNOSTIC
         * =====================================================
         */

        debugBonesThrottled(
                "class:" + morph.getClass().getName(),
                "getBones(): morph class = "
                        + morph.getClass().getName()
        );


        /*
         * =====================================================
         * CUSTOM MORPH
         * =====================================================
         */

        if (morph instanceof CustomMorph)
        {
            CustomMorph custom =
                    (CustomMorph) morph;

            debugBonesThrottled(
                    "custom:" + morph.getClass().getName(),
                    "getBones(): using CustomMorph path"
            );

            if (custom.model == null)
            {
                debugBonesThrottled(
                        "custom-model-null",
                        "getBones(): CustomMorph.model = null"
                );

                return result;
            }

            if (custom.model.limbs == null)
            {
                debugBonesThrottled(
                        "custom-limbs-null",
                        "getBones(): CustomMorph.model.limbs = null"
                );

                return result;
            }

            for (String name :
                    custom.model.limbs.keySet())
            {
                if (name == null ||
                        name.isEmpty())
                {
                    continue;
                }

                result.add(name);
            }


            Collections.sort(
                    result,
                    new Comparator<String>()
                    {
                        @Override
                        public int compare(
                                String a,
                                String b)
                        {
                            return a.compareToIgnoreCase(b);
                        }
                    }
            );


            debugBonesThrottled(
                    "custom-count:" + result.size(),
                    "getBones(): CustomMorph returned "
                            + result.size()
                            + " bones"
            );

            return result;
        }


        /*
         * =====================================================
         * ANIMATED MORPH
         * =====================================================
         *
         * Это путь для:
         *
         *     EmoticonsMorph
         *     AnimatedMorph
         */

        if (morph instanceof AnimatedMorph)
        {
            AnimatedMorph animated =
                    (AnimatedMorph) morph;

            debugBonesThrottled(
                    "animated:" + morph.getClass().getName(),
                    "getBones(): using AnimatedMorph / BOBJ path"
            );


            try
            {
                /*
                 * Animator создаётся лениво.
                 */
                animated.initiateAnimator();


                if (animated.animator == null)
                {
                    debugBonesThrottled(
                            "animated-animator-null",
                            "getBones(): AnimatedMorph.animator = null"
                    );

                    return result;
                }


                if (animated.animator.animation == null)
                {
                    debugBonesThrottled(
                            "animated-animation-null",
                            "getBones(): AnimatedMorph.animator.animation = null"
                    );

                    return result;
                }


                if (animated.animator.animation.meshes == null)
                {
                    debugBonesThrottled(
                            "animated-meshes-null",
                            "getBones(): AnimatedMorph animation.meshes = null"
                    );

                    return result;
                }


                debugBonesThrottled(
                        "animated-mesh-count:"
                                + animated.animator.animation.meshes.size(),
                        "getBones(): AnimatedMorph meshes = "
                                + animated.animator.animation.meshes.size()
                );


                /*
                 * Не допускаем дубликатов костей,
                 * если несколько meshes используют одну
                 * и ту же armature.
                 */
                Set<String> uniqueBones =
                        new HashSet<String>();


                for (int meshIndex = 0;
                     meshIndex <
                             animated.animator.animation.meshes.size();
                     meshIndex++)
                {
                    AnimationMesh mesh =
                            animated.animator.animation.meshes.get(
                                    meshIndex
                            );

                    if (mesh == null)
                    {
                        debug(
                                "getBones(): mesh["
                                        + meshIndex
                                        + "] = null"
                        );

                        continue;
                    }


                    BOBJArmature armature =
                            mesh.getArmature();


                    if (armature == null)
                    {
                        debug(
                                "getBones(): mesh["
                                        + meshIndex
                                        + "] armature = null"
                        );

                        continue;
                    }


                    String armatureName =
                            armature.name == null
                                    ? "<unnamed>"
                                    : armature.name;


                    debugBonesThrottled(
                            "armature:"
                                    + armatureName
                                    + ":"
                                    + armature.bones.size(),
                            "getBones(): mesh["
                                    + meshIndex
                                    + "] armature = "
                                    + armatureName
                                    + ", bones = "
                                    + armature.bones.size()
                    );


                    /*
                     * Основной источник.
                     */
                    if (armature.bones != null)
                    {
                        for (String boneName :
                                armature.bones.keySet())
                        {
                            if (boneName == null ||
                                    boneName.isEmpty())
                            {
                                continue;
                            }

                            uniqueBones.add(
                                    boneName
                            );
                        }
                    }


                    /*
                     * Fallback.
                     *
                     * В нормальной ситуации bones уже достаточно,
                     * но orderedBones полезен для диагностики
                     * повреждённой/неполностью загруженной armature.
                     */
                    if (armature.orderedBones != null)
                    {
                        for (BOBJBone bone :
                                armature.orderedBones)
                        {
                            if (bone == null ||
                                    bone.name == null ||
                                    bone.name.isEmpty())
                            {
                                continue;
                            }

                            uniqueBones.add(
                                    bone.name
                            );
                        }
                    }
                }


                result.addAll(
                        uniqueBones
                );


                Collections.sort(
                        result,
                        new Comparator<String>()
                        {
                            @Override
                            public int compare(
                                    String a,
                                    String b)
                            {
                                return a.compareToIgnoreCase(
                                        b
                                );
                            }
                        }
                );


                /*
                 * Диагностика результата.
                 */
                if (result.isEmpty())
                {
                    debugBonesThrottled(
                            "animated-result-empty:"
                                    + morph.getClass().getName(),
                            "getBones(): AnimatedMorph returned 0 bones"
                    );
                }
                else
                {
                    debugBonesThrottled(
                            "animated-result:"
                                    + result.size(),
                            "getBones(): AnimatedMorph returned "
                                    + result.size()
                                    + " bones"
                    );
                }


                return result;
            }
            catch (Throwable error)
            {
                debug(
                        "getBones(): ERROR while reading AnimatedMorph"
                );

                error.printStackTrace();

                return result;
            }
        }


        /*
         * =====================================================
         * UNKNOWN MORPH
         * =====================================================
         */

        debugBonesThrottled(
                "unsupported:"
                        + morph.getClass().getName(),
                "getBones(): morph type is not supported"
        );


        return result;
    }


    /*
     * =========================================================
     * KEY
     * =========================================================
     */

    /**
     * Любой существующий CharacterKey может
     * содержать Bones.
     *
     * Type больше не используется.
     */
    public static boolean isOverrideKey(
            CharacterKey key)
    {
        return key != null;
    }


    /*
     * =========================================================
     * KEY STATE
     * =========================================================
     */

    public static int getKeyState(
            CharacterKey key,
            String bone)
    {
        if (!isOverrideKey(key) ||
                bone == null ||
                bone.isEmpty())
        {
            return -1;
        }


        NBTTagCompound data =
                key.getData();


        if (data == null ||
                !data.hasKey(
                        BONES_TAG,
                        10
                ))
        {
            return -1;
        }


        NBTTagCompound bones =
                data.getCompoundTag(
                        BONES_TAG
                );


        if (!bones.hasKey(
                bone,
                3
        ))
        {
            return -1;
        }


        int state =
                bones.getInteger(
                        bone
                );


        if (state < STATE_DEFAULT ||
                state > STATE_DISABLE)
        {
            return STATE_DEFAULT;
        }


        return state;
    }


    /*
     * =========================================================
     * EFFECTIVE STATE
     * =========================================================
     */

    public static boolean isEnabled(
            AbstractMorph morph,
            CharacterKey key,
            String bone)
    {
        boolean morphDefault =
                getMorphDefaultEnabled(
                        morph,
                        bone
                );


        int state =
                getKeyState(
                        key,
                        bone
                );


        if (state == STATE_ENABLE)
        {
            return true;
        }


        if (state == STATE_DISABLE)
        {
            return false;
        }


        return morphDefault;
    }


    /*
     * =========================================================
     * DEFAULT STATE
     * =========================================================
     */

    public static boolean getMorphDefaultEnabled(
            AbstractMorph morph,
            String bone)
    {
        /*
         * CustomMorph:
         *
         * fixed < 0.5 = enabled
         * fixed >= 0.5 = disabled
         */
        if (morph instanceof CustomMorph)
        {
            CustomMorph custom =
                    (CustomMorph) morph;


            ModelPose pose =
                    custom.getCurrentPose();


            if (pose == null ||
                    bone == null)
            {
                return true;
            }


            ModelTransform transform =
                    pose.limbs.get(
                            bone
                    );


            if (transform instanceof LimbProperties)
            {
                LimbProperties properties =
                        (LimbProperties) transform;


                return properties.fixed <
                        0.5F;
            }


            return true;
        }


        /*
         * AnimatedMorph / EmoticonsMorph
         *
         * Emoticons хранит базовое состояние кости
         * в AnimatedMorph.pose -> AnimatorPoseTransform.fixed.
         *
         * FIXED (0)    = стандартная Emoticons-анимация OFF
         * ANIMATED (1) = стандартная Emoticons-анимация ON
         *
         * Если записи в pose нет, Emoticons использует
         * обычное состояние ON.
         */
        if (morph instanceof AnimatedMorph)
        {
            AnimatedMorph animated =
                    (AnimatedMorph) morph;

            if (bone == null ||
                    bone.isEmpty() ||
                    animated.pose == null)
            {
                return true;
            }

            mchorse.emoticons.skin_n_bones.api.animation.model.AnimatorPoseTransform transform =
                    animated.pose.bones.get(bone);

            if (transform == null)
            {
                return true;
            }

            return transform.fixed >= 0.5F;
        }

        return true;
    }


    /*
     * =========================================================
     * TIMELINE EFFECTIVE STATE
     * =========================================================
     */

    /**
     * Возвращает последний явный Body Part state для кости,
     * учитывая наследование CharacterKey.
     *
     * Последний Morph key является границей состояния:
     * новый Morph начинает с собственного состояния костей.
     *
     * Skin key состояние Body Parts НЕ сбрасывает.
     */
    public static int getEffectiveKeyState(
            BlockbusterSceneActorData actorData,
            int frame,
            String bone)
    {
        if (actorData == null ||
                bone == null ||
                bone.isEmpty())
        {
            return -1;
        }

        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline == null)
        {
            return -1;
        }

        int morphBoundary = -1;

        /*
         * Сначала находим последний Morph key.
         * CharacterKey универсальный, поэтому смотрим
         * на фактическое наличие Morph, а не на Type.
         */
        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }

            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null ||
                        key.getFrame() > frame ||
                        !key.hasMorph())
                {
                    continue;
                }

                morphBoundary =
                        Math.max(
                                morphBoundary,
                                key.getFrame()
                        );
            }
        }

        int result = -1;
        int latestFrame = Integer.MIN_VALUE;

        /*
         * После последнего Morph key старые Body Part
         * overrides уже не относятся к новому Morph.
         */
        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }

            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null)
                {
                    continue;
                }

                int keyFrame = key.getFrame();

                if (keyFrame > frame ||
                        keyFrame < morphBoundary)
                {
                    continue;
                }

                int state =
                        getKeyState(
                                key,
                                bone
                        );

                if (state < 0)
                {
                    continue;
                }

                if (keyFrame >= latestFrame)
                {
                    latestFrame = keyFrame;
                    result = state;
                }
            }
        }

        return result;
    }

    /**
     * Возвращает фактическое состояние кости:
     * true = стандартная Emoticons-анимация включена,
     * false = выключена.
     *
     * При отсутствии explicit override используется
     * состояние самого AnimatedMorph.
     */
    public static boolean isEnabled(
            AbstractMorph morph,
            BlockbusterSceneActorData actorData,
            int frame,
            String bone)
    {
        int state =
                getEffectiveKeyState(
                        actorData,
                        frame,
                        bone
                );

        if (state == STATE_ENABLE)
        {
            return true;
        }

        if (state == STATE_DISABLE)
        {
            return false;
        }

        return getMorphDefaultEnabled(
                morph,
                bone
        );
    }


    /*
     * =========================================================
     * TOGGLE
     * =========================================================
     */

    public static boolean toggleBone(
            CharacterKey key,
            AbstractMorph morph,
            String bone)
    {
        if (!isOverrideKey(key) ||
                bone == null ||
                bone.isEmpty())
        {
            return false;
        }


        boolean current =
                isEnabled(
                        morph,
                        key,
                        bone
                );


        boolean desired =
                !current;


        boolean morphDefault =
                getMorphDefaultEnabled(
                        morph,
                        bone
                );


        int state;


        if (desired == morphDefault)
        {
            state =
                    STATE_DEFAULT;
        }
        else if (desired)
        {
            state =
                    STATE_ENABLE;
        }
        else
        {
            state =
                    STATE_DISABLE;
        }


        debug(
                "toggleBone(): keyFrame="
                        + key.getFrame()
                        + " bone="
                        + bone
                        + " current="
                        + current
                        + " desired="
                        + desired
                        + " state="
                        + state
        );


        return setBoneState(
                key,
                bone,
                state
        );
    }


    /*
     * =========================================================
     * SET STATE
     * =========================================================
     */

    public static boolean setBoneState(
            CharacterKey key,
            String bone,
            int state)
    {
        if (!isOverrideKey(key) ||
                bone == null ||
                bone.isEmpty())
        {
            return false;
        }


        if (state < STATE_DEFAULT ||
                state > STATE_DISABLE)
        {
            state =
                    STATE_DEFAULT;
        }


        NBTTagCompound data =
                key.getData();


        if (data == null)
        {
            data =
                    new NBTTagCompound();
        }


        NBTTagCompound bones;


        if (data.hasKey(
                BONES_TAG,
                10
        ))
        {
            bones =
                    data.getCompoundTag(
                            BONES_TAG
                    );
        }
        else
        {
            bones =
                    new NBTTagCompound();
        }


        int previousState =
                bones.hasKey(
                        bone,
                        3
                )
                        ? bones.getInteger(
                        bone
                )
                        : -1;


        if (previousState == state)
        {
            return false;
        }


        bones.setInteger(
                bone,
                state
        );


        data.setTag(
                BONES_TAG,
                bones
        );


        /*
         * ВАЖНО:
         *
         * setData() меняет только Data.Bones.
         *
         * Morph/Skin внутри CharacterKey
         * сохраняются.
         */
        key.setData(
                data
        );


        debug(
                "setBoneState(): frame="
                        + key.getFrame()
                        + " bone="
                        + bone
                        + " previous="
                        + previousState
                        + " new="
                        + state
        );


        return true;
    }


    /*
     * =========================================================
     * REMOVE
     * =========================================================
     */

    public static boolean removeBoneOverride(
            CharacterKey key,
            String bone)
    {
        if (!isOverrideKey(key) ||
                bone == null ||
                bone.isEmpty())
        {
            return false;
        }


        NBTTagCompound data =
                key.getData();


        if (data == null ||
                !data.hasKey(
                        BONES_TAG,
                        10
                ))
        {
            return false;
        }


        NBTTagCompound bones =
                data.getCompoundTag(
                        BONES_TAG
                );


        if (!bones.hasKey(
                bone,
                3
        ))
        {
            return false;
        }


        bones.removeTag(
                bone
        );


        if (bones.hasNoTags())
        {
            data.removeTag(
                    BONES_TAG
            );
        }
        else
        {
            data.setTag(
                    BONES_TAG,
                    bones
            );
        }


        key.setData(
                data
        );


        debug(
                "removeBoneOverride(): frame="
                        + key.getFrame()
                        + " bone="
                        + bone
        );


        return true;
    }


    /*
     * =========================================================
     * APPLY CUSTOM MORPH
     * =========================================================
     *
     * Эта часть сохраняет старую рабочую логику
     * CustomMorph.
     */
    public static void apply(
            AbstractMorph morph,
            CharacterTimelineController timeline,
            int frame)
    {
        if (morph == null ||
                timeline == null)
        {
            return;
        }


        /*
         * Пока фактическое применение pose override
         * выполняется для CustomMorph.
         */
        if (!(morph instanceof CustomMorph))
        {
            return;
        }


        CustomMorph custom =
                (CustomMorph) morph;


        if (custom.model == null ||
                custom.model.limbs == null)
        {
            return;
        }


        List<String> bones =
                getBones(custom);


        if (bones.isEmpty())
        {
            return;
        }


        boolean hasOverride =
                false;


        for (String bone :
                bones)
        {
            if (findLatestState(
                    timeline,
                    frame,
                    bone
            ) != -1)
            {
                hasOverride =
                        true;

                break;
            }
        }


        if (!hasOverride)
        {
            return;
        }


        ensureCustomPose(
                custom
        );


        if (custom.customPose == null)
        {
            return;
        }


        for (String bone :
                bones)
        {
            int state =
                    findLatestState(
                            timeline,
                            frame,
                            bone
                    );


            if (state == -1)
            {
                continue;
            }


            ModelTransform transform =
                    custom.customPose.limbs.get(
                            bone
                    );


            if (!(transform instanceof LimbProperties))
            {
                LimbProperties properties =
                        new LimbProperties();


                if (transform != null)
                {
                    properties.copy(
                            transform
                    );
                }


                custom.customPose.limbs.put(
                        bone,
                        properties
                );


                transform =
                        properties;
            }


            LimbProperties properties =
                    (LimbProperties) transform;


            if (state == STATE_DEFAULT)
            {
                boolean enabled =
                        getMorphDefaultEnabled(
                                custom,
                                bone
                        );


                properties.fixed =
                        enabled
                                ? 0F
                                : 1F;
            }
            else if (state == STATE_ENABLE)
            {
                properties.fixed =
                        0F;
            }
            else if (state == STATE_DISABLE)
            {
                properties.fixed =
                        1F;
            }
        }
    }


    /*
     * =========================================================
     * CUSTOM POSE
     * =========================================================
     */

    private static void ensureCustomPose(
            CustomMorph custom)
    {
        if (custom == null)
        {
            return;
        }


        if (custom.customPose != null)
        {
            return;
        }


        ModelPose source =
                custom.getCurrentPose();


        CustomMorph.ModelProperties result =
                new CustomMorph.ModelProperties();


        if (source != null)
        {
            result.size =
                    new float[]
                            {
                                    source.size[0],
                                    source.size[1],
                                    source.size[2]
                            };


            for (Map.Entry<String, ModelTransform> entry :
                    source.limbs.entrySet())
            {
                LimbProperties properties =
                        new LimbProperties();


                properties.copy(
                        entry.getValue()
                );


                result.limbs.put(
                        entry.getKey(),
                        properties
                );
            }


            for (
                    mchorse.blockbuster.api.formats.obj.ShapeKey shape :
                    source.shapes
            )
            {
                result.shapes.add(
                        shape.copy()
                );
            }
        }


        if (custom.model != null)
        {
            result.updateLimbs(
                    custom.model,
                    false
            );
        }


        custom.customPose =
                result;
    }


    /*
     * =========================================================
     * FIND LATEST STATE
     * =========================================================
     */

    public static int findLatestState(
            CharacterTimelineController timeline,
            int frame,
            String bone)
    {
        if (timeline == null ||
                bone == null ||
                bone.isEmpty())
        {
            return -1;
        }


        int latestFrame =
                -1;


        int latestState =
                -1;


        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }


            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null)
                {
                    continue;
                }


                NBTTagCompound data =
                        key.getData();


                if (data == null ||
                        !data.hasKey(
                                BONES_TAG,
                                10
                        ))
                {
                    continue;
                }


                NBTTagCompound bones =
                        data.getCompoundTag(
                                BONES_TAG
                        );


                if (!bones.hasKey(
                        bone,
                        3
                ))
                {
                    continue;
                }


                if (key.getFrame() > frame)
                {
                    continue;
                }


                int keyFrame =
                        key.getFrame();


                if (keyFrame >= latestFrame)
                {
                    latestFrame =
                            keyFrame;


                    latestState =
                            bones.getInteger(
                                    bone
                            );


                    if (latestState <
                            STATE_DEFAULT ||
                            latestState >
                                    STATE_DISABLE)
                    {
                        latestState =
                                STATE_DEFAULT;
                    }
                }
            }
        }


        return latestState;
    }


    /*
     * =========================================================
     * SIGNATURE
     * =========================================================
     */

    public static String getSignature(
            CharacterTimelineController timeline,
            int frame)
    {
        if (timeline == null)
        {
            return "";
        }


        StringBuilder builder =
                new StringBuilder();


        List<String> values =
                new ArrayList<String>();


        for (CharacterTrack track :
                timeline.getTracks())
        {
            if (track == null)
            {
                continue;
            }


            for (CharacterKey key :
                    track.getKeys())
            {
                if (key == null ||
                        key.getFrame() > frame)
                {
                    continue;
                }


                NBTTagCompound data =
                        key.getData();


                if (data == null ||
                        !data.hasKey(
                                BONES_TAG,
                                10
                        ))
                {
                    continue;
                }


                NBTTagCompound bones =
                        data.getCompoundTag(
                                BONES_TAG
                        );


                for (String bone :
                        bones.getKeySet())
                {
                    values.add(
                            key.getFrame()
                                    + ":"
                                    + bone
                                    + "="
                                    + bones.getInteger(
                                    bone
                            )
                    );
                }
            }
        }


        Collections.sort(
                values
        );


        for (String value :
                values)
        {
            builder.append(
                    value
            );


            builder.append(
                    ';'
            );
        }


        return builder.toString();
    }
}
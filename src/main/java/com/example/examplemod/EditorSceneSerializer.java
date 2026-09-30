package com.example.examplemod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mchorse.mclib.utils.keyframes.KeyframeEasing;
import mchorse.mclib.utils.keyframes.KeyframeInterpolation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Сериализация данных, принадлежащих именно BBS Animation Editor.
 *
 * Оригинальные Blockbuster Scene и Record этим классом
 * НЕ изменяются.
 *
 * Editor data хранится отдельно:
 *
 *     blockbuster/editor/<scene>.editor.dat
 *
 * Сейчас сохраняются:
 *
 * - Scene position
 * - Character Timeline
 * - Pose / AnimationData
 * - AnimationBone
 * - AnimationKeyframe
 * - AnimationTransform
 * - interpolation
 * - easing
 * - curve parameters
 *
 * UI-состояние НЕ сохраняется:
 *
 * - selected actor
 * - selected scene
 * - current frame
 * - playing
 * - dropdowns
 * - theme
 * - camera
 *
 * Body Parts будут добавлены отдельным блоком,
 * когда появится полноценная модель Body Part.
 */
public final class EditorSceneSerializer
{
    /*
     * =========================================================
     * FORMAT
     * =========================================================
     */

    public static final int FORMAT_VERSION = 1;


    private EditorSceneSerializer()
    {
    }


    /*
     * =========================================================
     * WRITE ROOT
     * =========================================================
     */

    public static NBTTagCompound write(
            EditorSceneState state)
    {
        NBTTagCompound root =
                new NBTTagCompound();


        /*
         * Версия нашего editor.dat.
         *
         * Она НЕ связана с Version Blockbuster Record.
         */
        root.setInteger(
                "FormatVersion",
                FORMAT_VERSION
        );


        if (state == null)
        {
            return root;
        }


        /*
         * =====================================================
         * SCENE
         * =====================================================
         */

        NBTTagCompound scene =
                new NBTTagCompound();


        /*
         * -----------------------------------------------------
         * Scene position
         * -----------------------------------------------------
         */

        scene.setDouble(
                "X",
                state.getSceneX()
        );

        scene.setDouble(
                "Y",
                state.getSceneY()
        );

        scene.setDouble(
                "Z",
                state.getSceneZ()
        );


        /*
         * -----------------------------------------------------
         * Character Timeline
         * -----------------------------------------------------
         */

        NBTTagList actors =
                new NBTTagList();


        for (
                BlockbusterSceneActorData actorData :
                state.getActors()
        )
        {
            if (actorData == null)
            {
                continue;
            }

            actors.appendTag(
                    writeActor(
                            actorData
                    )
            );
        }


        scene.setTag(
                "Actors",
                actors
        );


        /*
         * -----------------------------------------------------
         * Pose / Animation Data
         * -----------------------------------------------------
         */

        scene.setTag(
                "AnimationData",
                writeAnimationData(
                        state.getAnimationData()
                )
        );


        /*
         * В будущем здесь можно будет добавить:
         *
         * BodyParts
         *
         * но пока этот блок отсутствует.
         */


        root.setTag(
                "Scene",
                scene
        );


        return root;
    }


    /*
     * =========================================================
     * READ ROOT
     * =========================================================
     */

    public static void read(
            EditorSceneState state,
            NBTTagCompound root)
    {
        if (state == null || root == null)
        {
            return;
        }


        /*
         * -----------------------------------------------------
         * FORMAT VERSION
         * -----------------------------------------------------
         */

        int version =
                root.hasKey(
                        "FormatVersion",
                        3
                )
                        ? root.getInteger(
                        "FormatVersion"
                )
                        : 1;


        if (version > FORMAT_VERSION)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Warning: editor data format "
                            + version
                            + " is newer than supported format "
                            + FORMAT_VERSION
            );
        }


        /*
         * На данный момент формат 1 совместим
         * с текущей моделью.
         *
         * Мы не прекращаем загрузку для неизвестной
         * будущей версии, поскольку NBT позволяет
         * безопасно пропускать неизвестные поля.
         */


        /*
         * -----------------------------------------------------
         * SCENE
         * -----------------------------------------------------
         */

        if (!root.hasKey(
                "Scene",
                10
        ))
        {
            return;
        }


        NBTTagCompound scene =
                root.getCompoundTag(
                        "Scene"
                );


        /*
         * =====================================================
         * SCENE POSITION
         * =====================================================
         */

        double x =
                scene.hasKey("X")
                        ? scene.getDouble("X")
                        : state.getSceneX();

        double y =
                scene.hasKey("Y")
                        ? scene.getDouble("Y")
                        : state.getSceneY();

        double z =
                scene.hasKey("Z")
                        ? scene.getDouble("Z")
                        : state.getSceneZ();


        /*
         * Внутри загрузки вызывается setScenePosition(),
         * поэтому saveController временно станет dirty.
         *
         * EditorSaveController после загрузки сбрасывает
         * dirty обратно в false.
         */
        state.setScenePosition(
                x,
                y,
                z
        );


        /*
         * =====================================================
         * CHARACTER TIMELINE
         * =====================================================
         */

        if (scene.hasKey(
                "Actors",
                9
        ))
        {
            readActors(
                    state,
                    scene.getTagList(
                            "Actors",
                            10
                    )
            );
        }


        /*
         * =====================================================
         * POSE / ANIMATION DATA
         * =====================================================
         */

        state.getAnimationData().clear();


        if (scene.hasKey(
                "AnimationData",
                10
        ))
        {
            readAnimationData(
                    state.getAnimationData(),
                    scene.getCompoundTag(
                            "AnimationData"
                    )
            );
        }


        /*
         * =====================================================
         * BODY PARTS
         * =====================================================
         *
         * Пока намеренно отсутствуют.
         *
         * Когда модель Body Parts будет готова,
         * сюда добавится:
         *
         * readBodyParts(...)
         *
         * При этом старые editor.dat останутся
         * полностью совместимыми.
         */
    }


    /*
     * =========================================================
     * CHARACTER ACTORS
     * =========================================================
     */

    private static NBTTagCompound writeActor(
            BlockbusterSceneActorData actorData)
    {
        NBTTagCompound tag =
                new NBTTagCompound();


        /*
         * У каждого actor editor-data есть
         * собственный стабильный Id.
         *
         * Именно по нему данные будут сопоставляться
         * после загрузки сцены.
         */
        tag.setString(
                "Id",
                actorData.getId()
        );


        /*
         * Character Timeline.
         *
         * Даже пустой Timeline сохраняется:
         *
         * это позволяет отличить отсутствие
         * editor-data от существующего пустого Timeline.
         */
        CharacterTimelineController timeline =
                actorData.getCharacterTimeline();

        if (timeline != null)
        {
            tag.setTag(
                    "CharacterTimeline",
                    timeline.toNBT()
            );
        }


        return tag;
    }


    private static void readActors(
            EditorSceneState state,
            NBTTagList actors)
    {
        if (state == null || actors == null)
        {
            return;
        }


        /*
         * -----------------------------------------------------
         * Build lookup table by Actor Id.
         * -----------------------------------------------------
         */

        Map<String, NBTTagCompound> actorTags =
                new HashMap<String, NBTTagCompound>();


        for (
                int i = 0;
                i < actors.tagCount();
                i++
        )
        {
            NBTTagCompound actorTag =
                    actors.getCompoundTagAt(i);


            String actorId =
                    actorTag.getString(
                            "Id"
                    );


            if (
                    actorId == null ||
                            actorId.isEmpty()
            )
            {
                continue;
            }


            actorTags.put(
                    actorId,
                    actorTag
            );
        }


        /*
         * -----------------------------------------------------
         * Apply data to current actors.
         * -----------------------------------------------------
         */

        for (
                BlockbusterSceneActorData actorData :
                state.getActors()
        )
        {
            if (actorData == null)
            {
                continue;
            }


            String actorId =
                    actorData.getId();


            if (
                    actorId == null ||
                            actorId.isEmpty()
            )
            {
                continue;
            }


            NBTTagCompound actorTag =
                    actorTags.get(
                            actorId
                    );


            if (actorTag == null)
            {
                /*
                 * Для нового/изменённого актёра
                 * editor-data просто отсутствуют.
                 */
                continue;
            }


            readActor(
                    actorData,
                    actorTag
            );
        }
    }


    private static void readActor(
            BlockbusterSceneActorData actorData,
            NBTTagCompound tag)
    {
        if (actorData == null || tag == null)
        {
            return;
        }


        CharacterTimelineController target =
                actorData.getCharacterTimeline();


        if (target == null)
        {
            return;
        }


        /*
         * Если Timeline присутствует в файле,
         * полностью восстанавливаем его содержимое.
         */
        if (tag.hasKey(
                "CharacterTimeline",
                10
        ))
        {
            CharacterTimelineController loaded =
                    CharacterTimelineController.fromNBT(
                            tag.getCompoundTag(
                                    "CharacterTimeline"
                            )
                    );


            /*
             * Контроллер принадлежит ActorData,
             * поэтому сам объект не заменяем.
             *
             * Это важно для ссылок UI/других систем.
             */
            target.clear();


            for (
                    CharacterTrack track :
                    loaded.getTracks()
            )
            {
                if (track == null)
                {
                    continue;
                }

                target.addTrack(
                        track
                );
            }
        }
        else
        {
            /*
             * Если блока нет, оставляем пустой Timeline.
             */
            target.clear();
        }
    }


    /*
     * =========================================================
     * SCENE ANIMATION DATA
     * =========================================================
     */

    private static NBTTagCompound writeAnimationData(
            SceneAnimationData animationData)
    {
        NBTTagCompound tag =
                new NBTTagCompound();


        if (animationData == null)
        {
            return tag;
        }


        NBTTagList actors =
                new NBTTagList();


        for (
                Map.Entry<String, ActorAnimationData> entry :
                animationData.getActors().entrySet()
        )
        {
            ActorAnimationData actor =
                    entry.getValue();


            if (actor == null)
            {
                continue;
            }


            String actorId =
                    entry.getKey();


            if (
                    actorId == null ||
                            actorId.isEmpty()
            )
            {
                continue;
            }


            NBTTagCompound actorTag =
                    new NBTTagCompound();


            actorTag.setString(
                    "Id",
                    actorId
            );


            actorTag.setTag(
                    "Bones",
                    writeBones(
                            actor.getBones()
                    )
            );


            actors.appendTag(
                    actorTag
            );
        }


        tag.setTag(
                "Actors",
                actors
        );


        return tag;
    }


    private static void readAnimationData(
            SceneAnimationData animationData,
            NBTTagCompound tag)
    {
        if (animationData == null || tag == null)
        {
            return;
        }


        if (!tag.hasKey(
                "Actors",
                9
        ))
        {
            return;
        }


        NBTTagList actors =
                tag.getTagList(
                        "Actors",
                        10
                );


        for (
                int i = 0;
                i < actors.tagCount();
                i++
        )
        {
            NBTTagCompound actorTag =
                    actors.getCompoundTagAt(i);


            String actorId =
                    actorTag.getString(
                            "Id"
                    );


            if (
                    actorId == null ||
                            actorId.isEmpty()
            )
            {
                continue;
            }


            ActorAnimationData actor =
                    new ActorAnimationData(
                            actorId
                    );


            readBones(
                    actor.getBones(),
                    actorTag
            );


            animationData.putActor(
                    actorId,
                    actor
            );
        }
    }


    /*
     * =========================================================
     * BONES
     * =========================================================
     */

    private static NBTTagList writeBones(
            List<AnimationBone> bones)
    {
        NBTTagList list =
                new NBTTagList();


        if (bones == null)
        {
            return list;
        }


        for (
                AnimationBone bone :
                bones
        )
        {
            if (bone == null)
            {
                continue;
            }


            String name =
                    bone.getName();


            if (
                    name == null ||
                            name.isEmpty()
            )
            {
                continue;
            }


            NBTTagCompound tag =
                    new NBTTagCompound();


            /*
             * -------------------------------------------------
             * Identity
             * -------------------------------------------------
             */

            tag.setString(
                    "Name",
                    name
            );


            /*
             * -------------------------------------------------
             * Parent
             * -------------------------------------------------
             */

            if (bone.getParent() != null)
            {
                tag.setString(
                        "Parent",
                        bone.getParent().getName()
                );
            }


            /*
             * -------------------------------------------------
             * Local position
             * -------------------------------------------------
             */

            tag.setFloat(
                    "LocalX",
                    bone.getLocalX()
            );

            tag.setFloat(
                    "LocalY",
                    bone.getLocalY()
            );

            tag.setFloat(
                    "LocalZ",
                    bone.getLocalZ()
            );


            /*
             * -------------------------------------------------
             * Keyframes
             * -------------------------------------------------
             */

            tag.setTag(
                    "Keyframes",
                    writeKeyframes(
                            bone.getKeyframes()
                    )
            );


            list.appendTag(
                    tag
            );
        }


        return list;
    }


    private static void readBones(
            List<AnimationBone> target,
            NBTTagCompound actorTag)
    {
        if (target == null || actorTag == null)
        {
            return;
        }


        target.clear();


        if (!actorTag.hasKey(
                "Bones",
                9
        ))
        {
            return;
        }


        NBTTagList list =
                actorTag.getTagList(
                        "Bones",
                        10
                );


        /*
         * =====================================================
         * FIRST PASS
         * =====================================================
         *
         * Создаём все Bone независимо от Parent.
         */

        Map<String, AnimationBone> boneMap =
                new HashMap<String, AnimationBone>();


        for (
                int i = 0;
                i < list.tagCount();
                i++
        )
        {
            NBTTagCompound tag =
                    list.getCompoundTagAt(i);


            String name =
                    tag.getString(
                            "Name"
                    );


            if (
                    name == null ||
                            name.isEmpty() ||
                            boneMap.containsKey(name)
            )
            {
                continue;
            }


            AnimationBone bone =
                    new AnimationBone(
                            name
                    );


            /*
             * Local position.
             */

            bone.setLocalPosition(
                    tag.getFloat(
                            "LocalX"
                    ),
                    tag.getFloat(
                            "LocalY"
                    ),
                    tag.getFloat(
                            "LocalZ"
                    )
            );


            /*
             * Keyframes.
             */

            readKeyframes(
                    bone.getKeyframes(),
                    tag
            );


            boneMap.put(
                    name,
                    bone
            );


            target.add(
                    bone
            );
        }


        /*
         * =====================================================
         * SECOND PASS
         * =====================================================
         *
         * Восстанавливаем Parent -> Child.
         */

        for (
                int i = 0;
                i < list.tagCount();
                i++
        )
        {
            NBTTagCompound tag =
                    list.getCompoundTagAt(i);


            String name =
                    tag.getString(
                            "Name"
                    );


            if (!tag.hasKey(
                    "Parent"
            ))
            {
                continue;
            }


            String parentName =
                    tag.getString(
                            "Parent"
                    );


            if (parentName.isEmpty())
            {
                continue;
            }


            AnimationBone bone =
                    boneMap.get(
                            name
                    );


            AnimationBone parent =
                    boneMap.get(
                            parentName
                    );


            if (
                    bone != null &&
                            parent != null &&
                            bone != parent
            )
            {
                parent.addChild(
                        bone
                );
            }
        }
    }


    /*
     * =========================================================
     * KEYFRAMES
     * =========================================================
     */

    private static NBTTagList writeKeyframes(
            List<AnimationKeyframe> keyframes)
    {
        NBTTagList list =
                new NBTTagList();


        if (keyframes == null)
        {
            return list;
        }


        for (
                AnimationKeyframe keyframe :
                keyframes
        )
        {
            if (keyframe == null)
            {
                continue;
            }


            NBTTagCompound tag =
                    new NBTTagCompound();


            /*
             * -------------------------------------------------
             * Frame
             * -------------------------------------------------
             */

            tag.setInteger(
                    "Frame",
                    Math.max(
                            0,
                            keyframe.getFrame()
                    )
            );


            /*
             * -------------------------------------------------
             * Transform
             * -------------------------------------------------
             */

            AnimationTransform transform =
                    keyframe.getTransform();


            if (transform != null)
            {
                NBTTagCompound transformTag =
                        new NBTTagCompound();


                /*
                 * Position.
                 */

                transformTag.setFloat(
                        "PX",
                        transform.getPositionX()
                );

                transformTag.setFloat(
                        "PY",
                        transform.getPositionY()
                );

                transformTag.setFloat(
                        "PZ",
                        transform.getPositionZ()
                );


                /*
                 * Rotation.
                 */

                transformTag.setFloat(
                        "RX",
                        transform.getRotationX()
                );

                transformTag.setFloat(
                        "RY",
                        transform.getRotationY()
                );

                transformTag.setFloat(
                        "RZ",
                        transform.getRotationZ()
                );


                /*
                 * Scale.
                 */

                transformTag.setFloat(
                        "SX",
                        transform.getScaleX()
                );

                transformTag.setFloat(
                        "SY",
                        transform.getScaleY()
                );

                transformTag.setFloat(
                        "SZ",
                        transform.getScaleZ()
                );


                tag.setTag(
                        "Transform",
                        transformTag
                );
            }


            /*
             * -------------------------------------------------
             * Interpolation
             * -------------------------------------------------
             */

            if (
                    keyframe.getInterpolation() != null
            )
            {
                tag.setString(
                        "Interpolation",
                        keyframe
                                .getInterpolation()
                                .name()
                );
            }


            /*
             * -------------------------------------------------
             * Easing
             * -------------------------------------------------
             */

            if (
                    keyframe.getEasing() != null
            )
            {
                tag.setString(
                        "Easing",
                        keyframe
                                .getEasing()
                                .name()
                );
            }


            /*
             * -------------------------------------------------
             * Curve parameters
             * -------------------------------------------------
             */

            tag.setFloat(
                    "RXCurve",
                    keyframe.getRX()
            );

            tag.setFloat(
                    "RYCurve",
                    keyframe.getRY()
            );

            tag.setFloat(
                    "LXCurve",
                    keyframe.getLX()
            );

            tag.setFloat(
                    "LYCurve",
                    keyframe.getLY()
            );


            list.appendTag(
                    tag
            );
        }


        return list;
    }


    private static void readKeyframes(
            List<AnimationKeyframe> target,
            NBTTagCompound boneTag)
    {
        if (target == null || boneTag == null)
        {
            return;
        }


        target.clear();


        if (!boneTag.hasKey(
                "Keyframes",
                9
        ))
        {
            return;
        }


        NBTTagList list =
                boneTag.getTagList(
                        "Keyframes",
                        10
                );


        for (
                int i = 0;
                i < list.tagCount();
                i++
        )
        {
            NBTTagCompound tag =
                    list.getCompoundTagAt(i);


            /*
             * -------------------------------------------------
             * Frame
             * -------------------------------------------------
             */

            int frame =
                    tag.hasKey(
                            "Frame"
                    )
                            ? tag.getInteger(
                            "Frame"
                    )
                            : 0;


            AnimationKeyframe keyframe =
                    new AnimationKeyframe(
                            Math.max(
                                    0,
                                    frame
                            )
                    );


            /*
             * -------------------------------------------------
             * Transform
             * -------------------------------------------------
             */

            if (tag.hasKey(
                    "Transform",
                    10
            ))
            {
                NBTTagCompound transformTag =
                        tag.getCompoundTag(
                                "Transform"
                        );


                AnimationTransform transform =
                        keyframe.getTransform();


                /*
                 * Position.
                 */

                transform.setPosition(
                        transformTag.getFloat(
                                "PX"
                        ),
                        transformTag.getFloat(
                                "PY"
                        ),
                        transformTag.getFloat(
                                "PZ"
                        )
                );


                /*
                 * Rotation.
                 */

                transform.setRotation(
                        transformTag.getFloat(
                                "RX"
                        ),
                        transformTag.getFloat(
                                "RY"
                        ),
                        transformTag.getFloat(
                                "RZ"
                        )
                );


                /*
                 * Scale.
                 */

                transform.setScale(
                        transformTag.getFloat(
                                "SX"
                        ),
                        transformTag.getFloat(
                                "SY"
                        ),
                        transformTag.getFloat(
                                "SZ"
                        )
                );
            }


            /*
             * -------------------------------------------------
             * Interpolation
             * -------------------------------------------------
             */

            if (tag.hasKey(
                    "Interpolation"
            ))
            {
                String interpolationName =
                        tag.getString(
                                "Interpolation"
                        );


                try
                {
                    keyframe.setInterpolation(
                            KeyframeInterpolation.valueOf(
                                    interpolationName
                            )
                    );
                }
                catch (IllegalArgumentException ignored)
                {
                    /*
                     * Неизвестный тип интерполяции.
                     *
                     * Оставляем безопасный default:
                     * LINEAR.
                     */
                }
            }


            /*
             * -------------------------------------------------
             * Easing
             * -------------------------------------------------
             */

            if (tag.hasKey(
                    "Easing"
            ))
            {
                String easingName =
                        tag.getString(
                                "Easing"
                        );


                try
                {
                    keyframe.setEasing(
                            KeyframeEasing.valueOf(
                                    easingName
                            )
                    );
                }
                catch (IllegalArgumentException ignored)
                {
                    /*
                     * Оставляем default:
                     * IN.
                     */
                }
            }


            /*
             * -------------------------------------------------
             * Curve
             * -------------------------------------------------
             */

            if (tag.hasKey(
                    "RXCurve"
            ))
            {
                keyframe.setRX(
                        tag.getFloat(
                                "RXCurve"
                        )
                );
            }


            if (tag.hasKey(
                    "RYCurve"
            ))
            {
                keyframe.setRY(
                        tag.getFloat(
                                "RYCurve"
                        )
                );
            }


            if (tag.hasKey(
                    "LXCurve"
            ))
            {
                keyframe.setLX(
                        tag.getFloat(
                                "LXCurve"
                        )
                );
            }


            if (tag.hasKey(
                    "LYCurve"
            ))
            {
                keyframe.setLY(
                        tag.getFloat(
                                "LYCurve"
                        )
                );
            }


            target.add(
                    keyframe
            );
        }


        /*
         * AnimationBone ожидает отсортированные
         * keyframes.
         */

        sortKeyframes(
                target
        );
    }


    /*
     * =========================================================
     * KEYFRAME SORT
     * =========================================================
     */

    private static void sortKeyframes(
            List<AnimationKeyframe> keyframes)
    {
        if (keyframes == null ||
                keyframes.size() < 2)
        {
            return;
        }


        /*
         * Сортировка insertion-sort.
         *
         * Списки ключей маленькие, поэтому здесь
         * не нужна сложная структура.
         */
        for (
                int i = 1;
                i < keyframes.size();
                i++
        )
        {
            AnimationKeyframe current =
                    keyframes.get(i);


            if (current == null)
            {
                continue;
            }


            int currentFrame =
                    current.getFrame();


            int j =
                    i - 1;


            while (
                    j >= 0 &&
                            keyframes.get(j) != null &&
                            keyframes
                                    .get(j)
                                    .getFrame()
                                    > currentFrame
            )
            {
                keyframes.set(
                        j + 1,
                        keyframes.get(j)
                );

                j--;
            }


            keyframes.set(
                    j + 1,
                    current
            );
        }
    }
}
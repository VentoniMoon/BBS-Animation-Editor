package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockbusterModelAccess
{
    private Object model;

    /*
     * Исходная структура скелета Blockbuster.
     *
     * ВАЖНО:
     *
     * rotationPointX/Y/Z внутри ModelCustomRenderer
     * являются изменяемыми runtime-значениями.
     *
     * Поэтому мы считываем их один раз при загрузке модели
     * и дальше используем именно этот снимок.
     */
    private final List<BlockbusterLimbData> originalLimbData =
            new ArrayList<BlockbusterLimbData>();

    public BlockbusterModelAccess(Object model)
    {
        this.model = model;
    }

    public Object getModel()
    {
        return this.model;
    }

    public void setModel(Object model)
    {
        this.model = model;

        /*
         * При смене модели старые данные скелета
         * больше использовать нельзя.
         */
        this.originalLimbData.clear();
    }

    public boolean isValid()
    {
        return this.model != null;
    }

    /**
     * Загружает уже существующую модель Blockbuster
     * из ModelCustom.MODELS.
     */
    public boolean loadModelByName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }

        try
        {
            Class<?> modelCustomClass =
                    Class.forName(
                            "mchorse.blockbuster.client.model.ModelCustom"
                    );

            Field modelsField =
                    modelCustomClass.getField("MODELS");

            Object models =
                    modelsField.get(null);

            if (!(models instanceof Map))
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "ModelCustom.MODELS is not a Map"
                );

                return false;
            }

            Object loadedModel =
                    ((Map<?, ?>) models).get(name);

            if (loadedModel == null)
            {
                System.out.println(
                        "[BBS Animation Editor] "
                                + "Model not found in MODELS: "
                                + name
                );

                return false;
            }

            /*
             * Сохраняем модель.
             */
            this.model = loadedModel;

            /*
             * Очень важно:
             *
             * Считываем исходный скелет ДО того,
             * как редактор начнёт применять свои transforms.
             */
            captureOriginalLimbData();

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Loaded Blockbuster model: "
                            + name
            );

            debugBones();

            return true;
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Failed to load Blockbuster model: "
                            + name
            );

            e.printStackTrace();

            return false;
        }
    }

    /**
     * Ищет реальную кость Blockbuster по имени.
     */
    public Object findBone(String name)
    {
        if (this.model == null || name == null)
        {
            return null;
        }

        try
        {
            Method method =
                    this.model.getClass()
                            .getMethod(
                                    "get",
                                    String.class
                            );

            return method.invoke(
                    this.model,
                    name
            );
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public boolean hasBone(String name)
    {
        return findBone(name) != null;
    }

    /**
     * Сохраняет исходную структуру Blockbuster-модели.
     *
     * Этот метод вызывается только при загрузке модели.
     *
     * После этого rotationPointX/Y/Z могут изменяться
     * Blockbuster при применении animation transform,
     * но сохранённые значения останутся неизменными.
     */
    private void captureOriginalLimbData()
    {
        this.originalLimbData.clear();

        if (this.model == null)
        {
            return;
        }

        try
        {
            Class<?> modelClass =
                    this.model.getClass();

            Field limbsField =
                    modelClass.getField("limbs");

            Object limbsObject =
                    limbsField.get(this.model);

            if (!(limbsObject instanceof Object[]))
            {
                return;
            }

            Object[] limbs =
                    (Object[]) limbsObject;

            for (Object renderer : limbs)
            {
                if (renderer == null)
                {
                    continue;
                }

                BlockbusterLimbData data =
                        readLimbData(renderer);

                if (data != null)
                {
                    this.originalLimbData.add(data);
                }
            }

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Captured original Blockbuster skeleton: "
                            + this.originalLimbData.size()
                            + " limbs"
            );
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Failed to capture original Blockbuster skeleton"
            );

            e.printStackTrace();
        }
    }

    /**
     * Возвращает исходные реальные limbs
     * загруженной Blockbuster-модели.
     *
     * ВАЖНО:
     *
     * Здесь больше НЕТ чтения текущих
     * rotationPointX/Y/Z.
     *
     * Возвращается сохранённый снимок,
     * сделанный при загрузке модели.
     */
    public List<BlockbusterLimbData> getLimbData()
    {
        return new ArrayList<BlockbusterLimbData>(
                this.originalLimbData
        );
    }

    /**
     * Читает одну реальную ModelCustomRenderer
     * и превращает её в нейтральное описание
     * BlockbusterLimbData.
     *
     * Этот метод используется только во время
     * первоначального захвата скелета.
     */
    private BlockbusterLimbData readLimbData(
            Object renderer)
    {
        try
        {
            Class<?> rendererClass =
                    renderer.getClass();

            /*
             * Получаем ModelLimb.
             */
            Field limbField =
                    rendererClass.getField("limb");

            Object limb =
                    limbField.get(renderer);

            if (limb == null)
            {
                return null;
            }

            /*
             * Получаем имя кости.
             */
            Field nameField =
                    limb.getClass()
                            .getField("name");

            Object nameObject =
                    nameField.get(limb);

            if (nameObject == null)
            {
                return null;
            }

            String name =
                    String.valueOf(nameObject);

            /*
             * Получаем родителя.
             */
            String parentName =
                    null;

            try
            {
                Field parentField =
                        rendererClass.getField(
                                "parent"
                        );

                Object parent =
                        parentField.get(renderer);

                if (parent != null)
                {
                    Field parentLimbField =
                            parent.getClass()
                                    .getField("limb");

                    Object parentLimb =
                            parentLimbField.get(parent);

                    if (parentLimb != null)
                    {
                        Field parentNameField =
                                parentLimb.getClass()
                                        .getField("name");

                        Object parentNameObject =
                                parentNameField.get(
                                        parentLimb
                                );

                        if (parentNameObject != null)
                        {
                            parentName =
                                    String.valueOf(
                                            parentNameObject
                                    );
                        }
                    }
                }
            }
            catch (Exception ignored)
            {
                /*
                 * parent == null означает корневую кость.
                 */
            }

            /*
             * Получаем исходную точку вращения.
             *
             * На этом этапе renderer ещё не используется
             * нашим Animation Editor.
             *
             * Полученные значения сразу копируются
             * в BlockbusterLimbData.
             */
            float x =
                    readFloatField(
                            renderer,
                            "rotationPointX"
                    );

            float y =
                    readFloatField(
                            renderer,
                            "rotationPointY"
                    );

            float z =
                    readFloatField(
                            renderer,
                            "rotationPointZ"
                    );

            return new BlockbusterLimbData(
                    name,
                    parentName,
                    x,
                    y,
                    z
            );
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Безопасно читает float-поле.
     *
     * Используется только при первоначальном
     * захвате структуры модели.
     */
    private float readFloatField(
            Object object,
            String fieldName)
    {
        try
        {
            Field field =
                    object.getClass()
                            .getField(fieldName);

            Object value =
                    field.get(object);

            if (value instanceof Number)
            {
                return ((Number) value)
                        .floatValue();
            }
        }
        catch (Exception ignored)
        {
        }

        return 0.0F;
    }

    /**
     * Диагностический вывод исходной
     * иерархии загруженной Blockbuster-модели.
     */
    public void debugBones()
    {
        if (this.model == null)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Cannot inspect bones: model is null"
            );

            return;
        }

        System.out.println("");
        System.out.println(
                "=============================================="
        );
        System.out.println(
                "[BBS Animation Editor] BLOCKBUSTER MODEL"
        );
        System.out.println(
                "=============================================="
        );

        List<BlockbusterLimbData> limbs =
                getLimbData();

        System.out.println(
                "[BBS Animation Editor] Total limbs: "
                        + limbs.size()
        );

        for (
                BlockbusterLimbData limb :
                limbs
        )
        {
            if (limb == null)
            {
                continue;
            }

            String parent =
                    limb.hasParent()
                            ? limb.getParentName()
                            : "ROOT";

            System.out.println(
                    "[BBS Animation Editor] "
                            + limb.getName()
                            + "    parent="
                            + parent
                            + "    position=("
                            + limb.getX()
                            + ", "
                            + limb.getY()
                            + ", "
                            + limb.getZ()
                            + ")"
            );
        }

        System.out.println(
                "=============================================="
        );
        System.out.println("");
    }

    /**
     * Применяет итоговый transform редактора
     * к настоящей ModelCustomRenderer.
     *
     * ВАЖНО:
     *
     * Blockbuster может изменить
     * rotationPointX/Y/Z после этого вызова.
     *
     * Это нормально.
     *
     * Наш исходный скелет хранится отдельно
     * в originalLimbData и от этого не зависит.
     */
    public boolean applyTransform(
            Object renderer,
            AnimationBoneSnapshot bone
    )
    {
        if (renderer == null || bone == null)
        {
            return false;
        }

        try
        {
            Class<?> rendererClass =
                    renderer.getClass();

            Class<?> modelTransformClass =
                    Class.forName(
                            "mchorse.blockbuster.api.ModelTransform"
                    );

            Object transform =
                    modelTransformClass.newInstance();

            Field translate =
                    modelTransformClass.getField(
                            "translate"
                    );

            Field rotate =
                    modelTransformClass.getField(
                            "rotate"
                    );

            Field scale =
                    modelTransformClass.getField(
                            "scale"
                    );

            float[] translateArray =
                    (float[]) translate.get(
                            transform
                    );

            float[] rotateArray =
                    (float[]) rotate.get(
                            transform
                    );

            float[] scaleArray =
                    (float[]) scale.get(
                            transform
                    );

            translateArray[0] =
                    bone.getPositionX();

            translateArray[1] =
                    bone.getPositionY();

            translateArray[2] =
                    bone.getPositionZ();

            rotateArray[0] =
                    bone.getRotationX();

            rotateArray[1] =
                    bone.getRotationY();

            rotateArray[2] =
                    bone.getRotationZ();

            scaleArray[0] =
                    bone.getScaleX();

            scaleArray[1] =
                    bone.getScaleY();

            scaleArray[2] =
                    bone.getScaleZ();

            Method applyTransform =
                    rendererClass.getMethod(
                            "applyTransform",
                            modelTransformClass
                    );

            applyTransform.invoke(
                    renderer,
                    transform
            );

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
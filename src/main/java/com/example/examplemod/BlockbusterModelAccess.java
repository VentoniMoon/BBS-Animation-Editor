package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockbusterModelAccess
{
    private static final Map<Object, List<BlockbusterLimbData>> SKELETON_CACHE =
            new java.util.WeakHashMap<Object, List<BlockbusterLimbData>>();

    private Object model;

    private final List<BlockbusterLimbData> originalLimbData =
            new ArrayList<BlockbusterLimbData>();

    public BlockbusterModelAccess(Object model)
    {
        this.model = model;
    }

    public static List<String> getAvailableModelNames()
    {
        List<String> names = new ArrayList<String>();

        try
        {
            Class<?> modelCustomClass =
                    Class.forName(
                            "mchorse.blockbuster.client.model.ModelCustom"
                    );

            Field modelsField =
                    modelCustomClass.getField("MODELS");

            Object models = modelsField.get(null);

            if (models instanceof Map)
            {
                for (Object key : ((Map<?, ?>) models).keySet())
                {
                    if (key != null)
                    {
                        names.add(String.valueOf(key));
                    }
                }
            }
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }

        java.util.Collections.sort(names);
        return names;
    }

    public Object getModel()
    {
        return this.model;
    }

    public void setModel(Object model)
    {
        this.model = model;
        this.originalLimbData.clear();

        if (model == null)
        {
            return;
        }

        List<BlockbusterLimbData> cached =
                SKELETON_CACHE.get(model);

        if (cached != null)
        {
            this.originalLimbData.addAll(cached);
        }
    }

    /**
     * Returns the authoritative Blockbuster API Model used by
     * CustomMorph.  ModelCustom.MODELS contains the client renderer,
     * not the API Model itself, so the renderer object must never be
     * cast to mchorse.blockbuster.api.Model.
     */
    public Object getApiModel()
    {
        if (this.model == null)
        {
            return null;
        }

        try
        {
            java.lang.reflect.Field modelField =
                    this.model.getClass().getField("model");

            Object apiModel =
                    modelField.get(this.model);

            if (apiModel != null)
            {
                return apiModel;
            }
        }
        catch (Exception ignored)
        {
            /* Fall through to Blockbuster's authoritative model map. */
        }

        return null;
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

            List<BlockbusterLimbData> cached =
                    SKELETON_CACHE.get(this.model);

            if (cached != null)
            {
                this.originalLimbData.clear();
                this.originalLimbData.addAll(cached);
            }
            else
            {
                captureOriginalLimbData();

                /*
                 * ModelCustom.MODELS contains the render-side model,
                 * while Blockbuster's CustomMorph is backed by
                 * Blockbuster.proxy.models.models. In some 1.12.2
                 * states the render model is present but its renderer
                 * limbs have not been initialized yet.
                 *
                 * The original Blockbuster source uses the
                 * ModelHandler model map as the authoritative model
                 * repository, so use that as a skeleton fallback.
                 */
                if (this.originalLimbData.isEmpty()
                        || allLimbPivotsAreZero())
                {
                    captureModelHandlerLimbData(name);
                }

                SKELETON_CACHE.put(
                        this.model,
                        new ArrayList<BlockbusterLimbData>(
                                this.originalLimbData
                        )
                );
            }

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Loaded Blockbuster model: "
                            + name
                            + " (limbs="
                            + this.originalLimbData.size()
                            + ")"
            );

            return !this.originalLimbData.isEmpty();
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

    private boolean allLimbPivotsAreZero()
    {
        if (this.originalLimbData.isEmpty())
        {
            return true;
        }

        for (BlockbusterLimbData limb :
                this.originalLimbData)
        {
            if (limb == null)
            {
                continue;
            }

            if (Math.abs(limb.getX()) > 0.0001F
                    || Math.abs(limb.getY()) > 0.0001F
                    || Math.abs(limb.getZ()) > 0.0001F)
            {
                return false;
            }
        }

        return true;
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
     * Fallback skeleton source used by Blockbuster itself.
     *
     * CustomMorph.changeModel()/updateModel() resolve models through
     * Blockbuster.proxy.models.models, not through ModelCustom.MODELS.
     */
    private void captureModelHandlerLimbData(
            String name)
    {
        this.originalLimbData.clear();

        if (name == null || name.isEmpty())
        {
            return;
        }

        try
        {
            Class<?> modelHandlerClass =
                    Class.forName(
                            "mchorse.blockbuster.api.ModelHandler"
                    );

            Field modelsField =
                    modelHandlerClass.getField("models");

            Object modelsObject =
                    modelsField.get(null);

            if (!(modelsObject instanceof Map))
            {
                return;
            }

            Object apiModel =
                    ((Map<?, ?>) modelsObject).get(name);

            if (apiModel == null)
            {
                return;
            }

            Field limbsField =
                    apiModel.getClass().getField("limbs");

            Object limbsObject =
                    limbsField.get(apiModel);

            if (!(limbsObject instanceof Map))
            {
                return;
            }

            /*
             * ModelLimb itself contains hierarchy/meta data, but the
             * actual default pivot positions live in the "standing"
             * ModelPose. This is exactly what ModelCustomRenderer
             * applies before rendering the model.
             */
            Field posesField =
                    apiModel.getClass().getField("poses");

            Object posesObject =
                    posesField.get(apiModel);

            Object standingPose = null;

            if (posesObject instanceof Map)
            {
                standingPose =
                        ((Map<?, ?>) posesObject).get("standing");
            }

            for (Object value :
                    ((Map<?, ?>) limbsObject).values())
            {
                if (value == null)
                {
                    continue;
                }

                Field nameField =
                        value.getClass().getField("name");

                Field parentField =
                        value.getClass().getField("parent");

                String limbName =
                        String.valueOf(
                                nameField.get(value)
                        );

                String parentName =
                        String.valueOf(
                                parentField.get(value)
                        );

                float x = 0.0F;
                float y = 0.0F;
                float z = 0.0F;

                /*
                 * ModelCustomRenderer.applyTransform() stores:
                 *
                 *   rotationPointX = translate.x
                 *   rotationPointY = -translate.y + 24 (root)
                 *   rotationPointZ = -translate.z
                 *
                 * and render() converts those values to blocks.
                 * Therefore the AnimationBone local pivot is kept in the
                 * same model-pixel units as AnimationTransform:
                 *
                 *   X = translate.x
                 *   Y = -translate.y
                 *   Z = -translate.z
                 *
                 * The /16 conversion happens only when the gizmo is
                 * placed into the Preview world, exactly like the
                 * Blockbuster renderer.
                 *
                 * The +24 root offset cancels inside
                 * ModelCustomRenderer.cachedTranslation.
                 */
                if (standingPose != null)
                {
                    try
                    {
                        Field poseLimbsField =
                                standingPose.getClass()
                                        .getField("limbs");

                        Object poseLimbsObject =
                                poseLimbsField.get(
                                        standingPose
                                );

                        if (poseLimbsObject instanceof Map)
                        {
                            Object transform =
                                    ((Map<?, ?>) poseLimbsObject)
                                        .get(limbName);

                            if (transform != null)
                            {
                                Field translateField =
                                        transform.getClass()
                                                .getField(
                                                        "translate"
                                                );

                                Object translate =
                                        translateField.get(
                                                transform
                                        );

                                if (translate instanceof float[])
                                {
                                    float[] values =
                                            (float[]) translate;

                                    if (values.length >= 3)
                                    {
                                        x = values[0];
                                        y = -values[1];
                                        z = -values[2];
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception ignored)
                    {
                        /*
                         * Keep the zero pivot if a custom model does
                         * not expose a standing transform in the
                         * expected form.
                         */
                    }
                }

                this.originalLimbData.add(
                        new BlockbusterLimbData(
                                limbName,
                                parentName,
                                x,
                                y,
                                z
                        )
                );
            }

            System.out.println(
                    "[BBS Animation Editor] "
                            + "Captured ModelHandler skeleton with standing pivots: "
                            + this.originalLimbData.size()
                            + " limbs"
            );
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] "
                            + "Failed to capture ModelHandler skeleton"
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

            /*
             * Blockbuster's ModelCustomRenderer stores root Y as
             * (-translateY + 24), while child limbs use -translateY.
             * AnimationBone stores the model-space pivot relative to
             * the actor origin, so remove the root-only +24 here.
             *
             * This makes renderer-captured skeleton data use the same
             * coordinate space as the ModelHandler/standing-pose
             * fallback and, consequently, the same space used by the
             * 3D gizmo.
             */
            if (parentName == null)
            {
                y -= 24.0F;
            }

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
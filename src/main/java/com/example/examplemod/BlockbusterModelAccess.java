package com.example.examplemod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class BlockbusterModelAccess
{
    private Object model;

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
    }

    public boolean isValid()
    {
        return this.model != null;
    }

    public boolean loadModelByName(String name)
    {
        if (name == null || name.isEmpty())
        {
            return false;
        }

        try
        {
            Class<?> modelCustomClass =
                    Class.forName("mchorse.blockbuster.client.model.ModelCustom");

            Field modelsField = modelCustomClass.getField("MODELS");
            Object models = modelsField.get(null);

            if (!(models instanceof Map))
            {
                System.out.println(
                        "[BBS Animation Editor] ModelCustom.MODELS is not a Map"
                );

                return false;
            }

            Object loadedModel = ((Map<?, ?>) models).get(name);

            if (loadedModel == null)
            {
                System.out.println(
                        "[BBS Animation Editor] Model not found in MODELS: "
                                + name
                );

                return false;
            }

            this.setModel(loadedModel);

            System.out.println(
                    "[BBS Animation Editor] Loaded Blockbuster model: "
                            + name
            );

            debugBones();

            return true;
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to load Blockbuster model: "
                            + name
            );

            e.printStackTrace();

            return false;
        }
    }

    public Object findBone(String name)
    {
        if (this.model == null || name == null)
        {
            return null;
        }

        try
        {
            Method method =
                    this.model.getClass().getMethod("get", String.class);

            return method.invoke(this.model, name);
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
     * Выводит все кости загруженной модели Blockbuster
     * вместе с их родителями.
     *
     * Это диагностический метод.
     */
    public void debugBones()
    {
        if (this.model == null)
        {
            System.out.println(
                    "[BBS Animation Editor] Cannot inspect bones: model is null"
            );

            return;
        }

        System.out.println("");
        System.out.println("==============================================");
        System.out.println("[BBS Animation Editor] BLOCKBUSTER MODEL");
        System.out.println("==============================================");

        try
        {
            Class<?> modelClass = this.model.getClass();

            Field limbsField = modelClass.getField("limbs");
            Object limbsObject = limbsField.get(this.model);

            if (!(limbsObject instanceof Object[]))
            {
                System.out.println(
                        "[BBS Animation Editor] limbs is not an Object[]"
                );

                return;
            }

            Object[] limbs = (Object[]) limbsObject;

            System.out.println(
                    "[BBS Animation Editor] Total limbs: "
                            + limbs.length
            );

            for (Object renderer : limbs)
            {
                if (renderer == null)
                {
                    continue;
                }

                printBoneInfo(renderer);
            }
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to inspect Blockbuster model"
            );

            e.printStackTrace();
        }

        System.out.println("==============================================");
        System.out.println("");
    }

    /**
     * Получает имя кости и имя её родителя.
     *
     * ModelCustomRenderer использует поле "parent",
     * а не поле "children".
     */
    private void printBoneInfo(Object renderer)
    {
        try
        {
            Class<?> rendererClass = renderer.getClass();

            Field limbField = rendererClass.getField("limb");
            Object limb = limbField.get(renderer);

            if (limb == null)
            {
                return;
            }

            Field nameField = limb.getClass().getField("name");
            Object name = nameField.get(limb);

            String parentName = "ROOT";

            try
            {
                Field parentField =
                        rendererClass.getField("parent");

                Object parent = parentField.get(renderer);

                if (parent != null)
                {
                    Field parentLimbField =
                            parent.getClass().getField("limb");

                    Object parentLimb =
                            parentLimbField.get(parent);

                    if (parentLimb != null)
                    {
                        Field parentNameField =
                                parentLimb.getClass().getField("name");

                        Object value =
                                parentNameField.get(parentLimb);

                        if (value != null)
                        {
                            parentName = String.valueOf(value);
                        }
                    }
                }
            }
            catch (Exception ignored)
            {
                /*
                 * Если parent недоступен,
                 * считаем кость корневой.
                 */
            }

            System.out.println(
                    "[BBS Animation Editor] "
                            + String.valueOf(name)
                            + "    parent="
                            + parentName
            );
        }
        catch (Exception e)
        {
            System.out.println(
                    "[BBS Animation Editor] Failed to inspect renderer"
            );

            e.printStackTrace();
        }
    }

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
            Class<?> rendererClass = renderer.getClass();

            Class<?> modelTransformClass =
                    Class.forName(
                            "mchorse.blockbuster.api.ModelTransform"
                    );

            Object transform =
                    modelTransformClass.newInstance();

            Field translate =
                    modelTransformClass.getField("translate");

            Field rotate =
                    modelTransformClass.getField("rotate");

            Field scale =
                    modelTransformClass.getField("scale");

            float[] translateArray =
                    (float[]) translate.get(transform);

            float[] rotateArray =
                    (float[]) rotate.get(transform);

            float[] scaleArray =
                    (float[]) scale.get(transform);

            translateArray[0] = bone.getPositionX();
            translateArray[1] = bone.getPositionY();
            translateArray[2] = bone.getPositionZ();

            rotateArray[0] = bone.getRotationX();
            rotateArray[1] = bone.getRotationY();
            rotateArray[2] = bone.getRotationZ();

            scaleArray[0] = bone.getScaleX();
            scaleArray[1] = bone.getScaleY();
            scaleArray[2] = bone.getScaleZ();

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
package com.example.examplemod;

/**
 * Описание одной кости реальной Blockbuster-модели.
 *
 * Этот класс НЕ является AnimationBone.
 *
 * Он только переносит структуру модели Blockbuster
 * в нейтральный формат, который затем будет использован
 * для построения AnimationBone.
 */
public class BlockbusterLimbData
{
    private final String name;
    private final String parentName;

    private final float x;
    private final float y;
    private final float z;

    public BlockbusterLimbData(
            String name,
            String parentName,
            float x,
            float y,
            float z)
    {
        this.name = name;
        this.parentName = parentName;

        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getName()
    {
        return this.name;
    }

    public String getParentName()
    {
        return this.parentName;
    }

    public float getX()
    {
        return this.x;
    }

    public float getY()
    {
        return this.y;
    }

    public float getZ()
    {
        return this.z;
    }

    public boolean hasParent()
    {
        return this.parentName != null
                && !this.parentName.isEmpty();
    }
}
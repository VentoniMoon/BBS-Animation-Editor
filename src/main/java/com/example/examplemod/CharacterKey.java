package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Один ключ Character Timeline.
 *
 * Character Key описывает изменение состояния Actor
 * в определённый момент времени.
 *
 * Сам Track не имеет смысла/названия.
 * Смысл хранится непосредственно в CharacterKey.
 */
public class CharacterKey
{
    public enum Type
    {
        SKIN,
        MORPH,
        ANIMATION,
        ACTION,
        BODY_PART_OVERRIDE,
        CUSTOM
    }

    private int frame;
    private Type type;

    /*
     * Произвольные данные ключа.
     *
     * Например:
     *
     * SKIN:
     *     Skin = "..."
     *
     * MORPH:
     *     Morph = {...}
     *
     * ANIMATION:
     *     Name = "walk"
     *
     * BODY_PART_OVERRIDE:
     *     Part = "arm.left"
     *     Enabled = false
     */
    private NBTTagCompound data;


    public CharacterKey()
    {
        this.frame = 0;
        this.type = Type.CUSTOM;
        this.data = new NBTTagCompound();
    }


    public CharacterKey(
            int frame,
            Type type)
    {
        this.frame = Math.max(0, frame);
        this.type = type == null
                ? Type.CUSTOM
                : type;
        this.data = new NBTTagCompound();
    }


    /*
     * =========================================================
     * FRAME
     * =========================================================
     */

    public int getFrame()
    {
        return this.frame;
    }

    public void setFrame(int frame)
    {
        this.frame = Math.max(0, frame);
    }


    /*
     * =========================================================
     * TYPE
     * =========================================================
     */

    public Type getType()
    {
        return this.type;
    }

    public void setType(Type type)
    {
        this.type = type == null
                ? Type.CUSTOM
                : type;
    }


    /*
     * =========================================================
     * DATA
     * =========================================================
     */

    public NBTTagCompound getData()
    {
        return this.data.copy();
    }

    public void setData(NBTTagCompound data)
    {
        this.data = data == null
                ? new NBTTagCompound()
                : data.copy();
    }

    public boolean hasData(String key)
    {
        return this.data.hasKey(key);
    }

    public String getString(String key)
    {
        return this.data.getString(key);
    }

    public void setString(
            String key,
            String value)
    {
        this.data.setString(
                key,
                value == null ? "" : value
        );
    }

    public boolean getBoolean(String key)
    {
        return this.data.getBoolean(key);
    }

    public void setBoolean(
            String key,
            boolean value)
    {
        this.data.setBoolean(
                key,
                value
        );
    }

    public int getInteger(String key)
    {
        return this.data.getInteger(key);
    }

    public void setInteger(
            String key,
            int value)
    {
        this.data.setInteger(
                key,
                value
        );
    }

    public NBTTagCompound getCompound(String key)
    {
        if (!this.data.hasKey(key, 10))
        {
            return new NBTTagCompound();
        }

        return this.data
                .getCompoundTag(key)
                .copy();
    }

    public void setCompound(
            String key,
            NBTTagCompound value)
    {
        if (value == null)
        {
            this.data.removeTag(key);
            return;
        }

        this.data.setTag(
                key,
                value.copy()
        );
    }


    /*
     * =========================================================
     * NBT
     * =========================================================
     */

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag =
                new NBTTagCompound();

        tag.setInteger(
                "Frame",
                this.frame
        );

        tag.setString(
                "Type",
                this.type.name()
        );

        tag.setTag(
                "Data",
                this.data.copy()
        );

        return tag;
    }


    public static CharacterKey fromNBT(
            NBTTagCompound tag)
    {
        CharacterKey key =
                new CharacterKey();

        if (tag == null)
        {
            return key;
        }

        if (tag.hasKey("Frame"))
        {
            key.frame =
                    Math.max(
                            0,
                            tag.getInteger("Frame")
                    );
        }

        if (tag.hasKey("Type"))
        {
            try
            {
                key.type =
                        Type.valueOf(
                                tag.getString("Type")
                        );
            }
            catch (IllegalArgumentException e)
            {
                key.type = Type.CUSTOM;
            }
        }

        if (tag.hasKey("Data", 10))
        {
            key.data =
                    tag.getCompoundTag("Data")
                            .copy();
        }

        return key;
    }
}
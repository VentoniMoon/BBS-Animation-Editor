package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

public class BlockbusterRecordAction
{
    private byte type;

    private NBTTagCompound nbt;

    public static BlockbusterRecordAction fromNBT(NBTTagCompound nbt)
    {
        BlockbusterRecordAction action =
                new BlockbusterRecordAction();

        action.nbt = nbt.copy();
        action.type = nbt.getByte("Type");

        return action;
    }

    public byte getType()
    {
        return this.type;
    }

    /**
     * Получить название действия через реестр Blockbuster.
     */
    public String getName(BlockbusterActionRegistry registry)
    {
        if (registry == null)
        {
            return null;
        }

        return registry.getName(this.type);
    }

    /**
     * Получить оригинальный NBT действия.
     */
    public NBTTagCompound getNBT()
    {
        if (this.nbt == null)
        {
            return new NBTTagCompound();
        }

        return this.nbt.copy();
    }

    /**
     * Получить значение NBT-параметра.
     */
    public boolean hasKey(String key)
    {
        return this.nbt != null && this.nbt.hasKey(key);
    }

    public String getString(String key)
    {
        if (this.nbt == null)
        {
            return "";
        }

        return this.nbt.getString(key);
    }

    public int getInteger(String key)
    {
        if (this.nbt == null)
        {
            return 0;
        }

        return this.nbt.getInteger(key);
    }

    public byte getByte(String key)
    {
        if (this.nbt == null)
        {
            return 0;
        }

        return this.nbt.getByte(key);
    }

    public NBTTagCompound getCompound(String key)
    {
        if (this.nbt == null || !this.nbt.hasKey(key, 10))
        {
            return new NBTTagCompound();
        }

        return this.nbt.getCompoundTag(key);
    }
}
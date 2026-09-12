package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

public class BlockbusterActionView
{
    private final BlockbusterRecordAction action;
    private final BlockbusterActionDefinition definition;

    public BlockbusterActionView(
            BlockbusterRecordAction action,
            BlockbusterActionDefinition definition)
    {
        this.action = action;
        this.definition = definition;
    }

    public BlockbusterRecordAction getAction()
    {
        return this.action;
    }

    public BlockbusterActionDefinition getDefinition()
    {
        return this.definition;
    }

    public byte getType()
    {
        return this.action.getType();
    }

    public String getName()
    {
        if (this.definition == null)
        {
            return null;
        }

        return this.definition.getName();
    }

    public NBTTagCompound getNBT()
    {
        return this.action.getNBT();
    }

    public boolean hasDefinition()
    {
        return this.definition != null;
    }

    public boolean hasField(
            BlockbusterActionDefinition.Field field)
    {
        if (field == null)
        {
            return false;
        }

        return this.action.hasKey(
                field.getName()
        );
    }

    public String getString(
            BlockbusterActionDefinition.Field field)
    {
        if (field == null)
        {
            return "";
        }

        return this.action.getString(
                field.getName()
        );
    }

    public int getInteger(
            BlockbusterActionDefinition.Field field)
    {
        if (field == null)
        {
            return 0;
        }

        return this.action.getInteger(
                field.getName()
        );
    }

    public byte getByte(
            BlockbusterActionDefinition.Field field)
    {
        if (field == null)
        {
            return 0;
        }

        return this.action.getByte(
                field.getName()
        );
    }

    public NBTTagCompound getCompound(
            BlockbusterActionDefinition.Field field)
    {
        if (field == null)
        {
            return new NBTTagCompound();
        }

        return this.action.getCompound(
                field.getName()
        );
    }
}
package com.example.examplemod;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class PreviewCameraEntity extends Entity
{
    public PreviewCameraEntity(World world)
    {
        super(world);
    }

    @Override
    protected void entityInit()
    {
    }

    @Override
    protected void readEntityFromNBT(
            NBTTagCompound compound)
    {
    }

    @Override
    protected void writeEntityToNBT(
            NBTTagCompound compound)
    {
    }
}
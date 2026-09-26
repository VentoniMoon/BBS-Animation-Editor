package com.example.examplemod;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class PreviewCameraEntity extends Entity
{
    public PreviewCameraEntity(World world)
    {
        super(world);

        this.setSize(0.0F, 0.0F);
        this.noClip = true;
    }

    public void updateCamera(
            double x,
            double y,
            double z,
            float yaw,
            float pitch)
    {
        this.lastTickPosX = x;
        this.lastTickPosY = y;
        this.lastTickPosZ = z;

        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;

        this.posX = x;
        this.posY = y;
        this.posZ = z;

        this.prevRotationYaw = yaw;
        this.prevRotationPitch = pitch;

        this.rotationYaw = yaw;
        this.rotationPitch = pitch;

        this.setPosition(x, y, z);
    }

    @Override
    protected void entityInit()
    {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound)
    {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound)
    {
    }
}
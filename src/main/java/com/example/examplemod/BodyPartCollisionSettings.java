package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Passive Jolt collision markup per decoded Chameleon bone.
 * Independent of enabling PHYSICAL hair on that bone: driven hair bones
 * remain excluded from their own static collision set.
 *
 * Old scenes without this metadata retain the former behavior:
 * every non-simulated cube is a Box collider at 100% size.
 */
public final class BodyPartCollisionSettings
{
    public enum Shape { BOX, SPHERE, CAPSULE }

    private boolean enabled=true;
    private Shape shape=Shape.BOX;
    private float scale=1F;

    public boolean isEnabled() {return enabled;}
    public void setEnabled(boolean enabled) {this.enabled=enabled;}
    public Shape getShape() {return shape;}
    public void setShape(Shape value)
    {
        if(value==null)throw new IllegalArgumentException("Missing collider type");
        this.shape=value;
    }
    public float getScale() {return scale;}
    public void setScale(float value)
    {
        if(!Float.isFinite(value) || value<0.25F || value>2F)
            throw new IllegalArgumentException("Collider scale outside 0.25..2");
        scale=value;
    }

    public NBTTagCompound toNBT()
    {
        NBTTagCompound nbt=new NBTTagCompound();
        nbt.setBoolean("Enabled",enabled);
        nbt.setString("Type",shape.name());
        nbt.setFloat("Scale",scale);
        return nbt;
    }

    public static BodyPartCollisionSettings fromNBT(NBTTagCompound nbt)
    {
        BodyPartCollisionSettings result=new BodyPartCollisionSettings();
        if(nbt==null)return result;
        if(nbt.hasKey("Enabled"))result.enabled=nbt.getBoolean("Enabled");
        if(nbt.hasKey("Type"))
            try {result.shape=Shape.valueOf(nbt.getString("Type"));}
            catch(IllegalArgumentException ignored) {result.shape=Shape.BOX;}
        if(nbt.hasKey("Scale"))
        {
            float value=nbt.getFloat("Scale");
            if(Float.isFinite(value) && value>=0.25F && value<=2F)
                result.scale=value;
        }
        return result;
    }
}

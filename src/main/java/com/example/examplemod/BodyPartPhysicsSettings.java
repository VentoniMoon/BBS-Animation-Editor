package com.example.examplemod;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Per-bone settings for the experimental Body Parts chain physics.
 * Configuration only: enabling a chain does not yet replace animation keys.
 */
public final class BodyPartPhysicsSettings
{
    private boolean enabled;
    private float gravity = 1.0F;
    private float stiffness = 0.55F;
    private float damping = 0.25F;
    private boolean bodyCollisions = true;

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public float getGravity() { return this.gravity; }
    public void setGravity(float value) { this.gravity = clamp(value, 0.0F, 2.0F); }

    public float getStiffness() { return this.stiffness; }
    public void setStiffness(float value) { this.stiffness = clamp(value, 0.0F, 1.0F); }

    public float getDamping() { return this.damping; }
    public void setDamping(float value) { this.damping = clamp(value, 0.0F, 1.0F); }

    public boolean hasBodyCollisions() { return this.bodyCollisions; }
    public void setBodyCollisions(boolean enabled) { this.bodyCollisions = enabled; }

    private static float clamp(float value, float min, float max)
    {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public NBTTagCompound toNBT()
    {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Enabled", this.enabled);
        tag.setFloat("Gravity", this.gravity);
        tag.setFloat("Stiffness", this.stiffness);
        tag.setFloat("Damping", this.damping);
        tag.setBoolean("BodyCollisions", this.bodyCollisions);
        return tag;
    }

    public static BodyPartPhysicsSettings fromNBT(NBTTagCompound tag)
    {
        BodyPartPhysicsSettings settings = new BodyPartPhysicsSettings();
        if (tag == null) return settings;
        settings.setEnabled(tag.getBoolean("Enabled"));
        if (tag.hasKey("Gravity")) settings.setGravity(tag.getFloat("Gravity"));
        if (tag.hasKey("Stiffness")) settings.setStiffness(tag.getFloat("Stiffness"));
        if (tag.hasKey("Damping")) settings.setDamping(tag.getFloat("Damping"));
        if (tag.hasKey("BodyCollisions")) settings.setBodyCollisions(tag.getBoolean("BodyCollisions"));
        return settings;
    }
}

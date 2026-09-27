package com.example.examplemod;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class PreviewFogRenderer
{
    private static final float FOG_START_FACTOR = 0.35F;
    private static final float FOG_END_FACTOR = 0.80F;

    private static final float MIN_FOG_END = 48.0F;
    private static final float MAX_FOG_END = 128.0F;

    private final Minecraft mc;
    private final FloatBuffer fogColorBuffer;

    private boolean active;

    public PreviewFogRenderer(Minecraft mc)
    {
        this.mc = mc;
        this.fogColorBuffer = BufferUtils.createFloatBuffer(4);
        this.active = false;
    }

    /**
     * Starts preview fog.
     *
     * The fog distance is intentionally shorter than the normal
     * Minecraft render distance because the editor viewport is small.
     */
    public void begin(float partialTicks, float terrainFarPlane)
    {
        Vec3d color;

        if (this.mc != null && this.mc.world != null)
        {
            color = this.mc.world.getFogColor(partialTicks);
        }
        else
        {
            color = new Vec3d(0.75D, 0.80D, 0.85D);
        }

        float red = clamp((float) color.x, 0.0F, 1.0F);
        float green = clamp((float) color.y, 0.0F, 1.0F);
        float blue = clamp((float) color.z, 0.0F, 1.0F);

        float safeFarPlane = terrainFarPlane;

        if (safeFarPlane < 1.0F)
        {
            safeFarPlane = 256.0F;
        }

        float fogEnd = safeFarPlane * FOG_END_FACTOR;

        if (fogEnd < MIN_FOG_END)
        {
            fogEnd = MIN_FOG_END;
        }

        if (fogEnd > MAX_FOG_END)
        {
            fogEnd = MAX_FOG_END;
        }

        float fogStart = fogEnd * (FOG_START_FACTOR / FOG_END_FACTOR);

        if (fogStart < 0.0F)
        {
            fogStart = 0.0F;
        }

        if (fogStart >= fogEnd)
        {
            fogStart = fogEnd * 0.5F;
        }

        /*
         * OpenGL fog color.
         */
        this.fogColorBuffer.clear();
        this.fogColorBuffer.put(red);
        this.fogColorBuffer.put(green);
        this.fogColorBuffer.put(blue);
        this.fogColorBuffer.put(1.0F);
        this.fogColorBuffer.flip();

        /*
         * Use OpenGL directly.
         *
         * GlStateManager.setFog(int) is private in the
         * Forge 1.12.2 mappings used by this project.
         */
        GL11.glEnable(GL11.GL_FOG);

        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);

        GL11.glFogf(GL11.GL_FOG_START, fogStart);
        GL11.glFogf(GL11.GL_FOG_END, fogEnd);

        GL11.glFog(GL11.GL_FOG_COLOR, this.fogColorBuffer);

        GL11.glHint(GL11.GL_FOG_HINT, GL11.GL_NICEST);

        this.active = true;
    }

    /**
     * Ends preview fog.
     */
    public void end()
    {
        GL11.glDisable(GL11.GL_FOG);

        /*
         * Restore reasonable OpenGL fog parameters.
         */
        GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
        GL11.glFogf(GL11.GL_FOG_START, 0.0F);
        GL11.glFogf(GL11.GL_FOG_END, 256.0F);

        this.active = false;
    }

    /**
     * Completely resets the fog state.
     */
    public void reset()
    {
        this.end();
    }

    public boolean isActive()
    {
        return this.active;
    }

    private static float clamp(float value, float min, float max)
    {
        if (value < min)
        {
            return min;
        }

        if (value > max)
        {
            return max;
        }

        return value;
    }
}
package net.minecraft.client;

import net.minecraft.client.shader.Framebuffer;

/** Exact minimal contract used by native actor pass smoke fixture. */
public class Minecraft
{
    public Object world = new Object();
    public Framebuffer framebuffer = new Framebuffer();
    public final FakeEntityRenderer entityRenderer = new FakeEntityRenderer();
    public Framebuffer getFramebuffer(){ return framebuffer; }

    public static final class FakeEntityRenderer
    {
        public int enables, disables;
        public void enableLightmap(){enables++;}
        public void disableLightmap(){disables++;}
    }
}

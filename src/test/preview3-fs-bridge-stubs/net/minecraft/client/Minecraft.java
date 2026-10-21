package net.minecraft.client;
public class Minecraft {
    public int displayWidth=1280,displayHeight=720;
    public Object world=new Object();
    private final net.minecraft.client.shader.Framebuffer framebuffer=
         new net.minecraft.client.shader.Framebuffer(1280,720,true);
    private final net.minecraft.client.renderer.entity.RenderManager manager=
         new net.minecraft.client.renderer.entity.RenderManager();
    public net.minecraft.client.shader.Framebuffer getFramebuffer(){return framebuffer;}
    public net.minecraft.client.renderer.entity.RenderManager getRenderManager(){return manager;}
}
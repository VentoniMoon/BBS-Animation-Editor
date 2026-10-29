package net.minecraft.client;
public class Minecraft {
    public int displayWidth,displayHeight;
    /** Simulate any user-selected GUI Scale independently of EditorGuiScale. */
    public int vanillaScaleFactor=1;
    public Object world=new Object();
    private final net.minecraft.client.shader.Framebuffer framebuffer;
    private final net.minecraft.client.renderer.entity.RenderManager manager=
         new net.minecraft.client.renderer.entity.RenderManager();
    public Minecraft(){this(1280,720);}
    public Minecraft(int width,int height){
        displayWidth=width;displayHeight=height;
        framebuffer=new net.minecraft.client.shader.Framebuffer(width,height,true);
    }
    public net.minecraft.client.shader.Framebuffer getFramebuffer(){return framebuffer;}
    public net.minecraft.client.renderer.entity.RenderManager getRenderManager(){return manager;}
}
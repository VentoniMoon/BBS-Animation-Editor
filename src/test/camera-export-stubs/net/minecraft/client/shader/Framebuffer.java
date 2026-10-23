package net.minecraft.client.shader;
public final class Framebuffer {
    public int framebufferWidth,framebufferHeight,framebufferObject;
    public Framebuffer(int w,int h,int id) {
        this.framebufferWidth=w;
        this.framebufferHeight=h;
        this.framebufferObject=id;
    }
}

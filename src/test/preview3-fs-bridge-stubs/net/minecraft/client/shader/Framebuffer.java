package net.minecraft.client.shader;
public class Framebuffer {
    private static int nextId=100;
    public int framebufferWidth,framebufferHeight,framebufferObject,framebufferTexture;
    public Framebuffer(int width,int height,boolean depth){createBindFramebuffer(width,height);}
    public void createBindFramebuffer(int width,int height){
        framebufferWidth=width;framebufferHeight=height;
        framebufferObject=nextId++;framebufferTexture=nextId++;
        org.lwjgl.opengl.GL30.glBindFramebuffer(
           org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,framebufferObject);
    }
    public void bindFramebuffer(boolean setViewport){
        org.lwjgl.opengl.GL30.glBindFramebuffer(
           org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,framebufferObject);
        if(setViewport)org.lwjgl.opengl.GL11.glViewport(0,0,framebufferWidth,framebufferHeight);
    }
    public void deleteFramebuffer(){}
}
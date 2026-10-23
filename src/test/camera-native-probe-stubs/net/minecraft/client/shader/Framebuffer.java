package net.minecraft.client.shader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
public final class Framebuffer {
  private static int ids=50;
  public int framebufferWidth,framebufferHeight,framebufferObject;
  public Framebuffer(int w,int h,boolean depth) {
    framebufferObject=ids++;
    createBindFramebuffer(w,h);
  }
  public void createBindFramebuffer(int w,int h) {
    framebufferWidth=w;framebufferHeight=h;
    bindFramebuffer(true);
  }
  public void bindFramebuffer(boolean viewport) {
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebufferObject);
    if(viewport)GL11.glViewport(0,0,framebufferWidth,framebufferHeight);
  }
  public void deleteFramebuffer(){}
}

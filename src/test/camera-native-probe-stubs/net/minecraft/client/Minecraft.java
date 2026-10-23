package net.minecraft.client;
import java.io.File;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.shader.Framebuffer;
public final class Minecraft {
  private static Minecraft instance;
  public Minecraft(){instance=this;}
  public static Minecraft getMinecraft(){return instance;}
  public int displayWidth=800,displayHeight=600;
  public Object world=new Object();
  public GuiScreen currentScreen;
  public File mcDataDir=new File(".");
  public Framebuffer framebufferMc=new Framebuffer(800,600,true);
  public Framebuffer getFramebuffer(){return framebufferMc;}
}

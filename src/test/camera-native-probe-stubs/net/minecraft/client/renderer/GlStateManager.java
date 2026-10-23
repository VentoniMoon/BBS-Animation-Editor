package net.minecraft.client.renderer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Match the Minecraft 1.12 texture cache semantics that caused font corruption. */
public final class GlStateManager {
  public static int activeTextureUnit=OpenGlHelper.defaultTexUnit;
  public static int cachedTexture=0;
  public static boolean cachedTexture2dEnabled=true;
  public static void setActiveTexture(int unit) {
    if(activeTextureUnit!=unit) {
      activeTextureUnit=unit;
      GL13.glActiveTexture(unit);
    }
  }
  public static void enableTexture2D() {
    cachedTexture2dEnabled=true;
  }
  public static void bindTexture(int texture) {
    if(cachedTexture!=texture) {
      cachedTexture=texture;
      GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
    }
  }
}

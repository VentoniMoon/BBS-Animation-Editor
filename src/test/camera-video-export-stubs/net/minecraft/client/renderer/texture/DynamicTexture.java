package net.minecraft.client.renderer.texture;
import java.awt.image.BufferedImage;
public final class DynamicTexture {
  public final BufferedImage image;
  public boolean deleted;
  public DynamicTexture(BufferedImage input) {image=input;}
  public void deleteGlTexture() {deleted=true;}
}
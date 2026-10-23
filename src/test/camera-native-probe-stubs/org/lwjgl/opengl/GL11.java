package org.lwjgl.opengl;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
public final class GL11 {
  public static final int GL_VIEWPORT=2978,GL_MAX_TEXTURE_SIZE=3379,
    GL_PACK_ALIGNMENT=3333,GL_READ_BUFFER=3074,GL_RGBA=6408,
    GL_UNSIGNED_BYTE=5121,GL_NO_ERROR=0,GL_ALL_ATTRIB_BITS=1048575;
  public static int[] viewport={0,0,800,600};
  public static int textureLimit=4096,pack=4,attribDepth;
  public static int glGetInteger(int key) {
    if(key==GL_MAX_TEXTURE_SIZE)return textureLimit;
    if(key==GL_PACK_ALIGNMENT)return pack;
    if(key==GL_READ_BUFFER)
      return GL30.read==7?GL30.oldReadBuffer:GL30.sourceReadBuffer;
    if(key==GL30.GL_READ_FRAMEBUFFER_BINDING)return GL30.read;
    if(key==GL30.GL_DRAW_FRAMEBUFFER_BINDING)return GL30.draw;
    if(key==GL13.GL_ACTIVE_TEXTURE)return GL13.active;
    return 0;
  }
  public static void glGetInteger(int key,IntBuffer dest) {
    if(key==GL_VIEWPORT)
      for(int i=0;i<4;i++)dest.put(i,viewport[i]);
  }
  public static void glPushAttrib(int bits){attribDepth++;}
  public static void glPopAttrib(){attribDepth--;}
  public static void glViewport(int x,int y,int w,int h) {
    viewport=new int[]{x,y,w,h};
  }
  public static void glReadBuffer(int attachment) {
    if(GL30.read==7)GL30.oldReadBuffer=attachment;
    else GL30.sourceReadBuffer=attachment;
  }
  public static void glPixelStorei(int key,int value){
    if(key==GL_PACK_ALIGNMENT)pack=value;
  }
  public static void glReadPixels(int x,int y,int w,int h,
      int format,int type,ByteBuffer pixels) {
    if(!pixels.isDirect()||format!=GL_RGBA||type!=GL_UNSIGNED_BYTE)
      throw new IllegalStateException("Wrong readback format");
    for(int pixel=0;pixel<w*h;pixel++) {
      int offset=pixel*4;
      pixels.put(offset,(byte)200);
      pixels.put(offset+1,(byte)(pixel%255));
      pixels.put(offset+2,(byte)80);
      pixels.put(offset+3,(byte)255);
    }
  }
  public static int glGetError(){return GL_NO_ERROR;}
}

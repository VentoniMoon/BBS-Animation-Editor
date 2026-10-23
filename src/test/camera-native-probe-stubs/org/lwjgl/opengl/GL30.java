package org.lwjgl.opengl;
public final class GL30 {
  public static final int GL_READ_FRAMEBUFFER=36008,GL_DRAW_FRAMEBUFFER=36009,
    GL_FRAMEBUFFER=36160,GL_READ_FRAMEBUFFER_BINDING=36010,
    GL_DRAW_FRAMEBUFFER_BINDING=36006,GL_COLOR_ATTACHMENT0=36064,
    GL_FRAMEBUFFER_COMPLETE=36053;
  public static int read=7,draw=7,sourceReadBuffer=GL_COLOR_ATTACHMENT0,oldReadBuffer=1029;
  public static int status=GL_FRAMEBUFFER_COMPLETE;
  public static void glBindFramebuffer(int target,int id) {
    if(target==GL_READ_FRAMEBUFFER||target==GL_FRAMEBUFFER)read=id;
    if(target==GL_DRAW_FRAMEBUFFER||target==GL_FRAMEBUFFER)draw=id;
  }
  public static int glCheckFramebufferStatus(int target) {return status;}
}

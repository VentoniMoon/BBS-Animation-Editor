package org.lwjgl.opengl;
public final class GL30 {
    public static final int GL_READ_FRAMEBUFFER=36008,GL_DRAW_FRAMEBUFFER=36009,
       GL_FRAMEBUFFER=36160,GL_READ_FRAMEBUFFER_BINDING=36010,
       GL_DRAW_FRAMEBUFFER_BINDING=36006;
    public static int read=0,draw=0,blits=0,depthBlits=0;
    /** Last world color-copy rectangle in physical FBO pixels. */
    public static int[] lastColorDestination=new int[4];
    public static void glBindFramebuffer(int target,int id) {
        if(target==GL_READ_FRAMEBUFFER||target==GL_FRAMEBUFFER)read=id;
        if(target==GL_DRAW_FRAMEBUFFER||target==GL_FRAMEBUFFER)draw=id;
    }
    public static void glBlitFramebuffer(int sx,int sy,int ex,int ey,
          int dx,int dy,int dex,int dey,int mask,int filter) {
        blits++;
        if(mask==GL11.GL_DEPTH_BUFFER_BIT)depthBlits++;
        if(mask==GL11.GL_COLOR_BUFFER_BIT)
            lastColorDestination=new int[]{dx,dy,dex,dey};
    }
}
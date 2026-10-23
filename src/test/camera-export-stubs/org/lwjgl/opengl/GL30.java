package org.lwjgl.opengl;
public final class GL30 {
    public static final int GL_READ_FRAMEBUFFER=36008;
    public static final int GL_READ_FRAMEBUFFER_BINDING=36010;
    public static final int GL_COLOR_ATTACHMENT0=36064;
    public static int read=7;
    public static int oldReadBuffer=1029;
    public static int sourceReadBuffer=GL_COLOR_ATTACHMENT0;
    public static void glBindFramebuffer(int target,int id) {
        if(target==GL_READ_FRAMEBUFFER)read=id;
    }
}

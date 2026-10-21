package org.lwjgl.opengl;
public final class GL13 {
    public static final int GL_ACTIVE_TEXTURE=34016,GL_TEXTURE0=33984;
    public static int active=GL_TEXTURE0;
    public static void glActiveTexture(int texture){active=texture;}
}
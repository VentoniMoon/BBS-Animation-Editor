package org.lwjgl.opengl;
public final class GL20 {
    public static final int GL_CURRENT_PROGRAM=35725;
    public static int program=0;
    public static void glUseProgram(int id){program=id;}
}
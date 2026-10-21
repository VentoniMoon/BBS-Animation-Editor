package org.lwjgl;
public final class BufferUtils {
    public static java.nio.IntBuffer createIntBuffer(int n) {return java.nio.IntBuffer.allocate(n);}
    public static java.nio.FloatBuffer createFloatBuffer(int n) {return java.nio.FloatBuffer.allocate(n);}
}
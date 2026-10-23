package org.lwjgl;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
public final class BufferUtils {
  public static ByteBuffer createByteBuffer(int size){return ByteBuffer.allocateDirect(size);}
  public static IntBuffer createIntBuffer(int size){return IntBuffer.allocate(size);}
}

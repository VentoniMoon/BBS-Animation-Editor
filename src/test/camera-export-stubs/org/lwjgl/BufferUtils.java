package org.lwjgl;
import java.nio.ByteBuffer;
public final class BufferUtils {
    private BufferUtils(){}
    public static ByteBuffer createByteBuffer(int count) {
        return ByteBuffer.allocateDirect(count);
    }
}

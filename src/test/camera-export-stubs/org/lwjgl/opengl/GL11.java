package org.lwjgl.opengl;
import java.nio.ByteBuffer;
public final class GL11 {
    public static final int GL_PACK_ALIGNMENT=3333,GL_READ_BUFFER=3074;
    public static final int GL_RGBA=6408,GL_UNSIGNED_BYTE=5121,GL_NO_ERROR=0;
    public static int pack=4;
    public static int readCalls,readX,readY,readW,readH;
    public static int glGetInteger(int pname) {
        if(pname==GL_PACK_ALIGNMENT)return pack;
        if(pname==GL30.GL_READ_FRAMEBUFFER_BINDING)return GL30.read;
        if(pname==GL_READ_BUFFER)return GL30.read==7?
                GL30.oldReadBuffer:GL30.sourceReadBuffer;
        return 0;
    }
    public static void glPixelStorei(int pname,int value) {
        if(pname==GL_PACK_ALIGNMENT)pack=value;
    }
    public static void glReadBuffer(int attachment) {
        if(GL30.read==7)GL30.oldReadBuffer=attachment;
        else GL30.sourceReadBuffer=attachment;
    }
    public static void glReadPixels(int x,int y,int w,int h,
            int format,int type,ByteBuffer pixels) {
        readCalls++;readX=x;readY=y;readW=w;readH=h;
        if(!pixels.isDirect())throw new IllegalStateException("Direct buffer required");
        if(GL30.read==7||format!=GL_RGBA||type!=GL_UNSIGNED_BYTE)
            throw new IllegalStateException("Wrong GL target or format");
        for(int i=0;i<w*h*4;i++)pixels.put(i,(byte)(i%251));
    }
    public static int glGetError(){return GL_NO_ERROR;}
}

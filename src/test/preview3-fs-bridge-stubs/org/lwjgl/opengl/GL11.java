package org.lwjgl.opengl;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
public final class GL11 {
    public static final int GL_MATRIX_MODE=2976, GL_PROJECTION=5889,
      GL_MODELVIEW=5888,GL_VIEWPORT=2978,GL_COLOR_BUFFER_BIT=16384,
      GL_DEPTH_BUFFER_BIT=256, GL_STENCIL_BUFFER_BIT=1024,
      GL_NO_ERROR=0,GL_NEAREST=9728,GL_LINEAR=9729,GL_ALL_ATTRIB_BITS=1048575,
      GL_SCISSOR_TEST=3089,GL_DEPTH_TEST=2929,GL_STENCIL_TEST=2960,
      GL_CULL_FACE=2884,GL_LIGHTING=2896,GL_FOG=2912,
      GL_ALPHA_TEST=3008,GL_TEXTURE_2D=3553, GL_BLEND=3042,
      GL_TEXTURE_BINDING_2D=32873,GL_SRC_ALPHA=770,
      GL_ONE_MINUS_SRC_ALPHA=771,GL_PROJECTION_MATRIX=2983,
      GL_MODELVIEW_STACK_DEPTH=2979,GL_PROJECTION_STACK_DEPTH=2980,
      GL_QUADS=7;
    public static int matrix=GL_MODELVIEW,modelDepth=1,projDepth=1;
    public static int[] viewport=new int[]{0,0,1280,720};
    public static int attrDepth=0,quads=0;
    public static boolean scissor;
    public static int glGetInteger(int p) {
        if(p==GL_MATRIX_MODE)return matrix;
        if(p==GL_MODELVIEW_STACK_DEPTH)return modelDepth;
        if(p==GL_PROJECTION_STACK_DEPTH)return projDepth;
        if(p==GL30.GL_READ_FRAMEBUFFER_BINDING)return GL30.read;
        if(p==GL30.GL_DRAW_FRAMEBUFFER_BINDING)return GL30.draw;
        if(p==GL20.GL_CURRENT_PROGRAM)return GL20.program;
        if(p==GL13.GL_ACTIVE_TEXTURE)return GL13.active;
        return 0;
    }
    public static void glGetInteger(int p,IntBuffer b) {
        if(p==GL_VIEWPORT)for(int i=0;i<4;i++)b.put(i,viewport[i]);
    }
    public static void glGetFloat(int p,FloatBuffer b) {
        for(int i=0;i<16;i++)b.put(i,(i==0||i==5||i==10)?1.5f:(i==11?-1f:0f));
    }
    public static boolean glIsEnabled(int value) {
        return value==GL_SCISSOR_TEST && scissor;
    }
    public static int glGetError(){return GL_NO_ERROR;}
    public static void glPushAttrib(int mask){attrDepth++;}
    public static void glPopAttrib(){attrDepth--;}
    public static void glMatrixMode(int mode){matrix=mode;}
    public static void glPushMatrix(){if(matrix==GL_PROJECTION)projDepth++;else modelDepth++;}
    public static void glPopMatrix(){if(matrix==GL_PROJECTION)projDepth--;else modelDepth--;}
    public static void glLoadMatrix(FloatBuffer b){}
    public static void glLoadIdentity(){}
    public static void glOrtho(double a,double b,double c,double d,double e,double f){}
    public static void glViewport(int x,int y,int w,int h){viewport=new int[]{x,y,w,h};}
    public static void glScissor(int x,int y,int w,int h){}
    public static void glEnable(int mask){if(mask==GL_SCISSOR_TEST)scissor=true;}
    public static void glDisable(int mask){if(mask==GL_SCISSOR_TEST)scissor=false;}
    public static void glClear(int mask){}
    public static void glClearColor(float r,float g,float b,float a){}
    public static void glDepthMask(boolean value){}
    public static void glColorMask(boolean a,boolean b,boolean c,boolean d){}
    public static void glColor4f(float r,float g,float b,float a){}
    public static void glBlendFunc(int a,int b){}
    public static void glBindTexture(int type,int texture){}
    public static void glBegin(int mode){quads++;}
    public static void glTexCoord2f(float u,float v){}
    public static void glVertex2f(float x,float y){}
    public static void glEnd(){}
}
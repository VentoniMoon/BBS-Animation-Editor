package com.example.examplemod;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Spatial cleanup for the finished BSL mirror image, not its GBuffer.
 *
 * The independent reflected view has no valid TAA history. Filtering only
 * the final color in a separate scratch framebuffer reduces BSL dithering
 * without corrupting material IDs, normals, depth or the main OptiFine pass.
 * All GL calls must run on the Minecraft render thread.
 */
public final class CameraMirrorBSLDenoiser
{
    private static final String TAG="[BBS Editor][Mirror][BSL Denoise] ";
    private static final String FRAGMENT=
            "#version 120\n"+
            "uniform sampler2D sourceImage;\n"+
            "uniform vec2 pixelSize;\n"+
            "uniform float strength;\n"+
            "void main() {\n"+
            "  vec2 uv = gl_TexCoord[0].st;\n"+
            "  vec4 original = texture2D(sourceImage, uv);\n"+
            "  vec3 sum = vec3(0.0);\n"+
            "  float sumWeight = 0.0;\n"+
            "  for (int y = -2; y <= 2; ++y) {\n"+
            "    for (int x = -2; x <= 2; ++x) {\n"+
            "      float distance2 = float(x*x + y*y);\n"+
            "      if (distance2 > 4.0) continue;\n"+
            "      vec3 sampleColor = texture2D(sourceImage,\n"+
            "          uv + vec2(float(x), float(y)) * pixelSize).rgb;\n"+
            "      vec3 delta = sampleColor - original.rgb;\n"+
            "      float weight = exp(-0.55 * distance2) *\n"+
            "                     exp(-90.0 * dot(delta, delta));\n"+
            "      sum += sampleColor * weight;\n"+
            "      sumWeight += weight;\n"+
            "    }\n"+
            "  }\n"+
            "  vec3 filtered = sum / max(0.0001, sumWeight);\n"+
            "  gl_FragColor = vec4(mix(original.rgb, filtered, strength),\n"+
            "                      original.a);\n"+
            "}\n";

    private static int scratchFbo, scratchColor, scratchWidth, scratchHeight;
    private static int program;
    private static boolean unavailable, logged;

    private CameraMirrorBSLDenoiser(){}

    /** After BSL's final shader, before drawing the reflection on its quad. */
    public static void apply(Framebuffer output)
    {
        if(unavailable||output==null||output.framebufferTexture<=0||
                "false".equalsIgnoreCase(System.getProperty(
                        "bbs.editor.camera.mirrorBSLDenoise","true")))
            return;
        float strength=strength();
        if(strength<=0F)return;
        try
        {
            filter(output,strength);
            if(!logged)
            {
                logged=true;
                System.out.println(TAG+"13-tap edge-aware final-color "+
                        "filter active; strength="+strength);
            }
        }
        catch(RuntimeException error)
        {
            // A failed optional filter must never disable mirror capture.
            unavailable=true;
            if(!logged)
            {
                logged=true;
                System.err.println(TAG+"disabled; raw mirror retained: "+
                        error);
            }
            releaseResources();
        }
    }

    private static float strength()
    {
        try
        {
            float value=Float.parseFloat(System.getProperty(
                    "bbs.editor.camera.mirrorBSLDenoiseStrength","0.90"));
            return Float.isNaN(value)?0.90F:Math.max(0F,Math.min(1F,value));
        }
        catch(NumberFormatException error){return 0.90F;}
    }

    private static void filter(Framebuffer output,float strength)
    {
        final int width=output.framebufferWidth;
        final int height=output.framebufferHeight;
        if(width<=0||height<=0)return;
        final int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int oldUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        final int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int oldRead=GL11.glGetInteger(
                GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int oldDraw=GL11.glGetInteger(
                GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport=BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0,1,0,1,-1,1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try
        {
            ensureResources(width,height);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,scratchFbo);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0,0,width,height);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,
                    output.framebufferTexture);
            GL20.glUniform1i(GL20.glGetUniformLocation(
                    program,"sourceImage"),0);
            GL20.glUniform2f(GL20.glGetUniformLocation(
                    program,"pixelSize"),1F/width,1F/height);
            GL20.glUniform1f(GL20.glGetUniformLocation(
                    program,"strength"),strength);
            GL11.glColor4f(1F,1F,1F,1F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0F,0F); GL11.glVertex2f(0F,0F);
            GL11.glTexCoord2f(1F,0F); GL11.glVertex2f(1F,0F);
            GL11.glTexCoord2f(1F,1F); GL11.glVertex2f(1F,1F);
            GL11.glTexCoord2f(0F,1F); GL11.glVertex2f(0F,1F);
            GL11.glEnd();

            // Explicit scratch -> final blit: no sampled texture is ever
            // attached to the shader's draw target (no feedback loop).
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,scratchFbo);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,
                    output.framebufferObject);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,
                    GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);
        }
        finally
        {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
            GL11.glViewport(viewport.get(0),viewport.get(1),
                    viewport.get(2),viewport.get(3));
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTexture);
            GL13.glActiveTexture(oldUnit);
            GL11.glMatrixMode(oldMode);
            GL20.glUseProgram(oldProgram);
        }
    }

    private static void ensureResources(int width,int height)
    {
        if(program==0)program=compileProgram();
        if(scratchFbo!=0&&scratchWidth==width&&scratchHeight==height)
            return;
        releaseBuffers();
        scratchWidth=width;scratchHeight=height;
        scratchColor=GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,scratchColor);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,
                width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer)null);
        scratchFbo=GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,scratchFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,
                scratchColor,0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        int status=GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if(status!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Scratch FBO incomplete: 0x"+
                    Integer.toHexString(status));
    }

    private static int compileProgram()
    {
        int shader=GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        int result=0;
        try
        {
            GL20.glShaderSource(shader,FRAGMENT);
            GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0)
                throw new IllegalStateException("Fragment compile: "+
                        GL20.glGetShaderInfoLog(shader,4096));
            result=GL20.glCreateProgram();
            GL20.glAttachShader(result,shader);
            GL20.glLinkProgram(result);
            if(GL20.glGetProgrami(result,GL20.GL_LINK_STATUS)==0)
                throw new IllegalStateException("Program link: "+
                        GL20.glGetProgramInfoLog(result,4096));
            return result;
        }
        catch(RuntimeException error)
        {
            if(result!=0)GL20.glDeleteProgram(result);
            throw error;
        }
        finally{GL20.glDeleteShader(shader);}
    }

    private static void releaseBuffers()
    {
        if(scratchFbo!=0)GL30.glDeleteFramebuffers(scratchFbo);
        if(scratchColor!=0)GL11.glDeleteTextures(scratchColor);
        scratchFbo=0;scratchColor=0;scratchWidth=0;scratchHeight=0;
    }

    private static void releaseResources()
    {
        releaseBuffers();
        if(program!=0)GL20.glDeleteProgram(program);
        program=0;
    }

    public static void reset()
    {
        releaseResources();
        unavailable=false;
        logged=false;
    }
}
